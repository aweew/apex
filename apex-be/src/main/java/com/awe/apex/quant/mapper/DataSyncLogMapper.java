package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.DataSyncLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据同步日志 Mapper
 */
@Mapper
public interface DataSyncLogMapper extends BaseMapper<DataSyncLog> {
}
