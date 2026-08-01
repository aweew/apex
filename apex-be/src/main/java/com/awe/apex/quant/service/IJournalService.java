package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.JournalCreateReq;
import com.awe.apex.quant.domain.entity.JournalTrade;

import java.util.List;

/**
 * 人工成交日记
 */
public interface IJournalService {

    /**
     * 录入
     *
     * @param req 请求
     * @return 记录
     */
    JournalTrade create(JournalCreateReq req);

    /**
     * 列表
     *
     * @param limit 条数
     * @return 列表
     */
    List<JournalTrade> latest(int limit);

    /**
     * 从清单一键填入
     *
     * @param actionId 清单ID
     * @param price    成交价
     * @param quantity 数量
     * @return 记录
     */
    JournalTrade fromAction(Long actionId, java.math.BigDecimal price, Integer quantity);
}
