package com.awe.apex.core.controller;

import com.awe.apex.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试服务
 *
 * @author Awe
 * @since 2025/9/9 14:06
 */
@RestController
@RequestMapping("/test")
public class TestController {

    /**
     * 测试接口
     *
     * @return 用户列表
     */
    @GetMapping("/user")
    public Result<?> getUser() {
        return Result.success();
    }

}
