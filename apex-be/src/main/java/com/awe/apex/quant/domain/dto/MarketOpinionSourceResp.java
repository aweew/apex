package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公开账号数据源状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketOpinionSourceResp {

    /** 账号或主体名称 */
    private String actorName;

    /** 公开平台 */
    private String platform;

    /** 已核验账号主页 */
    private String accountUrl;

    /** 来源状态 */
    private String sourceStatus;

    /** 状态说明 */
    private String sourceNote;
}
