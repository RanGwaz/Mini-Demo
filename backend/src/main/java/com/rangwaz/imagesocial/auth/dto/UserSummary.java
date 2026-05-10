package com.rangwaz.imagesocial.auth.dto;

/**
 * 用户信息摘要 VO，用于 JWT 响应及对外展示。
 * userNo：对外唯一用户ID（不暴露自增主键）。
 */
public record UserSummary(
        Long id,
        String username,
        String userNo,
        String nickname,
        String avatarUrl,
        String backgroundUrl,
        String bio,
        String roles) {
}
