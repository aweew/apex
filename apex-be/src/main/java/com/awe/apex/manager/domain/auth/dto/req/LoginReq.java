package com.awe.apex.manager.domain.auth.dto.req;

import com.awe.apex.common.constant.enums.GrantTypeEnum;
import com.awe.apex.common.validate.auth.EmailGroup;
import com.awe.apex.common.validate.auth.PasswordGroup;
import com.awe.apex.common.validate.auth.SmsGroup;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录请求对象
 *
 * @author Awe
 * @since 2025/12/11 13:08
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class LoginReq implements Serializable {

    @Serial
    private static final long serialVersionUID = -4535792619304936467L;

    /**
     * 客户端id
     */
    @NotBlank(message = "客户端不能为空")
    private String clientId;

    /**
     * 客户端key
     */
    private String clientKey;

    /**
     * 客户端秘钥
     */
    private String clientSecret;

    /**
     * 授权类型
     */
    @NotBlank(message = "授权类型不能为空")
    private GrantTypeEnum grantType;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空", groups = {PasswordGroup.class})
    private String password;

    /**
     * 验证码
     */
    private String code;

    /**
     * 唯一标识
     */
    private String uuid;

    /**
     * 钉钉验证码
     */
    private String dingTalkCode;

    /**
     * 电话号码
     */
    @NotBlank(message = "电话号码不能为空", groups = {SmsGroup.class})
    private String phone;

    /**
     * 短信验证码
     */
    @NotBlank(message = "电话验证码不能为空", groups = {SmsGroup.class})
    private String smsCode;

    /**
     * 邮箱地址
     */
    @NotBlank(message = "邮箱地址不能为空", groups = {EmailGroup.class})
    @Email(message = "邮箱地址格式不正确")
    private String email;

    /**
     * 邮箱验证码
     */
    @NotBlank(message = "邮箱验证码不能为空", groups = {EmailGroup.class})
    private String emailCode;

    /**
     * 小程序code
     */
    private String xcxCode;

    /**
     * 第三方登录平台
     */
    private String source;

    /**
     * 第三方登录code
     */
    private String socialCode;

    /**
     * 第三方登录socialState
     */
    private String socialState;

}
