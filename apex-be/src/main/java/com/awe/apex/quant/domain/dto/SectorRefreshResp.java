package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 板块刷新结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorRefreshResp {

    /**
     * 脚本退出码
     */
    private Integer exitCode;

    /**
     * 脚本日志摘要
     */
    private String log;

    /**
     * 刷新后榜单
     */
    private SectorBoardResp board;

    /**
     * 成分股（成分刷新时）
     */
    private SectorConstituentResp constituents;

    /**
     * 说明
     */
    private String message;
}
