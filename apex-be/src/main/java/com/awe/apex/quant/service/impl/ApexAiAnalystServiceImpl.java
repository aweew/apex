package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.bot.service.IBotQuestionService;
import com.awe.apex.quant.domain.bo.ApexAiIndustryAttributionBO;
import com.awe.apex.quant.domain.dto.ApexAiAnalysisResp;
import com.awe.apex.quant.domain.dto.ApexAiAnalyzeReq;
import com.awe.apex.quant.domain.dto.ApexAiContextResp;
import com.awe.apex.quant.domain.dto.ApexAiContributor;
import com.awe.apex.quant.domain.dto.ApexAiMetric;
import com.awe.apex.quant.domain.dto.ApexAiPortfolioOption;
import com.awe.apex.quant.domain.dto.ApexAiStrategyOption;
import com.awe.apex.quant.domain.dto.BotAskReq;
import com.awe.apex.quant.domain.dto.BotAskResp;
import com.awe.apex.quant.domain.dto.DecisionAttrBucket;
import com.awe.apex.quant.domain.dto.DecisionAttributionResp;
import com.awe.apex.quant.domain.dto.DecisionStrategyPerformance;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.domain.enums.ApexAiAnalysisTypeEnum;
import com.awe.apex.quant.service.IApexAiAnalystService;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IPortfolioService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Apex AI 分析服务实现
 */
@Slf4j
@Service
public class ApexAiAnalystServiceImpl implements IApexAiAnalystService {

    private static final String DISCLAIMER = "以上基于 Apex 当前数据生成，仅供研究，不构成投资建议。";

    @Resource
    private IPortfolioService portfolioService;

    @Resource
    private IDecisionService decisionService;

    @Resource
    private IBotQuestionService botQuestionService;

    @Resource
    private KimiChatClient kimiChatClient;

    /**
     * 查询工作台可用分析上下文
     *
     * @return 分析上下文
     */
    @Override
    public ApexAiContextResp context() {
        List<ApexAiPortfolioOption> portfolioOptions = new ArrayList<>();
        List<PortfolioSummaryResp> portfolios = portfolioService.listPortfolios(false);
        if (CollUtil.isNotEmpty(portfolios)) {
            for (PortfolioSummaryResp portfolio : portfolios) {
                if (!Boolean.TRUE.equals(portfolio.getEditable())) {
                    continue;
                }
                portfolioOptions.add(ApexAiPortfolioOption.builder()
                        .id(portfolio.getId())
                        .name(portfolio.getName())
                        .defaultPortfolio(portfolio.getIsDefault())
                        .positionCount(portfolio.getPositionCount())
                        .build());
            }
        }

        DecisionAttributionResp attribution = decisionService.attribution(60);
        List<ApexAiStrategyOption> strategyOptions = new ArrayList<>();
        if (Objects.nonNull(attribution) && CollUtil.isNotEmpty(attribution.getByStrategy())) {
            for (DecisionAttrBucket bucket : attribution.getByStrategy()) {
                strategyOptions.add(ApexAiStrategyOption.builder()
                        .strategyId(bucket.getKey())
                        .strategyName(StringUtils.isNotBlank(bucket.getLabel()) ? bucket.getLabel() : bucket.getKey())
                        .measuredCount(bucket.getMeasuredCount())
                        .avgNextPct(bucket.getAvgNextPct())
                        .winRate(bucket.getWinRate())
                        .build());
            }
            strategyOptions.sort(Comparator.comparing(ApexAiStrategyOption::getAvgNextPct,
                    Comparator.nullsLast(Comparator.naturalOrder())));
        }
        return ApexAiContextResp.builder()
                .aiConfigured(kimiChatClient.available())
                .portfolios(portfolioOptions)
                .strategies(strategyOptions)
                .recommendedQuestions(List.of(
                        "为什么今天收益下跌？",
                        "今天哪些板块拖累了组合？",
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
        if (Objects.isNull(request) || StringUtils.isBlank(request.getQuestion())) {
            throw new BusinessException("问题不能为空");
        }
        String question = request.getQuestion().trim();
        ApexAiAnalysisTypeEnum analysisType = ApexAiAnalysisTypeEnum.of(request.getAnalysisType());
        if (analysisType == ApexAiAnalysisTypeEnum.AUTO) {
            if (containsAny(question, "策略", "失效", "胜率", "样本", "共振", "超额")) {
                analysisType = ApexAiAnalysisTypeEnum.STRATEGY;
            } else if (containsAny(question, "组合", "收益", "盈亏", "亏", "赚", "板块", "持仓")) {
                analysisType = ApexAiAnalysisTypeEnum.PORTFOLIO;
            } else {
                analysisType = ApexAiAnalysisTypeEnum.GENERAL;
            }
        }
        return switch (analysisType) {
            case PORTFOLIO -> analyzePortfolio(request, question);
            case STRATEGY -> analyzeStrategy(request, question);
            case GENERAL, AUTO -> answerGeneral(question);
        };
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
        String enhancedSummary = enhanceSummary(deterministicSummary,
                "组合当日盈亏=" + totalTodayPnl + "；归因残差=" + residualValue
                        + "；行情覆盖=" + coveredCount + "/" + positionCount + "；问题=" + question);
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(ApexAiAnalysisTypeEnum.PORTFOLIO.getCode())
                .title(detail.getName() + " · 今日收益归因")
                .summary(StringUtils.isNotBlank(enhancedSummary) ? enhancedSummary : deterministicSummary)
                .portfolioId(detail.getId())
                .totalValue(totalTodayPnl)
                .residualValue(residualValue)
                .dataLevel(dataLevel)
                .dataAsOf(detail.getQuoteTime())
                .dataNote("按持仓行业聚合当日浮盈，以昨日持仓市值计算收益贡献；不含现金收益和已卖出证券的盘后影响。")
                .aiEnhanced(StringUtils.isNotBlank(enhancedSummary))
                .generatedAt(LocalDateTime.now())
                .metrics(List.of(
                        metric("今日收益率", signed(detail.getTodayPct(), 2) + "%", detail.getTodayPct(), "%",
                                tone(detail.getTodayPct()), "持仓当日盈亏 / 昨日持仓市值"),
                        metric("今日盈亏", signed(totalTodayPnl, 2) + " 元", totalTodayPnl, "元",
                                tone(totalTodayPnl), "当前有行情持仓的当日浮盈合计"),
                        metric("总权益", amount(detail.getTotalEquity()) + " 元", detail.getTotalEquity(), "元",
                                "NEUTRAL", "持仓市值与组合现金之和"),
                        metric("行情覆盖", coverageRate.toPlainString() + "%", coverageRate, "%",
                                "GREEN".equals(dataLevel) ? "UP" : "WARNING", coveredCount + "/" + positionCount + " 只持仓")))
                .contributors(contributors)
                .suggestions(buildPortfolioSuggestions(detail, contributors, missingQuoteCount, residualValue))
                .followUpQuestions(List.of(
                        "哪只股票对今天收益影响最大？",
                        "当前组合行业集中度是否过高？",
                        "结合今天的决策，我应该先处理哪些持仓？"))
                .disclaimer(DISCLAIMER)
                .build();
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
        String enhancedSummary = enhanceSummary(deterministicSummary,
                "策略=" + selectedStrategy.getKey() + "；问题=" + question + "；证据项=" + evidence.size()
                        + "；仅能引用已有次日和五日归因，不得补造因子IC或行情状态切换");
        String dataLevel = measuredCount >= 20 ? "GREEN" : measuredCount >= 5 ? "YELLOW" : "RED";
        return ApexAiAnalysisResp.builder()
                .requestId(UUID.randomUUID().toString())
                .analysisType(ApexAiAnalysisTypeEnum.STRATEGY.getCode())
                .title(selectedStrategy.getKey() + " · 策略有效性诊断")
                .summary(StringUtils.isNotBlank(enhancedSummary) ? enhancedSummary : deterministicSummary)
                .strategyId(selectedStrategy.getKey())
                .dataLevel(dataLevel)
                .dataNote(attribution.getMessage())
                .aiEnhanced(StringUtils.isNotBlank(enhancedSummary))
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
        BotAskResp botResponse = botQuestionService.ask(BotAskReq.builder()
                .requestId(UUID.randomUUID().toString())
                .userId("apex-web")
                .conversationId("apex-ai")
                .question(question)
                .build());
        return ApexAiAnalysisResp.builder()
                .requestId(botResponse.getRequestId())
                .analysisType(ApexAiAnalysisTypeEnum.GENERAL.getCode())
                .title("小灵 · Apex 数据问答")
                .summary(botResponse.getAnswer())
                .dataLevel(botResponse.getDataLevel())
                .dataNote(StringUtils.isNotBlank(botResponse.getDataAsOf())
                        ? "数据截至 " + botResponse.getDataAsOf() : "基于 Apex 当前可用数据")
                .aiEnhanced(Boolean.TRUE.equals(botResponse.getAiEnhanced()))
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

    private String enhanceSummary(String deterministicSummary, String evidence) {
        if (!kimiChatClient.available()) {
            return null;
        }
        String systemPrompt = "你是 Apex AI Analyst 小灵。只根据给定的 Apex 聚合指标解释，"
                + "不得补造数字、因子IC、市场状态、交易记录或因果关系。使用简洁中文，先给结论，再给证据。";
        String userPrompt = "规则分析：" + deterministicSummary + "\n证据约束：" + evidence
                + "\n请在 180 字内给出分析，不要重复免责声明。";
        String enhanced = kimiChatClient.chat(systemPrompt, userPrompt, 500);
        if (StringUtils.isBlank(enhanced)) {
            return null;
        }
        return enhanced.trim();
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
