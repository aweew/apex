package com.awe.apex.quant.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员用户列表项
 */
@Data
@Builder
public class ApexAdminUserResp {

    /** 用户ID */
    private Long id;

    /** 手机号 */
    private String phone;

    /** 昵称 */
    private String nickName;

    /** 角色 */
    private String role;

    /** 是否启用 */
    private Boolean enabled;

    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;
}
