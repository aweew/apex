package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.bot.config.ApexBotProperties;
import com.awe.apex.quant.domain.dto.MorningBriefingResp;
import com.awe.apex.quant.domain.dto.NewsPulseCardResp;
import com.awe.apex.quant.domain.dto.NewsPulseResp;
import com.awe.apex.quant.domain.dto.OvernightMarketQuote;
import com.awe.apex.quant.market.UsMarketQuoteClient;
import com.awe.apex.quant.service.IMorningBriefingService;
import com.awe.apex.quant.service.INewsPulseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 盘前晨报服务实现。
 */
@Slf4j
@Service
public class MorningBriefingServiceImpl implements IMorningBriefingService {

    @Resource
    private ApexBotProperties properties;

    @Resource
    private UsMarketQuoteClient usMarketQuoteClient;

    @Resource
    private INewsPulseService newsPulseService;

    /**
     * 汇总隔夜美股和夜间新闻。
     *
     * @return 盘前晨报
     */
    @Override
    public MorningBriefingResp generate() {
        LocalDateTime generatedAt = LocalDateTime.now();
        List<String> symbols = new ArrayList<>();
        for (String symbol : properties.getMorningBriefing().getSymbols().split(",")) {
            if (StringUtils.isNotBlank(symbol)) {
                symbols.add(symbol.trim());
            }
        }
        List<OvernightMarketQuote> marketQuotes = usMarketQuoteClient.fetch(symbols);
        NewsPulseResp newsPulse = loadNewsPulse();
        List<String> newsTitles = new ArrayList<>();
        if (Objects.nonNull(newsPulse) && CollUtil.isNotEmpty(newsPulse.getCards())) {
            for (NewsPulseCardResp card : newsPulse.getCards()) {
                if (StringUtils.isNotBlank(card.getTitle())) {
                    newsTitles.add(card.getTitle().trim());
                }
                if (newsTitles.size() >= 3) {
                    break;
                }
            }
        }

        StringBuilder summary = new StringBuilder("隔夜美股：");
        if (CollUtil.isEmpty(marketQuotes)) {
            summary.append("美股行情暂未获取。");
        } else {
            for (int index = 0; index < marketQuotes.size(); index++) {
                OvernightMarketQuote quote = marketQuotes.get(index);
                if (index > 0) {
                    summary.append("；");
                }
                summary.append(quote.getName()).append(" ").append(formatPercent(quote.getPctChg()));
            }
            summary.append("。");
        }
        summary.append("\n夜间新闻：");
        if (Objects.nonNull(newsPulse) && StringUtils.isNotBlank(newsPulse.getExecutiveSummary())) {
            summary.append(newsPulse.getExecutiveSummary());
        } else {
            summary.append("暂未形成有效摘要。");
        }
        summary.append("\n仅供研究，不构成投资建议。");
        return MorningBriefingResp.builder()
                .generatedAt(generatedAt)
                .marketQuotes(marketQuotes)
                .newsTitles(newsTitles)
                .summary(summary.toString())
                .dataLevel(CollUtil.isEmpty(marketQuotes) ? "YELLOW" : "GREEN")
                .build();
    }

    private NewsPulseResp loadNewsPulse() {
        try {
            return newsPulseService.pulse(6, true);
        } catch (Exception ex) {
            log.warn("盘前晨报夜间新闻摘要失败 reason={}", ex.getMessage());
            return null;
        }
    }

    private String formatPercent(BigDecimal pctChg) {
        if (Objects.isNull(pctChg)) {
            return "--";
        }
        return (pctChg.signum() > 0 ? "+" : "")
                + pctChg.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
