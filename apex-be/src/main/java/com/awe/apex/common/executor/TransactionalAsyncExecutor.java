package com.awe.apex.common.executor;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;

/**
 * 异步线程执行器
 * 用于解决主线程未提交事务时，异步线程无法获取到主线程事务上下文的问题。
 *
 * @author Awe
 * @since 2025/7/30 13:22
 */
public class TransactionalAsyncExecutor {

    public static void runAfterCommit(Runnable task, Executor executor) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(task);
                }
            });
        } else {
            // 无事务则直接执行
            executor.execute(task);
        }
    }

}
