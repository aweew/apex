package com.awe.apex.manager.domain.userRole.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户角色响应对象
 *
 * @author Awe
 * @since 2025-12-11 16:45:02
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class UserRoleResp implements Serializable {

    @Serial
    private static final long serialVersionUID = -85678202371474437L;

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
