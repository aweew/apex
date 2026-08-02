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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 市场简报日快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("market_briefing_snapshot")
public class MarketBriefingSnapshot implements Serializable {

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
     * 立场
     */
    private String stance;

    /**
     * 评分
     */
    private Integer stanceScore;

    /**
     * 数据等级
     */
    private String dataLevel;

    /**
     * 完整 JSON
     */
    private String payloadJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
