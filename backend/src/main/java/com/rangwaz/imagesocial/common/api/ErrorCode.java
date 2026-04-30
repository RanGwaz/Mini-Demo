package com.rangwaz.imagesocial.common.api;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码定义。
 * 编码规则：前缀字母标识模块，三位数字标识具体错误。
 * A = 通用/系统, U = 用户, P = 帖子, I = 互动, B = 业务兜底
 */
public enum ErrorCode {

    // ---- 通用 ----
    SUCCESS("0", "成功", HttpStatus.OK),
    PARAM_INVALID("A001", "请求参数不合法", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("A002", "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("A003", "没有权限执行此操作", HttpStatus.FORBIDDEN),
    NOT_FOUND("A004", "资源不存在", HttpStatus.NOT_FOUND),
    INTERNAL_ERROR("A999", "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),

    // ---- 用户模块 ----
    USER_NOT_FOUND("U001", "用户不存在", HttpStatus.NOT_FOUND),
    USERNAME_ALREADY_EXISTS("U002", "用户名已被注册", HttpStatus.CONFLICT),
    WRONG_CREDENTIALS("U003", "用户名或密码错误", HttpStatus.UNAUTHORIZED),
    USER_BANNED("U004", "账号已被封禁，如有疑问请联系客服", HttpStatus.FORBIDDEN),
    PHONE_ALREADY_BOUND("U005", "该手机号已被其他账号绑定", HttpStatus.CONFLICT),
    SMS_CODE_INVALID("U006", "验证码错误或已过期", HttpStatus.BAD_REQUEST),
    SMS_SEND_TOO_FREQUENT("U007", "发送过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    USER_NO_CHANGE_TOO_SOON("U008", "用户ID每半年只能修改一次", HttpStatus.BAD_REQUEST),
    USER_NO_ALREADY_EXISTS("U009", "该用户ID已被使用，请换一个", HttpStatus.CONFLICT),
    OAUTH_ALREADY_BOUND("U010", "该第三方账号已绑定其他用户", HttpStatus.CONFLICT),

    // ---- 帖子模块 ----
    POST_NOT_FOUND("P001", "帖子不存在", HttpStatus.NOT_FOUND),
    POST_FORBIDDEN("P002", "无权操作该帖子", HttpStatus.FORBIDDEN),

    // ---- 互动模块 ----
    ALREADY_LIKED("I001", "已经点赞过了", HttpStatus.CONFLICT),
    ALREADY_FAVORITED("I002", "已经收藏过了", HttpStatus.CONFLICT),
    ALREADY_FOLLOWING("I003", "已经关注了", HttpStatus.CONFLICT),
    CANNOT_FOLLOW_SELF("I004", "不能关注自己", HttpStatus.BAD_REQUEST),
    NOT_LIKED("I005", "尚未点赞", HttpStatus.BAD_REQUEST),
    NOT_FAVORITED("I006", "尚未收藏", HttpStatus.BAD_REQUEST),
    NOT_FOLLOWING("I007", "尚未关注", HttpStatus.BAD_REQUEST),

    // ---- 业务兜底（向后兼容旧的 new BusinessException(message) 调用）----
    BUSINESS_ERROR("B001", "业务处理失败", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
