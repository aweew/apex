package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 成交日历
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeCalendarResp {

    /**
     * 回看天数
     */
    private Integer days;

    /**
     * 说明
     */
    private String message;

    /**
     * 按日
     */
    private List<TradeCalendarDay> daysList;
}
