package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日线同步响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarSyncResp {

    /**
     * 数据来源
     */
    private String source;

    /**
     * 数据获取时间
     */
    private LocalDateTime fetchedAt;

    /**
     * 成功同步股票数
     */
    private Integer successCount;

    /**
     * 失败股票数
     */
    private Integer failCount;

    /**
     * 因总时限未执行的证券数量
     */
    private Integer deferredCount;

    /**
     * 写入/更新的 K 线条数
     */
    private Integer barCount;

    /**
     * 明细
     */
    private List<String> details;
}
