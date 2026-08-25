package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘前晨报。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MorningBriefingResp {

    /**
     * 晨报对应的 A 股交易日。
     */
    private LocalDate tradeDate;

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
     * 亚太市场指数报价。
     */
    private List<OvernightMarketQuote> asiaQuotes;

    /**
     * 黄金、原油、美元、汇率和美债等外围环境指标。
     */
    private List<ExternalMarketItemResp> externalMarketItems;

    /**
     * 富时 A50 期指连续报价。
     */
    private OvernightMarketQuote ftseA50Future;

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
     * 机构观点、活跃席位与授权大V状态。
     */
    private MarketOpinionRadarResp marketOpinion;

    /**
     * 晨报摘要。
     */
    private String summary;

    /**
     * 数据等级 GREEN/YELLOW。
     */
    private String dataLevel;

    /**
     * 当前展示的是等待刷新完成的上一份晨报。
     */
    private boolean stale;

    /**
     * 后台正在刷新晨报。
     */
    private boolean refreshing;
}
