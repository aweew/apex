package com.awe.apex.core.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.common.api.Result;
import com.awe.apex.manager.domain.auth.dto.req.LoginReq;
import com.awe.apex.manager.domain.auth.dto.resp.LoginResp;
import com.awe.apex.manager.domain.auth.dto.resp.UserInfoResp;
import com.awe.apex.manager.service.IAuthStrategy;
import com.awe.apex.manager.service.IUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @author Awe
 * @since 2025/12/10 09:00
 */
@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    @Resource
    private IUserService userService;

    @Resource
    private IAuthStrategy authStrategy;

    @PostMapping("/login")
    @SaIgnore
    public Result<LoginResp> login(@RequestBody LoginReq loginReq) {
        return Result.success(authStrategy.login(loginReq));
    }

    /**
     * 根据id获取系统用户
     *
     * @return 单条数据
     */
    @GetMapping("/userInfo")
    public Result<UserInfoResp> getUserInfo() {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.success(this.userService.getUserInfo(userId));
    }

}

