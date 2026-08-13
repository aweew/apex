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
import java.time.LocalDateTime;

/**
 * 实盘组合
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("portfolio")
public class Portfolio implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
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
     * 是否默认组合（我的持仓）
     */
    private Integer isDefault;

    /**
     * ACTIVE / ARCHIVED
     */
    private String status;

    /**
     * 排序
     */
    private Integer sortNo;

    /**
     * 现金余额
     */
    private BigDecimal cashBalance;

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
