package com.awe.apex.quant.decision;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.DecisionPortfolioHolding;
import com.awe.apex.quant.domain.dto.PortfolioSummaryResp;
import com.awe.apex.quant.domain.dto.RiskOverviewResp;
import com.awe.apex.quant.domain.entity.DecisionPortfolioSnapshot;
import com.awe.apex.quant.domain.entity.DecisionRun;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.domain.entity.PortfolioDaily;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.mapper.DecisionPortfolioSnapshotMapper;
import com.awe.apex.quant.service.IConfigService;
import com.awe.apex.quant.service.IPortfolioService;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 决策组合时点快照管理器
 */
@Component
public class DecisionPortfolioSnapshotManager {

    @Resource
    private IPortfolioService portfolioService;

    @Resource
    private IConfigService configService;

    @Resource
    private DecisionPortfolioSnapshotMapper snapshotMapper;

    /**
     * 获取本次决策唯一可用的组合上下文
     *
     * @param context 决策上下文
     * @param run     决策运行
     * @param marketRegime 市场状态
     * @param riskLimits 当前风控配置，历史回放不读取
     * @return 组合时点快照
     */
    public DecisionPortfolioSnapshot resolve(DecisionContext context, DecisionRun run,
                                             MarketRegimeResult marketRegime, RiskOverviewResp riskLimits) {
        if (Objects.isNull(context) || Objects.isNull(run) || Objects.isNull(run.getId())) {
            throw new BusinessException("决策组合上下文不完整");
        }
        if (DecisionMode.REPLAY.equals(context.getMode())) {
            DecisionPortfolioSnapshot historical = snapshotMapper.selectHistorical(context.getActionDate());
            if (Objects.isNull(historical)) {
                throw new BusinessException("历史回放缺少组合快照: " + context.getActionDate());
            }
            if (Objects.isNull(historical.getAtrStopMultiplier())
                    || Objects.isNull(historical.getAtrTakeMultiplier())) {
                throw new BusinessException("历史回放缺少冻结ATR参数: " + context.getActionDate());
            }
            hydrate(historical);
            LocalDateTime now = LocalDateTime.now();
            DecisionPortfolioSnapshot replaySnapshot = DecisionPortfolioSnapshot.builder()
                    .runId(run.getId())
                    .portfolioId(historical.getPortfolioId())
                    .actionDate(context.getActionDate())
                    .cash(historical.getCash())
                    .marketValue(historical.getMarketValue())
                    .totalEquity(historical.getTotalEquity())
                    .peakEquity(historical.getPeakEquity())
                    .drawdown(historical.getDrawdown())
                    .exposureRatio(historical.getExposureRatio())
                    .marketRegime(historical.getMarketRegime())
                    .exposureLimit(historical.getExposureLimit())
                    .singleStockLimit(historical.getSingleStockLimit())
                    .industryLimit(historical.getIndustryLimit())
                    .atrStopMultiplier(historical.getAtrStopMultiplier())
                    .atrTakeMultiplier(historical.getAtrTakeMultiplier())
                    .regimeReason(historical.getRegimeReason())
                    .industryExposureJson(historical.getIndustryExposureJson())
                    .holdingPayload(historical.getHoldingPayload())
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .holdings(historical.getHoldings())
                    .industryExposure(historical.getIndustryExposure())
                    .build();
            if (snapshotMapper.insert(replaySnapshot) != 1) {
                throw new BusinessException("历史回放组合快照保存失败: " + context.getActionDate());
            }
            return replaySnapshot;
        }

        Portfolio portfolio = portfolioService.ensureDefaultPortfolio();
        PortfolioDaily daily = portfolioService.snapshot(portfolio.getId());
        PortfolioSummaryResp summary = portfolioService.detail(portfolio.getId());
        DecisionPortfolioSnapshot snapshot = buildSnapshot(run, portfolio, daily, summary, marketRegime, riskLimits);
        if (snapshotMapper.insert(snapshot) != 1) {
            throw new BusinessException("决策组合快照保存失败: " + context.getActionDate());
        }
        return snapshot;
    }

    /**
     * 冻结本次决策补全后的持仓止损止盈
     *
     * @param snapshot 决策组合快照
     */
    public void updateHoldingRiskLevels(DecisionPortfolioSnapshot snapshot) {
        if (Objects.isNull(snapshot) || Objects.isNull(snapshot.getId())) {
            throw new BusinessException("决策组合快照缺少主键");
        }
        snapshot.setHoldingPayload(JsonUtils.toJsonString(snapshot.getHoldings()));
        snapshot.setUpdateTime(LocalDateTime.now());
        if (snapshotMapper.updateById(snapshot) != 1) {
            throw new BusinessException("决策组合风险价位冻结失败");
        }
    }

    private DecisionPortfolioSnapshot buildSnapshot(DecisionRun run, Portfolio portfolio,
                                                     PortfolioDaily daily, PortfolioSummaryResp summary,
                                                     MarketRegimeResult marketRegime,
                                                     RiskOverviewResp riskLimits) {
        List<DecisionPortfolioHolding> holdings = new ArrayList<>();
        Map<String, BigDecimal> industryExposureValue = new LinkedHashMap<>();
        if (CollUtil.isNotEmpty(summary.getHoldings())) {
            for (PortfolioHolding holding : summary.getHoldings()) {
                DecisionPortfolioHolding snapshotHolding = DecisionPortfolioHolding.builder()
                        .code(holding.getCode())
                        .name(holding.getName())
                        .quantity(holding.getQuantity())
                        .costPrice(holding.getCostPrice())
                        .marketPrice(holding.getMarketPrice())
                        .marketValue(holding.getMarketValue())
                        .stopLoss(holding.getStopLoss())
                        .takeProfit(holding.getTakeProfit())
                        .industry(holding.getIndustry())
                        .build();
                holdings.add(snapshotHolding);
                if (StringUtils.isNotBlank(holding.getIndustry()) && Objects.nonNull(holding.getMarketValue())) {
                    industryExposureValue.merge(holding.getIndustry(), holding.getMarketValue(), BigDecimal::add);
                }
            }
        }
        BigDecimal totalEquity = zeroIfNull(daily.getTotalEquity());
        Map<String, BigDecimal> industryExposure = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : industryExposureValue.entrySet()) {
            BigDecimal ratio = totalEquity.signum() > 0
                    ? entry.getValue().divide(totalEquity, 6, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            industryExposure.put(entry.getKey(), ratio);
        }
        LocalDateTime now = LocalDateTime.now();
        BigDecimal marketExposureLimit = Objects.nonNull(marketRegime)
                && Objects.nonNull(marketRegime.getTotalExposureLimit())
                ? marketRegime.getTotalExposureLimit() : MarketRegimeEnum.UNKNOWN.getTotalExposureLimit();
        BigDecimal configuredExposureLimit = Objects.nonNull(riskLimits)
                && Objects.nonNull(riskLimits.getTotalLimit())
                ? riskLimits.getTotalLimit() : marketExposureLimit;
        return DecisionPortfolioSnapshot.builder()
                .runId(run.getId())
                .portfolioId(portfolio.getId())
                .actionDate(run.getActionDate())
                .cash(zeroIfNull(daily.getCash()))
                .marketValue(zeroIfNull(daily.getMarketValue()))
                .totalEquity(totalEquity)
                .peakEquity(zeroIfNull(daily.getPeakEquity()))
                .drawdown(zeroIfNull(daily.getDrawdown()))
                .exposureRatio(totalEquity.signum() > 0
                        ? zeroIfNull(daily.getMarketValue()).divide(totalEquity, 6, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO)
                .marketRegime(Objects.nonNull(marketRegime) && Objects.nonNull(marketRegime.getMarketRegime())
                        ? marketRegime.getMarketRegime().getCode() : MarketRegimeEnum.UNKNOWN.getCode())
                .exposureLimit(marketExposureLimit.min(configuredExposureLimit))
                .singleStockLimit(Objects.nonNull(riskLimits) ? riskLimits.getSingleLimit() : null)
                .industryLimit(Objects.nonNull(riskLimits) ? riskLimits.getIndustryLimit() : null)
                .atrStopMultiplier(configService.getDecimal("atr_stop_mult", new BigDecimal("2.0")))
                .atrTakeMultiplier(configService.getDecimal("atr_take_mult", new BigDecimal("3.0")))
                .regimeReason(Objects.nonNull(marketRegime) ? marketRegime.getReason() : "市场状态数据不完整")
                .industryExposureJson(JsonUtils.toJsonString(industryExposure))
                .holdingPayload(JsonUtils.toJsonString(holdings))
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .holdings(holdings)
                .industryExposure(industryExposure)
                .build();
    }

    private void hydrate(DecisionPortfolioSnapshot snapshot) {
        List<DecisionPortfolioHolding> holdings = JsonUtils.parseArray(
                snapshot.getHoldingPayload(), DecisionPortfolioHolding.class);
        Map<String, BigDecimal> industryExposure = JsonUtils.parseObject(
                snapshot.getIndustryExposureJson(), new TypeReference<Map<String, BigDecimal>>() {
                });
        snapshot.setHoldings(CollUtil.isNotEmpty(holdings) ? holdings : List.of());
        snapshot.setIndustryExposure(Objects.nonNull(industryExposure)
                ? industryExposure : new LinkedHashMap<>());
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return Objects.nonNull(value) ? value : BigDecimal.ZERO;
    }
}
