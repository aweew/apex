package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Apex 手机号登录请求
 */
@Data
public class ApexLoginReq {

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
