package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.DecisionPlaybookResp;

/**
 * 决策战法与交易规则
 */
public interface IDecisionPlaybookService {

    /**
     * 获取决策战法手册
     *
     * @return 手册
     */
    DecisionPlaybookResp playbook();
}
