# 全局接口请求日志设计

## 目标

所有 HTTP 请求从进入应用到完成响应均有统一日志。日志前缀包含 `traceId` 和脱敏手机号，正文包含请求方法、URI、参数类型、脱敏参数、HTTP 状态码和耗时。正常请求使用 INFO，客户端或业务错误使用 WARN，系统错误使用 ERROR。

## 方案

使用一个最早执行的 `OncePerRequestFilter` 统一处理请求链路，替换现有分散的 `LogInterceptor` 和 `WebInvokeTimeInterceptor`。过滤器负责校验或生成 traceId、解析登录手机号、记录开始/结束日志，并在 `finally` 中清理 MDC。JSON 请求体仅在已知长度且不超过 64 KiB 时缓存，保证 Controller 可以重复读取；更大的请求体不读取，只记录省略提示。

手机号优先从 Sa-Token 会话读取。登录时将手机号写入会话；兼容存量登录态，会话中缺失时按用户 ID 查询一次并回填。未登录的登录/注册请求从参数中识别手机号。日志中的手机号显示为 `138****1234`。

参数按 JSON 树或请求参数结构递归脱敏。密码、Token、Cookie、签名等完全隐藏，手机号和身份证保留必要的首尾字符；解析失败的 JSON 不回退打印原文。日志正文最长 8 KiB，避免超大参数污染日志。

异常处理器通过 request attribute 告知过滤器业务错误或系统错误级别，从而兼容当前部分业务异常仍返回 HTTP 200 的响应约定。控制台继续使用 Logback `%highlight` 按日志级别着色，文件日志保持纯文本。

## 验收

- 客户端合法 traceId 可透传，非法值重新生成，响应头返回最终 traceId。
- 每条请求日志均带 traceId 和脱敏 phone，结束后 MDC 无残留。
- JSON、query/form 参数均脱敏，且 JSON body 不影响 Controller 读取。
- 2xx/3xx 为 INFO，4xx/业务异常为 WARN，5xx/系统异常为 ERROR。
- 请求链抛出异常时仍输出结束日志。
