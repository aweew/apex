package com.awe.apex.manager.domain.userRole.dto.req;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户角色新增请求对象
 *
 * @author Awe
 * @since 2025-12-11 16:45:02
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class UserRoleAddReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 597979471423125417L;

    /**
     * 用户id（关联sys_user表）
     */
    private Long userId;

    /**
     * 角色id（关联sys_user表）
     */
    private Long roleId;

}
