package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 股票池刷新响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UniverseRefreshResp {

    /**
     * 批次号
     */
    private String batchNo;

    /**
     * 入选数量
     */
    private Integer count;
}
