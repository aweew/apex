package com.awe.apex.manager.domain.userRole.convert;

import com.awe.apex.manager.domain.userRole.entity.UserRole;
import com.awe.apex.manager.domain.userRole.dto.req.UserRoleAddReq;
import com.awe.apex.manager.domain.userRole.dto.req.UserRoleUpdateReq;
import com.awe.apex.manager.domain.userRole.dto.req.UserRoleReq;
import com.awe.apex.manager.domain.userRole.dto.resp.UserRoleResp;
import com.awe.apex.common.convert.BaseConvert;
import org.mapstruct.Mapper;

/**
 * 系统用户角色转换器
 *
 * @author Awe
 * @since 2025-12-11 16:45:02
 */
@Mapper(componentModel = "spring")
public interface UserRoleConvert extends BaseConvert<UserRole, UserRoleAddReq, UserRoleUpdateReq, UserRoleReq, UserRoleResp> {

}
