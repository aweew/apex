package com.awe.apex.quant.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.common.api.Result;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.config.ApexProperties;
import com.awe.apex.quant.domain.dto.LocalLoginReq;
import com.awe.apex.quant.domain.dto.LocalLoginResp;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本地单用户登录
 */
@RestController
@RequestMapping("/api/auth")
public class LocalAuthController {

    @Resource
    private ApexProperties apexProperties;

    /**
     * 本地登录
     */
    @PostMapping("/local-login")
    public Result<LocalLoginResp> localLogin(@Valid @RequestBody LocalLoginReq req) {
        String username = apexProperties.getLocalUsername();
        String password = apexProperties.getLocalPassword();
        if (!username.equals(req.getUsername()) || !password.equals(req.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        StpUtil.login(1L);
        return Result.success(LocalLoginResp.builder()
                .accessToken(StpUtil.getTokenValue())
                .expireIn(StpUtil.getTokenTimeout())
                .build());
    }
}
