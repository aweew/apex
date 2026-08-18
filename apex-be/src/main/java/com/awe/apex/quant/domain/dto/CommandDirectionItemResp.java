package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 盘前机会或风险方向。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandDirectionItemResp {

    /**
     * 方向名称。
     */
    private String name;

    /**
     * 入选原因。
     */
    private String reason;
}
