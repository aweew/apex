package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 闭合交易收益分布
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnHistResp {

    /**
     * 样本数
     */
    private Integer sampleCount;

    /**
     * 说明
     */
    private String message;

    /**
     * 直方
     */
    private List<ReturnHistItem> buckets;
}
