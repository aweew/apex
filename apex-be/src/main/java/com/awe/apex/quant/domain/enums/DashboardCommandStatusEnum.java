package com.awe.apex.quant.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 看板指挥可信状态。
 */
@Getter
@AllArgsConstructor
public enum DashboardCommandStatusEnum {

    READY("READY", "数据正常"),
    PARTIAL("PARTIAL", "部分数据缺失"),
    STALE("STALE", "数据已过期"),
    BLOCKED("BLOCKED", "数据阻断"),
    GENERATING("GENERATING", "正在生成");

    /** 状态编码 */
    private final String code;

    /** 状态说明 */
    private final String desc;
}
