package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.ConfigItemReq;
import com.awe.apex.quant.domain.entity.SystemConfig;
import com.awe.apex.quant.service.IConfigService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 参数配置
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Resource
    private IConfigService configService;

    /**
     * 列表
     */
    @GetMapping
    public Result<List<SystemConfig>> list() {
        return Result.success(configService.listAll());
    }

    /**
     * 更新
     */
    @PutMapping
    public Result<SystemConfig> update(@Valid @RequestBody ConfigItemReq req) {
        return Result.success(configService.update(req));
    }
}
