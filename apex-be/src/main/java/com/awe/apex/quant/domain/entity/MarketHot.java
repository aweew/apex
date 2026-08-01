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

/**
 * 多平台热点股票快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("market_hot")
public class MarketHot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 来源 eastmoney/xueqiu/baidu
     */
    private String source;

    /**
     * 快照时间
     */
    private LocalDateTime snapshotTime;

    /**
     * 排名
     */
    private Integer rankNo;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 名称
     */
    private String name;

    /**
     * 最新价
     */
    private BigDecimal price;

    /**
     * 涨跌幅%
     */
    private BigDecimal pctChg;

    /**
     * 热度分值
     */
    private BigDecimal heatScore;

    /**
     * 热度说明
     */
    private String heatText;

    /**
     * 原始 JSON
     */
    private String payload;

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
