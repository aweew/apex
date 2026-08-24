package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Apex AI 分析后的站内下一步动作。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApexAiAction {

    /**
     * 动作名称。
     */
    private String label;

    /**
     * 站内路由。
     */
    private String route;

    /**
     * 视觉强调类型，支持 PRIMARY、DEFAULT。
     */
    private String tone;
}
