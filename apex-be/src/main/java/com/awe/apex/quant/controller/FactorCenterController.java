package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.FactorCenterResp;
import com.awe.apex.quant.service.IFactorCenterService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 因子中心
 */
@RestController
@RequestMapping("/api/factors")
public class FactorCenterController {

    @Resource
    private IFactorCenterService factorCenterService;

    /**
     * 查询个股因子中心详情。
     *
     * @param code 证券代码
     * @return 六类因子与 Alpha 评分
     */
    @GetMapping("/{code}")
    public Result<FactorCenterResp> query(@PathVariable String code) {
        return Result.success(factorCenterService.query(code));
    }
}
