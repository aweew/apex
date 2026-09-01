package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 盘后龙虎榜活跃席位及知名游资证据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostMarketActiveSeatResp {

    /** 龙虎榜营业部或观点主体。 */
    private String subjectName;

    /** 已映射的知名游资名称。 */
    private String actorName;

    /** 市场主体类型。 */
    private String actorType;

    /** 主体映射置信度。 */
    private String actorConfidence;

    /** 主体映射证据链接。 */
    private String actorEvidenceUrl;

    /** 买卖方向。 */
    private String direction;

    /** 关联证券代码。 */
    private String relatedCode;

    /** 关联证券名称。 */
    private String relatedName;

    /** 关联行业或主题。 */
    private String topic;

    /** 净买卖额，元。 */
    private BigDecimal netAmount;

    /** 数据来源。 */
    private String source;

    /** 原始标题。 */
    private String title;

    /** 原始摘要。 */
    private String summary;

    /** 原始证据链接。 */
    private String url;

    /** 公开发布时间。 */
    private LocalDateTime publishedAt;

    /** 同步快照时间。 */
    private LocalDateTime snapshotTime;
}
