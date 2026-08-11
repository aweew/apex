package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.config.ScriptDatabaseEnvironment;
import com.awe.apex.quant.domain.dto.SectorBoardItem;
import com.awe.apex.quant.domain.dto.SectorBoardResp;
import com.awe.apex.quant.domain.dto.SectorConstituentItem;
import com.awe.apex.quant.domain.dto.SectorConstituentResp;
import com.awe.apex.quant.domain.dto.SectorRefreshResp;
import com.awe.apex.quant.domain.dto.SectorRotationDay;
import com.awe.apex.quant.domain.dto.SectorRotationResp;
import com.awe.apex.quant.domain.entity.SectorBasic;
import com.awe.apex.quant.domain.entity.SectorConstituent;
import com.awe.apex.quant.domain.entity.SectorQuote;
import com.awe.apex.quant.mapper.SectorBasicMapper;
import com.awe.apex.quant.mapper.SectorConstituentMapper;
import com.awe.apex.quant.mapper.SectorQuoteMapper;
import com.awe.apex.quant.decision.MainlineBoardRules;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.ISectorBoardService;
import com.awe.apex.quant.util.ProcessIoUtils;
import com.awe.apex.quant.util.PythonCommandResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 板块看板服务实现
 */
@Slf4j
@Service
public class SectorBoardServiceImpl implements ISectorBoardService {

    private static final Set<String> VALID_TYPES = Set.of("INDUSTRY", "CONCEPT", "THEME");
    private static final long MAINLINE_CACHE_TTL_MS = 2 * 60 * 1000L;

    private final ConcurrentHashMap<String, MainlineCacheEntry> mainlineCache = new ConcurrentHashMap<>();

    @Resource
    private SectorQuoteMapper sectorQuoteMapper;

    @Resource
    private SectorConstituentMapper sectorConstituentMapper;

    @Resource
    private SectorBasicMapper sectorBasicMapper;

    @Resource
    private ScriptDatabaseEnvironment scriptDatabaseEnvironment;

    @Value("${apex.sector.python-cmd:${apex.hot.python-cmd:python}}")
    private String pythonCmd;

    @Value("${apex.sector.script-path:}")
    private String scriptPathConfig;

    /**
     * 板块榜单
     *
     * @param boardType 类型
     * @param sortBy    排序字段
     * @param order     排序方向
     * @param limit     条数
     * @param tradeDate 交易日
     * @return 榜单
     */
    @Override
    public SectorBoardResp board(String boardType, String sortBy, String order, Integer limit, String tradeDate) {
        String type = normalizeType(boardType);
        String sort = normalizeSortBy(sortBy, type);
        boolean asc = "asc".equalsIgnoreCase(StringUtils.isBlank(order) ? "desc" : order.trim());
        int size = Objects.isNull(limit) || limit <= 0 ? 100 : Math.min(limit, 500);
        List<LocalDate> availableDates = listAvailableTradeDates(type, 90);

        LocalDate resolvedDate = resolveTradeDate(tradeDate, availableDates, latestTradeDate(type));
        if (Objects.isNull(resolvedDate)) {
            return SectorBoardResp.builder()
                    .boardType(type)
                    .sortBy(sort)
                    .order(asc ? "asc" : "desc")
                    .availableDates(availableDates)
                    .items(List.of())
                    .inflowUnit("元（前端按亿元展示）")
                    .message("本地暂无板块数据，请点击刷新或运行 sync_sector.py --mode quote")
                    .build();
        }

        LambdaQueryWrapper<SectorQuote> query = Wrappers.<SectorQuote>lambdaQuery()
                .eq(SectorQuote::getBoardType, type)
                .eq(SectorQuote::getTradeDate, resolvedDate);
        if ("netInflow".equals(sort)) {
            if (asc) {
                query.orderByAsc(SectorQuote::getNetInflow);
            } else {
                query.orderByDesc(SectorQuote::getNetInflow);
            }
        } else if ("pctChg3d".equals(sort)) {
            if (asc) {
                query.orderByAsc(SectorQuote::getPctChg3d);
            } else {
                query.orderByDesc(SectorQuote::getPctChg3d);
            }
        } else if ("pctChg5d".equals(sort)) {
            if (asc) {
                query.orderByAsc(SectorQuote::getPctChg5d);
            } else {
                query.orderByDesc(SectorQuote::getPctChg5d);
            }
        } else if ("limitUpCount".equals(sort)) {
            if (asc) {
                query.orderByAsc(SectorQuote::getLimitUpCount);
            } else {
                query.orderByDesc(SectorQuote::getLimitUpCount);
            }
        } else if ("maxLianban".equals(sort)) {
            if (asc) {
                query.orderByAsc(SectorQuote::getMaxLianban);
            } else {
                query.orderByDesc(SectorQuote::getMaxLianban);
            }
        } else {
            if (asc) {
                query.orderByAsc(SectorQuote::getPctChg);
            } else {
                query.orderByDesc(SectorQuote::getPctChg);
            }
        }
        int querySize = "CONCEPT".equals(type) ? Math.min(size * 3, 500) : size;
        query.last("LIMIT " + querySize);
        List<SectorQuote> quotes = sectorQuoteMapper.selectList(query);

        List<SectorBoardItem> items = new ArrayList<>();
        LocalDateTime syncedAt = null;
        for (SectorQuote quote : quotes) {
            if ("CONCEPT".equals(type)
                    && !MainlineBoardRules.isConceptBoard(quote.getBoardType(), quote.getName())) {
                continue;
            }
            if (Objects.isNull(syncedAt) && Objects.nonNull(quote.getSyncedAt())) {
                syncedAt = quote.getSyncedAt();
            }
            items.add(SectorBoardItem.builder()
                    .code(quote.getCode())
                    .name(quote.getName())
                    .boardType(quote.getBoardType())
                    .tradeDate(quote.getTradeDate())
                    .pctChg(quote.getPctChg())
                    .pctChg3d(quote.getPctChg3d())
                    .pctChg5d(quote.getPctChg5d())
                    .netInflow(quote.getNetInflow())
                    .mainNetInflow(quote.getMainNetInflow())
                    .amount(quote.getAmount())
                    .upCount(quote.getUpCount())
                    .downCount(quote.getDownCount())
                    .limitUpCount(quote.getLimitUpCount())
                    .maxLianban(quote.getMaxLianban())
                    .leadStockCode(quote.getLeadStockCode())
                    .leadStockName(quote.getLeadStockName())
                    .leadStockPct(quote.getLeadStockPct())
                    .moveReason(quote.getMoveReason())
                    .syncedAt(quote.getSyncedAt())
                    .build());
            if (items.size() >= size) {
                break;
            }
        }

        String message = items.isEmpty()
                ? "交易日 " + resolvedDate + " 暂无 " + type + " 数据"
                : type + " " + items.size() + " 条 · " + resolvedDate;
        return SectorBoardResp.builder()
                .boardType(type)
                .sortBy(sort)
                .order(asc ? "asc" : "desc")
                .tradeDate(resolvedDate)
                .availableDates(availableDates)
                .syncedAt(syncedAt)
                .items(items)
                .inflowUnit("元（前端按亿元展示）")
                .message(message)
                .build();
    }

    /**
     * 成分股列表
     *
     * @param code      板块代码
     * @param boardType 类型
     * @param sortBy    排序字段
     * @param order     排序方向
     * @param tradeDate 交易日
     * @return 成分股
     */
    @Override
    public SectorConstituentResp constituents(String code, String boardType, String sortBy, String order,
                                             String tradeDate) {
        if (StringUtils.isBlank(code)) {
            throw new BusinessException("板块代码不能为空");
        }
        String type = normalizeType(boardType);
        String sectorCode = code.trim().toUpperCase(Locale.ROOT);
        boolean asc = "asc".equalsIgnoreCase(StringUtils.isBlank(order) ? "desc" : order.trim());

        LocalDate resolvedDate = parseTradeDate(tradeDate);
        if (Objects.isNull(resolvedDate)) {
            resolvedDate = latestConstituentDate(sectorCode, type);
        }
        String sectorName = resolveSectorName(sectorCode, type);
        if (Objects.isNull(resolvedDate)) {
            return SectorConstituentResp.builder()
                    .sectorCode(sectorCode)
                    .sectorName(sectorName)
                    .boardType(type)
                    .items(List.of())
                    .message("暂无成分股，请点击刷新成分")
                    .build();
        }

        List<SectorConstituent> rows = sectorConstituentMapper.selectList(Wrappers.<SectorConstituent>lambdaQuery()
                .eq(SectorConstituent::getSectorCode, sectorCode)
                .eq(SectorConstituent::getBoardType, type)
                .eq(SectorConstituent::getTradeDate, resolvedDate));

        List<SectorConstituentItem> items = new ArrayList<>();
        LocalDateTime syncedAt = null;
        for (SectorConstituent row : rows) {
            if (Objects.isNull(syncedAt) && Objects.nonNull(row.getSyncedAt())) {
                syncedAt = row.getSyncedAt();
            }
            items.add(SectorConstituentItem.builder()
                    .code(row.getStockCode())
                    .name(row.getStockName())
                    .latestPrice(row.getLatestPrice())
                    .pctChg(row.getPctChg())
                    .build());
        }

        // 1. 按请求字段排序（默认涨跌幅）
        String sortKey = StringUtils.isNotBlank(sortBy) ? sortBy.trim() : "pctChg";
        Comparator<SectorConstituentItem> comparator;
        if ("latestPrice".equalsIgnoreCase(sortKey) || "price".equalsIgnoreCase(sortKey)) {
            comparator = Comparator.comparing(
                    SectorConstituentItem::getLatestPrice, Comparator.nullsLast(Comparator.naturalOrder()));
        } else {
            comparator = Comparator.comparing(
                    SectorConstituentItem::getPctChg, Comparator.nullsLast(Comparator.naturalOrder()));
        }
        if (!asc) {
            comparator = comparator.reversed();
        }
        items.sort(comparator);

        return SectorConstituentResp.builder()
                .sectorCode(sectorCode)
                .sectorName(sectorName)
                .boardType(type)
                .tradeDate(resolvedDate)
                .syncedAt(syncedAt)
                .items(items)
                .message(items.isEmpty() ? "交易日 " + resolvedDate + " 暂无成分股" : "成分股 " + items.size() + " 只 · " + resolvedDate)
                .build();
    }

    /**
     * 刷新榜单
     *
     * @param types 类型逗号分隔
     * @return 结果
     */
    @Override
    public SectorRefreshResp refresh(String types) {
        String typeArg = StringUtils.isNotBlank(types) ? types.trim().toUpperCase(Locale.ROOT)
                : "INDUSTRY,CONCEPT,THEME";
        String logText = runScript(List.of("--mode", "quote", "--types", typeArg, "--sleep", "0.25"), 300);
        String firstType = typeArg.split(",")[0].trim();
        SectorBoardResp board = board(firstType, defaultSort(firstType), "desc", 100, null);
        return SectorRefreshResp.builder()
                .exitCode(0)
                .log(logText)
                .board(board)
                .message("刷新完成 · " + board.getMessage())
                .build();
    }

    /**
     * 主线识别
     *
     * @param tradeDate 交易日
     * @param limit     条数
     * @return 主线
     */
    @Override
    public List<SectorBoardItem> mainline(String tradeDate, Integer limit) {
        int size = Objects.isNull(limit) || limit <= 0 ? 8 : Math.min(limit, 30);
        String cacheKey = (StringUtils.isBlank(tradeDate) ? "_" : tradeDate.trim()) + ":" + size;
        MainlineCacheEntry cached = mainlineCache.get(cacheKey);
        if (Objects.nonNull(cached) && cached.expireAt > System.currentTimeMillis()) {
            return cached.items;
        }

        List<SectorBoardItem> pool = new ArrayList<>();
        String type = "CONCEPT";
        LocalDate resolvedDate = resolveTradeDate(tradeDate, null, latestTradeDate(type));
        if (Objects.nonNull(resolvedDate)) {
            // 多取一些再过滤风格和统计标签；排序用净流入优先，避免纯涨幅刷榜
            List<SectorQuote> quotes = sectorQuoteMapper.selectList(Wrappers.<SectorQuote>lambdaQuery()
                    .eq(SectorQuote::getBoardType, type)
                    .eq(SectorQuote::getTradeDate, resolvedDate)
                    .orderByDesc(SectorQuote::getNetInflow)
                    .orderByDesc(SectorQuote::getPctChg3d)
                    .orderByDesc(SectorQuote::getPctChg)
                    .last("LIMIT 60"));
            if (CollUtil.isNotEmpty(quotes)) {
                for (SectorQuote quote : quotes) {
                    if (!MainlineBoardRules.isConceptBoard(quote.getBoardType(), quote.getName())) {
                        continue;
                    }
                    pool.add(toBoardItem(quote));
                }
            }
        }
        if (CollUtil.isEmpty(pool)) {
            return List.of();
        }
        // 资金+持续性为主，当日涨幅降权；纯概念板块按综合分取 Top
        BigDecimal maxPct = absMax(pool, SectorBoardItem::getPctChg);
        BigDecimal max3 = absMax(pool, SectorBoardItem::getPctChg3d);
        BigDecimal max5 = absMax(pool, SectorBoardItem::getPctChg5d);
        BigDecimal maxIn = absMax(pool, this::preferInflow);
        BigDecimal maxAmt = absMax(pool, SectorBoardItem::getAmount);
        BigDecimal maxLu = absMaxInt(pool, SectorBoardItem::getLimitUpCount);
        BigDecimal maxLb = absMaxInt(pool, SectorBoardItem::getMaxLianban);
        List<SectorBoardItem> scored = new ArrayList<>();
        for (SectorBoardItem item : pool) {
            double score = 0;
            score += 0.12 * norm(item.getPctChg(), maxPct);
            score += 0.22 * norm(item.getPctChg3d(), max3);
            score += 0.15 * norm(item.getPctChg5d(), max5);
            score += 0.25 * norm(preferInflow(item), maxIn);
            score += 0.10 * norm(item.getAmount(), maxAmt);
            score += 0.08 * norm(toBig(item.getLimitUpCount()), maxLu);
            score += 0.03 * norm(toBig(item.getMaxLianban()), maxLb);
            score += MainlineBoardRules.typeBonus(item.getBoardType());
            SectorBoardItem copy = SectorBoardItem.builder()
                    .code(item.getCode())
                    .name(item.getName())
                    .boardType(item.getBoardType())
                    .tradeDate(item.getTradeDate())
                    .pctChg(item.getPctChg())
                    .pctChg3d(item.getPctChg3d())
                    .pctChg5d(item.getPctChg5d())
                    .netInflow(item.getNetInflow())
                    .mainNetInflow(item.getMainNetInflow())
                    .amount(item.getAmount())
                    .upCount(item.getUpCount())
                    .downCount(item.getDownCount())
                    .limitUpCount(item.getLimitUpCount())
                    .maxLianban(item.getMaxLianban())
                    .leadStockCode(item.getLeadStockCode())
                    .leadStockName(item.getLeadStockName())
                    .leadStockPct(item.getLeadStockPct())
                    .moveReason(item.getMoveReason())
                    .mainlineScore(BigDecimal.valueOf(score * 100).setScale(1, RoundingMode.HALF_UP))
                    .syncedAt(item.getSyncedAt())
                    .build();
            scored.add(copy);
        }
        scored.sort(Comparator
                .comparing(SectorBoardItem::getMainlineScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(item -> MainlineBoardRules.typeRank(item.getBoardType())));
        List<SectorBoardItem> top = new ArrayList<>();
        Set<String> seenCode = new HashSet<>();
        Set<String> seenName = new HashSet<>();
        for (SectorBoardItem item : scored) {
            String codeKey = item.getBoardType() + ":" + item.getCode();
            String nameKey = StringUtils.isBlank(item.getName()) ? codeKey : item.getName().trim();
            if (!seenCode.add(codeKey) || !seenName.add(nameKey)) {
                continue;
            }
            top.add(item);
            if (top.size() >= size) {
                break;
            }
        }
        mainlineCache.put(cacheKey, new MainlineCacheEntry(top, System.currentTimeMillis() + MAINLINE_CACHE_TTL_MS));
        return top;
    }

    /**
     * 优先取绝对值更大的资金流入（主净流入 vs 板块净流入）
     */
    private BigDecimal preferInflow(SectorBoardItem item) {
        BigDecimal main = item.getMainNetInflow();
        BigDecimal net = item.getNetInflow();
        if (Objects.isNull(main)) {
            return net;
        }
        if (Objects.isNull(net)) {
            return main;
        }
        return main.abs().compareTo(net.abs()) >= 0 ? main : net;
    }

    /**
     * 板块轮动时间轴
     *
     * @param boardType 类型
     * @param days      天数
     * @param topN      每日 Top
     * @return 时间轴
     */
    @Override
    public SectorRotationResp rotation(String boardType, Integer days, Integer topN) {
        String type = StringUtils.isNotBlank(boardType) ? boardType.trim().toUpperCase(Locale.ROOT) : "INDUSTRY";
        if (!VALID_TYPES.contains(type)) {
            type = "INDUSTRY";
        }
        int dayCount = Objects.isNull(days) || days <= 0 ? 10 : Math.min(days, 30);
        int top = Objects.isNull(topN) || topN <= 0 ? 5 : Math.min(topN, 15);

        List<SectorQuote> dateRows = sectorQuoteMapper.selectList(Wrappers.<SectorQuote>lambdaQuery()
                .select(SectorQuote::getTradeDate)
                .eq(SectorQuote::getBoardType, type)
                .isNotNull(SectorQuote::getTradeDate)
                .orderByDesc(SectorQuote::getTradeDate)
                .last("LIMIT " + (dayCount * 120)));
        LinkedHashMap<LocalDate, Boolean> seen = new LinkedHashMap<>();
        for (SectorQuote row : dateRows) {
            LocalDate tradeDate = row.getTradeDate();
            // 跳过周末/节假日误写入的「伪交易日」
            if (Objects.isNull(tradeDate) || !TradingCalendar.isTradingDay(tradeDate)) {
                continue;
            }
            seen.putIfAbsent(tradeDate, Boolean.TRUE);
            if (seen.size() >= dayCount) {
                break;
            }
        }
        List<LocalDate> dates = new ArrayList<>(seen.keySet());
        dates.sort(Comparator.reverseOrder());
        if (CollUtil.isEmpty(dates)) {
            return SectorRotationResp.builder().days(List.of()).message(type + " 暂无轮动数据").build();
        }

        // 一次拉取日期范围内行情，内存分组取 TopN
        List<SectorQuote> allRows = sectorQuoteMapper.selectList(Wrappers.<SectorQuote>lambdaQuery()
                .eq(SectorQuote::getBoardType, type)
                .in(SectorQuote::getTradeDate, dates)
                .orderByDesc(SectorQuote::getPctChg));
        Map<LocalDate, List<SectorQuote>> byDay = new HashMap<>();
        for (SectorQuote row : allRows) {
            if (Objects.isNull(row.getTradeDate())) {
                continue;
            }
            byDay.computeIfAbsent(row.getTradeDate(), k -> new ArrayList<>()).add(row);
        }

        List<SectorRotationDay> daysOut = new ArrayList<>();
        for (LocalDate day : dates) {
            List<SectorQuote> rows = byDay.getOrDefault(day, List.of());
            List<String> tops = new ArrayList<>();
            int n = 0;
            for (SectorQuote row : rows) {
                if (n >= top) {
                    break;
                }
                String pct = Objects.nonNull(row.getPctChg())
                        ? row.getPctChg().setScale(2, RoundingMode.HALF_UP) + "%"
                        : "-";
                String name = StringUtils.isNotBlank(row.getName()) ? row.getName() : row.getCode();
                tops.add(name + " " + pct);
                n++;
            }
            daysOut.add(SectorRotationDay.builder()
                    .tradeDate(day)
                    .tops(tops)
                    .build());
        }
        return SectorRotationResp.builder()
                .days(daysOut)
                .message(type + " 轮动 · 近 " + daysOut.size() + " 日 Top" + top)
                .build();
    }

    private SectorBoardItem toBoardItem(SectorQuote quote) {
        return SectorBoardItem.builder()
                .code(quote.getCode())
                .name(quote.getName())
                .boardType(quote.getBoardType())
                .tradeDate(quote.getTradeDate())
                .pctChg(quote.getPctChg())
                .pctChg3d(quote.getPctChg3d())
                .pctChg5d(quote.getPctChg5d())
                .netInflow(quote.getNetInflow())
                .mainNetInflow(quote.getMainNetInflow())
                .amount(quote.getAmount())
                .upCount(quote.getUpCount())
                .downCount(quote.getDownCount())
                .limitUpCount(quote.getLimitUpCount())
                .maxLianban(quote.getMaxLianban())
                .leadStockCode(quote.getLeadStockCode())
                .leadStockName(quote.getLeadStockName())
                .leadStockPct(quote.getLeadStockPct())
                .moveReason(quote.getMoveReason())
                .syncedAt(quote.getSyncedAt())
                .build();
    }

    private static final class MainlineCacheEntry {
        private final List<SectorBoardItem> items;
        private final long expireAt;

        private MainlineCacheEntry(List<SectorBoardItem> items, long expireAt) {
            this.items = items;
            this.expireAt = expireAt;
        }
    }

    private BigDecimal absMax(List<SectorBoardItem> items, Function<SectorBoardItem, BigDecimal> getter) {
        BigDecimal max = BigDecimal.ZERO;
        for (SectorBoardItem item : items) {
            BigDecimal v = getter.apply(item);
            if (Objects.isNull(v)) {
                continue;
            }
            BigDecimal abs = v.abs();
            if (abs.compareTo(max) > 0) {
                max = abs;
            }
        }
        return max;
    }

    private BigDecimal absMaxInt(List<SectorBoardItem> items, Function<SectorBoardItem, Integer> getter) {
        BigDecimal max = BigDecimal.ZERO;
        for (SectorBoardItem item : items) {
            Integer v = getter.apply(item);
            if (Objects.isNull(v) || v <= 0) {
                continue;
            }
            BigDecimal abs = BigDecimal.valueOf(v);
            if (abs.compareTo(max) > 0) {
                max = abs;
            }
        }
        return max;
    }

    private BigDecimal toBig(Integer value) {
        return Objects.isNull(value) ? null : BigDecimal.valueOf(value);
    }

    private double norm(BigDecimal value, BigDecimal maxAbs) {
        if (Objects.isNull(value) || Objects.isNull(maxAbs) || maxAbs.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        // 只奖励正向（上涨/流入）
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return value.divide(maxAbs, 6, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 刷新成分股
     *
     * @param code      板块代码
     * @param boardType 类型
     * @return 结果
     */
    @Override
    public SectorRefreshResp refreshConstituents(String code, String boardType) {
        if (StringUtils.isBlank(code)) {
            throw new BusinessException("板块代码不能为空");
        }
        String type = normalizeType(boardType);
        String sectorCode = code.trim().toUpperCase(Locale.ROOT);
        String logText = runScript(List.of(
                "--mode", "cons",
                "--types", type,
                "--codes", sectorCode,
                "--sleep", "0.2"
        ), 180);
        SectorConstituentResp cons = constituents(sectorCode, type, "pctChg", "desc", null);
        return SectorRefreshResp.builder()
                .exitCode(0)
                .log(logText)
                .constituents(cons)
                .message("成分刷新完成 · " + cons.getMessage())
                .build();
    }

    private LocalDate latestTradeDate(String boardType) {
        List<SectorQuote> rows = sectorQuoteMapper.selectList(Wrappers.<SectorQuote>lambdaQuery()
                .select(SectorQuote::getTradeDate)
                .eq(SectorQuote::getBoardType, boardType)
                .orderByDesc(SectorQuote::getTradeDate)
                .last("LIMIT 30"));
        if (CollUtil.isEmpty(rows)) {
            return null;
        }
        for (SectorQuote row : rows) {
            if (Objects.nonNull(row.getTradeDate()) && TradingCalendar.isTradingDay(row.getTradeDate())) {
                return row.getTradeDate();
            }
        }
        return null;
    }

    private List<LocalDate> listAvailableTradeDates(String boardType, int limit) {
        int size = Math.max(1, Math.min(limit, 180));
        List<SectorQuote> rows = sectorQuoteMapper.selectList(Wrappers.<SectorQuote>lambdaQuery()
                .select(SectorQuote::getTradeDate)
                .eq(SectorQuote::getBoardType, boardType)
                .orderByDesc(SectorQuote::getTradeDate)
                .last("LIMIT 3000"));
        List<LocalDate> dates = new ArrayList<>();
        if (CollUtil.isEmpty(rows)) {
            return dates;
        }
        for (SectorQuote row : rows) {
            LocalDate tradeDate = row.getTradeDate();
            if (Objects.isNull(tradeDate)
                    || !TradingCalendar.isTradingDay(tradeDate)
                    || dates.contains(tradeDate)) {
                continue;
            }
            dates.add(tradeDate);
            if (dates.size() >= size) {
                break;
            }
        }
        return dates;
    }

    private LocalDate parseTradeDate(String tradeDate) {
        if (StringUtils.isBlank(tradeDate)) {
            return null;
        }
        try {
            return LocalDate.parse(tradeDate.trim().substring(0, Math.min(10, tradeDate.trim().length())));
        } catch (Exception ex) {
            throw new BusinessException("交易日格式错误，应为 yyyy-MM-dd");
        }
    }

    private LocalDate resolveTradeDate(String tradeDate, List<LocalDate> availableDates, LocalDate latest) {
        LocalDate parsed = parseTradeDate(tradeDate);
        if (Objects.nonNull(parsed) && CollUtil.isNotEmpty(availableDates)) {
            for (LocalDate availableDate : availableDates) {
                if (!availableDate.isAfter(parsed)) {
                    return availableDate;
                }
            }
        }
        if (Objects.nonNull(parsed) && CollUtil.isEmpty(availableDates)) {
            return parsed;
        }
        if (CollUtil.isNotEmpty(availableDates)) {
            return availableDates.get(0);
        }
        return latest;
    }

    private LocalDate latestConstituentDate(String sectorCode, String boardType) {
        SectorConstituent latest = sectorConstituentMapper.selectOne(Wrappers.<SectorConstituent>lambdaQuery()
                .eq(SectorConstituent::getSectorCode, sectorCode)
                .eq(SectorConstituent::getBoardType, boardType)
                .orderByDesc(SectorConstituent::getTradeDate)
                .last("LIMIT 1"));
        return Objects.isNull(latest) ? null : latest.getTradeDate();
    }

    private String resolveSectorName(String sectorCode, String boardType) {
        SectorBasic basic = sectorBasicMapper.selectOne(Wrappers.<SectorBasic>lambdaQuery()
                .eq(SectorBasic::getCode, sectorCode)
                .eq(SectorBasic::getBoardType, boardType)
                .last("LIMIT 1"));
        if (Objects.nonNull(basic) && StringUtils.isNotBlank(basic.getName())) {
            return basic.getName();
        }
        SectorQuote quote = sectorQuoteMapper.selectOne(Wrappers.<SectorQuote>lambdaQuery()
                .eq(SectorQuote::getCode, sectorCode)
                .eq(SectorQuote::getBoardType, boardType)
                .orderByDesc(SectorQuote::getTradeDate)
                .last("LIMIT 1"));
        return Objects.nonNull(quote) ? quote.getName() : sectorCode;
    }

    private String normalizeType(String boardType) {
        String type = StringUtils.isBlank(boardType) ? "INDUSTRY" : boardType.trim().toUpperCase(Locale.ROOT);
        if (!VALID_TYPES.contains(type)) {
            throw new BusinessException("不支持的板块类型: " + boardType);
        }
        return type;
    }

    private String normalizeSortBy(String sortBy, String boardType) {
        if (StringUtils.isBlank(sortBy)) {
            return defaultSort(boardType);
        }
        String sort = sortBy.trim();
        if ("netInflow".equalsIgnoreCase(sort) || "net_inflow".equalsIgnoreCase(sort)) {
            return "netInflow";
        }
        if ("pctChg3d".equalsIgnoreCase(sort) || "pct_chg_3d".equalsIgnoreCase(sort)) {
            return "pctChg3d";
        }
        if ("pctChg5d".equalsIgnoreCase(sort) || "pct_chg_5d".equalsIgnoreCase(sort)) {
            return "pctChg5d";
        }
        if ("limitUpCount".equalsIgnoreCase(sort) || "limit_up_count".equalsIgnoreCase(sort)) {
            return "limitUpCount";
        }
        if ("maxLianban".equalsIgnoreCase(sort) || "max_lianban".equalsIgnoreCase(sort)) {
            return "maxLianban";
        }
        return "pctChg";
    }

    private String defaultSort(String boardType) {
        return "THEME".equals(boardType) ? "netInflow" : "pctChg";
    }

    private String runScript(List<String> args, long timeoutSec) {
        Path script = resolveScript();
        if (Objects.isNull(script) || !Files.isRegularFile(script)) {
            throw new BusinessException("未找到板块同步脚本 sync_sector.py，请配置 apex.sector.script-path");
        }
        List<String> command = new ArrayList<>();
        command.add(PythonCommandResolver.resolve(pythonCmd));
        command.add("-u");
        command.add(script.toAbsolutePath().toString());
        if (CollUtil.isNotEmpty(args)) {
            command.addAll(args);
        }

        int exit = -1;
        String outputText = "";
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(script.getParent().toFile());
            pb.redirectErrorStream(true);
            scriptDatabaseEnvironment.apply(pb.environment());
            Process process = pb.start();
            outputText = ProcessIoUtils.readAndDrain(process.getInputStream(), detectCharset(), 10000);
            boolean finished = ProcessIoUtils.waitOrKill(process, timeoutSec);
            if (!finished) {
                throw new BusinessException("板块同步超时（>" + timeoutSec + "s），请命令行运行 sync_sector.py");
            }
            exit = process.exitValue();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("板块同步失败 script={}, err={}", script, ex.getMessage());
            throw new BusinessException("板块同步失败: " + ex.getMessage());
        }
        if (exit != 0) {
            throw new BusinessException("板块同步脚本退出码 " + exit + "：" + trimOut(outputText));
        }
        return trimOut(outputText);
    }

    private Path resolveScript() {
        List<Path> candidates = new ArrayList<>();
        if (StringUtils.isNotBlank(scriptPathConfig)) {
            candidates.add(Paths.get(scriptPathConfig.trim()));
        }
        Path userDir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        for (Path start : List.of(userDir, cwd)) {
            Path cursor = start;
            for (int i = 0; i < 5 && Objects.nonNull(cursor); i++) {
                candidates.add(cursor.resolve("scripts/market_data/sync_sector.py"));
                candidates.add(cursor.resolve("sync_sector.py"));
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
                    log.info("板块脚本定位成功 path={}", normalized);
                    return normalized;
                }
            } catch (Exception ignored) {
                // 下一候选
            }
        }
        log.warn("板块脚本未找到 user.dir={} cwd={} config={}", userDir, cwd, scriptPathConfig);
        return null;
    }

    private Charset detectCharset() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
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
        return t.length() > 2000 ? t.substring(0, 2000) : t;
    }
}
