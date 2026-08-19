package com.awe.apex.quant.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.common.constant.enums.StatusEnum;
import com.awe.apex.common.constant.Constants;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.manager.domain.user.entity.User;
import com.awe.apex.manager.mapper.UserMapper;
import com.awe.apex.quant.domain.dto.ApexInviteRegisterReq;
import com.awe.apex.quant.domain.dto.ApexInviteResp;
import com.awe.apex.quant.domain.dto.ApexAdminUserResp;
import com.awe.apex.quant.domain.dto.ApexLoginReq;
import com.awe.apex.quant.domain.dto.ApexLoginResp;
import com.awe.apex.quant.domain.dto.ApexPasswordChangeReq;
import com.awe.apex.quant.domain.dto.ApexPasswordResetReq;
import com.awe.apex.quant.domain.dto.ApexUserResp;
import com.awe.apex.quant.domain.entity.ApexUserInvite;
import com.awe.apex.quant.domain.entity.ApexUserProfile;
import com.awe.apex.quant.domain.entity.PaperAccount;
import com.awe.apex.quant.domain.entity.Portfolio;
import com.awe.apex.quant.mapper.ApexUserInviteMapper;
import com.awe.apex.quant.mapper.ApexUserProfileMapper;
import com.awe.apex.quant.mapper.PaperAccountMapper;
import com.awe.apex.quant.mapper.PortfolioMapper;
import com.awe.apex.quant.service.ApexUserAuthService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Apex 用户认证与资产归属服务实现
 */
@Service
public class ApexUserAuthServiceImpl implements ApexUserAuthService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_MEMBER = "MEMBER";

    @Resource
    private UserMapper userMapper;

    @Resource
    private ApexUserProfileMapper userProfileMapper;

    @Resource
    private ApexUserInviteMapper userInviteMapper;

    @Resource
    private PaperAccountMapper paperAccountMapper;

    @Resource
    private PortfolioMapper portfolioMapper;

    /**
     * 用户登录
     *
     * @param req 登录请求
     * @return 登录结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApexLoginResp login(ApexLoginReq req) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getPhone, req.getPhone()).last("LIMIT 1"));
        if (Objects.isNull(user) || !BCrypt.checkpw(req.getPassword(), user.getPassword())) {
            throw new BusinessException("手机号或密码错误");
        }
        if (StatusEnum.DISABLE.equals(user.getStatus())) {
            throw new BusinessException("账户已被禁用");
        }
        ApexUserProfile profile = requireProfile(user.getId());
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);
        StpUtil.login(user.getId());
        StpUtil.getSession().set(Constants.PHONE, user.getPhone());
        StpUtil.getSession().set(Constants.NICK_NAME, user.getNickName());
        return ApexLoginResp.builder()
                .accessToken(StpUtil.getTokenValue())
                .expireIn(StpUtil.getTokenTimeout())
                .user(buildUserResp(user, profile))
                .build();
    }

    /**
     * 使用邀请注册
     *
     * @param req 注册请求
     * @return 注册用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApexUserResp register(ApexInviteRegisterReq req) {
        ApexUserInvite invite = userInviteMapper.selectOne(Wrappers.<ApexUserInvite>lambdaQuery()
                .eq(ApexUserInvite::getTokenHash, hashToken(req.getToken())).last("LIMIT 1"));
        if (Objects.isNull(invite) || !"INVITE".equals(invite.getPurpose()) || Objects.nonNull(invite.getUsedTime()) || Objects.nonNull(invite.getRevokedTime())
                || invite.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("邀请链接无效或已过期");
        }
        Long userCount = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getPhone, req.getPhone()));
        if (Objects.nonNull(userCount) && userCount > 0) {
            throw new BusinessException("该手机号已注册");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = User.builder()
                .phone(req.getPhone())
                .password(BCrypt.hashpw(req.getPassword()))
                .nickName(req.getNickName().trim())
                .registerTime(now)
                .status(StatusEnum.ENABLE)
                .createTime(now)
                .updateTime(now)
                .isDelete(false)
                .build();
        userMapper.insert(user);

        PaperAccount account = PaperAccount.builder()
                .userId(user.getId())
                .accountName("用户" + user.getId() + "模拟盘")
                .cash(BigDecimal.ZERO)
                .initCash(BigDecimal.ZERO)
                .status("ACTIVE")
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        paperAccountMapper.insert(account);

        Portfolio portfolio = Portfolio.builder()
                .userId(user.getId())
                .name(req.getNickName().trim())
                .note("用户初始化默认组合")
                .ownerLabel(req.getNickName().trim())
                .isDefault(1)
                .status("ACTIVE")
                .sortNo(0)
                .cashBalance(BigDecimal.ZERO)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        portfolioMapper.insert(portfolio);

        ApexUserProfile profile = new ApexUserProfile(null, user.getId(), account.getId(), ROLE_MEMBER, now, now);
        userProfileMapper.insert(profile);
        invite.setUsedUserId(user.getId());
        invite.setUsedTime(now);
        userInviteMapper.updateById(invite);
        return buildUserResp(user, profile);
    }

    /**
     * 获取当前用户
     *
     * @return 当前用户
     */
    @Override
    public ApexUserResp currentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (Objects.isNull(user) || StatusEnum.DISABLE.equals(user.getStatus())) {
            StpUtil.logout(userId);
            throw new BusinessException("登录已失效");
        }
        return buildUserResp(user, requireProfile(userId));
    }

    /**
     * 修改当前用户密码
     *
     * @param req 密码请求
     */
    @Override
    public void changePassword(ApexPasswordChangeReq req) {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (Objects.isNull(user) || !BCrypt.checkpw(req.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("当前密码错误");
        }
        user.setPassword(BCrypt.hashpw(req.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    /**
     * 创建邀请
     *
     * @return 邀请信息
     */
    @Override
    public ApexInviteResp createInvite() {
        requireAdmin();
        String token = generateToken();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusDays(7);
        ApexUserInvite invite = new ApexUserInvite();
        invite.setTokenHash(hashToken(token));
        invite.setCreatorUserId(StpUtil.getLoginIdAsLong());
        invite.setPurpose("INVITE");
        invite.setExpireTime(expireTime);
        invite.setCreateTime(now);
        userInviteMapper.insert(invite);
        return ApexInviteResp.builder().token(token).expireTime(expireTime).build();
    }

    /**
     * 创建指定用户的密码重置令牌
     *
     * @param userId 用户ID
     * @return 重置令牌
     */
    @Override
    public ApexInviteResp createPasswordReset(Long userId) {
        requireAdmin();
        User user = userMapper.selectById(userId);
        if (Objects.isNull(user)) {
            throw new BusinessException("用户不存在");
        }
        String token = generateToken();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusDays(7);
        ApexUserInvite invite = new ApexUserInvite();
        invite.setTokenHash(hashToken(token));
        invite.setCreatorUserId(StpUtil.getLoginIdAsLong());
        invite.setPurpose("RESET");
        invite.setTargetUserId(userId);
        invite.setExpireTime(expireTime);
        invite.setCreateTime(now);
        userInviteMapper.insert(invite);
        return ApexInviteResp.builder().token(token).expireTime(expireTime).build();
    }

    /**
     * 使用重置令牌更新密码
     *
     * @param req 重置请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ApexPasswordResetReq req) {
        ApexUserInvite invite = userInviteMapper.selectOne(Wrappers.<ApexUserInvite>lambdaQuery()
                .eq(ApexUserInvite::getTokenHash, hashToken(req.getToken())).last("LIMIT 1"));
        if (Objects.isNull(invite) || !"RESET".equals(invite.getPurpose()) || Objects.isNull(invite.getTargetUserId())
                || Objects.nonNull(invite.getUsedTime()) || Objects.nonNull(invite.getRevokedTime())
                || invite.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("重置链接无效或已过期");
        }
        User user = userMapper.selectById(invite.getTargetUserId());
        if (Objects.isNull(user)) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(BCrypt.hashpw(req.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        invite.setUsedTime(LocalDateTime.now());
        invite.setUsedUserId(user.getId());
        userInviteMapper.updateById(invite);
        StpUtil.kickout(user.getId());
    }

    /**
     * 查询管理员用户列表
     *
     * @return 用户列表
     */
    @Override
    public List<ApexAdminUserResp> listUsers() {
        requireAdmin();
        List<User> users = userMapper.selectList(Wrappers.<User>lambdaQuery().orderByAsc(User::getId));
        List<ApexAdminUserResp> result = new ArrayList<>();
        for (User user : users) {
            ApexUserProfile profile = userProfileMapper.selectOne(Wrappers.<ApexUserProfile>lambdaQuery()
                    .eq(ApexUserProfile::getUserId, user.getId()).last("LIMIT 1"));
            if (Objects.isNull(profile)) {
                continue;
            }
            result.add(ApexAdminUserResp.builder().id(user.getId()).phone(user.getPhone()).nickName(user.getNickName())
                    .role(profile.getRole()).enabled(StatusEnum.ENABLE.equals(user.getStatus()))
                    .lastLoginTime(user.getLastLoginTime()).build());
        }
        return result;
    }

    /**
     * 查询启用且已初始化资产档案的用户ID。
     *
     * @return 用户ID列表
     */
    @Override
    public List<Long> listEnabledUserIds() {
        List<User> users = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .eq(User::getStatus, StatusEnum.ENABLE)
                .orderByAsc(User::getId));
        List<Long> userIds = new ArrayList<>();
        for (User user : users) {
            Long profileCount = userProfileMapper.selectCount(Wrappers.<ApexUserProfile>lambdaQuery()
                    .eq(ApexUserProfile::getUserId, user.getId()));
            if (Objects.nonNull(profileCount) && profileCount > 0) {
                userIds.add(user.getId());
            }
        }
        return userIds;
    }

    /**
     * 校验指定用户存在、已初始化资产档案且处于启用状态。
     *
     * @param userId 用户ID
     */
    @Override
    public void requireEnabledUser(Long userId) {
        if (Objects.isNull(userId)) {
            throw new BusinessException("账户不存在或已禁用");
        }
        User user = userMapper.selectById(userId);
        if (Objects.isNull(user) || !StatusEnum.ENABLE.equals(user.getStatus())) {
            throw new BusinessException("账户不存在或已禁用");
        }
        requireProfile(userId);
    }

    /**
     * 启用或禁用用户
     *
     * @param userId 用户ID
     * @param enabled 是否启用
     */
    @Override
    public void updateUserStatus(Long userId, Boolean enabled) {
        requireAdmin();
        User user = userMapper.selectById(userId);
        if (Objects.isNull(user)) {
            throw new BusinessException("用户不存在");
        }
        user.setStatus(Boolean.TRUE.equals(enabled) ? StatusEnum.ENABLE : StatusEnum.DISABLE);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        if (!Boolean.TRUE.equals(enabled)) {
            StpUtil.kickout(userId);
        }
    }

    /**
     * 获取当前用户模拟账户ID
     *
     * @return 模拟账户ID
     */
    @Override
    public Long currentPaperAccountId() {
        return requireProfile(StpUtil.getLoginIdAsLong()).getPaperAccountId();
    }

    /**
     * 校验当前用户为管理员
     */
    @Override
    public void requireAdmin() {
        ApexUserProfile profile = requireProfile(StpUtil.getLoginIdAsLong());
        if (!ROLE_ADMIN.equals(profile.getRole())) {
            throw new BusinessException("无管理员权限");
        }
    }

    private ApexUserProfile requireProfile(Long userId) {
        ApexUserProfile profile = userProfileMapper.selectOne(Wrappers.<ApexUserProfile>lambdaQuery()
                .eq(ApexUserProfile::getUserId, userId).last("LIMIT 1"));
        if (Objects.isNull(profile)) {
            throw new BusinessException("用户资产档案未初始化");
        }
        return profile;
    }

    private ApexUserResp buildUserResp(User user, ApexUserProfile profile) {
        return ApexUserResp.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .nickName(user.getNickName())
                .role(profile.getRole())
                .paperAccountId(profile.getPaperAccountId())
                .build();
    }

    private String generateToken() {
        byte[] tokenBytes = new byte[32];
        new SecureRandom().nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashToken(String token) {
        try {
            byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(hashBytes.length * 2);
            for (byte hashByte : hashBytes) {
                hash.append(String.format("%02x", hashByte));
            }
            return hash.toString();
        } catch (Exception ex) {
            throw new BusinessException("邀请令牌处理失败", ex);
        }
    }
}
