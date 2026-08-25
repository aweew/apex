package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A 股集合竞价确认状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpeningAuctionResp {

    /**
     * 状态编码：WAITING/AUCTION/CONFIRMED/CLOSED/UNAVAILABLE。
     */
    private String state;

    /**
     * 面向页面的状态说明。
     */
    private String stateDesc;

    /**
     * 是否已读取到有效竞价报价。
     */
    private boolean available;

    /**
     * 本次状态计算时间。
     */
    private LocalDateTime asOf;

    /**
     * 沪深300与创业板指竞价报价。
     */
    private List<OpeningAuctionIndexResp> indexes;
}
