package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ClawBot 股票问答响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotAskResp {

    /**
     * 请求号。
     */
    private String requestId;

    /**
     * 识别意图。
     */
    private String intent;

    /**
     * 股票代码，可空。
     */
    private String stockCode;

    /**
     * 股票名称，可空。
     */
    private String stockName;

    /**
     * 可直接发送到微信的答案。
     */
    private String answer;

    /**
     * 数据截至时间。
     */
    private String dataAsOf;

    /**
     * 数据完整度 GREEN/YELLOW/RED。
     */
    private String dataLevel;

    /**
     * 是否使用 AI 增强。
     */
    private Boolean aiEnhanced;
}
