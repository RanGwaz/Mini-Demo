package com.rangwaz.imagesocial.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 绑定/换绑手机号请求（需已登录） */
public record BindPhoneRequest(
        @NotBlank
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @NotBlank
        @Size(min = 4, max = 8)
        String code
) {
}
