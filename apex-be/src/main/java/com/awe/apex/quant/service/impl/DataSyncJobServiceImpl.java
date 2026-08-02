package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.SyncJobResp;
import com.awe.apex.quant.domain.dto.SyncOverviewResp;
import com.awe.apex.quant.domain.dto.SyncStartReq;
import com.awe.apex.quant.domain.dto.SyncTaskDefResp;
import com.awe.apex.quant.domain.entity.SyncJob;
import com.awe.apex.quant.mapper.SyncJobMapper;
import com.awe.apex.quant.service.IDataSyncJobService;
import com.awe.apex.quant.sync.SyncTaskHealth;
import com.awe.apex.quant.sync.SyncTaskRegistry;
import com.awe.apex.quant.sync.SyncTaskSpec;
import com.awe.apex.quant.util.ProcessIoUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final int LOG_MAX = 12000;

    @Resource
    private SyncJobMapper syncJobMapper;

    @Resource
    private SyncTaskRegistry syncTaskRegistry;

    @Resource
    private ObjectMapper objectMapper;

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
                SyncJob one = syncJobMapper.selectOne(Wrappers.<SyncJob>lambdaQuery()
                        .eq(SyncJob::getTaskType, spec.getTaskType())
                        .orderByDesc(SyncJob::getId)
                        .last("LIMIT 1"));
                if (Objects.nonNull(one)) {
                    latestByType.put(spec.getTaskType(), toResp(one));
                }
            }
        }

        int running = 0;
        List<SyncTaskDefResp> tasks = new ArrayList<>();
        for (SyncTaskSpec spec : syncTaskRegistry.all()) {
            SyncJobResp latest = latestByType.get(spec.getTaskType());
            boolean isRunning = Objects.nonNull(latest) && "RUNNING".equals(latest.getStatus());
            if (isRunning) {
                running++;
            }
            LocalDateTime lastSuccessAt = null;
            SyncJob success = syncJobMapper.selectOne(Wrappers.<SyncJob>lambdaQuery()
                    .eq(SyncJob::getTaskType, spec.getTaskType())
                    .eq(SyncJob::getStatus, "SUCCESS")
                    .orderByDesc(SyncJob::getFinishedAt)
                    .last("LIMIT 1"));
            if (Objects.nonNull(success)) {
                lastSuccessAt = Objects.nonNull(success.getFinishedAt())
                        ? success.getFinishedAt() : success.getStartedAt();
            }
            boolean latestFailed = Objects.nonNull(latest) && "FAILED".equals(latest.getStatus());
            String health = SyncTaskHealth.resolve(isRunning, lastSuccessAt, latestFailed, LocalDateTime.now());
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
        if (Objects.isNull(req) || StringUtils.isBlank(req.getTaskType())) {
            throw new BusinessException("请指定 taskType");
        }
        SyncTaskSpec spec = syncTaskRegistry.require(req.getTaskType());
        SyncJob running = syncJobMapper.selectOne(Wrappers.<SyncJob>lambdaQuery()
                .eq(SyncJob::getTaskType, spec.getTaskType())
                .eq(SyncJob::getStatus, "RUNNING")
                .orderByDesc(SyncJob::getId)
                .last("LIMIT 1"));
        if (Objects.nonNull(running)) {
            throw new BusinessException(spec.getName() + " 正在运行中（jobId=" + running.getId() + "），请先停止");
        }

        Path scriptDir = resolveScriptDir();
        Path script = scriptDir.resolve(spec.getScriptFile());
        if (!Files.isRegularFile(script)) {
            throw new BusinessException("未找到脚本 " + spec.getScriptFile() + "，请配置 apex.sync.script-dir");
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

        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancelFlags.put(job.getId(), cancelled);
        Future<?> future = executor.submit(() -> runJob(job.getId(), spec, script, scriptArgs, cancelled));
        runningFutures.put(job.getId(), future);
        return getJob(job.getId());
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
        // 运行中补充进度文件
        if ("RUNNING".equals(job.getStatus())) {
            enrichProgressFromFile(job);
            syncJobMapper.updateById(job);
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
        if (!"RUNNING".equals(job.getStatus()) && !"PENDING".equals(job.getStatus())) {
            return toResp(job);
        }
        AtomicBoolean flag = cancelFlags.get(jobId);
        if (Objects.nonNull(flag)) {
            flag.set(true);
        }
        Process process = runningProcesses.get(jobId);
        if (Objects.nonNull(process) && process.isAlive()) {
            process.destroyForcibly();
        }
        Future<?> future = runningFutures.get(jobId);
        if (Objects.nonNull(future)) {
            future.cancel(true);
        }
        job.setStatus("CANCELLED");
        job.setMessage("用户停止");
        job.setFinishedAt(LocalDateTime.now());
        appendLog(job, "\n[stopped by user]\n");
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
            list.add(toResp(row));
        }
        return list;
    }

    @PreDestroy
    public void shutdown() {
        for (Map.Entry<Long, Process> entry : runningProcesses.entrySet()) {
            Process p = entry.getValue();
            if (Objects.nonNull(p) && p.isAlive()) {
                p.destroyForcibly();
            }
        }
        executor.shutdownNow();
    }

    private void runJob(Long jobId, SyncTaskSpec spec, Path script, List<String> scriptArgs, AtomicBoolean cancelled) {
        SyncJob job = syncJobMapper.selectById(jobId);
        if (Objects.isNull(job)) {
            return;
        }
        List<String> command = new ArrayList<>();
        command.add(pythonCmd);
        command.add("-u");
        command.add(script.toAbsolutePath().toString());
        command.addAll(scriptArgs);

        StringBuilder logBuf = new StringBuilder();
        int exit = -1;
        try {
            job.setStatus("RUNNING");
            job.setMessage("运行中");
            job.setStartedAt(LocalDateTime.now());
            syncJobMapper.updateById(job);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(script.getParent().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            runningProcesses.put(jobId, process);
            try {
                job.setPid(process.pid());
            } catch (Exception ignored) {
                // Java 版本/平台可能无 pid
            }
            syncJobMapper.updateById(job);

            // 并行读 stdout 至 EOF（避免 break 后管道堵死），主线程 waitOrKill
            Charset charset = detectCharset();
            Future<String> readFuture = executor.submit(() -> {
                try {
                    return ProcessIoUtils.readAndDrain(process.getInputStream(), charset, LOG_MAX);
                } catch (Exception ex) {
                    log.warn("读同步输出失败 jobId={} err={}", jobId, ex.getMessage());
                    return "";
                }
            });
            long timeoutSec = Math.max(spec.getTimeoutSec(), 60);
            boolean finished = ProcessIoUtils.waitOrKill(process, timeoutSec);
            String outputText;
            try {
                outputText = readFuture.get(Math.min(timeoutSec + 30, 600), TimeUnit.SECONDS);
            } catch (Exception ex) {
                outputText = "";
            }
            if (!finished) {
                throw new BusinessException("同步超时（>" + spec.getTimeoutSec() + "s）");
            }
            exit = process.exitValue();
            if (StringUtils.isNotBlank(outputText)) {
                String[] lines = outputText.split("\n", -1);
                long done = 0;
                for (String line : lines) {
                    if (StringUtils.isBlank(line)) {
                        continue;
                    }
                    done++;
                    appendLine(logBuf, line);
                    if (!cancelled.get()) {
                        updateProgressFromLine(job, line, done);
                    }
                }
            }
            job.setExitCode(exit);
            job.setLogTail(trimLog(logBuf.toString()));
            enrichProgressFromFile(job);
            if (cancelled.get()) {
                job.setStatus("CANCELLED");
                job.setMessage("用户停止");
            } else if (exit == 0) {
                job.setStatus("SUCCESS");
                job.setProgressPct(100);
                job.setMessage("完成");
            } else {
                job.setStatus("FAILED");
                job.setMessage("脚本退出码 " + exit);
            }
            job.setFinishedAt(LocalDateTime.now());
            syncJobMapper.updateById(job);
        } catch (Exception ex) {
            log.warn("同步任务失败 jobId={} type={} err={}", jobId, spec.getTaskType(), ex.getMessage());
            job = syncJobMapper.selectById(jobId);
            if (Objects.nonNull(job) && !"CANCELLED".equals(job.getStatus())) {
                job.setStatus(cancelled.get() ? "CANCELLED" : "FAILED");
                job.setMessage(clip(ex.getMessage(), 400));
                job.setExitCode(exit);
                job.setLogTail(trimLog(logBuf + "\n" + ex.getMessage()));
                job.setFinishedAt(LocalDateTime.now());
                syncJobMapper.updateById(job);
            }
        } finally {
            cleanup(jobId);
        }
    }

    private void updateProgressFromLine(SyncJob job, String line, long lineNo) {
        if (StringUtils.isBlank(line)) {
            return;
        }
        Matcher step = STEP_PATTERN.matcher(line);
        if (step.find()) {
            int done = Integer.parseInt(step.group(1));
            int total = Integer.parseInt(step.group(2));
            job.setDoneItems(done);
            job.setTotalItems(total);
            if (total > 0) {
                job.setProgressPct(Math.min(99, done * 100 / total));
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

    private void enrichProgressFromFile(SyncJob job) {
        SyncTaskSpec spec;
        try {
            spec = syncTaskRegistry.require(job.getTaskType());
        } catch (Exception ex) {
            return;
        }
        if (StringUtils.isBlank(spec.getProgressFile())) {
            return;
        }
        Path dir = resolveScriptDir();
        Path file = dir.resolve(spec.getProgressFile());
        if (!Files.isRegularFile(file)) {
            return;
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
            }
        } catch (Exception ex) {
            log.debug("读进度文件失败 path={} err={}", file, ex.getMessage());
        }
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
    }

    private SyncJobResp toResp(SyncJob job) {
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
                .logTail(job.getLogTail())
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
