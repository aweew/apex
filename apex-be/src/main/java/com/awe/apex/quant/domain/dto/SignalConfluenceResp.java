package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 信号共振
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalConfluenceResp {

    /**
     * 回看天数
     */
    private Integer days;

    /**
     * 最少策略数
     */
    private Integer minStrategies;

    /**
     * 说明
     */
    private String message;

    /**
     * 共振列表
     */
    private List<SignalConfluenceItem> items;
}
