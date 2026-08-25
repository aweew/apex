package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.MarketBreadthForecast;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 盘前涨跌比预测快照数据访问。
 */
@Mapper
public interface MarketBreadthForecastMapper extends BaseMapper<MarketBreadthForecast> {
}
