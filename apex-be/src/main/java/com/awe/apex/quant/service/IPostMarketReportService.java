package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.PostMarketReportResp;

/**
 * 最新交易日盘后总结服务。
 */
public interface IPostMarketReportService {

    /**
     * 读取盘后可见窗口内的最新交易日总结。
     *
     * @param forceRefresh 是否跳过缓存重新生成
     * @return 最新盘后总结，非可见窗口返回 null
     */
    PostMarketReportResp latest(boolean forceRefresh);

    /**
     * 使用本地收盘数据生成最新交易日总结。
     *
     * @return 最新盘后总结
     */
    PostMarketReportResp generate();

    /**
     * 重新组装本地数据并刷新最新盘后总结缓存。
     *
     * @return 最新盘后总结，非可见窗口返回 null
     */
    PostMarketReportResp refresh();
}
