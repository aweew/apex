package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.config.ScriptDatabaseEnvironment;
import com.awe.apex.quant.domain.dto.HotConfluenceItem;
import com.awe.apex.quant.domain.dto.HotOverviewResp;
import com.awe.apex.quant.domain.dto.HotRefreshResp;
import com.awe.apex.quant.domain.entity.MarketHot;
import com.awe.apex.quant.mapper.MarketHotMapper;
import com.awe.apex.quant.service.IHotService;
import com.awe.apex.quant.util.PythonCommandResolver;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 多平台热点服务
 */
@Slf4j
@Service
public class HotServiceImpl implements IHotService {

    @Resource
    private MarketHotMapper marketHotMapper;

    @Resource
    private ScriptDatabaseEnvironment scriptDatabaseEnvironment;

    @Value("${apex.hot.python-cmd:python}")
    private String pythonCmd;

    @Value("${apex.hot.script-path:}")
    private String scriptPathConfig;

    /**
     * 热点总览（各源最新快照 + 共振）
     *
     * @param limit 每源条数
     * @return 总览
     */
    @Override
    public HotOverviewResp overview(Integer limit) {
        int size = Objects.isNull(limit) || limit <= 0 ? 40 : Math.min(limit, 100);
        // 共振用更大样本，避免展示条数偏小时漏掉多源交集
        int confluenceSize = Math.max(size, 50);
        Map<String, LocalDateTime> times = new LinkedHashMap<>();
        List<MarketHot> eastmoneyFull = latestOf("eastmoney", confluenceSize, times);
        List<MarketHot> xueqiuFull = latestOf("xueqiu", confluenceSize, times);
        List<MarketHot> baiduFull = latestOf("baidu", confluenceSize, times);
        List<HotConfluenceItem> confluence = buildConfluence(List.of(eastmoneyFull, xueqiuFull, baiduFull));
        List<MarketHot> eastmoney = trimList(eastmoneyFull, size);
        List<MarketHot> xueqiu = trimList(xueqiuFull, size);
        List<MarketHot> baidu = trimList(baiduFull, size);
        String message = "东财 " + eastmoney.size() + " · 雪球 " + xueqiu.size()
                + " · 百度 " + baidu.size() + " · 共振 " + confluence.size();
        return HotOverviewResp.builder()
                .snapshotTimes(times)
                .eastmoney(eastmoney)
                .xueqiu(xueqiu)
                .baidu(baidu)
                .confluence(confluence)
                .message(message)
                .build();
    }

    /**
     * 按来源查询最新快照
     *
     * @param source 来源
     * @param limit  条数
     * @return 列表
     */
    @Override
    public List<MarketHot> listBySource(String source, Integer limit) {
        String src = StringUtils.isBlank(source) ? "eastmoney" : source.trim().toLowerCase();
        int size = Objects.isNull(limit) || limit <= 0 ? 40 : Math.min(limit, 100);
        return latestOf(src, size, new HashMap<>());
    }

    /**
     * 调用脚本刷新热点
     *
     * @param sources 来源，逗号分隔可空
     * @param limit   每源条数
     * @return 结果
     */
    @Override
    public HotRefreshResp refresh(String sources, Integer limit) {
        Path script = resolveScript();
        if (Objects.isNull(script) || !Files.isRegularFile(script)) {
            throw new BusinessException("未找到热点同步脚本 sync_hot.py，请配置 apex.hot.script-path 或确认仓库 scripts/market_data");
        }
        String src = StringUtils.isNotBlank(sources) ? sources.trim() : "eastmoney,xueqiu,baidu";
        int size = Objects.isNull(limit) || limit <= 0 ? 50 : Math.min(limit, 100);
        List<String> command = new ArrayList<>();
        command.add(PythonCommandResolver.resolve(pythonCmd));
        command.add("-u");
        command.add(script.toAbsolutePath().toString());
        command.add("--sources");
        command.add(src);
        command.add("--limit");
        command.add(String.valueOf(size));

        StringBuilder output = new StringBuilder();
        int exit = -1;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(script.getParent().toFile());
            pb.redirectErrorStream(true);
            scriptDatabaseEnvironment.apply(pb.environment());
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), detectCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                    if (output.length() > 8000) {
                        break;
                    }
                }
            }
            boolean finished = process.waitFor(240, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("热点同步超时（>240s），请稍后重试或命令行手动跑 sync_hot.py");
            }
            exit = process.exitValue();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("热点同步失败 script={}, err={}", script, ex.getMessage());
            throw new BusinessException("热点同步失败: " + ex.getMessage());
        }
        if (exit != 0) {
            throw new BusinessException("热点同步脚本退出码 " + exit + "：" + trimOut(output.toString()));
        }
        HotOverviewResp overview = overview(size);
        return HotRefreshResp.builder()
                .exitCode(exit)
                .log(trimOut(output.toString()))
                .overview(overview)
                .message("刷新完成 · " + overview.getMessage())
                .build();
    }

    /**
     * 最新多源共振（code -> 条目），供决策/今日关注加分
     *
     * @param limit 每源条数
     * @return 共振映射
     */
    @Override
    public Map<String, HotConfluenceItem> confluenceMap(Integer limit) {
        HotOverviewResp overview = overview(limit);
        Map<String, HotConfluenceItem> map = new HashMap<>();
        if (CollUtil.isEmpty(overview.getConfluence())) {
            return map;
        }
        for (HotConfluenceItem item : overview.getConfluence()) {
            if (StringUtils.isNotBlank(item.getCode())) {
                map.put(item.getCode(), item);
            }
        }
        return map;
    }

    private List<MarketHot> latestOf(String source, int limit, Map<String, LocalDateTime> times) {
        MarketHot latest = marketHotMapper.selectOne(Wrappers.<MarketHot>lambdaQuery()
                .eq(MarketHot::getSource, source)
                .orderByDesc(MarketHot::getSnapshotTime)
                .last("LIMIT 1"));
        if (Objects.isNull(latest) || Objects.isNull(latest.getSnapshotTime())) {
            times.put(source, null);
            return List.of();
        }
        times.put(source, latest.getSnapshotTime());
        return marketHotMapper.selectList(Wrappers.<MarketHot>lambdaQuery()
                .eq(MarketHot::getSource, source)
                .eq(MarketHot::getSnapshotTime, latest.getSnapshotTime())
                .orderByAsc(MarketHot::getRankNo)
                .last("LIMIT " + limit));
    }

    private List<HotConfluenceItem> buildConfluence(List<List<MarketHot>> sourceLists) {
        Map<String, HotConfluenceItem> map = new LinkedHashMap<>();
        Map<String, Set<String>> sourceSet = new HashMap<>();
        for (List<MarketHot> list : sourceLists) {
            if (CollUtil.isEmpty(list)) {
                continue;
            }
            for (MarketHot hot : list) {
                if (StringUtils.isBlank(hot.getCode())) {
                    continue;
                }
                String code = hot.getCode();
                sourceSet.computeIfAbsent(code, k -> new LinkedHashSet<>()).add(hot.getSource());
                HotConfluenceItem item = map.get(code);
                if (Objects.isNull(item)) {
                    item = HotConfluenceItem.builder()
                            .code(code)
                            .name(hot.getName())
                            .bestRank(hot.getRankNo())
                            .pctChg(hot.getPctChg())
                            .price(hot.getPrice())
                            .sources(new ArrayList<>())
                            .sourceCount(0)
                            .build();
                    map.put(code, item);
                } else {
                    if (StringUtils.isBlank(item.getName()) && StringUtils.isNotBlank(hot.getName())) {
                        item.setName(hot.getName());
                    }
                    if (Objects.nonNull(hot.getRankNo())
                            && (Objects.isNull(item.getBestRank()) || hot.getRankNo() < item.getBestRank())) {
                        item.setBestRank(hot.getRankNo());
                    }
                    if (Objects.isNull(item.getPctChg()) && Objects.nonNull(hot.getPctChg())) {
                        item.setPctChg(hot.getPctChg());
                    }
                    if (Objects.isNull(item.getPrice()) && Objects.nonNull(hot.getPrice())) {
                        item.setPrice(hot.getPrice());
                    }
                }
            }
        }
        List<HotConfluenceItem> result = new ArrayList<>();
        for (Map.Entry<String, HotConfluenceItem> entry : map.entrySet()) {
            Set<String> sources = sourceSet.get(entry.getKey());
            if (Objects.isNull(sources) || sources.size() < 2) {
                continue;
            }
            HotConfluenceItem item = entry.getValue();
            List<String> srcList = new ArrayList<>(sources);
            srcList.sort(String::compareTo);
            item.setSources(srcList);
            item.setSourceCount(srcList.size());
            result.add(item);
        }
        result.sort(Comparator
                .comparing(HotConfluenceItem::getSourceCount, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(HotConfluenceItem::getBestRank, Comparator.nullsLast(Comparator.naturalOrder())));
        if (result.size() > 30) {
            return result.subList(0, 30);
        }
        return result;
    }

    private Path resolveScript() {
        List<Path> candidates = new ArrayList<>();
        if (StringUtils.isNotBlank(scriptPathConfig)) {
            candidates.add(Paths.get(scriptPathConfig.trim()));
        }
        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        // 从当前目录向上最多 5 级查找仓库根下的脚本
        for (Path start : List.of(userDir, cwd)) {
            Path cursor = start;
            for (int i = 0; i < 5 && Objects.nonNull(cursor); i++) {
                candidates.add(cursor.resolve("scripts/market_data/sync_hot.py"));
                candidates.add(cursor.resolve("sync_hot.py"));
                cursor = cursor.getParent();
            }
        }
        for (Path path : candidates) {
            if (Objects.isNull(path)) {
                continue;
            }
            try {
                Path normalized = path.toAbsolutePath().normalize();
                if (Files.isRegularFile(normalized)) {
                    log.info("热点脚本定位成功 path={}", normalized);
                    return normalized;
                }
            } catch (Exception ignored) {
                // 下一候选
            }
        }
        log.warn("热点脚本未找到 user.dir={} cwd={} config={}", userDir, cwd, scriptPathConfig);
        return null;
    }

    private Charset detectCharset() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return Charset.forName("GBK");
        }
        return StandardCharsets.UTF_8;
    }

    private List<MarketHot> trimList(List<MarketHot> list, int size) {
        if (CollUtil.isEmpty(list) || list.size() <= size) {
            return list;
        }
        return list.subList(0, size);
    }

    private String trimOut(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String t = text.trim();
        return t.length() > 2000 ? t.substring(0, 2000) : t;
    }
}
