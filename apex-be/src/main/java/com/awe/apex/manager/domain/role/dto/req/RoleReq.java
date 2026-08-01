package com.awe.apex.manager.domain.role.dto.req;

import com.awe.apex.common.constant.enums.StatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;

/**
 * 系统角色查询请求对象
 *
 * @author Awe
 * @since 2025-12-15 13:17:44
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class RoleReq implements Serializable {

    @Serial
    private static final long serialVersionUID = -25179656479422992L;

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

}
