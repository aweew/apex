package com.awe.apex.quant.domain.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Apex 当前用户信息
 */
@Data
@Builder
public class ApexUserResp {

    /** 用户ID */
    private Long id;

    /** 手机号 */
    private String phone;

    /** 昵称 */
    private String nickName;

    /** 角色 */
    private String role;

    /** 模拟账户ID */
    private Long paperAccountId;
}
