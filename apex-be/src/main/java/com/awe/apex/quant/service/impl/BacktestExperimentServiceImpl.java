package com.awe.apex.quant.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.quant.domain.dto.BacktestExperimentDetailResp;
import com.awe.apex.quant.domain.dto.BacktestExperimentListResp;
import com.awe.apex.quant.domain.dto.RollingBacktestReq;
import com.awe.apex.quant.domain.dto.RollingBacktestResp;
import com.awe.apex.quant.domain.entity.BacktestExperiment;
import com.awe.apex.quant.mapper.BacktestExperimentMapper;
import com.awe.apex.quant.service.IBacktestExperimentService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 回测实验历史服务实现
 */
@Service
public class BacktestExperimentServiceImpl implements IBacktestExperimentService {

    @Resource
    private BacktestExperimentMapper backtestExperimentMapper;

    /**
     * 保存实验快照
     *
     * @param request 实际请求
     * @param result  完整结果
     * @return 实验ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(RollingBacktestReq request, RollingBacktestResp result) {
        if (Objects.isNull(request) || Objects.isNull(result)) {
            throw new BusinessException("回测实验快照不能为空");
        }
        if (Objects.isNull(result.getCost())) {
            throw new BusinessException("回测实验成本快照不能为空");
        }
        if (Objects.isNull(result.getInitCash()) || result.getInitCash().signum() <= 0) {
            throw new BusinessException("回测实验初始资金快照无效");
        }
        LocalDateTime currentTime = LocalDateTime.now();
        BacktestExperiment experiment = BacktestExperiment.builder()
                .userId(StpUtil.getLoginIdAsLong())
                .code(result.getCode())
                .strategyId(result.getStrategyId())
                .strategyName(result.getStrategyName())
                .strategyParameters(result.getStrategyParameters())
                .benchmarkCode(result.getBenchmarkCode())
                .windowMode(result.getWindowMode())
                .dataBeginDate(result.getDataBeginDate())
                .dataEndDate(result.getDataEndDate())
                .outSampleBeginDate(result.getOutSampleBeginDate())
                .outSampleEndDate(result.getOutSampleEndDate())
                .trainDays(result.getTrainDays())
                .testDays(result.getTestDays())
                .stepDays(result.getStepDays())
                .initCash(result.getInitCash())
                .foldCount(result.getFoldCount())
                .compoundedOutSampleReturn(result.getCompoundedOutSampleReturn())
                .compoundedBenchmarkReturn(result.getCompoundedBenchmarkReturn())
                .compoundedExcessReturn(result.getCompoundedExcessReturn())
                .outSampleSharpe(result.getOutSampleSharpe())
                .worstOutSampleDrawdown(result.getWorstOutSampleDrawdown())
                .commissionRate(result.getCost().getCommissionRate())
                .stampTaxRate(result.getCost().getStampTaxRate())
                .buySlippage(result.getCost().getBuySlippage())
                .sellSlippage(result.getCost().getSellSlippage())
                .executionModelVersion(result.getExecutionModelVersion())
                .priceAdjustment(result.getPriceAdjustment())
                .dataFingerprint(result.getDataFingerprint())
                .requestJson(JsonUtils.toJsonString(request))
                .resultJson(JsonUtils.toJsonString(result))
                .createTime(currentTime)
                .updateTime(currentTime)
                .deleted(0)
                .build();
        int insertedCount = backtestExperimentMapper.insert(experiment);
        if (insertedCount != 1 || Objects.isNull(experiment.getId())) {
            throw new BusinessException("回测实验保存失败");
        }
        return experiment.getId();
    }

    /**
     * 查询最近实验
     *
     * @param limit 条数
     * @return 实验摘要
     */
    @Override
    public List<BacktestExperimentListResp> list(Integer limit) {
        int querySize = Objects.isNull(limit) ? 20 : Math.max(1, Math.min(limit, 100));
        List<BacktestExperiment> experiments = backtestExperimentMapper.selectList(
                Wrappers.<BacktestExperiment>lambdaQuery()
                        .select(BacktestExperiment::getId,
                                BacktestExperiment::getCode,
                                BacktestExperiment::getStrategyId,
                                BacktestExperiment::getStrategyName,
                                BacktestExperiment::getStrategyParameters,
                                BacktestExperiment::getBenchmarkCode,
                                BacktestExperiment::getWindowMode,
                                BacktestExperiment::getDataBeginDate,
                                BacktestExperiment::getDataEndDate,
                                BacktestExperiment::getOutSampleBeginDate,
                                BacktestExperiment::getOutSampleEndDate,
                                BacktestExperiment::getTrainDays,
                                BacktestExperiment::getTestDays,
                                BacktestExperiment::getStepDays,
                                BacktestExperiment::getInitCash,
                                BacktestExperiment::getFoldCount,
                                BacktestExperiment::getCompoundedOutSampleReturn,
                                BacktestExperiment::getCompoundedBenchmarkReturn,
                                BacktestExperiment::getCompoundedExcessReturn,
                                BacktestExperiment::getOutSampleSharpe,
                                BacktestExperiment::getWorstOutSampleDrawdown,
                                BacktestExperiment::getCommissionRate,
                                BacktestExperiment::getStampTaxRate,
                                BacktestExperiment::getBuySlippage,
                                BacktestExperiment::getSellSlippage,
                                BacktestExperiment::getExecutionModelVersion,
                                BacktestExperiment::getPriceAdjustment,
                                BacktestExperiment::getDataFingerprint,
                                BacktestExperiment::getCreateTime)
                        .eq(BacktestExperiment::getUserId, StpUtil.getLoginIdAsLong())
                        .orderByDesc(BacktestExperiment::getId)
                        .last("limit " + querySize));
        List<BacktestExperimentListResp> responses = new ArrayList<>();
        if (CollUtil.isEmpty(experiments)) {
            return responses;
        }
        for (BacktestExperiment experiment : experiments) {
            responses.add(BacktestExperimentListResp.builder()
                    .id(experiment.getId())
                    .code(experiment.getCode())
                    .strategyId(experiment.getStrategyId())
                    .strategyName(experiment.getStrategyName())
                    .strategyParameters(experiment.getStrategyParameters())
                    .benchmarkCode(experiment.getBenchmarkCode())
                    .windowMode(experiment.getWindowMode())
                    .dataBeginDate(experiment.getDataBeginDate())
                    .dataEndDate(experiment.getDataEndDate())
                    .outSampleBeginDate(experiment.getOutSampleBeginDate())
                    .outSampleEndDate(experiment.getOutSampleEndDate())
                    .trainDays(experiment.getTrainDays())
                    .testDays(experiment.getTestDays())
                    .stepDays(experiment.getStepDays())
                    .initCash(experiment.getInitCash())
                    .foldCount(experiment.getFoldCount())
                    .compoundedOutSampleReturn(experiment.getCompoundedOutSampleReturn())
                    .compoundedBenchmarkReturn(experiment.getCompoundedBenchmarkReturn())
                    .compoundedExcessReturn(experiment.getCompoundedExcessReturn())
                    .outSampleSharpe(experiment.getOutSampleSharpe())
                    .worstOutSampleDrawdown(experiment.getWorstOutSampleDrawdown())
                    .commissionRate(experiment.getCommissionRate())
                    .stampTaxRate(experiment.getStampTaxRate())
                    .buySlippage(experiment.getBuySlippage())
                    .sellSlippage(experiment.getSellSlippage())
                    .executionModelVersion(experiment.getExecutionModelVersion())
                    .priceAdjustment(experiment.getPriceAdjustment())
                    .dataFingerprint(experiment.getDataFingerprint())
                    .createTime(experiment.getCreateTime())
                    .build());
        }
        return responses;
    }

    /**
     * 查询实验详情
     *
     * @param id 实验ID
     * @return 实验详情
     */
    @Override
    public BacktestExperimentDetailResp detail(Long id) {
        BacktestExperiment experiment = requireOwnedExperiment(id);
        try {
            RollingBacktestReq request = JsonUtils.parseObject(experiment.getRequestJson(), RollingBacktestReq.class);
            RollingBacktestResp result = JsonUtils.parseObject(experiment.getResultJson(), RollingBacktestResp.class);
            if (Objects.isNull(request) || Objects.isNull(result)) {
                throw new BusinessException("回测实验快照损坏");
            }
            result.setExperimentId(experiment.getId());
            return BacktestExperimentDetailResp.builder()
                    .id(experiment.getId())
                    .request(request)
                    .result(result)
                    .createTime(experiment.getCreateTime())
                    .build();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException("回测实验快照损坏", exception);
        }
    }

    /**
     * 删除实验
     *
     * @param id 实验ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        BacktestExperiment experiment = requireOwnedExperiment(id);
        backtestExperimentMapper.deleteById(experiment.getId());
    }

    private BacktestExperiment requireOwnedExperiment(Long id) {
        if (Objects.isNull(id)) {
            throw new BusinessException("回测实验不存在");
        }
        BacktestExperiment experiment = backtestExperimentMapper.selectOne(
                Wrappers.<BacktestExperiment>lambdaQuery()
                        .eq(BacktestExperiment::getId, id)
                        .eq(BacktestExperiment::getUserId, StpUtil.getLoginIdAsLong())
                        .last("limit 1"));
        if (Objects.isNull(experiment)) {
            throw new BusinessException("回测实验不存在");
        }
        return experiment;
    }
}
