package com.awe.apex.manager.domain.role.entity;

import com.awe.apex.common.constant.enums.StatusEnum;
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
 * 系统角色实体类
 *
 * @author Awe
 * @since 2025-12-12 16:17:39
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@TableName("sys_role")
public class Role implements Serializable {

    @Serial
    private static final long serialVersionUID = -65593716951227319L;

    /**
     * 主键id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 角色编码
     */
    private String code;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 是否系统内置角色
     */
    private Boolean isSystem;

    /**
     * 状态（1启用，2禁用）
     */
    private StatusEnum status;

    /**
     * 角色描述
     */
    private String remark;

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
