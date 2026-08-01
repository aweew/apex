package com.awe.apex.manager.domain.roleMenu.dto.req;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统角色权限新增请求对象
 *
 * @author Awe
 * @since 2025-12-11 16:45:00
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class RoleMenuAddReq implements Serializable {

    @Serial
    private static final long serialVersionUID = -82774415658532767L;

    /**
     * 角色id（关联sys_user表）
     */
    private Long roleId;

    /**
     * 权限id（关联sys_menu表）
     */
    private Long menuId;

}
