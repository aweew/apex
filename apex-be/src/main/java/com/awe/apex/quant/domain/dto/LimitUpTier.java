package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 连板梯队
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimitUpTier {

    /**
     * 连板数（1=首板）
     */
    private Integer lianban;

    /**
     * 梯队标题 如 3板 / 首板
     */
    private String title;

    /**
     * 晋级标签 如 二进三
     */
    private String promoteLabel;

    /**
     * 晋级率%（可空）
     */
    private BigDecimal promoteRate;

    /**
     * 家数
     */
    private Integer count;

    /**
     * 个股列表
     */
    private List<LimitUpStockItem> stocks;
}
