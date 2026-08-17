package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单只股票分时复核拉取结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerIntradayFetchResp {

    /** 证券代码 */
    private String code;

    /** 分时数据 */
    private StockIntradayResp intraday;

    /** 拉取失败说明 */
    private String error;

    /** 是否来自短时缓存 */
    private Boolean cached;
}
