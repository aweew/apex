package com.awe.apex.quant.service.impl;

import com.awe.apex.common.constant.enums.StatusEnum;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.manager.domain.user.entity.User;
import com.awe.apex.manager.mapper.UserMapper;
import com.awe.apex.quant.domain.entity.ApexUserProfile;
import com.awe.apex.quant.mapper.ApexUserProfileMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
}
