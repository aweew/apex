package com.awe.apex.quant.util;

import cn.hutool.extra.pinyin.PinyinUtil;
import com.awe.apex.common.util.StringUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Locale;

/**
 * 证券名称拼音处理
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StockPinyinUtils {

    /**
     * 生成证券简称的拼音首字母缩写。
     *
     * @param stockName 证券简称
     * @return 小写拼音首字母缩写
     */
    public static String buildAbbr(String stockName) {
        if (StringUtils.isBlank(stockName)) {
            return null;
        }
        return PinyinUtil.getFirstLetter(stockName, "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }
}
