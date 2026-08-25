package com.awe.apex.quant.decision;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 决策候选通道
 */
@Getter
@AllArgsConstructor
public enum DecisionLaneEnum {

    CORE("CORE", "核心防守线"),
    GROWTH("GROWTH", "科技成长线");

    /** 通道编码 */
    private final String code;

    /** 通道说明 */
    private final String desc;
}
