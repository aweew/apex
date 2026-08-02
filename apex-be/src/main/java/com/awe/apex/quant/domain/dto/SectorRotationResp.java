package com.awe.apex.quant.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 板块轮动时间轴
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorRotationResp {

    /**
     * 按日倒序
     */
    private List<SectorRotationDay> days;

    /**
     * 说明
     */
    private String message;
}
