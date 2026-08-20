package com.awe.apex.quant.sync;

import com.awe.apex.quant.domain.dto.SyncStartReq;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CapitalFlowSyncTaskTest {

    @Test
    void registryExposesFlowAndDragonTigerModes() {
        SyncTaskRegistry registry = new SyncTaskRegistry();

        SyncTaskSpec flowTask = registry.require("CAPITAL_FLOW");
        SyncTaskSpec dragonTigerTask = registry.require("DRAGON_TIGER");

        assertEquals("sync_capital_flow.py", flowTask.getScriptFile());
        assertEquals(List.of("--mode", "flow"), registry.buildArgs(flowTask, new SyncStartReq()));
        SyncStartReq intradayRequest = new SyncStartReq();
        intradayRequest.setMode("stock");
        assertEquals(List.of("--mode", "stock"), registry.buildArgs(flowTask, intradayRequest));
        assertEquals("sync_capital_flow.py", dragonTigerTask.getScriptFile());
        assertEquals(List.of("--mode", "lhb"), registry.buildArgs(dragonTigerTask, new SyncStartReq()));
    }
}
