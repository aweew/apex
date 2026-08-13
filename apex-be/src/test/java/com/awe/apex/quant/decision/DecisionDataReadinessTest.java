package com.awe.apex.quant.decision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 决策数据就绪门禁
 */
class DecisionDataReadinessTest {

    @Test
    void allowsYellowDataButBlocksRedData() {
        assertTrue(DecisionDataReadiness.canPublish("GREEN"));
        assertTrue(DecisionDataReadiness.canPublish("YELLOW"));
        assertFalse(DecisionDataReadiness.canPublish("RED"));
        assertFalse(DecisionDataReadiness.canPublish(null));
    }
}
