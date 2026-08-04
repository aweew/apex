package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioImportResp {

    /**
     * 成功条数
     */
    private Integer success;

    /**
     * 失败条数
     */
    private Integer fail;

    /**
     * 失败行说明
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();
}
