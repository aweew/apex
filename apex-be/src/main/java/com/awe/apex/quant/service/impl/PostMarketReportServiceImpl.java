package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.cache.RedisCacheService;
import com.awe.apex.quant.domain.dto.DragonTigerItemResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.PostMarketActiveSeatResp;
import com.awe.apex.quant.domain.dto.PostMarketReportResp;
import com.awe.apex.quant.domain.dto.PostMarketStarStockResp;
import com.awe.apex.quant.domain.dto.SectorBoardItem;
import com.awe.apex.quant.domain.dto.SectorBoardResp;
import com.awe.apex.quant.domain.entity.DragonTigerItem;
import com.awe.apex.quant.domain.entity.LimitUpPool;
import com.awe.apex.quant.domain.entity.MarketBriefingSnapshot;
import com.awe.apex.quant.domain.entity.MarketOpinion;
import com.awe.apex.quant.domain.entity.StockFundFlow;
import com.awe.apex.quant.mapper.DragonTigerItemMapper;
import com.awe.apex.quant.mapper.LimitUpPoolMapper;
import com.awe.apex.quant.mapper.MarketBriefingSnapshotMapper;
import com.awe.apex.quant.mapper.MarketOpinionMapper;
import com.awe.apex.quant.mapper.StockFundFlowMapper;
import com.awe.apex.quant.market.TradingCalendar;
import com.awe.apex.quant.service.IPostMarketReportService;
import com.awe.apex.quant.service.ISectorBoardService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 最新交易日盘后总结服务实现。
 */
@Slf4j
@Service
public class PostMarketReportServiceImpl implements IPostMarketReportService {

    static final String CACHE_KEY = "apex:post-market-report:latest:v1";
    static final Duration CACHE_TTL = Duration.ofDays(2);
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalTime VISIBLE_TIME = LocalTime.of(18, 30);
    private static final LocalTime MARKET_OPEN_TIME = LocalTime.of(9, 30);
    private static final int BOARD_LIMIT = 10;
    private static final int MAINLINE_LIMIT = 8;
    private static final int STAR_STOCK_LIMIT = 12;
    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");
    private static final List<String> REQUIRED_SECTIONS = List.of(
            "收盘结论", "01｜大盘", "02｜板块", "03｜主线", "04｜明星个股", "05｜龙虎榜与知名游资", "06｜风险与次日观察");

    @Resource
    private MarketBriefingSnapshotMapper marketBriefingSnapshotMapper;

    @Resource
    private LimitUpPoolMapper limitUpPoolMapper;

    @Resource
    private StockFundFlowMapper stockFundFlowMapper;

    @Resource
    private DragonTigerItemMapper dragonTigerItemMapper;

    @Resource
    private MarketOpinionMapper marketOpinionMapper;

    @Resource
    private ISectorBoardService sectorBoardService;

    @Resource
    private RedisCacheService redisCacheService;

    @Resource
    private KimiChatClient kimiChatClient;

    /**
     * 读取盘后可见窗口内的最新交易日总结。
     *
     * @param forceRefresh 是否跳过缓存重新生成
     * @return 最新盘后总结，非可见窗口返回 null
     */
    @Override
    public PostMarketReportResp latest(boolean forceRefresh) {
        return latest(forceRefresh, LocalDateTime.now(SHANGHAI_ZONE));
    }

    PostMarketReportResp latest(boolean forceRefresh, LocalDateTime currentTime) {
        if (!isVisibleWindow(currentTime)) {
            return null;
        }
        LocalDate latestTradeDate = TradingCalendar.latestCompletedTradingDay(currentTime);
        if (!forceRefresh) {
            PostMarketReportResp cachedReport = redisCacheService.get(CACHE_KEY, PostMarketReportResp.class);
            if (Objects.nonNull(cachedReport) && latestTradeDate.equals(cachedReport.getTradeDate())) {
                return cachedReport;
            }
        }
        return generate(currentTime);
    }

    /**
     * 使用本地收盘数据生成最新交易日总结。
     *
     * @return 最新盘后总结
     */
    @Override
    public PostMarketReportResp generate() {
        return generate(LocalDateTime.now(SHANGHAI_ZONE));
    }

    PostMarketReportResp generate(LocalDateTime generatedAt) {
        LocalDate tradeDate = TradingCalendar.latestCompletedTradingDay(generatedAt);
        List<String> missingData = new ArrayList<>();

        // 1. 读取严格对应目标交易日的市场和板块快照
        MarketBriefingSnapshot marketSnapshotRow = marketBriefingSnapshotMapper.selectOne(
                Wrappers.<MarketBriefingSnapshot>lambdaQuery()
                        .eq(MarketBriefingSnapshot::getTradeDate, tradeDate)
                        .orderByDesc(MarketBriefingSnapshot::getId)
                        .last("LIMIT 1"));
        MarketBriefingResp marketSnapshot = null;
        if (Objects.nonNull(marketSnapshotRow) && StringUtils.isNotBlank(marketSnapshotRow.getPayloadJson())) {
            try {
                marketSnapshot = JsonUtils.parseObject(marketSnapshotRow.getPayloadJson(), MarketBriefingResp.class);
                if (Objects.nonNull(marketSnapshot) && !tradeDate.equals(marketSnapshot.getAsOf())) {
                    marketSnapshot = null;
                }
            } catch (Exception ex) {
                log.warn("盘后总结市场快照解析失败，交易日={}，原因={}", tradeDate, ex.getMessage());
            }
        }
        if (Objects.isNull(marketSnapshot)) {
            missingData.add("大盘收盘快照");
        }
        List<SectorBoardItem> industryBoards = loadBoards("INDUSTRY", tradeDate);
        List<SectorBoardItem> conceptBoards = loadBoards("CONCEPT", tradeDate);
        List<SectorBoardItem> mainlines = filterBoardsByTradeDate(
                sectorBoardService.mainline(tradeDate.toString(), MAINLINE_LIMIT), tradeDate);
        if (CollUtil.isEmpty(industryBoards) && CollUtil.isEmpty(conceptBoards)) {
            missingData.add("板块行情与资金流");
        }
        if (CollUtil.isEmpty(mainlines)) {
            missingData.add("主线识别");
        }

        // 2. 合并涨停池与主力资金，形成确定性的明星个股
        List<LimitUpPool> limitUpRows = limitUpPoolMapper.selectList(Wrappers.<LimitUpPool>lambdaQuery()
                .eq(LimitUpPool::getTradeDate, tradeDate)
                .orderByDesc(LimitUpPool::getLianban)
                .orderByDesc(LimitUpPool::getSealAmount));
        List<StockFundFlow> stockFundFlowRows = stockFundFlowMapper.selectList(Wrappers.<StockFundFlow>lambdaQuery()
                .eq(StockFundFlow::getTradeDate, tradeDate)
                .orderByDesc(StockFundFlow::getMainNetInflow)
                .last("LIMIT 30"));
        List<PostMarketStarStockResp> starStocks = buildStarStocks(limitUpRows, stockFundFlowRows);
        if (CollUtil.isEmpty(starStocks)) {
            missingData.add("明星个股候选");
        }

        // 3. 龙虎榜和活跃席位都严格限定目标交易日，禁止混入近五日数据
        List<DragonTigerItem> dragonTigerRows = dragonTigerItemMapper.selectList(
                Wrappers.<DragonTigerItem>lambdaQuery()
                        .eq(DragonTigerItem::getTradeDate, tradeDate)
                        .orderByDesc(DragonTigerItem::getNetBuyAmount));
        List<DragonTigerItemResp> dragonTigerItems = buildDragonTigerItems(dragonTigerRows);
        if (CollUtil.isEmpty(dragonTigerItems)) {
            missingData.add("龙虎榜明细");
        }
        LocalDateTime opinionStartTime = tradeDate.atStartOfDay();
        LocalDateTime opinionEndTime = tradeDate.plusDays(1).atStartOfDay();
        List<MarketOpinion> activeSeatRows = marketOpinionMapper.selectList(Wrappers.<MarketOpinion>query()
                .eq("opinion_type", "ACTIVE_SEAT")
                .ge("published_at", opinionStartTime)
                .lt("published_at", opinionEndTime)
                .orderByDesc("published_at")
                .orderByDesc("id"));
        List<PostMarketActiveSeatResp> activeSeats = buildActiveSeats(activeSeatRows);
        if (CollUtil.isEmpty(activeSeats)) {
            missingData.add("活跃席位与知名游资证据");
        }

        // 4. 汇总数据质量并生成证据约束正文
        String dataLevel = resolveDataLevel(marketSnapshot, industryBoards, conceptBoards, missingData);
        String marketStatus = Objects.isNull(marketSnapshot) || StringUtils.isBlank(marketSnapshot.getStance())
                ? "收盘状态待补充" : marketSnapshot.getStance();
        String coreView = buildCoreView(marketSnapshot, mainlines);
        String maxRisk = buildMaxRisk(marketSnapshot);
        PostMarketReportResp context = PostMarketReportResp.builder()
                .reportDate(generatedAt.toLocalDate())
                .tradeDate(tradeDate)
                .generatedAt(generatedAt)
                .dataAsOf(resolveDataAsOf(marketSnapshotRow, industryBoards, conceptBoards, mainlines,
                        limitUpRows, stockFundFlowRows, dragonTigerRows, activeSeatRows))
                .dataLevel(dataLevel)
                .reportSource("RULE")
                .contentLevel("RED".equals(dataLevel) ? "DEGRADED" : "FULL")
                .coreView(coreView)
                .marketStatus(marketStatus)
                .maxRisk(maxRisk)
                .marketSnapshot(marketSnapshot)
                .industryBoards(industryBoards)
                .conceptBoards(conceptBoards)
                .mainlines(Objects.isNull(mainlines) ? List.of() : mainlines)
                .starStocks(starStocks)
                .dragonTigerItems(dragonTigerItems)
                .activeSeats(activeSeats)
                .missingData(missingData)
                .qualityWarnings(buildQualityWarnings(dataLevel, missingData))
                .build();

        String reportSource = "RULE";
        String content = buildRuleReport(context);
        if (kimiChatClient.available()) {
            try {
                String userPrompt = "以下 JSON 是本次盘后总结唯一允许使用的事实与证据上下文：\n"
                        + JsonUtils.toJsonString(context);
                String modelContent = kimiChatClient.chat(loadSystemPrompt(), userPrompt, 2600);
                if (isCompleteReport(modelContent, context)) {
                    content = modelContent.trim();
                    reportSource = "AI";
                } else {
                    log.warn("盘后总结模型输出未通过事实校验，使用规则版，交易日={}", tradeDate);
                }
            } catch (Exception ex) {
                log.warn("盘后总结模型生成失败，使用规则版，交易日={}，原因={}", tradeDate, ex.getMessage());
            }
        }
        PostMarketReportResp report = context.toBuilder()
                .reportSource(reportSource)
                .content(content)
                .build();
        redisCacheService.put(CACHE_KEY, report, CACHE_TTL);
        log.info("盘后总结生成完成，交易日={}，来源={}，数据等级={}，主线数={}，明星股数={}，龙虎榜数={}，席位数={}",
                tradeDate, reportSource, dataLevel, report.getMainlines().size(), starStocks.size(),
                dragonTigerItems.size(), activeSeats.size());
        return report;
    }

    /**
     * 重新组装本地数据并刷新最新盘后总结缓存。
     *
     * @return 最新盘后总结，非可见窗口返回 null
     */
    @Override
    public PostMarketReportResp refresh() {
        LocalDateTime currentTime = LocalDateTime.now(SHANGHAI_ZONE);
        if (!isVisibleWindow(currentTime)) {
            log.info("盘后总结刷新跳过：当前不在最近交易日 18:30 至下一交易日 09:30 可见窗口");
            return null;
        }
        return generate(currentTime);
    }

    static boolean isVisibleWindow(LocalDateTime currentTime) {
        LocalDate latestTradeDate = TradingCalendar.latestCompletedTradingDay(currentTime);
        LocalDateTime visibleStart = latestTradeDate.atTime(VISIBLE_TIME);
        LocalDateTime visibleEnd = TradingCalendar.nextTradingDay(latestTradeDate).atTime(MARKET_OPEN_TIME);
        return !currentTime.isBefore(visibleStart) && currentTime.isBefore(visibleEnd);
    }

    private List<SectorBoardItem> loadBoards(String boardType, LocalDate tradeDate) {
        SectorBoardResp performanceBoard = sectorBoardService.board(
                boardType, "pctChg", "desc", BOARD_LIMIT, tradeDate.toString());
        SectorBoardResp inflowBoard = sectorBoardService.board(
                boardType, "netInflow", "desc", BOARD_LIMIT, tradeDate.toString());
        List<SectorBoardItem> boards = new ArrayList<>();
        appendUniqueBoards(boards, performanceBoard, tradeDate);
        appendUniqueBoards(boards, inflowBoard, tradeDate);
        if (boards.size() > BOARD_LIMIT) {
            return new ArrayList<>(boards.subList(0, BOARD_LIMIT));
        }
        return boards;
    }

    private void appendUniqueBoards(List<SectorBoardItem> boards, SectorBoardResp boardResp, LocalDate tradeDate) {
        if (Objects.isNull(boardResp) || CollUtil.isEmpty(boardResp.getItems())) {
            return;
        }
        for (SectorBoardItem item : boardResp.getItems()) {
            if (Objects.isNull(item) || !tradeDate.equals(item.getTradeDate())) {
                continue;
            }
            boolean exists = false;
            for (SectorBoardItem existing : boards) {
                if (Objects.equals(existing.getBoardType(), item.getBoardType())
                        && Objects.equals(existing.getCode(), item.getCode())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                boards.add(item);
            }
        }
    }

    private List<SectorBoardItem> filterBoardsByTradeDate(List<SectorBoardItem> boards, LocalDate tradeDate) {
        List<SectorBoardItem> matchedBoards = new ArrayList<>();
        if (CollUtil.isEmpty(boards)) {
            return matchedBoards;
        }
        for (SectorBoardItem board : boards) {
            if (Objects.nonNull(board) && tradeDate.equals(board.getTradeDate())) {
                matchedBoards.add(board);
            }
        }
        return matchedBoards;
    }

    private List<PostMarketStarStockResp> buildStarStocks(List<LimitUpPool> limitUpRows,
                                                           List<StockFundFlow> stockFundFlowRows) {
        List<PostMarketStarStockResp> starStocks = new ArrayList<>();
        if (CollUtil.isNotEmpty(limitUpRows)) {
            for (LimitUpPool limitUpRow : limitUpRows) {
                starStocks.add(PostMarketStarStockResp.builder()
                        .code(limitUpRow.getCode())
                        .name(limitUpRow.getName())
                        .pctChg(limitUpRow.getPctChg())
                        .latestPrice(limitUpRow.getLatestPrice())
                        .turnoverRate(limitUpRow.getTurnoverRate())
                        .lianban(limitUpRow.getLianban())
                        .sealAmount(limitUpRow.getSealAmount())
                        .industry(limitUpRow.getIndustry())
                        .theme(limitUpRow.getTheme())
                        .build());
            }
        }
        if (CollUtil.isNotEmpty(stockFundFlowRows)) {
            for (StockFundFlow stockFundFlowRow : stockFundFlowRows) {
                PostMarketStarStockResp starStock = findStarStock(starStocks, stockFundFlowRow.getCode());
                if (Objects.isNull(starStock)) {
                    starStock = PostMarketStarStockResp.builder()
                            .code(stockFundFlowRow.getCode())
                            .name(stockFundFlowRow.getName())
                            .pctChg(stockFundFlowRow.getPctChg())
                            .build();
                    starStocks.add(starStock);
                }
                starStock.setMainNetInflow(stockFundFlowRow.getMainNetInflow());
                starStock.setMainNetInflowPct(stockFundFlowRow.getMainNetInflowPct());
            }
        }
        for (PostMarketStarStockResp starStock : starStocks) {
            List<String> reasons = new ArrayList<>();
            if (Objects.nonNull(starStock.getLianban()) && starStock.getLianban() > 1) {
                reasons.add(starStock.getLianban() + "连板");
            } else if (Objects.nonNull(starStock.getLianban()) && starStock.getLianban() == 1) {
                reasons.add("涨停");
            }
            if (isPositive(starStock.getSealAmount())) {
                reasons.add("封板资金居前");
            }
            if (isPositive(starStock.getMainNetInflow())) {
                reasons.add("主力净流入居前");
            }
            starStock.setReasons(reasons);
        }
        starStocks.sort(Comparator
                .comparing(PostMarketStarStockResp::getLianban,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PostMarketStarStockResp::getMainNetInflow,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PostMarketStarStockResp::getSealAmount,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        if (starStocks.size() > STAR_STOCK_LIMIT) {
            return new ArrayList<>(starStocks.subList(0, STAR_STOCK_LIMIT));
        }
        return starStocks;
    }

    private PostMarketStarStockResp findStarStock(List<PostMarketStarStockResp> starStocks, String code) {
        for (PostMarketStarStockResp starStock : starStocks) {
            if (Objects.equals(starStock.getCode(), code)) {
                return starStock;
            }
        }
        return null;
    }

    private List<DragonTigerItemResp> buildDragonTigerItems(List<DragonTigerItem> rows) {
        List<DragonTigerItemResp> items = new ArrayList<>();
        if (CollUtil.isEmpty(rows)) {
            return items;
        }
        for (DragonTigerItem row : rows) {
            items.add(DragonTigerItemResp.builder()
                    .code(row.getCode())
                    .name(row.getName())
                    .tradeDate(row.getTradeDate())
                    .reason(row.getReason())
                    .closePrice(row.getClosePrice())
                    .pctChg(row.getPctChg())
                    .turnoverRate(row.getTurnoverRate())
                    .netBuyAmount(row.getNetBuyAmount())
                    .buyAmount(row.getBuyAmount())
                    .sellAmount(row.getSellAmount())
                    .amount(row.getAmount())
                    .syncedAt(row.getSyncedAt())
                    .build());
        }
        return items;
    }

    private List<PostMarketActiveSeatResp> buildActiveSeats(List<MarketOpinion> rows) {
        List<PostMarketActiveSeatResp> activeSeats = new ArrayList<>();
        if (CollUtil.isEmpty(rows)) {
            return activeSeats;
        }
        for (MarketOpinion row : rows) {
            activeSeats.add(PostMarketActiveSeatResp.builder()
                    .subjectName(row.getSubjectName())
                    .actorName(row.getActorName())
                    .actorType(row.getActorType())
                    .actorConfidence(row.getActorConfidence())
                    .actorEvidenceUrl(row.getActorEvidenceUrl())
                    .direction(row.getDirection())
                    .relatedCode(row.getRelatedCode())
                    .relatedName(row.getRelatedName())
                    .topic(row.getTopic())
                    .netAmount(row.getNetAmount())
                    .source(row.getSource())
                    .title(row.getTitle())
                    .summary(row.getSummary())
                    .url(row.getUrl())
                    .publishedAt(row.getPublishedAt())
                    .snapshotTime(row.getSnapshotTime())
                    .build());
        }
        return activeSeats;
    }

    private String resolveDataLevel(MarketBriefingResp marketSnapshot, List<SectorBoardItem> industryBoards,
                                    List<SectorBoardItem> conceptBoards, List<String> missingData) {
        if (Objects.isNull(marketSnapshot) && CollUtil.isEmpty(industryBoards) && CollUtil.isEmpty(conceptBoards)) {
            return "RED";
        }
        if (Objects.nonNull(marketSnapshot) && "RED".equals(marketSnapshot.getDataLevel())) {
            return "RED";
        }
        return CollUtil.isEmpty(missingData) ? "GREEN" : "YELLOW";
    }

    private String buildCoreView(MarketBriefingResp marketSnapshot, List<SectorBoardItem> mainlines) {
        if (Objects.isNull(marketSnapshot)) {
            return "收盘数据不足，暂不形成强弱结论。";
        }
        StringBuilder coreView = new StringBuilder();
        coreView.append(StringUtils.isBlank(marketSnapshot.getStance()) ? "市场状态待确认" : marketSnapshot.getStance());
        if (CollUtil.isNotEmpty(mainlines)) {
            coreView.append("，主线关注");
            int count = Math.min(3, mainlines.size());
            for (int index = 0; index < count; index++) {
                if (index > 0) {
                    coreView.append("、");
                }
                coreView.append(mainlines.get(index).getName());
            }
        }
        return coreView.append("。").toString();
    }

    private String buildMaxRisk(MarketBriefingResp marketSnapshot) {
        if (Objects.isNull(marketSnapshot)) {
            return "关键收盘数据尚未齐全。";
        }
        if (Objects.nonNull(marketSnapshot.getLimitDownCount()) && marketSnapshot.getLimitDownCount() >= 10) {
            return "跌停家数偏多，注意高位股亏钱效应扩散。";
        }
        if (Objects.nonNull(marketSnapshot.getBreadthDown()) && Objects.nonNull(marketSnapshot.getBreadthUp())
                && marketSnapshot.getBreadthDown() > marketSnapshot.getBreadthUp()) {
            return "下跌家数多于上涨家数，关注指数与个股体感背离。";
        }
        return "关注主线持续性及次日开盘后的资金承接。";
    }

    private List<String> buildQualityWarnings(String dataLevel, List<String> missingData) {
        List<String> qualityWarnings = new ArrayList<>();
        if (!"GREEN".equals(dataLevel)) {
            qualityWarnings.add("部分收盘数据尚未齐全，结论已按可核验事实降级");
        }
        if (CollUtil.isNotEmpty(missingData)) {
            qualityWarnings.add("待补数据：" + String.join("、", missingData));
        }
        return qualityWarnings;
    }

    private String buildRuleReport(PostMarketReportResp report) {
        StringBuilder content = new StringBuilder();
        content.append("收盘结论\n").append(report.getCoreView()).append("\n\n");
        content.append("01｜大盘\n");
        if (Objects.isNull(report.getMarketSnapshot())) {
            content.append("大盘收盘快照暂未齐全。\n\n");
        } else {
            MarketBriefingResp market = report.getMarketSnapshot();
            content.append("市场状态：").append(valueOrDash(report.getMarketStatus()))
                    .append("；上涨 ").append(valueOrDash(market.getBreadthUp()))
                    .append(" 家，下跌 ").append(valueOrDash(market.getBreadthDown()))
                    .append(" 家；涨停 ").append(valueOrDash(market.getLimitUpCount()))
                    .append(" 家，跌停 ").append(valueOrDash(market.getLimitDownCount())).append(" 家。\n\n");
        }
        content.append("02｜板块\n");
        appendBoardNames(content, "行业", report.getIndustryBoards());
        appendBoardNames(content, "概念", report.getConceptBoards());
        content.append('\n');
        content.append("03｜主线\n");
        appendBoardNames(content, "规则识别", report.getMainlines());
        content.append('\n');
        content.append("04｜明星个股\n");
        if (CollUtil.isEmpty(report.getStarStocks())) {
            content.append("暂无满足条件的明星个股。\n\n");
        } else {
            for (PostMarketStarStockResp starStock : report.getStarStocks()) {
                content.append(starStock.getName()).append("（").append(starStock.getCode()).append("）")
                        .append("：").append(CollUtil.isEmpty(starStock.getReasons())
                                ? "进入当日候选" : String.join("、", starStock.getReasons())).append("。\n");
            }
            content.append('\n');
        }
        content.append("05｜龙虎榜与知名游资\n")
                .append("龙虎榜 ").append(report.getDragonTigerItems().size())
                .append(" 只，活跃席位 ").append(report.getActiveSeats().size()).append(" 条；")
                .append(report.getActiveSeats().stream().anyMatch(item -> StringUtils.isNotBlank(item.getActorName()))
                        ? "已标注可追溯的知名游资映射。" : "暂无可核验的知名游资映射。").append("\n\n");
        content.append("06｜风险与次日观察\n").append(report.getMaxRisk());
        return content.toString();
    }

    private void appendBoardNames(StringBuilder content, String label, List<SectorBoardItem> boards) {
        content.append(label).append("：");
        if (CollUtil.isEmpty(boards)) {
            content.append("暂无数据。\n");
            return;
        }
        int count = Math.min(5, boards.size());
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                content.append("、");
            }
            content.append(boards.get(index).getName());
        }
        content.append("。\n");
    }

    private boolean isCompleteReport(String content, PostMarketReportResp context) {
        if (StringUtils.isBlank(content)) {
            return false;
        }
        for (String requiredSection : REQUIRED_SECTIONS) {
            if (!content.contains(requiredSection)) {
                return false;
            }
        }
        Set<String> allowedCodes = new HashSet<>();
        for (PostMarketStarStockResp starStock : context.getStarStocks()) {
            allowedCodes.add(starStock.getCode());
        }
        for (DragonTigerItemResp dragonTigerItem : context.getDragonTigerItems()) {
            allowedCodes.add(dragonTigerItem.getCode());
        }
        Matcher codeMatcher = STOCK_CODE_PATTERN.matcher(content);
        while (codeMatcher.find()) {
            if (!allowedCodes.contains(codeMatcher.group(1))) {
                return false;
            }
        }
        return true;
    }

    private LocalDateTime resolveDataAsOf(MarketBriefingSnapshot marketSnapshotRow,
                                          List<SectorBoardItem> industryBoards,
                                          List<SectorBoardItem> conceptBoards,
                                          List<SectorBoardItem> mainlines,
                                          List<LimitUpPool> limitUpRows,
                                          List<StockFundFlow> stockFundFlowRows,
                                          List<DragonTigerItem> dragonTigerRows,
                                          List<MarketOpinion> activeSeatRows) {
        LocalDateTime dataAsOf = Objects.isNull(marketSnapshotRow) ? null : marketSnapshotRow.getUpdateTime();
        for (SectorBoardItem item : mergeLists(industryBoards, conceptBoards, mainlines)) {
            dataAsOf = later(dataAsOf, item.getSyncedAt());
        }
        if (CollUtil.isNotEmpty(limitUpRows)) {
            for (LimitUpPool row : limitUpRows) {
                dataAsOf = later(dataAsOf, row.getSyncedAt());
            }
        }
        if (CollUtil.isNotEmpty(stockFundFlowRows)) {
            for (StockFundFlow row : stockFundFlowRows) {
                dataAsOf = later(dataAsOf, row.getSyncedAt());
            }
        }
        if (CollUtil.isNotEmpty(dragonTigerRows)) {
            for (DragonTigerItem row : dragonTigerRows) {
                dataAsOf = later(dataAsOf, row.getSyncedAt());
            }
        }
        if (CollUtil.isNotEmpty(activeSeatRows)) {
            for (MarketOpinion row : activeSeatRows) {
                dataAsOf = later(dataAsOf, row.getSnapshotTime());
            }
        }
        return dataAsOf;
    }

    @SafeVarargs
    private final List<SectorBoardItem> mergeLists(List<SectorBoardItem>... boardLists) {
        List<SectorBoardItem> merged = new ArrayList<>();
        for (List<SectorBoardItem> boardList : boardLists) {
            if (CollUtil.isNotEmpty(boardList)) {
                merged.addAll(boardList);
            }
        }
        return merged;
    }

    private LocalDateTime later(LocalDateTime current, LocalDateTime candidate) {
        if (Objects.isNull(candidate)) {
            return current;
        }
        return Objects.isNull(current) || candidate.isAfter(current) ? candidate : current;
    }

    private boolean isPositive(BigDecimal value) {
        return Objects.nonNull(value) && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private String valueOrDash(Object value) {
        return Objects.isNull(value) ? "--" : String.valueOf(value);
    }

    private String loadSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/post-market-report.txt");
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("读取盘后总结提示词失败", ex);
        }
    }
}
