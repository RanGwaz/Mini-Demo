package com.rangwaz.imagesocial.auth;

import java.security.SecureRandom;

/**
 * 对外用户唯一编号生成器。
 *
 * <p>生成规则：8位，仅由字母（大写）+ 数字组成，排除易混淆字符（0/O/I/1/L）。
 * 字符集 32 个字符，8位 → 32^8 ≈ 10^12 种组合，碰撞率极低。
 * <p>应用层在调用前检查 DB 唯一性，极低概率碰撞时重试即可。
 */
public final class UserNoGenerator {

    /** 去除易混淆字符 0/O/1/I/L 后的安全字符集 */
    private static final char[] CHARS =
            "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray(); // 31 chars

    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private UserNoGenerator() {
    }

    /** 生成一个 8 位随机用户编号，如 "QR7M3KP2" */
    public static String generate() {
        char[] result = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            result[i] = CHARS[RANDOM.nextInt(CHARS.length)];
        }
        return new String(result);
    }
}
