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
import com.awe.apex.quant.domain.dto.StockAnalysisAiResp;
import com.awe.apex.quant.domain.dto.StockAnalysisFreshnessResp;
import com.awe.apex.quant.domain.dto.StockAnalysisResp;
import com.awe.apex.quant.domain.dto.StockSearchItem;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.IStockAnalysisService;
import com.awe.apex.quant.service.IStockService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ClawBot 股票问答服务实现。
 */
@Service
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

        // 3. 组合问题与通用决策问题无需猜测股票名称
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
        StockAnalysisResp analysis = stockAnalysisService.analyze(code, "BUY", 120, true, false);
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
        MarketBriefingResp briefing = marketBriefingService.briefing(false);
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
        BotHoldingRiskResp risk = botHoldingRiskService.analyze();
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

    private BotAskResp answerDecision(String requestId) {
        DecisionAdviceResp advice = decisionService.advice(null);
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

    private BigDecimal toPercent(BigDecimal ratio) {
        return ratio.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
