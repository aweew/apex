package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量加入自选请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistAddReq {

    /**
     * 分组名
     */
    private String groupName;

    /**
     * 来源标记
     */
    private String source;

    /**
     * 待加入标的
     */
    private List<WatchlistAddItem> items;
}
