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
import java.time.LocalDateTime;

/**
 * 市场新闻资讯
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("market_news")
public class MarketNews implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 来源 eastmoney/cls/ths/sina/cctv
     */
    private String source;

    /**
     * 去重键（来源内唯一）
     */
    private String externalId;

    /**
     * 标题
     */
    private String title;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 正文/快讯内容
     */
    private String content;

    /**
     * 原文链接
     */
    private String url;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 关联代码（逗号分隔）
     */
    private String relatedCodes;

    /**
     * 情感：利好/利空/中性
     */
    private String sentiment;

    /**
     * 同步快照时间
     */
    private LocalDateTime snapshotTime;

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
