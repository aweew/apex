package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Bot 结构化工具响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotToolResp {

    /** 请求号 */
    private String requestId;

    /** 工具意图 */
    private String intent;

    /** 可直接发送的规则答案 */
    private String answer;

    /** 数据截至时间 */
    private String dataAsOf;

    /** 数据完整度 GREEN / YELLOW / RED */
    private String dataLevel;

    /** 逐标的行情时效摘要 */
    @Builder.Default
    private List<String> quoteStatus = new ArrayList<>();
}
