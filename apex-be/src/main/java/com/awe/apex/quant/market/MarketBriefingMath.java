package com.awe.apex.quant.market;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 市场简报纯计算：广度解析、分化信号、成交额字段选择（便于单测）
 */
public final class MarketBriefingMath {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private MarketBriefingMath() {
    }

    /**
     * 东财涨跌分布档位聚合。[上涨, 下跌, 平盘]
     * level&gt;0 上涨（含涨停档 11），level&lt;0 下跌（含跌停档 -11），0 平盘。
     *
     * @param levels 档位 → 家数
     * @return [up, down, flat]
     */
    public static int[] aggregateFenbu(java.util.Map<Integer, Integer> levels) {
        int up = 0;
        int down = 0;
        int flat = 0;
        if (levels == null || levels.isEmpty()) {
            return new int[]{0, 0, 0};
        }
        for (java.util.Map.Entry<Integer, Integer> entry : levels.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            int level = entry.getKey();
            int count = Math.max(0, entry.getValue());
            if (level > 0) {
                up += count;
            } else if (level < 0) {
                down += count;
            } else {
                flat += count;
            }
        }
        return new int[]{up, down, flat};
    }

    /**
     * 沪+深指数涨跌家数相加。
     *
     * @param shUp   上证上涨
     * @param shDown 上证下跌
     * @param shFlat 上证平盘
     * @param szUp   深成上涨
     * @param szDown 深成下跌
     * @param szFlat 深成平盘
     * @return [up, down, flat]
     */
    public static int[] sumIndexBreadth(int shUp, int shDown, int shFlat,
                                        int szUp, int szDown, int szFlat) {
        return new int[]{
                Math.max(0, shUp) + Math.max(0, szUp),
                Math.max(0, shDown) + Math.max(0, szDown),
                Math.max(0, shFlat) + Math.max(0, szFlat)
        };
    }

    /**
     * 广度信号：指数与家数分化时不加偏多/偏空分。
     *
     * @param up     上涨家数
     * @param down   下跌家数
     * @param dayAvg 上证/创业板当日均涨跌幅，可空
     * @return 信号结果
     */
    public static BreadthSignal resolveBreadthSignal(int up, int down, BigDecimal dayAvg) {
        BreadthSignal signal = new BreadthSignal();
        signal.signal = "中性";
        signal.scoreDelta = 0;
        signal.divergent = false;
        if (up <= 0 && down <= 0) {
            return signal;
        }
        boolean indexDown = Objects.nonNull(dayAvg) && dayAvg.compareTo(ZERO) < 0;
        boolean indexUp = Objects.nonNull(dayAvg) && dayAvg.compareTo(ZERO) > 0;
        if (up > down * 1.5) {
            if (indexDown) {
                signal.signal = "分化";
                signal.divergent = true;
                signal.tip = "指数下跌但上涨家数占优，属权重拖累、个股偏强；勿把广度当成指数已转强。";
            } else {
                signal.signal = "偏多";
                signal.scoreDelta = 6;
            }
        } else if (down > up * 1.5) {
            if (indexUp) {
                signal.signal = "分化";
                signal.divergent = true;
                signal.tip = "指数上涨但下跌家数占优，赚钱效应一般，追高需谨慎。";
            } else {
                signal.signal = "偏空";
                signal.scoreDelta = -8;
                signal.tip = "下跌家数明显多于上涨，市场广度偏弱。";
            }
        }
        return signal;
    }

    /**
     * 指数成交额字段：优先 f6，f48 为 0/- 时丢弃。
     *
     * @param f6  成交额
     * @param f48 备用（指数上常为 0）
     * @return 正数成交额或 null
     */
    public static BigDecimal pickIndexAmount(BigDecimal f6, BigDecimal f48) {
        if (Objects.nonNull(f6) && f6.signum() > 0) {
            return f6;
        }
        if (Objects.nonNull(f48) && f48.signum() > 0) {
            return f48;
        }
        return null;
    }

    /**
     * 量能标签：有较前日比则「放量/缩量 + pct」；仅有今日额则「实时/今日」。
     *
     * @param vsMa5Pct 较上一交易日%，可空（参数名历史遗留）
     * @param usedLive 是否实时额
     * @return 标签，无额时 null
     */
    public static String volumeLabel(BigDecimal vsMa5Pct, boolean usedLive) {
        if (Objects.nonNull(vsMa5Pct)) {
            String trend = vsMa5Pct.compareTo(ZERO) >= 0 ? "放量" : "缩量";
            String sign = vsMa5Pct.compareTo(ZERO) > 0 ? "+" : "";
            return trend + " " + sign + vsMa5Pct.setScale(2, java.math.RoundingMode.HALF_UP) + "%";
        }
        return usedLive ? "实时" : "今日";
    }

    /**
     * 上涨占比（整数百分比）
     */
    public static Integer upSharePct(int up, int down, int flat) {
        int total = up + down + flat;
        if (total <= 0) {
            return null;
        }
        return up * 100 / total;
    }

    /**
     * 广度信号结果
     */
    public static final class BreadthSignal {
        /** 偏多 / 偏空 / 分化 / 中性 */
        public String signal;
        /** 评分增量 */
        public int scoreDelta;
        /** 是否指数与家数分化 */
        public boolean divergent;
        /** 提示文案，可空 */
        public String tip;
    }
}
