package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 建议买入 · AI 详细总结
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionBuyAiResp {

    /**
     * 是否已配置大模型
     */
    private Boolean configured;

    /**
     * 是否来自缓存
     */
    private Boolean fromCache;

    /**
     * 模型名
     */
    private String model;

    /**
     * 决策日
     */
    private LocalDate actionDate;

    /**
     * 买入条数
     */
    private Integer buyCount;

    /**
     * 整体立场（如：可分批试探 / 精选跟踪 / 暂缓进攻）
     */
    private String stance;

    /**
     * 详细总结正文（约 200–350 字）
     */
    private String summary;

    /**
     * 关键关注点
     */
    private List<String> watchPoints;

    /**
     * 单票要点（按清单顺序，最多 8 只）
     */
    private List<DecisionBuyAiStockNote> stockNotes;

    /**
     * 风险提示
     */
    private String riskNote;

    /**
     * 生成时间
     */
    private LocalDateTime generatedAt;

    /**
     * 免责声明
     */
    private String disclaimer;
}
