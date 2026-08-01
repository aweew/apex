package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 样本内外 walk-forward 结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalkForwardResp {

    /**
     * 代码
     */
    private String code;

    /**
     * 策略
     */
    private String strategyId;

    /**
     * 样本内结束日（含）
     */
    private LocalDate inSampleEnd;

    /**
     * 样本内收益
     */
    private BigDecimal inSampleReturn;

    /**
     * 样本内回撤
     */
    private BigDecimal inSampleMaxDrawdown;

    /**
     * 样本内夏普
     */
    private BigDecimal inSampleSharpe;

    /**
     * 样本外收益
     */
    private BigDecimal outSampleReturn;

    /**
     * 样本外回撤
     */
    private BigDecimal outSampleMaxDrawdown;

    /**
     * 样本外夏普
     */
    private BigDecimal outSampleSharpe;

    /**
     * 样本外相对样本内衰减（out - in）
     */
    private BigDecimal returnDecay;

    /**
     * 免责声明
     */
    private String disclaimer;
}
