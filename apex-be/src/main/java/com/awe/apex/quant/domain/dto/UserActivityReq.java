package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户页面访问事件请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityReq {

    /** 功能模块编码 */
    @NotBlank(message = "功能模块不能为空")
    @Size(max = 64, message = "功能模块长度不能超过64个字符")
    private String moduleCode;
}
