package com.awe.apex.quant.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.dto.UserActivityReq;
import com.awe.apex.quant.domain.dto.UserUsageDetailResp;
import com.awe.apex.quant.domain.dto.UserUsageModuleResp;
import com.awe.apex.quant.domain.dto.UserUsageOverviewResp;
import com.awe.apex.quant.domain.dto.UserUsageTrendResp;
import com.awe.apex.quant.domain.entity.ApexUserActivity;
import com.awe.apex.quant.domain.enums.UserActivityTypeEnum;
import com.awe.apex.quant.domain.enums.UserUsageModuleEnum;
import com.awe.apex.quant.mapper.UserUsageMapper;
import com.awe.apex.quant.service.UserUsageService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 用户使用情况服务实现。
 */
@Service
public class UserUsageServiceImpl implements UserUsageService {

    @Resource
    private UserUsageMapper userUsageMapper;

    /**
     * 记录用户登录事件。
     *
     * @param userId 用户ID
     */
    @Override
    public void recordLogin(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        ApexUserActivity activity = ApexUserActivity.builder()
                .userId(userId)
                .activityType(UserActivityTypeEnum.LOGIN.getCode())
                .moduleCode(UserUsageModuleEnum.AUTH.getCode())
                .moduleName(UserUsageModuleEnum.AUTH.getDesc())
                .occurredAt(now)
                .createTime(now)
                .build();
        userUsageMapper.insert(activity);
    }

    /**
     * 记录当前用户页面访问事件。
     *
     * @param req 页面访问请求
     */
    @Override
    public void recordPageView(UserActivityReq req) {
        UserUsageModuleEnum module = UserUsageModuleEnum.findByCode(req.getModuleCode());
        if (Objects.isNull(module)) {
            throw new BusinessException("功能模块不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        ApexUserActivity activity = ApexUserActivity.builder()
                .userId(StpUtil.getLoginIdAsLong())
                .activityType(UserActivityTypeEnum.PAGE_VIEW.getCode())
                .moduleCode(module.getCode())
                .moduleName(module.getDesc())
                .occurredAt(now)
                .createTime(now)
                .build();
        userUsageMapper.insert(activity);
    }

    /**
     * 查询管理员用户使用情况总览。
     *
     * @param days 统计周期天数
     * @return 用户使用情况总览
     */
    @Override
    public UserUsageOverviewResp overview(int days) {
        if (days < 1 || days > 90) {
            throw new BusinessException("统计周期必须在1到90天之间");
        }

        // 1. 查询统计周期内的总览、趋势、功能排行与用户明细
        LocalDateTime generatedAt = LocalDateTime.now();
        LocalDate startDate = generatedAt.toLocalDate().minusDays(days - 1L);
        LocalDateTime startTime = startDate.atStartOfDay();
        UserUsageOverviewResp overview = userUsageMapper.selectOverview(startTime);
        if (Objects.isNull(overview)) {
            overview = UserUsageOverviewResp.builder()
                    .totalUsers(0L)
                    .enabledUsers(0L)
                    .activeUsers(0L)
                    .newUsers(0L)
                    .totalVisits(0L)
                    .build();
        }
        List<UserUsageTrendResp> sourceTrend = userUsageMapper.selectDailyTrend(startTime);
        List<UserUsageModuleResp> modules = userUsageMapper.selectModuleUsage(startTime);
        List<UserUsageDetailResp> users = userUsageMapper.selectUserUsage(startTime);

        // 2. 补齐无访问日期，保证趋势图的时间轴连续
        List<UserUsageTrendResp> trend = new ArrayList<>();
        for (int dayOffset = 0; dayOffset < days; dayOffset++) {
            LocalDate statisticsDate = startDate.plusDays(dayOffset);
            UserUsageTrendResp currentTrend = null;
            if (CollUtil.isNotEmpty(sourceTrend)) {
                for (UserUsageTrendResp trendItem : sourceTrend) {
                    if (Objects.equals(statisticsDate, trendItem.getDate())) {
                        currentTrend = trendItem;
                        break;
                    }
                }
            }
            if (Objects.isNull(currentTrend)) {
                currentTrend = UserUsageTrendResp.builder()
                        .date(statisticsDate)
                        .activeUsers(0L)
                        .visits(0L)
                        .build();
            }
            trend.add(currentTrend);
        }

        // 3. 计算活跃率与功能访问占比
        long totalUsers = Objects.nonNull(overview.getTotalUsers()) ? overview.getTotalUsers() : 0L;
        long activeUsers = Objects.nonNull(overview.getActiveUsers()) ? overview.getActiveUsers() : 0L;
        double activeRate = totalUsers > 0 ? Math.round(activeUsers * 1000.0 / totalUsers) / 10.0 : 0.0;
        long moduleVisits = 0L;
        if (CollUtil.isNotEmpty(modules)) {
            for (UserUsageModuleResp module : modules) {
                moduleVisits += Objects.nonNull(module.getVisits()) ? module.getVisits() : 0L;
            }
            for (UserUsageModuleResp module : modules) {
                long visits = Objects.nonNull(module.getVisits()) ? module.getVisits() : 0L;
                double visitRate = moduleVisits > 0 ? Math.round(visits * 1000.0 / moduleVisits) / 10.0 : 0.0;
                module.setVisitRate(visitRate);
            }
        }

        overview.setPeriodDays(days);
        overview.setGeneratedAt(generatedAt);
        overview.setActiveRate(activeRate);
        overview.setTrend(trend);
        overview.setModules(CollUtil.isNotEmpty(modules) ? modules : List.of());
        overview.setUsers(CollUtil.isNotEmpty(users) ? users : List.of());
        return overview;
    }
}
