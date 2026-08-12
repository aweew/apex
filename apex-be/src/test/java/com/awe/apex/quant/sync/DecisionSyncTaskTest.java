package com.awe.apex.quant.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DecisionSyncTaskTest {

    @Test
    void registryExposesDecisionAsBackendTask() {
        SyncTaskSpec task = new SyncTaskRegistry().require("DECISION");

        assertEquals("智能决策", task.getName());
        assertEquals("决策任务", task.getGroupName());
        assertNull(task.getScriptFile());
    }
}
