package com.awe.apex.core.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Awe
 * @since 2025/12/10 09:00
 */
@RestController
@RequestMapping("/user")
@Slf4j
public class TestLoginController {

    // 测试登录，浏览器访问： http://localhost:6001/user/doLogin?username=zhang&password=123456
    @GetMapping("/doLogin")
    @SaIgnore
    public String doLogin(String username, String password) {
        // 此处仅作模拟示例，真实项目需要从数据库中查询数据进行比对
        if ("zhang".equals(username) && "123456".equals(password)) {
            // 登录10001账号，并为生成的 Token 追加扩展参数name
            StpUtil.login(10001, new SaLoginParameter()
                    .setExtra("name", "zhangsan")
                    .setExtra("age", 18)
                    .setExtra("role", "超级管理员"));

            return "登录成功";
        }
        return "登录失败";
    }

    // 查询登录状态，浏览器访问： http://localhost:6001/user/isLogin
    @GetMapping("/isLogin")
    public String isLogin() {

        // 获取扩展参数
        String name = String.valueOf(StpUtil.getExtra("name"));
        log.info("当前登录用户名称：{}", name);

        // 获取任意 Token 的扩展参数
        // String name = StpUtil.getExtra("tokenValue", "name");

        return "当前会话是否登录：" + StpUtil.isLogin();
    }

}
