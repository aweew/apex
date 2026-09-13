package com.awe.apex.common.config;

import com.awe.apex.quant.signal.mapper.SignalCenterMapper;
import com.awe.apex.quant.signal.query.SignalCenterService;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MyBatis 扫描边界配置测试。
 */
class MybatisConfigTest {

    /**
     * 信号中心服务接口和 Mapper 必须分属独立扫描边界。
     */
    @Test
    void keepsSignalCenterMapperOutsideServicePackage() {
        MapperScan mapperScan = MybatisConfig.class.getAnnotation(MapperScan.class);
        List<String> basePackages = Arrays.asList(mapperScan.basePackages());

        assertTrue(basePackages.contains("com.awe.apex.quant.signal.mapper"));
        assertFalse(basePackages.contains("com.awe.apex.quant.signal.query"));
        assertEquals("com.awe.apex.quant.signal.mapper", SignalCenterMapper.class.getPackageName());
        assertEquals("com.awe.apex.quant.signal.query", SignalCenterService.class.getPackageName());
    }
}
