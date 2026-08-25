package com.awe.apex.quant.sync;

import com.awe.apex.quant.domain.dto.SyncStartReq;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndexSyncTaskTest {

    @Test
    void registryKeepsTheDefaultFullIndexSyncArguments() {
        SyncTaskRegistry registry = new SyncTaskRegistry();
        SyncTaskSpec task = registry.require("INDEX");

        assertEquals(List.of(
                "--start", "20180101",
                "--sleep", "0.25"), registry.buildArgs(task, new SyncStartReq()));
    }

    @Test
    void registryPassesSelectedIndexCodesToTheScript() {
        SyncTaskRegistry registry = new SyncTaskRegistry();
        SyncTaskSpec task = registry.require("INDEX");
        SyncStartReq request = new SyncStartReq();
        request.setCodes("JP_N225,KR_KOSPI");
        request.setStart("20260818");

        assertEquals(List.of(
                "--codes", "JP_N225,KR_KOSPI",
                "--start", "20260818",
                "--sleep", "0.25"), registry.buildArgs(task, request));
    }
}
