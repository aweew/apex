package com.awe.apex.manager.domain.role.dto.req;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统角色新增请求对象
 *
 * @author Awe
 * @since 2025-12-15 13:12:10
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class RoleAddReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 933382324496594580L;

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
    * 角色描述
    */
    private String remark;

}
