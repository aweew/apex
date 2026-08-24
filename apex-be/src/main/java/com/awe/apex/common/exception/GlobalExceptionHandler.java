package com.awe.apex.common.exception;

import com.awe.apex.common.api.Result;
import com.awe.apex.common.constant.Constants;
import com.awe.apex.common.constant.ErrorCodeEnum;
import cn.dev33.satoken.exception.NotLoginException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 统一异常处理器
 *
 * @author Awe
 * @since 2025/9/9 11:35
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理登录态失效
     *
     * @param exception 登录态异常
     * @param request   HTTP 请求
     * @return 未授权响应
     */
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(NotLoginException.class)
    public Result<?> handleNotLoginException(NotLoginException exception, HttpServletRequest request) {
        markRequestLevel(request, Constants.LOG_LEVEL_WARN);
        log.warn("登录态失效，类型={}", exception.getType());
        return Result.failure(HttpStatus.UNAUTHORIZED.value(), "登录已失效，请重新登录");
    }

    /**
     * 保持不存在资源的 HTTP 语义，避免客户端将 404 误判为业务成功。
     *
     * @param exception 资源未找到异常
     * @return 未找到响应
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<?> handleNoResourceFoundException(NoResourceFoundException exception) {
        return Result.failure(HttpStatus.NOT_FOUND.value(), "请求资源不存在");
    }

    /**
     * 处理业务异常。
     *
     * @param exception 业务异常
     * @param request   HTTP 请求
     * @return 失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException exception, HttpServletRequest request) {
        markRequestLevel(request, Constants.LOG_LEVEL_WARN);
        log.warn("业务请求失败：{}", exception.getMessage());
        Integer code = exception.getCode();
        if (Objects.isNull(code)) {
            code = ErrorCodeEnum.FAILURE.getCode();
        }
        return Result.failure(code, exception.getMessage());
    }

    /**
     * 处理系统异常。
     *
     * @param exception 系统异常
     * @param request   HTTP 请求
     * @return 失败响应
     */
    @ExceptionHandler(SystemException.class)
    public Result<?> handleSystemException(SystemException exception, HttpServletRequest request) {
        markRequestLevel(request, Constants.LOG_LEVEL_ERROR);
        log.error("系统异常：", exception);
        Integer code = exception.getCode();
        if (Objects.isNull(code)) {
            code = ErrorCodeEnum.ERROR.getCode();
        }
        return Result.failure(code, "系统异常，请联系管理员");
    }

    /**
     * 处理方法参数约束异常。
     *
     * @param exception 参数约束异常
     * @param request   HTTP 请求
     * @return 失败响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleValidationException(ConstraintViolationException exception, HttpServletRequest request) {
        markRequestLevel(request, Constants.LOG_LEVEL_WARN);
        return Result.failure(ErrorCodeEnum.FAILURE.getCode(), exception.getMessage());
    }

    /**
     * 处理请求对象字段校验异常。
     *
     * @param exception 字段校验异常
     * @param request   HTTP 请求
     * @return 失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception,
                                                           HttpServletRequest request) {
        markRequestLevel(request, Constants.LOG_LEVEL_WARN);
        BindingResult bindingResult = exception.getBindingResult();
        Map<String, String> errors = bindingResult.getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (msg1, msg2) -> msg1 // 字段重复时取第一个
                ));

        return Result.failure(ErrorCodeEnum.FAILURE.getCode(), "参数校验失败", errors);
    }

    /**
     * 处理无法读取的 HTTP 消息。
     *
     * @param exception 消息读取异常
     * @param request   HTTP 请求
     * @return 失败响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException exception,
                                                           HttpServletRequest request) {
        markRequestLevel(request, Constants.LOG_LEVEL_WARN);
        return Result.failure(ErrorCodeEnum.FAILURE.getCode(), exception.getMessage());
    }

    /**
     * 客户端主动断开（超时/刷新/取消请求）：业务可能已跑完，勿当系统故障刷 ERROR
     *
     * @param exception 客户端断开异常
     * @param request   HTTP 请求
     */
    @ExceptionHandler({ClientAbortException.class, AsyncRequestNotUsableException.class})
    public void handleClientAbort(Exception exception, HttpServletRequest request) {
        markRequestLevel(request, Constants.LOG_LEVEL_WARN);
        log.warn("客户端已断开连接，响应未写完：{}", exception.getMessage());
    }

    /**
     * 处理未分类异常。
     *
     * @param exception 未分类异常
     * @param request   HTTP 请求
     * @return 失败响应
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception exception, HttpServletRequest request) {
        if (isClientAbort(exception)) {
            markRequestLevel(request, Constants.LOG_LEVEL_WARN);
            log.warn("客户端已断开连接，响应未写完：{}", exception.getMessage());
            return null;
        }
        markRequestLevel(request, Constants.LOG_LEVEL_ERROR);
        log.error("系统异常：", exception);
        return Result.failure(ErrorCodeEnum.ERROR.getCode(), "未知异常，请联系管理员");
    }

    private void markRequestLevel(HttpServletRequest request, String level) {
        request.setAttribute(Constants.REQUEST_LOG_LEVEL, level);
    }

    private boolean isClientAbort(Throwable exception) {
        Throwable currentException = exception;
        while (Objects.nonNull(currentException)) {
            if (currentException instanceof ClientAbortException
                    || currentException instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String message = currentException.getMessage();
            if (currentException instanceof IOException && Objects.nonNull(message)
                    && (message.contains("中止了一个已建立的连接")
                    || message.contains("Broken pipe")
                    || message.contains("Connection reset by peer"))) {
                return true;
            }
            currentException = currentException.getCause();
        }
        return false;
    }

}
