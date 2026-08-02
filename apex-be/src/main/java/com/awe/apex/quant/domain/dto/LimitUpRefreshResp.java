package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 涨停池刷新结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimitUpRefreshResp {

    /**
     * 提示
     */
    private String message;

    /**
     * 脚本日志
     */
    private String log;

    /**
     * 刷新后的天梯
     */
    private LimitUpLadderResp ladder;
}
