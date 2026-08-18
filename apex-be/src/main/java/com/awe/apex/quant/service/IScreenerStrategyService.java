package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.ScreenerStrategyReorderReq;
import com.awe.apex.quant.domain.dto.ScreenerStrategyResp;
import com.awe.apex.quant.domain.dto.ScreenerStrategySaveReq;

import java.util.List;

/**
 * 可维护选股策略服务
 */
public interface IScreenerStrategyService {

    /**
     * 查询系统模板和当前用户策略。
     *
     * @return 策略列表
     */
    List<ScreenerStrategyResp> list();

    /**
     * 查询当前用户策略详情。
     *
     * @param id 策略ID
     * @return 策略详情
     */
    ScreenerStrategyResp detail(Long id);

    /**
     * 新增或更新当前用户策略。
     *
     * @param req 保存请求
     * @return 策略详情
     */
    ScreenerStrategyResp save(ScreenerStrategySaveReq req);

    /**
     * 将系统模板复制为当前用户策略。
     *
     * @param templateKey 模板标识
     * @return 新策略
     */
    ScreenerStrategyResp copyTemplate(String templateKey);

    /**
     * 复制当前用户策略。
     *
     * @param id 原策略ID
     * @return 新策略
     */
    ScreenerStrategyResp copy(Long id);

    /**
     * 启用或停用当前用户策略。
     *
     * @param id      策略ID
     * @param enabled 是否启用
     * @return 更新后策略
     */
    ScreenerStrategyResp toggle(Long id, Boolean enabled);

    /**
     * 调整当前用户策略顺序。
     *
     * @param req 排序请求
     */
    void reorder(ScreenerStrategyReorderReq req);

    /**
     * 删除当前用户策略。
     *
     * @param id 策略ID
     */
    void remove(Long id);

    /**
     * 解析可运行的用户策略或系统模板。
     *
     * @param strategyId 策略ID
     * @param templateKey 模板标识
     * @return 策略定义
     */
    ScreenerStrategyResp resolveRunnable(Long strategyId, String templateKey);
}
