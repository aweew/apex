package com.awe.apex.quant.domain.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 一键决策请求
 */
@Data
public class DecisionRunReq {

    /**
     * 自选分组，默认「我的自选」
     */
    private String groupName;

    /**
     * 决策日，可空=今天
     */
    private LocalDate date;
}
