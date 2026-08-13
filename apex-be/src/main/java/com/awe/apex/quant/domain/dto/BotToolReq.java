package com.awe.apex.quant.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Bot 结构化工具请求。
 */
@Data
public class BotToolReq {

    /** 工具名称：PORTFOLIO_ADVICE / PORTFOLIO_STATUS / HOLDING_IMPORT */
    @NotBlank(message = "工具名称不能为空")
    private String operation;

    /** 微信用户标识 */
    @NotBlank(message = "微信用户不能为空")
    private String userId;

    /** 微信会话标识 */
    @NotBlank(message = "微信会话不能为空")
    private String conversationId;

    /** 调用方请求号 */
    private String requestId;

    /** 组合名称 */
    private String portfolioName;

    /** Smart Trader交易者ID */
    private Long traderId;

    /** Smart Trader排名类型 */
    private String rankingType;

    /** 截图解析出的持仓 */
    @Valid
    private List<BotHoldingInput> holdings = new ArrayList<>();

    /** 截图中的证券市值，用于校验 */
    private BigDecimal totalMarketValue;

}
