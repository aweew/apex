package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘前晨报。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MorningBriefingResp {

    /**
     * 晨报生成时间。
     */
    private LocalDateTime generatedAt;

    /**
     * 美股与关键股报价。
     */
    private List<OvernightMarketQuote> marketQuotes;

    /**
     * 市场指数报价。
     */
    private List<OvernightMarketQuote> indexQuotes;

    /**
     * 明星异动报价。
     */
    private List<OvernightMarketQuote> starQuotes;

    /**
     * 美股主题情绪。
     */
    private List<OvernightMarketTheme> marketThemes;

    /**
     * 夜间重点新闻标题。
     */
    private List<String> newsTitles;

    /**
     * 今日消息面完整快照。
     */
    private NewsPulseResp newsPulse;

    /**
     * 晨报摘要。
     */
    private String summary;

    /**
     * 数据等级 GREEN/YELLOW。
     */
    private String dataLevel;
}
