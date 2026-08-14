package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.ApexInviteRegisterReq;
import com.awe.apex.quant.domain.dto.ApexInviteResp;
import com.awe.apex.quant.domain.dto.ApexLoginReq;
import com.awe.apex.quant.domain.dto.ApexLoginResp;
import com.awe.apex.quant.domain.dto.ApexPasswordChangeReq;
import com.awe.apex.quant.domain.dto.ApexUserResp;
import com.awe.apex.quant.domain.dto.ApexAdminUserResp;
import com.awe.apex.quant.domain.dto.ApexPasswordResetReq;

import java.util.List;

/**
 * Apex 用户认证与资产归属服务
 */
public interface ApexUserAuthService {

    /**
     * 用户登录
     *
     * @param req 登录请求
     * @return 登录结果
     */
    ApexLoginResp login(ApexLoginReq req);

    /**
     * 使用邀请注册
     *
     * @param req 注册请求
     * @return 注册用户
     */
    ApexUserResp register(ApexInviteRegisterReq req);

    /**
     * 获取当前用户
     *
     * @return 当前用户
     */
    ApexUserResp currentUser();

    /**
     * 修改当前用户密码
     *
     * @param req 密码请求
     */
    void changePassword(ApexPasswordChangeReq req);

    /**
     * 创建邀请
     *
     * @return 邀请信息
     */
    ApexInviteResp createInvite();

    /**
     * 创建指定用户的密码重置令牌
     *
     * @param userId 用户ID
     * @return 重置令牌
     */
    ApexInviteResp createPasswordReset(Long userId);

    /**
     * 使用重置令牌更新密码
     *
     * @param req 重置请求
     */
    void resetPassword(ApexPasswordResetReq req);

    /**
     * 查询管理员用户列表
     *
     * @return 用户列表
     */
    List<ApexAdminUserResp> listUsers();

    /**
     * 启用或禁用用户
     *
     * @param userId 用户ID
     * @param enabled 是否启用
     */
    void updateUserStatus(Long userId, Boolean enabled);

    /**
     * 获取当前用户模拟账户ID
     *
     * @return 模拟账户ID
     */
    Long currentPaperAccountId();

    /**
     * 校验当前用户为管理员
     */
    void requireAdmin();
}
