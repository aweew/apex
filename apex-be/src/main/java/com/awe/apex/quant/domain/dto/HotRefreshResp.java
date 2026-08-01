package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点刷新结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotRefreshResp {

    /**
     * 脚本退出码
     */
    private Integer exitCode;

    /**
     * 脚本日志摘要
     */
    private String log;

    /**
     * 刷新后总览
     */
    private HotOverviewResp overview;

    /**
     * 说明
     */
    private String message;
}
