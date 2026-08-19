package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 持仓截图识别预览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioImageImportPreviewResp {

    /**
     * 可编辑的持仓预览行
     */
    @Builder.Default
    private List<PortfolioImageImportRowResp> rows = new ArrayList<>();

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
