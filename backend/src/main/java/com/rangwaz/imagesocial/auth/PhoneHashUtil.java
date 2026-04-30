package com.rangwaz.imagesocial.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 手机号哈希工具。
 *
 * <p>设计：SHA-256(phone + salt)，手机号明文永不落库。
 * <ul>
 *   <li>salt 每次绑定时随机重新生成，防止彩虹表攻击</li>
 *   <li>使用 UUID（去横线）作为 salt，长度 32 字符</li>
 * </ul>
 */
public final class PhoneHashUtil {

    private PhoneHashUtil() {
    }

    /** 生成随机 salt（32位十六进制字符串） */
    public static String generateSalt() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 计算手机号哈希。
     *
     * @param phone 明文手机号
     * @param salt  随机盐
     * @return SHA-256(phone + salt) 的十六进制字符串（64字符）
     */
    public static String hash(String phone, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((phone + salt).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
