package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.dto.UserUsageDetailResp;
import com.awe.apex.quant.domain.dto.UserUsageModuleResp;
import com.awe.apex.quant.domain.dto.UserUsageOverviewResp;
import com.awe.apex.quant.domain.dto.UserUsageTrendResp;
import com.awe.apex.quant.domain.entity.ApexUserActivity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户使用情况数据库访问层。
 */
public interface UserUsageMapper extends BaseMapper<ApexUserActivity> {

    /**
     * 查询统计周期总览指标。
     *
     * @param startTime 统计开始时间
     * @return 总览指标
     */
    @Select("""
            SELECT COUNT(DISTINCT t1.id) AS totalUsers,
                   COUNT(DISTINCT CASE WHEN t1.status = 1 THEN t1.id END) AS enabledUsers,
                   COUNT(DISTINCT CASE WHEN t2.id IS NOT NULL THEN t1.id END) AS activeUsers,
                   COUNT(DISTINCT CASE WHEN t1.register_time >= #{startTime} THEN t1.id END) AS newUsers,
                   COUNT(t2.id) AS totalVisits
            FROM sys_user t1
            LEFT JOIN apex_user_activity t2 ON t2.user_id = t1.id
                                                AND t2.occurred_at >= #{startTime}
            WHERE t1.is_delete = 0
            """)
    UserUsageOverviewResp selectOverview(@Param("startTime") LocalDateTime startTime);

    /**
     * 查询每日用户使用趋势。
     *
     * @param startTime 统计开始时间
     * @return 每日趋势
     */
    @Select("""
            SELECT DATE(t1.occurred_at) AS date,
                   COUNT(DISTINCT t1.user_id) AS activeUsers,
                   COUNT(t1.id) AS visits
            FROM apex_user_activity t1
            WHERE t1.occurred_at >= #{startTime}
            GROUP BY DATE(t1.occurred_at)
            ORDER BY date ASC
            """)
    List<UserUsageTrendResp> selectDailyTrend(@Param("startTime") LocalDateTime startTime);

    /**
     * 查询功能模块使用排行。
     *
     * @param startTime 统计开始时间
     * @return 功能模块排行
     */
    @Select("""
            SELECT t1.module_code AS moduleCode,
                   t1.module_name AS moduleName,
                   COUNT(DISTINCT t1.user_id) AS activeUsers,
                   COUNT(t1.id) AS visits
            FROM apex_user_activity t1
            WHERE t1.occurred_at >= #{startTime}
              AND t1.activity_type = 'PAGE_VIEW'
            GROUP BY t1.module_code, t1.module_name
            ORDER BY visits DESC, t1.module_code ASC
            """)
    List<UserUsageModuleResp> selectModuleUsage(@Param("startTime") LocalDateTime startTime);

    /**
     * 查询逐用户使用明细。
     *
     * @param startTime 统计开始时间
     * @return 用户使用明细
     */
    @Select("""
            SELECT t1.id AS userId,
                   t1.phone,
                   t1.nick_name AS nickName,
                   t2.role,
                   CASE WHEN t1.status = 1 THEN TRUE ELSE FALSE END AS enabled,
                   t1.register_time AS registerTime,
                   t1.last_login_time AS lastLoginTime,
                   COUNT(t3.id) AS visits,
                   COUNT(DISTINCT DATE(t3.occurred_at)) AS activeDays,
                   MAX(t3.occurred_at) AS lastActiveTime
            FROM sys_user t1
            LEFT JOIN apex_user_profile t2 ON t2.user_id = t1.id
            LEFT JOIN apex_user_activity t3 ON t3.user_id = t1.id
                                                AND t3.occurred_at >= #{startTime}
            WHERE t1.is_delete = 0
            GROUP BY t1.id, t1.phone, t1.nick_name, t2.role, t1.status,
                     t1.register_time, t1.last_login_time
            ORDER BY lastActiveTime IS NULL ASC, lastActiveTime DESC, t1.register_time DESC
            """)
    List<UserUsageDetailResp> selectUserUsage(@Param("startTime") LocalDateTime startTime);
}
