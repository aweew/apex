package com.awe.apex.manager.domain.auth.dto.resp;

import com.awe.apex.common.constant.enums.StatusEnum;
import com.awe.apex.manager.domain.menu.dto.resp.MenuResp;
import com.awe.apex.manager.domain.role.dto.resp.RoleResp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 用户响应信息
 *
 * @author Awe
 * @since 2025/12/16 13:04
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoResp implements Serializable {

    @Serial
    private static final long serialVersionUID = 2187195765213091054L;

    /**
     * 主键id
     */
    private Long id;

    /**
     * 电话
     */
    private String phone;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 性别（1男，2女）
     */
    private Integer sex;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 状态（1启用，2禁用）
     */
    private StatusEnum status;

    /**
     * 角色
     */
    private List<RoleResp> roles;

    /**
     * 菜单
     */
    private List<MenuResp> menus;

    /**
     * 权限
     */
    private List<String> permissions;

}
