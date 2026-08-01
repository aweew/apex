package com.awe.apex.quant.domain.dto;

import lombok.Data;

/**
 * 自选导入请求
 */
@Data
public class WatchlistImportReq {

    /**
     * 相对 mx-output-dir 的文件名，或绝对路径
     */
    private String filePath;

    /**
     * 分组名
     */
    private String groupName;
}
