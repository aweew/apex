package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Bot 待确认写操作。
 */
@Data
@TableName("bot_pending_operation")
public class BotPendingOperation {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作类型 */
    private String operationType;

    /** 组合ID */
    private Long portfolioId;

    /** 微信用户 */
    private String userId;

    /** 微信会话 */
    private String conversationId;

    /** 一次性确认码 */
    private String confirmationCode;

    /** 校验后的持仓JSON */
    private String payloadJson;

    /** PENDING / CONFIRMED / EXPIRED */
    private String status;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 确认时间 */
    private LocalDateTime confirmTime;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
