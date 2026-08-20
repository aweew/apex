package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 因子分类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactorCategoryResp {

    /**
     * 分类键
     */
    private String key;

    /**
     * 分类名称
     */
    private String label;

    /**
     * 分类说明
     */
    private String description;

    /**
     * 分类下的指标
     */
    private List<FactorItemResp> factors;
}
