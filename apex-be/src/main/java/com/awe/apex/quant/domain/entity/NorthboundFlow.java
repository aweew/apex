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
 * 北向资金日快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("northbound_flow")
public class NorthboundFlow {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 交易日 */
    private LocalDate tradeDate;

    /** 北向资金净买额元 */
    private BigDecimal netBuyAmount;

    /** 北向资金买入额元 */
    private BigDecimal buyAmount;

    /** 北向资金卖出额元 */
    private BigDecimal sellAmount;

    /** 北向资金累计净买额元 */
    private BigDecimal cumulativeNetBuyAmount;

    /** 数据状态PUBLISHED已披露NOT_DISCLOSED未披露 */
    private String dataStatus;

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
