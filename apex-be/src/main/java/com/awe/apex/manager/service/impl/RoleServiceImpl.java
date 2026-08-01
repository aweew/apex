package com.awe.apex.manager.service.impl;

import com.awe.apex.manager.domain.role.dto.resp.RoleResp;
import com.awe.apex.manager.domain.user.entity.User;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.awe.apex.manager.domain.role.entity.Role;
import com.awe.apex.manager.mapper.RoleMapper;
import com.awe.apex.manager.service.IRoleService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统角色服务实现类
 *
 * @author Awe
 * @since 2025-12-11 16:45:00
 */
@Service("roleService")
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements IRoleService {

    @Override
    public List<RoleResp> listByUserId(Long userId) {
        return this.baseMapper.listByUserId(userId);
    }

}
