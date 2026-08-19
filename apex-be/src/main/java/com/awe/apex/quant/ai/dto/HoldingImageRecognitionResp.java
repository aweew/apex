package com.awe.apex.quant.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 大模型持仓截图识别结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldingImageRecognitionResp {

    /**
     * 识别出的持仓
     */
    @Builder.Default
    private List<HoldingImageRecognitionRow> holdings = new ArrayList<>();

    /**
     * 截图中的总市值
     */
    private BigDecimal totalMarketValue;

    /**
     * 截图完整性提示
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
