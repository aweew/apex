package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 多源热点共振
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotConfluenceItem {

    /**
     * 证券代码
     */
    private String code;

    /**
     * 名称
     */
    private String name;

    /**
     * 出现源数量
     */
    private Integer sourceCount;

    /**
     * 来源列表
     */
    private List<String> sources;

    /**
     * 最佳排名（越小越热）
     */
    private Integer bestRank;

    /**
     * 参考涨跌幅
     */
    private BigDecimal pctChg;

    /**
     * 参考现价
     */
    private BigDecimal price;
}
