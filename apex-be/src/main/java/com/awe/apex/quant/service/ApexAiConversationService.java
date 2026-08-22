package com.awe.apex.quant.service;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.ai.KimiChatMessage;
import com.awe.apex.quant.context.ApexUserContext;
import com.awe.apex.quant.domain.dto.ApexAiAnalysisResp;
import com.awe.apex.quant.domain.dto.ApexAiAnalyzeReq;
import com.awe.apex.quant.domain.dto.ApexAiConversationMessageResp;
import com.awe.apex.quant.domain.dto.ApexAiConversationResp;
import com.awe.apex.quant.domain.entity.ApexAiConversation;
import com.awe.apex.quant.domain.entity.ApexAiMessage;
import com.awe.apex.quant.mapper.ApexAiConversationMapper;
import com.awe.apex.quant.mapper.ApexAiMessageMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Apex AI 用户会话服务。
 */
@Service
public class ApexAiConversationService {

    private static final int RESTORE_MESSAGE_LIMIT = 50;

    @Resource
    private ApexAiConversationMapper conversationMapper;

    @Resource
    private ApexAiMessageMapper messageMapper;

    @Resource
    private ApexUserContext userContext;

    /**
     * 创建新会话或校验指定会话归属。
     *
     * @param conversationId 会话ID，为空时创建新会话
     * @param question       首个问题
     * @return 可用会话ID
     */
    public Long openConversation(Long conversationId, String question) {
        Long currentUserId = userContext.currentUserId();
        if (Objects.nonNull(conversationId)) {
            ApexAiConversation conversation = conversationMapper.selectOwnedConversation(conversationId, currentUserId);
            if (Objects.isNull(conversation)) {
                throw new BusinessException("会话不存在或无权访问");
            }
            return conversation.getId();
        }
        String normalizedQuestion = StringUtils.isNotBlank(question) ? question.trim() : "新对话";
        String title = normalizedQuestion.length() > 40
                ? normalizedQuestion.substring(0, 40) : normalizedQuestion;
        ApexAiConversation conversation = ApexAiConversation.builder()
                .userId(currentUserId)
                .title(title)
                .messageCount(0)
                .deleted(0)
                .build();
        conversationMapper.insert(conversation);
        return conversation.getId();
    }

    /**
     * 保存一轮用户问题与规则分析。
     *
     * @param conversationId 会话ID
     * @param request        分析请求
     * @param analysis       规则分析
     * @param latencyMs      规则分析耗时毫秒
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveAnalysis(Long conversationId, ApexAiAnalyzeReq request,
                             ApexAiAnalysisResp analysis, Long latencyMs) {
        Long currentUserId = userContext.currentUserId();
        messageMapper.insertUserMessage(conversationId, currentUserId, request, analysis.getRequestId());
        messageMapper.insertAssistantMessage(conversationId, currentUserId, analysis, latencyMs);
        conversationMapper.touchConversation(conversationId, currentUserId, analysis.getAnalysisType(),
                analysis.getSummary(), 2);
    }

    /**
     * 读取指定请求的结构化规则分析。
     *
     * @param conversationId 会话ID
     * @param requestId      请求编号
     * @return 结构化分析
     */
    public ApexAiAnalysisResp loadAnalysis(Long conversationId, String requestId) {
        Long currentUserId = userContext.currentUserId();
        if (Objects.isNull(conversationMapper.selectOwnedConversation(conversationId, currentUserId))) {
            throw new BusinessException("会话不存在或无权访问");
        }
        ApexAiMessage assistantMessage = messageMapper.selectAssistantMessage(
                conversationId, currentUserId, requestId);
        if (Objects.isNull(assistantMessage) || StringUtils.isBlank(assistantMessage.getAnalysisJson())) {
            throw new BusinessException("分析结果不存在或已失效");
        }
        return JsonUtils.parseObject(assistantMessage.getAnalysisJson(), ApexAiAnalysisResp.class);
    }

    /**
     * 保存 AI 增强结果。
     *
     * @param analysis  增强分析
     * @param latencyMs 增强耗时毫秒
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveEnhancement(ApexAiAnalysisResp analysis, Long latencyMs) {
        Long currentUserId = userContext.currentUserId();
        int updated = messageMapper.updateEnhancement(currentUserId, analysis, latencyMs);
        if (updated == 0) {
            throw new BusinessException("分析结果不存在或无权更新");
        }
        conversationMapper.touchConversation(analysis.getConversationId(), currentUserId,
                analysis.getAnalysisType(), analysis.getSummary(), 0);
    }

    /**
     * 查询真实角色的最近会话历史。
     *
     * @param conversationId 会话ID
     * @param limit          最大消息数
     * @return Kimi 角色消息
     */
    public List<KimiChatMessage> history(Long conversationId, int limit) {
        Long currentUserId = userContext.currentUserId();
        int normalizedLimit = Math.max(2, Math.min(limit, 20));
        List<ApexAiMessage> messages = messageMapper.selectRecentMessages(
                conversationId, currentUserId, normalizedLimit);
        List<KimiChatMessage> history = new ArrayList<>();
        if (CollUtil.isEmpty(messages)) {
            return history;
        }
        for (ApexAiMessage message : messages) {
            if (StringUtils.isBlank(message.getRole()) || StringUtils.isBlank(message.getContent())) {
                continue;
            }
            history.add(KimiChatMessage.builder()
                    .role(message.getRole().toLowerCase(Locale.ROOT))
                    .content(message.getContent())
                    .build());
        }
        return history;
    }

    /**
     * 恢复当前用户最近一次会话。
     *
     * @return 最近会话；没有历史时返回空会话
     */
    public ApexAiConversationResp latest() {
        Long currentUserId = userContext.currentUserId();
        ApexAiConversation conversation = conversationMapper.selectLatestConversation(currentUserId);
        if (Objects.isNull(conversation)) {
            return ApexAiConversationResp.builder().messages(List.of()).build();
        }
        List<ApexAiMessage> messageRows = messageMapper.selectRecentMessages(
                conversation.getId(), currentUserId, RESTORE_MESSAGE_LIMIT);
        List<ApexAiConversationMessageResp> messages = new ArrayList<>();
        if (CollUtil.isNotEmpty(messageRows)) {
            for (ApexAiMessage messageRow : messageRows) {
                ApexAiAnalysisResp analysis = StringUtils.isNotBlank(messageRow.getAnalysisJson())
                        ? JsonUtils.parseObject(messageRow.getAnalysisJson(), ApexAiAnalysisResp.class) : null;
                messages.add(ApexAiConversationMessageResp.builder()
                        .id(messageRow.getId())
                        .role(messageRow.getRole())
                        .content(messageRow.getContent())
                        .analysisType(messageRow.getAnalysisType())
                        .portfolioId(messageRow.getPortfolioId())
                        .strategyId(messageRow.getStrategyId())
                        .requestId(messageRow.getRequestId())
                        .analysis(analysis)
                        .createTime(messageRow.getCreateTime())
                        .build());
            }
        }
        return ApexAiConversationResp.builder()
                .conversationId(conversation.getId())
                .title(conversation.getTitle())
                .lastAnalysisType(conversation.getLastAnalysisType())
                .messages(messages)
                .build();
    }
}
