package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.SignalConfluenceResp;
import com.awe.apex.quant.domain.dto.SignalForwardResp;
import com.awe.apex.quant.domain.dto.SignalItemResp;
import com.awe.apex.quant.domain.dto.SignalRunReq;
import com.awe.apex.quant.domain.dto.SignalStatsResp;
import com.awe.apex.quant.domain.entity.StrategySignalEntity;

import java.time.LocalDate;
import java.util.List;

/**
 * 策略信号服务
 */
public interface ISignalService {

    /**
     * 运行信号
     *
     * @param req 请求
     * @return 信号列表
     */
    List<StrategySignalEntity> run(SignalRunReq req);

    /**
     * 运行信号并上报批次进度
     *
     * @param req              请求
     * @param progressListener 进度监听器
     * @return 信号列表
     */
    List<StrategySignalEntity> run(SignalRunReq req, TaskProgressListener progressListener);

    /**
     * 扫描策略信号但不写入用户信号表。
     *
     * @param req 请求
     * @param progressListener 进度监听器
     * @return 扫描信号
     */
    List<StrategySignalEntity> scan(SignalRunReq req, TaskProgressListener progressListener);

    /**
     * 将指定信号写入当前用户信号表。
     *
     * @param signals 待保存信号
     * @return 已保存信号
     */
    List<StrategySignalEntity> saveForCurrentUser(List<StrategySignalEntity> signals);

    /**
     * 最近信号
     *
     * @param limit       条数
     * @param dedupeByCode 按代码去重（保留最新一条）
     * @return 列表
     */
    List<StrategySignalEntity> latest(int limit, boolean dedupeByCode);

    /**
     * 信号列表补充证券名称
     *
     * @param signals 信号
     * @return 含名称的列表
     */
    List<SignalItemResp> toItemRespList(List<StrategySignalEntity> signals);

    /**
     * 近 N 日信号统计
     *
     * @param days 天数
     * @return 统计
     */
    SignalStatsResp stats(int days);

    /**
     * 信号前瞻收益评估（按收盘价，跳过不足前瞻窗口的样本）
     *
     * @param lookbackDays 回看信号天数
     * @param horizonDays  前瞻交易日数
     * @return 统计
     */
    SignalForwardResp forwardEval(int lookbackDays, int horizonDays);

    /**
     * 多策略同向共振
     *
     * @param days          回看天数
     * @param minStrategies 最少策略数
     * @return 共振
     */
    SignalConfluenceResp confluence(int days, int minStrategies);

    /**
     * 截止指定交易日计算同向共振
     *
     * @param days          回看天数
     * @param minStrategies 最少策略数
     * @param asOfDate      截止日
     * @return 共振
     */
    SignalConfluenceResp confluence(int days, int minStrategies, LocalDate asOfDate);
}
