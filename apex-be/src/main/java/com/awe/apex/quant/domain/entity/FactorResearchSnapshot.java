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
 * 个股横截面因子研究快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("factor_research_snapshot")
public class FactorResearchSnapshot {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 快照交易日 */
    private LocalDate tradeDate;

    /** 模型版本 */
    private String modelVersion;

    /** 证券代码 */
    private String code;

    /** 所属行业 */
    private String industry;

    /** 质量原始值ROE */
    private BigDecimal qualityRaw;

    /** 质量可比组分位 */
    private BigDecimal qualityPercentile;

    /** 成长原始值净利润同比 */
    private BigDecimal growthRaw;

    /** 成长可比组分位 */
    private BigDecimal growthPercentile;

    /** 估值原始值盈利收益率 */
    private BigDecimal valuationRaw;

    /** 估值可比组分位 */
    private BigDecimal valuationPercentile;

    /** 动量原始值相对基准强度 */
    private BigDecimal momentumRaw;

    /** 动量可比组分位 */
    private BigDecimal momentumPercentile;

    /** 资金原始值成交额强度 */
    private BigDecimal capitalRaw;

    /** 资金可比组分位 */
    private BigDecimal capitalPercentile;

    /** 研究评分 */
    private BigDecimal researchScore;

    /** 可用权重覆盖率 */
    private BigDecimal coverage;

    /** 全市场候选样本数 */
    private Integer universeSize;

    /** 快照生成时间 */
    private LocalDateTime capturedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
