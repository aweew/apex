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
 * 指数日线
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("index_bar")
public class IndexBar implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 内部代码，如 CN_SH / US_DJI
     */
    private String code;

    /**
     * 指数名称
     */
    private String name;

    /**
     * 市场区域 CN/HK/JP/KR/US
     */
    private String region;

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 开盘
     */
    private BigDecimal openPrice;

    /**
     * 最高
     */
    private BigDecimal highPrice;

    /**
     * 最低
     */
    private BigDecimal lowPrice;

    /**
     * 收盘
     */
    private BigDecimal closePrice;

    /**
     * 成交量
     */
    private BigDecimal volume;

    /**
     * 成交额
     */
    private BigDecimal amount;

    /**
     * 涨跌幅%
     */
    private BigDecimal pctChg;

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
