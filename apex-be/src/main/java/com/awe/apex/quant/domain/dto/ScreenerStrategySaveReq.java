package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 选股策略保存请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerStrategySaveReq {

    /** 主键，新增时为空 */
    private Long id;

    /** 策略名称 */
    private String name;

    /** 策略说明 */
    private String description;

    /** 运行模式 */
    private String runMode;

    /** 是否启用 */
    private Boolean enabled;

    /** 排序号 */
    private Integer sortNo;

    /** 策略规则 */
    private List<ScreenerStrategyRuleSaveReq> rules;
}
