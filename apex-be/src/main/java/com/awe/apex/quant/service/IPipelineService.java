package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.PipelineRunReq;
import com.awe.apex.quant.domain.dto.PipelineRunResp;

/**
 * 一键研究流水线
 */
public interface IPipelineService {

    /**
     * 运行流水线
     *
     * @param req 请求
     * @return 结果
     */
    PipelineRunResp run(PipelineRunReq req);
}
