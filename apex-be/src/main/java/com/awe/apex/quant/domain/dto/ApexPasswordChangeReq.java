package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 密码变更请求
 */
@Data
public class ApexPasswordChangeReq {

    /** 当前密码 */
    @NotBlank(message = "当前密码不能为空")
    private String currentPassword;

    /** 新密码 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度须为8到64位")
    private String newPassword;
}
