package com.awe.apex.quant.domain.dto;

import lombok.Data;

/**
 * 保存组合
 */
@Data
public class PortfolioSaveReq {

    /**
     * 主键，更新时必填
     */
    private Long id;

    /**
     * 组合名称
     */
    private String name;

    /**
     * 备注
     */
    private String note;

    /**
     * 实盘归属人标签
     */
    private String ownerLabel;

    /**
     * ACTIVE / ARCHIVED
     */
    private String status;

    /**
     * 排序
     */
    private Integer sortNo;
}
