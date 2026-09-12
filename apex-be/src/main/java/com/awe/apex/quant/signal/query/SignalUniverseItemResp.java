package com.awe.apex.quant.signal.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 信号中心计算股票池条目。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalUniverseItemResp {

    /** 证券代码。 */
    private String symbol;

    /** 证券名称。 */
    private String name;

    /** 股票所属市场。 */
    private String market;
}
