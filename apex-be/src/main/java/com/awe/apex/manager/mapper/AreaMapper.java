package com.awe.apex.manager.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.awe.apex.manager.domain.area.entity.Area;
import org.apache.ibatis.annotations.Mapper;

/**
 * 区域数据库访问层
 *
 * @author Awe
 * @since 2025-09-09 15:09:22
 */
@Mapper
public interface AreaMapper extends BaseMapper<Area> {

}
