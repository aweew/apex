package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自选新增条目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistAddItem {

    /**
     * 证券代码
     */
    private String code;

    /**
     * 证券简称
     */
    private String name;
}
