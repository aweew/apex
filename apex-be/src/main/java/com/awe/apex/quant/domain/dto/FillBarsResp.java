package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 循环补齐日线响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FillBarsResp {

    /**
     * 实际轮数
     */
    private Integer rounds;

    /**
     * 累计成功股票数
     */
    private Integer totalSuccess;

    /**
     * 累计失败股票数
     */
    private Integer totalFail;

    /**
     * 累计写入 K 线
     */
    private Integer totalBars;

    /**
     * 是否因无更多过期而提前结束
     */
    private Boolean completed;

    /**
     * 说明
     */
    private String message;
}
