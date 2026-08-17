package com.awe.apex.quant.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.awe.apex.common.constant.Constants;
import com.awe.apex.common.constant.enums.StatusEnum;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.manager.domain.user.entity.User;
import com.awe.apex.manager.mapper.UserMapper;
import com.awe.apex.quant.domain.dto.ApexLoginReq;
import com.awe.apex.quant.domain.entity.ApexUserProfile;
import com.awe.apex.quant.mapper.ApexUserProfileMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApexUserAuthServiceImplTest {

    private static final Long USER_ID = 7L;

    private final UserMapper userMapper = mock(UserMapper.class);
    private final ApexUserProfileMapper userProfileMapper = mock(ApexUserProfileMapper.class);
    private final ApexUserAuthServiceImpl service = new ApexUserAuthServiceImpl();

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ApexUserProfile.class);
    }

    @BeforeEach
    void setUp() {
        Mockito.reset(userMapper, userProfileMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "userProfileMapper", userProfileMapper);
    }

    @Test
    void shouldRejectDisabledUser() {
        User disabledUser = User.builder()
                .id(USER_ID)
                .status(StatusEnum.DISABLE)
                .build();
        when(userMapper.selectById(USER_ID)).thenReturn(disabledUser);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireEnabledUser(USER_ID));

        assertEquals("账户不存在或已禁用", exception.getMessage());
    }

    @Test
    void shouldRejectEnabledUserWithoutAssetProfile() {
        User enabledUser = User.builder()
                .id(USER_ID)
                .status(StatusEnum.ENABLE)
                .build();
        when(userMapper.selectById(USER_ID)).thenReturn(enabledUser);
        when(userProfileMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.requireEnabledUser(USER_ID));

        assertEquals("用户资产档案未初始化", exception.getMessage());
    }

    @Test
    void shouldCachePhoneInSessionAfterLogin() {
        String phone = "13812345678";
        String password = "test-password";
        User user = User.builder()
                .id(USER_ID)
                .phone(phone)
                .password(BCrypt.hashpw(password))
                .status(StatusEnum.ENABLE)
                .build();
        ApexUserProfile profile = new ApexUserProfile(1L, USER_ID, 9L, "MEMBER", null, null);
        ApexLoginReq request = new ApexLoginReq();
        request.setPhone(phone);
        request.setPassword(password);
        SaSession session = mock(SaSession.class);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userProfileMapper.selectOne(any())).thenReturn(profile);

        try (MockedStatic<StpUtil> stpUtil = Mockito.mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getSession).thenReturn(session);
            stpUtil.when(StpUtil::getTokenValue).thenReturn("access-token");
            stpUtil.when(StpUtil::getTokenTimeout).thenReturn(3600L);

            service.login(request);
        }

        verify(session).set(Constants.PHONE, phone);
    }
}
