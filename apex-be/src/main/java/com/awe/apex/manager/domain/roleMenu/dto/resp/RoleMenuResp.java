package com.awe.apex.manager.domain.roleMenu.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统角色权限响应对象
 *
 * @author Awe
 * @since 2025-12-11 16:45:00
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class RoleMenuResp implements Serializable {

    @Serial
    private static final long serialVersionUID = -45695578200880777L;

    /**
     * 主键id
     */
    private Long id;

    /**
     * 角色id（关联sys_user表）
     */
    private Long roleId;

    /**
     * 权限id（关联sys_menu表）
     */
    private Long menuId;

    /**
     * 创建人id
     */
    private Long createUserId;

    /**
     * 更新人id
     */
    private Long updateUserId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
