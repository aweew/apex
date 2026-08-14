package com.awe.apex.quant.bot.config;

import lombok.Data;

/**
 * Bot 盘前晨报配置。
 */
@Data
public class MorningBriefingProperties {

    /**
     * 是否启用盘前晨报任务。
     */
    private boolean enabled = true;

    /**
     * 腾讯行情符号，逗号分隔，按展示顺序排列。
     */
    private String symbols = "usIXIC,usDJI,usINX,usNVDA,usAAPL,usTSLA,usBABA,usPDD";
}
