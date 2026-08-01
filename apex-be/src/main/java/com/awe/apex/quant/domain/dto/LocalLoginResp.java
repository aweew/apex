package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本地登录响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalLoginResp {

    /**
     * token
     */
    private String accessToken;

    /**
     * 过期秒数
     */
    private Long expireIn;
}
