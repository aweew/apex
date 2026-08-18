package com.awe.apex.quant.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作指引稳定代码。
 */
@Getter
@AllArgsConstructor
public enum OperationGuideCodeEnum {

    RISK_FIRST("RISK_FIRST", "风险处置"),
    BUY_CONDITIONALLY("BUY_CONDITIONALLY", "条件新仓"),
    WATCH_ALERTS("WATCH_ALERTS", "观察提醒"),
    REFRESH_DATA("REFRESH_DATA", "刷新数据"),
    VIEW_CONTEXT("VIEW_CONTEXT", "查看盘前依据");

    /** 操作编码 */
    private final String code;

    /** 操作说明 */
    private final String desc;
}
