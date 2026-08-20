package com.awe.apex.quant.sync;

import com.awe.apex.quant.domain.dto.SyncStartReq;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NightlyRepairSyncTaskTest {

    @Test
    void registryExposesNightlyRepairTaskAndBuildsArguments() {
        SyncTaskRegistry registry = new SyncTaskRegistry();
        SyncTaskSpec task = registry.require("NIGHTLY_REPAIR");
        SyncStartReq request = new SyncStartReq();
        request.setExpectedDate("2026-08-17");
        request.setStart("20240101");
        request.setBatch(60);
        request.setRounds(8);

        List<String> arguments = registry.buildArgs(task, request);

        assertEquals("凌晨数据补缺", task.getName());
        assertEquals("sync_nightly_repair.py", task.getScriptFile());
        assertTrue(arguments.containsAll(List.of(
                "--expected-date", "2026-08-17",
                "--start", "20240101",
                "--bars-batch", "60",
                "--bars-rounds", "8")));
    }

    @Test
    void registryBuildsContinuousBoundedNightlyRepairDefaults() {
        SyncTaskRegistry registry = new SyncTaskRegistry();
        SyncTaskSpec task = registry.require("NIGHTLY_REPAIR");

        List<String> arguments = registry.buildArgs(task, new SyncStartReq());

        assertTrue(arguments.containsAll(List.of(
                "--bars-rounds", "0",
                "--bars-max-minutes", "150")));
        assertTrue(task.getDefaultParamsHint().contains("150 分钟"));
    }
}
