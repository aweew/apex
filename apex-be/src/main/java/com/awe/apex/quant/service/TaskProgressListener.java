package com.awe.apex.quant.service;

/**
 * 长任务进度监听器
 */
@FunctionalInterface
public interface TaskProgressListener {

    /**
     * 上报任务进度
     *
     * @param completed 已完成数量
     * @param total     总数量
     * @param message   当前阶段说明
     */
    void onProgress(int completed, int total, String message);
}
