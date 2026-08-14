package com.awe.apex.quant.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.common.api.Result;
import com.awe.apex.quant.domain.dto.ApexInviteRegisterReq;
import com.awe.apex.quant.domain.dto.ApexInviteResp;
import com.awe.apex.quant.domain.dto.ApexAdminUserResp;
import com.awe.apex.quant.domain.dto.ApexLoginReq;
import com.awe.apex.quant.domain.dto.ApexLoginResp;
import com.awe.apex.quant.domain.dto.ApexPasswordChangeReq;
import com.awe.apex.quant.domain.dto.ApexPasswordResetReq;
import com.awe.apex.quant.domain.dto.ApexUserResp;
import com.awe.apex.quant.domain.dto.ApexUserStatusReq;
import com.awe.apex.quant.service.ApexUserAuthService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Apex Web 用户认证接口
 */
@RestController
@RequestMapping("/api/auth")
public class ApexAuthController {

    @Resource
    private ApexUserAuthService apexUserAuthService;

    /**
     * 手机号密码登录
     *
     * @param req 登录请求
     * @return Bearer 令牌与用户信息
     */
    @PostMapping("/login")
    public Result<ApexLoginResp> login(@Valid @RequestBody ApexLoginReq req) {
        return Result.success(apexUserAuthService.login(req));
    }

    /**
     * 使用邀请完成注册
     *
     * @param req 注册请求
     * @return 注册用户
     */
    @PostMapping("/register")
    public Result<ApexUserResp> register(@Valid @RequestBody ApexInviteRegisterReq req) {
        return Result.success(apexUserAuthService.register(req));
    }

    /**
     * 获取当前用户
     *
     * @return 当前用户
     */
    @GetMapping("/me")
    public Result<ApexUserResp> me() {
        return Result.success(apexUserAuthService.currentUser());
    }

    /**
     * 修改密码
     *
     * @param req 密码请求
     * @return 空
     */
    @PostMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ApexPasswordChangeReq req) {
        apexUserAuthService.changePassword(req);
        return Result.success();
    }

    /**
     * 使用一次性链接重置密码
     *
     * @param req 重置请求
     * @return 空
     */
    @PostMapping("/reset")
    public Result<Void> resetPassword(@Valid @RequestBody ApexPasswordResetReq req) {
        apexUserAuthService.resetPassword(req);
        return Result.success();
    }

    /**
     * 登出当前账号
     *
     * @return 空
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success();
    }

    /**
     * 创建邀请链接所需令牌
     *
     * @return 邀请令牌
     */
    @PostMapping("/admin/invites")
    public Result<ApexInviteResp> createInvite() {
        return Result.success(apexUserAuthService.createInvite());
    }

    /**
     * 查询用户列表
     *
     * @return 用户列表
     */
    @GetMapping("/admin/users")
    public Result<List<ApexAdminUserResp>> listUsers() {
        return Result.success(apexUserAuthService.listUsers());
    }

    /**
     * 启用或禁用用户
     *
     * @param userId 用户ID
     * @param req 启停请求
     * @return 空
     */
    @PostMapping("/admin/users/{userId}/status")
    public Result<Void> updateUserStatus(@org.springframework.web.bind.annotation.PathVariable Long userId,
                                         @Valid @RequestBody ApexUserStatusReq req) {
        apexUserAuthService.updateUserStatus(userId, req.getEnabled());
        return Result.success();
    }

    /**
     * 创建密码重置令牌
     *
     * @param userId 用户ID
     * @return 重置令牌
     */
    @PostMapping("/admin/users/{userId}/reset")
    public Result<ApexInviteResp> createPasswordReset(@org.springframework.web.bind.annotation.PathVariable Long userId) {
        return Result.success(apexUserAuthService.createPasswordReset(userId));
    }
}
