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
import java.util.List;
import java.util.Objects;
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

        int bull = 0;
        int bear = 0;
        int neutral = 0;
        List<NewsPulseCardResp> scored = new ArrayList<>();
        for (MarketNews row : todayRows) {
            String sentiment = StringUtils.isBlank(row.getSentiment()) ? "中性" : row.getSentiment().trim();
            if ("利好".equals(sentiment)) {
                bull++;
            } else if ("利空".equals(sentiment)) {
                bear++;
            } else {
                neutral++;
            }
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

        scored.sort(Comparator
                .comparing((NewsPulseCardResp c) -> Objects.isNull(c.getStars()) ? 0 : c.getStars())
                .reversed()
                .thenComparing(NewsPulseCardResp::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        List<NewsPulseCardResp> cards = scored.size() > limit ? scored.subList(0, limit) : scored;
        List<PreMarketEventImpactResp> eventImpacts = buildPreMarketEventImpacts(scored);

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
        String biasLabel = resolveBiasLabel(bull, bear, marketStance, effectHint);

        String summarySource = "rule";
        String executive = buildRuleSummary(bull, bear, cards, hotThemes, biasLabel, marketStance, effectHint);
        boolean llmOk = kimiChatClient.available();
        if (llmOk) {
            String llm = resolveLlmSummary(forceLlm, bull, bear, cards, hotThemes, biasLabel, marketStance, effectHint);
            if (StringUtils.isNotBlank(llm)) {
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
            if (isUsefulEvent(impact)) {
                impacts.add(impact);
            }
        }
        impacts.sort(Comparator
                .comparing(PreMarketEventImpactResp::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PreMarketEventImpactResp::getPublishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        if (impacts.size() > 5) {
            return new ArrayList<>(impacts.subList(0, 5));
        }
        return impacts;
    }

    private boolean isUsefulEvent(PreMarketEventImpactResp impact) {
        if (Objects.isNull(impact) || StringUtils.isBlank(impact.getTitle())) {
            return false;
        }
        if ("ANNOUNCEMENT".equals(impact.getEventType())) {
            return CollUtil.isNotEmpty(impact.getRelatedCodes());
        }
        if (CollUtil.isNotEmpty(impact.getRelatedCodes()) || CollUtil.isNotEmpty(impact.getThemes())) {
            return true;
        }
        return "MARKET".equals(impact.getImpactScope())
                && ("POLICY".equals(impact.getEventType()) || "EMERGENCY".equals(impact.getEventType()))
                && Objects.nonNull(impact.getPriority())
                && impact.getPriority() >= 4;
    }

    private String resolveLlmSummary(boolean force,
                                     int bull,
                                     int bear,
                                     List<NewsPulseCardResp> cards,
                                     List<String> hotThemes,
                                     String biasLabel,
                                     String marketStance,
                                     String effectHint) {
        String cacheKey = LocalDate.now() + "|" + bull + "|" + bear + "|" + cards.size()
                + "|" + (cards.isEmpty() ? "" : cards.get(0).getId());
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
        for (NewsPulseCardResp card : cards) {
            lines.append(i++).append(". [")
                    .append(card.getSentiment()).append("][★").append(card.getStars()).append("] ")
                    .append(card.getTitle());
            if (CollUtil.isNotEmpty(card.getThemes())) {
                lines.append(" | 题材:").append(String.join("/", card.getThemes()));
            }
            lines.append('\n');
        }

        String system = "你是 A 股盘前消息面分析助手。只根据给定新闻列表归纳，禁止编造未出现的事件或数据。"
                + "输出一段中文（80-140字），格式："
                + "利好方向：…；承压方向：…；整体…。"
                + "语气简洁专业，不要用 markdown。";
        String user = "统计：利好 " + bull + " 条，利空 " + bear + " 条，综合标签「"
                + (StringUtils.isBlank(biasLabel) ? "中性" : biasLabel) + "」。\n"
                + "市场立场：" + (StringUtils.isBlank(marketStance) ? "未知" : marketStance) + "\n"
                + "赚钱效应：" + (StringUtils.isBlank(effectHint) ? "未知" : effectHint) + "\n"
                + "热点主题：" + (CollUtil.isEmpty(hotThemes) ? "无" : String.join("、", hotThemes)) + "\n"
                + "新闻列表：\n" + lines;

        String text = kimiChatClient.chat(system, user, 512);
        if (StringUtils.isNotBlank(text)) {
            summaryCache.set(new CachedSummary(cacheKey, text.trim(), LocalDateTime.now()));
            return text.trim();
        }
        return null;
    }

    private String buildRuleSummary(int bull,
                                    int bear,
                                    List<NewsPulseCardResp> cards,
                                    List<String> hotThemes,
                                    String biasLabel,
                                    String marketStance,
                                    String effectHint) {
        List<String> bullThemes = new ArrayList<>();
        List<String> bearThemes = new ArrayList<>();
        for (NewsPulseCardResp card : cards) {
            if (CollUtil.isEmpty(card.getThemes())) {
                continue;
            }
            if ("利好".equals(card.getSentiment())) {
                for (String t : card.getThemes()) {
                    if (!bullThemes.contains(t) && bullThemes.size() < 4) {
                        bullThemes.add(t);
                    }
                }
            } else if ("利空".equals(card.getSentiment())) {
                for (String t : card.getThemes()) {
                    if (!bearThemes.contains(t) && bearThemes.size() < 3) {
                        bearThemes.add(t);
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("利好方向：");
        if (CollUtil.isNotEmpty(bullThemes)) {
            sb.append(String.join("+", bullThemes));
        } else if (CollUtil.isNotEmpty(hotThemes)) {
            sb.append(String.join("+", hotThemes.subList(0, Math.min(3, hotThemes.size()))));
        } else {
            sb.append(bull > 0 ? "外围/政策偏暖线索" : "暂无明显主线");
        }
        sb.append("；承压方向：");
        if (CollUtil.isNotEmpty(bearThemes)) {
            sb.append(String.join("、", bearThemes));
        } else {
            sb.append(bear > 0 ? "个别利空扰动" : "暂无显著利空");
        }
        sb.append("。整体");
        if (StringUtils.isNotBlank(biasLabel)) {
            sb.append(biasLabel);
        } else {
            sb.append("中性观望");
        }
        if (StringUtils.isNotBlank(marketStance)) {
            sb.append("，行情立场偏").append(marketStance);
        }
        if (StringUtils.isNotBlank(effectHint)) {
            sb.append("（").append(effectHint).append("）");
        }
        sb.append("。");
        return sb.toString();
    }

    private String resolveBiasLabel(int bull, int bear, String marketStance, String effectHint) {
        if (StringUtils.isNotBlank(effectHint)) {
            if (effectHint.contains("强") || effectHint.contains("偏多") || effectHint.contains("赚钱")) {
                return "涨稍多";
            }
            if (effectHint.contains("弱") || effectHint.contains("偏空") || effectHint.contains("亏钱")) {
                return "跌稍多";
            }
        }
        if ("进攻".equals(marketStance)) {
            return "涨稍多";
        }
        if ("防守".equals(marketStance)) {
            return "跌稍多";
        }
        int diff = bull - bear;
        if (diff >= 2) {
            return "涨稍多";
        }
        if (diff <= -2) {
            return "跌稍多";
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
