package com.awe.apex.quant.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户启停请求
 */
@Data
public class ApexUserStatusReq {

    /** 是否启用 */
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
}
