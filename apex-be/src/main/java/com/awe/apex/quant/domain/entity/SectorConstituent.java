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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 板块成分股快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sector_constituent")
public class SectorConstituent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 板块代码
     */
    private String sectorCode;

    /**
     * 板块类型
     */
    private String boardType;

    /**
     * 成分股代码
     */
    private String stockCode;

    /**
     * 成分股名称
     */
    private String stockName;

    /**
     * 涨跌幅%
     */
    private BigDecimal pctChg;

    /**
     * 最新价
     */
    private BigDecimal latestPrice;

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 同步时间
     */
    private LocalDateTime syncedAt;

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
