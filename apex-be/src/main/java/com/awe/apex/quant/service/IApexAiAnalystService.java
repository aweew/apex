package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.ApexAiAnalysisResp;
import com.awe.apex.quant.domain.dto.ApexAiAnalyzeReq;
import com.awe.apex.quant.domain.dto.ApexAiContextResp;

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
}
