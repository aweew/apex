package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.IndexBoardResp;
import com.awe.apex.quant.domain.dto.IndexQuoteItem;
import com.awe.apex.quant.domain.dto.IndexRefreshResp;
import com.awe.apex.quant.domain.entity.IndexBar;
import com.awe.apex.quant.mapper.IndexBarMapper;
import com.awe.apex.quant.service.IIndexBoardService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 大盘指数看板实现
 */
@Slf4j
@Service
public class IndexBoardServiceImpl implements IIndexBoardService {

    private static final List<String> CN_CODES = List.of("CN_SH", "CN_SZ", "CN_CYB", "CN_BJ50", "CN_KC50");
    private static final List<String> HK_CODES = List.of("HK_HSI", "HK_HSTECH");
    private static final List<String> ASIA_CODES = List.of("JP_N225", "KR_KOSPI");
    private static final List<String> US_CODES = List.of("US_DJI", "US_IXIC", "US_SPX");

    @Resource
    private IndexBarMapper indexBarMapper;

    @Value("${apex.index.python-cmd:${apex.hot.python-cmd:python}}")
    private String pythonCmd;

    @Value("${apex.index.script-path:}")
    private String scriptPathConfig;

    /**
     * 分市场看板
     *
     * @param sparkDays 迷你走势天数
     * @return 看板
     */
    @Override
    public IndexBoardResp board(Integer sparkDays) {
        int spark = Objects.isNull(sparkDays) || sparkDays <= 0 ? 30 : Math.min(sparkDays, 120);
        List<IndexQuoteItem> cn = buildGroup(CN_CODES, spark);
        List<IndexQuoteItem> hk = buildGroup(HK_CODES, spark);
        List<IndexQuoteItem> asia = buildGroup(ASIA_CODES, spark);
        List<IndexQuoteItem> us = buildGroup(US_CODES, spark);
        int total = cn.size() + hk.size() + asia.size() + us.size();
        String message = total == 0
                ? "本地暂无指数，请点击刷新或运行 sync_index.py --start 20180101"
                : "A股 " + cn.size() + " · 港股 " + hk.size() + " · 日韩 " + asia.size() + " · 美股 " + us.size();
        return IndexBoardResp.builder()
                .cn(cn)
                .hk(hk)
                .asia(asia)
                .us(us)
                .message(message)
                .build();
    }

    /**
     * 指数历史日线
     *
     * @param code  内部代码
     * @param limit 条数
     * @return 日线
     */
    @Override
    public List<IndexBar> bars(String code, Integer limit) {
        if (StringUtils.isBlank(code)) {
            return List.of();
        }
        int size = Objects.isNull(limit) || limit <= 0 ? 120 : Math.min(limit, 2000);
        List<IndexBar> desc = indexBarMapper.selectList(Wrappers.<IndexBar>lambdaQuery()
                .eq(IndexBar::getCode, code.trim().toUpperCase())
                .orderByDesc(IndexBar::getTradeDate)
                .last("LIMIT " + size));
        Collections.reverse(desc);
        return desc;
    }

    /**
     * 刷新指数
     *
     * @param start 起始
     * @return 结果
     */
    @Override
    public IndexRefreshResp refresh(String start) {
        Path script = resolveScript();
        if (Objects.isNull(script) || !Files.isRegularFile(script)) {
            throw new BusinessException("未找到指数同步脚本 sync_index.py，请配置 apex.index.script-path");
        }
        String begin = StringUtils.isNotBlank(start) ? start.trim() : "20180101";
        List<String> command = new ArrayList<>();
        command.add(pythonCmd);
        command.add("-u");
        command.add(script.toAbsolutePath().toString());
        command.add("--start");
        command.add(begin);

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
                    if (output.length() > 10000) {
                        break;
                    }
                }
            }
            boolean finished = process.waitFor(600, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("指数同步超时（>600s），请命令行运行 sync_index.py");
            }
            exit = process.exitValue();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("指数同步失败 script={}, err={}", script, ex.getMessage());
            throw new BusinessException("指数同步失败: " + ex.getMessage());
        }
        if (exit != 0) {
            throw new BusinessException("指数同步脚本退出码 " + exit + "：" + trimOut(output.toString()));
        }
        IndexBoardResp board = board(30);
        return IndexRefreshResp.builder()
                .success(true)
                .log(trimOut(output.toString()))
                .board(board)
                .message("刷新完成 · " + board.getMessage())
                .build();
    }

    private List<IndexQuoteItem> buildGroup(List<String> codes, int sparkDays) {
        List<IndexQuoteItem> items = new ArrayList<>();
        for (String code : codes) {
            IndexQuoteItem item = buildQuote(code, sparkDays);
            if (Objects.nonNull(item)) {
                items.add(item);
            }
        }
        return items;
    }

    private IndexQuoteItem buildQuote(String code, int sparkDays) {
        List<IndexBar> recent = indexBarMapper.selectList(Wrappers.<IndexBar>lambdaQuery()
                .eq(IndexBar::getCode, code)
                .orderByDesc(IndexBar::getTradeDate)
                .last("LIMIT " + Math.max(sparkDays + 1, 5)));
        if (recent.isEmpty()) {
            return null;
        }
        Collections.reverse(recent);
        IndexBar latest = recent.get(recent.size() - 1);
        IndexBar prev = recent.size() >= 2 ? recent.get(recent.size() - 2) : null;

        BigDecimal volume = latest.getVolume();
        BigDecimal prevVolume = Objects.nonNull(prev) ? prev.getVolume() : null;
        BigDecimal volumeChgPct = null;
        String volumeTrend = "无数据";
        if (Objects.nonNull(volume) && volume.signum() > 0
                && Objects.nonNull(prevVolume) && prevVolume.signum() > 0) {
            volumeChgPct = volume.subtract(prevVolume)
                    .divide(prevVolume, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            if (volumeChgPct.compareTo(new BigDecimal("3")) >= 0) {
                volumeTrend = "放量";
            } else if (volumeChgPct.compareTo(new BigDecimal("-3")) <= 0) {
                volumeTrend = "缩量";
            } else {
                volumeTrend = "平量";
            }
        }

        List<BigDecimal> sparkCloses = new ArrayList<>();
        List<BigDecimal> sparkVolumes = new ArrayList<>();
        int from = Math.max(0, recent.size() - sparkDays);
        for (int i = from; i < recent.size(); i++) {
            IndexBar bar = recent.get(i);
            sparkCloses.add(bar.getClosePrice());
            sparkVolumes.add(bar.getVolume());
        }

        return IndexQuoteItem.builder()
                .code(latest.getCode())
                .name(latest.getName())
                .region(latest.getRegion())
                .tradeDate(latest.getTradeDate())
                .closePrice(latest.getClosePrice())
                .pctChg(latest.getPctChg())
                .volume(volume)
                .prevVolume(prevVolume)
                .volumeChgPct(volumeChgPct)
                .volumeTrend(volumeTrend)
                .sparkCloses(sparkCloses)
                .sparkVolumes(sparkVolumes)
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
                cwd.resolve("../scripts/market_data/sync_index.py"),
                cwd.resolve("scripts/market_data/sync_index.py"),
                cwd.resolve("../../scripts/market_data/sync_index.py")
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
        return t.length() > 5000 ? t.substring(0, 5000) + "…" : t;
    }
}
