package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.ResearchScoreResp;

import java.time.LocalDate;

/**
 * 横截面因子研究快照服务。
 */
public interface IFactorResearchSnapshotService {

    /**
     * 发布指定交易日的不可变研究快照。
     *
     * @param tradeDate 目标交易日
     */
    void publish(LocalDate tradeDate);

    /**
     * 查询证券最新研究快照。
     *
     * @param code 证券代码
     * @return 研究评分
     */
    ResearchScoreResp queryLatest(String code);
}
