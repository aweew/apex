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
 * 股票池快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("universe_snapshot")
public class UniverseSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 创建用户ID
     */
    private Long creatorUserId;

    /**
     * 批次号
     */
    private String batchNo;

    /**
     * 数据截止日
     */
    private LocalDate asOfDate;

    /**
     * 证券代码
     */
    private String code;

    /**
     * 证券简称
     */
    private String name;

    /**
     * 过滤标记
     */
    private String reasonTags;

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
