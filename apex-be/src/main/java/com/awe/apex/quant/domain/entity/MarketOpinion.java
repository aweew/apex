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
import java.time.LocalDateTime;

/**
 * 公开市场观点与活跃席位快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("market_opinion")
public class MarketOpinion {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 类型 INSTITUTION/ACTIVE_SEAT/KOL */
    private String opinionType;

    /** 数据来源 */
    private String source;

    /** 来源内去重键 */
    private String externalId;

    /** 观点主体 */
    private String subjectName;

    /** 关联市场主体名称 */
    private String actorName;

    /** 关联市场主体类型 */
    private String actorType;

    /** 主体关联置信度 */
    private String actorConfidence;

    /** 主体关联证据链接 */
    private String actorEvidenceUrl;

    /** 原始标题 */
    private String title;

    /** 原始摘要 */
    private String summary;

    /** 评级或行为方向 */
    private String direction;

    /** 关联证券代码 */
    private String relatedCode;

    /** 关联证券名称 */
    private String relatedName;

    /** 行业或主题 */
    private String topic;

    /** 净买卖额，元 */
    private BigDecimal netAmount;

    /** 原文链接 */
    private String url;

    /** 公开发布时间 */
    private LocalDateTime publishedAt;

    /** 同步快照时间 */
    private LocalDateTime snapshotTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
