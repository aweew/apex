package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 共享市场决策扫描。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("decision_market_scan")
public class DecisionMarketScan implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 决策日期 */
    private LocalDate actionDate;

    /** 股票池批次号 */
    private String universeBatchNo;

    /** 是否包含北交所 */
    private Boolean includeBj;

    /** 扫描状态RUNNING、SUCCESS或FAILED */
    private String status;

    /** 股票池数量 */
    private Integer universeCount;

    /** 热点扩扫数量 */
    private Integer hotScanCount;

    /** 实际扫描证券数量 */
    private Integer scanCodeCount;

    /** 买入信号数量 */
    private Integer signalCount;

    /** 失败原因 */
    private String errorMessage;

    /** 扫描开始时间 */
    private LocalDateTime startedAt;

    /** 扫描完成时间 */
    private LocalDateTime finishedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
