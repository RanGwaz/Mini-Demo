package com.rangwaz.imagesocial.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 发送短信验证码请求。
 * phone 字段传明文手机号，后端不落库，只用于发送短信。
 */
public record SendSmsCodeRequest(
        @NotBlank
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone
) {
}
