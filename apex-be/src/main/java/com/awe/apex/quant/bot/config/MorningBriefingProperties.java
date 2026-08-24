package com.awe.apex.quant.bot.config;

import lombok.Data;

/**
 * Bot 盘前晨报配置。
 */
@Data
public class MorningBriefingProperties {

    private static final String DEFAULT_INDEX_SYMBOLS = "usIXIC,usDJI,usINX";
    private static final String DEFAULT_ASIA_INDEX_SYMBOLS = "hkHSI,hkHSTECH";
    private static final String DEFAULT_STAR_SYMBOLS = "usMSFT,usAAPL,usAMZN,usGOOG,usMETA,usTSLA,usSPCX,"
            + "usNVDA,usAMD,usAVGO,usARM,usMRVL,usMU,usSKHY,usSNDK,usWDC,usSTX,"
            + "usTSM,usGFS,usASML,usAMAT,usLRCX,usKLAC,usSNPS,usCDNS,usQCOM,usINTC,"
            + "usTXN,usADI,usNXPI,usON,usBABA,usPDD";

    /**
     * 是否启用盘前晨报任务。
     */
    private boolean enabled = true;

    /**
     * 腾讯行情符号，逗号分隔，按展示顺序排列。
     */
    private String symbols = DEFAULT_INDEX_SYMBOLS + "," + DEFAULT_ASIA_INDEX_SYMBOLS + "," + DEFAULT_STAR_SYMBOLS;

    /**
     * 市场指数符号，逗号分隔。
     */
    private String indexSymbols = DEFAULT_INDEX_SYMBOLS;

    /**
     * 亚太市场指数符号，逗号分隔。
     */
    private String asiaIndexSymbols = DEFAULT_ASIA_INDEX_SYMBOLS;

    /**
     * 明星股观察池符号，逗号分隔。
     */
    private String starSymbols = DEFAULT_STAR_SYMBOLS;

    /**
     * 明星异动最大展示数量。
     */
    private int starQuoteLimit = 8;

    /**
     * 科技巨头主题符号，逗号分隔。
     */
    private String technologyGiantsSymbols = "usMSFT,usAAPL,usAMZN,usGOOG,usMETA,usTSLA,usSPCX";

    /**
     * AI 芯片主题符号，逗号分隔。
     */
    private String aiChipSymbols = "usNVDA,usAMD,usAVGO,usARM,usMRVL";

    /**
     * 存储主题符号，逗号分隔。
     */
    private String storageSymbols = "usMU,usSKHY,usSNDK,usWDC,usSTX";

    /**
     * 晶圆制造主题符号，逗号分隔。
     */
    private String waferManufacturingSymbols = "usTSM,usGFS";

    /**
     * 半导体设备主题符号，逗号分隔。
     */
    private String semiconductorEquipmentSymbols = "usASML,usAMAT,usLRCX,usKLAC";

    /**
     * EDA 与 IP 主题符号，逗号分隔。
     */
    private String edaIpSymbols = "usSNPS,usCDNS";

    /**
     * 模拟与汽车芯片主题符号，逗号分隔。
     */
    private String analogAutomotiveChipSymbols = "usQCOM,usINTC,usTXN,usADI,usNXPI,usON";

    /**
     * 中概风向主题符号，逗号分隔。
     */
    private String chinaConceptSymbols = "usBABA,usPDD";
}
