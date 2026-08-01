package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码出现次数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeCountItem {

    /**
     * 证券代码
     */
    private String code;

    /**
     * 次数
     */
    private Integer count;
}
