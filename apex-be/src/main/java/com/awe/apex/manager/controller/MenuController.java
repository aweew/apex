package com.awe.apex.manager.controller;

import com.awe.apex.common.api.PageResponse;
import com.awe.apex.common.api.Result;
import com.awe.apex.manager.domain.menu.convert.MenuConvert;
import com.awe.apex.manager.domain.menu.dto.req.MenuAddReq;
import com.awe.apex.manager.domain.menu.dto.req.MenuReq;
import com.awe.apex.manager.domain.menu.dto.req.MenuUpdateReq;
import com.awe.apex.manager.domain.menu.dto.resp.MenuResp;
import com.awe.apex.manager.domain.menu.entity.Menu;
import com.awe.apex.manager.service.IMenuService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * 系统权限管理
 *
 * @author Awe
 * @since 2025-12-11 16:45:01
 */
@RestController
@RequestMapping("/sys/menu")
public class MenuController {

    @Resource
    private IMenuService menuService;

    @Resource
    private MenuConvert menuConvert;

    /**
     * 根据筛选条件获取系统权限列表(分页)
     *
     * @param pageReq 分页对象
     * @param req     筛选条件
     * @return 查询结果
     */
    @GetMapping("/page")
    public Result<PageResponse<MenuResp>> page(Page<Menu> pageReq, MenuReq req) {
        Menu menu = menuConvert.toEntity(req);
        Page<Menu> page = this.menuService.page(pageReq, Wrappers.lambdaQuery(menu));
        return Result.success(page, menuConvert::toResp);
    }

    /**
     * 根据筛选条件获取系统权限列表(全量)
     *
     * @param req 筛选条件
     * @return 查询结果
     */
    @GetMapping("/list")
    public Result<List<MenuResp>> list(MenuReq req) {
        Menu menu = menuConvert.toEntity(req);
        List<Menu> list = menuService.list(Wrappers.lambdaQuery(menu));
        return Result.success(list, menuConvert::toRespList);
    }

    /**
     * 根据id获取系统权限
     *
     * @param id 主键
     * @return 单条数据
     */
    @GetMapping("/{id}")
    public Result<MenuResp> getById(@PathVariable("id") Long id) {
        Menu menu = this.menuService.getById(id);
        if (Objects.nonNull(menu)) {
            return Result.success(menu, menuConvert::toResp);
        }
        return Result.success();
    }

    /**
     * 新增系统权限
     *
     * @param req 新增对象
     * @return 新增结果
     */
    @PostMapping
    public Result<Void> save(@Valid @RequestBody MenuAddReq req) {
        this.menuService.save(menuConvert.addToEntity(req));
        return Result.success();
    }

    /**
     * 更新系统权限
     *
     * @param req 更新对象
     * @return 更新结果
     */
    @PutMapping
    public Result<Void> update(@Valid @RequestBody MenuUpdateReq req) {
        Menu menu = this.menuService.getById(req.getId());
        if (Objects.nonNull(menu)) {
            menuConvert.updateToEntity(req, menu);
            this.menuService.updateById(menu);
        }
        return Result.success();
    }

    /**
     * 删除系统权限
     *
     * @param id 主键
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteById(@PathVariable("id") Long id) {
        this.menuService.removeById(id);
        return Result.success();
    }

}
