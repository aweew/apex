package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.ai.AiChatProperties;
import com.awe.apex.quant.ai.KimiChatClient;
import com.awe.apex.quant.domain.dto.HotConfluenceItem;
import com.awe.apex.quant.domain.dto.HotOverviewResp;
import com.awe.apex.quant.domain.dto.MarketBriefingResp;
import com.awe.apex.quant.domain.dto.NewsPulseCardResp;
import com.awe.apex.quant.domain.dto.NewsPulseResp;
import com.awe.apex.quant.domain.dto.PreMarketEventImpactResp;
import com.awe.apex.quant.domain.entity.MarketHot;
import com.awe.apex.quant.domain.entity.MarketNews;
import com.awe.apex.quant.holding.NewsPulseHeuristics;
import com.awe.apex.quant.holding.PreMarketEventHeuristics;
import com.awe.apex.quant.mapper.MarketNewsMapper;
import com.awe.apex.quant.service.IHotService;
import com.awe.apex.quant.service.IMarketBriefingService;
import com.awe.apex.quant.service.INewsPulseService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 今日消息面：聚合资讯 + 热点 + 行情立场，Kimi 摘要（可降级）
 */
@Slf4j
@Service
public class NewsPulseServiceImpl implements INewsPulseService {

    private final AtomicReference<CachedSummary> summaryCache = new AtomicReference<>();

    @Resource
    private MarketNewsMapper marketNewsMapper;

    @Resource
    private IHotService hotService;

    @Resource
    private IMarketBriefingService marketBriefingService;

    @Resource
    private KimiChatClient kimiChatClient;

    @Resource
    private AiChatProperties aiChatProperties;

    /**
     * 构建今日消息面
     *
     * @param cardLimit   卡片数
     * @param forceLlm    强制刷新 LLM
     * @return 总览
     */
    @Override
    public NewsPulseResp pulse(Integer cardLimit, boolean forceLlm) {
        int limit = Objects.isNull(cardLimit) || cardLimit <= 0 ? 9 : Math.min(cardLimit, 18);
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();

        List<MarketNews> todayRows = marketNewsMapper.selectList(Wrappers.<MarketNews>lambdaQuery()
                .ge(MarketNews::getPublishedAt, dayStart)
                .orderByDesc(MarketNews::getPublishedAt)
                .orderByDesc(MarketNews::getId)
                .last("LIMIT 200"));

        // 今日不足则回退近 36 小时
        if (CollUtil.isEmpty(todayRows) || todayRows.size() < 3) {
            todayRows = marketNewsMapper.selectList(Wrappers.<MarketNews>lambdaQuery()
                    .ge(MarketNews::getPublishedAt, LocalDateTime.now().minusHours(36))
                    .orderByDesc(MarketNews::getPublishedAt)
                    .orderByDesc(MarketNews::getId)
                    .last("LIMIT 200"));
        }

        List<NewsPulseCardResp> scored = new ArrayList<>();
        for (MarketNews row : todayRows) {
            String sentiment = StringUtils.isBlank(row.getSentiment()) ? "中性" : row.getSentiment().trim();
            boolean yaowen = isYaowen(row.getSummary(), row.getContent());
            String summaryText = cleanSummary(row.getSummary(), row.getContent());
            int stars = NewsPulseHeuristics.estimateStars(
                    row.getTitle(), summaryText, sentiment, row.getSource(), yaowen);
            List<String> themes = NewsPulseHeuristics.extractThemes(row.getTitle(), summaryText);
            scored.add(NewsPulseCardResp.builder()
                    .id(row.getId())
                    .sentiment(sentiment)
                    .stars(stars)
                    .title(row.getTitle())
                    .summary(summaryText)
                    .themes(themes)
                    .relatedCodes(parseRelatedCodes(row.getRelatedCodes()))
                    .publishedAt(row.getPublishedAt())
                    .source(row.getSource())
                    .url(row.getUrl())
                    .yaowen(yaowen)
                    .build());
        }

        List<PreMarketEventImpactResp> eventImpacts = buildPreMarketEventImpacts(scored);
        List<NewsPulseCardResp> cards = new ArrayList<>();
        int visibleCardLimit = Math.min(limit, 3);
        for (PreMarketEventImpactResp impact : eventImpacts) {
            for (NewsPulseCardResp card : scored) {
                if (Objects.equals(impact.getTitle(), card.getTitle())
                        && Objects.equals(impact.getSource(), card.getSource())) {
                    cards.add(card);
                    break;
                }
            }
            if (cards.size() >= visibleCardLimit) {
                break;
            }
        }

        int bull = 0;
        int bear = 0;
        int neutral = 0;
        for (PreMarketEventImpactResp impact : eventImpacts) {
            if ("利好".equals(impact.getDirection())) {
                bull++;
            } else if ("利空".equals(impact.getDirection())) {
                bear++;
            } else {
                neutral++;
            }
        }

        MarketBriefingResp briefing = null;
        try {
            briefing = marketBriefingService.briefing(false);
        } catch (Exception ex) {
            log.warn("消息面拉取 briefing 失败: {}", ex.getMessage());
        }

        List<String> hotThemes = new ArrayList<>();
        try {
            HotOverviewResp hot = hotService.overview(30);
            if (Objects.nonNull(hot) && CollUtil.isNotEmpty(hot.getConfluence())) {
                for (HotConfluenceItem item : hot.getConfluence()) {
                    if (Objects.nonNull(item) && StringUtils.isNotBlank(item.getName())) {
                        hotThemes.add(item.getName());
                    }
                    if (hotThemes.size() >= 6) {
                        break;
                    }
                }
            }
            if (hotThemes.isEmpty() && Objects.nonNull(hot) && CollUtil.isNotEmpty(hot.getEastmoney())) {
                for (MarketHot item : hot.getEastmoney()) {
                    if (Objects.nonNull(item) && StringUtils.isNotBlank(item.getName())) {
                        hotThemes.add(item.getName());
                    }
                    if (hotThemes.size() >= 6) {
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("消息面拉取热点失败: {}", ex.getMessage());
        }
        if (CollUtil.isEmpty(hotThemes) && Objects.nonNull(briefing) && CollUtil.isNotEmpty(briefing.getHotThemes())) {
            hotThemes.addAll(briefing.getHotThemes().stream().limit(6).toList());
        }

        String marketStance = Objects.nonNull(briefing) ? briefing.getStance() : null;
        String effectHint = null;
        if (Objects.nonNull(briefing) && Objects.nonNull(briefing.getEffect())) {
            effectHint = briefing.getEffect().getHint();
        }
        String biasLabel = resolveBiasLabel(bull, bear);

        String summarySource = "rule";
        String executive = buildRuleSummary(eventImpacts);
        boolean llmOk = kimiChatClient.available();
        if (llmOk) {
            String llm = resolveLlmSummary(forceLlm, bull, bear, eventImpacts, biasLabel);
            if (StringUtils.isNotBlank(llm)
                    && llm.contains("首要变量：")
                    && llm.contains("A股映射：")
                    && llm.contains("开盘验证：")) {
                executive = llm;
                summarySource = "llm";
            }
        }

        return NewsPulseResp.builder()
                .bullCount(bull)
                .bearCount(bear)
                .neutralCount(neutral)
                .biasLabel(biasLabel)
                .marketStance(marketStance)
                .effectHint(effectHint)
                .executiveSummary(executive)
                .summarySource(summarySource)
                .hotThemes(hotThemes)
                .cards(new ArrayList<>(cards))
                .eventImpacts(eventImpacts)
                .message("今日消息面 · 利好 " + bull + " / 利空 " + bear
                        + (StringUtils.isNotBlank(biasLabel) ? " · " + biasLabel : ""))
                .summarizedAt(LocalDateTime.now())
                .llmConfigured(llmOk)
                .build();
    }

    private List<String> parseRelatedCodes(String relatedCodes) {
        List<String> codes = new ArrayList<>();
        if (StringUtils.isBlank(relatedCodes)) {
            return codes;
        }
        for (String code : relatedCodes.split("[,，\\s]+")) {
            if (StringUtils.isNotBlank(code)) {
                codes.add(code.trim());
            }
        }
        return codes;
    }

    private List<PreMarketEventImpactResp> buildPreMarketEventImpacts(List<NewsPulseCardResp> cards) {
        List<PreMarketEventImpactResp> impacts = new ArrayList<>();
        for (NewsPulseCardResp card : cards) {
            PreMarketEventImpactResp impact = PreMarketEventHeuristics.toImpact(card);
            if (PreMarketEventHeuristics.isRelevant(impact)) {
                impacts.add(impact);
            }
        }
        impacts.sort(Comparator
                .comparingInt(PreMarketEventHeuristics::relevanceScore)
                .reversed()
                .thenComparing(PreMarketEventImpactResp::getPublishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        List<PreMarketEventImpactResp> distinctImpacts = new ArrayList<>();
        Set<String> topicKeys = new HashSet<>();
        for (PreMarketEventImpactResp impact : impacts) {
            if (topicKeys.add(PreMarketEventHeuristics.topicKey(impact))) {
                distinctImpacts.add(impact);
            }
            if (distinctImpacts.size() >= 3) {
                break;
            }
        }
        return distinctImpacts;
    }

    private String resolveLlmSummary(boolean force,
                                     int bull,
                                     int bear,
                                     List<PreMarketEventImpactResp> impacts,
                                     String biasLabel) {
        String cacheKey = "direction-v2|" + LocalDate.now() + "|" + bull + "|" + bear + "|" + impacts.hashCode();
        CachedSummary cached = summaryCache.get();
        int ttl = Math.max(60, aiChatProperties.getSummaryCacheSeconds());
        if (!force && Objects.nonNull(cached)
                && cacheKey.equals(cached.key)
                && cached.at.plusSeconds(ttl).isAfter(LocalDateTime.now())
                && StringUtils.isNotBlank(cached.text)) {
            return cached.text;
        }

        StringBuilder lines = new StringBuilder();
        int i = 1;
        for (PreMarketEventImpactResp impact : impacts) {
            lines.append(i++).append(". [")
                    .append(impact.getDirection()).append("][重要度")
                    .append(impact.getPriority()).append("] ")
                    .append(impact.getTitle());
            if (CollUtil.isNotEmpty(impact.getRelatedCodes())) {
                lines.append(" | 代码:").append(String.join("/", impact.getRelatedCodes()));
            }
            if (CollUtil.isNotEmpty(impact.getThemes())) {
                lines.append(" | 题材:").append(String.join("/", impact.getThemes()));
            }
            lines.append(" | 传导:").append(impact.getImpactExplanation());
            lines.append('\n');
        }

        String system = "你是 A 股券商晨会编辑。只根据给定的高相关事件归纳，禁止补充列表之外的事件或数据。"
                + "必须输出一段中文（90-160字），严格使用格式："
                + "首要变量：…；A股映射：…；开盘验证：…。"
                + "A股映射要回答今天的市场风向，优先概括板块、行业、风格或风险偏好；"
                + "只有事件明确指向单家公司时，才把关联代码作为核验线索放在方向之后，禁止把代码列表当成核心结论或荐股。"
                + "首要变量只能写最重要事件，开盘验证必须写可观察条件。"
                + "不要引用新闻数量、市场立场或赚钱效应充当结论，不要使用 markdown。";
        String user = "统计：利好 " + bull + " 条，利空 " + bear + " 条，综合标签「"
                + (StringUtils.isBlank(biasLabel) ? "中性" : biasLabel) + "」。\n"
                + "高相关事件 Top 3：\n" + lines;

        String text = kimiChatClient.chat(system, user, 512);
        if (StringUtils.isNotBlank(text)) {
            summaryCache.set(new CachedSummary(cacheKey, text.trim(), LocalDateTime.now()));
            return text.trim();
        }
        return null;
    }

    private String buildRuleSummary(List<PreMarketEventImpactResp> impacts) {
        if (CollUtil.isEmpty(impacts)) {
            return "首要变量：暂无可验证的高相关事件；A股映射：当前没有明确指向板块或个股的新增线索；"
                    + "开盘验证：等待权威政策、公司公告或量价反馈。";
        }
        PreMarketEventImpactResp primaryImpact = impacts.get(0);
        List<String> themes = new ArrayList<>();
        List<String> relatedCodes = new ArrayList<>();
        for (PreMarketEventImpactResp impact : impacts) {
            if (CollUtil.isNotEmpty(impact.getThemes())) {
                for (String theme : impact.getThemes()) {
                    if (!themes.contains(theme) && themes.size() < 4) {
                        themes.add(theme);
                    }
                }
            }
            if (CollUtil.isNotEmpty(impact.getRelatedCodes())) {
                for (String code : impact.getRelatedCodes()) {
                    if (!relatedCodes.contains(code) && relatedCodes.size() < 4) {
                        relatedCodes.add(code);
                    }
                }
            }
        }
        String transmission;
        if (CollUtil.isNotEmpty(themes)) {
            transmission = "市场风向优先观察" + String.join("、", themes);
            if (CollUtil.isNotEmpty(relatedCodes)) {
                transmission += "，相关个股仅作核验：" + String.join("、", relatedCodes);
            }
        } else if (CollUtil.isNotEmpty(relatedCodes)) {
            transmission = "当前以公司事件为主，先核验" + String.join("、", relatedCodes) + "公告对市场的实际影响";
        } else {
            transmission = "先看大盘风险偏好与政策受益方向";
        }
        String primaryTitle = primaryImpact.getTitle();
        if (primaryTitle.length() > 52) {
            primaryTitle = primaryTitle.substring(0, 52) + "…";
        }
        String openingValidation;
        if ("EARNINGS".equals(primaryImpact.getEventType())) {
            openingValidation = "对照业绩预期与指引，观察关联标的竞价和成交反馈";
        } else if ("POLICY".equals(primaryImpact.getEventType())) {
            openingValidation = "核对正式文件，并观察受益方向竞价强度与量能";
        } else if ("ANNOUNCEMENT".equals(primaryImpact.getEventType())) {
            openingValidation = "核对公告规模、条件和落地进度，再看关联个股竞价";
        } else {
            openingValidation = "先核验权威来源与持续性，再观察相关资产价格响应";
        }
        return "首要变量：" + primaryTitle + "；A股映射：" + transmission
                + "；开盘验证：" + openingValidation + "。";
    }

    private String resolveBiasLabel(int bull, int bear) {
        if (bull > bear) {
            return "消息偏多";
        }
        if (bear > bull) {
            return "消息偏空";
        }
        return "中性";
    }

    private boolean isYaowen(String summary, String content) {
        String text = (summary == null ? "" : summary) + (content == null ? "" : content);
        return text.contains("【要闻】");
    }

    private String cleanSummary(String summary, String content) {
        String text = StringUtils.isNotBlank(summary) ? summary : content;
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String cleaned = text.replace("【要闻】", "").trim();
        return cleaned.length() > 160 ? cleaned.substring(0, 160) + "…" : cleaned;
    }

    private record CachedSummary(String key, String text, LocalDateTime at) {
    }
}
