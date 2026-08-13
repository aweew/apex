package com.awe.apex.quant.decision;

/**
 * 决策发布的数据就绪门禁。
 */
public final class DecisionDataReadiness {

    private DecisionDataReadiness() {
    }

    /**
     * 判断当前市场数据是否允许发布正式决策。
     *
     * @param dataLevel 市场简报数据等级
     * @return 是否允许发布
     */
    public static boolean canPublish(String dataLevel) {
        return "GREEN".equals(dataLevel) || "YELLOW".equals(dataLevel);
    }
}
