package com.awe.apex.quant.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

/**
 * 子进程 IO：截断日志的同时 drain，避免管道堵死
 */
public final class ProcessIoUtils {

    private ProcessIoUtils() {
    }

    /**
     * 读取进程输出，超过 maxChars 后仍继续消费至 EOF，但不再追加
     *
     * @param in       输入流
     * @param charset  字符集
     * @param maxChars 保留上限
     * @return 截断后的文本
     */
    public static String readAndDrain(InputStream in, Charset charset, int maxChars) throws Exception {
        StringBuilder out = new StringBuilder();
        boolean truncated = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!truncated) {
                    out.append(line).append('\n');
                    if (out.length() > maxChars) {
                        truncated = true;
                        out.append("\n...[output truncated]...\n");
                    }
                }
            }
        }
        return out.toString();
    }

    /**
     * 等待进程结束，超时则强杀
     *
     * @param process    进程
     * @param timeoutSec 秒
     * @return 是否在超时前结束
     */
    public static boolean waitOrKill(Process process, long timeoutSec) throws InterruptedException {
        boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        return finished;
    }
}
