package com.awe.apex.quant.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作指引目标类型。
 */
@Getter
@AllArgsConstructor
public enum OperationTargetTypeEnum {

    POSITION("POSITION", "持仓"),
    DECISION("DECISION", "智能决策"),
    OBSERVE("OBSERVE", "观察池"),
    DATA("DATA", "数据");

    /** 目标类型编码 */
    private final String code;

    /** 目标类型说明 */
    private final String desc;
}
