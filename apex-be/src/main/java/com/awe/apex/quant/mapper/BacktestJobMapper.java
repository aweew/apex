package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.BacktestJob;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 回测任务 Mapper
 */
@Mapper
public interface BacktestJobMapper extends BaseMapper<BacktestJob> {
}
