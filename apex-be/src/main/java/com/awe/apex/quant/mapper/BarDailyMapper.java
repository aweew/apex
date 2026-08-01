package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.BarDaily;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日线行情 Mapper
 */
@Mapper
public interface BarDailyMapper extends BaseMapper<BarDaily> {
}
