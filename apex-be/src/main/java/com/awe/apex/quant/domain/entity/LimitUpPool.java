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
 * 涨停池日快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("limit_up_pool")
public class LimitUpPool implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 证券名称
     */
    private String name;

    /**
     * 涨跌幅%
     */
    private BigDecimal pctChg;

    /**
     * 最新价
     */
    private BigDecimal latestPrice;

    /**
     * 成交额
     */
    private BigDecimal amount;

    /**
     * 流通市值
     */
    private BigDecimal circMv;

    /**
     * 换手率%
     */
    private BigDecimal turnoverRate;

    /**
     * 连板数（首板=1）
     */
    private Integer lianban;

    /**
     * 首次封板时间 HHMMSS
     */
    private String firstSealTime;

    /**
     * 最后封板时间 HHMMSS
     */
    private String lastSealTime;

    /**
     * 炸板次数
     */
    private Integer breakCount;

    /**
     * 封板资金
     */
    private BigDecimal sealAmount;

    /**
     * 所属行业
     */
    private String industry;

    /**
     * 展示题材
     */
    private String theme;

    /**
     * 涨停统计 如 5/5
     */
    private String ztStats;

    /**
     * 来源
     */
    private String source;

    /**
     * 同步时间
     */
    private LocalDateTime syncedAt;

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
