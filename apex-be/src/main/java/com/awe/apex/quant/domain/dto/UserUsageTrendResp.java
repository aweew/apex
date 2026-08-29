package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 用户使用趋势。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUsageTrendResp {

    /** 统计日期 */
    private LocalDate date;

    /** 活跃用户数 */
    private Long activeUsers;

    /** 访问次数 */
    private Long visits;
}
