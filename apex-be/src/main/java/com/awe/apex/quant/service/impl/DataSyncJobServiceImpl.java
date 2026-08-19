package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.config.ScriptDatabaseEnvironment;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.bo.UserDecisionResultBO;
import com.awe.apex.quant.domain.dto.BarSyncResp;
import com.awe.apex.quant.domain.dto.DecisionRunReq;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.SyncJobResp;
import com.awe.apex.quant.domain.dto.SyncOverviewResp;
import com.awe.apex.quant.domain.dto.SyncStartReq;
import com.awe.apex.quant.domain.dto.SyncTaskDefResp;
import com.awe.apex.quant.domain.entity.SyncJob;
import com.awe.apex.quant.mapper.SyncJobMapper;
import com.awe.apex.quant.service.IDataSyncJobService;
import com.awe.apex.quant.service.IBarDailyService;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.IMorningBriefingService;
import com.awe.apex.quant.service.IMyHoldingService;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.service.IWatchlistService;
import com.awe.apex.quant.sync.SyncJobLeaseService;
import com.awe.apex.quant.sync.SyncTaskHealth;
import com.awe.apex.quant.sync.SyncTaskRegistry;
import com.awe.apex.quant.sync.SyncTaskSpec;
import com.awe.apex.quant.util.ProcessIoUtils;
import com.awe.apex.quant.util.PythonCommandResolver;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一数据同步任务：异步启停 + 进度/日志
 */
@Slf4j
@Service
public class DataSyncJobServiceImpl implements IDataSyncJobService {

    private static final Pattern PCT_PATTERN = Pattern.compile("(\\d{1,3})\\s*%");
    private static final Pattern STEP_PATTERN = Pattern.compile("\\[(\\d+)\\s*/\\s*(\\d+)]");
    /** 编排脚本阶段进度，例如“步骤 1/5：index” */
    private static final Pattern SCRIPT_STEP_PATTERN = Pattern.compile("(?:步骤|step)\\s+(\\d+)\\s*/\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTIAL_COUNT_PATTERN = Pattern.compile(
            "成功(?:数|数据源数)=(\\d+).*失败(?:数|数据源数)=(\\d+)");
    private static final int LOG_MAX = 12000;
    private static final long ORPHAN_RECONCILE_GRACE_SECONDS = 300;
    private static final long SHARED_DECISION_KEY = 0L;
    private static final int USER_DECISION_PARALLELISM = 2;

    @Resource
    private SyncJobMapper syncJobMapper;

    @Resource
    private SyncTaskRegistry syncTaskRegistry;

    @Resource
    private IMarketBriefingService marketBriefingService;

    @Resource
    private IMorningBriefingService morningBriefingService;

    @Resource
    private IWatchlistService watchlistService;

    @Resource
    private IBarDailyService barDailyService;

    @Resource
    private IMyHoldingService myHoldingService;

    @Resource
    private IPortfolioService portfolioService;

    @Resource
    private IConfigService configService;

    @Resource
    private IDecisionService decisionService;

    @Resource
    private ApexUserContext userContext;

    @Resource
    private ApexUserAuthService userAuthService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ScriptDatabaseEnvironment scriptDatabaseEnvironment;

    @Resource
    private SyncJobLeaseService syncJobLeaseService;

    @Value("${apex.sync.python-cmd:${apex.hot.python-cmd:python}}")
    private String pythonCmd;

    @Value("${apex.sync.script-dir:}")
    private String scriptDirConfig;

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "apex-sync-job");
        t.setDaemon(true);
        return t;
    });

    private final Map<Long, Process> runningProcesses = new ConcurrentHashMap<>();
    private final Map<Long, Future<?>> runningFutures = new ConcurrentHashMap<>();
    private final Map<Long, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();
    private final Map<Long, Long> runningDecisionJobs = new ConcurrentHashMap<>();
    private final Map<Long, String> runningLeaseKeys = new ConcurrentHashMap<>();
    private final Map<Long, String> runningLeaseOwners = new ConcurrentHashMap<>();

    /**
     * 清理超过任务超时窗口的僵尸记录，保留可能由其他实例执行的近期任务。
     */
    @PostConstruct
    public void reconcileOrphanJobs() {
        List<SyncJob> orphans = syncJobMapper.selectList(Wrappers.<SyncJob>lambdaQuery()
                .in(SyncJob::getStatus, List.of("RUNNING", "PENDING")));
        if (CollUtil.isEmpty(orphans)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (SyncJob job : orphans) {
            SyncTaskSpec spec;
            try {
                spec = syncTaskRegistry.require(job.getTaskType());
            } catch (Exception ex) {
                log.warn("保留无法识别的运行中任务，任务编号={}，任务类型={}", job.getId(), job.getTaskType());
                continue;
            }
            LocalDateTime startedAt = job.getStartedAt();
            LocalDateTime staleAfter = Objects.nonNull(startedAt)
                    ? startedAt.plusSeconds(spec.getTimeoutSec() + ORPHAN_RECONCILE_GRACE_SECONDS) : null;
            if (Objects.nonNull(staleAfter) && now.isBefore(staleAfter)) {
                log.info("保留可能由其他实例执行的同步任务，任务编号={}，任务类型={}，超时判定时间={}",
                        job.getId(), job.getTaskType(), staleAfter);
                continue;
            }
            job.setStatus("FAILED");
            job.setMessage("任务超过运行时限（僵尸任务已清理）");
            job.setFinishedAt(now);
            appendLog(job, "\n[僵尸任务] 超过运行时限，任务标记为失败\n");
            syncJobMapper.updateById(job);
            log.warn("清理超时僵尸同步任务，任务编号={}，任务类型={}，原状态=运行中或等待中",
                    job.getId(), job.getTaskType());
        }
    }

    /**
     * 总览
     *
     * @return 总览
     */
    @Override
    public SyncOverviewResp overview() {
        List<SyncJobResp> recent = recentJobs(20);
        Map<String, SyncJobResp> latestByType = new ConcurrentHashMap<>();
        for (SyncJobResp job : recent) {
            latestByType.putIfAbsent(job.getTaskType(), job);
        }
        // 补充各类型最新（含更早）
        for (SyncTaskSpec spec : syncTaskRegistry.all()) {
            if (!latestByType.containsKey(spec.getTaskType())) {
                var latestQuery = Wrappers.<SyncJob>lambdaQuery()
                        .eq(SyncJob::getTaskType, spec.getTaskType());
                SyncJob one = syncJobMapper.selectOne(latestQuery
                        .orderByDesc(SyncJob::getId)
                        .last("LIMIT 1"));
                if (Objects.nonNull(one)) {
                    latestByType.put(spec.getTaskType(), toResp(one, false));
                }
            }
        }

        int running = 0;
        List<SyncTaskDefResp> tasks = new ArrayList<>();
        for (SyncTaskSpec spec : syncTaskRegistry.all()) {
            SyncJobResp latest = latestByType.get(spec.getTaskType());
            boolean isRunning = Objects.nonNull(latest)
                    && ("RUNNING".equals(latest.getStatus()) || "PENDING".equals(latest.getStatus()));
            if (isRunning) {
                running++;
            }
            LocalDateTime lastSuccessAt = null;
            var successQuery = Wrappers.<SyncJob>lambdaQuery()
                    .eq(SyncJob::getTaskType, spec.getTaskType())
                    .eq(SyncJob::getStatus, "SUCCESS");
            SyncJob success = syncJobMapper.selectOne(successQuery
                    .orderByDesc(SyncJob::getId)
                    .last("LIMIT 1"));
            if (Objects.nonNull(success)) {
                lastSuccessAt = Objects.nonNull(success.getFinishedAt())
                        ? success.getFinishedAt() : success.getStartedAt();
            }
            boolean latestFailed = Objects.nonNull(latest) && "FAILED".equals(latest.getStatus());
            String health = "PARTIAL".equals(Objects.nonNull(latest) ? latest.getStatus() : null)
                    ? "YELLOW" : SyncTaskHealth.resolve(isRunning, lastSuccessAt, latestFailed, LocalDateTime.now());
            tasks.add(SyncTaskDefResp.builder()
                    .taskType(spec.getTaskType())
                    .name(spec.getName())
                    .groupName(spec.getGroupName())
                    .description(spec.getDescription())
                    .defaultParamsHint(spec.getDefaultParamsHint())
                    .running(isRunning)
                    .latestJob(latest)
                    .lastSuccessAt(lastSuccessAt)
                    .healthLevel(health)
                    .build());
        }
        return SyncOverviewResp.builder()
                .tasks(tasks)
                .runningCount(running)
                .recentJobs(recent)
                .message("可启动 " + tasks.size() + " 类同步 · 运行中 " + running)
                .build();
    }

    /**
     * 启动
     *
     * @param req 请求
     * @return 任务
     */
    @Override
    public SyncJobResp start(SyncStartReq req) {
        userAuthService.requireAdmin();
        return startInternal(req);
    }

    /**
     * 由系统调度启动共享同步任务
     *
     * @param req 请求
     * @return 任务状态
     */
    @Override
    public SyncJobResp startSystemTask(SyncStartReq req) {
        return startInternal(req);
    }

    /**
     * 拒绝旧版按用户启动入口，智能决策统一由共享任务生成。
     *
     * @param req 请求
     * @param userId 所属用户ID
     * @return 不返回，始终抛出业务异常
     */
    @Override
    public SyncJobResp startForUser(SyncStartReq req, Long userId) {
        throw new BusinessException("智能决策已改为系统共享任务，不支持按用户启动");
    }

    /**
     * 判断当前用户的智能决策任务是否正在运行。
     *
     * @return true=正在运行
     */
    @Override
    public boolean isCurrentUserDecisionRunning() {
        return runningDecisionJobs.containsKey(SHARED_DECISION_KEY);
    }

    /**
     * 判断指定类型的共享任务是否正在等待或运行。
     *
     * @param taskType 任务类型
     * @return true=正在等待或运行
     */
    @Override
    public boolean isTaskRunning(String taskType) {
        if (StringUtils.isBlank(taskType)) {
            return false;
        }
        SyncJob runningJob = syncJobMapper.selectOne(Wrappers.<SyncJob>lambdaQuery()
                .eq(SyncJob::getTaskType, taskType.trim().toUpperCase(Locale.ROOT))
                .in(SyncJob::getStatus, List.of("PENDING", "RUNNING"))
                .orderByDesc(SyncJob::getId)
                .last("LIMIT 1"));
        return Objects.nonNull(runningJob);
    }

    private synchronized SyncJobResp startInternal(SyncStartReq req) {
        if (Objects.isNull(req) || StringUtils.isBlank(req.getTaskType())) {
            throw new BusinessException("请指定 taskType");
        }
        SyncTaskSpec spec = syncTaskRegistry.require(req.getTaskType());
        String leaseKey = "apex:sync:lease:" + spec.getTaskType();
        String leaseOwner = UUID.randomUUID().toString();
        Duration leaseTtl = Duration.ofSeconds(Math.max(spec.getTimeoutSec() + ORPHAN_RECONCILE_GRACE_SECONDS, 600));
        if (!syncJobLeaseService.tryAcquire(leaseKey, leaseOwner, leaseTtl)) {
            throw new BusinessException(spec.getName() + " 已由其他服务实例启动，请等待完成");
        }
        boolean leaseRegistered = false;
        try {
            var runningQuery = Wrappers.<SyncJob>lambdaQuery()
                    .eq(SyncJob::getTaskType, spec.getTaskType())
                    .in(SyncJob::getStatus, List.of("PENDING", "RUNNING"));
            if ("DECISION".equals(spec.getTaskType())) {
                if (runningDecisionJobs.containsKey(SHARED_DECISION_KEY)) {
                    throw new BusinessException(spec.getName() + " 正在运行中（jobId="
                            + runningDecisionJobs.get(SHARED_DECISION_KEY) + "），请等待完成");
                }
            } else {
                SyncJob running = syncJobMapper.selectOne(runningQuery
                        .orderByDesc(SyncJob::getId)
                        .last("LIMIT 1"));
                if (Objects.nonNull(running)) {
                    throw new BusinessException(spec.getName() + " 正在运行中（jobId="
                            + running.getId() + "），请先停止");
                }
            }

            Path script = null;
            if (!"DECISION".equals(spec.getTaskType())) {
                Path scriptDir = resolveScriptDir();
                script = scriptDir.resolve(spec.getScriptFile());
                if (!Files.isRegularFile(script)) {
                    throw new BusinessException("未找到脚本 " + spec.getScriptFile() + "，请配置 apex.sync.script-dir");
                }
            }
            List<String> scriptArgs = syncTaskRegistry.buildArgs(spec, req);
            String paramsJson;
            try {
                paramsJson = objectMapper.writeValueAsString(scriptArgs);
            } catch (Exception ex) {
                paramsJson = String.join(" ", scriptArgs);
            }

            SyncJob job = SyncJob.builder()
                    .taskType(spec.getTaskType())
                    .taskName(spec.getName())
                    .status("PENDING")
                    .paramsJson(paramsJson)
                    .progressPct(0)
                    .message("排队启动")
                    .logTail("")
                    .startedAt(LocalDateTime.now())
                    .build();
            syncJobMapper.insert(job);
            runningLeaseKeys.put(job.getId(), leaseKey);
            runningLeaseOwners.put(job.getId(), leaseOwner);
            leaseRegistered = true;
            if ("DECISION".equals(spec.getTaskType())) {
                runningDecisionJobs.put(SHARED_DECISION_KEY, job.getId());
            }

            AtomicBoolean cancelled = new AtomicBoolean(false);
            cancelFlags.put(job.getId(), cancelled);
            try {
                Runnable jobTask;
                if ("DECISION".equals(spec.getTaskType())) {
                    jobTask = () -> runDecisionJob(job.getId(), req, cancelled);
                } else {
                    Path jobScript = script;
                    jobTask = () -> runJob(job.getId(), spec, jobScript, scriptArgs, cancelled);
                }
                FutureTask<Void> future = new FutureTask<>(jobTask, null);
                runningFutures.put(job.getId(), future);
                executor.execute(future);
                return toResp(job);
            } catch (RuntimeException ex) {
                runningDecisionJobs.remove(SHARED_DECISION_KEY, job.getId());
                cleanup(job.getId());
                throw ex;
            }
        } finally {
            if (!leaseRegistered) {
                syncJobLeaseService.release(leaseKey, leaseOwner);
            }
        }
    }

    private void runDecisionJob(Long jobId, SyncStartReq syncRequest, AtomicBoolean cancelled) {
        SyncJob job = syncJobMapper.selectById(jobId);
        if (Objects.isNull(job)) {
            return;
        }
        try {
            job.setStatus("RUNNING");
            job.setMessage("正在扫描共享市场信号");
            job.setStartedAt(LocalDateTime.now());
            appendLog(job, "[共享市场决策] 已开始\n");
            syncJobMapper.updateById(job);

            DecisionRunReq request = new DecisionRunReq();
            request.setGroupName(configService.getString("auto_sync_group", "我的自选"));
            request.setIncludeBj(Boolean.TRUE.equals(syncRequest.getIncludeBj()));
            decisionService.refreshMarketSignals(request, (completed, total, message) -> {
                if (cancelled.get()) {
                    return;
                }
                int progressPct = total > 0 ? completed * 70 / total : 0;
                int currentProgress = Objects.nonNull(job.getProgressPct()) ? job.getProgressPct() : 0;
                job.setProgressPct(Math.min(70, Math.max(currentProgress, progressPct)));
                job.setDoneItems(completed);
                job.setTotalItems(total > 0 ? total : null);
                job.setMessage(clip(message, 400));
                appendLog(job, "[共享扫描] " + job.getProgressPct() + "% " + message + "\n");
                syncJobMapper.updateById(job);
            });
            if (cancelled.get()) {
                return;
            }

            List<Long> userIds = userAuthService.listEnabledUserIds();
            if (CollUtil.isEmpty(userIds)) {
                userIds = new ArrayList<>();
            }
            int userTotal = userIds.size();
            int successCount = 0;
            int failureCount = 0;
            int totalBuyCount = 0;
            int totalSellCount = 0;
            int totalHoldCount = 0;
            if (userTotal > 0) {
                ExecutorService decisionExecutor = Executors.newFixedThreadPool(
                        Math.min(USER_DECISION_PARALLELISM, userTotal), runnable -> {
                    Thread thread = new Thread(runnable, "apex-user-decision");
                    thread.setDaemon(true);
                    return thread;
                });
                CompletionService<UserDecisionResultBO> completionService =
                        new ExecutorCompletionService<>(decisionExecutor);
                try {
                    for (Long userId : userIds) {
                        completionService.submit(() -> {
                            try {
                                DecisionTodayResp response = userContext.runAsUser(userId,
                                        () -> decisionService.run(request, (completed, total, message) -> { }));
                                return UserDecisionResultBO.builder()
                                        .userId(userId)
                                        .success(true)
                                        .response(response)
                                        .build();
                            } catch (Exception ex) {
                                return UserDecisionResultBO.builder()
                                        .userId(userId)
                                        .success(false)
                                        .errorMessage(StringUtils.isNotBlank(ex.getMessage())
                                                ? ex.getMessage() : ex.toString())
                                        .build();
                            }
                        });
                    }

                    for (int completedUsers = 1; completedUsers <= userTotal; completedUsers++) {
                        if (cancelled.get()) {
                            return;
                        }
                        UserDecisionResultBO userResult = completionService.take().get();
                        if (Boolean.TRUE.equals(userResult.getSuccess())) {
                            DecisionTodayResp response = userResult.getResponse();
                            successCount++;
                            totalBuyCount += Objects.nonNull(response.getBuyCount()) ? response.getBuyCount() : 0;
                            totalSellCount += Objects.nonNull(response.getSellCount()) ? response.getSellCount() : 0;
                            totalHoldCount += Objects.nonNull(response.getHoldCount()) ? response.getHoldCount() : 0;
                            appendLog(job, "[用户决策] 用户编号=" + userResult.getUserId()
                                    + "，运行编号=" + response.getRunNo() + "，状态=成功\n");
                        } else {
                            failureCount++;
                            appendLog(job, "[用户决策] 用户编号=" + userResult.getUserId() + "，状态=失败，原因="
                                    + clip(userResult.getErrorMessage(), 300) + "\n");
                            log.warn("用户智能决策投影失败，任务编号={}，用户编号={}，异常={}",
                                    jobId, userResult.getUserId(), userResult.getErrorMessage());
                        }
                        job.setProgressPct(70 + completedUsers * 29 / userTotal);
                        job.setDoneItems(completedUsers);
                        job.setTotalItems(userTotal);
                        job.setMessage("正在生成用户决策 " + completedUsers + "/" + userTotal);
                        syncJobMapper.updateById(job);
                    }
                } finally {
                    decisionExecutor.shutdownNow();
                }
            }

            job.setStatus(failureCount > 0 ? "PARTIAL" : "SUCCESS");
            job.setProgressPct(100);
            job.setMessage("完成：用户成功 " + successCount + "，失败 " + failureCount
                    + "，买入 " + totalBuyCount + "，卖出 " + totalSellCount + "，持有 " + totalHoldCount);
            appendLog(job, "[共享市场决策] 用户成功=" + successCount + "，用户失败=" + failureCount + "\n");
            job.setFinishedAt(LocalDateTime.now());
            syncJobMapper.updateById(job);
        } catch (Exception ex) {
            SyncJob failedJob = syncJobMapper.selectById(jobId);
            if (Objects.nonNull(failedJob) && !"CANCELLED".equals(failedJob.getStatus())) {
                failedJob.setStatus("FAILED");
                failedJob.setMessage(clip(StringUtils.isNotBlank(ex.getMessage())
                        ? ex.getMessage() : "智能决策失败", 400));
                appendLog(failedJob, "[错误] " + ex + "\n");
                failedJob.setFinishedAt(LocalDateTime.now());
                syncJobMapper.updateById(failedJob);
            }
            log.warn("共享智能决策任务失败，任务编号={}，异常={}", jobId, ex.toString());
        } finally {
            cleanup(jobId);
            runningDecisionJobs.remove(SHARED_DECISION_KEY, jobId);
        }
    }

    /**
     * 查询
     *
     * @param jobId ID
     * @return 状态
     */
    @Override
    public SyncJobResp getJob(Long jobId) {
        if (Objects.isNull(jobId)) {
            throw new BusinessException("jobId 不能为空");
        }
        SyncJob job = syncJobMapper.selectById(jobId);
        if (Objects.isNull(job)) {
            throw new BusinessException("任务不存在: " + jobId);
        }
        // 仅脚本进度文件需要回写；条件更新避免轮询用旧 RUNNING 覆盖任务终态
        if ("RUNNING".equals(job.getStatus()) && enrichProgressFromFile(job)) {
            int updated = syncJobMapper.update(job, Wrappers.<SyncJob>lambdaUpdate()
                    .eq(SyncJob::getId, jobId)
                    .eq(SyncJob::getStatus, "RUNNING"));
            if (updated == 0) {
                SyncJob latestJob = syncJobMapper.selectById(jobId);
                if (Objects.nonNull(latestJob)) {
                    job = latestJob;
                }
            }
        }
        return toResp(job);
    }

    /**
     * 停止
     *
     * @param jobId ID
     * @return 状态
     */
    @Override
    public SyncJobResp stop(Long jobId) {
        SyncJob job = syncJobMapper.selectById(jobId);
        if (Objects.isNull(job)) {
            throw new BusinessException("任务不存在: " + jobId);
        }
        if (!"DECISION".equals(job.getTaskType())) {
            userAuthService.requireAdmin();
        }
        if ("DECISION".equals(job.getTaskType())
                && ("RUNNING".equals(job.getStatus()) || "PENDING".equals(job.getStatus()))) {
            throw new BusinessException("智能决策运行后不可停止，请等待后台完成");
        }
        if (!"RUNNING".equals(job.getStatus()) && !"PENDING".equals(job.getStatus())) {
            return toResp(job);
        }
        AtomicBoolean flag = cancelFlags.get(jobId);
        if (Objects.nonNull(flag)) {
            flag.set(true);
        }
        Process process = runningProcesses.get(jobId);
        if (Objects.nonNull(process) && process.isAlive()) {
            ProcessIoUtils.destroyProcessTree(process);
        }
        Future<?> future = runningFutures.get(jobId);
        if (Objects.nonNull(future)) {
            future.cancel(true);
        }
        job.setStatus("CANCELLED");
        job.setMessage("用户停止");
        job.setFinishedAt(LocalDateTime.now());
        appendLog(job, "\n[用户停止]\n");
        syncJobMapper.updateById(job);
        cleanup(jobId);
        return toResp(job);
    }

    /**
     * 最近任务
     *
     * @param limit 条数
     * @return 列表
     */
    @Override
    public List<SyncJobResp> recentJobs(Integer limit) {
        int size = Objects.isNull(limit) || limit <= 0 ? 20 : Math.min(limit, 100);
        List<SyncJob> rows = syncJobMapper.selectList(Wrappers.<SyncJob>lambdaQuery()
                .orderByDesc(SyncJob::getId)
                .last("LIMIT " + size));
        List<SyncJobResp> list = new ArrayList<>();
        if (CollUtil.isEmpty(rows)) {
            return list;
        }
        for (SyncJob row : rows) {
            // 列表不带完整日志，避免 overview 体积过大；看日志走 getJob
            list.add(toResp(row, false));
        }
        return list;
    }

    /**
     * 停止任务线程并释放本实例持有的跨实例租约。
     */
    @PreDestroy
    public void shutdown() {
        for (Map.Entry<Long, Process> entry : runningProcesses.entrySet()) {
            Process p = entry.getValue();
            if (Objects.nonNull(p) && p.isAlive()) {
                ProcessIoUtils.destroyProcessTree(p);
            }
        }
        for (Future<?> future : new ArrayList<>(runningFutures.values())) {
            future.cancel(true);
        }
        executor.shutdownNow();
        for (Long jobId : new ArrayList<>(runningLeaseKeys.keySet())) {
            cleanup(jobId);
        }
        runningDecisionJobs.clear();
    }

    private void runJob(Long jobId, SyncTaskSpec spec, Path script, List<String> scriptArgs, AtomicBoolean cancelled) {
        SyncJob job = syncJobMapper.selectById(jobId);
        if (Objects.isNull(job)) {
            return;
        }
        List<String> command = new ArrayList<>();
        command.add(PythonCommandResolver.resolve(pythonCmd));
        command.add("-u");
        command.add(script.toAbsolutePath().toString());
        command.addAll(scriptArgs);

        StringBuilder logBuf = new StringBuilder();
        int exit = -1;
        Process process = null;
        try {
            job.setStatus("RUNNING");
            job.setMessage("运行中");
            job.setStartedAt(LocalDateTime.now());
            syncJobMapper.updateById(job);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(script.getParent().toFile());
            pb.redirectErrorStream(true);
            Map<String, String> env = pb.environment();
            scriptDatabaseEnvironment.apply(env);
            env.put("PYTHONUNBUFFERED", "1");
            env.put("PYTHONIOENCODING", "utf-8");
            env.put("PYTHONUTF8", "1");
            process = pb.start();
            runningProcesses.put(jobId, process);
            try {
                job.setPid(process.pid());
            } catch (Exception ignored) {
                // Java 版本/平台可能无 pid
            }
            syncJobMapper.updateById(job);

            // 独立线程逐行写入进度和日志，避免长脚本结束前无可观测信息。
            Charset charset = detectCharset();
            Process drainProcess = process;
            SyncJob runningJob = job;
            Thread drainThread = new Thread(() -> {
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(drainProcess.getInputStream(), charset))) {
                    String line;
                    long lineNo = 0;
                    while ((line = reader.readLine()) != null) {
                        lineNo++;
                        synchronized (logBuf) {
                            appendLine(logBuf, line);
                            runningJob.setLogTail(trimLog(logBuf.toString()));
                        }
                        if (!cancelled.get()) {
                            updateProgressFromLine(runningJob, line, lineNo);
                            syncJobMapper.update(runningJob, Wrappers.<SyncJob>lambdaUpdate()
                                    .eq(SyncJob::getId, jobId)
                                    .eq(SyncJob::getStatus, "RUNNING"));
                        }
                    }
                } catch (Exception ex) {
                    synchronized (logBuf) {
                        appendLine(logBuf, "[错误] 读取同步输出失败：" + errorMessage(ex));
                        runningJob.setLogTail(trimLog(logBuf.toString()));
                    }
                    if (!cancelled.get()) {
                        try {
                            syncJobMapper.update(runningJob, Wrappers.<SyncJob>lambdaUpdate()
                                    .eq(SyncJob::getId, jobId)
                                    .eq(SyncJob::getStatus, "RUNNING"));
                        } catch (Exception updateEx) {
                            log.warn("持久化同步输出异常失败，任务编号={}，异常={}", jobId, updateEx.getMessage());
                        }
                    }
                    log.warn("读取同步输出失败，任务编号={}，异常={}", jobId, ex.getMessage());
                }
            }, "apex-sync-drain-" + jobId);
            drainThread.setDaemon(true);
            drainThread.start();

            long timeoutSec = Math.max(spec.getTimeoutSec(), 60);
            boolean finished = ProcessIoUtils.waitOrKill(process, timeoutSec);
            try {
                drainThread.join(10000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            if (!finished) {
                throw new BusinessException("同步超时（>" + spec.getTimeoutSec() + "s）");
            }
            try {
                exit = process.exitValue();
            } catch (IllegalThreadStateException ex) {
                ProcessIoUtils.destroyProcessTree(process);
                exit = process.waitFor();
            }
            // 按行刷进度（日志已在 drain 线程写入）
            String snapshot;
            synchronized (logBuf) {
                snapshot = logBuf.toString();
            }
            if (StringUtils.isNotBlank(snapshot)) {
                String[] lines = snapshot.split("\n", -1);
                long done = 0;
                for (String line : lines) {
                    if (StringUtils.isBlank(line)) {
                        continue;
                    }
                    done++;
                    if (!cancelled.get()) {
                        updateProgressFromLine(job, line, done);
                    }
                }
            }
            job.setExitCode(exit);
            String logText = trimLog(snapshot);
            if (StringUtils.isBlank(logText)) {
                logText = buildFallbackLog(command, exit, null);
            } else if (exit != 0) {
                logText = trimLog(logText + "\n[退出码=" + exit + "]\n");
            }
            job.setLogTail(logText);
            enrichProgressFromFile(job);
            boolean partialScriptResult = isPartialScriptResult(snapshot);
            if (!cancelled.get()) {
                invalidateMarketBriefingCache(job, spec.getTaskType());
            }
            if (cancelled.get()) {
                job.setStatus("CANCELLED");
                job.setMessage("用户停止");
            } else if (exit != 0 && !partialScriptResult) {
                job.setStatus("FAILED");
                job.setMessage("脚本退出码 " + exit);
            } else {
                String postProcessingWarning = "";
                if ("CLOSE_BUNDLE".equals(spec.getTaskType())) {
                    job.setProgressPct(Math.min(99, Math.max(90,
                            Objects.nonNull(job.getProgressPct()) ? job.getProgressPct() : 0)));
                    job.setDoneItems(0);
                    job.setTotalItems(null);
                    job.setMessage("脚本完成，正在执行收盘后处理");
                    appendLog(job, "[收盘后处理] 已开始\n");
                    syncJobMapper.updateById(job);
                }
                if (exit == 0) {
                    postProcessingWarning = onMarketDataSynced(job, cancelled);
                }
                if (cancelled.get()) {
                    job.setStatus("CANCELLED");
                    job.setMessage("用户停止");
                } else if (partialScriptResult || StringUtils.isNotBlank(postProcessingWarning)) {
                    job.setStatus("PARTIAL");
                    job.setProgressPct(100);
                    job.setMessage(StringUtils.isNotBlank(postProcessingWarning)
                            ? clip(postProcessingWarning, 400) : "完成，但部分条目失败（详见日志）");
                } else {
                    job.setStatus("SUCCESS");
                    job.setProgressPct(100);
                    job.setMessage("完成");
                }
            }
            job.setFinishedAt(LocalDateTime.now());
            syncJobMapper.updateById(job);
        } catch (Exception ex) {
            log.warn("同步任务失败，任务编号={}，任务类型={}，异常={}", jobId, spec.getTaskType(), ex.toString());
            if (Objects.nonNull(process)) {
                try {
                    if (process.isAlive()) {
                        ProcessIoUtils.destroyProcessTree(process);
                    }
                    exit = process.exitValue();
                } catch (Exception ignored) {
                    // keep exit
                }
            }
            job = syncJobMapper.selectById(jobId);
            if (Objects.nonNull(job) && !"CANCELLED".equals(job.getStatus())) {
                String errMsg = StringUtils.isNotBlank(ex.getMessage())
                        ? ex.getMessage()
                        : ex.getClass().getSimpleName();
                if (ex instanceof InterruptedException) {
                    errMsg = "任务线程被中断（常见于服务热重启/停止），请重试";
                    Thread.currentThread().interrupt();
                }
                job.setStatus(cancelled.get() ? "CANCELLED" : "FAILED");
                job.setMessage(clip(errMsg, 400));
                job.setExitCode(exit);
                String processLog;
                synchronized (logBuf) {
                    processLog = logBuf.toString();
                }
                String fallback = buildFallbackLog(command, exit, errMsg);
                String persistedLog = job.getLogTail();
                String existingLog = StringUtils.isNotBlank(persistedLog) ? persistedLog : processLog;
                String merged = StringUtils.isNotBlank(existingLog) ? (existingLog + "\n" + fallback) : fallback;
                job.setLogTail(trimLog(merged));
                job.setFinishedAt(LocalDateTime.now());
                syncJobMapper.updateById(job);
            }
        } finally {
            cleanup(jobId);
        }
    }

    /**
     * 无脚本输出时仍写入可诊断日志，避免前端「暂无日志」
     */
    private String buildFallbackLog(List<String> command, int exit, String error) {
        StringBuilder sb = new StringBuilder();
        sb.append("[命令] ").append(String.join(" ", command)).append('\n');
        sb.append("[退出码] ").append(exit).append('\n');
        if (StringUtils.isNotBlank(error)) {
            sb.append("[错误] ").append(error).append('\n');
        }
        return sb.toString();
    }

    /**
     * 市场相关同步成功后刷新依赖数据
     */
    private String onMarketDataSynced(SyncJob job, AtomicBoolean cancelled) {
        if (Objects.isNull(job) || StringUtils.isBlank(job.getTaskType())) {
            return "";
        }
        String type = job.getTaskType().trim().toUpperCase(Locale.ROOT);
        if (!"CLOSE_BUNDLE".equals(type)) {
            return "";
        }

        ensurePostProcessingActive(cancelled);
        String group = "我的自选";
        try {
            if (Objects.nonNull(configService)) {
                group = configService.getString("auto_sync_group", group);
            }
        } catch (Exception ex) {
            appendLog(job, "[警告] 读取同步分组失败，使用默认分组：" + ex.getMessage() + "\n");
        }

        List<Long> userIds;
        try {
            userIds = userAuthService.listEnabledUserIds();
        } catch (Exception ex) {
            throw new BusinessException("收盘后处理失败：读取启用用户失败：" + errorMessage(ex), ex);
        }
        if (CollUtil.isEmpty(userIds)) {
            job.setProgressPct(99);
            job.setDoneItems(0);
            job.setTotalItems(0);
            job.setMessage("收盘后处理：无启用用户，已跳过用户数据刷新");
            appendLog(job, "[收盘后处理] 没有启用用户，已跳过用户数据刷新\n");
            syncJobMapper.updateById(job);
            return "";
        }

        String syncGroup = group;
        List<String> failureMessages = new ArrayList<>();
        runCloseBundlePostProcessing(job, syncGroup, userIds, cancelled, failureMessages);
        if (CollUtil.isNotEmpty(failureMessages)) {
            String failureSummary = "收盘后处理失败 " + failureMessages.size() + " 项："
                    + String.join("；", failureMessages);
            appendLog(job, "[错误] " + failureSummary + "\n");
            syncJobMapper.updateById(job);
            return failureSummary;
        }
        return "";
    }

    private void invalidateMarketBriefingCache(SyncJob job, String taskType) {
        if (StringUtils.isBlank(taskType)) {
            return;
        }
        String type = taskType.trim().toUpperCase(Locale.ROOT);
        if ("CLOSE_BUNDLE".equals(type) || "NIGHTLY_REPAIR".equals(type)
                || "INDEX".equals(type) || "SECTOR_QUOTE".equals(type) || "LIMIT_UP".equals(type)) {
            try {
                marketBriefingService.invalidateCache();
            } catch (Exception ex) {
                appendLog(job, "[警告] 清理市场简报缓存失败：" + ex.getMessage() + "\n");
                log.warn("清理市场简报缓存失败，任务类型={}，异常={}", type, ex.getMessage());
            }
        }
        if ("NEWS".equals(type) || "CLOSE_BUNDLE".equals(type)) {
            try {
                morningBriefingService.invalidateCache();
            } catch (Exception ex) {
                appendLog(job, "[警告] 清理盘前晨报缓存失败：" + ex.getMessage() + "\n");
                log.warn("清理盘前晨报缓存失败，任务类型={}，异常={}", type, ex.getMessage());
            }
        }
    }

    private void runCloseBundlePostProcessing(SyncJob job, String group, List<Long> userIds,
                                              AtomicBoolean cancelled, List<String> failureMessages) {
        Set<String> quoteCodes = new LinkedHashSet<>();
        Set<String> barCodes = new LinkedHashSet<>();

        // 1. 只在用户上下文中收集代码，所有共享行情在用户循环外统一处理。
        for (Long userId : userIds) {
            ensurePostProcessingActive(cancelled);
            try {
                userContext.runAsUser(userId, () -> {
                    List<String> watchlistCodes = watchlistService.listWatchlistCodes(group);
                    List<String> holdingCodes = myHoldingService.listHoldingCodes();
                    List<String> portfolioCodes = portfolioService.listActiveHoldingCodes();
                    quoteCodes.addAll(watchlistCodes);
                    quoteCodes.addAll(holdingCodes);
                    quoteCodes.addAll(portfolioCodes);
                    barCodes.addAll(watchlistCodes);
                    barCodes.addAll(holdingCodes);
                });
            } catch (Exception ex) {
                String failureMessage = "用户 " + userId + " · 代码收集：" + errorMessage(ex);
                failureMessages.add(failureMessage);
                appendLog(job, "[错误] 收盘后处理失败：" + failureMessage + "\n");
                log.warn("收盘任务代码收集失败，用户编号={}，原因={}", userId, errorMessage(ex));
            }
        }
        appendLog(job, "[收盘后处理] 代码汇总完成，行情去重数=" + quoteCodes.size()
                + "，日线去重数=" + barCodes.size() + "\n");

        int totalStages = userIds.size() + 2;
        AtomicInteger completedStages = new AtomicInteger();

        // 2. 共享行情只刷新一次，避免自选、持仓和组合重复请求相同证券。
        persistPostProcessingStage(job, "共享行情", completedStages.get(), totalStages, cancelled);
        boolean quoteSuccess = true;
        long quoteStartedAt = System.currentTimeMillis();
        try {
            if (CollUtil.isNotEmpty(quoteCodes)) {
                Map<String, Object> quoteResult = myHoldingService.refreshQuotesForCodes(
                        new ArrayList<>(quoteCodes), false);
                int failCount = resultCount(quoteResult, "fail");
                if (failCount > 0) {
                    throw new BusinessException("共享行情刷新失败 " + failCount + " 项");
                }
            }
            log.info("收盘任务共享行情完成，证券数={}，耗时毫秒={}",
                    quoteCodes.size(), System.currentTimeMillis() - quoteStartedAt);
        } catch (Exception ex) {
            quoteSuccess = false;
            recordPostProcessingFailure(job, "共享行情", ex, failureMessages,
                    completedStages, totalStages, cancelled);
        }
        if (quoteSuccess) {
            persistPostProcessingStage(job, "共享行情完成", completedStages.incrementAndGet(), totalStages, cancelled);
        }

        // 3. 日线按全体用户代码去重，只同步缺失或过期数据。
        persistPostProcessingStage(job, "共享日线", completedStages.get(), totalStages, cancelled);
        boolean barSuccess = true;
        long barStartedAt = System.currentTimeMillis();
        try {
            BarSyncResp barResponse = barDailyService.syncStaleCodes(new ArrayList<>(barCodes));
            int failCount = Objects.nonNull(barResponse.getFailCount()) ? barResponse.getFailCount() : 0;
            if (failCount > 0) {
                throw new BusinessException("共享日线刷新失败 " + failCount + " 项");
            }
            log.info("收盘任务共享日线完成，证券数={}，成功数={}，耗时毫秒={}",
                    barCodes.size(), barResponse.getSuccessCount(), System.currentTimeMillis() - barStartedAt);
        } catch (Exception ex) {
            barSuccess = false;
            recordPostProcessingFailure(job, "共享日线", ex, failureMessages,
                    completedStages, totalStages, cancelled);
        }
        if (barSuccess) {
            persistPostProcessingStage(job, "共享日线完成", completedStages.incrementAndGet(), totalStages, cancelled);
        }

        // 4. 用户循环只生成组合快照，不再触发外部行情和日线请求。
        for (Long userId : userIds) {
            String stage = "用户 " + userId + " · 组合快照";
            persistPostProcessingStage(job, stage, completedStages.get(), totalStages, cancelled);
            boolean snapshotSuccess = true;
            long snapshotStartedAt = System.currentTimeMillis();
            try {
                int snapshotCount = userContext.runAsUser(userId, portfolioService::snapshotAll);
                log.info("收盘任务组合快照完成，用户编号={}，快照数={}，耗时毫秒={}",
                        userId, snapshotCount, System.currentTimeMillis() - snapshotStartedAt);
            } catch (Exception ex) {
                snapshotSuccess = false;
                recordPostProcessingFailure(job, stage, ex, failureMessages,
                        completedStages, totalStages, cancelled);
            }
            if (snapshotSuccess) {
                persistPostProcessingStage(job, stage + "完成",
                        completedStages.incrementAndGet(), totalStages, cancelled);
            }
        }
    }

    private void persistPostProcessingStage(SyncJob job, String stage, int completedStages,
                                            int totalStages, AtomicBoolean cancelled) {
        ensurePostProcessingActive(cancelled);
        int progress = 90 + Math.min(9, completedStages * 9 / Math.max(totalStages, 1));
        job.setProgressPct(progress);
        job.setDoneItems(completedStages);
        job.setTotalItems(totalStages);
        job.setMessage("收盘后处理：" + stage);
        appendLog(job, "[收盘后处理] 阶段=" + stage + "，进度=" + progress + "%\n");
        syncJobMapper.updateById(job);
    }

    private void ensurePostProcessingActive(AtomicBoolean cancelled) {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new BusinessException("收盘后处理已取消");
        }
    }

    private void recordPostProcessingFailure(SyncJob job, String stage, Exception ex,
                                             List<String> failureMessages, AtomicInteger completedStages,
                                             int totalStages, AtomicBoolean cancelled) {
        String failureMessage = stage + "：" + errorMessage(ex);
        failureMessages.add(failureMessage);
        appendLog(job, "[错误] 收盘后处理失败：" + failureMessage + "\n");
        log.warn("收盘任务后处理失败，阶段={}，原因={}", stage, errorMessage(ex));
        persistPostProcessingStage(job, stage + "失败，继续后续任务",
                completedStages.incrementAndGet(), totalStages, cancelled);
    }

    private String errorMessage(Exception ex) {
        return StringUtils.isNotBlank(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private int resultCount(Map<String, Object> result, String key) {
        if (Objects.isNull(result)) {
            throw new BusinessException("后处理返回结果为空");
        }
        Object value = result.get(key);
        return value instanceof Number count ? count.intValue() : 0;
    }

    private boolean isPartialScriptResult(String logText) {
        if (StringUtils.isBlank(logText)) {
            return false;
        }
        String[] lines = logText.split("\\n");
        for (String line : lines) {
            Matcher matcher = PARTIAL_COUNT_PATTERN.matcher(line);
            if (matcher.find() && Integer.parseInt(matcher.group(1)) > 0
                    && Integer.parseInt(matcher.group(2)) > 0) {
                return true;
            }
        }
        return false;
    }

    private void updateProgressFromLine(SyncJob job, String line, long lineNo) {
        if (StringUtils.isBlank(line)) {
            return;
        }
        if ("NIGHTLY_REPAIR".equalsIgnoreCase(job.getTaskType())
                && !line.contains("[NIGHTLY_REPAIR] 步骤")
                && !line.contains("[NIGHTLY_REPAIR] step")) {
            return;
        }
        Matcher step = STEP_PATTERN.matcher(line);
        if (!step.find()) {
            step = SCRIPT_STEP_PATTERN.matcher(line);
            if (!step.find()) {
                step = null;
            }
        }
        if (Objects.nonNull(step)) {
            int done = Integer.parseInt(step.group(1));
            int total = Integer.parseInt(step.group(2));
            job.setDoneItems(done);
            job.setTotalItems(total);
            if (total > 0) {
                job.setProgressPct(Math.min(99, done * 100 / total));
            }
            if (line.contains("CLOSE_BUNDLE") || line.contains("步骤")
                    || line.toLowerCase(Locale.ROOT).contains("step")) {
                job.setMessage(trimMessage(line));
            }
            return;
        }
        Matcher pct = PCT_PATTERN.matcher(line);
        if (pct.find()) {
            int p = Integer.parseInt(pct.group(1));
            if (p >= 0 && p <= 100) {
                job.setProgressPct(Math.min(99, p));
            }
        } else if (Objects.isNull(job.getProgressPct()) || job.getProgressPct() < 5) {
            // 无明确进度时缓慢爬升，避免一直 0
            job.setProgressPct((int) Math.min(90, 5 + lineNo / 20));
        }
    }

    private String trimMessage(String line) {
        String text = line.trim();
        if (text.length() > 180) {
            return text.substring(0, 180);
        }
        return text;
    }

    private boolean enrichProgressFromFile(SyncJob job) {
        SyncTaskSpec spec;
        try {
            spec = syncTaskRegistry.require(job.getTaskType());
        } catch (Exception ex) {
            return false;
        }
        if (StringUtils.isBlank(spec.getProgressFile())) {
            return false;
        }
        Path dir = resolveScriptDir();
        Path file = dir.resolve(spec.getProgressFile());
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(file.toFile());
            if ("A_SHARE_BARS".equals(spec.getTaskType()) && root.isObject()) {
                int total = root.size();
                int done = 0;
                var fields = root.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    JsonNode node = entry.getValue();
                    if (node.has("ok") && node.get("ok").asBoolean(false)) {
                        done++;
                    } else if (node.has("last_date") || node.has("status")) {
                        done++;
                    }
                }
                job.setTotalItems(total);
                job.setDoneItems(done);
                if (total > 0) {
                    job.setProgressPct(Math.min(99, done * 100 / total));
                }
                return true;
            } else if ("FUNDAMENTALS".equals(spec.getTaskType()) && root.isObject()) {
                int done = 0;
                int buckets = 0;
                var modes = root.fields();
                while (modes.hasNext()) {
                    var modeEntry = modes.next();
                    if (!modeEntry.getValue().isObject()) {
                        continue;
                    }
                    buckets++;
                    done += modeEntry.getValue().size();
                }
                job.setDoneItems(done);
                job.setTotalItems(buckets > 0 ? done : null);
                if (done > 0) {
                    job.setProgressPct(Math.min(99, 10 + Math.min(80, done / 5)));
                }
                return true;
            }
        } catch (Exception ex) {
            log.debug("读取进度文件失败，文件路径={}，异常={}", file, ex.getMessage());
        }
        return false;
    }

    private Path resolveScriptDir() {
        List<Path> candidates = new ArrayList<>();
        if (StringUtils.isNotBlank(scriptDirConfig)) {
            candidates.add(Paths.get(scriptDirConfig.trim()));
        }
        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        for (Path start : List.of(userDir, cwd)) {
            Path cursor = start;
            for (int i = 0; i < 5 && Objects.nonNull(cursor); i++) {
                candidates.add(cursor.resolve("scripts/market_data"));
                cursor = cursor.getParent();
            }
        }
        for (Path path : candidates) {
            if (Objects.isNull(path)) {
                continue;
            }
            try {
                Path normalized = path.toAbsolutePath().normalize();
                if (Files.isDirectory(normalized) && Files.isRegularFile(normalized.resolve("sync_hot.py"))) {
                    return normalized;
                }
            } catch (Exception ignored) {
                // next
            }
        }
        throw new BusinessException("未找到 scripts/market_data，请配置 apex.sync.script-dir");
    }

    private void cleanup(Long jobId) {
        runningProcesses.remove(jobId);
        runningFutures.remove(jobId);
        cancelFlags.remove(jobId);
        String leaseKey = runningLeaseKeys.remove(jobId);
        String leaseOwner = runningLeaseOwners.remove(jobId);
        if (Objects.nonNull(syncJobLeaseService)) {
            syncJobLeaseService.release(leaseKey, leaseOwner);
        }
    }

    private SyncJobResp toResp(SyncJob job) {
        return toResp(job, true);
    }

    private SyncJobResp toResp(SyncJob job, boolean includeLog) {
        return SyncJobResp.builder()
                .id(job.getId())
                .taskType(job.getTaskType())
                .taskName(job.getTaskName())
                .status(job.getStatus())
                .paramsJson(job.getParamsJson())
                .progressPct(job.getProgressPct())
                .doneItems(job.getDoneItems())
                .totalItems(job.getTotalItems())
                .message(job.getMessage())
                .logTail(includeLog ? job.getLogTail() : null)
                .exitCode(job.getExitCode())
                .pid(job.getPid())
                .startedAt(job.getStartedAt())
                .finishedAt(job.getFinishedAt())
                .build();
    }

    private void appendLog(SyncJob job, String text) {
        String cur = StringUtils.isBlank(job.getLogTail()) ? "" : job.getLogTail();
        job.setLogTail(trimLog(cur + text));
    }

    private void appendLine(StringBuilder buf, String line) {
        buf.append(line).append('\n');
        if (buf.length() > LOG_MAX) {
            buf.delete(0, buf.length() - LOG_MAX);
        }
    }

    private String trimLog(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        return text.length() > LOG_MAX ? text.substring(text.length() - LOG_MAX) : text;
    }

    private String clip(String text, int max) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String t = text.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private Charset detectCharset() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return Charset.forName("GBK");
        }
        return StandardCharsets.UTF_8;
    }
}
