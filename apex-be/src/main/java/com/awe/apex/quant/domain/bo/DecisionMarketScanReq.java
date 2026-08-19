package com.awe.apex.quant.domain.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 共享市场扫描请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionMarketScanReq {

    /** 决策日期 */
    private LocalDate actionDate;

    /** 股票池批次号 */
    private String universeBatchNo;

    /** 是否包含北交所 */
    private Boolean includeBj;

    /** 股票池数量 */
    private Integer universeCount;

    /** 热点扩扫数量 */
    private Integer hotScanCount;

    /** 待扫描证券代码 */
    private List<String> codes;
}
