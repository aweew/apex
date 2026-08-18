package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.DecisionItemResp;
import com.awe.apex.quant.domain.dto.ObserveGuideTemplateResp;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
import com.awe.apex.quant.domain.dto.ObservePoolSaveReq;
import com.awe.apex.quant.domain.entity.ObservePool;

import java.util.List;
import java.util.Map;

/**
 * 观察池服务
 */
public interface IObservePoolService {

    /**
     * 观察池列表（含现场评估）
     *
     * @param status  状态过滤；空则默认排除 ARCHIVED
     * @param side    方向 BUY/SELL/MOOD
     * @param keyword 代码/名称关键字
     * @return 列表
     */
    List<ObservePoolResp> list(String status, String side, String keyword);

    /**
     * 看板用轻量告警：仅现价评估 TRIGGERED/NEAR，不做估值与技术指标
     *
     * @param limit 返回条数上限
     * @return 接近/已触发列表
     */
    List<ObservePoolResp> listReadyAlerts(int limit);

    /**
     * 查询当前用户未归档观察股代码，不加载行情和技术指标。
     *
     * @return 未归档观察股代码
     */
    List<String> listActiveCodes();

    /**
     * 新增或更新
     *
     * @param req 请求
     * @return 实体
     */
    ObservePool save(ObservePoolSaveReq req);

    /**
     * 删除
     *
     * @param id 主键
     */
    void remove(Long id);

    /**
     * 归档
     *
     * @param id 主键
     * @return 实体
     */
    ObservePool archive(Long id);

    /**
     * 刷新评估并回写状态
     *
     * @return 统计
     */
    Map<String, Object> refresh();

    /**
     * 按原因返回指导模板
     *
     * @param reason 原因关键词
     * @return 模板
     */
    ObserveGuideTemplateResp guideTemplate(String reason);

    /**
     * 全部指导模板
     *
     * @return 模板列表
     */
    List<ObserveGuideTemplateResp> guideTemplates();

    /**
     * 根据智能决策写入观察池：仅「准备买入」；sells 参数保留兼容但不写入
     *
     * @param buys  买入建议
     * @param sells 卖出建议（忽略）
     * @return 同步统计
     */
    Map<String, Object> syncFromDecision(List<DecisionItemResp> buys, List<DecisionItemResp> sells);
}
