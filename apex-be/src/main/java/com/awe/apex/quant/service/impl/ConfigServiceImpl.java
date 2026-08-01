package com.awe.apex.quant.service.impl;

import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.domain.dto.ConfigItemReq;
import com.awe.apex.quant.domain.entity.SystemConfig;
import com.awe.apex.quant.mapper.SystemConfigMapper;
import com.awe.apex.quant.service.IConfigService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 系统配置实现
 */
@Service
public class ConfigServiceImpl implements IConfigService {

    @Resource
    private SystemConfigMapper systemConfigMapper;

    @Override
    public List<SystemConfig> listAll() {
        return systemConfigMapper.selectList(Wrappers.<SystemConfig>lambdaQuery().orderByAsc(SystemConfig::getId));
    }

    @Override
    public SystemConfig update(ConfigItemReq req) {
        SystemConfig config = systemConfigMapper.selectOne(Wrappers.<SystemConfig>lambdaQuery()
                .eq(SystemConfig::getConfigKey, req.getConfigKey())
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (Objects.isNull(config)) {
            config = SystemConfig.builder()
                    .configKey(req.getConfigKey())
                    .configValue(req.getConfigValue())
                    .createTime(now)
                    .updateTime(now)
                    .deleted(0)
                    .build();
            systemConfigMapper.insert(config);
            return config;
        }
        config.setConfigValue(req.getConfigValue());
        config.setUpdateTime(now);
        systemConfigMapper.updateById(config);
        return config;
    }

    @Override
    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        String value = getString(key, null);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            return new BigDecimal(value);
        } catch (Exception ex) {
            throw new BusinessException("配置值非法: " + key);
        }
    }

    @Override
    public String getString(String key, String defaultValue) {
        SystemConfig config = systemConfigMapper.selectOne(Wrappers.<SystemConfig>lambdaQuery()
                .eq(SystemConfig::getConfigKey, key)
                .last("limit 1"));
        if (Objects.isNull(config) || StringUtils.isBlank(config.getConfigValue())) {
            return defaultValue;
        }
        return config.getConfigValue();
    }
}
