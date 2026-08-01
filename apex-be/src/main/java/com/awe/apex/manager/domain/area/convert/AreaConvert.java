package com.awe.apex.manager.domain.area.convert;

import com.awe.apex.common.convert.BaseConvert;
import com.awe.apex.manager.domain.area.dto.AreaAddReq;
import com.awe.apex.manager.domain.area.dto.AreaReq;
import com.awe.apex.manager.domain.area.dto.AreaResp;
import com.awe.apex.manager.domain.area.dto.AreaUpdateReq;
import com.awe.apex.manager.domain.area.entity.Area;
import org.mapstruct.Mapper;

/**
 * 区域转换器
 *
 * @author Awe
 * @since 2025/12/8 16:54
 */
@Mapper(componentModel = "spring")
public interface AreaConvert extends BaseConvert<Area, AreaAddReq, AreaUpdateReq, AreaReq, AreaResp> {

}
