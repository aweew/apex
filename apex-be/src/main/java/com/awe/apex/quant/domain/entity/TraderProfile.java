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

/** Smart Trader 交易风格画像。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("trader_profile")
public class TraderProfile implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    /** 主键 */ @TableId(type = IdType.AUTO) private Long id;
    /** 交易者ID */ private Long traderId;
    /** 风格标签 */ private String style;
    /** 偏好行业JSON */ private String preferredIndustries;
    /** 平均持仓天数 */ private BigDecimal averageHoldingDays;
    /** 胜率 */ private BigDecimal winRate;
    /** 盈亏比 */ private BigDecimal profitLossRatio;
    /** 最大回撤 */ private BigDecimal maxDrawdown;
    /** 换手率 */ private BigDecimal turnoverRate;
    /** 波动率 */ private BigDecimal volatility;
    /** 集中度 */ private BigDecimal concentration;
    /** 画像说明 */ private String summary;
    /** 创建时间 */ private LocalDateTime createTime;
    /** 更新时间 */ private LocalDateTime updateTime;
    /** 逻辑删除 */ @TableLogic private Integer deleted;
}
