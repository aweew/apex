package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.ScreenerStrategyReorderReq;
import com.awe.apex.quant.domain.dto.ScreenerStrategyResp;
import com.awe.apex.quant.domain.dto.ScreenerStrategyRuleResp;
import com.awe.apex.quant.domain.dto.ScreenerStrategyRuleSaveReq;
import com.awe.apex.quant.domain.dto.ScreenerStrategySaveReq;
import com.awe.apex.quant.domain.entity.ScreenerStrategy;
import com.awe.apex.quant.domain.entity.ScreenerStrategyRule;
import com.awe.apex.quant.domain.enums.ScreenerOperatorEnum;
import com.awe.apex.quant.domain.enums.ScreenerRuleTypeEnum;
import com.awe.apex.quant.domain.enums.ScreenerRunModeEnum;
import com.awe.apex.quant.domain.enums.ScreenerStrategySourceEnum;
import com.awe.apex.quant.mapper.ScreenerStrategyMapper;
import com.awe.apex.quant.mapper.ScreenerStrategyRuleMapper;
import com.awe.apex.quant.screener.ScreenerStrategyTemplateRegistry;
import com.awe.apex.quant.service.IScreenerStrategyService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 可维护选股策略服务实现
 */
@Slf4j
@Service
public class ScreenerStrategyServiceImpl implements IScreenerStrategyService {

    private static final int MAX_RULE_COUNT = 30;

    @Resource
    private ApexUserContext userContext;

    @Resource
    private ScreenerStrategyMapper strategyMapper;

    @Resource
    private ScreenerStrategyRuleMapper ruleMapper;

    @Resource
    private ScreenerStrategyTemplateRegistry templateRegistry;

    /**
     * 查询系统模板和当前用户策略。
     *
     * @return 策略列表
     */
    @Override
    public List<ScreenerStrategyResp> list() {
        List<ScreenerStrategyResp> result = new ArrayList<>(templateRegistry.listTemplates());
        List<ScreenerStrategy> strategies = strategyMapper.selectList(Wrappers.<ScreenerStrategy>lambdaQuery()
                .eq(ScreenerStrategy::getUserId, userContext.currentUserId())
                .orderByAsc(ScreenerStrategy::getSortNo)
                .orderByDesc(ScreenerStrategy::getId));
        if (CollUtil.isEmpty(strategies)) {
            return result;
        }
        Map<Long, List<ScreenerStrategyRule>> ruleMap = loadRuleMap(strategies);
        for (ScreenerStrategy strategy : strategies) {
            result.add(toResp(strategy, ruleMap.getOrDefault(strategy.getId(), List.of())));
        }
        return result;
    }

    /**
     * 查询当前用户策略详情。
     *
     * @param id 策略ID
     * @return 策略详情
     */
    @Override
    public ScreenerStrategyResp detail(Long id) {
        ScreenerStrategy strategy = requireOwned(id);
        List<ScreenerStrategyRule> rules = ruleMapper.selectList(Wrappers.<ScreenerStrategyRule>lambdaQuery()
                .eq(ScreenerStrategyRule::getStrategyId, id)
                .orderByAsc(ScreenerStrategyRule::getSortNo)
                .orderByAsc(ScreenerStrategyRule::getId));
        return toResp(strategy, rules);
    }

    /**
     * 新增或更新当前用户策略。
     *
     * @param req 保存请求
     * @return 策略详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScreenerStrategyResp save(ScreenerStrategySaveReq req) {
        return saveDefinition(req, ScreenerStrategySourceEnum.USER.getCode(), null);
    }

    /**
     * 将系统模板复制为当前用户策略。
     *
     * @param templateKey 模板标识
     * @return 新策略
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScreenerStrategyResp copyTemplate(String templateKey) {
        ScreenerStrategyResp template = templateRegistry.getTemplate(templateKey);
        if (Objects.isNull(template)) {
            throw new BusinessException("系统模板不存在");
        }
        ScreenerStrategySaveReq req = toSaveReq(template);
        req.setName(nextCopyName(template.getName()));
        return saveDefinition(req, ScreenerStrategySourceEnum.TEMPLATE_COPY.getCode(), templateKey);
    }

    /**
     * 复制当前用户策略。
     *
     * @param id 原策略ID
     * @return 新策略
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScreenerStrategyResp copy(Long id) {
        ScreenerStrategyResp source = detail(id);
        ScreenerStrategySaveReq req = toSaveReq(source);
        req.setName(nextCopyName(source.getName()));
        String sourceType = StringUtils.isNotBlank(source.getTemplateKey())
                ? ScreenerStrategySourceEnum.TEMPLATE_COPY.getCode()
                : ScreenerStrategySourceEnum.USER.getCode();
        return saveDefinition(req, sourceType, source.getTemplateKey());
    }

    /**
     * 启用或停用当前用户策略。
     *
     * @param id      策略ID
     * @param enabled 是否启用
     * @return 更新后策略
     */
    @Override
    public ScreenerStrategyResp toggle(Long id, Boolean enabled) {
        if (Objects.isNull(enabled)) {
            throw new BusinessException("启用状态不能为空");
        }
        ScreenerStrategy strategy = requireOwned(id);
        strategy.setEnabled(Boolean.TRUE.equals(enabled) ? 1 : 0);
        strategy.setUpdateTime(LocalDateTime.now());
        strategyMapper.updateById(strategy);
        log.info("选股策略状态更新 userId={}, strategyId={}, enabled={}",
                strategy.getUserId(), strategy.getId(), enabled);
        return detail(id);
    }

    /**
     * 调整当前用户策略顺序。
     *
     * @param req 排序请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorder(ScreenerStrategyReorderReq req) {
        if (Objects.isNull(req) || CollUtil.isEmpty(req.getStrategyIds())) {
            throw new BusinessException("策略顺序不能为空");
        }
        Long userId = userContext.currentUserId();
        List<ScreenerStrategy> strategies = strategyMapper.selectList(Wrappers.<ScreenerStrategy>lambdaQuery()
                .eq(ScreenerStrategy::getUserId, userId)
                .in(ScreenerStrategy::getId, req.getStrategyIds()));
        if (strategies.size() != req.getStrategyIds().size()) {
            throw new BusinessException("策略顺序包含无权访问的策略");
        }
        Map<Long, ScreenerStrategy> strategyMap = new HashMap<>();
        for (ScreenerStrategy strategy : strategies) {
            strategyMap.put(strategy.getId(), strategy);
        }
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < req.getStrategyIds().size(); index++) {
            ScreenerStrategy strategy = strategyMap.get(req.getStrategyIds().get(index));
            strategy.setSortNo((index + 1) * 10);
            strategy.setUpdateTime(now);
            strategyMapper.updateById(strategy);
        }
        log.info("选股策略排序完成 userId={}, count={}", userId, strategies.size());
    }

    /**
     * 删除当前用户策略。
     *
     * @param id 策略ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        ScreenerStrategy strategy = requireOwned(id);
        ruleMapper.delete(Wrappers.<ScreenerStrategyRule>lambdaQuery()
                .eq(ScreenerStrategyRule::getStrategyId, id));
        strategyMapper.deleteById(id);
        log.info("选股策略删除 userId={}, strategyId={}", strategy.getUserId(), id);
    }

    /**
     * 解析可运行的用户策略或系统模板。
     *
     * @param strategyId  策略ID
     * @param templateKey 模板标识
     * @return 策略定义
     */
    @Override
    public ScreenerStrategyResp resolveRunnable(Long strategyId, String templateKey) {
        if (Objects.nonNull(strategyId)) {
            ScreenerStrategyResp strategy = detail(strategyId);
            if (!Boolean.TRUE.equals(strategy.getEnabled())) {
                throw new BusinessException("策略已停用，请先启用后再运行");
            }
            return strategy;
        }
        if (StringUtils.isBlank(templateKey)) {
            throw new BusinessException("请选择要运行的策略");
        }
        ScreenerStrategyResp template = templateRegistry.getTemplate(templateKey.trim());
        if (Objects.isNull(template)) {
            throw new BusinessException("系统模板不存在");
        }
        return template;
    }

    private ScreenerStrategyResp saveDefinition(ScreenerStrategySaveReq req, String sourceType,
                                                 String templateKey) {
        validate(req);
        Long userId = userContext.currentUserId();
        LocalDateTime now = LocalDateTime.now();
        ScreenerStrategy strategy;
        if (Objects.nonNull(req.getId())) {
            strategy = requireOwned(req.getId());
            strategy.setName(req.getName().trim());
            strategy.setDescription(StringUtils.trim(req.getDescription()));
            strategy.setRunMode(normalizeRunMode(req.getRunMode()));
            strategy.setEnabled(Boolean.FALSE.equals(req.getEnabled()) ? 0 : 1);
            strategy.setSortNo(Objects.nonNull(req.getSortNo()) ? req.getSortNo() : strategy.getSortNo());
            strategy.setVersionNo((Objects.nonNull(strategy.getVersionNo()) ? strategy.getVersionNo() : 0) + 1);
            strategy.setUpdateTime(now);
            strategyMapper.updateById(strategy);
            ruleMapper.delete(Wrappers.<ScreenerStrategyRule>lambdaQuery()
                    .eq(ScreenerStrategyRule::getStrategyId, strategy.getId()));
        } else {
            strategy = ScreenerStrategy.builder()
                    .userId(userId)
                    .name(req.getName().trim())
                    .description(StringUtils.trim(req.getDescription()))
                    .sourceType(sourceType)
                    .templateKey(templateKey)
                    .runMode(normalizeRunMode(req.getRunMode()))
                    .enabled(Boolean.FALSE.equals(req.getEnabled()) ? 0 : 1)
                    .sortNo(Objects.nonNull(req.getSortNo()) ? req.getSortNo() : nextSortNo(userId))
                    .versionNo(1)
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();
            strategyMapper.insert(strategy);
        }
        int index = 0;
        for (ScreenerStrategyRuleSaveReq ruleReq : req.getRules()) {
            ScreenerStrategyRule rule = ScreenerStrategyRule.builder()
                    .strategyId(strategy.getId())
                    .ruleType(ruleReq.getRuleType().trim().toUpperCase())
                    .operatorCode(ruleReq.getOperatorCode().trim().toUpperCase())
                    .minValue(ruleReq.getMinValue())
                    .maxValue(ruleReq.getMaxValue())
                    .intValue(ruleReq.getIntValue())
                    .textValue(StringUtils.trim(ruleReq.getTextValue()))
                    .boolValue(Objects.isNull(ruleReq.getBoolValue()) ? null
                            : (Boolean.TRUE.equals(ruleReq.getBoolValue()) ? 1 : 0))
                    .lookbackDays(ruleReq.getLookbackDays())
                    .toleranceValue(ruleReq.getToleranceValue())
                    .sortNo(Objects.nonNull(ruleReq.getSortNo()) ? ruleReq.getSortNo() : (index + 1) * 10)
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();
            ruleMapper.insert(rule);
            index++;
        }
        log.info("选股策略保存 userId={}, strategyId={}, version={}, ruleCount={}",
                userId, strategy.getId(), strategy.getVersionNo(), req.getRules().size());
        return detail(strategy.getId());
    }

    private void validate(ScreenerStrategySaveReq req) {
        if (Objects.isNull(req) || StringUtils.isBlank(req.getName())) {
            throw new BusinessException("策略名称不能为空");
        }
        if (req.getName().trim().length() > 64) {
            throw new BusinessException("策略名称不能超过 64 个字");
        }
        normalizeRunMode(req.getRunMode());
        if (CollUtil.isEmpty(req.getRules())) {
            throw new BusinessException("策略至少需要一条规则");
        }
        if (req.getRules().size() > MAX_RULE_COUNT) {
            throw new BusinessException("单个策略最多支持 " + MAX_RULE_COUNT + " 条规则");
        }
        for (ScreenerStrategyRuleSaveReq rule : req.getRules()) {
            validateRule(rule);
        }
    }

    private void validateRule(ScreenerStrategyRuleSaveReq rule) {
        if (Objects.isNull(rule) || Objects.isNull(findRuleType(rule.getRuleType()))) {
            throw new BusinessException("存在不支持的规则类型");
        }
        ScreenerOperatorEnum operator = findOperator(rule.getOperatorCode());
        if (Objects.isNull(operator)) {
            throw new BusinessException("规则操作符无效: " + rule.getRuleType());
        }
        if (ScreenerOperatorEnum.BETWEEN.equals(operator)) {
            if (Objects.isNull(rule.getMinValue()) || Objects.isNull(rule.getMaxValue())
                    || rule.getMinValue().compareTo(rule.getMaxValue()) > 0) {
                throw new BusinessException("区间规则上下限无效: " + rule.getRuleType());
            }
        }
        if (Objects.nonNull(rule.getLookbackDays())
                && (rule.getLookbackDays() < 1 || rule.getLookbackDays() > 250)) {
            throw new BusinessException("回看交易日数需在 1 - 250");
        }
        boolean hasValue = Objects.nonNull(rule.getMinValue()) || Objects.nonNull(rule.getIntValue())
                || StringUtils.isNotBlank(rule.getTextValue()) || Objects.nonNull(rule.getBoolValue());
        if (!ScreenerOperatorEnum.BETWEEN.equals(operator) && !hasValue) {
            throw new BusinessException("规则缺少比较值: " + rule.getRuleType());
        }
    }

    private ScreenerStrategy requireOwned(Long id) {
        if (Objects.isNull(id)) {
            throw new BusinessException("策略ID不能为空");
        }
        ScreenerStrategy strategy = strategyMapper.selectOne(Wrappers.<ScreenerStrategy>lambdaQuery()
                .eq(ScreenerStrategy::getId, id)
                .eq(ScreenerStrategy::getUserId, userContext.currentUserId())
                .last("LIMIT 1"));
        if (Objects.isNull(strategy)) {
            throw new BusinessException("策略不存在或无权访问");
        }
        if (!userContext.currentUserId().equals(strategy.getUserId())) {
            throw new BusinessException("策略不存在或无权访问");
        }
        return strategy;
    }

    private int nextSortNo(Long userId) {
        ScreenerStrategy last = strategyMapper.selectOne(Wrappers.<ScreenerStrategy>lambdaQuery()
                .eq(ScreenerStrategy::getUserId, userId)
                .orderByDesc(ScreenerStrategy::getSortNo)
                .orderByDesc(ScreenerStrategy::getId)
                .last("LIMIT 1"));
        return Objects.nonNull(last) && Objects.nonNull(last.getSortNo()) ? last.getSortNo() + 10 : 10;
    }

    private String nextCopyName(String sourceName) {
        String base = StringUtils.isNotBlank(sourceName) ? sourceName.trim() : "未命名策略";
        String name = base + " 副本";
        if (name.length() <= 64) {
            return name;
        }
        return base.substring(0, Math.min(base.length(), 61)) + "副本";
    }

    private Map<Long, List<ScreenerStrategyRule>> loadRuleMap(List<ScreenerStrategy> strategies) {
        List<Long> ids = new ArrayList<>();
        for (ScreenerStrategy strategy : strategies) {
            ids.add(strategy.getId());
        }
        List<ScreenerStrategyRule> rules = ruleMapper.selectList(Wrappers.<ScreenerStrategyRule>lambdaQuery()
                .in(ScreenerStrategyRule::getStrategyId, ids)
                .orderByAsc(ScreenerStrategyRule::getSortNo)
                .orderByAsc(ScreenerStrategyRule::getId));
        Map<Long, List<ScreenerStrategyRule>> ruleMap = new HashMap<>();
        for (ScreenerStrategyRule rule : rules) {
            ruleMap.computeIfAbsent(rule.getStrategyId(), key -> new ArrayList<>()).add(rule);
        }
        return ruleMap;
    }

    private ScreenerStrategyResp toResp(ScreenerStrategy strategy, List<ScreenerStrategyRule> rules) {
        List<ScreenerStrategyRuleResp> ruleResponses = new ArrayList<>();
        for (ScreenerStrategyRule rule : rules) {
            ScreenerRuleTypeEnum type = findRuleType(rule.getRuleType());
            ScreenerOperatorEnum operator = findOperator(rule.getOperatorCode());
            ruleResponses.add(ScreenerStrategyRuleResp.builder()
                    .id(rule.getId())
                    .ruleType(rule.getRuleType())
                    .ruleName(Objects.nonNull(type) ? type.getDesc() : rule.getRuleType())
                    .operatorCode(rule.getOperatorCode())
                    .operatorName(Objects.nonNull(operator) ? operator.getDesc() : rule.getOperatorCode())
                    .minValue(rule.getMinValue())
                    .maxValue(rule.getMaxValue())
                    .intValue(rule.getIntValue())
                    .textValue(rule.getTextValue())
                    .boolValue(Objects.isNull(rule.getBoolValue()) ? null : rule.getBoolValue() == 1)
                    .lookbackDays(rule.getLookbackDays())
                    .toleranceValue(rule.getToleranceValue())
                    .sortNo(rule.getSortNo())
                    .summary(buildSummary(type, operator, rule))
                    .build());
        }
        ruleResponses.sort(Comparator.comparing(ScreenerStrategyRuleResp::getSortNo,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return ScreenerStrategyResp.builder()
                .id(strategy.getId())
                .templateKey(strategy.getTemplateKey())
                .name(strategy.getName())
                .description(strategy.getDescription())
                .sourceType(strategy.getSourceType())
                .runMode(strategy.getRunMode())
                .enabled(Objects.nonNull(strategy.getEnabled()) && strategy.getEnabled() == 1)
                .sortNo(strategy.getSortNo())
                .versionNo(strategy.getVersionNo())
                .template(false)
                .editable(true)
                .disclaimer(StringUtils.isNotBlank(strategy.getTemplateKey())
                        ? "复制自常用模板，收益未经验证，不构成投资建议。" : null)
                .rules(ruleResponses)
                .updateTime(strategy.getUpdateTime())
                .build();
    }

    private String buildSummary(ScreenerRuleTypeEnum type, ScreenerOperatorEnum operator,
                                ScreenerStrategyRule rule) {
        if (ScreenerRuleTypeEnum.MARKET_BOARD.equals(type)) {
            return "市场范围 " + ("MAIN_BOARD".equalsIgnoreCase(rule.getTextValue())
                    ? "沪深主板" : rule.getTextValue());
        }
        if (ScreenerRuleTypeEnum.EXCLUDE_ST.equals(type)) {
            return Objects.nonNull(rule.getBoolValue()) && rule.getBoolValue() == 1 ? "排除 ST" : "包含 ST";
        }
        if (ScreenerRuleTypeEnum.INTRADAY_CURRENT_ABOVE_AVG.equals(type)) {
            return Objects.nonNull(rule.getBoolValue()) && rule.getBoolValue() == 1
                    ? "当前价不低于分时均价" : "当前价低于分时均价";
        }
        String name = Objects.nonNull(type) ? type.getDesc() : rule.getRuleType();
        String lookback = Objects.nonNull(rule.getLookbackDays()) ? "近 " + rule.getLookbackDays() + " 日 " : "";
        if (ScreenerOperatorEnum.BETWEEN.equals(operator)) {
            return lookback + name + " " + formatRuleValue(type, rule, false)
                    + " - " + formatRuleValue(type, rule, true);
        }
        return lookback + name + " " + (Objects.nonNull(operator) ? operator.getDesc() : "")
                + " " + formatRuleValue(type, rule, false);
    }

    private String formatRuleValue(ScreenerRuleTypeEnum type, ScreenerStrategyRule rule, boolean maximum) {
        BigDecimal numberValue = maximum ? rule.getMaxValue() : rule.getMinValue();
        if (Objects.nonNull(numberValue)) {
            if (ScreenerRuleTypeEnum.TOTAL_MV.equals(type) || ScreenerRuleTypeEnum.CIRC_MV.equals(type)
                    || ScreenerRuleTypeEnum.AMOUNT.equals(type)) {
                return decimal(numberValue.movePointLeft(8)) + "亿";
            }
            if (ScreenerRuleTypeEnum.SEAL_AMOUNT.equals(type)) {
                return decimal(numberValue.movePointLeft(4)) + "万";
            }
            String value = decimal(numberValue);
            if (ScreenerRuleTypeEnum.PCT_CHG.equals(type)
                    || ScreenerRuleTypeEnum.TURNOVER_RATE.equals(type)
                    || ScreenerRuleTypeEnum.RANGE_RETURN.equals(type)
                    || ScreenerRuleTypeEnum.RS20.equals(type)
                    || ScreenerRuleTypeEnum.ATR_PCT.equals(type)
                    || ScreenerRuleTypeEnum.PRICE_POSITION.equals(type)
                    || ScreenerRuleTypeEnum.INTRADAY_ABOVE_AVG_RATIO.equals(type)) {
                return value + "%";
            }
            return value;
        }
        if (Objects.nonNull(rule.getIntValue())) {
            String unit = switch (type) {
                case LIMIT_UP_LEVEL -> "板";
                case LIMIT_UP_COUNT, BREAK_COUNT -> "次";
                case UP_DAYS -> "天";
                case INTRADAY_MAX_BELOW_MINUTES -> "分钟";
                case THEME_LINKAGE_COUNT -> "家";
                default -> "";
            };
            return rule.getIntValue() + unit;
        }
        if (StringUtils.isNotBlank(rule.getTextValue())) {
            String textValue = rule.getTextValue();
            if ((ScreenerRuleTypeEnum.FIRST_SEAL_TIME.equals(type)
                    || ScreenerRuleTypeEnum.LAST_SEAL_TIME.equals(type)) && textValue.length() == 6) {
                return textValue.substring(0, 2) + ":" + textValue.substring(2, 4);
            }
            return textValue;
        }
        if (Objects.nonNull(rule.getBoolValue())) {
            return rule.getBoolValue() == 1 ? "是" : "否";
        }
        return "-";
    }

    private String decimal(BigDecimal value) {
        return Objects.nonNull(value) ? value.stripTrailingZeros().toPlainString() : "-";
    }

    private ScreenerStrategySaveReq toSaveReq(ScreenerStrategyResp source) {
        List<ScreenerStrategyRuleSaveReq> rules = new ArrayList<>();
        for (ScreenerStrategyRuleResp rule : source.getRules()) {
            rules.add(ScreenerStrategyRuleSaveReq.builder()
                    .ruleType(rule.getRuleType())
                    .operatorCode(rule.getOperatorCode())
                    .minValue(rule.getMinValue())
                    .maxValue(rule.getMaxValue())
                    .intValue(rule.getIntValue())
                    .textValue(rule.getTextValue())
                    .boolValue(rule.getBoolValue())
                    .lookbackDays(rule.getLookbackDays())
                    .toleranceValue(rule.getToleranceValue())
                    .sortNo(rule.getSortNo())
                    .build());
        }
        return ScreenerStrategySaveReq.builder()
                .name(source.getName())
                .description(source.getDescription())
                .runMode(source.getRunMode())
                .enabled(true)
                .rules(rules)
                .build();
    }

    private String normalizeRunMode(String runMode) {
        String normalized = StringUtils.isNotBlank(runMode)
                ? runMode.trim().toUpperCase() : ScreenerRunModeEnum.REALTIME.getCode();
        for (ScreenerRunModeEnum value : ScreenerRunModeEnum.values()) {
            if (value.getCode().equals(normalized)) {
                return normalized;
            }
        }
        throw new BusinessException("不支持的策略运行模式");
    }

    private ScreenerRuleTypeEnum findRuleType(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        for (ScreenerRuleTypeEnum value : ScreenerRuleTypeEnum.values()) {
            if (value.getCode().equalsIgnoreCase(code.trim())) {
                return value;
            }
        }
        return null;
    }

    private ScreenerOperatorEnum findOperator(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        for (ScreenerOperatorEnum value : ScreenerOperatorEnum.values()) {
            if (value.getCode().equalsIgnoreCase(code.trim())) {
                return value;
            }
        }
        return null;
    }
}
