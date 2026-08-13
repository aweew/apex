package com.awe.apex.quant.bot.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.bot.service.IBotToolService;
import com.awe.apex.quant.domain.dto.BotHoldingInput;
import com.awe.apex.quant.domain.dto.BotToolReq;
import com.awe.apex.quant.domain.dto.BotToolResp;
import com.awe.apex.quant.domain.dto.PortfolioHoldingSaveReq;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.dto.PortfolioTipItem;
import com.awe.apex.quant.domain.entity.BotCallAudit;
import com.awe.apex.quant.domain.entity.BotPendingOperation;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.mapper.BotCallAuditMapper;
import com.awe.apex.quant.mapper.BotPendingOperationMapper;
import com.awe.apex.quant.mapper.PortfolioMapper;
import com.awe.apex.quant.market.MarketCodeUtils;
import com.awe.apex.quant.service.IPortfolioService;
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
    private static final int CONFIRM_MINUTES = 10;

    @Resource
    private ApexBotProperties properties;

    @Resource
    private IPortfolioService portfolioService;

    @Resource
    private PortfolioMapper portfolioMapper;

    @Resource
    private BotPendingOperationMapper pendingOperationMapper;

    @Resource
    private BotCallAuditMapper callAuditMapper;

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
                case "HOLDING_PREVIEW" -> holdingPreview(request, requestId);
                case "HOLDING_CONFIRM" -> holdingConfirm(request, requestId);
                case "OPERATION_STATUS" -> operationStatus(request, requestId);
                default -> throw new BusinessException("不支持的 Bot 工具: " + operation);
            };
            return response;
        } catch (BusinessException ex) {
            errorMessage = ex.getMessage();
            throw ex;
        } catch (Exception ex) {
            errorMessage = ex.getMessage();
            log.warn("Bot 工具执行失败 requestId={} operation={} err={}", requestId, request.getOperation(), ex.getMessage());
            throw new BusinessException("Bot 工具执行失败，请提供请求号 " + requestId);
        } finally {
            audit(request, requestId, response, errorMessage, System.currentTimeMillis() - start);
        }
    }

    private BotToolResp portfolioAdvice(BotToolReq request, String requestId) {
        PortfolioSummaryResp portfolio = detailByName(request.getPortfolioName());
        StringBuilder answer = new StringBuilder("组合投资建议：").append(portfolio.getName()).append("\n");
        answer.append("持仓 ").append(defaultInteger(portfolio.getPositionCount())).append(" 只，现金 ")
                .append(amount(portfolio.getCashBalance())).append("，累计浮盈 ").append(amount(portfolio.getTotalPnl())).append("。\n");
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
        answer.append("总权益 ").append(amount(portfolio.getTotalEquity())).append("，持仓 ")
                .append(defaultInteger(portfolio.getPositionCount())).append(" 只，今日盈亏 ")
                .append(amount(portfolio.getTodayPnl())).append("。\n");
        appendFreshness(answer, portfolio);
        answer.append(DISCLAIMER);
        return response(requestId, "PORTFOLIO_STATUS", answer.toString(), portfolio);
    }

    private BotToolResp holdingPreview(BotToolReq request, String requestId) {
        Portfolio portfolio = portfolioByName(request.getPortfolioName());
        validateHoldingInputs(request.getHoldings(), request.getTotalMarketValue());
        List<PortfolioHolding> existing = portfolioService.detail(portfolio.getId()).getHoldings();
        Set<String> existingCodes = new HashSet<>();
        for (PortfolioHolding holding : existing) {
            existingCodes.add(holding.getCode());
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
        String confirmationCode = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        LocalDateTime now = LocalDateTime.now();
        BotPendingOperation pending = new BotPendingOperation();
        pending.setOperationType("REPLACE_PORTFOLIO_HOLDINGS");
        pending.setPortfolioId(portfolio.getId());
        pending.setUserId(request.getUserId());
        pending.setConversationId(request.getConversationId());
        pending.setConfirmationCode(confirmationCode);
        pending.setPayloadJson(JsonUtils.toJsonString(request.getHoldings()));
        pending.setStatus("PENDING");
        pending.setExpireTime(now.plusMinutes(CONFIRM_MINUTES));
        pending.setCreateTime(now);
        pending.setDeleted(0);
        pendingOperationMapper.insert(pending);
        String answer = "已解析「" + portfolio.getName() + "」持仓预览：" + request.getHoldings().size()
                + " 只，新增 " + added + " 只，移除 " + deleted + " 只。\n"
                + "该操作会全量替换当前组合持仓。请在 " + CONFIRM_MINUTES + " 分钟内回复：确认 " + confirmationCode;
        return BotToolResp.builder().requestId(requestId).intent("HOLDING_PREVIEW").answer(answer)
                .dataLevel("GREEN").operationId(pending.getId()).confirmationCode(confirmationCode).build();
    }

    private BotToolResp holdingConfirm(BotToolReq request, String requestId) {
        BotPendingOperation pending = pendingOperationMapper.selectOne(Wrappers.<BotPendingOperation>lambdaQuery()
                .eq(BotPendingOperation::getConfirmationCode, request.getConfirmationCode())
                .eq(BotPendingOperation::getUserId, request.getUserId())
                .eq(BotPendingOperation::getConversationId, request.getConversationId()).last("LIMIT 1"));
        if (Objects.isNull(pending) || !"PENDING".equals(pending.getStatus())) {
            throw new BusinessException("确认码无效或已使用");
        }
        if (pending.getExpireTime().isBefore(LocalDateTime.now())) {
            pending.setStatus("EXPIRED");
            pendingOperationMapper.updateById(pending);
            throw new BusinessException("确认码已过期，请重新生成预览");
        }
        int updated = pendingOperationMapper.update(null, Wrappers.<BotPendingOperation>lambdaUpdate()
                .eq(BotPendingOperation::getId, pending.getId())
                .eq(BotPendingOperation::getStatus, "PENDING")
                .set(BotPendingOperation::getStatus, "PROCESSING"));
        if (updated != 1) {
            throw new BusinessException("确认码正在处理或已使用");
        }
        List<BotHoldingInput> inputs = JsonUtils.parseArray(pending.getPayloadJson(), BotHoldingInput.class);
        PortfolioSummaryResp current = portfolioService.detail(pending.getPortfolioId());
        for (PortfolioHolding holding : current.getHoldings()) {
            portfolioService.removeHolding(pending.getPortfolioId(), holding.getId());
        }
        for (BotHoldingInput input : inputs) {
            PortfolioHoldingSaveReq saveReq = new PortfolioHoldingSaveReq();
            saveReq.setCode(MarketCodeUtils.normalizeHoldingCode(input.getCode()));
            saveReq.setName(input.getName());
            saveReq.setQuantity(input.getQuantity());
            saveReq.setCostPrice(input.getCostPrice());
            portfolioService.saveHolding(pending.getPortfolioId(), saveReq);
        }
        portfolioService.refreshQuotes(pending.getPortfolioId(), false);
        portfolioService.snapshot(pending.getPortfolioId());
        pending.setStatus("CONFIRMED");
        pending.setConfirmTime(LocalDateTime.now());
        pendingOperationMapper.updateById(pending);
        return BotToolResp.builder().requestId(requestId).intent("HOLDING_CONFIRM")
                .answer("已全量更新组合持仓，并完成行情刷新和今日快照。")
                .dataLevel("GREEN").operationId(pending.getId()).build();
    }

    private BotToolResp operationStatus(BotToolReq request, String requestId) {
        BotPendingOperation pending = pendingOperationMapper.selectOne(Wrappers.<BotPendingOperation>lambdaQuery()
                .eq(BotPendingOperation::getConfirmationCode, request.getConfirmationCode())
                .eq(BotPendingOperation::getUserId, request.getUserId())
                .eq(BotPendingOperation::getConversationId, request.getConversationId()).last("LIMIT 1"));
        if (Objects.isNull(pending)) {
            throw new BusinessException("未找到对应操作");
        }
        return BotToolResp.builder().requestId(requestId).intent("OPERATION_STATUS")
                .answer("操作状态：" + pending.getStatus() + "，过期时间：" + pending.getExpireTime())
                .dataLevel("GREEN").operationId(pending.getId()).build();
    }

    private PortfolioSummaryResp detailByName(String name) {
        return portfolioService.detail(portfolioByName(name).getId());
    }

    private Portfolio portfolioByName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new BusinessException("请指定组合名称");
        }
        List<Portfolio> portfolios = portfolioMapper.selectList(Wrappers.<Portfolio>lambdaQuery()
                .eq(Portfolio::getName, name.trim()).eq(Portfolio::getStatus, "ACTIVE"));
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
            log.warn("Bot 审计写入失败 requestId={} err={}", requestId, ex.getMessage());
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
