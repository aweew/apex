package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 选股数据缺失或失败摘要
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenerDataIssueResp {

    /** 数据阶段 */
    private String stage;

    /** 问题类型 */
    private String issueType;

    /** 受影响股票数量 */
    private Integer count;

    /** 问题说明 */
    private String message;
}
