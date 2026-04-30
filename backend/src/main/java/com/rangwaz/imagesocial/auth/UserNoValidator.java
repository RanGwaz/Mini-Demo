package com.rangwaz.imagesocial.auth;

import java.util.regex.Pattern;

/**
 * user_no 格式校验工具类。
 *
 * <p>规则：长度恰好 8 位，字符范围：字母（大小写）、数字、下划线、连字符。
 * 示例合法值："AB3D-E_F2"、"QR7M3KP2"。
 */
public final class UserNoValidator {

    /** 合法 user_no 的正则：8位，[A-Za-z0-9_-] */
    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9_\\-]{8}$");

    private UserNoValidator() {
    }

    /**
     * 校验 user_no 格式是否合法。
     *
     * @param userNo 待校验的用户编号
     * @return {@code true} 表示格式合法
     */
    public static boolean isValid(String userNo) {
        if (userNo == null) {
            return false;
        }
        return PATTERN.matcher(userNo).matches();
    }

    /**
     * 断言 user_no 格式合法，不合法则抛出 {@link IllegalArgumentException}。
     *
     * @param userNo 待校验的用户编号
     * @throws IllegalArgumentException 格式不合法时抛出
     */
    public static void assertValid(String userNo) {
        if (!isValid(userNo)) {
            throw new IllegalArgumentException(
                    "用户ID格式不合法：必须为8位，只能包含字母、数字、下划线和连字符，实际值：" + userNo);
        }
    }
}
