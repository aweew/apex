package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.SyncJob;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 同步任务 Mapper
 */
@Mapper
public interface SyncJobMapper extends BaseMapper<SyncJob> {
}
