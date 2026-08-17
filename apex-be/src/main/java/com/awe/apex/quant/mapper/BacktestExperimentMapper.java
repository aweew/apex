package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.BacktestExperiment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 回测实验 Mapper
 */
@Mapper
public interface BacktestExperimentMapper extends BaseMapper<BacktestExperiment> {
}
