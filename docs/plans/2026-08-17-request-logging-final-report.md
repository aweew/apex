# 全局接口请求日志完成报告

已完成统一请求日志链路：请求开始和结束均包含 traceId、脱敏手机号、方法、URI、参数类型、脱敏参数、状态码和耗时；业务错误为 WARN，系统错误为 ERROR，控制台按级别着色。

旧的 `LogInterceptor`、`WebInvokeTimeInterceptor` 和未注册的 `RepeatableFilter` 已移除。登录成功会把手机号写入 Sa-Token 会话，存量会话按需补齐。JSON 请求体支持安全复读和 64 KiB 日志上限，日志参数最长 8 KiB。

本次未提交、未部署；工作树中的其它既有修改均保留。
