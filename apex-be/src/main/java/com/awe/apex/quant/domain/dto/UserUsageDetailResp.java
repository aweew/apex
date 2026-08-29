package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单个用户使用明细。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUsageDetailResp {

    /** 用户ID */
    private Long userId;

    /** 手机号 */
    private String phone;

    /** 用户昵称 */
    private String nickName;

    /** 用户角色 */
    private String role;

    /** 是否启用 */
    private Boolean enabled;

    /** 注册时间 */
    private LocalDateTime registerTime;

    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;

    /** 统计期内访问次数 */
    private Long visits;

    /** 统计期内活跃天数 */
    private Long activeDays;

    /** 最近活跃时间 */
    private LocalDateTime lastActiveTime;
}
