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
 * 数据同步日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("data_sync_log")
public class DataSyncLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 同步类型
     */
    private String syncType;

    /**
     * 数据来源
     */
    private String source;

    /**
     * 同步范围描述
     */
    private String scopeDesc;

    /**
     * 状态 SUCCESS/FAIL/PARTIAL
     */
    private String status;

    /**
     * 消息
     */
    private String message;

    /**
     * 数据获取时间
     */
    private LocalDateTime fetchedAt;

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
