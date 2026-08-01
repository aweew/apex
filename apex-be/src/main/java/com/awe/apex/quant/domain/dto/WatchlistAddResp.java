package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量加入自选结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistAddResp {

    /**
     * 新增条数
     */
    private Integer addedCount;

    /**
     * 已存在更新条数
     */
    private Integer updatedCount;

    /**
     * 分组
     */
    private String groupName;

    /**
     * 说明
     */
    private String message;
}
