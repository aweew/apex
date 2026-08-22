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
 * Apex AI 会话消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("apex_ai_message")
public class ApexAiMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话ID。
     */
    private Long conversationId;

    /**
     * 所属用户ID。
     */
    private Long userId;

    /**
     * 消息角色。
     */
    private String role;

    /**
     * 消息文本内容。
     */
    private String content;

    /**
     * 分析类型。
     */
    private String analysisType;

    /**
     * 关联组合ID。
     */
    private Long portfolioId;

    /**
     * 关联策略ID。
     */
    private String strategyId;

    /**
     * 分析请求编号。
     */
    private String requestId;

    /**
     * 结构化分析结果JSON。
     */
    private String analysisJson;

    /**
     * 是否经过大模型增强。
     */
    private Boolean aiEnhanced;

    /**
     * 本阶段处理耗时毫秒。
     */
    private Long latencyMs;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记。
     */
    @TableLogic
    private Integer deleted;
}
