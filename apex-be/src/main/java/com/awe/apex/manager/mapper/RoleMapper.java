package com.awe.apex.manager.mapper;

import com.awe.apex.manager.domain.role.dto.resp.RoleResp;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.awe.apex.manager.domain.role.entity.Role;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统角色数据库访问层
 *
 * @author Awe
 * @since 2025-12-11 16:45:00
 */
public interface RoleMapper extends BaseMapper<Role> {

    List<RoleResp> listByUserId(@Param("userId") Long userId);

}
