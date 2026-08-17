package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.ScreenerStrategyReorderReq;
import com.awe.apex.quant.domain.dto.ScreenerStrategyResp;
import com.awe.apex.quant.domain.dto.ScreenerStrategySaveReq;
import com.awe.apex.quant.service.IScreenerStrategyService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 可维护选股策略接口
 */
@RestController
@RequestMapping("/api/screener/strategies")
public class ScreenerStrategyController {

    @Resource
    private IScreenerStrategyService strategyService;

    /**
     * 查询系统模板和当前用户策略。
     *
     * @return 策略列表
     */
    @GetMapping
    public Result<List<ScreenerStrategyResp>> list() {
        return Result.success(strategyService.list());
    }

    /**
     * 查询当前用户策略详情。
     *
     * @param id 策略ID
     * @return 策略详情
     */
    @GetMapping("/{id}")
    public Result<ScreenerStrategyResp> detail(@PathVariable Long id) {
        return Result.success(strategyService.detail(id));
    }

    /**
     * 新增策略。
     *
     * @param req 保存请求
     * @return 策略详情
     */
    @PostMapping
    public Result<ScreenerStrategyResp> create(@RequestBody ScreenerStrategySaveReq req) {
        req.setId(null);
        return Result.success(strategyService.save(req));
    }

    /**
     * 更新策略。
     *
     * @param id  策略ID
     * @param req 保存请求
     * @return 策略详情
     */
    @PutMapping("/{id}")
    public Result<ScreenerStrategyResp> update(@PathVariable Long id,
                                               @RequestBody ScreenerStrategySaveReq req) {
        req.setId(id);
        return Result.success(strategyService.save(req));
    }

    /**
     * 复制系统模板。
     *
     * @param templateKey 模板标识
     * @return 新策略
     */
    @PostMapping("/templates/{templateKey}/copy")
    public Result<ScreenerStrategyResp> copyTemplate(@PathVariable String templateKey) {
        return Result.success(strategyService.copyTemplate(templateKey));
    }

    /**
     * 复制用户策略。
     *
     * @param id 策略ID
     * @return 新策略
     */
    @PostMapping("/{id}/copy")
    public Result<ScreenerStrategyResp> copy(@PathVariable Long id) {
        return Result.success(strategyService.copy(id));
    }

    /**
     * 启用或停用用户策略。
     *
     * @param id      策略ID
     * @param enabled 是否启用
     * @return 更新后策略
     */
    @PostMapping("/{id}/toggle")
    public Result<ScreenerStrategyResp> toggle(@PathVariable Long id,
                                               @RequestParam Boolean enabled) {
        return Result.success(strategyService.toggle(id, enabled));
    }

    /**
     * 调整用户策略顺序。
     *
     * @param req 排序请求
     * @return 空
     */
    @PostMapping("/reorder")
    public Result<Void> reorder(@RequestBody ScreenerStrategyReorderReq req) {
        strategyService.reorder(req);
        return Result.success(null);
    }

    /**
     * 删除用户策略。
     *
     * @param id 策略ID
     * @return 空
     */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        strategyService.remove(id);
        return Result.success(null);
    }
}
