package com.awe.apex.manager.domain.roleMenu.convert;

import com.awe.apex.manager.domain.roleMenu.entity.RoleMenu;
import com.awe.apex.manager.domain.roleMenu.dto.req.RoleMenuAddReq;
import com.awe.apex.manager.domain.roleMenu.dto.req.RoleMenuUpdateReq;
import com.awe.apex.manager.domain.roleMenu.dto.req.RoleMenuReq;
import com.awe.apex.manager.domain.roleMenu.dto.resp.RoleMenuResp;
import com.awe.apex.common.convert.BaseConvert;
import org.mapstruct.Mapper;

/**
 * 系统角色权限转换器
 *
 * @author Awe
 * @since 2025-12-11 16:45:00
 */
@Mapper(componentModel = "spring")
public interface RoleMenuConvert extends BaseConvert<RoleMenu, RoleMenuAddReq, RoleMenuUpdateReq, RoleMenuReq, RoleMenuResp> {

}
