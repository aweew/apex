package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.ai.KimiChatMessage;
import com.awe.apex.quant.bot.service.IBotHoldingRiskService;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.ApexAiAction;
import com.awe.apex.quant.domain.bo.ApexAiIndustryAttributionBO;
import com.awe.apex.quant.domain.dto.ApexAiAnalysisResp;
import com.awe.apex.quant.domain.dto.ApexAiAnalyzeReq;
import com.awe.apex.quant.domain.dto.ApexAiContextResp;
import com.awe.apex.quant.domain.dto.ApexAiContributor;
import com.awe.apex.quant.domain.dto.ApexAiEnhanceReq;
import com.awe.apex.quant.domain.dto.ApexAiMetric;
import com.awe.apex.quant.domain.dto.ApexAiPortfolioOption;
import com.awe.apex.quant.domain.dto.ApexAiStrategyOption;
import com.awe.apex.quant.domain.dto.BotHoldingRiskItem;
import com.awe.apex.quant.domain.dto.BotHoldingRiskResp;
import com.awe.apex.quant.domain.dto.CapitalFlowOverviewResp;
import com.awe.apex.quant.domain.dto.DecisionAdviceActionResp;
import com.awe.apex.quant.domain.dto.DecisionAdviceResp;
import com.awe.apex.quant.domain.dto.DecisionAttrBucket;
import com.awe.apex.quant.domain.dto.DecisionAttributionResp;
import com.awe.apex.quant.domain.dto.DecisionStrategyPerformance;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.MarketFactorItem;
import com.awe.apex.quant.domain.dto.NewsPulseCardResp;
import com.awe.apex.quant.domain.dto.NewsPulseResp;
import com.awe.apex.quant.domain.dto.StockAnalysisResp;
import com.awe.apex.quant.domain.dto.StockAnalysisFreshnessResp;
import com.awe.apex.quant.domain.dto.StockSearchItem;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.domain.enums.ApexAiAnalysisTypeEnum;
import com.awe.apex.quant.mapper.ApexAiQueryMapper;
import com.awe.apex.quant.service.ApexAiConversationService;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.awe.apex.quant.service.IApexAiAnalystService;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.ICapitalFlowService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.INewsPulseService;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.service.IStockAnalysisService;
import com.awe.apex.quant.service.IStockService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Apex AI 分析服务实现
 */
@Slf4j
@Service
public class ApexAiAnalystServiceImpl implements IApexAiAnalystService {

    private static final String DISCLAIMER = "以上基于 Apex 当前数据生成，仅供研究，不构成投资建议。";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[+-]?\\d+(?:\\.\\d+)?");
    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");

    @Resource
    private IPortfolioService portfolioService;

    @Resource
    private IDecisionService decisionService;

    @Resource
    private KimiChatClient kimiChatClient;

    @Resource
    private ApexAiQueryMapper apexAiQueryMapper;

    @Resource
    private ApexUserContext userContext;

    @Resource
    private ApexUserAuthService userAuthService;

    @Resource
    private ApexAiConversationService conversationService;

    @Resource
    private IMarketBriefingService marketBriefingService;

    @Resource
    private IBotHoldingRiskService botHoldingRiskService;

    @Resource
    private IStockService stockService;

    @Resource
    private IStockAnalysisService stockAnalysisService;

    @Resource
    private ICapitalFlowService capitalFlowService;

    @Resource
    private INewsPulseService newsPulseService;

    /**
     * 查询工作台可用分析上下文
     *
     * @return 分析上下文
     */
    @Override
    public ApexAiContextResp context() {
        Long currentUserId = userContext.currentUserId();
        boolean currentUserAdmin = userAuthService.isAdmin(currentUserId);
        List<ApexAiPortfolioOption> portfolioOptions = apexAiQueryMapper.selectPortfolioOptions(
                currentUserId, currentUserAdmin);
        if (CollUtil.isEmpty(portfolioOptions)) {
            portfolioService.listPortfolios(false);
            portfolioOptions = apexAiQueryMapper.selectPortfolioOptions(currentUserId, currentUserAdmin);
        }
        List<ApexAiStrategyOption> strategyOptions = apexAiQueryMapper.selectStrategyOptions(currentUserId, 60);
        return ApexAiContextResp.builder()
                .aiConfigured(kimiChatClient.available())
                .portfolios(portfolioOptions)
                .strategies(strategyOptions)
                .recommendedQuestions(List.of(
                        "今天大盘怎么样？",
                        "今天应该买什么？",
                        "我的持仓风险怎么样？",
                        "为什么今天收益下跌？",
                        "资金面有什么变化？",
                        "这个策略最近为什么失效？",
                        "非共振信号是否应该降低权重？"))
                .build();
    }

    /**
     * 分析 Apex 数据并回答问题
     *
     * @param request 分析请求
     * @return 分析结果
     */
    @Override
    public ApexAiAnalysisResp analyze(ApexAiAnalyzeReq request) {
        long startedAt = System.nanoTime();
        if (Objects.isNull(request) || StringUtils.isBlank(request.getQuestion())) {
            throw new BusinessException("问题不能为空");
        }
        String question = request.getQuestion().trim();
        Long conversationId = conversationService.openConversation(request.getConversationId(), question);
        ApexAiAnalysisTypeEnum analysisType = ApexAiAnalysisTypeEnum.of(request.getAnalysisType());
        if (analysisType == ApexAiAnalysisTypeEnum.AUTO) {
            if (containsAny(question, "北向", "主力资金", "资金面", "龙虎榜", "净流入")) {
                analysisType = ApexAiAnalysisTypeEnum.CAPITAL_FLOW;
            } else if (containsAny(question, "新闻", "消息面", "利好", "利空", "资讯")) {
                analysisType = ApexAiAnalysisTypeEnum.NEWS_PULSE;
            } else if (containsAny(question, "今天买什么", "今天卖什么", "今日决策", "今天策略", "今日策略",
                    "怎么操作", "应该买什么", "应该卖什么")
                    || (containsAny(question, "今天", "今日") && containsAny(question, "策略", "意见", "建议"))) {
                analysisType = ApexAiAnalysisTypeEnum.DECISION;
            } else if (containsAny(question, "我的持仓", "持仓风险", "组合风险", "总体风险", "仓位", "浮亏", "浮盈")) {
                analysisType = ApexAiAnalysisTypeEnum.RISK;
            } else if (containsAny(question, "大盘", "市场", "指数", "热点", "赚钱效应", "成交量")) {
                analysisType = ApexAiAnalysisTypeEnum.MARKET;
            } else if (containsAny(question, "策略", "失效", "胜率", "样本", "共振", "超额")) {
                analysisType = ApexAiAnalysisTypeEnum.STRATEGY;
            } else if (containsAny(question, "组合", "收益", "盈亏", "亏", "赚", "板块", "持仓")) {
                analysisType = ApexAiAnalysisTypeEnum.PORTFOLIO;
            } else if (isStockQuestion(question)) {
                analysisType = ApexAiAnalysisTypeEnum.STOCK;
            } else {
                analysisType = ApexAiAnalysisTypeEnum.GENERAL;
            }
        }
        ApexAiAnalysisResp analysis = switch (analysisType) {
            case MARKET -> answerMarket();
            case DECISION -> answerDecision();
            case RISK -> answerHoldingRisk();
            case STOCK -> answerStock(question);
            case CAPITAL_FLOW -> answerCapitalFlow();
            case NEWS_PULSE -> answerNewsPulse();
            case PORTFOLIO -> analyzePortfolio(request, question);
            case STRATEGY -> analyzeStrategy(request, question);
            case GENERAL, AUTO -> answerGeneral(question);
        };
        analysis.setConversationId(conversationId);
        request.setConversationId(conversationId);
        request.setAnalysisType(analysis.getAnalysisType());
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000L;
        conversationService.saveAnalysis(conversationId, request, analysis, latencyMs);
        return analysis;
    }

    /**
     * 使用 Kimi 增强已持久化的规则分析。
     *
     * @param request 增强请求
     * @return 增强结果；AI 不可用或调用失败时返回原规则结果
     */
    @Override
    public ApexAiAnalysisResp enhance(ApexAiEnhanceReq request) {
        if (Objects.isNull(request) || Objects.isNull(request.getConversationId())
                || StringUtils.isBlank(request.getRequestId())) {
            throw new BusinessException("会话ID和请求编号不能为空");
        }
        ApexAiAnalysisResp analysis = conversationService.loadAnalysis(
                request.getConversationId(), request.getRequestId());
        if (!kimiChatClient.available()) {
            return analysis;
        }

        // 保留真实对话角色，并把当前结构化结果作为本轮增强约束。
        List<KimiChatMessage> messages = new ArrayList<>();
        messages.add(KimiChatMessage.builder()
                .role("system")
                .content("你是 Apex AI Analyst 小灵。只根据给定的 Apex 结构化分析解释，"
                        + "不得修改或补造数字、因子IC、市场状态、交易记录、证据、指标和因果关系。"
                        + "使用简洁中文，先给结论，再解释主要证据，180字以内，不重复免责声明。")
                .build());
        messages.addAll(conversationService.history(request.getConversationId(), 10));
        StringBuilder evidence = new StringBuilder()
                .append("标题=").append(analysis.getTitle())
                .append("；规则摘要=").append(analysis.getSummary())
                .append("；数据完整度=").append(analysis.getDataLevel())
                .append("；总值=").append(analysis.getTotalValue())
                .append("；残差=").append(analysis.getResidualValue())
                .append("；数据说明=").append(analysis.getDataNote());
        if (CollUtil.isNotEmpty(analysis.getMetrics())) {
            evidence.append("；指标=");
            for (ApexAiMetric metric : analysis.getMetrics()) {
                evidence.append(metric.getLabel()).append(':').append(metric.getValue()).append('；');
            }
        }
        if (CollUtil.isNotEmpty(analysis.getContributors())) {
            evidence.append("证据=");
            for (ApexAiContributor contributor : analysis.getContributors()) {
                evidence.append(contributor.getName()).append(':')
                        .append(contributor.getValue()).append('(')
                        .append(contributor.getDetail()).append(")；");
            }
        }
        messages.add(KimiChatMessage.builder()
                .role("user")
                .content("请增强上一条规则分析的文字结论，只返回改写后的摘要。结构化分析："
                        + evidence)
                .build());

        long startedAt = System.nanoTime();
        String enhancedSummary = kimiChatClient.chatMessages(messages, 500);
        if (StringUtils.isBlank(enhancedSummary)) {
            return analysis;
        }
        if (containsUnsupportedNumber(enhancedSummary, evidence.toString())) {
            log.warn("Kimi 增强包含规则证据外数字，保留规则结果，请求编号={}", analysis.getRequestId());
            return analysis;
        }
        analysis.setSummary(enhancedSummary.trim());
        analysis.setAiEnhanced(true);
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000L;
        conversationService.saveEnhancement(analysis, latencyMs);
        return analysis;
    }

    private boolean containsUnsupportedNumber(String enhancedSummary, String evidence) {
        Set<String> evidenceNumbers = new HashSet<>();
        Matcher evidenceMatcher = NUMBER_PATTERN.matcher(evidence);
        while (evidenceMatcher.find()) {
            BigDecimal number = new BigDecimal(evidenceMatcher.group()).stripTrailingZeros();
            evidenceNumbers.add(number.toPlainString());
            evidenceNumbers.add(number.abs().toPlainString());
        }
        Matcher summaryMatcher = NUMBER_PATTERN.matcher(enhancedSummary);
        while (summaryMatcher.find()) {
            BigDecimal number = new BigDecimal(summaryMatcher.group()).stripTrailingZeros();
            if (!evidenceNumbers.contains(number.toPlainString())
                    && !evidenceNumbers.contains(number.abs().toPlainString())) {
                return true;
            }
        }
        return false;
    }

    private ApexAiAnalysisResp analyzePortfolio(ApexAiAnalyzeReq request, String question) {
        PortfolioSummaryResp selectedPortfolio = null;
        List<PortfolioSummaryResp> portfolios = portfolioService.listPortfolios(false);
        if (CollUtil.isNotEmpty(portfolios)) {
            for (PortfolioSummaryResp portfolio : portfolios) {
                if (!Boolean.TRUE.equals(portfolio.getEditable())) {
                    continue;
                }
                if (Objects.nonNull(request.getPortfolioId())
                        && Objects.equals(request.getPortfolioId(), portfolio.getId())) {
                    selectedPortfolio = portfolio;
                    break;
                }
                if (Objects.isNull(request.getPortfolioId()) && StringUtils.isNotBlank(portfolio.getName())
                        && question.contains(portfolio.getName())) {
                    selectedPortfolio = portfolio;
                    break;
                }
                if (Objects.isNull(request.getPortfolioId()) && Boolean.TRUE.equals(portfolio.getIsDefault())) {
                    selectedPortfolio = portfolio;
                } else if (Objects.isNull(selectedPortfolio)) {
                    selectedPortfolio = portfolio;
                }
            }
        }
        if (Objects.isNull(selectedPortfolio)) {
            return emptyPortfolioAnalysis();
        }

        PortfolioSummaryResp detail = portfolioService.detail(selectedPortfolio.getId());
        List<ApexAiIndustryAttributionBO> industryAttributions = new ArrayList<>();
        BigDecimal attributedPnl = BigDecimal.ZERO;
        if (CollUtil.isNotEmpty(detail.getHoldings())) {
            for (PortfolioHolding holding : detail.getHoldings()) {
                if (Objects.isNull(holding.getTodayPnl())) {
                    continue;
                }
                String industry = StringUtils.isNotBlank(holding.getIndustry())
                        ? holding.getIndustry().trim() : "未分类";
                ApexAiIndustryAttributionBO attribution = null;
                for (ApexAiIndustryAttributionBO candidate : industryAttributions) {
                    if (industry.equals(candidate.getIndustry())) {
                        attribution = candidate;
                        break;
                    }
                }
                if (Objects.isNull(attribution)) {
                    attribution = ApexAiIndustryAttributionBO.builder().industry(industry).build();
                    industryAttributions.add(attribution);
                }
                attribution.setTodayPnl(attribution.getTodayPnl().add(holding.getTodayPnl()));
                attribution.getHoldings().add(holding);
                attributedPnl = attributedPnl.add(holding.getTodayPnl());
            }
        }
        industryAttributions.sort(Comparator.comparing(
                attribution -> attribution.getTodayPnl().abs(), Comparator.reverseOrder()));

        BigDecimal totalTodayPnl = Objects.nonNull(detail.getTodayPnl())
                ? detail.getTodayPnl().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal previousMarketValue = Objects.nonNull(detail.getMarketValue())
                ? detail.getMarketValue().subtract(totalTodayPnl) : BigDecimal.ZERO;
        List<ApexAiContributor> contributors = new ArrayList<>();
        int rank = 1;
        for (ApexAiIndustryAttributionBO attribution : industryAttributions) {
            attribution.getHoldings().sort(Comparator.comparing(
                    holding -> Objects.nonNull(holding.getTodayPnl())
                            ? holding.getTodayPnl().abs() : BigDecimal.ZERO,
                    Comparator.reverseOrder()));
            List<String> leadingStocks = new ArrayList<>();
            int stockLimit = Math.min(2, attribution.getHoldings().size());
            for (int index = 0; index < stockLimit; index++) {
                PortfolioHolding holding = attribution.getHoldings().get(index);
                leadingStocks.add(StringUtils.isNotBlank(holding.getName()) ? holding.getName() : holding.getCode());
            }
            BigDecimal contributionPct = previousMarketValue.abs().compareTo(BigDecimal.ONE) >= 0
                    ? attribution.getTodayPnl().multiply(BigDecimal.valueOf(100))
                    .divide(previousMarketValue, 4, RoundingMode.HALF_UP)
                    : null;
            contributors.add(ApexAiContributor.builder()
                    .rank(rank++)
                    .name(attribution.getIndustry())
                    .detail(attribution.getHoldings().size() + " 只持仓"
                            + (CollUtil.isNotEmpty(leadingStocks) ? " · 主要影响 " + String.join("、", leadingStocks) : ""))
                    .value(attribution.getTodayPnl().setScale(2, RoundingMode.HALF_UP))
                    .contributionPct(contributionPct)
                    .sampleCount(attribution.getHoldings().size())
                    .direction(direction(attribution.getTodayPnl()))
                    .build());
        }
        BigDecimal residualValue = totalTodayPnl.subtract(attributedPnl).setScale(2, RoundingMode.HALF_UP);
        int positionCount = Objects.nonNull(detail.getPositionCount()) ? detail.getPositionCount() : 0;
        int missingQuoteCount = Objects.nonNull(detail.getMissingQuoteCount()) ? detail.getMissingQuoteCount() : 0;
        int coveredCount = Math.max(0, positionCount - missingQuoteCount);
        BigDecimal coverageRate = positionCount > 0
                ? BigDecimal.valueOf(coveredCount).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(positionCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        String dataLevel = positionCount == 0 || coveredCount == 0 ? "RED"
                : missingQuoteCount > 0 ? "YELLOW" : "GREEN";
        String leadingName = CollUtil.isNotEmpty(contributors) ? contributors.get(0).getName() : "暂无可归因行业";
        String deterministicSummary = detail.getName() + "今日收益率 " + signed(detail.getTodayPct(), 2) + "%（"
                + signed(totalTodayPnl, 2) + " 元）。"
                + (CollUtil.isNotEmpty(contributors)
                ? "影响最大的方向是" + leadingName + "，贡献 "
                + signed(contributors.get(0).getContributionPct(), 2) + " 个百分点。"
                : "当前持仓缺少可用的当日盈亏明细。");
        List<ApexAiMetric> metrics = buildPortfolioMetrics(detail, totalTodayPnl, coverageRate, dataLevel,
                coveredCount, positionCount);
        if (containsAny(question, "哪只", "个股", "股票", "影响最大", "拖累最大")) {
            return analyzePortfolioStockImpact(detail, totalTodayPnl, previousMarketValue, residualValue,
                    dataLevel, metrics);
        }
        if (containsAny(question, "行业集中", "集中度", "行业权重", "行业暴露")) {
            return analyzePortfolioIndustryExposure(detail, totalTodayPnl, residualValue, dataLevel, metrics);
        }
        if (containsAny(question, "先处理", "处理哪些", "优先处理", "持仓风险", "风险怎么样", "该卖", "止损")) {
            return analyzePortfolioReviewPriority(detail, totalTodayPnl, previousMarketValue, residualValue,
                    dataLevel, metrics, containsAny(question, "决策", "操作", "买", "卖"));
        }
        boolean currentDayMoveQuestion = containsAny(question, "今天", "当日")
                && containsAny(question, "下跌", "上涨", "跌", "涨", "表现");
        if (!containsAny(question, "收益", "盈亏", "板块", "行业", "归因") && !currentDayMoveQuestion) {
            return answerUnsupportedPortfolioQuestion(detail, question, dataLevel);
        }
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(ApexAiAnalysisTypeEnum.PORTFOLIO.getCode())
                .title(detail.getName() + " · 今日收益归因")
                .summary(deterministicSummary)
                .portfolioId(detail.getId())
                .totalValue(totalTodayPnl)
                .residualValue(residualValue)
                .dataLevel(dataLevel)
                .dataAsOf(detail.getQuoteTime())
                .dataNote("按持仓行业聚合当日浮盈，以昨日持仓市值计算收益贡献；不含现金收益和已卖出证券的盘后影响。")
                .aiEnhanced(false)
                .generatedAt(LocalDateTime.now())
                .metrics(metrics)
                .contributors(contributors)
                .suggestions(buildPortfolioSuggestions(detail, contributors, missingQuoteCount, residualValue))
                .followUpQuestions(List.of(
                        "哪只股票对今天收益影响最大？",
                        "当前组合行业集中度是否过高？",
                        "结合今天的决策，我应该先处理哪些持仓？"))
                .disclaimer(DISCLAIMER)
                .build();
    }

    private ApexAiAnalysisResp analyzePortfolioStockImpact(PortfolioSummaryResp detail, BigDecimal totalTodayPnl,
                                                            BigDecimal previousMarketValue, BigDecimal residualValue,
                                                            String dataLevel, List<ApexAiMetric> metrics) {
        List<PortfolioHolding> holdings = new ArrayList<>();
        if (CollUtil.isNotEmpty(detail.getHoldings())) {
            for (PortfolioHolding holding : detail.getHoldings()) {
                if (Objects.nonNull(holding.getTodayPnl())) {
                    holdings.add(holding);
                }
            }
        }
        holdings.sort(Comparator.comparing(holding -> holding.getTodayPnl().abs(), Comparator.reverseOrder()));
        List<ApexAiContributor> contributors = new ArrayList<>();
        int stockLimit = Math.min(5, holdings.size());
        for (int index = 0; index < stockLimit; index++) {
            PortfolioHolding holding = holdings.get(index);
            BigDecimal contributionPct = previousMarketValue.abs().compareTo(BigDecimal.ONE) >= 0
                    ? holding.getTodayPnl().multiply(BigDecimal.valueOf(100))
                    .divide(previousMarketValue, 4, RoundingMode.HALF_UP) : null;
            contributors.add(ApexAiContributor.builder()
                    .rank(index + 1)
                    .name(StringUtils.isNotBlank(holding.getName()) ? holding.getName() : holding.getCode())
                    .detail((StringUtils.isNotBlank(holding.getIndustry()) ? holding.getIndustry() + " · " : "")
                            + "当日浮盈 " + signed(holding.getTodayPnl(), 2) + " 元")
                    .value(holding.getTodayPnl().setScale(2, RoundingMode.HALF_UP))
                    .contributionPct(contributionPct)
                    .sampleCount(1)
                    .direction(direction(holding.getTodayPnl()))
                    .build());
        }
        String summary = CollUtil.isEmpty(contributors)
                ? detail.getName() + "当前没有可用于个股归因的当日盈亏明细。"
                : detail.getName() + "对今日收益影响最大的是" + contributors.get(0).getName() + "，当日浮盈 "
                + signed(contributors.get(0).getValue(), 2) + " 元，占组合昨日持仓市值的 "
                + signed(contributors.get(0).getContributionPct(), 2) + " 个百分点。";
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(ApexAiAnalysisTypeEnum.PORTFOLIO.getCode())
                .title(detail.getName() + " · 个股影响排序")
                .summary(summary)
                .portfolioId(detail.getId())
                .totalValue(totalTodayPnl)
                .residualValue(residualValue)
                .dataLevel(dataLevel)
                .dataAsOf(detail.getQuoteTime())
                .dataNote("按单只持仓当日浮盈排序，以昨日持仓市值计算收益贡献。")
                .aiEnhanced(false)
                .generatedAt(LocalDateTime.now())
                .metrics(metrics)
                .contributors(contributors)
                .suggestions(List.of("先核对首位个股是否触及既定止损、止盈或仓位上限",
                        "再结合行业归因判断是单只波动还是同方向风险暴露"))
                .followUpQuestions(List.of("今天哪些板块拖累了组合？", "当前组合行业集中度是否过高？",
                        "结合今天的决策，我应该先处理哪些持仓？"))
                .disclaimer(DISCLAIMER)
                .build();
    }

    private ApexAiAnalysisResp analyzePortfolioIndustryExposure(PortfolioSummaryResp detail,
                                                                 BigDecimal totalTodayPnl, BigDecimal residualValue,
                                                                 String dataLevel, List<ApexAiMetric> metrics) {
        List<ApexAiIndustryAttributionBO> industryAttributions = new ArrayList<>();
        if (CollUtil.isNotEmpty(detail.getHoldings())) {
            for (PortfolioHolding holding : detail.getHoldings()) {
                String industry = StringUtils.isNotBlank(holding.getIndustry()) ? holding.getIndustry().trim() : "未分类";
                ApexAiIndustryAttributionBO attribution = null;
                for (ApexAiIndustryAttributionBO candidate : industryAttributions) {
                    if (industry.equals(candidate.getIndustry())) {
                        attribution = candidate;
                        break;
                    }
                }
                if (Objects.isNull(attribution)) {
                    attribution = ApexAiIndustryAttributionBO.builder().industry(industry).build();
                    industryAttributions.add(attribution);
                }
                attribution.getHoldings().add(holding);
                if (Objects.nonNull(holding.getTodayPnl())) {
                    attribution.setTodayPnl(attribution.getTodayPnl().add(holding.getTodayPnl()));
                }
            }
        }
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        for (ApexAiIndustryAttributionBO attribution : industryAttributions) {
            for (PortfolioHolding holding : attribution.getHoldings()) {
                if (Objects.nonNull(holding.getMarketValue())) {
                    totalMarketValue = totalMarketValue.add(holding.getMarketValue());
                }
            }
        }
        List<ApexAiContributor> contributors = new ArrayList<>();
        for (ApexAiIndustryAttributionBO attribution : industryAttributions) {
            BigDecimal industryMarketValue = BigDecimal.ZERO;
            for (PortfolioHolding holding : attribution.getHoldings()) {
                if (Objects.nonNull(holding.getMarketValue())) {
                    industryMarketValue = industryMarketValue.add(holding.getMarketValue());
                }
            }
            BigDecimal weightPct = totalMarketValue.compareTo(BigDecimal.ZERO) > 0
                    ? industryMarketValue.multiply(BigDecimal.valueOf(100))
                    .divide(totalMarketValue, 4, RoundingMode.HALF_UP) : null;
            contributors.add(ApexAiContributor.builder()
                    .rank(0)
                    .name(attribution.getIndustry())
                    .detail(attribution.getHoldings().size() + " 只持仓 · 行业市值 " + amount(industryMarketValue)
                            + " 元 · 当日浮盈 " + signed(attribution.getTodayPnl(), 2) + " 元")
                    .value(industryMarketValue.setScale(2, RoundingMode.HALF_UP))
                    .contributionPct(weightPct)
                    .sampleCount(attribution.getHoldings().size())
                    .direction("NEUTRAL")
                    .build());
        }
        contributors.sort(Comparator.comparing(ApexAiContributor::getValue, Comparator.reverseOrder()));
        for (int index = 0; index < contributors.size(); index++) {
            contributors.get(index).setRank(index + 1);
        }
        String summary = CollUtil.isEmpty(contributors) || Objects.isNull(contributors.get(0).getContributionPct())
                ? detail.getName() + "缺少可用持仓市值，暂不能判断行业集中度。"
                : detail.getName() + "最大行业暴露是" + contributors.get(0).getName() + "，占持仓市值 "
                + signed(contributors.get(0).getContributionPct(), 2) + "%（"
                + amount(contributors.get(0).getValue()) + " 元）。";
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(ApexAiAnalysisTypeEnum.PORTFOLIO.getCode())
                .title(detail.getName() + " · 行业集中度")
                .summary(summary)
                .portfolioId(detail.getId())
                .totalValue(totalTodayPnl)
                .residualValue(residualValue)
                .dataLevel(totalMarketValue.compareTo(BigDecimal.ZERO) > 0 ? dataLevel : "YELLOW")
                .dataAsOf(detail.getQuoteTime())
                .dataNote("按当前有市值的持仓归集行业暴露；行业权重不含现金。")
                .aiEnhanced(false)
                .generatedAt(LocalDateTime.now())
                .metrics(metrics)
                .contributors(contributors)
                .suggestions(List.of("将最大行业暴露与预设行业风险预算比较，再决定是否需要减仓或分散",
                        "行业内多只持仓同步波动时，优先评估共同风险而非只看单只盈亏"))
                .followUpQuestions(List.of("今天哪些板块拖累了组合？", "哪只股票对今天收益影响最大？",
                        "结合今天的决策，我应该先处理哪些持仓？"))
                .disclaimer(DISCLAIMER)
                .build();
    }

    private ApexAiAnalysisResp analyzePortfolioReviewPriority(PortfolioSummaryResp detail, BigDecimal totalTodayPnl,
                                                               BigDecimal previousMarketValue, BigDecimal residualValue,
                                                               String dataLevel, List<ApexAiMetric> metrics,
                                                               boolean includeDecision) {
        DecisionAdviceResp decisionAdvice = includeDecision ? decisionService.advice(null) : null;
        List<PortfolioHolding> holdings = new ArrayList<>();
        if (CollUtil.isNotEmpty(detail.getHoldings())) {
            for (PortfolioHolding holding : detail.getHoldings()) {
                if (Objects.nonNull(holding.getTodayPnl())) {
                    holdings.add(holding);
                }
            }
        }
        holdings.sort(Comparator.comparing(holding -> holding.getTodayPnl().abs(), Comparator.reverseOrder()));
        List<ApexAiContributor> contributors = new ArrayList<>();
        int stockLimit = Math.min(3, holdings.size());
        for (int index = 0; index < stockLimit; index++) {
            PortfolioHolding holding = holdings.get(index);
            DecisionAdviceActionResp decisionAction = null;
            if (Objects.nonNull(decisionAdvice) && CollUtil.isNotEmpty(decisionAdvice.getActions())) {
                for (DecisionAdviceActionResp candidate : decisionAdvice.getActions()) {
                    if (StringUtils.isNotBlank(candidate.getCode()) && candidate.getCode().equals(holding.getCode())) {
                        decisionAction = candidate;
                        break;
                    }
                }
            }
            boolean stopLossTriggered = Objects.nonNull(holding.getMarketPrice()) && Objects.nonNull(holding.getStopLoss())
                    && holding.getMarketPrice().compareTo(holding.getStopLoss()) <= 0;
            BigDecimal contributionPct = previousMarketValue.abs().compareTo(BigDecimal.ONE) >= 0
                    ? holding.getTodayPnl().multiply(BigDecimal.valueOf(100))
                    .divide(previousMarketValue, 4, RoundingMode.HALF_UP) : null;
            String riskDetail = Objects.nonNull(decisionAction)
                    ? "今日决策 " + decisionAction.getAction() + " · "
                    + (StringUtils.isNotBlank(decisionAction.getReason()) ? decisionAction.getReason() : "请按决策规则复核")
                    : stopLossTriggered
                    ? "现价 " + amount(holding.getMarketPrice()) + " 元已不高于止损价 " + amount(holding.getStopLoss()) + " 元"
                    : "当日浮盈 " + signed(holding.getTodayPnl(), 2) + " 元，需按既定风控规则复核";
            contributors.add(ApexAiContributor.builder()
                    .rank(index + 1)
                    .name(StringUtils.isNotBlank(holding.getName()) ? holding.getName() : holding.getCode())
                    .detail(riskDetail)
                    .value(holding.getTodayPnl().setScale(2, RoundingMode.HALF_UP))
                    .contributionPct(contributionPct)
                    .sampleCount(1)
                    .displayValue(Objects.nonNull(decisionAction) ? decisionAction.getAction() : null)
                    .direction(Objects.nonNull(decisionAction) && containsAny(decisionAction.getAction(), "SELL", "REDUCE")
                            || stopLossTriggered || holding.getTodayPnl().signum() < 0 ? "NEGATIVE" : "NEUTRAL")
                    .build());
        }
        String summary = CollUtil.isEmpty(contributors)
                ? detail.getName() + "当前没有可用于持仓复核的当日盈亏明细。"
                : detail.getName() + "建议先复核" + contributors.get(0).getName() + "："
                + contributors.get(0).getDetail() + "。此排序仅按当日影响与已录入止损价生成，未替代交易决策。";
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(ApexAiAnalysisTypeEnum.PORTFOLIO.getCode())
                .title(detail.getName() + " · 持仓复核优先级")
                .summary(summary)
                .portfolioId(detail.getId())
                .totalValue(totalTodayPnl)
                .residualValue(residualValue)
                .dataLevel(dataLevel)
                .dataAsOf(detail.getQuoteTime())
                .dataNote(includeDecision
                        ? "优先级按当日影响、已录入止损价和今日决策匹配生成；未匹配到的持仓仍需人工复核。"
                        : "优先级按当日盈亏绝对值排序；止损状态仅使用已录入的止损价与当前行情。")
                .aiEnhanced(false)
                .generatedAt(LocalDateTime.now())
                .metrics(metrics)
                .contributors(contributors)
                .suggestions(List.of("先核对首位持仓的止损、止盈、仓位上限与原始买入逻辑是否仍成立",
                        "不要仅因单日涨跌改动规则；结合当日决策和行业暴露后再执行交易"))
                .actions(List.of(action("查看今日决策", "/decision", "PRIMARY"),
                        action("查看组合", "/portfolio", "DEFAULT")))
                .followUpQuestions(List.of("哪只股票对今天收益影响最大？", "当前组合行业集中度是否过高？",
                        "今天哪些板块拖累了组合？"))
                .disclaimer(DISCLAIMER)
                .build();
    }

    private ApexAiAnalysisResp answerMarket() {
        MarketBriefingResp briefing = marketBriefingService.briefing(false);
        if (Objects.isNull(briefing)) {
            return unavailableAnalysis(ApexAiAnalysisTypeEnum.MARKET, "市场研判", "当前没有可用市场简报，请先同步行情后重试。",
                    List.of(action("查看同步中心", "/sync", "PRIMARY")));
        }
        List<ApexAiContributor> contributors = new ArrayList<>();
        if (CollUtil.isNotEmpty(briefing.getFactors())) {
            int factorLimit = Math.min(4, briefing.getFactors().size());
            for (int index = 0; index < factorLimit; index++) {
                MarketFactorItem factor = briefing.getFactors().get(index);
                contributors.add(ApexAiContributor.builder()
                        .rank(index + 1)
                        .name(factor.getName())
                        .detail(StringUtils.isNotBlank(factor.getNote()) ? factor.getNote() : "市场简报因子")
                        .displayValue(factor.getValue())
                        .direction("偏多".equals(factor.getSignal()) ? "POSITIVE"
                                : "偏空".equals(factor.getSignal()) ? "NEGATIVE" : "NEUTRAL")
                        .build());
            }
        }
        String stance = StringUtils.isNotBlank(briefing.getStance()) ? briefing.getStance() : "暂无明确立场";
        String summary = "当前市场立场为" + stance + "。"
                + (StringUtils.isNotBlank(briefing.getStanceReason()) ? briefing.getStanceReason() : "市场说明暂未生成")
                + (StringUtils.isNotBlank(briefing.getPositionAdvice()) ? " 建议仓位：" + briefing.getPositionAdvice() + "。" : "");
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(ApexAiAnalysisTypeEnum.MARKET.getCode())
                .title("今日市场研判")
                .summary(summary)
                .dataLevel(StringUtils.isNotBlank(briefing.getDataLevel()) ? briefing.getDataLevel() : "YELLOW")
                .dataAsOf(Objects.nonNull(briefing.getAsOf()) ? briefing.getAsOf().atStartOfDay() : null)
                .dataNote(StringUtils.isNotBlank(briefing.getMessage()) ? briefing.getMessage() : "市场简报由指数、量能、广度与情绪数据生成。")
                .aiEnhanced(false)
                .generatedAt(LocalDateTime.now())
                .metrics(List.of(metric("市场立场", stance, Objects.nonNull(briefing.getStanceScore())
                                ? BigDecimal.valueOf(briefing.getStanceScore()) : null, "", "NEUTRAL", "基于当前市场简报"),
                        metric("建议仓位", defaultText(briefing.getPositionAdvice(), "--"), null, "", "NEUTRAL", "以最新市场简报为准")))
                .contributors(contributors)
                .suggestions(List.of("先按建议仓位控制总风险，再查看今日决策中的候选标的"))
                .actions(List.of(action("查看今日决策", "/decision", "PRIMARY"), action("查看完整行情", "/market", "DEFAULT")))
                .followUpQuestions(List.of("今天应该买什么？", "资金面有什么变化？", "我的持仓风险怎么样？"))
                .disclaimer(DISCLAIMER)
                .build();
    }

    private ApexAiAnalysisResp answerDecision() {
        DecisionAdviceResp advice = decisionService.advice(null);
        if (Objects.isNull(advice)) {
            return unavailableAnalysis(ApexAiAnalysisTypeEnum.DECISION, "今日决策", "当前没有可用的今日决策，请先生成决策后重试。",
                    List.of(action("生成今日决策", "/decision", "PRIMARY")));
        }
        List<ApexAiContributor> contributors = new ArrayList<>();
        if (CollUtil.isNotEmpty(advice.getActions())) {
            int actionLimit = Math.min(5, advice.getActions().size());
            for (int index = 0; index < actionLimit; index++) {
                DecisionAdviceActionResp decisionAction = advice.getActions().get(index);
                contributors.add(ApexAiContributor.builder()
                        .rank(Objects.nonNull(decisionAction.getPriority()) ? decisionAction.getPriority() : index + 1)
                        .name(defaultText(decisionAction.getName(), decisionAction.getCode()))
                        .detail(defaultText(decisionAction.getReason(), "请在决策页核对完整依据与风险提示"))
                        .displayValue(defaultText(decisionAction.getAction(), "WATCH"))
                        .direction(containsAny(decisionAction.getAction(), "BUY", "ADD") ? "POSITIVE"
                                : containsAny(decisionAction.getAction(), "SELL", "REDUCE") ? "NEGATIVE" : "NEUTRAL")
                        .build());
            }
        }
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(ApexAiAnalysisTypeEnum.DECISION.getCode())
                .title("今日操作决策")
                .summary(defaultText(advice.getSummary(), "当前没有可执行的决策动作，请先核对数据新鲜度与市场状态。"))
                .dataLevel(Objects.nonNull(advice.getActionDate()) ? "GREEN" : "YELLOW")
                .dataAsOf(Objects.nonNull(advice.getGeneratedAt()) ? advice.getGeneratedAt()
                        : Objects.nonNull(advice.getActionDate()) ? advice.getActionDate().atStartOfDay() : null)
                .dataNote("决策动作仅供研究与复核，执行前应在决策页查看完整风险约束。")
                .aiEnhanced(Boolean.TRUE.equals(advice.getAiEnhanced()))
                .generatedAt(LocalDateTime.now())
                .metrics(List.of(metric("目标仓位", percent(advice.getTargetExposure()), advice.getTargetExposure(), "%", "NEUTRAL", "本轮决策后的目标总仓位"),
                        metric("可执行动作", String.valueOf(CollUtil.isEmpty(advice.getActions()) ? 0 : advice.getActions().size()),
                                BigDecimal.valueOf(CollUtil.isEmpty(advice.getActions()) ? 0 : advice.getActions().size()), "项", "NEUTRAL", "展示优先级最高的动作")))
                .contributors(contributors)
                .suggestions(List.of("先检查持仓风险与行情截至时间，再决定是否执行具体动作"))
                .actions(List.of(action("查看完整决策", "/decision", "PRIMARY"), action("查看模拟盘", "/paper", "DEFAULT")))
                .followUpQuestions(List.of("我的持仓风险怎么样？", "结合今天的决策，我应该先处理哪些持仓？", "今天大盘怎么样？"))
                .disclaimer(DISCLAIMER)
                .build();
    }

    private ApexAiAnalysisResp answerHoldingRisk() {
        BotHoldingRiskResp risk = botHoldingRiskService.analyze();
        if (Objects.isNull(risk)) {
            return unavailableAnalysis(ApexAiAnalysisTypeEnum.RISK, "持仓风险", "当前没有可用于风险复核的持仓数据。",
                    List.of(action("查看组合", "/portfolio", "PRIMARY")));
        }
        List<ApexAiContributor> contributors = new ArrayList<>();
        if (CollUtil.isNotEmpty(risk.getAlerts())) {
            int alertLimit = Math.min(5, risk.getAlerts().size());
            for (int index = 0; index < alertLimit; index++) {
                BotHoldingRiskItem alert = risk.getAlerts().get(index);
                contributors.add(ApexAiContributor.builder()
                        .rank(index + 1)
                        .name(defaultText(alert.getName(), alert.getCode()))
                        .detail(defaultText(alert.getMessage(), "风险服务未返回具体说明"))
                        .displayValue(defaultText(alert.getLevel(), "WARN"))
                        .direction("CRITICAL".equals(alert.getLevel()) ? "NEGATIVE" : "NEUTRAL")
                        .build());
            }
        }
        int criticalCount = zero(risk.getCriticalCount());
        int warnCount = zero(risk.getWarnCount());
        String summary = criticalCount > 0 ? "当前有 " + criticalCount + " 项严重风险，需要优先按既定风控规则复核。"
                : warnCount > 0 ? "当前有 " + warnCount + " 项风险提示，建议在交易前完成复核。"
                : "当前没有触发持仓风险告警，仍应结合今日决策和行情时效复核。";
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(ApexAiAnalysisTypeEnum.RISK.getCode())
                .title("持仓风险复核")
                .summary(summary)
                .dataLevel(criticalCount > 0 ? "RED" : warnCount > 0 ? "YELLOW" : "GREEN")
                .dataAsOf(null)
                .dataNote("风险项基于当前持仓、行情与已录入风控阈值生成；行情截至 "
                        + defaultText(risk.getDataAsOf(), "未知") + "。")
                .aiEnhanced(false)
                .generatedAt(LocalDateTime.now())
                .metrics(List.of(metric("持仓", String.valueOf(zero(risk.getHoldingCount())), BigDecimal.valueOf(zero(risk.getHoldingCount())), "只", "NEUTRAL", "当前组合持仓数量"),
                        metric("严重风险", String.valueOf(criticalCount), BigDecimal.valueOf(criticalCount), "项", criticalCount > 0 ? "WARNING" : "NEUTRAL", "需优先复核的风险项")))
                .contributors(contributors)
                .suggestions(List.of("先查看严重风险对应持仓，再结合今日决策核对止损、仓位与原始逻辑"))
                .actions(List.of(action("查看组合", "/portfolio", "PRIMARY"), action("查看今日决策", "/decision", "DEFAULT")))
                .followUpQuestions(List.of("结合今天的决策，我应该先处理哪些持仓？", "为什么今天收益下跌？"))
                .disclaimer(DISCLAIMER)
                .build();
    }

    private ApexAiAnalysisResp answerStock(String question) {
        String code = resolveStockCode(question);
        String name = null;
        if (StringUtils.isBlank(code)) {
            String keyword = extractStockKeyword(question);
            if (StringUtils.isNotBlank(keyword)) {
                List<StockSearchItem> stocks = stockService.search(keyword, 5);
                if (CollUtil.isNotEmpty(stocks)) {
                    code = stocks.get(0).getCode();
                    name = stocks.get(0).getName();
                }
            }
        }
        if (StringUtils.isBlank(code)) {
            return unavailableAnalysis(ApexAiAnalysisTypeEnum.STOCK, "个股研判", "没有识别到明确标的，请提供六位代码或完整股票名称。",
                    List.of(action("查看股票筛选", "/screener", "PRIMARY")));
        }
        StockAnalysisResp analysis = stockAnalysisService.analyze(code, "BUY", 120, false, false);
        if (Objects.isNull(analysis)) {
            return unavailableAnalysis(ApexAiAnalysisTypeEnum.STOCK, "个股研判", "当前没有可用的个股分析数据，请先同步行情后重试。",
                    List.of(action("查看同步中心", "/sync", "PRIMARY")));
        }
        String stockName = defaultText(analysis.getName(), defaultText(name, code));
        StockAnalysisFreshnessResp freshness = analysis.getFreshness();
        String dataNote = defaultText(analysis.getDataNote(), "个股结论基于本地行情、技术面、估值与策略数据。");
        if (Objects.nonNull(freshness) && StringUtils.isNotBlank(freshness.getNote())) {
            dataNote = dataNote + " " + freshness.getNote();
        }
        String dataLevel = Objects.nonNull(freshness) && !Boolean.TRUE.equals(freshness.getBarsStale())
                ? "GREEN" : "YELLOW";
        List<ApexAiContributor> contributors = List.of(ApexAiContributor.builder()
                .rank(1)
                .name(stockName)
                .detail(defaultText(analysis.getActionHint(), defaultText(analysis.getSummary(), "暂无可验证的个股结论")))
                .displayValue(defaultText(analysis.getStance(), "暂无观点"))
                .direction("看多".equals(analysis.getStance()) ? "POSITIVE"
                        : "看空".equals(analysis.getStance()) ? "NEGATIVE" : "NEUTRAL")
                .build());
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(ApexAiAnalysisTypeEnum.STOCK.getCode())
                .title(stockName + " · 个股研判")
                .summary(defaultText(analysis.getSummary(), "当前缺少可验证的个股摘要，请查看完整个股页。"))
                .dataLevel(dataLevel)
                .dataAsOf(Objects.nonNull(freshness) && Objects.nonNull(freshness.getLastBarDate())
                        ? freshness.getLastBarDate().atStartOfDay() : null)
                .dataNote(dataNote)
                .aiEnhanced(false)
                .generatedAt(LocalDateTime.now())
                .metrics(List.of(metric("最新价", amount(analysis.getLatestPrice()), analysis.getLatestPrice(), "元", "NEUTRAL", "以个股页行情时点为准"),
                        metric("当日涨跌", signed(analysis.getPctChg(), 2) + "%", analysis.getPctChg(), "%", tone(analysis.getPctChg()), "最新行情涨跌幅")))
                .contributors(contributors)
                .suggestions(List.of("查看完整个股研判后，再结合市场立场和组合仓位决定是否纳入计划"))
                .actions(List.of(action("查看个股", "/stock/" + code, "PRIMARY"), action("查看今日决策", "/decision", "DEFAULT")))
                .followUpQuestions(List.of("今天大盘怎么样？", "今天应该买什么？", "我的持仓风险怎么样？"))
                .disclaimer(DISCLAIMER)
                .build();
    }

    private ApexAiAnalysisResp answerCapitalFlow() {
        CapitalFlowOverviewResp overview = capitalFlowService.overview(5);
        if (Objects.isNull(overview) || Objects.isNull(overview.getNorthboundFlow())) {
            return unavailableAnalysis(ApexAiAnalysisTypeEnum.CAPITAL_FLOW, "资金面", "当前没有可用资金面快照，请先同步资金数据后重试。",
                    List.of(action("查看资金面", "/market?tab=capital-flow", "PRIMARY"), action("查看同步中心", "/sync", "DEFAULT")));
        }
        BigDecimal netBuyAmount = overview.getNorthboundFlow().getNetBuyAmount();
        String dataStatus = overview.getNorthboundFlow().getDataStatus();
        boolean published = "PUBLISHED".equalsIgnoreCase(dataStatus);
        boolean availableAmount = Objects.nonNull(netBuyAmount);
        String summary = published && availableAmount
                ? "北向资金当日净" + (netBuyAmount.signum() >= 0 ? "买入 " : "卖出 ")
                + amount(netBuyAmount.abs()) + " 元。资金面仅反映已同步数据，需结合市场立场和个股信号复核。"
                : "北向资金当日数据尚未披露，不能据此判断资金净流入方向。";
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(ApexAiAnalysisTypeEnum.CAPITAL_FLOW.getCode())
                .title("资金面变化")
                .summary(summary)
                .dataLevel(published && availableAmount && Objects.nonNull(overview.getNorthboundFlow().getSyncedAt())
                        ? "GREEN" : "YELLOW")
                .dataAsOf(overview.getNorthboundFlow().getSyncedAt())
                .dataNote("北向与主力资金以最近一次同步快照为准。")
                .aiEnhanced(false)
                .generatedAt(LocalDateTime.now())
                .metrics(List.of(metric("北向净额", availableAmount ? amount(netBuyAmount) : "未披露", netBuyAmount,
                        "元", availableAmount ? tone(netBuyAmount) : "NEUTRAL", "最近交易日北向资金净买入额")))
                .suggestions(List.of("先确认资金数据截至时间，再查看资金面榜单与市场立场是否一致"))
                .actions(List.of(action("查看资金面", "/market?tab=capital-flow", "PRIMARY"), action("查看完整行情", "/market", "DEFAULT")))
                .followUpQuestions(List.of("今天大盘怎么样？", "今天应该买什么？"))
                .disclaimer(DISCLAIMER)
                .build();
    }

    private ApexAiAnalysisResp answerNewsPulse() {
        NewsPulseResp pulse = newsPulseService.pulse(5, false);
        if (Objects.isNull(pulse)) {
            return unavailableAnalysis(ApexAiAnalysisTypeEnum.NEWS_PULSE, "消息面", "当前没有可用消息面数据，请先同步资讯后重试。",
                    List.of(action("查看财经资讯", "/news", "PRIMARY")));
        }
        List<ApexAiContributor> contributors = new ArrayList<>();
        if (CollUtil.isNotEmpty(pulse.getCards())) {
            int cardLimit = Math.min(4, pulse.getCards().size());
            for (int index = 0; index < cardLimit; index++) {
                NewsPulseCardResp card = pulse.getCards().get(index);
                contributors.add(ApexAiContributor.builder()
                        .rank(index + 1)
                        .name(defaultText(card.getTitle(), "未命名资讯"))
                        .detail(defaultText(card.getSummary(), "请到资讯页查看原文与来源"))
                        .displayValue(defaultText(card.getSentiment(), "中性"))
                        .direction("利好".equals(card.getSentiment()) ? "POSITIVE"
                                : "利空".equals(card.getSentiment()) ? "NEGATIVE" : "NEUTRAL")
                        .build());
            }
        }
        boolean hasNews = CollUtil.isNotEmpty(pulse.getCards());
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(ApexAiAnalysisTypeEnum.NEWS_PULSE.getCode())
                .title("今日消息面")
                .summary(hasNews ? defaultText(pulse.getExecutiveSummary(), "消息面摘要尚未生成，请结合原始资讯判断影响。")
                        : "当前没有可用消息面数据，请先同步资讯后重试。")
                .dataLevel(hasNews ? "YELLOW" : "RED")
                .dataAsOf(pulse.getSummarizedAt())
                .dataNote(hasNews ? defaultText(pulse.getMessage(), "消息面为资讯聚合，不替代公司公告与交易决策。")
                        : "当前数据不可用，未使用模型补造结论。")
                .aiEnhanced("llm".equalsIgnoreCase(pulse.getSummarySource()))
                .generatedAt(LocalDateTime.now())
                .metrics(List.of(metric("利好", String.valueOf(zero(pulse.getBullCount())), BigDecimal.valueOf(zero(pulse.getBullCount())), "条", "UP", "当前消息面统计"),
                        metric("利空", String.valueOf(zero(pulse.getBearCount())), BigDecimal.valueOf(zero(pulse.getBearCount())), "条", "WARNING", "当前消息面统计")))
                .contributors(contributors)
                .suggestions(List.of("查看原始资讯与公告，避免仅凭标题或情绪标签调整持仓"))
                .actions(List.of(action("查看财经资讯", "/news", "PRIMARY"), action("查看今日决策", "/decision", "DEFAULT")))
                .followUpQuestions(List.of("今天大盘怎么样？", "资金面有什么变化？"))
                .disclaimer(DISCLAIMER)
                .build();
    }

    private ApexAiAnalysisResp unavailableAnalysis(ApexAiAnalysisTypeEnum analysisType, String title,
                                                    String summary, List<ApexAiAction> actions) {
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(analysisType.getCode())
                .title(title)
                .summary(summary)
                .dataLevel("RED")
                .dataNote("当前数据不可用，未使用模型补造结论。")
                .aiEnhanced(false)
                .generatedAt(LocalDateTime.now())
                .suggestions(List.of("完成数据同步后重新提问"))
                .actions(actions)
                .disclaimer(DISCLAIMER)
                .build();
    }

    private ApexAiAnalysisResp answerUnsupportedPortfolioQuestion(PortfolioSummaryResp detail, String question,
                                                                    String dataLevel) {
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(ApexAiAnalysisTypeEnum.PORTFOLIO.getCode())
                .title(detail.getName() + " · 组合数据问答")
                .summary("已收到问题：“" + question + "”。当前组合规则分析只覆盖当日收益归因、个股影响、行业集中度和持仓复核；"
                        + "该问题需要可用的 AI 对话或额外数据源，不能用同一份收益归因结果代替回答。")
                .portfolioId(detail.getId())
                .dataLevel(dataLevel)
                .dataAsOf(detail.getQuoteTime())
                .dataNote("当前问题未匹配可验证的组合规则分析能力")
                .aiEnhanced(false)
                .generatedAt(LocalDateTime.now())
                .followUpQuestions(List.of("为什么今天收益下跌？", "哪只股票对今天收益影响最大？",
                        "当前组合行业集中度是否过高？", "结合今天的决策，我应该先处理哪些持仓？"))
                .disclaimer(DISCLAIMER)
                .build();
    }

    private List<ApexAiMetric> buildPortfolioMetrics(PortfolioSummaryResp detail, BigDecimal totalTodayPnl,
                                                      BigDecimal coverageRate, String dataLevel, int coveredCount,
                                                      int positionCount) {
        return List.of(
                metric("今日收益率", signed(detail.getTodayPct(), 2) + "%", detail.getTodayPct(), "%",
                        tone(detail.getTodayPct()), "持仓当日盈亏 / 昨日持仓市值"),
                metric("今日盈亏", signed(totalTodayPnl, 2) + " 元", totalTodayPnl, "元",
                        tone(totalTodayPnl), "当前有行情持仓的当日浮盈合计"),
                metric("总权益", amount(detail.getTotalEquity()) + " 元", detail.getTotalEquity(), "元",
                        "NEUTRAL", "持仓市值与组合现金之和"),
                metric("行情覆盖", coverageRate.toPlainString() + "%", coverageRate, "%",
                        "GREEN".equals(dataLevel) ? "UP" : "WARNING", coveredCount + "/" + positionCount + " 只持仓"));
    }

    private ApexAiAnalysisResp analyzeStrategy(ApexAiAnalyzeReq request, String question) {
        int days = Objects.nonNull(request.getDays()) ? Math.max(5, Math.min(request.getDays(), 60)) : 60;
        DecisionAttributionResp attribution = decisionService.attribution(days);
        List<DecisionAttrBucket> strategyBuckets = Objects.nonNull(attribution)
                && CollUtil.isNotEmpty(attribution.getByStrategy()) ? attribution.getByStrategy() : List.of();
        DecisionAttrBucket selectedStrategy = null;
        String requestedStrategyId = StringUtils.trim(request.getStrategyId());
        for (DecisionAttrBucket bucket : strategyBuckets) {
            if (StringUtils.isNotBlank(requestedStrategyId) && requestedStrategyId.equalsIgnoreCase(bucket.getKey())) {
                selectedStrategy = bucket;
                break;
            }
            if (StringUtils.isBlank(requestedStrategyId) && StringUtils.isNotBlank(bucket.getKey())
                    && question.toUpperCase().contains(bucket.getKey().toUpperCase())) {
                selectedStrategy = bucket;
                break;
            }
            if (Objects.isNull(selectedStrategy)
                    || compareNullable(bucket.getAvgNextPct(), selectedStrategy.getAvgNextPct()) < 0) {
                selectedStrategy = bucket;
            }
        }
        if (Objects.isNull(selectedStrategy)) {
            return ApexAiAnalysisResp.builder()
                    .requestId(UUID.randomUUID().toString())
                    .analysisType(ApexAiAnalysisTypeEnum.STRATEGY.getCode())
                    .title("策略诊断")
                    .summary("当前没有可用于策略诊断的决策归因样本。请先生成决策，并等待次日或五日收益归因完成。")
                    .strategyId(requestedStrategyId)
                    .dataLevel("RED")
                    .dataNote(Objects.nonNull(attribution) ? attribution.getMessage() : "暂无决策归因")
                    .aiEnhanced(false)
                    .generatedAt(LocalDateTime.now())
                    .suggestions(List.of("先生成并发布真实决策", "至少积累 20 个已计量次日样本后再判断策略是否失效"))
                    .followUpQuestions(List.of("今天有哪些策略信号？", "现有决策样本覆盖了多少交易日？"))
                    .disclaimer(DISCLAIMER)
                    .build();
        }

        DecisionStrategyPerformance maturePerformance = null;
        if (Objects.nonNull(attribution) && CollUtil.isNotEmpty(attribution.getMatureStrategyPerformance())) {
            for (DecisionStrategyPerformance performance : attribution.getMatureStrategyPerformance()) {
                if (selectedStrategy.getKey().equalsIgnoreCase(performance.getStrategyId())) {
                    maturePerformance = performance;
                    break;
                }
            }
        }
        List<ApexAiContributor> evidence = new ArrayList<>();
        evidence.add(ApexAiContributor.builder()
                .rank(1)
                .name("次日信号表现")
                .detail("已计量 " + zero(selectedStrategy.getMeasuredCount()) + " / "
                        + zero(selectedStrategy.getSampleCount()) + " 个样本，胜率 "
                        + signed(selectedStrategy.getWinRate(), 2) + "%")
                .value(selectedStrategy.getAvgNextPct())
                .sampleCount(selectedStrategy.getMeasuredCount())
                .direction(direction(selectedStrategy.getAvgNextPct()))
                .build());
        if (Objects.nonNull(maturePerformance)) {
            BigDecimal excess5dPct = multiplyHundred(maturePerformance.getAvgExcess5d());
            BigDecimal winRate5dPct = multiplyHundred(maturePerformance.getWinRate5d());
            evidence.add(ApexAiContributor.builder()
                    .rank(evidence.size() + 1)
                    .name("成熟五日超额")
                    .detail("近一年完整样本 " + zero(maturePerformance.getSampleCount()) + " 个，超额胜率 "
                            + signed(winRate5dPct, 2) + "%")
                    .value(excess5dPct)
                    .sampleCount(maturePerformance.getSampleCount())
                    .direction(direction(excess5dPct))
                    .build());
        }

        DecisionAttrBucket confluence = findBucket(attribution.getByConfluence(), "共振");
        DecisionAttrBucket nonConfluence = findBucket(attribution.getByConfluence(), "非共振");
        if (Objects.nonNull(confluence) && Objects.nonNull(nonConfluence)
                && compareNullable(nonConfluence.getAvgNextPct(), confluence.getAvgNextPct()) < 0) {
            evidence.add(ApexAiContributor.builder()
                    .rank(evidence.size() + 1)
                    .name("非共振样本拖累")
                    .detail("整体非共振次日均值 " + signed(nonConfluence.getAvgNextPct(), 2)
                            + "% ，低于共振样本 " + signed(confluence.getAvgNextPct(), 2) + "%")
                    .value(nonConfluence.getAvgNextPct().subtract(confluence.getAvgNextPct()))
                    .sampleCount(nonConfluence.getMeasuredCount())
                    .direction("NEGATIVE")
                    .build());
        }
        DecisionAttrBucket mainline = findBucket(attribution.getByMainline(), "主线同向");
        DecisionAttrBucket offMainline = findBucket(attribution.getByMainline(), "非主线");
        if (Objects.nonNull(mainline) && Objects.nonNull(offMainline)
                && compareNullable(offMainline.getAvgNextPct(), mainline.getAvgNextPct()) < 0) {
            evidence.add(ApexAiContributor.builder()
                    .rank(evidence.size() + 1)
                    .name("非主线环境偏弱")
                    .detail("整体非主线次日均值 " + signed(offMainline.getAvgNextPct(), 2)
                            + "% ，低于主线同向 " + signed(mainline.getAvgNextPct(), 2) + "%")
                    .value(offMainline.getAvgNextPct().subtract(mainline.getAvgNextPct()))
                    .sampleCount(offMainline.getMeasuredCount())
                    .direction("NEGATIVE")
                    .build());
        }
        DecisionAttrBucket weakestStance = weakestMeasuredBucket(attribution.getByStance());
        if (Objects.nonNull(weakestStance) && Objects.nonNull(weakestStance.getAvgNextPct())
                && weakestStance.getAvgNextPct().signum() < 0) {
            evidence.add(ApexAiContributor.builder()
                    .rank(evidence.size() + 1)
                    .name(weakestStance.getKey() + "环境承压")
                    .detail("该市场立场下整体次日均值 " + signed(weakestStance.getAvgNextPct(), 2)
                            + "% ，样本 " + zero(weakestStance.getMeasuredCount()) + " 个")
                    .value(weakestStance.getAvgNextPct())
                    .sampleCount(weakestStance.getMeasuredCount())
                    .direction("NEGATIVE")
                    .build());
        }

        int measuredCount = zero(selectedStrategy.getMeasuredCount());
        String deterministicSummary = selectedStrategy.getKey() + " 在近 " + attribution.getDays()
                + " 个决策日中有 " + measuredCount + " 个已计量样本，次日平均 "
                + signed(selectedStrategy.getAvgNextPct(), 2) + "% ，胜率 "
                + signed(selectedStrategy.getWinRate(), 2) + "% 。"
                + (Objects.nonNull(maturePerformance)
                ? "成熟五日样本的平均超额为 " + signed(multiplyHundred(maturePerformance.getAvgExcess5d()), 2) + "% 。"
                : "成熟五日超额样本尚不足，暂不能判断中期有效性。");
        String dataLevel = measuredCount >= 20 ? "GREEN" : measuredCount >= 5 ? "YELLOW" : "RED";
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(ApexAiAnalysisTypeEnum.STRATEGY.getCode())
                .title(selectedStrategy.getKey() + " · 策略有效性诊断")
                .summary(deterministicSummary)
                .strategyId(selectedStrategy.getKey())
                .dataLevel(dataLevel)
                .dataNote(attribution.getMessage())
                .aiEnhanced(false)
                .generatedAt(LocalDateTime.now())
                .metrics(buildStrategyMetrics(selectedStrategy, maturePerformance))
                .contributors(evidence)
                .suggestions(buildStrategySuggestions(confluence, nonConfluence, mainline, offMainline, measuredCount))
                .followUpQuestions(List.of(
                        selectedStrategy.getKey() + " 在什么市场立场下表现最差？",
                        "共振和非共振信号的差距有多大？",
                        "成熟五日超额样本是否足够支持降权？"))
                .disclaimer(DISCLAIMER)
                .build();
    }

    private ApexAiAnalysisResp answerGeneral(String question) {
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(ApexAiAnalysisTypeEnum.GENERAL.getCode())
                .title("小灵 · Apex 数据问答")
                .summary("已收到问题：“" + question + "”。当前规则分析没有匹配到组合收益或策略归因场景，"
                        + "可继续使用 AI 增强回答，或切换到组合、策略分析范围获取结构化证据。")
                .dataLevel("YELLOW")
                .dataNote("当前问题未匹配结构化组合或策略分析")
                .aiEnhanced(false)
                .generatedAt(LocalDateTime.now())
                .followUpQuestions(List.of("今天大盘怎么样？", "我的持仓风险怎么样？", "今天应该买什么？"))
                .disclaimer(DISCLAIMER)
                .build();
    }

    private ApexAiAnalysisResp emptyPortfolioAnalysis() {
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(ApexAiAnalysisTypeEnum.PORTFOLIO.getCode())
                .title("组合收益归因")
                .summary("当前没有可分析的个人组合。请先创建组合并录入持仓。")
                .dataLevel("RED")
                .dataNote("未找到当前用户可编辑组合")
                .aiEnhanced(false)
                .generatedAt(LocalDateTime.now())
                .suggestions(List.of("创建默认组合", "录入持仓并刷新行情后重新分析"))
                .disclaimer(DISCLAIMER)
                .build();
    }

    private List<String> buildPortfolioSuggestions(PortfolioSummaryResp detail,
                                                   List<ApexAiContributor> contributors,
                                                   int missingQuoteCount,
                                                   BigDecimal residualValue) {
        List<String> suggestions = new ArrayList<>();
        if (missingQuoteCount > 0) {
            suggestions.add("先刷新 " + missingQuoteCount + " 只缺失行情的持仓，再确认完整归因");
        }
        if (CollUtil.isNotEmpty(contributors) && contributors.get(0).getSampleCount() > 1
                && contributors.get(0).getValue().signum() < 0) {
            suggestions.add("复核" + contributors.get(0).getName() + "持仓是否超过既定行业风险预算");
        }
        if (residualValue.abs().compareTo(new BigDecimal("0.01")) > 0) {
            suggestions.add("检查未分类持仓、现金收益和当日已卖出证券，解释剩余 " + amount(residualValue) + " 元差异");
        }
        if (CollUtil.isEmpty(suggestions)) {
            suggestions.add(Objects.nonNull(detail.getTodayPnl()) && detail.getTodayPnl().signum() < 0
                    ? "先处理贡献最负的持仓，再评估是否调整行业暴露"
                    : "保持现有风险预算，并观察主要正贡献能否延续");
        }
        return suggestions;
    }

    private List<ApexAiMetric> buildStrategyMetrics(DecisionAttrBucket strategy,
                                                    DecisionStrategyPerformance maturePerformance) {
        List<ApexAiMetric> metrics = new ArrayList<>();
        metrics.add(metric("次日均值", signed(strategy.getAvgNextPct(), 2) + "%", strategy.getAvgNextPct(), "%",
                tone(strategy.getAvgNextPct()), "仅统计已有下一交易日行情的建议"));
        metrics.add(metric("次日胜率", signed(strategy.getWinRate(), 2) + "%", strategy.getWinRate(), "%",
                Objects.nonNull(strategy.getWinRate()) && strategy.getWinRate().compareTo(new BigDecimal("50")) >= 0
                        ? "UP" : "WARNING", "次日涨幅大于 0 的样本占比"));
        metrics.add(metric("有效样本", zero(strategy.getMeasuredCount()) + " / " + zero(strategy.getSampleCount()),
                BigDecimal.valueOf(zero(strategy.getMeasuredCount())), "个", "NEUTRAL", "已计量样本 / 全部策略建议"));
        if (Objects.nonNull(maturePerformance)) {
            BigDecimal excessPct = multiplyHundred(maturePerformance.getAvgExcess5d());
            metrics.add(metric("五日平均超额", signed(excessPct, 2) + "%", excessPct, "%",
                    tone(excessPct), "近一年正式发布建议相对沪深300的成熟样本"));
        } else {
            metrics.add(metric("五日平均超额", "样本不足", null, "%", "WARNING", "尚无成熟五日超额样本"));
        }
        return metrics;
    }

    private List<String> buildStrategySuggestions(DecisionAttrBucket confluence,
                                                  DecisionAttrBucket nonConfluence,
                                                  DecisionAttrBucket mainline,
                                                  DecisionAttrBucket offMainline,
                                                  int measuredCount) {
        List<String> suggestions = new ArrayList<>();
        if (measuredCount < 20) {
            suggestions.add("当前有效样本少于 20 个，先降置信度，不做永久停用判断");
        }
        if (Objects.nonNull(confluence) && Objects.nonNull(nonConfluence)
                && compareNullable(nonConfluence.getAvgNextPct(), confluence.getAvgNextPct()) < 0) {
            suggestions.add("降低非共振信号权重，优先保留多策略共振样本");
        }
        if (Objects.nonNull(mainline) && Objects.nonNull(offMainline)
                && compareNullable(offMainline.getAvgNextPct(), mainline.getAvgNextPct()) < 0) {
            suggestions.add("提高主线同向门槛，对非主线信号缩小风险预算");
        }
        if (CollUtil.isEmpty(suggestions)) {
            suggestions.add("保持参数不变继续积累样本，并用五日超额确认短期波动是否持续");
        }
        return suggestions;
    }

    private ApexAiMetric metric(String label, String value, BigDecimal numericValue,
                                String unit, String tone, String detail) {
        return ApexAiMetric.builder()
                .label(label)
                .value(value)
                .numericValue(numericValue)
                .unit(unit)
                .tone(tone)
                .detail(detail)
                .build();
    }

    private ApexAiAction action(String label, String route, String tone) {
        return ApexAiAction.builder()
                .label(label)
                .route(route)
                .tone(tone)
                .build();
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.isNotBlank(value) ? value : defaultValue;
    }

    private String percent(BigDecimal value) {
        return Objects.nonNull(value) ? value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%" : "--";
    }

    private boolean isStockQuestion(String question) {
        return STOCK_CODE_PATTERN.matcher(question).find()
                || containsAny(question, "买", "卖", "持有", "个股", "股票", "分析一下");
    }

    private String resolveStockCode(String question) {
        Matcher matcher = STOCK_CODE_PATTERN.matcher(question);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractStockKeyword(String question) {
        String keyword = question;
        String[] stopWords = {"请问", "帮我", "看一下", "分析一下", "今天", "现在", "目前", "股票", "个股",
                "还能买吗", "能买吗", "买不买", "该买吗", "能卖吗", "持有吗", "怎么样", "怎么办", "看看", "分析",
                "是否", "可以", "值得", "适合", "能不能", "应该", "买吗", "卖吗", "如何", "为什么", "还能", "？", "?", "。", "，", ","};
        for (String stopWord : stopWords) {
            keyword = keyword.replace(stopWord, "");
        }
        keyword = keyword.trim();
        return keyword.length() >= 2 && keyword.length() <= 20 ? keyword : null;
    }

    private DecisionAttrBucket findBucket(List<DecisionAttrBucket> buckets, String key) {
        if (CollUtil.isEmpty(buckets)) {
            return null;
        }
        for (DecisionAttrBucket bucket : buckets) {
            if (key.equals(bucket.getKey())) {
                return bucket;
            }
        }
        return null;
    }

    private DecisionAttrBucket weakestMeasuredBucket(List<DecisionAttrBucket> buckets) {
        DecisionAttrBucket weakest = null;
        if (CollUtil.isEmpty(buckets)) {
            return null;
        }
        for (DecisionAttrBucket bucket : buckets) {
            if (Objects.isNull(bucket.getAvgNextPct()) || zero(bucket.getMeasuredCount()) == 0) {
                continue;
            }
            if (Objects.isNull(weakest) || bucket.getAvgNextPct().compareTo(weakest.getAvgNextPct()) < 0) {
                weakest = bucket;
            }
        }
        return weakest;
    }

    private int compareNullable(BigDecimal first, BigDecimal second) {
        if (Objects.isNull(first)) {
            return Objects.isNull(second) ? 0 : 1;
        }
        return Objects.isNull(second) ? -1 : first.compareTo(second);
    }

    private BigDecimal multiplyHundred(BigDecimal value) {
        return Objects.nonNull(value) ? value.multiply(BigDecimal.valueOf(100)) : null;
    }

    private String tone(BigDecimal value) {
        if (Objects.isNull(value) || value.signum() == 0) {
            return "NEUTRAL";
        }
        return value.signum() > 0 ? "UP" : "DOWN";
    }

    private String direction(BigDecimal value) {
        if (Objects.isNull(value) || value.signum() == 0) {
            return "NEUTRAL";
        }
        return value.signum() > 0 ? "POSITIVE" : "NEGATIVE";
    }

    private String signed(BigDecimal value, int scale) {
        if (Objects.isNull(value)) {
            return "--";
        }
        BigDecimal scaled = value.setScale(scale, RoundingMode.HALF_UP);
        return (scaled.signum() > 0 ? "+" : "") + scaled.toPlainString();
    }

    private String amount(BigDecimal value) {
        return Objects.nonNull(value) ? value.setScale(2, RoundingMode.HALF_UP).toPlainString() : "--";
    }

    private int zero(Integer value) {
        return Objects.nonNull(value) ? value : 0;
    }

    private boolean containsAny(String source, String... keywords) {
        if (StringUtils.isBlank(source)) {
            return false;
        }
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
