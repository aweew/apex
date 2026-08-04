package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 组合操作提示 / 风险项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioTipItem {

    /**
     * 级别：critical / warn / info
     */
    private String level;

    /**
     * 提示正文
     */
    private String text;

    /**
     * 关联证券代码（可空）
     */
    private String code;

    /**
     * 关联证券简称（可空）
     */
    private String name;
}
