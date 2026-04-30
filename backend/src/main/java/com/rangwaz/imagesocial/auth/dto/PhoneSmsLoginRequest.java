package com.rangwaz.imagesocial.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 手机号 + 验证码登录/注册请求。
 * 若手机号未注册则自动注册新账号。
 */
public record PhoneSmsLoginRequest(
        @NotBlank
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank
        @Size(min = 4, max = 8)
        String code,

        /** 自动注册时使用的昵称，可选；不传则系统自动生成 */
        String nickname
) {
}
