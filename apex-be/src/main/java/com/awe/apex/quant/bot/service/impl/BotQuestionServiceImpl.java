package com.awe.apex.quant.bot.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.bot.service.IBotQuestionService;
import com.awe.apex.quant.bot.service.IBotHoldingRiskService;
import com.awe.apex.quant.domain.dto.BotAskReq;
import com.awe.apex.quant.domain.dto.BotAskResp;
import com.awe.apex.quant.domain.dto.BotHoldingRiskItem;
import com.awe.apex.quant.domain.dto.BotHoldingRiskResp;
import com.awe.apex.quant.domain.dto.DecisionAdviceActionResp;
import com.awe.apex.quant.domain.dto.DecisionAdviceResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.dto.PortfolioTopHoldingResp;
import com.awe.apex.quant.domain.dto.StockAnalysisAiResp;
import com.awe.apex.quant.domain.dto.StockAnalysisFreshnessResp;
import com.awe.apex.quant.domain.dto.StockAnalysisResp;
import com.awe.apex.quant.domain.dto.StockSearchItem;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.service.IStockAnalysisService;
import com.awe.apex.quant.service.IStockService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ClawBot 股票问答服务实现。
 */
@Service
@Slf4j
public class BotQuestionServiceImpl implements IBotQuestionService {

    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");
    private static final String DISCLAIMER = "以上基于 Apex 当前数据生成，仅供研究，不构成投资建议。";

    @Resource
    private IStockService stockService;

    @Resource
    private IStockAnalysisService stockAnalysisService;

    @Resource
    private IMarketBriefingService marketBriefingService;

    @Resource
    private IBotHoldingRiskService botHoldingRiskService;

    @Resource
    private IPortfolioService portfolioService;

    @Resource
    private IDecisionService decisionService;

    /**
     * 识别问题意图并读取 Apex 业务数据生成回答。
     *
     * @param request 问答请求
     * @return 问答响应
     */
    @Override
    public BotAskResp ask(BotAskReq request) {
        String question = request.getQuestion().trim();
        String requestId = StringUtils.isNotBlank(request.getRequestId())
                ? request.getRequestId().trim() : UUID.randomUUID().toString();

        // 1. 明确股票代码优先，避免通用买卖关键词覆盖个股问题
        Matcher codeMatcher = STOCK_CODE_PATTERN.matcher(question);
        if (codeMatcher.find()) {
            return answerStock(requestId, codeMatcher.group(1), null);
        }

        // 2. 市场问题不做股票名称猜测
        if (containsAny(question, "大盘", "市场", "指数", "热点", "板块", "赚钱效应", "成交量")) {
            return answerMarket(requestId);
        }

        // 3. 优先匹配真实组合名称，再处理默认持仓和通用决策
        long portfolioListStartedAt = System.nanoTime();
        List<PortfolioSummaryResp> portfolios = portfolioService.listPortfolios(false);
        log.info("Bot 组合列表查询完成 requestId={} portfolioCount={} durationMs={}",
                requestId, portfolios.size(), elapsedMillis(portfolioListStartedAt));
        if (CollUtil.isNotEmpty(portfolios)) {
            for (PortfolioSummaryResp portfolio : portfolios) {
                if (StringUtils.isNotBlank(portfolio.getName()) && question.contains(portfolio.getName())) {
                    return answerPortfolio(requestId, portfolio.getId(), containsAny(question, "建议", "怎么操作", "怎么办", "买卖"));
                }
            }
        }
        if (containsAny(question, "今天", "今日")
                && containsAny(question, "盈亏", "赚多少", "亏多少", "赚了多少", "亏了多少")) {
            for (PortfolioSummaryResp portfolio : portfolios) {
                if (Boolean.TRUE.equals(portfolio.getIsDefault())) {
                    return answerPortfolioTodayPnl(requestId, portfolio.getId());
                }
            }
            return BotAskResp.builder()
                    .requestId(requestId)
                    .intent("PORTFOLIO_TODAY_PNL")
                    .answer("还没有可用于计算的默认组合。请先在 Apex 录入持仓后再查询今日盈亏。\n" + DISCLAIMER)
                    .dataLevel("YELLOW")
                    .aiEnhanced(false)
                    .build();
        }
        if (containsAny(question, "我的持仓", "持仓风险", "组合风险", "总体风险", "仓位", "浮亏", "浮盈")) {
            return answerPortfolioRisk(requestId);
        }
        if (containsAny(question, "今天买什么", "今天卖什么", "今日决策", "怎么操作", "应该买什么", "应该卖什么")) {
            return answerDecision(requestId);
        }

        // 4. 尝试从自然语言中抽取股票名称
        String stockKeyword = extractStockKeyword(question);
        if (StringUtils.isNotBlank(stockKeyword)) {
            List<StockSearchItem> stocks = stockService.search(stockKeyword, 5);
            if (CollUtil.isNotEmpty(stocks)) {
                StockSearchItem stock = stocks.get(0);
                return answerStock(requestId, stock.getCode(), stock.getName());
            }
        }

        return BotAskResp.builder()
                .requestId(requestId)
                .intent("UNRECOGNIZED")
                .answer("我暂时无法确定你要查的标的。可以问：\n"
                        + "1. 300750 现在能买吗\n"
                        + "2. 今天大盘怎么样\n"
                        + "3. 我的持仓风险怎么样\n"
                        + "4. 今天应该买什么\n\n" + DISCLAIMER)
                .dataLevel("YELLOW")
                .aiEnhanced(false)
                .build();
    }

    private BotAskResp answerStock(String requestId, String code, String fallbackName) {
        long startedAt = System.nanoTime();
        StockAnalysisResp analysis = stockAnalysisService.analyze(code, "BUY", 120, true, false);
        log.info("Bot 个股分析查询完成 requestId={} code={} durationMs={}",
                requestId, code, elapsedMillis(startedAt));
        String name = StringUtils.isNotBlank(analysis.getName()) ? analysis.getName() : fallbackName;
        StringBuilder answer = new StringBuilder();
        answer.append(StringUtils.isNotBlank(name) ? name : code).append("（").append(code).append("）\n");
        answer.append("结论：").append(defaultText(analysis.getStance(), "暂无明确立场")).append("。")
                .append(defaultText(analysis.getSummary(), "暂无摘要")).append("\n");
        if (Objects.nonNull(analysis.getLatestPrice())) {
            answer.append("现价：").append(analysis.getLatestPrice());
            if (Objects.nonNull(analysis.getPctChg())) {
                answer.append("，涨跌幅 ").append(analysis.getPctChg()).append("%");
            }
            answer.append("\n");
        }
        if (StringUtils.isNotBlank(analysis.getActionHint())) {
            answer.append("操作：").append(analysis.getActionHint()).append("\n");
        }
        if (CollUtil.isNotEmpty(analysis.getRiskFlags())) {
            answer.append("风险：").append(String.join("；", analysis.getRiskFlags())).append("\n");
        }
        StockAnalysisAiResp ai = analysis.getAi();
        boolean aiEnhanced = Objects.nonNull(ai) && StringUtils.isNotBlank(ai.getBrief());
        if (aiEnhanced) {
            answer.append("AI补充：").append(ai.getBrief()).append("\n");
        }
        StockAnalysisFreshnessResp freshness = analysis.getFreshness();
        String dataAsOf = Objects.nonNull(freshness) && Objects.nonNull(freshness.getLastBarDate())
                ? freshness.getLastBarDate().toString() : null;
        String dataLevel = Objects.isNull(freshness) || Objects.isNull(freshness.getLastBarDate())
                ? "YELLOW" : (Boolean.TRUE.equals(freshness.getBarsStale()) ? "RED" : "GREEN");
        if (Objects.nonNull(freshness) && StringUtils.isNotBlank(freshness.getNote())) {
            answer.append("数据：").append(freshness.getNote()).append("\n");
        }
        if (StringUtils.isNotBlank(dataAsOf)) {
            answer.append("截至：").append(dataAsOf).append("\n");
        }
        answer.append(DISCLAIMER);
        return BotAskResp.builder()
                .requestId(requestId)
                .intent("STOCK_ANALYSIS")
                .stockCode(code)
                .stockName(name)
                .answer(answer.toString())
                .dataAsOf(dataAsOf)
                .dataLevel(dataLevel)
                .aiEnhanced(aiEnhanced)
                .build();
    }

    private BotAskResp answerMarket(String requestId) {
        long startedAt = System.nanoTime();
        MarketBriefingResp briefing = marketBriefingService.briefing(false);
        log.info("Bot 市场简报查询完成 requestId={} durationMs={}", requestId, elapsedMillis(startedAt));
        StringBuilder answer = new StringBuilder("今日市场\n");
        answer.append("立场：").append(defaultText(briefing.getStance(), "暂无")).append("。")
                .append(defaultText(briefing.getStanceReason(), "暂无市场说明")).append("\n");
        answer.append("仓位：").append(defaultText(briefing.getPositionAdvice(), "暂无仓位建议")).append("\n");
        if (CollUtil.isNotEmpty(briefing.getHotThemes())) {
            answer.append("热点：").append(String.join("、", briefing.getHotThemes())).append("\n");
        }
        if (StringUtils.isNotBlank(briefing.getMessage())) {
            answer.append("数据：").append(briefing.getMessage()).append("\n");
        }
        answer.append(DISCLAIMER);
        return BotAskResp.builder()
                .requestId(requestId)
                .intent("MARKET_BRIEFING")
                .answer(answer.toString())
                .dataAsOf(Objects.nonNull(briefing.getAsOf()) ? briefing.getAsOf().toString() : null)
                .dataLevel(defaultText(briefing.getDataLevel(), "YELLOW"))
                .aiEnhanced(false)
                .build();
    }

    private BotAskResp answerPortfolioRisk(String requestId) {
        long startedAt = System.nanoTime();
        BotHoldingRiskResp risk = botHoldingRiskService.analyze();
        log.info("Bot 持仓风险查询完成 requestId={} holdingCount={} durationMs={}",
                requestId, defaultInteger(risk.getHoldingCount()), elapsedMillis(startedAt));
        StringBuilder answer = new StringBuilder("持仓风险\n");
        answer.append("持仓：").append(defaultInteger(risk.getHoldingCount())).append(" 只，")
                .append(defaultInteger(risk.getQuotedCount())).append(" 只有有效行情\n");
        answer.append("告警：CRITICAL ").append(defaultInteger(risk.getCriticalCount()))
                .append("，WARN ").append(defaultInteger(risk.getWarnCount())).append("\n");
        if (CollUtil.isNotEmpty(risk.getAlerts())) {
            int limit = Math.min(5, risk.getAlerts().size());
            for (int index = 0; index < limit; index++) {
                BotHoldingRiskItem alert = risk.getAlerts().get(index);
                answer.append("- [").append(defaultText(alert.getLevel(), "INFO")).append("] ")
                        .append(defaultText(alert.getName(), alert.getCode())).append("：")
                        .append(alert.getMessage()).append("\n");
            }
        } else {
            answer.append("当前没有触发风控告警。\n");
        }
        if (StringUtils.isNotBlank(risk.getDataAsOf())) {
            answer.append("行情截至：").append(risk.getDataAsOf()).append("\n");
        }
        answer.append(DISCLAIMER);
        String dataLevel = defaultInteger(risk.getCriticalCount()) > 0 ? "RED"
                : (defaultInteger(risk.getWarnCount()) > 0 ? "YELLOW" : "GREEN");
        return BotAskResp.builder()
                .requestId(requestId)
                .intent("PORTFOLIO_RISK")
                .answer(answer.toString())
                .dataAsOf(risk.getDataAsOf())
                .dataLevel(dataLevel)
                .aiEnhanced(false)
                .build();
    }

    private BotAskResp answerPortfolio(String requestId, Long portfolioId, boolean includeAdvice) {
        long startedAt = System.nanoTime();
        PortfolioSummaryResp portfolio = portfolioService.detail(portfolioId);
        log.info("Bot 组合详情查询完成 requestId={} portfolioId={} includeAdvice={} positionCount={} durationMs={}",
                requestId, portfolioId, includeAdvice, defaultInteger(portfolio.getPositionCount()),
                elapsedMillis(startedAt));
        StringBuilder answer = new StringBuilder();
        answer.append("组合：").append(portfolio.getName()).append("\n");
        answer.append("总权益：").append(defaultAmount(portfolio.getTotalEquity()))
                .append("，现金：").append(defaultAmount(portfolio.getCashBalance()))
                .append("，持仓：").append(defaultInteger(portfolio.getPositionCount())).append(" 只\n");
        answer.append("累计浮盈：").append(defaultAmount(portfolio.getTotalPnl()))
                .append("，今日浮盈：").append(defaultAmount(portfolio.getTodayPnl()));
        if (Objects.nonNull(portfolio.getTodayPct())) {
            answer.append("（").append(portfolio.getTodayPct()).append("%）");
        }
        answer.append("\n");
        if (includeAdvice && Objects.nonNull(portfolio.getBrief())) {
            answer.append("组合立场：").append(defaultText(portfolio.getBrief().getStance(), "暂无"))
                    .append("。 ").append(defaultText(portfolio.getBrief().getSummary(), "暂无组合研判")).append("\n");
            if (CollUtil.isNotEmpty(portfolio.getBrief().getActions())) {
                answer.append("优先操作：\n");
                for (var action : portfolio.getBrief().getActions()) {
                    answer.append("- ").append(action.getText()).append("\n");
                }
            }
        }
        if (includeAdvice && CollUtil.isNotEmpty(portfolio.getHoldings())) {
            answer.append("单票建议：\n");
            int count = 0;
            for (var holding : portfolio.getHoldings()) {
                if (count >= 8) {
                    break;
                }
                answer.append("- ").append(defaultText(holding.getName(), holding.getCode())).append("（")
                        .append(holding.getCode()).append("）：").append(defaultText(holding.getVerdict(), "数据不足"));
                if (StringUtils.isNotBlank(holding.getAdvice())) {
                    answer.append("，").append(holding.getAdvice());
                }
                if (Objects.nonNull(holding.getWeightPct())) {
                    answer.append("，仓位 ").append(holding.getWeightPct()).append("%");
                }
                if (Objects.nonNull(holding.getStopLoss())) {
                    answer.append("，止损 ").append(holding.getStopLoss());
                }
                answer.append("\n");
                count++;
            }
        } else if (CollUtil.isNotEmpty(portfolio.getTopHoldings())) {
            answer.append("主要持仓：\n");
            for (PortfolioTopHoldingResp holding : portfolio.getTopHoldings()) {
                answer.append("- ").append(defaultText(holding.getName(), holding.getCode()));
                if (Objects.nonNull(holding.getWeightPct())) {
                    answer.append("，仓位 ").append(holding.getWeightPct()).append("%");
                }
                if (Objects.nonNull(holding.getPctChg())) {
                    answer.append("，今日 ").append(holding.getPctChg()).append("%");
                }
                answer.append("\n");
            }
        }
        if (Objects.nonNull(portfolio.getQuoteTime())) {
            answer.append("行情最早时间：").append(portfolio.getQuoteTime()).append("\n");
            if (isQuoteExpired(portfolio.getQuoteTime())) {
                answer.append("行情已超过24小时未更新，建议可靠性下降。\n");
            }
        }
        if (defaultInteger(portfolio.getMissingQuoteCount()) > 0) {
            answer.append("缺少行情：").append(portfolio.getMissingQuoteCount()).append(" 只，建议可靠性下降。\n");
        }
        if (Objects.nonNull(portfolio.getUpdateTime())) {
            answer.append("组合更新时间：").append(portfolio.getUpdateTime()).append("\n");
        }
        answer.append(DISCLAIMER);
        return BotAskResp.builder()
                .requestId(requestId)
                .intent("PORTFOLIO_SUMMARY")
                .answer(answer.toString())
                .dataAsOf(Objects.nonNull(portfolio.getQuoteTime()) ? portfolio.getQuoteTime().toString() : null)
                .dataLevel(defaultInteger(portfolio.getMissingQuoteCount()) > 0 ? "RED"
                        : (isQuoteExpired(portfolio.getQuoteTime()) ? "YELLOW"
                        : (Objects.nonNull(portfolio.getQuoteTime()) ? "GREEN" : "YELLOW")))
                .aiEnhanced(false)
                .build();
    }

    private BotAskResp answerPortfolioTodayPnl(String requestId, Long portfolioId) {
        long startedAt = System.nanoTime();
        PortfolioSummaryResp portfolio = portfolioService.detail(portfolioId);
        log.info("Bot 今日盈亏查询完成 requestId={} portfolioId={} positionCount={} durationMs={}",
                requestId, portfolioId, defaultInteger(portfolio.getPositionCount()), elapsedMillis(startedAt));
        StringBuilder answer = new StringBuilder("今日持仓盈亏\n");
        if (defaultInteger(portfolio.getPositionCount()) == 0) {
            answer.append("默认组合暂无持仓，无法计算今日盈亏。请先在 Apex 录入持仓。\n");
        } else if (Objects.isNull(portfolio.getTodayPnl())) {
            answer.append("默认组合有 ").append(defaultInteger(portfolio.getPositionCount()))
                    .append(" 只持仓，但缺少今日行情，暂时无法计算今日盈亏。\n");
        } else {
            BigDecimal todayPnl = portfolio.getTodayPnl().setScale(2, RoundingMode.HALF_UP);
            if (todayPnl.signum() < 0) {
                answer.append("今日亏损：").append(todayPnl.abs().toPlainString()).append(" 元\n");
            } else if (todayPnl.signum() > 0) {
                answer.append("今日盈利：").append(todayPnl.toPlainString()).append(" 元\n");
            } else {
                answer.append("今日盈亏：0.00 元\n");
            }
            if (Objects.nonNull(portfolio.getTodayPct())) {
                answer.append("今日涨跌幅：").append(portfolio.getTodayPct()).append("%\n");
            }
        }
        if (Objects.nonNull(portfolio.getQuoteTime())) {
            answer.append("行情最早时间：").append(portfolio.getQuoteTime()).append("\n");
        }
        answer.append(DISCLAIMER);
        boolean available = defaultInteger(portfolio.getPositionCount()) > 0 && Objects.nonNull(portfolio.getTodayPnl());
        return BotAskResp.builder()
                .requestId(requestId)
                .intent("PORTFOLIO_TODAY_PNL")
                .answer(answer.toString())
                .dataAsOf(Objects.nonNull(portfolio.getQuoteTime()) ? portfolio.getQuoteTime().toString() : null)
                .dataLevel(available && !isQuoteExpired(portfolio.getQuoteTime()) ? "GREEN" : "YELLOW")
                .aiEnhanced(false)
                .build();
    }

    private BotAskResp answerDecision(String requestId) {
        long startedAt = System.nanoTime();
        DecisionAdviceResp advice = decisionService.advice(null);
        log.info("Bot 今日决策查询完成 requestId={} actionCount={} durationMs={}",
                requestId, CollUtil.isNotEmpty(advice.getActions()) ? advice.getActions().size() : 0,
                elapsedMillis(startedAt));
        StringBuilder answer = new StringBuilder("今日决策\n");
        answer.append(defaultText(advice.getSummary(), "当前没有可执行决策")).append("\n");
        if (Objects.nonNull(advice.getTargetExposure())) {
            answer.append("目标仓位：").append(toPercent(advice.getTargetExposure())).append("%\n");
        }
        if (CollUtil.isNotEmpty(advice.getActions())) {
            int limit = Math.min(6, advice.getActions().size());
            for (int index = 0; index < limit; index++) {
                DecisionAdviceActionResp action = advice.getActions().get(index);
                answer.append("- ").append(defaultText(action.getName(), action.getCode()))
                        .append("：").append(defaultText(action.getAction(), "WATCH"));
                if (StringUtils.isNotBlank(action.getReason())) {
                    answer.append("，").append(action.getReason());
                }
                answer.append("\n");
            }
        }
        answer.append(DISCLAIMER);
        return BotAskResp.builder()
                .requestId(requestId)
                .intent("TODAY_DECISION")
                .answer(answer.toString())
                .dataAsOf(Objects.nonNull(advice.getGeneratedAt()) ? advice.getGeneratedAt().toString()
                        : (Objects.nonNull(advice.getActionDate()) ? advice.getActionDate().toString() : null))
                .dataLevel(Objects.nonNull(advice.getActionDate()) ? "GREEN" : "YELLOW")
                .aiEnhanced(Boolean.TRUE.equals(advice.getAiEnhanced()))
                .build();
    }

    private String extractStockKeyword(String question) {
        String keyword = question;
        String[] stopWords = {"请问", "帮我", "看一下", "分析一下", "今天", "现在", "目前", "股票", "个股",
                "还能买吗", "能买吗", "买不买", "该买吗", "能买吗", "能卖吗", "持有吗", "怎么样", "怎么办",
                "看看", "分析", "是否", "可以", "值得", "适合", "能不能", "应该", "买吗", "卖吗", "如何",
                "为什么", "还能", "？", "?", "。", "，", ","};
        for (String stopWord : stopWords) {
            keyword = keyword.replace(stopWord, "");
        }
        keyword = keyword.trim();
        return keyword.length() >= 2 && keyword.length() <= 20 ? keyword : null;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.isNotBlank(value) ? value : defaultValue;
    }

    private int defaultInteger(Integer value) {
        return Objects.nonNull(value) ? value : 0;
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return Objects.nonNull(value) ? value.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }

    private BigDecimal toPercent(BigDecimal ratio) {
        return ratio.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private boolean isQuoteExpired(java.time.LocalDateTime quoteTime) {
        return Objects.nonNull(quoteTime) && Duration.between(quoteTime, java.time.LocalDateTime.now()).toHours() >= 24;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
