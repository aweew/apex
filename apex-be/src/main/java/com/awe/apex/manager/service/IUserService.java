package com.awe.apex.manager.service;

import com.awe.apex.common.api.Result;
import com.awe.apex.manager.domain.auth.dto.resp.UserInfoResp;
import com.awe.apex.manager.domain.user.dto.resp.UserResp;
import com.baomidou.mybatisplus.extension.service.IService;
import com.awe.apex.manager.domain.user.entity.User;

/**
 * 系统用户服务接口
 *
 * @author Awe
 * @since 2025-12-10 16:03:20
 */
public interface IUserService extends IService<User> {

    User getByPhone(String phone);

    UserInfoResp getUserInfo(Long userId);

}
