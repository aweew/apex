package com.awe.apex.manager.service;

import com.awe.apex.manager.domain.menu.dto.req.MenuReq;
import com.awe.apex.manager.domain.menu.dto.resp.MenuResp;
import com.awe.apex.manager.domain.menu.entity.Menu;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 系统权限服务接口
 *
 * @author Awe
 * @since 2025-12-11 16:45:01
 */
public interface IMenuService extends IService<Menu> {

    List<MenuResp> listAllMenus();

    List<MenuResp> treeAllMenus();

    List<MenuResp> treeAllMenus(MenuReq req);

    List<MenuResp> listAllMenusByUserId(Long userId);

    List<MenuResp> treeAllMenusByUserId(Long userId);

    List<MenuResp> listAllMenusByRoleId(Long roleId);

    List<MenuResp> treeAllMenusByRoleId(Long roleId);

    List<String> listPermissionsByUserId(Long userId);

    List<String> listPermissionsByRoleId(Long roleId);

}
