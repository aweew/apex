package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.ApexAiAnalysisResp;
import com.awe.apex.quant.domain.dto.ApexAiAnalyzeReq;
import com.awe.apex.quant.domain.dto.ApexAiContextResp;
import com.awe.apex.quant.domain.dto.ApexAiEnhanceReq;

/**
 * Apex AI 分析服务
 */
public interface IApexAiAnalystService {

    /**
     * 查询工作台可用分析上下文
     *
     * @return 分析上下文
     */
    ApexAiContextResp context();

    /**
     * 分析 Apex 数据并回答问题
     *
     * @param request 分析请求
     * @return 分析结果
     */
    ApexAiAnalysisResp analyze(ApexAiAnalyzeReq request);

    /**
     * 使用大模型增强已生成的规则分析。
     *
     * @param request 增强请求
     * @return 增强结果；失败时返回原规则结果
     */
    ApexAiAnalysisResp enhance(ApexAiEnhanceReq request);
}
