package com.awe.apex.manager.domain.user.convert;

import com.awe.apex.manager.domain.user.entity.User;
import com.awe.apex.manager.domain.user.dto.req.UserAddReq;
import com.awe.apex.manager.domain.user.dto.req.UserUpdateReq;
import com.awe.apex.manager.domain.user.dto.req.UserReq;
import com.awe.apex.manager.domain.user.dto.resp.UserResp;
import com.awe.apex.common.convert.BaseConvert;
import org.mapstruct.Mapper;

/**
 * 系统用户转换器
 *
 * @author Awe
 * @since 2025-12-10 16:03:20
 */
@Mapper(componentModel = "spring")
public interface UserConvert extends BaseConvert<User, UserAddReq, UserUpdateReq, UserReq, UserResp> {

}
