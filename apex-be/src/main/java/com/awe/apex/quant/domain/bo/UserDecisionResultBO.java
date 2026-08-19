package com.awe.apex.quant.domain.bo;

import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个用户的智能决策执行结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDecisionResultBO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 是否执行成功
     */
    private Boolean success;

    /**
     * 决策结果
     */
    private DecisionTodayResp response;

    /**
     * 失败原因
     */
    private String errorMessage;
}
