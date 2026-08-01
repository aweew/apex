package com.awe.apex.manager.controller;

import com.awe.apex.common.api.PageResponse;
import com.awe.apex.common.api.Result;
import com.awe.apex.manager.domain.area.convert.AreaConvert;
import com.awe.apex.manager.domain.area.dto.AreaAddReq;
import com.awe.apex.manager.domain.area.dto.AreaReq;
import com.awe.apex.manager.domain.area.dto.AreaResp;
import com.awe.apex.manager.domain.area.dto.AreaUpdateReq;
import com.awe.apex.manager.domain.area.entity.Area;
import com.awe.apex.manager.service.IAreaService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * 区域管理
 *
 * @author Awe
 * @since 2025-09-09 15:09:21
 */
@RestController
@RequestMapping("/area")
public class AreaController {

    @Resource
    private IAreaService areaService;

    @Resource
    private AreaConvert areaConvert;

    /**
     * 根据筛选条件获取区域列表(分页)
     *
     * @param pageRequest 分页对象
     * @param req         筛选条件
     * @return 查询结果
     */
    @GetMapping("/page")
    public Result<PageResponse<AreaResp>> page(Page<Area> pageRequest, AreaReq req) {
        Area entity = areaConvert.toEntity(req);
        Page<Area> page = areaService.page(pageRequest, Wrappers.lambdaQuery(entity));
        return Result.success(page, areaConvert::toResp);
    }

    /**
     * 根据筛选条件获取区域列表(全量)
     *
     * @param req 筛选条件
     * @return 查询结果
     */
    @GetMapping("/list")
    public Result<List<AreaResp>> list(AreaReq req) {
        Area entity = areaConvert.toEntity(req);
        List<Area> list = areaService.list(Wrappers.lambdaQuery(entity));
        return Result.success(list, areaConvert::toRespList);
    }

    /**
     * 根据id获取区域
     *
     * @param id 主键
     * @return 单条数据
     */
    @GetMapping("/{id}")
    public Result<AreaResp> getById(@PathVariable("id") Long id) {
        Area entity = this.areaService.getById(id);
        return Result.success(entity, areaConvert::toResp);
    }

    /**
     * 新增区域
     *
     * @param req 新增对象
     * @return 新增结果
     */
    @PostMapping
    public Result<Void> save(@Valid @RequestBody AreaAddReq req) {
        this.areaService.save(areaConvert.addToEntity(req));
        return Result.success();
    }

    /**
     * 更新区域
     *
     * @param req 更新对象
     * @return 更新结果
     */
    @PutMapping
    public Result<Void> update(@Valid @RequestBody AreaUpdateReq req) {
        Area entity = this.areaService.getById(req.getId());
        if (Objects.nonNull(entity)) {
            areaConvert.updateToEntity(req, entity);
            this.areaService.updateById(entity);
        }
        return Result.success();
    }

    /**
     * 删除区域
     *
     * @param id 主键
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteById(@PathVariable("id") Long id) {
        this.areaService.removeById(id);
        return Result.success();
    }

}
