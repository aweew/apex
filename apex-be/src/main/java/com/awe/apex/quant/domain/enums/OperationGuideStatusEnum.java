package com.awe.apex.quant.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作指引条目状态。
 */
@Getter
@AllArgsConstructor
public enum OperationGuideStatusEnum {

    REQUIRED("REQUIRED", "必做"),
    READY("READY", "可执行"),
    WAIT("WAIT", "等待"),
    BLOCKED("BLOCKED", "阻断"),
    DONE("DONE", "已清");

    /** 状态编码 */
    private final String code;

    /** 状态说明 */
    private final String desc;
}
