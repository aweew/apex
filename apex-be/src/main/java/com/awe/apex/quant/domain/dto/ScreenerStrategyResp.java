package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 选股策略响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerStrategyResp {

    /** 用户策略主键，系统模板为空 */
    private Long id;

    /** 系统模板稳定标识 */
    private String templateKey;

    /** 策略名称 */
    private String name;

    /** 策略说明 */
    private String description;

    /** 来源类型 */
    private String sourceType;

    /** 运行模式 */
    private String runMode;

    /** 是否启用 */
    private Boolean enabled;

    /** 排序号 */
    private Integer sortNo;

    /** 版本号 */
    private Integer versionNo;

    /** 是否系统模板 */
    private Boolean template;

    /** 是否允许直接编辑 */
    private Boolean editable;

    /** 模板风险说明 */
    private String disclaimer;

    /** 策略通俗指南 */
    private ScreenerStrategyGuideResp guide;

    /** 规则列表 */
    private List<ScreenerStrategyRuleResp> rules;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
