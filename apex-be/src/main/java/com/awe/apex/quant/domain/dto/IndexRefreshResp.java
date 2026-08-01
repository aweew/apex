package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 指数刷新结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexRefreshResp {

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
     * 刷新后看板
     */
    private IndexBoardResp board;
}
