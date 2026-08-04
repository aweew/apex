package com.awe.apex.quant.holding;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.ObserveTechSignal;
import com.awe.apex.quant.domain.dto.PortfolioBriefResp;
import com.awe.apex.quant.domain.dto.PortfolioTipItem;
import com.awe.apex.quant.domain.entity.PortfolioHolding;
import com.awe.apex.quant.market.MarketCodeUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 组合级简报聚合：只基于持仓已有字段，不做预测性表述
 */
public final class PortfolioBriefBuilder {

    private static final BigDecimal HEAVY_WEIGHT = new BigDecimal("20");
    private static final BigDecimal WATCH_WEIGHT = new BigDecimal("12");
    private static final BigDecimal THEME_HEAVY = new BigDecimal("45");
    private static final BigDecimal NEAR_STOP_PCT = new BigDecimal("0.03");

    private PortfolioBriefBuilder() {
    }

    /**
     * 根据已 enrich 的持仓生成组合简报
     *
     * @param holdings 持仓（含市值/权重/评价等）
     * @return 简报；空仓返回 null
     */
    public static PortfolioBriefResp build(List<PortfolioHolding> holdings) {
        if (CollUtil.isEmpty(holdings)) {
            return null;
        }
        BigDecimal totalMv = BigDecimal.ZERO;
        for (PortfolioHolding holding : holdings) {
            if (Objects.nonNull(holding.getMarketValue()) && holding.getMarketValue().signum() > 0) {
                totalMv = totalMv.add(holding.getMarketValue());
            }
        }

        List<PortfolioTipItem> actions = new ArrayList<>();
        List<PortfolioTipItem> risks = new ArrayList<>();
        List<String> watchPoints = new ArrayList<>();
        List<String> stopLabels = new ArrayList<>();

        int stopHit = 0;
        int takeHit = 0;
        int weakTech = 0;
        int richVal = 0;
        int dataThin = 0;
        BigDecimal etfMv = BigDecimal.ZERO;
        BigDecimal maxWeight = BigDecimal.ZERO;
        PortfolioHolding maxHolding = null;
        Map<String, BigDecimal> themeMv = new HashMap<>();

        List<PortfolioHolding> byWeight = new ArrayList<>(holdings);
        byWeight.sort(Comparator.comparing(
                (PortfolioHolding h) -> Objects.nonNull(h.getWeightPct()) ? h.getWeightPct() : BigDecimal.ZERO
        ).reversed());

        for (PortfolioHolding holding : holdings) {
            String code = MarketCodeUtils.normalizeHoldingCode(holding.getCode());
            String name = StringUtils.isNotBlank(holding.getName()) ? holding.getName() : code;
            String label = stockLabel(code, name);
            BigDecimal weight = Objects.nonNull(holding.getWeightPct()) ? holding.getWeightPct() : BigDecimal.ZERO;
            BigDecimal price = holding.getMarketPrice();
            String verdict = StringUtils.trim(holding.getVerdict());

            if (MarketCodeUtils.isFundOrEtf(code) && Objects.nonNull(holding.getMarketValue())) {
                etfMv = etfMv.add(holding.getMarketValue());
            }
            if (weight.compareTo(maxWeight) > 0) {
                maxWeight = weight;
                maxHolding = holding;
            }

            String theme = primaryTheme(holding);
            if (StringUtils.isNotBlank(theme) && Objects.nonNull(holding.getMarketValue())) {
                themeMv.merge(theme, holding.getMarketValue(), BigDecimal::add);
            }

            if ("止损卖出".equals(verdict)) {
                stopHit++;
                stopLabels.add(label + "（止损 " + money(holding.getStopLoss()) + "）");
            } else if ("止盈减仓".equals(verdict)) {
                takeHit++;
                actions.add(tip("warn", code, name,
                        label + "：现价触及止盈 "
                                + money(holding.getTakeProfit())
                                + "，建议至少减仓锁定利润，余仓上移止损至成本附近。"));
            } else if ("逢高减仓".equals(verdict)) {
                actions.add(tip("warn", code, name,
                        label + "：技术偏弱且估值偏贵，反弹优先减仓，不宜加仓摊薄。"));
            } else if ("谨慎持有".equals(verdict) && weight.compareTo(WATCH_WEIGHT) >= 0) {
                actions.add(tip("info", code, name,
                        label + " 仓位约 " + weight.stripTrailingZeros().toPlainString()
                                + "% 且评价谨慎，建议收紧止损、降低交易频率。"));
            }

            if ("数据不足".equals(verdict) || StringUtils.isNotBlank(holding.getTechSummary())
                    && holding.getTechSummary().contains("日线不足")) {
                dataThin++;
                watchPoints.add(label + "：日线不足，同步后再做技术/止损评估。");
            }

            int hit = techHit(holding);
            int total = techTotal(holding);
            if (total > 0 && hit < 3) {
                weakTech++;
            }
            if (isRich(holding.getValuationLevel())) {
                richVal++;
            }

            // 接近止损但未触发
            if (Objects.nonNull(price) && Objects.nonNull(holding.getStopLoss())
                    && holding.getStopLoss().signum() > 0
                    && price.compareTo(holding.getStopLoss()) > 0) {
                BigDecimal dist = price.subtract(holding.getStopLoss())
                        .divide(price, 4, RoundingMode.HALF_UP);
                if (dist.compareTo(NEAR_STOP_PCT) <= 0) {
                    watchPoints.add(label + "：距止损仅 "
                            + dist.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                            + "%，留意收盘是否失守。");
                }
            }

            if (weight.compareTo(HEAVY_WEIGHT) >= 0) {
                risks.add(tip("warn", code, name,
                        "单票集中度偏高：" + label + " 约占组合 "
                                + weight.stripTrailingZeros().toPlainString()
                                + "%，波动将显著放大组合回撤。"));
                if (!"止损卖出".equals(verdict) && !"止盈减仓".equals(verdict)) {
                    actions.add(tip("warn", code, name,
                            label + " 仓位过重，建议制定分批降权计划（事件前/大涨后优先），单票目标降至 15% 以内。"));
                }
            } else if (weight.compareTo(WATCH_WEIGHT) >= 0) {
                watchPoints.add(label + "：权重 "
                        + weight.stripTrailingZeros().toPlainString()
                        + "%，属核心仓，需盯估值与止损纪律。");
            }

            // 贵 + 弱 叠加（结构性风险，不与操作建议重复写「减仓」话术）
            if (isRich(holding.getValuationLevel()) && total > 0 && hit < 3
                    && !"止损卖出".equals(verdict) && !"逢高减仓".equals(verdict)
                    && !"止盈减仓".equals(verdict)) {
                risks.add(tip("warn", code, name,
                        "结构风险：" + label + " 估值偏贵且技术偏弱，赔率与胜率双弱。"));
            }
        }

        // 止损触线：操作建议列明细（含名称/止损价），风险预警只写组合级一条，避免左右重复
        if (CollUtil.isNotEmpty(stopLabels)) {
            String joined = String.join("、", stopLabels);
            if (stopLabels.size() == 1) {
                actions.add(0, tip("critical", null, null,
                        joined + "：现价已触及止损，建议优先离场并复盘触发原因，避免情绪化扛单。"));
            } else {
                actions.add(0, tip("critical", null, null,
                        "以下 " + stopLabels.size() + " 只已触及止损，建议优先离场并复盘："
                                + joined + "。"));
            }
            risks.add(0, tip("critical", null, null,
                    "止损纪律风险：已破线 " + stopLabels.size()
                            + " 只仍持有，继续扛单等于放弃风险预算；名单见左侧操作建议。"));
        }

        String topTheme = null;
        BigDecimal topThemePct = BigDecimal.ZERO;
        if (totalMv.signum() > 0 && !themeMv.isEmpty()) {
            Map.Entry<String, BigDecimal> top = null;
            for (Map.Entry<String, BigDecimal> e : themeMv.entrySet()) {
                if (Objects.isNull(top) || e.getValue().compareTo(top.getValue()) > 0) {
                    top = e;
                }
            }
            if (Objects.nonNull(top)) {
                topTheme = top.getKey();
                topThemePct = top.getValue().multiply(BigDecimal.valueOf(100))
                        .divide(totalMv, 1, RoundingMode.HALF_UP);
                if (topThemePct.compareTo(THEME_HEAVY) >= 0) {
                    risks.add(tip("warn", null, null,
                            "题材集中：" + topTheme + " 约占组合 "
                                    + topThemePct.toPlainString()
                                    + "%，板块回调时回撤同步放大，注意控制同主题加仓。"));
                    watchPoints.add("主题材「" + topTheme + "」占比偏高，关注板块成交与龙头分歧。");
                }
            }
        }

        BigDecimal etfPct = BigDecimal.ZERO;
        if (totalMv.signum() > 0) {
            etfPct = etfMv.multiply(BigDecimal.valueOf(100)).divide(totalMv, 1, RoundingMode.HALF_UP);
        }

        // 前三大权重
        BigDecimal top3 = BigDecimal.ZERO;
        int n = Math.min(3, byWeight.size());
        for (int i = 0; i < n; i++) {
            BigDecimal w = byWeight.get(i).getWeightPct();
            if (Objects.nonNull(w)) {
                top3 = top3.add(w);
            }
        }
        if (top3.compareTo(new BigDecimal("55")) >= 0) {
            risks.add(tip("warn", null, null,
                    "组合头部集中：前三大合计约 " + top3.setScale(1, RoundingMode.HALF_UP)
                            + "%，个股特异性风险主导净值波动。"));
        }

        if (dataThin > 0) {
            risks.add(tip("info", null, null,
                    "数据完备性：" + dataThin + " 只缺少足够日线，技术与止损评估可信度下降，建议先同步日线。"));
        }

        String stance = resolveStance(stopHit, weakTech, richVal, holdings.size(), maxWeight, topThemePct);
        String thesis = buildThesis(holdings.size(), topTheme, topThemePct, etfPct, maxHolding, maxWeight);
        String summary = buildSummary(holdings.size(), stance, topTheme, topThemePct,
                maxWeight, stopHit, takeHit, weakTech, richVal);

        // 通用操作建议（无紧急单票时也给结构建议）
        if (actions.isEmpty()) {
            actions.add(tip("info", null, null,
                    "暂无紧急触发项。维持止损纪律，避免同题材追高加仓；反弹优先处理偏贵偏弱仓位。"));
        }
        if (watchPoints.isEmpty()) {
            watchPoints.add("盯住主题材波动与指数强弱差；单票事件（业绩/减持）优先于题材叙事。");
            watchPoints.add("止损/止盈价位已写入，收盘价有效突破再执行，减少盘中噪音。");
        }

        // 去重并限长
        actions = limitTips(dedupeTips(actions), 8);
        risks = limitTips(dedupeTips(risks), 8);
        watchPoints = limitStrings(dedupeStrings(watchPoints), 8);

        return PortfolioBriefResp.builder()
                .stance(stance)
                .summary(summary)
                .thesis(thesis)
                .actions(actions)
                .watchPoints(watchPoints)
                .risks(risks)
                .maxWeightPct(maxWeight.setScale(1, RoundingMode.HALF_UP))
                .maxWeightCode(Objects.nonNull(maxHolding) ? maxHolding.getCode() : null)
                .topTheme(topTheme)
                .topThemePct(topThemePct)
                .etfPct(etfPct)
                .stopHitCount(stopHit)
                .takeHitCount(takeHit)
                .weakTechCount(weakTech)
                .richValCount(richVal)
                .build();
    }

    private static String resolveStance(int stopHit, int weakTech, int richVal, int n,
                                        BigDecimal maxWeight, BigDecimal topThemePct) {
        if (stopHit > 0 || maxWeight.compareTo(new BigDecimal("25")) >= 0
                || (n > 0 && weakTech * 2 >= n)) {
            return "防守";
        }
        if (richVal * 2 >= n || (Objects.nonNull(topThemePct) && topThemePct.compareTo(THEME_HEAVY) >= 0)) {
            return "均衡偏谨慎";
        }
        if (n > 0 && weakTech <= n / 4 && richVal <= n / 4) {
            return "偏进攻";
        }
        return "均衡";
    }

    private static String buildThesis(int n, String topTheme, BigDecimal topThemePct,
                                      BigDecimal etfPct, PortfolioHolding maxHolding, BigDecimal maxWeight) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前 ").append(n).append(" 只持仓");
        if (StringUtils.isNotBlank(topTheme) && Objects.nonNull(topThemePct) && topThemePct.signum() > 0) {
            sb.append("，主线偏向「").append(topTheme).append("」（约 ")
                    .append(topThemePct.toPlainString()).append("%）");
        } else {
            sb.append("，题材较分散或未命中核心标签");
        }
        if (Objects.nonNull(etfPct) && etfPct.compareTo(new BigDecimal("15")) >= 0) {
            sb.append("；ETF/基金底仓约 ").append(etfPct.toPlainString())
                    .append("%，偏工具化配置，个股 alpha 与板块 beta 需分开看");
        } else {
            sb.append("；权益以个股为主，净值弹性更大，更依赖个股止损纪律");
        }
        if (Objects.nonNull(maxHolding) && maxWeight.compareTo(WATCH_WEIGHT) >= 0) {
            String nm = StringUtils.isNotBlank(maxHolding.getName()) ? maxHolding.getName() : maxHolding.getCode();
            sb.append("。最重仓 ").append(maxHolding.getCode()).append(" ").append(nm)
                    .append("（约 ").append(maxWeight.setScale(1, RoundingMode.HALF_UP).toPlainString())
                    .append("%），组合表现将高度绑定该票路径");
        }
        sb.append("。操作上宜「先处理触线与赔率差的票，再谈加仓」；同主题不加仓、不加杠杆叙事。");
        return sb.toString();
    }

    private static String buildSummary(int n, String stance, String topTheme, BigDecimal topThemePct,
                                       BigDecimal maxWeight, int stopHit, int takeHit,
                                       int weakTech, int richVal) {
        StringBuilder sb = new StringBuilder();
        sb.append(n).append(" 只持仓 · 姿态「").append(stance).append("」");
        if (StringUtils.isNotBlank(topTheme) && Objects.nonNull(topThemePct) && topThemePct.signum() > 0) {
            sb.append(" · 主线 ").append(topTheme).append(" ")
                    .append(topThemePct.toPlainString()).append("%");
        }
        if (Objects.nonNull(maxWeight) && maxWeight.signum() > 0) {
            sb.append(" · 单票最重 ")
                    .append(maxWeight.setScale(1, RoundingMode.HALF_UP).toPlainString()).append("%");
        }
        sb.append(" · 止损触线 ").append(stopHit).append(" / 止盈触线 ").append(takeHit);
        sb.append(" · 技术偏弱 ").append(weakTech).append(" · 估值偏贵 ").append(richVal);
        return sb.toString();
    }

    private static String primaryTheme(PortfolioHolding holding) {
        List<String> tags = holding.getThemeTags();
        if (CollUtil.isEmpty(tags)) {
            return null;
        }
        for (String tag : tags) {
            if (StringUtils.isNotBlank(tag)) {
                return tag.trim();
            }
        }
        return null;
    }

    private static int techHit(PortfolioHolding holding) {
        List<ObserveTechSignal> signals = holding.getTechSignals();
        if (CollUtil.isEmpty(signals)) {
            return 0;
        }
        int hit = 0;
        for (ObserveTechSignal signal : signals) {
            if (Objects.nonNull(signal) && Boolean.TRUE.equals(signal.getHit())) {
                hit++;
            }
        }
        return hit;
    }

    private static int techTotal(PortfolioHolding holding) {
        List<ObserveTechSignal> signals = holding.getTechSignals();
        return CollUtil.isEmpty(signals) ? 0 : signals.size();
    }

    private static boolean isRich(String level) {
        return "OVERVALUED".equals(level) || "SLIGHTLY_EXPENSIVE".equals(level);
    }

    private static String stockLabel(String code, String name) {
        if (StringUtils.isBlank(code)) {
            return StringUtils.isNotBlank(name) ? name : "";
        }
        if (StringUtils.isBlank(name) || name.equals(code)) {
            return code;
        }
        return code + " " + name;
    }

    private static PortfolioTipItem tip(String level, String code, String name, String text) {
        return PortfolioTipItem.builder()
                .level(level)
                .code(code)
                .name(name)
                .text(text)
                .build();
    }

    private static String money(BigDecimal v) {
        if (Objects.isNull(v)) {
            return "-";
        }
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static List<PortfolioTipItem> dedupeTips(List<PortfolioTipItem> list) {
        List<PortfolioTipItem> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (PortfolioTipItem item : list) {
            if (Objects.isNull(item) || StringUtils.isBlank(item.getText())) {
                continue;
            }
            if (seen.contains(item.getText())) {
                continue;
            }
            seen.add(item.getText());
            out.add(item);
        }
        // critical > warn > info
        out.sort(Comparator.comparingInt((PortfolioTipItem t) -> levelRank(t.getLevel())));
        return out;
    }

    private static int levelRank(String level) {
        if ("critical".equals(level)) {
            return 0;
        }
        if ("warn".equals(level)) {
            return 1;
        }
        return 2;
    }

    private static List<PortfolioTipItem> limitTips(List<PortfolioTipItem> list, int max) {
        if (list.size() <= max) {
            return list;
        }
        return new ArrayList<>(list.subList(0, max));
    }

    private static List<String> dedupeStrings(List<String> list) {
        List<String> out = new ArrayList<>();
        for (String s : list) {
            if (StringUtils.isBlank(s) || out.contains(s)) {
                continue;
            }
            out.add(s);
        }
        return out;
    }

    private static List<String> limitStrings(List<String> list, int max) {
        if (list.size() <= max) {
            return list;
        }
        return new ArrayList<>(list.subList(0, max));
    }
}
