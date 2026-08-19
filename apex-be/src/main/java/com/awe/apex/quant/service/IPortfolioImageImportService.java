package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.PortfolioImageImportPreviewResp;
import org.springframework.web.multipart.MultipartFile;

/**
 * 持仓截图识别服务
 */
public interface IPortfolioImageImportService {

    /**
     * 识别持仓截图并返回可编辑预览，不保存持仓
     *
     * @param portfolioId 组合ID
     * @param image       持仓截图
     * @return 识别预览
     */
    PortfolioImageImportPreviewResp preview(Long portfolioId, MultipartFile image);
}
