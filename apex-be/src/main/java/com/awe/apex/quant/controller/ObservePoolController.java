package com.awe.apex.quant.controller;

import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.DecisionRunReq;
import com.awe.apex.quant.domain.dto.DecisionTodayResp;
import com.awe.apex.quant.domain.dto.ObserveGuideTemplateResp;
import com.awe.apex.quant.domain.dto.ObservePoolResp;
import com.awe.apex.quant.domain.dto.ObservePoolSaveReq;
import com.awe.apex.quant.domain.entity.ObservePool;
import com.awe.apex.quant.service.IDecisionService;
import com.awe.apex.quant.service.IObservePoolService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 观察池
 */
@RestController
@RequestMapping("/api/observe")
public class ObservePoolController {

    @Resource
    private IObservePoolService observePoolService;

    @Resource
    private IDecisionService decisionService;

    /**
     * 观察池列表
     *
     * @param status  状态
     * @param keyword 关键字
     * @return 列表
     */
    @GetMapping("/list")
    public Result<List<ObservePoolResp>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(observePoolService.list(status, keyword));
    }

    /**
     * 新增或更新
     *
     * @param req 请求
     * @return 实体
     */
    @PostMapping("/save")
    public Result<ObservePool> save(@RequestBody ObservePoolSaveReq req) {
        return Result.success(observePoolService.save(req));
    }

    /**
     * 删除
     *
     * @param id 主键
     * @return 空
     */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        observePoolService.remove(id);
        return Result.success(null);
    }

    /**
     * 归档
     *
     * @param id 主键
     * @return 实体
     */
    @PostMapping("/{id}/archive")
    public Result<ObservePool> archive(@PathVariable Long id) {
        return Result.success(observePoolService.archive(id));
    }

    /**
     * 刷新评估
     *
     * @return 统计
     */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh() {
        return Result.success(observePoolService.refresh());
    }

    /**
     * 单个指导模板
     *
     * @param reason 原因
     * @return 模板
     */
    @GetMapping("/guide-template")
    public Result<ObserveGuideTemplateResp> guideTemplate(@RequestParam(required = false) String reason) {
        return Result.success(observePoolService.guideTemplate(reason));
    }

    /**
     * 全部指导模板
     *
     * @return 模板列表
     */
    @GetMapping("/guide-templates")
    public Result<List<ObserveGuideTemplateResp>> guideTemplates() {
        return Result.success(observePoolService.guideTemplates());
    }

    /**
     * 一键自动决策：跑智能决策并写入观察池剧本
     *
     * @param req 决策请求
     * @return 决策结果（含观察池写入统计）
     */
    @PostMapping("/auto-decide")
    public Result<DecisionTodayResp> autoDecide(@RequestBody(required = false) DecisionRunReq req) {
        return Result.success(decisionService.run(req));
    }
}
