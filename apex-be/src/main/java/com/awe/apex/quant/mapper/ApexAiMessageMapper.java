package com.awe.apex.quant.mapper;

import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.quant.domain.dto.ApexAiAnalysisResp;
import com.awe.apex.quant.domain.dto.ApexAiAnalyzeReq;
import com.awe.apex.quant.domain.entity.ApexAiMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Apex AI 消息 Mapper。
 */
@Mapper
public interface ApexAiMessageMapper extends BaseMapper<ApexAiMessage> {

    /**
     * 保存用户问题。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param request        分析请求
     * @param requestId      请求编号
     * @return 插入行数
     */
    @Insert("""
            INSERT INTO apex_ai_message (
                conversation_id, user_id, role, content, analysis_type,
                portfolio_id, strategy_id, request_id, ai_enhanced
            ) VALUES (
                #{conversationId}, #{userId}, 'USER', #{request.question}, #{request.analysisType},
                #{request.portfolioId}, #{request.strategyId}, #{requestId}, 0
            )
            """)
    int insertUserMessage(@Param("conversationId") Long conversationId,
                          @Param("userId") Long userId,
                          @Param("request") ApexAiAnalyzeReq request,
                          @Param("requestId") String requestId);

    /**
     * 保存结构化规则分析。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param analysis       规则分析
     * @param latencyMs      处理耗时毫秒
     * @return 插入行数
     */
    default int insertAssistantMessage(Long conversationId, Long userId,
                                       ApexAiAnalysisResp analysis, Long latencyMs) {
        return insertAssistantMessageRow(conversationId, userId, analysis.getSummary(),
                analysis.getAnalysisType(), analysis.getPortfolioId(), analysis.getStrategyId(),
                analysis.getRequestId(), JsonUtils.toJsonString(analysis),
                Boolean.TRUE.equals(analysis.getAiEnhanced()), latencyMs);
    }

    /**
     * 写入助手消息行。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param content        消息文本
     * @param analysisType   分析类型
     * @param portfolioId    组合ID
     * @param strategyId     策略ID
     * @param requestId      请求编号
     * @param analysisJson   分析JSON
     * @param aiEnhanced     是否AI增强
     * @param latencyMs      耗时毫秒
     * @return 插入行数
     */
    @Insert("""
            INSERT INTO apex_ai_message (
                conversation_id, user_id, role, content, analysis_type, portfolio_id,
                strategy_id, request_id, analysis_json, ai_enhanced, latency_ms
            ) VALUES (
                #{conversationId}, #{userId}, 'ASSISTANT', #{content}, #{analysisType}, #{portfolioId},
                #{strategyId}, #{requestId}, #{analysisJson}, #{aiEnhanced}, #{latencyMs}
            )
            """)
    int insertAssistantMessageRow(@Param("conversationId") Long conversationId,
                                  @Param("userId") Long userId,
                                  @Param("content") String content,
                                  @Param("analysisType") String analysisType,
                                  @Param("portfolioId") Long portfolioId,
                                  @Param("strategyId") String strategyId,
                                  @Param("requestId") String requestId,
                                  @Param("analysisJson") String analysisJson,
                                  @Param("aiEnhanced") boolean aiEnhanced,
                                  @Param("latencyMs") Long latencyMs);

    /**
     * 查询最近消息并恢复为时间正序。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param limit          最大消息数
     * @return 消息列表
     */
    @Select("""
            SELECT t1.id,
                   t1.conversation_id,
                   t1.user_id,
                   t1.role,
                   t1.content,
                   t1.analysis_type,
                   t1.portfolio_id,
                   t1.strategy_id,
                   t1.request_id,
                   t1.analysis_json,
                   t1.ai_enhanced,
                   t1.latency_ms,
                   t1.create_time,
                   t1.update_time,
                   t1.deleted
            FROM (
                SELECT t2.*
                FROM apex_ai_message t2
                WHERE t2.conversation_id = #{conversationId}
                  AND t2.user_id = #{userId}
                  AND t2.deleted = 0
                ORDER BY t2.create_time DESC, t2.id DESC
                LIMIT #{limit}
            ) t1
            ORDER BY t1.create_time ASC, t1.id ASC
            """)
    List<ApexAiMessage> selectRecentMessages(@Param("conversationId") Long conversationId,
                                              @Param("userId") Long userId,
                                              @Param("limit") int limit);

    /**
     * 查询指定请求的助手分析。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param requestId      请求编号
     * @return 助手消息
     */
    @Select("""
            SELECT t1.id,
                   t1.conversation_id,
                   t1.user_id,
                   t1.role,
                   t1.content,
                   t1.analysis_type,
                   t1.portfolio_id,
                   t1.strategy_id,
                   t1.request_id,
                   t1.analysis_json,
                   t1.ai_enhanced,
                   t1.latency_ms,
                   t1.create_time,
                   t1.update_time,
                   t1.deleted
            FROM apex_ai_message t1
            WHERE t1.conversation_id = #{conversationId}
              AND t1.user_id = #{userId}
              AND t1.request_id = #{requestId}
              AND t1.role = 'ASSISTANT'
              AND t1.deleted = 0
            LIMIT 1
            """)
    ApexAiMessage selectAssistantMessage(@Param("conversationId") Long conversationId,
                                          @Param("userId") Long userId,
                                          @Param("requestId") String requestId);

    /**
     * 保存 AI 增强后的摘要和结构化结果。
     *
     * @param userId    用户ID
     * @param analysis  增强结果
     * @param latencyMs 增强耗时毫秒
     * @return 更新行数
     */
    default int updateEnhancement(Long userId, ApexAiAnalysisResp analysis, Long latencyMs) {
        return updateEnhancementRow(analysis.getConversationId(), userId, analysis.getRequestId(),
                analysis.getSummary(), JsonUtils.toJsonString(analysis), latencyMs);
    }

    /**
     * 更新 AI 增强消息行。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param requestId      请求编号
     * @param content        增强摘要
     * @param analysisJson   增强分析JSON
     * @param latencyMs      增强耗时毫秒
     * @return 更新行数
     */
    @Update("""
            UPDATE apex_ai_message t1
            SET t1.content = #{content},
                t1.analysis_json = #{analysisJson},
                t1.ai_enhanced = 1,
                t1.latency_ms = #{latencyMs},
                t1.update_time = CURRENT_TIMESTAMP
            WHERE t1.conversation_id = #{conversationId}
              AND t1.user_id = #{userId}
              AND t1.request_id = #{requestId}
              AND t1.role = 'ASSISTANT'
              AND t1.deleted = 0
            """)
    int updateEnhancementRow(@Param("conversationId") Long conversationId,
                             @Param("userId") Long userId,
                             @Param("requestId") String requestId,
                             @Param("content") String content,
                             @Param("analysisJson") String analysisJson,
                             @Param("latencyMs") Long latencyMs);
}
