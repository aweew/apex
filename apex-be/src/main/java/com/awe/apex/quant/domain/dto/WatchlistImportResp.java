package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自选导入响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistImportResp {

    /**
     * 导入条数
     */
    private Integer importCount;

    /**
     * 数据来源文件
     */
    private String sourceFile;

    /**
     * 分组
     */
    private String groupName;
}
