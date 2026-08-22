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
 * Apex AI 用户会话。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("apex_ai_conversation")
public class ApexAiConversation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID。
     */
    private Long userId;

    /**
     * 会话标题。
     */
    private String title;

    /**
     * 会话摘要。
     */
    private String summary;

    /**
     * 最近分析类型。
     */
    private String lastAnalysisType;

    /**
     * 消息数量。
     */
    private Integer messageCount;

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
