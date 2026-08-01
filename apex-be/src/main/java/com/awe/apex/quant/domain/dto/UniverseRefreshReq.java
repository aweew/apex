package com.awe.apex.quant.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 股票池刷新请求
 */
@Data
public class UniverseRefreshReq {

    /**
     * 代码列表，空则取自选
     */
    private List<String> codes;

    /**
     * 分组
     */
    private String groupName;
}
