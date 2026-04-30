package com.rangwaz.imagesocial.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 修改对外用户ID请求（半年限制一次） */
public record ChangeUserNoRequest(
        @NotBlank
        @Pattern(
                regexp = "^[A-Za-z0-9_\\-]{8}$",
                message = "用户ID必须为8位，只能包含字母、数字、下划线和连字符"
        )
        String userNo
) {
}
