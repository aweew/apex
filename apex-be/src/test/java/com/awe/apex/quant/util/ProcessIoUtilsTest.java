package com.awe.apex.quant.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 进程输出 drain
 */
class ProcessIoUtilsTest {

    @Test
    void truncatesButDrainsAll() throws Exception {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            big.append("line-").append(i).append('\n');
        }
        String text = ProcessIoUtils.readAndDrain(
                new ByteArrayInputStream(big.toString().getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8,
                80);
        assertTrue(text.contains("truncated"));
        assertTrue(text.contains("line-0"));
    }

    @Test
    void waitOrKill_finishesQuickProcess() throws Exception {
        ProcessBuilder pb;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            pb = new ProcessBuilder("cmd", "/c", "echo", "ok");
        } else {
            pb = new ProcessBuilder("echo", "ok");
        }
        Process process = pb.start();
        ProcessIoUtils.readAndDrain(process.getInputStream(), StandardCharsets.UTF_8, 1000);
        assertTrue(ProcessIoUtils.waitOrKill(process, 10));
        assertEquals(0, process.exitValue());
    }

    @Test
    void destroyProcessTree_stopsParentAndChild() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return;
        }
        Process process = new ProcessBuilder("sh", "-c", "sleep 30 & wait").start();
        Thread.sleep(100);

        ProcessIoUtils.destroyProcessTree(process);

        assertTrue(process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(process.toHandle().descendants().noneMatch(ProcessHandle::isAlive));
    }
}
