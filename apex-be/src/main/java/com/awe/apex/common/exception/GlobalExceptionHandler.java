package com.awe.apex.common.exception;

import com.awe.apex.common.api.Result;
import com.awe.apex.common.constant.ErrorCodeEnum;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
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

    // 业务异常
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        Integer code = e.getCode();
        if (code == null) {
            code = ErrorCodeEnum.FAILURE.getCode();
        }
        return Result.failure(code, e.getMessage());
    }

    // 系统异常
    @ExceptionHandler(SystemException.class)
    public Result<?> handleSystemException(SystemException e) {
        Integer code = e.getCode();
        if (code == null) {
            code = ErrorCodeEnum.ERROR.getCode();
        }
        return Result.failure(code, "系统异常，请联系管理员");
    }

    // 参数校验异常（如 @Valid 抛出的）
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleValidationException(ConstraintViolationException e) {
        return Result.failure(ErrorCodeEnum.FAILURE.getCode(), e.getMessage());
    }

    // 参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        Map<String, String> errors = bindingResult.getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (msg1, msg2) -> msg1 // 字段重复时取第一个
                ));

        return Result.failure(ErrorCodeEnum.FAILURE.getCode(), "参数校验失败", errors);
    }

    // 参数校验异常（如 @Valid 抛出的）
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return Result.failure(ErrorCodeEnum.FAILURE.getCode(), e.getMessage());
    }

    // 兜底异常
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常：", e);
        return Result.failure(ErrorCodeEnum.ERROR.getCode(), "未知异常，请联系管理员");
    }

}
