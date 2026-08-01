package com.awe.apex.manager.domain.menu.convert;

import com.awe.apex.manager.domain.menu.entity.Menu;
import com.awe.apex.manager.domain.menu.dto.req.MenuAddReq;
import com.awe.apex.manager.domain.menu.dto.req.MenuUpdateReq;
import com.awe.apex.manager.domain.menu.dto.req.MenuReq;
import com.awe.apex.manager.domain.menu.dto.resp.MenuResp;
import com.awe.apex.common.convert.BaseConvert;
import org.mapstruct.Mapper;

/**
 * 系统权限转换器
 *
 * @author Awe
 * @since 2025-12-11 16:45:01
 */
@Mapper(componentModel = "spring")
public interface MenuConvert extends BaseConvert<Menu, MenuAddReq, MenuUpdateReq, MenuReq, MenuResp> {

}
