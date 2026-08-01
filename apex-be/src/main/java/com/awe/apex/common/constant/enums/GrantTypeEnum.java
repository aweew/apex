package com.awe.apex.common.constant.enums;

import com.awe.apex.common.constant.EnumDict;
import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 授权类型枚举
 *
 * @author Awe
 * @since 2025/12/11 13:18
 */
@Getter
@AllArgsConstructor
public enum GrantTypeEnum {

    // ===== 10 段：基础账号 =====
    PASSWORD(10, "密码模式"),

    // ===== 20 段：通用验证码 =====
    SMS_CODE(20, "短信验证码"),
    EMAIL_CODE(21, "邮箱验证码"),

    // ===== 30 段：微信登录 =====
    WECHAT_MP_QR(30, "微信公众号扫码登录"),
    WECHAT_MINI_CODE(31, "微信小程序登录"),
    WECHAT_APP_OAUTH(32, "微信APP授权登录"),

    // ===== 40 段：钉钉登录 =====
    DINGTALK_QR(40, "钉钉扫码登录"),
    DINGTALK_CODE(41, "钉钉验证码登录"),
    DINGTALK_MINI_CODE(42, "钉钉小程序登录"),

    // ===== 50 段：自有平台扫码 =====
    QR_CODE(50, "平台二维码扫码登录"),

    // ===== 90 段：兜底扩展 =====
    THIRD_PARTY(90, "第三方平台登录");
    ;

    @EnumValue
    private final int value;
    private final String label;

}
