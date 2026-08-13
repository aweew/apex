package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 交易事件入库结果。 */
@Data
@AllArgsConstructor
public class TradeEventIngestResp {
    /** 交易事件ID */ private Long tradeEventId;
    /** 事件状态 */ private String status;
}
