package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 本地登录
 */
@Data
public class LocalLoginReq {

    /**
     * 用户名
     */
    @NotBlank
    private String username;

    /**
     * 密码
     */
    @NotBlank
    private String password;
}
