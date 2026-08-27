package com.awe.apex.quant.holding;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.NewsPulseCardResp;
import com.awe.apex.quant.domain.dto.PreMarketEventImpactResp;

import java.util.Objects;

/**
 * 盘前资讯事件分类与影响说明规则。
 */
public final class PreMarketEventHeuristics {

    private static final String[] POLICY_KEYWORDS = {"国务院", "证监会", "央行", "发改委", "工信部", "财政部",
            "商务部", "监管", "政策", "条例", "方案", "行动计划", "降准", "降息"};
    private static final String[] EMERGENCY_KEYWORDS = {"突发", "事故", "爆炸", "地震", "洪水", "台风", "战争",
            "冲突", "制裁", "断供", "停产", "召回"};
    private static final String[] EARNINGS_KEYWORDS = {"财报", "业绩预告", "业绩快报", "业绩指引", "年报", "半年报",
            "季报", "净利润", "营收", "同比增长", "同比下降", "亏损", "扭亏", "Earnings", "earnings"};
    private static final String[] GLOBAL_TECH_BELLWETHER_KEYWORDS = {"英伟达", "NVIDIA", "Nvidia", "台积电", "苹果",
            "微软", "谷歌", "特斯拉"};
    private static final String[] ANNOUNCEMENT_KEYWORDS = {"公告", "回购", "增持", "减持", "解禁", "重组", "并购",
            "合同", "中标", "立案", "处罚", "停牌"};

    private PreMarketEventHeuristics() {
    }

    /**
     * 将一条资讯转换为盘前事件影响；无明确事件属性时返回空。
     *
     * @param card 消息面卡片
     * @return 盘前事件影响
     */
    public static PreMarketEventImpactResp toImpact(NewsPulseCardResp card) {
        if (Objects.isNull(card)) {
            return null;
        }
        String title = StringUtils.isBlank(card.getTitle()) ? "" : card.getTitle();
        String summary = StringUtils.isBlank(card.getSummary()) ? "" : card.getSummary();
        String text = title + " " + summary;
        PreMarketEventTypeEnum eventType = resolveEventType(text);
        if (Objects.isNull(eventType)) {
            return null;
        }
        boolean globalTechEarnings = PreMarketEventTypeEnum.EARNINGS == eventType
                && containsAny(text, GLOBAL_TECH_BELLWETHER_KEYWORDS);
        PreMarketImpactScopeEnum impactScope = resolveImpactScope(card, eventType);
        boolean officialSource = isOfficialSource(card.getSource());
        String direction = resolveDirection(card.getSentiment());
        return PreMarketEventImpactResp.builder()
                .eventType(eventType.getCode())
                .eventTypeName(eventType.getDesc())
                .impactScope(impactScope.getCode())
                .impactScopeName(impactScope.getDesc())
                .direction(direction)
                .priority(resolvePriority(card, eventType, impactScope, officialSource, globalTechEarnings))
                .title(card.getTitle())
                .summary(card.getSummary())
                .relatedCodes(card.getRelatedCodes())
                .themes(card.getThemes())
                .impactExplanation(buildImpactExplanation(eventType, impactScope, globalTechEarnings))
                .source(card.getSource())
                .url(card.getUrl())
                .publishedAt(card.getPublishedAt())
                .officialSource(officialSource)
                .verificationStatus(officialSource ? "已核验" : "媒体报道待核验")
                .build();
    }

    private static PreMarketEventTypeEnum resolveEventType(String text) {
        if (containsAny(text, EARNINGS_KEYWORDS)) {
            return PreMarketEventTypeEnum.EARNINGS;
        }
        if (containsAny(text, EMERGENCY_KEYWORDS)) {
            return PreMarketEventTypeEnum.EMERGENCY;
        }
        if (containsAny(text, POLICY_KEYWORDS)) {
            return PreMarketEventTypeEnum.POLICY;
        }
        if (containsAny(text, ANNOUNCEMENT_KEYWORDS)) {
            return PreMarketEventTypeEnum.ANNOUNCEMENT;
        }
        return null;
    }

    private static PreMarketImpactScopeEnum resolveImpactScope(NewsPulseCardResp card,
                                                                 PreMarketEventTypeEnum eventType) {
        if (CollUtil.isNotEmpty(card.getRelatedCodes())) {
            return PreMarketImpactScopeEnum.STOCK;
        }
        if (CollUtil.isNotEmpty(card.getThemes())) {
            return PreMarketImpactScopeEnum.THEME;
        }
        if (PreMarketEventTypeEnum.POLICY == eventType || PreMarketEventTypeEnum.EMERGENCY == eventType) {
            return PreMarketImpactScopeEnum.MARKET;
        }
        return PreMarketImpactScopeEnum.THEME;
    }

    private static String resolveDirection(String sentiment) {
        if ("利好".equals(sentiment) || "利空".equals(sentiment)) {
            return sentiment;
        }
        return "待验证";
    }

    private static int resolvePriority(NewsPulseCardResp card, PreMarketEventTypeEnum eventType,
                                       PreMarketImpactScopeEnum impactScope, boolean officialSource,
                                       boolean globalTechEarnings) {
        int priority = Objects.nonNull(card.getStars()) ? card.getStars() : 2;
        if (PreMarketImpactScopeEnum.STOCK == impactScope) {
            priority++;
        }
        if (PreMarketEventTypeEnum.EMERGENCY == eventType || PreMarketEventTypeEnum.POLICY == eventType) {
            priority++;
        }
        if (officialSource) {
            priority++;
        }
        if (globalTechEarnings) {
            priority += 2;
        }
        return Math.min(priority, 5);
    }

    private static String buildImpactExplanation(PreMarketEventTypeEnum eventType,
                                                  PreMarketImpactScopeEnum impactScope,
                                                  boolean globalTechEarnings) {
        if (PreMarketEventTypeEnum.POLICY == eventType) {
            return "政策与监管变化先影响" + impactScope.getDesc() + "预期，需以正式文件内容和落地节奏为准。";
        }
        if (PreMarketEventTypeEnum.EARNINGS == eventType) {
            if (globalTechEarnings) {
                return "全球科技龙头财报会重定价全球 AI 风险偏好，A 股算力、光模块和半导体先看业绩指引与盘后价格反应。";
            }
            return "业绩信息优先影响关联个股；没有市场预期对照时，超预期程度仍待验证。";
        }
        if (PreMarketEventTypeEnum.EMERGENCY == eventType) {
            return "突发事件可能扰动" + impactScope.getDesc() + "，需等待权威来源确认及持续性评估。";
        }
        return "公司公告优先影响关联个股，需阅读原文确认交易规模、条件和落地进度。";
    }

    private static boolean containsAny(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOfficialSource(String source) {
        if (StringUtils.isBlank(source)) {
            return false;
        }
        String normalizedSource = source.trim().toLowerCase();
        return "sse".equals(normalizedSource) || "szse".equals(normalizedSource)
                || "bjse".equals(normalizedSource) || "cninfo".equals(normalizedSource)
                || "company".equals(normalizedSource);
    }
}
