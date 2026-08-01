package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.NewsItemResp;
import com.awe.apex.quant.domain.dto.NewsOverviewResp;
import com.awe.apex.quant.domain.dto.NewsRefreshResp;
import com.awe.apex.quant.domain.entity.MarketNews;
import com.awe.apex.quant.mapper.MarketNewsMapper;
import com.awe.apex.quant.service.INewsService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 新闻资讯服务实现
 */
@Slf4j
@Service
public class NewsServiceImpl implements INewsService {

    private static final List<String> ALL_SOURCES = List.of("eastmoney", "cls", "ths", "sina", "cctv");

    @Resource
    private MarketNewsMapper marketNewsMapper;

    @Value("${apex.news.python-cmd:${apex.hot.python-cmd:python}}")
    private String pythonCmd;

    @Value("${apex.news.script-path:}")
    private String scriptPathConfig;

    /**
     * 新闻总览
     *
     * @param source  来源
     * @param limit   条数
     * @param keyword 关键词
     * @return 总览
     */
    @Override
    public NewsOverviewResp overview(String source, Integer limit, String keyword) {
        int size = Objects.isNull(limit) || limit <= 0 ? 80 : Math.min(limit, 300);
        Map<String, LocalDateTime> times = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String src : ALL_SOURCES) {
            MarketNews latest = marketNewsMapper.selectOne(Wrappers.<MarketNews>lambdaQuery()
                    .eq(MarketNews::getSource, src)
                    .orderByDesc(MarketNews::getSnapshotTime)
                    .last("LIMIT 1"));
            times.put(src, Objects.nonNull(latest) ? latest.getSnapshotTime() : null);
            Long cnt = marketNewsMapper.selectCount(Wrappers.<MarketNews>lambdaQuery()
                    .eq(MarketNews::getSource, src)
                    .ge(MarketNews::getPublishedAt, LocalDateTime.now().minusDays(3)));
            counts.put(src, Objects.isNull(cnt) ? 0 : cnt.intValue());
        }

        var query = Wrappers.<MarketNews>lambdaQuery()
                .orderByDesc(MarketNews::getPublishedAt)
                .orderByDesc(MarketNews::getId)
                .last("LIMIT " + size);
        if (StringUtils.isNotBlank(source) && !"all".equalsIgnoreCase(source.trim())) {
            query.eq(MarketNews::getSource, source.trim().toLowerCase());
        }
        if (StringUtils.isNotBlank(keyword)) {
            String kw = keyword.trim();
            query.and(w -> w.like(MarketNews::getTitle, kw)
                    .or().like(MarketNews::getSummary, kw)
                    .or().like(MarketNews::getContent, kw)
                    .or().like(MarketNews::getRelatedCodes, kw));
        }
        List<MarketNews> rows = marketNewsMapper.selectList(query);
        List<NewsItemResp> items = new ArrayList<>();
        for (MarketNews row : rows) {
            items.add(toItem(row));
        }
        String srcLabel = StringUtils.isBlank(source) || "all".equalsIgnoreCase(source) ? "全部" : source;
        return NewsOverviewResp.builder()
                .snapshotTimes(times)
                .sourceCounts(counts)
                .items(items)
                .message(srcLabel + " " + items.size() + " 条 · 近3日 东财"
                        + counts.getOrDefault("eastmoney", 0)
                        + "/财联社" + counts.getOrDefault("cls", 0)
                        + "/同花顺" + counts.getOrDefault("ths", 0)
                        + "/新浪" + counts.getOrDefault("sina", 0))
                .build();
    }

    /**
     * 按来源列表
     *
     * @param source 来源
     * @param limit  条数
     * @return 列表
     */
    @Override
    public List<NewsItemResp> listBySource(String source, Integer limit) {
        NewsOverviewResp overview = overview(source, limit, null);
        return overview.getItems();
    }

    /**
     * 调用脚本刷新
     *
     * @param sources 来源
     * @param limit   每源条数
     * @return 结果
     */
    @Override
    public NewsRefreshResp refresh(String sources, Integer limit) {
        Path script = resolveScript();
        if (Objects.isNull(script) || !Files.isRegularFile(script)) {
            throw new BusinessException("未找到新闻同步脚本 sync_news.py，请配置 apex.news.script-path");
        }
        String src = StringUtils.isNotBlank(sources) ? sources.trim() : "eastmoney,cls,ths,sina";
        int size = Objects.isNull(limit) || limit <= 0 ? 80 : Math.min(limit, 200);
        List<String> command = new ArrayList<>();
        command.add(pythonCmd);
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
            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("新闻同步超时（>300s），请命令行运行 sync_news.py");
            }
            exit = process.exitValue();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("新闻同步失败 script={}, err={}", script, ex.getMessage());
            throw new BusinessException("新闻同步失败: " + ex.getMessage());
        }
        if (exit != 0) {
            throw new BusinessException("新闻同步脚本退出码 " + exit + "：" + trimOut(output.toString()));
        }
        NewsOverviewResp overview = overview("all", size, null);
        return NewsRefreshResp.builder()
                .success(true)
                .log(trimOut(output.toString()))
                .overview(overview)
                .message("刷新完成 · " + overview.getMessage())
                .build();
    }

    private NewsItemResp toItem(MarketNews row) {
        List<String> codes = new ArrayList<>();
        if (StringUtils.isNotBlank(row.getRelatedCodes())) {
            String[] parts = row.getRelatedCodes().split("[,，\\s]+");
            for (String part : parts) {
                if (StringUtils.isNotBlank(part)) {
                    codes.add(part.trim());
                }
            }
        }
        return NewsItemResp.builder()
                .id(row.getId())
                .source(row.getSource())
                .title(row.getTitle())
                .summary(row.getSummary())
                .content(row.getContent())
                .url(row.getUrl())
                .publishedAt(row.getPublishedAt())
                .relatedCodes(codes)
                .sentiment(row.getSentiment())
                .build();
    }

    private Path resolveScript() {
        if (StringUtils.isNotBlank(scriptPathConfig)) {
            Path configured = Paths.get(scriptPathConfig.trim());
            if (Files.isRegularFile(configured)) {
                return configured;
            }
        }
        Path cwd = Paths.get(System.getProperty("user.dir", "."));
        List<Path> candidates = List.of(
                cwd.resolve("../scripts/market_data/sync_news.py"),
                cwd.resolve("scripts/market_data/sync_news.py"),
                cwd.resolve("../../scripts/market_data/sync_news.py")
        );
        for (Path path : candidates) {
            Path abs = path.normalize().toAbsolutePath();
            if (Files.isRegularFile(abs)) {
                return abs;
            }
        }
        return null;
    }

    private Charset detectCharset() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return Charset.forName("GBK");
        }
        return StandardCharsets.UTF_8;
    }

    private String trimOut(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String t = text.trim();
        return t.length() > 4000 ? t.substring(0, 4000) + "…" : t;
    }
}
