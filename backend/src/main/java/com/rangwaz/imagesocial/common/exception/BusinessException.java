package com.rangwaz.imagesocial.common.exception;

import com.rangwaz.imagesocial.common.api.ErrorCode;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 使用自定义消息覆盖枚举默认消息，适合携带动态内容时使用 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** 向后兼容：仅传 message 时归类为通用业务错误 */
    public BusinessException(String message) {
        super(message);
        this.errorCode = ErrorCode.BUSINESS_ERROR;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
