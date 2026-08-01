package com.awe.apex.common.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常
 *
 * @author Awe
 * @since 2025/9/9 11:06
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 282422271692067022L;

    /**
     * 错误码
     */
    private Integer code;

    /**
     * 错误明细
     * 比如：内部调试错误
     */
    private String detailMessage;

    public BusinessException() {
        super();
    }

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    public BusinessException(Throwable cause) {
        super(cause);
    }

    protected BusinessException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
