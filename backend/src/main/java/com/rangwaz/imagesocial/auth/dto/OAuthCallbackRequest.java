package com.rangwaz.imagesocial.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 第三方 OAuth 登录回调请求（微信/Google 等）。
 * 前端获取到 code 后传给后端，后端用 code 换 access_token。
 */
public record OAuthCallbackRequest(
        /** 第三方平台标识：wechat / google / github / apple */
        @NotBlank String provider,

        /** 第三方平台回调 code（授权码） */
        @NotBlank String code,

        /** 微信等平台需要的 state 防 CSRF 参数 */
        String state
) {
}
