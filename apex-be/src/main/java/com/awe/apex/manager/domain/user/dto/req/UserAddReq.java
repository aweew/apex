package com.awe.apex.manager.domain.user.dto.req;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户新增请求对象
 *
 * @author Awe
 * @since 2025-12-10 16:03:20
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class UserAddReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 415865780811806418L;

    /**
     * 电话
     */
    private String phone;

    /**
     * 用户密码
     */
    private String password;

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
     * 注册时间
     */
    private LocalDateTime registerTime;

    /**
     * 最近登录ip
     */
    private String lastLoginIp;

    /**
     * 最近登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 备注
     */
    private String remark;

}
