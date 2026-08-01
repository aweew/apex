package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 日线同步请求
 */
@Data
public class BarSyncReq {

    /**
     * 证券代码列表
     */
    @NotEmpty(message = "codes 不能为空")
    private List<String> codes;

    /**
     * 开始日期 yyyyMMdd 或 yyyy-MM-dd
     */
    private String beginDate;

    /**
     * 结束日期 yyyyMMdd 或 yyyy-MM-dd
     */
    private String endDate;
}
