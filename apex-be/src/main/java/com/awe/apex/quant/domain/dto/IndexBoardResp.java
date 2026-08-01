package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 大盘分市场看板
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexBoardResp {

    /**
     * A股
     */
    private List<IndexQuoteItem> cn;

    /**
     * 港股
     */
    private List<IndexQuoteItem> hk;

    /**
     * 日韩
     */
    private List<IndexQuoteItem> asia;

    /**
     * 美国
     */
    private List<IndexQuoteItem> us;

    /**
     * 说明
     */
    private String message;
}
