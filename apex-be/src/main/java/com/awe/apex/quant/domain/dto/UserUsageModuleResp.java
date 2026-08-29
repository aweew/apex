package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能模块使用统计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUsageModuleResp {

    /** 功能模块编码 */
    private String moduleCode;

    /** 功能模块名称 */
    private String moduleName;

    /** 活跃用户数 */
    private Long activeUsers;

    /** 访问次数 */
    private Long visits;

    /** 模块访问占比 */
    private Double visitRate;
}
