package com.awe.apex.quant.domain.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Apex 登录响应
 */
@Data
@Builder
public class ApexLoginResp {

    /** Bearer 令牌 */
    private String accessToken;

    /** 令牌有效期秒数 */
    private Long expireIn;

    /** 当前用户 */
    private ApexUserResp user;
}
