package com.awe.apex.manager.domain.userRole.dto.req;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户角色查询请求对象
 *
 * @author Awe
 * @since 2025-12-11 16:45:02
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class UserRoleReq implements Serializable {

    @Serial
    private static final long serialVersionUID = -57233992310922672L;

    /**
     * 主键id
     */
    private Long id;

    /**
     * 用户id（关联sys_user表）
     */
    private Long userId;

    /**
     * 角色id（关联sys_user表）
     */
    private Long roleId;

}
