package com.awe.apex.quant.domain.dto;

import lombok.Data;

/**
 * 组合导入请求
 */
@Data
public class PortfolioImportReq {

    /**
     * 文本内容：每行 代码,数量,成本
     */
    private String text;
}
