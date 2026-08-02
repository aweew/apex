package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 观察池指导模板
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObserveGuideTemplateResp {

    /**
     * 原因关键词
     */
    private String reason;

    /**
     * 推荐触发类型
     */
    private String triggerType;

    /**
     * 补充条件示例
     */
    private String triggerExpr;

    /**
     * 详细指导
     */
    private String guideText;

    /**
     * 标签建议
     */
    private String tags;
}
