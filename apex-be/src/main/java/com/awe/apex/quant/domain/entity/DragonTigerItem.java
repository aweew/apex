package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 龙虎榜日明细。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("dragon_tiger_item")
public class DragonTigerItem {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 证券代码 */
    private String code;

    /** 证券名称 */
    private String name;

    /** 交易日 */
    private LocalDate tradeDate;

    /** 上榜原因 */
    private String reason;

    /** 收盘价 */
    private BigDecimal closePrice;

    /** 涨跌幅百分比 */
    private BigDecimal pctChg;

    /** 换手率百分比 */
    private BigDecimal turnoverRate;

    /** 龙虎榜净买额元 */
    private BigDecimal netBuyAmount;

    /** 龙虎榜买入额元 */
    private BigDecimal buyAmount;

    /** 龙虎榜卖出额元 */
    private BigDecimal sellAmount;

    /** 龙虎榜成交额元 */
    private BigDecimal amount;

    /** 同步时间 */
    private LocalDateTime syncedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
