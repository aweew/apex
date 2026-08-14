package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 一次性重置密码请求
 */
@Data
public class ApexPasswordResetReq {

    /** 重置令牌 */
    @NotBlank(message = "重置令牌不能为空")
    private String token;

    /** 新密码 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度须为8到64位")
    private String newPassword;
}
