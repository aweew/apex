package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 选股策略运行请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerStrategyRunReq {

    /** 用户策略ID，与模板标识二选一 */
    private Long strategyId;

    /** 系统模板标识，与用户策略ID二选一 */
    private String templateKey;

    /** 最大返回数量 */
    private Integer limit;
}
