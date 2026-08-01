package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 收益分布直方桶
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnHistItem {

    /**
     * 区间标签
     */
    private String bucket;

    /**
     * 笔数
     */
    private Integer count;
}
