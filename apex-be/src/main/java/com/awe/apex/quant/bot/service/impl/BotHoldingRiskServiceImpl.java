package com.awe.apex.quant.bot.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.bot.service.IBotHoldingRiskService;
import com.awe.apex.quant.domain.dto.BotHoldingRiskItem;
import com.awe.apex.quant.domain.dto.BotHoldingRiskResp;
import com.awe.apex.quant.domain.entity.MyHolding;
import com.awe.apex.quant.service.IMyHoldingService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ClawBot 真实持仓风险服务实现。
 */
@Service
public class BotHoldingRiskServiceImpl implements IBotHoldingRiskService {

    private static final BigDecimal NEAR_STOP_RATIO = new BigDecimal("0.03");
    private static final BigDecimal WARN_LOSS_RATIO = new BigDecimal("-0.08");

    @Resource
    private IMyHoldingService myHoldingService;

    /**
     * 使用真实持仓和最新行情生成风险摘要。
     *
     * @return 风险摘要
     */
    @Override
    public BotHoldingRiskResp analyze() {
        List<MyHolding> holdings = myHoldingService.listHoldingsLite();
        List<BotHoldingRiskItem> alerts = new ArrayList<>();
        int quotedCount = 0;
        int criticalCount = 0;
        int warnCount = 0;
        LocalDateTime dataAsOf = null;

        if (CollUtil.isNotEmpty(holdings)) {
            for (MyHolding holding : holdings) {
                BigDecimal price = holding.getMarketPrice();
                if (Objects.isNull(price) || price.signum() <= 0) {
                    alerts.add(buildAlert("WARN", "DATA", holding, "缺少最新行情，暂时无法判断价格风险"));
                    warnCount++;
                    continue;
                }
                quotedCount++;
                if (Objects.nonNull(holding.getQuoteTime())
                        && (Objects.isNull(dataAsOf) || holding.getQuoteTime().isBefore(dataAsOf))) {
                    dataAsOf = holding.getQuoteTime();
                }

                boolean critical = false;
                BigDecimal stopLoss = holding.getStopLoss();
                if (Objects.nonNull(stopLoss) && stopLoss.signum() > 0) {
                    if (price.compareTo(stopLoss) <= 0) {
                        alerts.add(buildAlert("CRITICAL", "STOP", holding,
                                "现价 " + priceText(price) + " 已触及止损价 " + priceText(stopLoss)));
                        criticalCount++;
                        critical = true;
                    } else {
                        BigDecimal distanceRatio = price.subtract(stopLoss)
                                .divide(stopLoss, 4, RoundingMode.HALF_UP);
                        if (distanceRatio.compareTo(NEAR_STOP_RATIO) <= 0) {
                            alerts.add(buildAlert("WARN", "STOP", holding,
                                    "现价 " + priceText(price) + " 接近止损价 " + priceText(stopLoss)));
                            warnCount++;
                        }
                    }
                }
                if (!critical && Objects.nonNull(holding.getPnlPct())
                        && holding.getPnlPct().compareTo(WARN_LOSS_RATIO) <= 0) {
                    alerts.add(buildAlert("WARN", "LOSS", holding,
                            "当前浮亏 " + percentText(holding.getPnlPct()) + "%"));
                    warnCount++;
                }
            }
        }

        return BotHoldingRiskResp.builder()
                .holdingCount(CollUtil.isNotEmpty(holdings) ? holdings.size() : 0)
                .quotedCount(quotedCount)
                .criticalCount(criticalCount)
                .warnCount(warnCount)
                .dataAsOf(Objects.nonNull(dataAsOf) ? dataAsOf.toString() : null)
                .alerts(alerts)
                .build();
    }

    private BotHoldingRiskItem buildAlert(String level, String riskType, MyHolding holding, String message) {
        return BotHoldingRiskItem.builder()
                .level(level)
                .riskType(riskType)
                .code(holding.getCode())
                .name(StringUtils.isNotBlank(holding.getName()) ? holding.getName() : holding.getCode())
                .message(message)
                .build();
    }

    private String priceText(BigDecimal price) {
        return price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String percentText(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }
}
