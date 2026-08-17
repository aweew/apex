package com.awe.apex.quant.context;

import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Apex 当前用户上下文，支持请求线程和显式后台任务。
 */
@Component
public class ApexUserContext {

    private final ThreadLocal<Long> backgroundUserId = new ThreadLocal<>();

    /**
     * 获取当前用户ID。
     *
     * @return 当前用户ID
     */
    public Long currentUserId() {
        Long userId = backgroundUserId.get();
        return Objects.nonNull(userId) ? userId : StpUtil.getLoginIdAsLong();
    }

    /**
     * 获取可空的当前用户ID。
     *
     * @return 当前用户ID，未登录时返回空
     */
    public Long currentUserIdOrNull() {
        Long userId = backgroundUserId.get();
        if (Objects.nonNull(userId)) {
            return userId;
        }
        return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
    }

    /**
     * 使用指定用户身份执行后台任务，并在结束后恢复原上下文。
     *
     * @param userId 用户ID
     * @param task   后台任务
     */
    public void runAsUser(Long userId, Runnable task) {
        if (Objects.isNull(task)) {
            throw new BusinessException("后台任务不能为空");
        }
        runAsUser(userId, () -> {
            task.run();
            return null;
        });
    }

    /**
     * 使用指定用户身份执行后台任务并返回结果，结束后恢复原上下文。
     *
     * @param userId 用户ID
     * @param task   后台任务
     * @param <T>    返回值类型
     * @return 后台任务结果
     */
    public <T> T runAsUser(Long userId, Supplier<T> task) {
        if (Objects.isNull(userId)) {
            throw new BusinessException("后台任务缺少用户ID");
        }
        if (Objects.isNull(task)) {
            throw new BusinessException("后台任务不能为空");
        }
        Long previousUserId = backgroundUserId.get();
        backgroundUserId.set(userId);
        try {
            return task.get();
        } finally {
            if (Objects.nonNull(previousUserId)) {
                backgroundUserId.set(previousUserId);
            } else {
                backgroundUserId.remove();
            }
        }
    }
}
