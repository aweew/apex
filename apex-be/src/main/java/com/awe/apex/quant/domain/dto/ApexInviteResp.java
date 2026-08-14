package com.awe.apex.quant.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邀请创建响应
 */
@Data
@Builder
public class ApexInviteResp {

    /** 邀请令牌 */
    private String token;

    /** 失效时间 */
    private LocalDateTime expireTime;
}
