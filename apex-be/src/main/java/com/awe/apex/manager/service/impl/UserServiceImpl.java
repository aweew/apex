package com.awe.apex.manager.service.impl;

import com.awe.apex.manager.domain.auth.dto.resp.UserInfoResp;
import com.awe.apex.manager.domain.menu.dto.resp.MenuResp;
import com.awe.apex.manager.domain.role.dto.resp.RoleResp;
import com.awe.apex.manager.domain.user.entity.User;
import com.awe.apex.manager.mapper.UserMapper;
import com.awe.apex.manager.service.IMenuService;
import com.awe.apex.manager.service.IRoleService;
import com.awe.apex.manager.service.IUserService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 系统用户服务实现类
 *
 * @author Awe
 * @since 2025-12-10 16:03:20
 */
@Service("userService")
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private IRoleService roleService;

    @Resource
    private IMenuService menuService;

    @Override
    public User getByPhone(String phone) {
        return this.baseMapper.selectOne(Wrappers.lambdaQuery(User.class).eq(User::getPhone, phone));
    }

    @Override
    public UserInfoResp getUserInfo(Long userId) {
        User user = this.getById(userId);
        if (Objects.isNull(user)) {
            return null;
        }

        UserInfoResp userInfoResp = new UserInfoResp();
        BeanUtils.copyProperties(user, userInfoResp);

        // 查询角色
        List<RoleResp> roles = roleService.listByUserId(userId);
        userInfoResp.setRoles(roles);

        // 查询菜单
        List<MenuResp> menus = menuService.listAllMenusByUserId(userId);
        userInfoResp.setMenus(menus);

        // 查询权限
        List<String> permissions = menuService.listPermissionsByUserId(userId);
        userInfoResp.setPermissions(permissions);

        return userInfoResp;
    }

}
