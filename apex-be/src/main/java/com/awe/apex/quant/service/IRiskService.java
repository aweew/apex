package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.RiskOverviewResp;
import com.awe.apex.quant.domain.dto.RiskRuleUpdateReq;
import com.awe.apex.quant.domain.entity.RiskRule;

import java.util.List;

/**
 * 风控服务
 */
public interface IRiskService {

    /**
     * 风控概览
     *
     * @param accountId 账户
     * @return 概览
     */
    RiskOverviewResp overview(Long accountId);

    /**
     * 下单前校验，不通过抛业务异常
     *
     * @param accountId 账户
     * @param code      代码
     * @param side      方向
     * @param quantity  数量
     * @param price     价格
     */
    void checkBeforeOrder(Long accountId, String code, String side, Integer quantity, java.math.BigDecimal price);

    /**
     * 规则列表
     *
     * @return 规则
     */
    List<RiskRule> listRules();

    /**
     * 更新单条风控规则
     *
     * @param req 请求
     * @return 规则
     */
    RiskRule updateRule(RiskRuleUpdateReq req);

    /**
     * 应用风控预设：conservative / balanced / aggressive
     *
     * @param preset 预设名
     * @return 规则列表
     */
    List<RiskRule> applyPreset(String preset);
}
