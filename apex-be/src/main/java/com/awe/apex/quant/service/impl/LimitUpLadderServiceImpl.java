package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.LimitUpEffectResp;
import com.awe.apex.quant.domain.dto.LimitUpLadderResp;
import com.awe.apex.quant.domain.dto.LimitUpRefreshResp;
import com.awe.apex.quant.domain.dto.LimitUpStockItem;
import com.awe.apex.quant.domain.dto.LimitUpThemeStat;
import com.awe.apex.quant.domain.dto.LimitUpTier;
import com.awe.apex.quant.domain.entity.LimitUpPool;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.LimitUpPoolMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.service.ILimitUpLadderService;
import com.awe.apex.quant.util.ProcessIoUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 连板天梯服务
 */
@Slf4j
@Service
public class LimitUpLadderServiceImpl implements ILimitUpLadderService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String[] PROMOTE_CN = {
            "", "首板", "一进二", "二进三", "三进四", "四进五", "五进六",
            "六进七", "七进八", "八进九", "九进十"
    };

    @Resource
    private LimitUpPoolMapper limitUpPoolMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Value("${apex.hot.python-cmd:python}")
    private String pythonCmd;

    @Value("${apex.limit-up.script-path:}")
    private String scriptPathConfig;

    /**
     * 查询连板天梯
     *
     * @param tradeDate 交易日可空
     * @return 天梯
     */
    @Override
    public LimitUpLadderResp ladder(String tradeDate) {
        List<LocalDate> available = listAvailableDates();
        LocalDate resolved = resolveTradeDate(tradeDate, available);
        if (Objects.isNull(resolved)) {
            return LimitUpLadderResp.builder()
                    .availableDates(available)
                    .totalCount(0)
                    .maxLianban(0)
                    .themes(List.of())
                    .tiers(List.of())
                    .message("暂无涨停池数据，请先刷新")
                    .build();
        }
        List<LimitUpPool> today = limitUpPoolMapper.selectList(Wrappers.<LimitUpPool>lambdaQuery()
                .eq(LimitUpPool::getTradeDate, resolved)
                .orderByDesc(LimitUpPool::getLianban)
                .orderByAsc(LimitUpPool::getFirstSealTime)
                .orderByAsc(LimitUpPool::getCode));
        LocalDate prevDate = findPrevDate(resolved, available);
        Map<String, Integer> prevLianban = loadPrevLianban(prevDate);

        Map<Integer, List<LimitUpPool>> byTier = new LinkedHashMap<>();
        int maxLb = 0;
        LocalDateTime syncedAt = null;
        for (LimitUpPool row : today) {
            int lb = Objects.nonNull(row.getLianban()) ? row.getLianban() : 1;
            maxLb = Math.max(maxLb, lb);
            byTier.computeIfAbsent(lb, k -> new ArrayList<>()).add(row);
            if (Objects.isNull(syncedAt) && Objects.nonNull(row.getSyncedAt())) {
                syncedAt = row.getSyncedAt();
            }
        }

        List<Integer> tiersDesc = byTier.keySet().stream()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        List<LimitUpTier> tiers = new ArrayList<>();
        for (Integer lb : tiersDesc) {
            List<LimitUpPool> stocks = byTier.get(lb);
            BigDecimal rate = null;
            String promoteLabel = null;
            if (lb >= 2) {
                promoteLabel = promoteLabel(lb);
                rate = calcPromoteRate(lb, prevLianban, today);
            }
            List<LimitUpStockItem> items = new ArrayList<>();
            for (LimitUpPool row : stocks) {
                items.add(toStockItem(row));
            }
            tiers.add(LimitUpTier.builder()
                    .lianban(lb)
                    .title(lb <= 1 ? "首板" : lb + "板")
                    .promoteLabel(promoteLabel)
                    .promoteRate(rate)
                    .count(items.size())
                    .stocks(items)
                    .build());
        }

        List<LimitUpThemeStat> themes = buildThemes(today);
        LimitUpEffectResp effect = buildEffect(prevDate, prevLianban, today);
        return LimitUpLadderResp.builder()
                .tradeDate(resolved)
                .availableDates(available)
                .totalCount(today.size())
                .maxLianban(maxLb)
                .themes(themes)
                .tiers(tiers)
                .syncedAt(syncedAt)
                .effect(effect)
                .message(resolved + " 涨停 " + today.size() + " 家 · 最高 " + maxLb + " 板"
                        + (Objects.nonNull(effect) && Objects.nonNull(effect.getPromoteRate())
                        ? (" · 晋级率 " + effect.getPromoteRate() + "%") : ""))
                .build();
    }

    /**
     * 刷新涨停池并返回天梯
     *
     * @param tradeDate 交易日可空
     * @return 结果
     */
    @Override
    public LimitUpRefreshResp refresh(String tradeDate) {
        Path script = resolveScript();
        if (Objects.isNull(script) || !Files.isRegularFile(script)) {
            throw new BusinessException("未找到涨停同步脚本 sync_limit_up.py");
        }
        LocalDate day = parseDateOrNull(tradeDate);
        if (Objects.isNull(day)) {
            day = LocalDate.now();
        }
        List<String> command = new ArrayList<>();
        command.add(pythonCmd);
        command.add("-u");
        command.add(script.toAbsolutePath().toString());
        command.add("--date");
        command.add(day.format(DAY));
        command.add("--with-prev");
        String logText = runScript(command, 180);
        LimitUpLadderResp ladder = ladder(day.toString());
        return LimitUpRefreshResp.builder()
                .message("涨停池已刷新")
                .log(logText)
                .ladder(ladder)
                .build();
    }

    private List<LimitUpThemeStat> buildThemes(List<LimitUpPool> today) {
        Map<String, LimitUpThemeStat> map = new HashMap<>();
        for (LimitUpPool row : today) {
            String theme = StringUtils.isNotBlank(row.getTheme()) ? row.getTheme().trim()
                    : (StringUtils.isNotBlank(row.getIndustry()) ? row.getIndustry().trim() : null);
            if (StringUtils.isBlank(theme)) {
                continue;
            }
            LimitUpThemeStat stat = map.computeIfAbsent(theme, t -> LimitUpThemeStat.builder()
                    .theme(t)
                    .count(0)
                    .maxLianban(0)
                    .build());
            stat.setCount(stat.getCount() + 1);
            int lb = Objects.nonNull(row.getLianban()) ? row.getLianban() : 1;
            if (lb > stat.getMaxLianban()) {
                stat.setMaxLianban(lb);
            }
        }
        return map.values().stream()
                .sorted(Comparator.comparing(LimitUpThemeStat::getCount).reversed()
                        .thenComparing(LimitUpThemeStat::getMaxLianban, Comparator.reverseOrder()))
                .limit(12)
                .collect(Collectors.toList());
    }

    private BigDecimal calcPromoteRate(int targetLianban, Map<String, Integer> prevLianban,
                                       List<LimitUpPool> today) {
        if (prevLianban.isEmpty()) {
            return null;
        }
        int base = 0;
        for (Integer lb : prevLianban.values()) {
            if (Objects.nonNull(lb) && lb == targetLianban - 1) {
                base++;
            }
        }
        if (base <= 0) {
            return null;
        }
        Set<String> prevBase = new HashSet<>();
        for (Map.Entry<String, Integer> e : prevLianban.entrySet()) {
            if (Objects.nonNull(e.getValue()) && e.getValue() == targetLianban - 1) {
                prevBase.add(e.getKey());
            }
        }
        int success = 0;
        for (LimitUpPool row : today) {
            if (Objects.nonNull(row.getLianban()) && row.getLianban() == targetLianban
                    && prevBase.contains(row.getCode())) {
                success++;
            }
        }
        return BigDecimal.valueOf(success * 100.0 / base).setScale(1, RoundingMode.HALF_UP);
    }

    private Map<String, Integer> loadPrevLianban(LocalDate prevDate) {
        Map<String, Integer> map = new HashMap<>();
        if (Objects.isNull(prevDate)) {
            return map;
        }
        List<LimitUpPool> prev = limitUpPoolMapper.selectList(Wrappers.<LimitUpPool>lambdaQuery()
                .eq(LimitUpPool::getTradeDate, prevDate)
                .select(LimitUpPool::getCode, LimitUpPool::getLianban));
        for (LimitUpPool row : prev) {
            if (StringUtils.isNotBlank(row.getCode())) {
                map.put(row.getCode(), row.getLianban());
            }
        }
        return map;
    }

    private LimitUpStockItem toStockItem(LimitUpPool row) {
        String theme = StringUtils.isNotBlank(row.getTheme()) ? row.getTheme() : row.getIndustry();
        return LimitUpStockItem.builder()
                .code(row.getCode())
                .name(row.getName())
                .lianban(row.getLianban())
                .pctChg(row.getPctChg())
                .latestPrice(row.getLatestPrice())
                .firstSealTime(formatSealTime(row.getFirstSealTime()))
                .lastSealTime(formatSealTime(row.getLastSealTime()))
                .breakCount(row.getBreakCount())
                .sealAmount(row.getSealAmount())
                .turnoverRate(row.getTurnoverRate())
                .theme(theme)
                .ztStats(row.getZtStats())
                .build();
    }

    private LimitUpEffectResp buildEffect(LocalDate prevDate, Map<String, Integer> prevLianban,
                                          List<LimitUpPool> today) {
        if (Objects.isNull(prevDate) || prevLianban.isEmpty()) {
            return LimitUpEffectResp.builder()
                    .prevCount(0)
                    .message("缺少前一日涨停池，无法统计赚钱效应")
                    .build();
        }
        Map<String, LimitUpPool> todayMap = new HashMap<>();
        for (LimitUpPool row : today) {
            todayMap.put(row.getCode(), row);
        }
        int ok = 0;
        int hold = 0;
        int fail = 0;
        List<String> failNames = new ArrayList<>();
        List<String> prevCodes = new ArrayList<>(prevLianban.keySet());
        BigDecimal pctSum = BigDecimal.ZERO;
        int pctN = 0;
        Map<String, StockBasic> basics = new HashMap<>();
        if (CollUtil.isNotEmpty(prevCodes)) {
            List<StockBasic> list = stockBasicMapper.selectList(Wrappers.<StockBasic>lambdaQuery()
                    .in(StockBasic::getCode, prevCodes));
            for (StockBasic b : list) {
                basics.put(b.getCode(), b);
            }
        }
        for (Map.Entry<String, Integer> e : prevLianban.entrySet()) {
            String code = e.getKey();
            int prevLb = Objects.nonNull(e.getValue()) ? e.getValue() : 1;
            LimitUpPool cur = todayMap.get(code);
            if (Objects.nonNull(cur) && Objects.nonNull(cur.getLianban()) && cur.getLianban() > prevLb) {
                ok++;
            } else if (Objects.nonNull(cur) && Objects.nonNull(cur.getLianban()) && cur.getLianban() >= prevLb) {
                hold++;
            } else {
                fail++;
                StockBasic basic = basics.get(code);
                String name = Objects.nonNull(cur) ? cur.getName()
                        : (Objects.nonNull(basic) ? basic.getName() : code);
                if (failNames.size() < 12) {
                    failNames.add(name);
                }
            }
            StockBasic basic = basics.get(code);
            if (Objects.nonNull(basic) && Objects.nonNull(basic.getPctChg())) {
                pctSum = pctSum.add(basic.getPctChg());
                pctN++;
            } else if (Objects.nonNull(cur) && Objects.nonNull(cur.getPctChg())) {
                pctSum = pctSum.add(cur.getPctChg());
                pctN++;
            }
        }
        int prevCount = prevLianban.size();
        BigDecimal rate = prevCount > 0
                ? BigDecimal.valueOf(ok * 100.0 / prevCount).setScale(1, RoundingMode.HALF_UP)
                : null;
        BigDecimal avg = pctN > 0
                ? pctSum.divide(BigDecimal.valueOf(pctN), 2, RoundingMode.HALF_UP)
                : null;
        return LimitUpEffectResp.builder()
                .prevCount(prevCount)
                .promoteOk(ok)
                .promoteHold(hold)
                .promoteFail(fail)
                .promoteRate(rate)
                .avgNextPct(avg)
                .failNames(failNames)
                .message("昨涨停 " + prevCount + " · 晋级 " + ok + " · 同板 " + hold + " · 断板 " + fail
                        + (Objects.nonNull(avg) ? (" · 今日均涨跌 " + avg + "%") : ""))
                .build();
    }

    private String formatSealTime(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return raw;
        }
        digits = digits.length() >= 6 ? digits.substring(0, 6) : digits;
        if (digits.length() == 4) {
            return digits.substring(0, 2) + ":" + digits.substring(2, 4);
        }
        return digits.substring(0, 2) + ":" + digits.substring(2, 4);
    }

    private String promoteLabel(int lianban) {
        if (lianban >= 2 && lianban < PROMOTE_CN.length) {
            return PROMOTE_CN[lianban];
        }
        if (lianban >= 2) {
            return (lianban - 1) + "进" + lianban;
        }
        return null;
    }

    private List<LocalDate> listAvailableDates() {
        List<LimitUpPool> rows = limitUpPoolMapper.selectList(Wrappers.<LimitUpPool>lambdaQuery()
                .select(LimitUpPool::getTradeDate)
                .orderByDesc(LimitUpPool::getTradeDate)
                .last("LIMIT 500"));
        List<LocalDate> dates = new ArrayList<>();
        Set<LocalDate> seen = new HashSet<>();
        for (LimitUpPool row : rows) {
            if (Objects.nonNull(row.getTradeDate()) && seen.add(row.getTradeDate())) {
                dates.add(row.getTradeDate());
            }
            if (dates.size() >= 60) {
                break;
            }
        }
        return dates;
    }

    private LocalDate resolveTradeDate(String tradeDate, List<LocalDate> available) {
        LocalDate parsed = parseDateOrNull(tradeDate);
        if (Objects.nonNull(parsed)) {
            return parsed;
        }
        if (CollUtil.isNotEmpty(available)) {
            return available.get(0);
        }
        return null;
    }

    private LocalDate findPrevDate(LocalDate current, List<LocalDate> available) {
        if (Objects.isNull(current) || CollUtil.isEmpty(available)) {
            return null;
        }
        for (LocalDate d : available) {
            if (d.isBefore(current)) {
                return d;
            }
        }
        return null;
    }

    private LocalDate parseDateOrNull(String tradeDate) {
        if (StringUtils.isBlank(tradeDate)) {
            return null;
        }
        String text = tradeDate.trim();
        try {
            if (text.contains("-")) {
                return LocalDate.parse(text);
            }
            return LocalDate.parse(text, DAY);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("交易日格式错误: " + tradeDate);
        }
    }

    private Path resolveScript() {
        if (StringUtils.isNotBlank(scriptPathConfig)) {
            return Paths.get(scriptPathConfig.trim());
        }
        Path cwd = Paths.get("").toAbsolutePath();
        List<Path> candidates = List.of(
                cwd.resolve("scripts/market_data/sync_limit_up.py"),
                cwd.resolve("../scripts/market_data/sync_limit_up.py"),
                cwd.resolve("../../scripts/market_data/sync_limit_up.py"),
                Paths.get(System.getProperty("user.dir", "."))
                        .toAbsolutePath().normalize()
                        .resolve("scripts/market_data/sync_limit_up.py")
        );
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return p.normalize();
            }
        }
        return null;
    }

    private String runScript(List<String> command, long timeoutSec) {
        log.info("执行涨停同步: {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Path script = Paths.get(command.get(2));
        if (Objects.nonNull(script.getParent())) {
            pb.directory(script.getParent().toFile());
        }
        try {
            Process process = pb.start();
            Charset charset = Charset.forName("GBK");
            String out = ProcessIoUtils.readAndDrain(process.getInputStream(), charset, 20000);
            boolean finished = ProcessIoUtils.waitOrKill(process, timeoutSec);
            if (!finished) {
                throw new BusinessException("涨停同步超时");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException("涨停同步失败: " + out.trim());
            }
            return StringUtils.isNotBlank(out) ? out.trim() : "ok";
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("涨停同步异常: " + ex.getMessage(), ex);
        }
    }
}
