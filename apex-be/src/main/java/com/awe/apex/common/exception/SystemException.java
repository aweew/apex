package com.awe.apex.common.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 系统异常
 *
 * @author Awe
 * @since 2025/9/9 11:17
 */
@Getter
public class SystemException extends RuntimeException {

    /**
     * 错误码
     */
    private final int code = 500;

    @Serial
    private static final long serialVersionUID = 4265620907777268105L;

    public SystemException() {
        super();
    }

    public SystemException(String message) {
        super(message);
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
    }

    public SystemException(Throwable cause) {
        super(cause);
    }

    protected SystemException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
