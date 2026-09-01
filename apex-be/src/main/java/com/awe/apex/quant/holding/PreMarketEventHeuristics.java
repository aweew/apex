package com.awe.apex.quant.holding;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.NewsPulseCardResp;
import com.awe.apex.quant.domain.dto.PreMarketEventImpactResp;

import java.util.Locale;
import java.util.Objects;

/**
 * 盘前资讯事件分类与影响说明规则。
 */
public final class PreMarketEventHeuristics {

    private static final String[] POLICY_KEYWORDS = {"国务院", "证监会", "央行", "发改委", "工信部", "财政部",
            "商务部", "监管", "政策", "条例", "方案", "行动计划", "降准", "降息"};
    private static final String[] EMERGENCY_KEYWORDS = {"突发", "事故", "爆炸", "地震", "洪水", "台风", "战争",
            "冲突", "制裁", "断供", "停产", "召回"};
    private static final String[] WEATHER_DISASTER_KEYWORDS = {"地震", "洪水", "台风", "暴雨", "暴雪", "高温", "寒潮"};
    private static final String[] EARNINGS_KEYWORDS = {"财报", "业绩预告", "业绩快报", "业绩指引", "年报", "半年报",
            "季报", "净利润", "营收", "同比增长", "同比下降", "亏损", "扭亏", "Earnings", "earnings"};
    private static final String[] GLOBAL_TECH_BELLWETHER_KEYWORDS = {"英伟达", "NVIDIA", "Nvidia", "台积电", "苹果",
            "微软", "谷歌", "特斯拉"};
    private static final String[] ANNOUNCEMENT_KEYWORDS = {"公告", "回购", "增持", "减持", "解禁", "重组", "并购",
            "合同", "中标", "立案", "处罚", "停牌"};
    private static final String[] COMMENTARY_KEYWORDS = {"分析师解读", "经济学家评论", "机构观点", "市场观点",
            "点评", "解读", "评论", "认为", "展望"};
    private static final String[] DIRECT_ACTION_KEYWORDS = {"发布", "印发", "公布", "通过", "批准", "实施", "启动",
            "出台", "签署", "加征", "暂停", "上调", "下调", "降准", "降息", "回购", "增持", "减持", "中标",
            "财报", "业绩预告", "业绩快报", "业绩指引", "公告"};
    private static final String[] NATIONAL_POLICY_KEYWORDS = {"国务院", "中央", "全国人大", "证监会", "央行",
            "发改委", "工信部", "财政部", "商务部"};
    private static final String[] FOREIGN_POLICY_KEYWORDS = {"美国财长", "日本", "日元", "日本央行", "欧洲央行",
            "欧盟", "英国央行", "韩国央行", "印度央行"};
    private static final String[] CHINA_MARKET_LINK_KEYWORDS = {"A股", "中国资产", "人民币", "港股", "中概",
            "沪深", "富时A50", "富时 A50"};
    private static final String[] GLOBAL_SYSTEMIC_KEYWORDS = {"美联储", "FOMC"};
    private static final String[][] TOPIC_RULES = {
            {"fed", "美联储", "FOMC", "鲍威尔", "沃什"},
            {"china-monetary", "中国人民银行", "央行", "LPR", "降准", "降息"},
            {"capital-market", "证监会", "资本市场", "交易制度"},
            {"trade", "中美", "关税", "贸易摩擦", "出口管制"},
            {"geopolitics", "战争", "冲突", "制裁", "停火"}
    };

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
        if (containsAny(text, WEATHER_DISASTER_KEYWORDS)
                && CollUtil.isEmpty(card.getRelatedCodes()) && CollUtil.isEmpty(card.getThemes())) {
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

    /**
     * 判断事件是否具有可解释的 A 股传导关系。
     *
     * @param impact 盘前事件影响
     * @return 是否进入消息面候选
     */
    public static boolean isRelevant(PreMarketEventImpactResp impact) {
        if (Objects.isNull(impact) || StringUtils.isBlank(impact.getTitle())) {
            return false;
        }
        String text = impact.getTitle() + " "
                + (StringUtils.isBlank(impact.getSummary()) ? "" : impact.getSummary());
        boolean hasTarget = CollUtil.isNotEmpty(impact.getRelatedCodes()) || CollUtil.isNotEmpty(impact.getThemes());
        if (containsAny(text, COMMENTARY_KEYWORDS) && !containsAny(text, DIRECT_ACTION_KEYWORDS)
                && CollUtil.isEmpty(impact.getRelatedCodes())) {
            return false;
        }
        if ("POLICY".equals(impact.getEventType())
                && containsAny(text, FOREIGN_POLICY_KEYWORDS)
                && !containsAny(text, CHINA_MARKET_LINK_KEYWORDS)
                && !(containsAny(text, GLOBAL_SYSTEMIC_KEYWORDS) && containsAny(text, DIRECT_ACTION_KEYWORDS))) {
            return false;
        }
        if ("ANNOUNCEMENT".equals(impact.getEventType())) {
            return CollUtil.isNotEmpty(impact.getRelatedCodes());
        }
        if ("EARNINGS".equals(impact.getEventType())) {
            return hasTarget || (StringUtils.isNotBlank(impact.getImpactExplanation())
                    && impact.getImpactExplanation().contains("全球 AI"));
        }
        if ("EMERGENCY".equals(impact.getEventType())) {
            return hasTarget;
        }
        if (hasTarget) {
            return true;
        }
        return "POLICY".equals(impact.getEventType())
                && "MARKET".equals(impact.getImpactScope())
                && containsAny(text, NATIONAL_POLICY_KEYWORDS)
                && containsAny(text, DIRECT_ACTION_KEYWORDS)
                && Objects.nonNull(impact.getPriority())
                && impact.getPriority() >= 4;
    }

    /**
     * 计算事件重要度和 A 股相关性的综合排序分。
     *
     * @param impact 盘前事件影响
     * @return 排序分，分值越高越靠前
     */
    public static int relevanceScore(PreMarketEventImpactResp impact) {
        if (Objects.isNull(impact)) {
            return 0;
        }
        int score = (Objects.isNull(impact.getPriority()) ? 0 : impact.getPriority()) * 100;
        if (CollUtil.isNotEmpty(impact.getRelatedCodes())) {
            score += 80;
        }
        if (CollUtil.isNotEmpty(impact.getThemes())) {
            score += 50;
        }
        if (impact.isOfficialSource()) {
            score += 40;
        }
        if ("POLICY".equals(impact.getEventType())) {
            score += 30;
        }
        String text = impact.getTitle() + " "
                + (StringUtils.isBlank(impact.getSummary()) ? "" : impact.getSummary());
        if (containsAny(text, DIRECT_ACTION_KEYWORDS)) {
            score += 20;
        }
        score += sourceWeight(impact.getSource());
        return score;
    }

    /**
     * 生成事件主题键，用于合并同一宏观变量的重复报道。
     *
     * @param impact 盘前事件影响
     * @return 主题键
     */
    public static String topicKey(PreMarketEventImpactResp impact) {
        if (Objects.isNull(impact)) {
            return "";
        }
        String eventType = StringUtils.isBlank(impact.getEventType()) ? "UNKNOWN" : impact.getEventType();
        if (CollUtil.isNotEmpty(impact.getRelatedCodes())) {
            return eventType + ":code:" + impact.getRelatedCodes().get(0);
        }
        if (CollUtil.isNotEmpty(impact.getThemes())) {
            return eventType + ":theme:" + impact.getThemes().get(0);
        }
        String text = (StringUtils.isBlank(impact.getTitle()) ? "" : impact.getTitle()) + " "
                + (StringUtils.isBlank(impact.getSummary()) ? "" : impact.getSummary());
        String upperText = text.toUpperCase(Locale.ROOT);
        for (String[] rule : TOPIC_RULES) {
            for (int i = 1; i < rule.length; i++) {
                if (upperText.contains(rule[i].toUpperCase(Locale.ROOT))) {
                    return eventType + ":topic:" + rule[0];
                }
            }
        }
        String normalizedTitle = (StringUtils.isBlank(impact.getTitle()) ? "" : impact.getTitle())
                .replaceAll("[^\\p{IsHan}A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
        return eventType + ":title:"
                + normalizedTitle.substring(0, Math.min(normalizedTitle.length(), 24));
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

    private static int sourceWeight(String source) {
        if (StringUtils.isBlank(source)) {
            return 0;
        }
        String normalizedSource = source.trim().toLowerCase(Locale.ROOT);
        if ("cctv".equals(normalizedSource) || "xinhua".equals(normalizedSource)
                || "gov".equals(normalizedSource)) {
            return 25;
        }
        if ("cls".equals(normalizedSource)) {
            return 15;
        }
        if ("eastmoney".equals(normalizedSource) || "sina".equals(normalizedSource)
                || "ths".equals(normalizedSource)) {
            return 5;
        }
        return 0;
    }
}
