package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
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
 * 交易事件原始证据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("trade_evidence")
public class TradeEvidence implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 交易事件ID */
    private Long tradeEventId;
    /** 交易者ID */
    private Long traderId;
    /** 证据来源 */
    private String source;
    /** 原始文本 */
    private String rawText;
    /** 图片地址 */
    private String imageUrl;
    /** AI 标准化结果JSON */
    private String parsedResult;
    /** 解析置信度 */
    private BigDecimal confidence;
    /** 创建时间 */
    private LocalDateTime createTime;
}
