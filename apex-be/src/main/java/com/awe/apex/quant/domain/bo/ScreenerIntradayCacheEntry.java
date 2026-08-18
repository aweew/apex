package com.awe.apex.quant.domain.bo;

import com.awe.apex.quant.domain.dto.StockIntradayResp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 选股分时短时缓存项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerIntradayCacheEntry {

    /** 分时数据 */
    private StockIntradayResp response;

    /** 失效时间戳 */
    private Long expiresAt;
}
