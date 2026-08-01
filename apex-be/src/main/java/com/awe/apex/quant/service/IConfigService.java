package com.awe.apex.quant.service;

import com.awe.apex.quant.domain.dto.ConfigItemReq;
import com.awe.apex.quant.domain.entity.SystemConfig;

import java.math.BigDecimal;
import java.util.List;

/**
 * 系统配置
 */
public interface IConfigService {

    /**
     * 全部配置
     *
     * @return 列表
     */
    List<SystemConfig> listAll();

    /**
     * 更新
     *
     * @param req 请求
     * @return 配置
     */
    SystemConfig update(ConfigItemReq req);

    /**
     * 读取小数配置
     *
     * @param key          键
     * @param defaultValue 默认
     * @return 值
     */
    BigDecimal getDecimal(String key, BigDecimal defaultValue);

    /**
     * 读取字符串配置
     *
     * @param key          键
     * @param defaultValue 默认
     * @return 值
     */
    String getString(String key, String defaultValue);
}
