package com.awe.apex.manager.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.awe.apex.manager.domain.area.entity.Area;
import com.awe.apex.manager.mapper.AreaMapper;
import com.awe.apex.manager.service.IAreaService;
import org.springframework.stereotype.Service;

/**
 * 区域服务实现类
 *
 * @author Awe
 * @since 2025-09-09 15:09:22
 */
@Service("areaService")
public class AreaServiceImpl extends ServiceImpl<AreaMapper, Area> implements IAreaService {

}
