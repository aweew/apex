package com.awe.apex.manager.domain.role.dto.resp;

import com.awe.apex.common.constant.enums.StatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统角色响应对象
 *
 * @author Awe
 * @since 2025-12-15 11:12:59
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class RoleResp implements Serializable {

    @Serial
    private static final long serialVersionUID = -18878859868491618L;

    /**
     * 主键id
     */
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

}
