package com.awe.apex.quant.domain.bo;

import com.awe.apex.quant.domain.entity.StrategySignalEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 可供用户决策投影使用的共享市场快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionMarketSnapshot {

    /** 共享扫描ID */
    private Long scanId;

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

    /** 实际扫描证券数量 */
    private Integer scanCodeCount;

    /** 共享买入信号 */
    private List<StrategySignalEntity> signals;
}
