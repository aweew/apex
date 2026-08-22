package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.ApexAiConversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Apex AI 会话 Mapper。
 */
@Mapper
public interface ApexAiConversationMapper extends BaseMapper<ApexAiConversation> {

    /**
     * 查询用户自己的指定会话。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 会话
     */
    @Select("""
            SELECT t1.id,
                   t1.user_id,
                   t1.title,
                   t1.summary,
                   t1.last_analysis_type,
                   t1.message_count,
                   t1.create_time,
                   t1.update_time,
                   t1.deleted
            FROM apex_ai_conversation t1
            WHERE t1.id = #{conversationId}
              AND t1.user_id = #{userId}
              AND t1.deleted = 0
            LIMIT 1
            """)
    ApexAiConversation selectOwnedConversation(@Param("conversationId") Long conversationId,
                                                @Param("userId") Long userId);

    /**
     * 查询用户最近更新的会话。
     *
     * @param userId 用户ID
     * @return 最近会话
     */
    @Select("""
            SELECT t1.id,
                   t1.user_id,
                   t1.title,
                   t1.summary,
                   t1.last_analysis_type,
                   t1.message_count,
                   t1.create_time,
                   t1.update_time,
                   t1.deleted
            FROM apex_ai_conversation t1
            WHERE t1.user_id = #{userId}
              AND t1.deleted = 0
            ORDER BY t1.update_time DESC, t1.id DESC
            LIMIT 1
            """)
    ApexAiConversation selectLatestConversation(@Param("userId") Long userId);

    /**
     * 更新会话摘要与消息计数。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param analysisType   分析类型
     * @param summary        最新摘要
     * @param messageDelta   新增消息数
     * @return 更新行数
     */
    @Update("""
            UPDATE apex_ai_conversation t1
            SET t1.last_analysis_type = #{analysisType},
                t1.summary = #{summary},
                t1.message_count = t1.message_count + #{messageDelta},
                t1.update_time = CURRENT_TIMESTAMP
            WHERE t1.id = #{conversationId}
              AND t1.user_id = #{userId}
              AND t1.deleted = 0
            """)
    int touchConversation(@Param("conversationId") Long conversationId,
                          @Param("userId") Long userId,
                          @Param("analysisType") String analysisType,
                          @Param("summary") String summary,
                          @Param("messageDelta") int messageDelta);
}
