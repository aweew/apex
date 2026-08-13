package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Bot 工具调用审计记录。
 */
@Data
@TableName("bot_call_audit")
public class BotCallAudit {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 请求号 */
    private String requestId;

    /** 工具名 */
    private String operation;

    /** 微信用户 */
    private String userId;

    /** 微信会话 */
    private String conversationId;

    /** 结果级别 */
    private String dataLevel;

    /** 错误原因 */
    private String errorMessage;

    /** 耗时毫秒 */
    private Long durationMs;

    /** 创建时间 */
    private LocalDateTime createTime;
}
