package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 盘前观察或失效条件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandWatchConditionResp {

    /**
     * 条件标题。
     */
    private String title;

    /**
     * 条件内容。
     */
    private String condition;
}
