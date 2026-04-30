package com.rangwaz.imagesocial.common.api;

import java.time.Instant;

/**
 * 统一 HTTP 响应结构。
 * - code：业务错误码，成功时为 "0"
 * - data：响应负载，失败时为 null
 * - message：人类可读描述
 */
public record ApiResponse<T>(
        boolean success,
        String code,
        T data,
        String message,
        Instant timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, ErrorCode.SUCCESS.getCode(), data, "OK", Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, ErrorCode.SUCCESS.getCode(), data, message, Instant.now());
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getCode(), null, errorCode.getMessage(), Instant.now());
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.getCode(), null, message, Instant.now());
    }

    // ---- 向后兼容旧调用 ----

    public static <T> ApiResponse<T> success(T data) {
        return ok(data);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ok(data, message);
    }

    public static <T> ApiResponse<T> failure(String message) {
        return fail(ErrorCode.BUSINESS_ERROR, message);
    }
}
