package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.BacktestTrade;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 回测成交 Mapper
 */
@Mapper
public interface BacktestTradeMapper extends BaseMapper<BacktestTrade> {
}
