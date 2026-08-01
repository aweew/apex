package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 三大报表科目明细（EAV）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("stock_fin_report_item")
public class StockFinReportItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 报表类型 profit/balance/cashflow
     */
    private String statementType;

    /**
     * 报告期
     */
    private LocalDate reportDate;

    /**
     * 科目名称
     */
    private String itemName;

    /**
     * 科目数值
     */
    private BigDecimal itemValue;

    /**
     * 原始文本
     */
    private String itemValueText;

    /**
     * 数据来源
     */
    private String source;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;
}
