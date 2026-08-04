package com.awe.apex.quant.market;

import com.awe.apex.common.util.StringUtils;

import java.util.Set;

/**
 * A 股市场代码工具
 */
public final class MarketCodeUtils {

    /**
     * 常见上证指数（不含与深市个股冲突的代码，如 000016 康佳）
     */
    private static final Set<String> SH_INDEX_CODES = Set.of(
            "000300", "000688", "000905", "000852", "000903", "000906"
    );

    private MarketCodeUtils() {
    }

    /**
     * 是否指数代码
     *
     * @param code 证券代码
     * @return true=指数
     */
    public static boolean isIndex(String code) {
        String pure = normalizeCode(code);
        return StringUtils.isNotBlank(pure) && SH_INDEX_CODES.contains(pure);
    }

    /**
     * 推断市场：SH / SZ / BJ / HK
     *
     * @param code 证券代码
     * @return 市场
     */
    public static String resolveMarket(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String pure = code.trim().toUpperCase();
        if (pure.contains(".")) {
            String[] parts = pure.split("\\.");
            if (parts.length == 2) {
                if ("SH".equals(parts[1]) || "SZ".equals(parts[1]) || "BJ".equals(parts[1]) || "HK".equals(parts[1])) {
                    return parts[1];
                }
                if ("SH".equals(parts[0]) || "SZ".equals(parts[0]) || "BJ".equals(parts[0]) || "HK".equals(parts[0])) {
                    return parts[0];
                }
            }
        }
        if (pure.startsWith("HK")) {
            return "HK";
        }
        String digits = normalizeCode(pure);
        if (isHkCode(digits)) {
            return "HK";
        }
        if (isIndex(digits)) {
            return "SH";
        }
        // 北交所：83/87/43/92 开头；900 为沪 B，不能整段 9 判 BJ
        if (digits != null && (digits.startsWith("92") || digits.startsWith("83")
                || digits.startsWith("87") || digits.startsWith("4"))) {
            return "BJ";
        }
        // 沪市：6 主板、5 基金/ETF（含 588 科创板 ETF）、9 沪 B
        if (digits != null && (digits.startsWith("5") || digits.startsWith("6") || digits.startsWith("9"))) {
            return "SH";
        }
        // 其余默认深市（0/1/2/3 等）；勿再把 8 开头一律当北交所（会误伤 588xxx）
        return "SZ";
    }

    /**
     * 是否港股代码（常见 4~5 位，如 1810 / 01810 / 03986）
     *
     * @param digits 纯数字代码
     * @return true=港股
     */
    public static boolean isHkCode(String digits) {
        if (StringUtils.isBlank(digits)) {
            return false;
        }
        // A 股主板/科创/创业均为 6 位；港股通常见 4~5 位
        return digits.length() >= 4 && digits.length() <= 5;
    }

    /**
     * 规范化为纯数字代码
     *
     * @param code 原始代码
     * @return 6 位代码
     */
    public static String normalizeCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String pure = code.trim().toUpperCase();
        if (pure.contains(".")) {
            String[] parts = pure.split("\\.");
            for (String part : parts) {
                if (part.matches("\\d{6}")) {
                    return part;
                }
            }
        }
        String digits = pure.replaceAll("\\D", "");
        if (digits.length() >= 6) {
            return digits.substring(digits.length() - 6);
        }
        return digits;
    }

    /**
     * 是否场内基金/ETF（无上市公司 F10，勿走公司概况）
     *
     * @param code 证券代码
     * @return true=基金/ETF
     */
    public static boolean isFundOrEtf(String code) {
        String digits = normalizeCode(code);
        if (StringUtils.isBlank(digits) || digits.length() < 6) {
            return false;
        }
        // 沪市基金/ETF：5xxxxx（含 51/56/58 等）
        if (digits.startsWith("5")) {
            return true;
        }
        // 深市基金/ETF：15/16/18 开头为主
        return digits.startsWith("15") || digits.startsWith("16") || digits.startsWith("18");
    }

    /**
     * 东方财富 secid
     *
     * @param code 证券代码
     * @return secid
     */
    public static String toEastMoneySecId(String code) {
        String pure = normalizeHoldingCode(code);
        String market = resolveMarket(pure);
        if ("HK".equals(market)) {
            // 东财港股 secid：116.xxxxx
            return "116." + pure;
        }
        if ("SH".equals(market)) {
            return "1." + pure;
        }
        return "0." + pure;
    }

    /**
     * 持仓/行情用代码规范化：港股补齐 5 位，A 股保持 6 位
     *
     * @param code 原始代码
     * @return 规范化代码
     */
    public static String normalizeHoldingCode(String code) {
        String digits = normalizeCode(code);
        if (StringUtils.isBlank(digits)) {
            return digits;
        }
        if (isHkCode(digits)) {
            if (digits.length() >= 5) {
                return digits.substring(digits.length() - 5);
            }
            String padded = "00000" + digits;
            return padded.substring(padded.length() - 5);
        }
        return digits;
    }
}
