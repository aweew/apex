package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.LocalLoginReq;
import com.awe.apex.quant.domain.dto.LocalLoginResp;
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

    /**
     * 本地登录
     */
    @PostMapping("/local-login")
    public Result<LocalLoginResp> localLogin(@Valid @RequestBody LocalLoginReq req) {
        throw new BusinessException("本地单用户登录已下线，请使用手机号登录");
    }
}
