package com.awe.apex.manager.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.awe.apex.manager.domain.roleMenu.entity.RoleMenu;
import com.awe.apex.manager.mapper.RoleMenuMapper;
import com.awe.apex.manager.service.IRoleMenuService;
import org.springframework.stereotype.Service;

/**
 * 系统角色权限服务实现类
 *
 * @author Awe
 * @since 2025-12-11 16:45:00
 */
@Service("roleMenuService")
public class RoleMenuServiceImpl extends ServiceImpl<RoleMenuMapper, RoleMenu> implements IRoleMenuService {

}
