package com.awe.apex.quant.bot.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.bot.service.IBotToolService;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.BotHoldingInput;
import com.awe.apex.quant.domain.dto.BotTradeInput;
import com.awe.apex.quant.domain.dto.BotToolReq;
import com.awe.apex.quant.domain.dto.BotToolResp;
import com.awe.apex.quant.domain.dto.HoldingTradeReq;
import com.awe.apex.quant.domain.dto.PortfolioHoldingSaveReq;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.dto.PortfolioTipItem;
import com.awe.apex.quant.domain.entity.BotCallAudit;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.domain.enums.PortfolioTradeSourceEnum;
import com.awe.apex.quant.mapper.BotCallAuditMapper;
import com.awe.apex.quant.mapper.PortfolioMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.service.IPortfolioService;
import com.awe.apex.quant.service.ISmartTraderAnalyticsService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * ClawBot 结构化工具服务实现。
 */
@Slf4j
@Service
public class BotToolServiceImpl implements IBotToolService {

    private static final String DISCLAIMER = "以上基于 Apex 当前数据生成，仅供研究，不构成投资建议。";
    @Resource
    private ApexBotProperties properties;

    @Resource
    private IPortfolioService portfolioService;

    @Resource
    private PortfolioMapper portfolioMapper;

    @Resource
    private BotCallAuditMapper callAuditMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Resource
    private ApexUserContext userContext;

    @Resource
    private ISmartTraderAnalyticsService smartTraderAnalyticsService;

    /**
     * 执行受控 Bot 工具。
     *
     * @param request 工具请求
     * @return 工具响应
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BotToolResp execute(BotToolReq request) {
        String requestId = StringUtils.isNotBlank(request.getRequestId()) ? request.getRequestId() : UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        BotToolResp response = null;
        String errorMessage = null;
        try {
            String operation = request.getOperation().trim().toUpperCase();
            response = switch (operation) {
                case "PORTFOLIO_ADVICE" -> portfolioAdvice(request, requestId);
                case "PORTFOLIO_STATUS" -> portfolioStatus(request, requestId);
                case "HOLDING_BUY" -> holdingBuy(request, requestId);
                case "HOLDING_SELL" -> holdingSell(request, requestId);
                case "HOLDING_IMPORT" -> holdingImport(request, requestId);
                case "SMART_TRADER_RANKING" -> smartTraderRanking(request, requestId);
                case "SMART_TRADER_POSITION" -> smartTraderPosition(request, requestId);
                case "SMART_TRADER_PORTFOLIO" -> smartTraderPortfolio(request, requestId);
                case "SMART_TRADER_PROFILE" -> smartTraderProfile(request, requestId);
                case "SMART_MONEY_FACTORS" -> smartMoneyFactors(request, requestId);
                default -> throw new BusinessException("不支持的 Bot 工具: " + operation);
            };
            return response;
        } catch (BusinessException ex) {
            errorMessage = ex.getMessage();
            throw ex;
        } catch (Exception ex) {
            errorMessage = ex.getMessage();
            log.warn("Bot 工具执行失败，请求编号={}，操作={}，异常={}", requestId, request.getOperation(), ex.getMessage());
            throw new BusinessException("Bot 工具执行失败，请提供请求号 " + requestId);
        } finally {
            audit(request, requestId, response, errorMessage, System.currentTimeMillis() - start);
        }
    }

    private BotToolResp smartTraderRanking(BotToolReq request, String requestId) {
        var rankings = smartTraderAnalyticsService.ranking(request.getRankingType());
        StringBuilder answer = new StringBuilder("Smart Trader 排行榜\n");
        int limit = Math.min(10, rankings.size());
        for (int index = 0; index < limit; index++) {
            var ranking = rankings.get(index);
            answer.append(index + 1).append(". ").append(ranking.getTraderName()).append("：累计 ")
                    .append(ranking.getTotalReturn()).append("，当日 ").append(ranking.getDailyReturn()).append("，回撤 ")
                    .append(ranking.getMaxDrawdown()).append("\n");
        }
        if (rankings.isEmpty()) answer.append("暂无可用排名样本。\n");
        return BotToolResp.builder().requestId(requestId).intent("SMART_TRADER_RANKING").answer(answer.append(DISCLAIMER).toString()).dataLevel(rankings.isEmpty() ? "YELLOW" : "GREEN").build();
    }

    private BotToolResp smartTraderPosition(BotToolReq request, String requestId) {
        var positions = smartTraderAnalyticsService.positions(requireTraderId(request));
        StringBuilder answer = new StringBuilder("交易者当前持仓\n");
        for (var position : positions) answer.append(position.getStockName()).append("（").append(position.getSymbol()).append("）：")
                .append(position.getQuantity()).append("股\n");
        if (positions.isEmpty()) answer.append("暂无持仓。\n");
        return BotToolResp.builder().requestId(requestId).intent("SMART_TRADER_POSITION").answer(answer.append(DISCLAIMER).toString()).dataLevel("GREEN").build();
    }

    private BotToolResp smartTraderPortfolio(BotToolReq request, String requestId) {
        var portfolio = smartTraderAnalyticsService.portfolio(requireTraderId(request));
        String answer = "交易者账户\n累计收益 " + portfolio.getTotalProfitRate() + "，最大回撤 "
                + portfolio.getMaxDrawdown() + "，当前持仓 " + portfolio.getPositions().size() + " 只\n" + DISCLAIMER;
        return BotToolResp.builder().requestId(requestId).intent("SMART_TRADER_PORTFOLIO").answer(answer).dataAsOf(portfolio.getTradeDate().toString()).dataLevel("GREEN").build();
    }

    private BotToolResp smartTraderProfile(BotToolReq request, String requestId) {
        var profile = smartTraderAnalyticsService.profile(requireTraderId(request));
        String answer = "交易者画像\n风格：" + profile.getStyle() + "\n偏好行业：" + profile.getPreferredIndustries()
                + "\n集中度：" + profile.getConcentration() + "\n" + profile.getSummary() + "\n" + DISCLAIMER;
        return BotToolResp.builder().requestId(requestId).intent("SMART_TRADER_PROFILE").answer(answer).dataLevel("YELLOW").build();
    }

    private BotToolResp smartMoneyFactors(BotToolReq request, String requestId) {
        var factors = smartTraderAnalyticsService.factors(null);
        StringBuilder answer = new StringBuilder("Smart Money 因子\n");
        int limit = Math.min(10, factors.size());
        for (int index = 0; index < limit; index++) {
            var factor = factors.get(index);
            answer.append(factor.getStockName()).append("（").append(factor.getSymbol()).append("）：因子 ")
                    .append(factor.getFactorValue()).append("，共识度 ").append(factor.getConsensus()).append("\n");
        }
        if (factors.isEmpty()) answer.append("暂无可用因子样本。\n");
        return BotToolResp.builder().requestId(requestId).intent("SMART_MONEY_FACTORS").answer(answer.append(DISCLAIMER).toString()).dataLevel(factors.isEmpty() ? "YELLOW" : "GREEN").build();
    }

    private Long requireTraderId(BotToolReq request) {
        if (Objects.isNull(request.getTraderId())) throw new BusinessException("请提供交易者ID");
        return request.getTraderId();
    }

    private BotToolResp portfolioAdvice(BotToolReq request, String requestId) {
        PortfolioSummaryResp portfolio = detailByName(request.getPortfolioName());
        StringBuilder answer = new StringBuilder("组合投资建议：").append(portfolio.getName()).append("\n");
        answer.append("持仓 ").append(defaultInteger(portfolio.getPositionCount())).append(" 只。\n");
        appendFreshness(answer, portfolio);
        if (Objects.nonNull(portfolio.getBrief())) {
            answer.append("组合立场：").append(defaultText(portfolio.getBrief().getStance(), "暂无"))
                    .append("。 ").append(defaultText(portfolio.getBrief().getSummary(), "暂无组合研判")).append("\n");
            appendTips(answer, "优先操作", portfolio.getBrief().getActions());
            appendTips(answer, "风险", portfolio.getBrief().getRisks());
        }
        if (CollUtil.isNotEmpty(portfolio.getHoldings())) {
            answer.append("单票建议：\n");
            int count = 0;
            for (PortfolioHolding holding : portfolio.getHoldings()) {
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
        }
        answer.append(DISCLAIMER);
        return response(requestId, "PORTFOLIO_ADVICE", answer.toString(), portfolio);
    }

    private BotToolResp portfolioStatus(BotToolReq request, String requestId) {
        PortfolioSummaryResp portfolio = detailByName(request.getPortfolioName());
        StringBuilder answer = new StringBuilder("组合状态：").append(portfolio.getName()).append("\n");
        answer.append("持仓 ").append(defaultInteger(portfolio.getPositionCount())).append(" 只。");
        if (Objects.nonNull(portfolio.getTodayPct())) {
            answer.append(" 今日涨跌 ").append(portfolio.getTodayPct()).append("%。");
        }
        answer.append("\n");
        appendFreshness(answer, portfolio);
        answer.append(DISCLAIMER);
        return response(requestId, "PORTFOLIO_STATUS", answer.toString(), portfolio);
    }

    private BotToolResp holdingImport(BotToolReq request, String requestId) {
        if (!Boolean.TRUE.equals(request.getFullReplace())) {
            throw new BusinessException("全量更新必须明确确认 fullReplace=true；买入或加仓请使用 HOLDING_BUY");
        }
        Portfolio portfolio = activePortfolioByName(request.getPortfolioName());
        return userContext.runAsUser(portfolio.getUserId(), () -> importHoldings(portfolio, request, requestId));
    }

    private BotToolResp holdingBuy(BotToolReq request, String requestId) {
        return holdingTrade(request, requestId, "BUY");
    }

    private BotToolResp holdingSell(BotToolReq request, String requestId) {
        return holdingTrade(request, requestId, "SELL");
    }

    private BotToolResp holdingTrade(BotToolReq request, String requestId, String side) {
        Portfolio portfolio = activePortfolioByName(request.getPortfolioName());
        return userContext.runAsUser(portfolio.getUserId(), () -> executeHoldingTrade(portfolio, request, requestId, side));
    }

    private BotToolResp executeHoldingTrade(Portfolio portfolio, BotToolReq request, String requestId, String side) {
        resolveTradeCodes(request.getTrades());
        validateTradeInputs(request.getTrades());
        String operation = "BUY".equals(side) ? "HOLDING_BUY" : "HOLDING_SELL";
        String action = "BUY".equals(side) ? "新增买入" : "卖出";
        StringBuilder answer = new StringBuilder("已").append(action).append("「")
                .append(portfolio.getName()).append("」：");
        for (int index = 0; index < request.getTrades().size(); index++) {
            BotTradeInput input = request.getTrades().get(index);
            HoldingTradeReq tradeReq = new HoldingTradeReq();
            tradeReq.setCode(input.getCode());
            tradeReq.setName(input.getName());
            tradeReq.setSide(side);
            tradeReq.setQuantity(input.getQuantity());
            tradeReq.setTradePrice(input.getTradePrice());
            tradeReq.setTradeTime(input.getTradeTime());
            portfolioService.tradeHolding(portfolio.getId(), tradeReq,
                    PortfolioTradeSourceEnum.WECHAT_BOT, requestId + ":" + input.getCode());
            if (index > 0) {
                answer.append("；");
            }
            answer.append(defaultText(input.getName(), input.getCode())).append("（").append(input.getCode()).append("）")
                    .append(" ").append(input.getQuantity()).append(" 股，成交价 ").append(input.getTradePrice());
        }
        portfolioService.refreshQuotes(portfolio.getId(), false);
        portfolioService.snapshot(portfolio.getId());
        answer.append("；仅变更指定持仓，已完成行情刷新和今日快照。");
        return BotToolResp.builder().requestId(requestId).intent(operation)
                .answer(answer.toString()).dataLevel("GREEN").build();
    }

    private BotToolResp importHoldings(Portfolio portfolio, BotToolReq request, String requestId) {
        resolveHoldingCodes(request.getHoldings());
        validateHoldingInputs(request.getHoldings(), request.getTotalMarketValue());
        List<PortfolioHolding> existing = portfolioService.detail(portfolio.getId()).getHoldings();
        Map<String, PortfolioHolding> existingByCode = new LinkedHashMap<>();
        Set<String> existingCodes = new HashSet<>();
        for (PortfolioHolding holding : existing) {
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            existingCodes.add(code);
            existingByCode.put(code, holding);
        }
        Set<String> inputCodes = new HashSet<>();
        for (BotHoldingInput holding : request.getHoldings()) {
            inputCodes.add(MarketCodeUtils.normalizeHoldingCode(holding.getCode()));
        }
        int added = 0;
        for (String code : inputCodes) {
            if (!existingCodes.contains(code)) {
                added++;
            }
        }
        int deleted = 0;
        for (String code : existingCodes) {
            if (!inputCodes.contains(code)) {
                deleted++;
            }
        }
        for (PortfolioHolding holding : existing) {
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            if (!inputCodes.contains(code)) {
                portfolioService.removeHolding(portfolio.getId(), holding.getId(),
                        PortfolioTradeSourceEnum.WECHAT_BOT, requestId + ":" + code);
            }
        }
        for (BotHoldingInput input : request.getHoldings()) {
            String code = MarketCodeUtils.normalizeHoldingCode(input.getCode());
            PortfolioHolding currentHolding = existingByCode.get(code);
            boolean sameCost = Objects.nonNull(currentHolding)
                    && Objects.nonNull(currentHolding.getCostPrice())
                    && Objects.nonNull(input.getCostPrice())
                    && currentHolding.getCostPrice().compareTo(input.getCostPrice()) == 0;
            boolean sameName = Objects.nonNull(currentHolding)
                    && (StringUtils.isBlank(input.getName()) || Objects.equals(currentHolding.getName(), input.getName()));
            if (Objects.nonNull(currentHolding)
                    && Objects.equals(currentHolding.getQuantity(), input.getQuantity())
                    && sameCost && sameName) {
                continue;
            }
            PortfolioHoldingSaveReq saveReq = new PortfolioHoldingSaveReq();
            if (Objects.nonNull(currentHolding)) {
                saveReq.setId(currentHolding.getId());
            }
            saveReq.setCode(code);
            saveReq.setName(input.getName());
            saveReq.setQuantity(input.getQuantity());
            saveReq.setCostPrice(input.getCostPrice());
            saveReq.setTradePrice(input.getTradePrice());
            saveReq.setTradeTime(input.getTradeTime());
            portfolioService.saveHolding(portfolio.getId(), saveReq,
                    PortfolioTradeSourceEnum.WECHAT_BOT, requestId + ":" + code);
        }
        portfolioService.refreshQuotes(portfolio.getId(), false);
        portfolioService.snapshot(portfolio.getId());
        String answer = "已根据截图全量更新「" + portfolio.getName() + "」：共 " + request.getHoldings().size()
                + " 只，新增 " + added + " 只，移除 " + deleted + " 只；已完成行情刷新和今日快照。";
        return BotToolResp.builder().requestId(requestId).intent("HOLDING_IMPORT")
                .answer(answer).dataLevel("GREEN").build();
    }

    private PortfolioSummaryResp detailByName(String name) {
        return portfolioService.detail(portfolioByName(name).getId());
    }

    private Portfolio portfolioByName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new BusinessException("请指定组合名称");
        }
        List<Portfolio> portfolios = portfolioMapper.selectList(Wrappers.<Portfolio>lambdaQuery()
                .eq(Portfolio::getUserId, userContext.currentUserId())
                .eq(Portfolio::getName, name.trim())
                .eq(Portfolio::getStatus, "ACTIVE"));
        return requireUniquePortfolio(name, portfolios);
    }

    private Portfolio activePortfolioByName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new BusinessException("请指定组合名称");
        }
        List<Portfolio> portfolios = portfolioMapper.selectList(Wrappers.<Portfolio>lambdaQuery()
                .eq(Portfolio::getName, name.trim())
                .eq(Portfolio::getStatus, "ACTIVE"));
        return requireUniquePortfolio(name, portfolios);
    }

    private Portfolio requireUniquePortfolio(String name, List<Portfolio> portfolios) {
        if (CollUtil.isEmpty(portfolios)) {
            throw new BusinessException("未找到组合: " + name);
        }
        if (portfolios.size() > 1) {
            throw new BusinessException("组合名称不唯一: " + name);
        }
        return portfolios.get(0);
    }

    private void validateHoldingInputs(List<BotHoldingInput> inputs, BigDecimal totalMarketValue) {
        if (CollUtil.isEmpty(inputs)) {
            throw new BusinessException("未解析到可确认的持仓，请补充证券代码、数量和成本价");
        }
        Set<String> codes = new HashSet<>();
        BigDecimal calculatedMarketValue = BigDecimal.ZERO;
        boolean hasMarketValue = false;
        for (BotHoldingInput input : inputs) {
            String code = MarketCodeUtils.normalizeHoldingCode(input.getCode());
            if (StringUtils.isBlank(code) || !code.matches("\\d{6}")) {
                throw new BusinessException("证券代码无效: " + input.getCode());
            }
            if (!codes.add(code)) {
                throw new BusinessException("存在重复证券代码: " + code);
            }
            if (Objects.isNull(input.getQuantity()) || input.getQuantity() <= 0
                    || Objects.isNull(input.getCostPrice()) || input.getCostPrice().signum() <= 0) {
                throw new BusinessException("持仓数量或成本价无效: " + code);
            }
            if (Objects.nonNull(input.getMarketValue()) && input.getMarketValue().signum() > 0) {
                calculatedMarketValue = calculatedMarketValue.add(input.getMarketValue());
                hasMarketValue = true;
            }
        }
        if (Objects.nonNull(totalMarketValue) && totalMarketValue.signum() > 0 && hasMarketValue) {
            BigDecimal difference = calculatedMarketValue.subtract(totalMarketValue).abs()
                    .divide(totalMarketValue, 4, RoundingMode.HALF_UP);
            if (difference.compareTo(new BigDecimal("0.20")) > 0) {
                throw new BusinessException("截图总市值与逐项市值偏差超过20%，请核对截图识别结果");
            }
        }
    }

    private void resolveHoldingCodes(List<BotHoldingInput> inputs) {
        if (CollUtil.isEmpty(inputs)) {
            return;
        }
        for (BotHoldingInput input : inputs) {
            if (StringUtils.isNotBlank(input.getCode())) {
                continue;
            }
            if (StringUtils.isBlank(input.getName())) {
                throw new BusinessException("持仓缺少证券代码和名称");
            }
            List<StockBasic> matchedStocks = stockBasicMapper.selectList(
                    Wrappers.<StockBasic>lambdaQuery()
                            .eq(StockBasic::getName, input.getName().trim())
                            .last("LIMIT 2"));
            if (CollUtil.isEmpty(matchedStocks)) {
                throw new BusinessException("无法识别证券名称: " + input.getName());
            }
            if (matchedStocks.size() > 1 || StringUtils.isBlank(matchedStocks.get(0).getCode())) {
                throw new BusinessException("证券名称匹配不唯一，请补充代码: " + input.getName());
            }
            input.setCode(matchedStocks.get(0).getCode());
            input.setName(matchedStocks.get(0).getName());
        }
    }

    private void resolveTradeCodes(List<BotTradeInput> inputs) {
        if (CollUtil.isEmpty(inputs)) {
            return;
        }
        for (BotTradeInput input : inputs) {
            if (StringUtils.isNotBlank(input.getCode())) {
                input.setCode(MarketCodeUtils.normalizeHoldingCode(input.getCode()));
                continue;
            }
            if (StringUtils.isBlank(input.getName())) {
                throw new BusinessException("成交缺少证券代码和名称");
            }
            List<StockBasic> matchedStocks = stockBasicMapper.selectList(
                    Wrappers.<StockBasic>lambdaQuery()
                            .eq(StockBasic::getName, input.getName().trim())
                            .last("LIMIT 2"));
            if (CollUtil.isEmpty(matchedStocks)) {
                throw new BusinessException("无法识别证券名称: " + input.getName());
            }
            if (matchedStocks.size() > 1 || StringUtils.isBlank(matchedStocks.get(0).getCode())) {
                throw new BusinessException("证券名称匹配不唯一，请补充代码: " + input.getName());
            }
            input.setCode(MarketCodeUtils.normalizeHoldingCode(matchedStocks.get(0).getCode()));
            input.setName(matchedStocks.get(0).getName());
        }
    }

    private void validateTradeInputs(List<BotTradeInput> inputs) {
        if (CollUtil.isEmpty(inputs)) {
            throw new BusinessException("未解析到可确认的成交，请补充证券代码、数量和成交价");
        }
        Set<String> codes = new HashSet<>();
        for (BotTradeInput input : inputs) {
            if (StringUtils.isBlank(input.getCode()) || !input.getCode().matches("\\d{6}")) {
                throw new BusinessException("证券代码无效: " + input.getCode());
            }
            if (!codes.add(input.getCode())) {
                throw new BusinessException("存在重复证券代码: " + input.getCode());
            }
            if (Objects.isNull(input.getQuantity()) || input.getQuantity() <= 0
                    || Objects.isNull(input.getTradePrice()) || input.getTradePrice().signum() <= 0) {
                throw new BusinessException("成交数量或成交价无效: " + input.getCode());
            }
        }
    }

    private BotToolResp response(String requestId, String intent, String answer, PortfolioSummaryResp portfolio) {
        List<String> quoteStatus = new ArrayList<>();
        if (CollUtil.isNotEmpty(portfolio.getHoldings())) {
            for (PortfolioHolding holding : portfolio.getHoldings()) {
                quoteStatus.add(defaultText(holding.getName(), holding.getCode()) + "："
                        + (Objects.nonNull(holding.getQuoteTime()) ? holding.getQuoteTime().toString() : "无行情"));
            }
        }
        String level = defaultInteger(portfolio.getMissingQuoteCount()) > 0 ? "RED"
                : (isQuoteExpired(portfolio.getQuoteTime()) ? "YELLOW"
                : (Objects.nonNull(portfolio.getQuoteTime()) ? "GREEN" : "YELLOW"));
        return BotToolResp.builder().requestId(requestId).intent(intent).answer(answer)
                .dataAsOf(Objects.nonNull(portfolio.getQuoteTime()) ? portfolio.getQuoteTime().toString() : null)
                .dataLevel(level).quoteStatus(quoteStatus).build();
    }

    private void appendFreshness(StringBuilder answer, PortfolioSummaryResp portfolio) {
        if (Objects.nonNull(portfolio.getQuoteTime())) {
            answer.append("行情最早时间：").append(portfolio.getQuoteTime()).append("。\n");
            if (isQuoteExpired(portfolio.getQuoteTime())) {
                answer.append("行情已超过24小时未更新，建议仅供参考。\n");
            }
        } else {
            answer.append("行情时间缺失，结论可靠性下降。\n");
        }
        if (defaultInteger(portfolio.getMissingQuoteCount()) > 0) {
            answer.append("缺少行情：").append(portfolio.getMissingQuoteCount()).append(" 只，相关建议仅供参考。\n");
        }
        if (Objects.nonNull(portfolio.getUpdateTime())) {
            answer.append("组合更新时间：").append(portfolio.getUpdateTime()).append("。\n");
        }
    }

    private void appendTips(StringBuilder answer, String title, List<PortfolioTipItem> tips) {
        if (CollUtil.isEmpty(tips)) {
            return;
        }
        answer.append(title).append("：\n");
        for (PortfolioTipItem tip : tips) {
            answer.append("- ").append(tip.getText()).append("\n");
        }
    }

    private void audit(BotToolReq request, String requestId, BotToolResp response, String errorMessage, long durationMs) {
        try {
            BotCallAudit audit = new BotCallAudit();
            audit.setRequestId(requestId);
            audit.setOperation(request.getOperation());
            audit.setUserId(request.getUserId());
            audit.setConversationId(request.getConversationId());
            audit.setDataLevel(Objects.nonNull(response) ? response.getDataLevel() : "ERROR");
            audit.setErrorMessage(errorMessage);
            audit.setDurationMs(durationMs);
            audit.setCreateTime(LocalDateTime.now());
            callAuditMapper.insert(audit);
        } catch (Exception ex) {
            log.warn("Bot 审计写入失败，请求编号={}，异常={}", requestId, ex.getMessage());
        }
    }

    private int defaultInteger(Integer value) {
        return Objects.nonNull(value) ? value : 0;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.isNotBlank(value) ? value : defaultValue;
    }

    private String amount(BigDecimal value) {
        return Objects.nonNull(value) ? value.setScale(2, RoundingMode.HALF_UP).toPlainString() : "-";
    }

    private boolean isQuoteExpired(LocalDateTime quoteTime) {
        return Objects.nonNull(quoteTime) && Duration.between(quoteTime, LocalDateTime.now()).toHours() >= 24;
    }
}
