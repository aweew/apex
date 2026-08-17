package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 回测实验历史详情
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestExperimentDetailResp {

    /**
     * 实验ID
     */
    private Long id;

    /**
     * 实际评估请求
     */
    private RollingBacktestReq request;

    /**
     * 完整评估结果
     */
    private RollingBacktestResp result;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
