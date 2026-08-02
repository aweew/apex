package com.awe.apex.quant.sync;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 同步任务健康色判定（可单测）
 */
public final class SyncTaskHealth {

    private SyncTaskHealth() {
    }

    /**
     * 判定健康等级
     *
     * @param running       是否运行中
     * @param lastSuccessAt 最近成功时间
     * @param latestFailed  最近任务是否失败
     * @param now           当前时间
     * @return GREEN/YELLOW/RED/RUNNING
     */
    public static String resolve(boolean running, LocalDateTime lastSuccessAt,
                                 boolean latestFailed, LocalDateTime now) {
        if (running) {
            return "RUNNING";
        }
        if (latestFailed) {
            return "RED";
        }
        if (Objects.isNull(lastSuccessAt)) {
            return "RED";
        }
        LocalDateTime base = Objects.nonNull(now) ? now : LocalDateTime.now();
        if (lastSuccessAt.isBefore(base.minusDays(7))) {
            return "RED";
        }
        if (lastSuccessAt.isBefore(base.minusDays(3))) {
            return "YELLOW";
        }
        return "GREEN";
    }
}
