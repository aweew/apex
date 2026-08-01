package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新闻刷新结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsRefreshResp {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 说明
     */
    private String message;

    /**
     * 脚本日志
     */
    private String log;

    /**
     * 刷新后总览
     */
    private NewsOverviewResp overview;
}
