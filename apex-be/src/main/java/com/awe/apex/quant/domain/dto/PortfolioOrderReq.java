package com.awe.apex.quant.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 组合排序请求
 */
@Data
public class PortfolioOrderReq {

    /**
     * 组合ID，按展示顺序排列
     */
    private List<Long> portfolioIds;
}
