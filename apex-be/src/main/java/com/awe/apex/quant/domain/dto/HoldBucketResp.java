package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 持仓周期分桶汇总
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldBucketResp {

    /**
     * 闭合样本数
     */
    private Integer sampleCount;

    /**
     * 说明
     */
    private String message;

    /**
     * 分桶
     */
    private List<HoldBucketItem> buckets;
}
