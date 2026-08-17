package com.awe.apex.manager.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.common.constant.Constants;
import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.manager.domain.auth.dto.req.LoginReq;
import com.awe.apex.manager.domain.auth.dto.resp.LoginResp;
import com.awe.apex.manager.domain.user.entity.User;
import com.awe.apex.manager.service.IAuthStrategy;
import com.awe.apex.manager.service.IUserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @author Awe
 * @since 2025/12/11 13:29
 */
@Service("passwordAuthStrategy")
public class PasswordAuthStrategy implements IAuthStrategy {

    @Resource
    private IUserService userService;

    @Override
    public void validate(LoginReq loginReq) {

    }

    @Override
    public LoginResp login(LoginReq loginReq) {
        String phone = loginReq.getPhone();
        String password = loginReq.getPassword();

        User user = userService.getByPhone(phone);
        if (Objects.isNull(user)) {
            throw new RuntimeException("用户不存在");
        }
        // if (!BCrypt.checkpw(password, user.getPassword())) {
        //     throw new RuntimeException("密码错误");
        // }
        StpUtil.login(user.getId());
        StpUtil.getSession().set(Constants.PHONE, user.getPhone());

        return LoginResp.builder().accessToken(StpUtil.getTokenValue()).expireIn(StpUtil.getTokenTimeout()).build();
    }

}
