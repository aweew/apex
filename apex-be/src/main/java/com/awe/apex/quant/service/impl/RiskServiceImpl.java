package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.RiskAlertItem;
import com.awe.apex.quant.domain.dto.RiskOverviewResp;
import com.awe.apex.quant.domain.dto.RiskRuleUpdateReq;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.domain.entity.PaperAccount;
import com.awe.apex.quant.domain.entity.PaperPosition;
import com.awe.apex.quant.domain.entity.RiskRule;
import com.awe.apex.quant.domain.entity.StockBasic;
import com.awe.apex.quant.mapper.BarDailyMapper;
import com.awe.apex.quant.mapper.PaperAccountMapper;
import com.awe.apex.quant.mapper.PaperPositionMapper;
import com.awe.apex.quant.mapper.RiskRuleMapper;
import com.awe.apex.quant.mapper.StockBasicMapper;
import com.awe.apex.quant.service.IRiskService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 风控服务实现
 */
@Service
public class RiskServiceImpl implements IRiskService {

    @Resource
    private RiskRuleMapper riskRuleMapper;

    @Resource
    private PaperAccountMapper paperAccountMapper;

    @Resource
    private PaperPositionMapper paperPositionMapper;

    @Resource
    private BarDailyMapper barDailyMapper;

    @Resource
    private StockBasicMapper stockBasicMapper;

    @Override
    public RiskOverviewResp overview(Long accountId) {
        PaperAccount account = paperAccountMapper.selectById(accountId);
        if (Objects.isNull(account)) {
            throw new BusinessException("账户不存在");
        }
        BigDecimal totalLimit = ruleDecimal("total_position_limit", new BigDecimal("0.80"));
        BigDecimal singleLimit = ruleDecimal("single_stock_limit", new BigDecimal("0.15"));
        BigDecimal industryLimit = ruleDecimal("industry_limit", new BigDecimal("0.30"));
        BigDecimal stopLossPct = ruleDecimal("stop_loss_pct", new BigDecimal("0.08"));
        BigDecimal takeProfitPct = ruleDecimal("take_profit_pct", new BigDecimal("0.20"));
        int maxHoldDays = ruleDecimal("max_hold_days", new BigDecimal("60")).intValue();

        List<PaperPosition> positions = paperPositionMapper.selectList(Wrappers.<PaperPosition>lambdaQuery()
                .eq(PaperPosition::getAccountId, accountId));
        BigDecimal positionValue = BigDecimal.ZERO;
        List<String> warnings = new ArrayList<>();
        List<RiskAlertItem> alerts = new ArrayList<>();
        Map<String, BigDecimal> industryMv = new HashMap<>();
        for (PaperPosition position : positions) {
            BigDecimal price = latestClose(position.getCode());
            if (Objects.isNull(price)) {
                addAlert(alerts, warnings, "WARN", "DATA", position.getCode(), position.getCode() + " 无最新收盘价");
                continue;
            }
            BigDecimal mv = price.multiply(BigDecimal.valueOf(position.getQuantity()));
            positionValue = positionValue.add(mv);
            String industry = resolveIndustry(position.getCode());
            if (StringUtils.isNotBlank(industry)) {
                industryMv.merge(industry, mv, BigDecimal::add);
            }
            if (Objects.nonNull(position.getStopLoss()) && price.compareTo(position.getStopLoss()) <= 0) {
                addAlert(alerts, warnings, "CRITICAL", "STOP", position.getCode(), position.getCode() + " 触及止损");
            }
            if (Objects.nonNull(position.getTakeProfit()) && price.compareTo(position.getTakeProfit()) >= 0) {
                addAlert(alerts, warnings, "INFO", "STOP", position.getCode(), position.getCode() + " 触及止盈");
            }
            BigDecimal drop = position.getCostPrice().subtract(price).divide(position.getCostPrice(), 4, RoundingMode.HALF_UP);
            if (drop.compareTo(stopLossPct) >= 0) {
                addAlert(alerts, warnings, "CRITICAL", "STOP", position.getCode(),
                        position.getCode() + " 浮亏超过默认止损比例");
            }
            BigDecimal gain = price.subtract(position.getCostPrice()).divide(position.getCostPrice(), 4, RoundingMode.HALF_UP);
            if (gain.compareTo(takeProfitPct) >= 0) {
                addAlert(alerts, warnings, "INFO", "STOP", position.getCode(),
                        position.getCode() + " 浮盈超过默认止盈比例");
            }
            if (Objects.nonNull(position.getCreateTime()) && maxHoldDays > 0) {
                long hold = java.time.temporal.ChronoUnit.DAYS.between(position.getCreateTime().toLocalDate(), java.time.LocalDate.now());
                if (hold >= maxHoldDays) {
                    addAlert(alerts, warnings, "WARN", "HOLD", position.getCode(),
                            position.getCode() + " 持仓已 " + hold + " 天，超过上限 " + maxHoldDays);
                }
            }
        }
        BigDecimal totalAsset = account.getCash().add(positionValue);
        BigDecimal ratio = totalAsset.signum() == 0 ? BigDecimal.ZERO
                : positionValue.divide(totalAsset, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(totalLimit) > 0) {
            addAlert(alerts, warnings, "CRITICAL", "POSITION", null, "总仓位超限: " + ratio);
        }
        for (PaperPosition position : positions) {
            BigDecimal price = latestClose(position.getCode());
            if (Objects.isNull(price) || totalAsset.signum() == 0) {
                continue;
            }
            BigDecimal singleRatio = price.multiply(BigDecimal.valueOf(position.getQuantity()))
                    .divide(totalAsset, 4, RoundingMode.HALF_UP);
            if (singleRatio.compareTo(singleLimit) > 0) {
                addAlert(alerts, warnings, "WARN", "POSITION", position.getCode(),
                        position.getCode() + " 单票仓位超限: " + singleRatio);
            }
        }
        if (totalAsset.signum() > 0) {
            for (Map.Entry<String, BigDecimal> entry : industryMv.entrySet()) {
                BigDecimal industryRatio = entry.getValue().divide(totalAsset, 4, RoundingMode.HALF_UP);
                if (industryRatio.compareTo(industryLimit) > 0) {
                    addAlert(alerts, warnings, "WARN", "INDUSTRY", null,
                            entry.getKey() + " 行业仓位超限: " + industryRatio);
                }
            }
        }
        int criticalCount = 0;
        int warnCount = 0;
        for (RiskAlertItem alert : alerts) {
            if ("CRITICAL".equals(alert.getLevel())) {
                criticalCount++;
            } else if ("WARN".equals(alert.getLevel())) {
                warnCount++;
            }
        }
        return RiskOverviewResp.builder()
                .accountId(accountId)
                .totalAsset(totalAsset)
                .cash(account.getCash())
                .positionValue(positionValue)
                .positionRatio(ratio)
                .totalLimit(totalLimit)
                .singleLimit(singleLimit)
                .industryLimit(industryLimit)
                .warnings(warnings)
                .alerts(alerts)
                .criticalCount(criticalCount)
                .warnCount(warnCount)
                .build();
    }

    private void addAlert(List<RiskAlertItem> alerts, List<String> warnings,
                          String level, String category, String code, String message) {
        warnings.add(message);
        alerts.add(RiskAlertItem.builder()
                .level(level)
                .category(category)
                .code(code)
                .message(message)
                .build());
    }

    @Override
    public void checkBeforeOrder(Long accountId, String code, String side, Integer quantity, BigDecimal price) {
        if (!"BUY".equalsIgnoreCase(side)) {
            return;
        }
        PaperAccount account = paperAccountMapper.selectById(accountId);
        if (Objects.isNull(account)) {
            throw new BusinessException("账户不存在");
        }
        BigDecimal amount = price.multiply(BigDecimal.valueOf(quantity));
        if (account.getCash().compareTo(amount) < 0) {
            throw new BusinessException("资金不足");
        }
        RiskOverviewResp current = overview(accountId);
        BigDecimal afterPos = current.getPositionValue().add(amount);
        BigDecimal afterAsset = current.getCash().subtract(amount).add(afterPos);
        BigDecimal totalLimit = current.getTotalLimit();
        BigDecimal singleLimit = current.getSingleLimit();
        BigDecimal industryLimit = current.getIndustryLimit();
        if (afterAsset.signum() > 0) {
            BigDecimal totalRatio = afterPos.divide(afterAsset, 4, RoundingMode.HALF_UP);
            if (totalRatio.compareTo(totalLimit) > 0) {
                throw new BusinessException("下单后总仓位将超限");
            }
            BigDecimal singleRatio = amount.divide(afterAsset, 4, RoundingMode.HALF_UP);
            if (singleRatio.compareTo(singleLimit) > 0) {
                throw new BusinessException("下单后单票仓位将超限");
            }
            String industry = resolveIndustry(code);
            if (StringUtils.isNotBlank(industry) && Objects.nonNull(industryLimit)) {
                BigDecimal sameIndustryMv = amount;
                List<PaperPosition> positions = paperPositionMapper.selectList(Wrappers.<PaperPosition>lambdaQuery()
                        .eq(PaperPosition::getAccountId, accountId));
                for (PaperPosition position : positions) {
                    if (!industry.equals(resolveIndustry(position.getCode()))) {
                        continue;
                    }
                    BigDecimal close = latestClose(position.getCode());
                    if (Objects.isNull(close)) {
                        continue;
                    }
                    sameIndustryMv = sameIndustryMv.add(close.multiply(BigDecimal.valueOf(position.getQuantity())));
                }
                BigDecimal industryRatio = sameIndustryMv.divide(afterAsset, 4, RoundingMode.HALF_UP);
                if (industryRatio.compareTo(industryLimit) > 0) {
                    throw new BusinessException("下单后行业仓位将超限: " + industry);
                }
            }
        }
    }

    @Override
    public List<RiskRule> listRules() {
        return riskRuleMapper.selectList(Wrappers.<RiskRule>lambdaQuery().orderByAsc(RiskRule::getId));
    }

    /**
     * 更新单条风控规则
     *
     * @param req 请求
     * @return 规则
     */
    @Override
    public RiskRule updateRule(RiskRuleUpdateReq req) {
        if (Objects.isNull(req) || StringUtils.isBlank(req.getRuleKey()) || StringUtils.isBlank(req.getRuleValue())) {
            throw new BusinessException("规则键值不能为空");
        }
        try {
            new BigDecimal(req.getRuleValue().trim());
        } catch (Exception ex) {
            throw new BusinessException("规则值必须为数字");
        }
        RiskRule rule = riskRuleMapper.selectOne(Wrappers.<RiskRule>lambdaQuery()
                .eq(RiskRule::getRuleKey, req.getRuleKey().trim())
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (Objects.isNull(rule)) {
            rule = RiskRule.builder()
                    .ruleKey(req.getRuleKey().trim())
                    .ruleValue(req.getRuleValue().trim())
                    .remark("自定义")
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();
            riskRuleMapper.insert(rule);
            return rule;
        }
        rule.setRuleValue(req.getRuleValue().trim());
        rule.setUpdateTime(now);
        riskRuleMapper.updateById(rule);
        return rule;
    }

    /**
     * 应用风控预设
     *
     * @param preset 预设名
     * @return 规则列表
     */
    @Override
    public List<RiskRule> applyPreset(String preset) {
        String name = StringUtils.isBlank(preset) ? "balanced" : preset.trim().toLowerCase();
        Map<String, String> values = new LinkedHashMap<>();
        if ("conservative".equals(name) || "保守".equals(preset)) {
            values.put("total_position_limit", "0.60");
            values.put("single_stock_limit", "0.10");
            values.put("industry_limit", "0.20");
            values.put("stop_loss_pct", "0.05");
            values.put("take_profit_pct", "0.12");
        } else if ("aggressive".equals(name) || "激进".equals(preset)) {
            values.put("total_position_limit", "0.95");
            values.put("single_stock_limit", "0.25");
            values.put("industry_limit", "0.40");
            values.put("stop_loss_pct", "0.12");
            values.put("take_profit_pct", "0.30");
        } else if ("balanced".equals(name) || "均衡".equals(preset)) {
            values.put("total_position_limit", "0.80");
            values.put("single_stock_limit", "0.15");
            values.put("industry_limit", "0.30");
            values.put("stop_loss_pct", "0.08");
            values.put("take_profit_pct", "0.20");
        } else {
            throw new BusinessException("未知预设: " + preset + "，可用 conservative/balanced/aggressive");
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            RiskRuleUpdateReq req = new RiskRuleUpdateReq();
            req.setRuleKey(entry.getKey());
            req.setRuleValue(entry.getValue());
            updateRule(req);
        }
        return listRules();
    }

    private BigDecimal ruleDecimal(String key, BigDecimal defaultValue) {
        RiskRule rule = riskRuleMapper.selectOne(Wrappers.<RiskRule>lambdaQuery()
                .eq(RiskRule::getRuleKey, key)
                .last("limit 1"));
        if (Objects.isNull(rule)) {
            return defaultValue;
        }
        return new BigDecimal(rule.getRuleValue());
    }

    private BigDecimal latestClose(String code) {
        BarDaily bar = barDailyMapper.selectOne(Wrappers.<BarDaily>lambdaQuery()
                .eq(BarDaily::getCode, code)
                .orderByDesc(BarDaily::getTradeDate)
                .last("limit 1"));
        return Objects.isNull(bar) ? null : bar.getClosePrice();
    }

    private String resolveIndustry(String code) {
        StockBasic basic = stockBasicMapper.selectOne(Wrappers.<StockBasic>lambdaQuery()
                .eq(StockBasic::getCode, code)
                .last("limit 1"));
        if (Objects.isNull(basic) || StringUtils.isBlank(basic.getIndustry())) {
            return null;
        }
        return basic.getIndustry().trim();
    }
}
