package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 邀请注册请求
 */
@Data
public class ApexInviteRegisterReq {

    /** 邀请令牌 */
    @NotBlank(message = "邀请令牌不能为空")
    private String token;

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    /** 昵称 */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 32, message = "昵称不能超过32个字符")
    private String nickName;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度须为8到64位")
    private String password;
}
