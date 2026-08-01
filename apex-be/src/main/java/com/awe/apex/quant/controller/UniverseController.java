package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.UniverseRefreshReq;
import com.awe.apex.quant.domain.dto.UniverseRefreshResp;
import com.awe.apex.quant.domain.entity.UniverseSnapshot;
import com.awe.apex.quant.service.IUniverseService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 股票池接口
 */
@RestController
@RequestMapping("/api/universe")
public class UniverseController {

    @Resource
    private IUniverseService universeService;

    /**
     * 刷新股票池
     *
     * @param req 请求
     * @return 结果
     */
    @PostMapping("/refresh")
    public Result<UniverseRefreshResp> refresh(@RequestBody(required = false) UniverseRefreshReq req) {
        if (req == null) {
            req = new UniverseRefreshReq();
        }
        return Result.success(universeService.refresh(req));
    }

    /**
     * 最新股票池
     *
     * @return 列表
     */
    @GetMapping("/latest")
    public Result<List<UniverseSnapshot>> latest() {
        return Result.success(universeService.latest());
    }
}
