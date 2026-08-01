package com.awe.apex.manager.domain.roleMenu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统角色权限实体类
 *
 * @author Awe
 * @since 2025-12-11 16:45:00
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@TableName("sys_role_menu")
public class RoleMenu implements Serializable {

    @Serial
    private static final long serialVersionUID = 611430485440584974L;

    /**
     * 主键id
     */
    @TableId(value = "id", type = IdType.AUTO)
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

    /**
     * 逻辑删除（0未删除，1已删除）
     */
    private Boolean isDelete;

}
