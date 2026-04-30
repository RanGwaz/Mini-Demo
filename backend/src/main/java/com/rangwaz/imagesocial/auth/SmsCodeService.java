package com.rangwaz.imagesocial.auth;

import com.rangwaz.imagesocial.common.api.ErrorCode;
import com.rangwaz.imagesocial.common.exception.BusinessException;
import java.security.SecureRandom;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 短信验证码服务。
 *
 * <p>Redis Key 设计：
 * <ul>
 *   <li>{@code sms:code:{phone}}  → 验证码明文，TTL 5 分钟</li>
 *   <li>{@code sms:limit:{phone}} → 发送计数，TTL 1 分钟（限流窗口）</li>
 * </ul>
 *
 * <p>生产环境需将 {@link #doSend} 替换为真实短信 SDK 调用。
 */
@Service
public class SmsCodeService {

    private static final Logger log = LoggerFactory.getLogger(SmsCodeService.class);

    /** 验证码有效期 */
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    /** 限流窗口（每分钟最多发 N 次） */
    private static final Duration LIMIT_WINDOW = Duration.ofMinutes(1);
    /** 每窗口内最大发送次数 */
    private static final int MAX_SEND_PER_WINDOW = 1;
    /** 验证码位数 */
    private static final int CODE_LENGTH = 6;

    private static final String CODE_PREFIX  = "sms:code:";
    private static final String LIMIT_PREFIX = "sms:limit:";

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public SmsCodeService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 生成并发送验证码。
     * 同一手机号 1 分钟内只能发 {@value MAX_SEND_PER_WINDOW} 次。
     */
    public void sendCode(String phone) {
        String limitKey = LIMIT_PREFIX + phone;
        Long count = redis.opsForValue().increment(limitKey);
        if (count != null && count == 1) {
            // 第一次写入时设置过期
            redis.expire(limitKey, LIMIT_WINDOW);
        }
        if (count != null && count > MAX_SEND_PER_WINDOW) {
            throw new BusinessException(ErrorCode.SMS_SEND_TOO_FREQUENT);
        }

        String code = generateCode();
        redis.opsForValue().set(CODE_PREFIX + phone, code, CODE_TTL);
        doSend(phone, code);
    }

    /**
     * 校验验证码。校验通过后立即删除（一次性），防止重放攻击。
     *
     * @throws BusinessException 验证码错误或已过期
     */
    public void verifyAndConsume(String phone, String inputCode) {
        String key = CODE_PREFIX + phone;
        String stored = redis.opsForValue().get(key);
        if (stored == null || !stored.equals(inputCode.trim())) {
            throw new BusinessException(ErrorCode.SMS_CODE_INVALID);
        }
        redis.delete(key);
    }

    // -------------------------------------------------------------------------
    // 私有方法
    // -------------------------------------------------------------------------

    private String generateCode() {
        int max = (int) Math.pow(10, CODE_LENGTH);
        return String.format("%0" + CODE_LENGTH + "d", random.nextInt(max));
    }

    /**
     * 实际短信发送逻辑。
     * 开发环境直接打印，生产环境替换为短信 SDK（阿里云/腾讯云等）。
     */
    private void doSend(String phone, String code) {
        // TODO: 替换为真实短信 SDK 调用
        // 示例（阿里云）：
        //   SendSmsRequest req = new SendSmsRequest()
        //       .setPhoneNumbers(phone)
        //       .setSignName("ImageSocial")
        //       .setTemplateCode("SMS_XXXXXX")
        //       .setTemplateParam("{\"code\":\"" + code + "\"}");
        //   smsClient.sendSms(req);
        log.info("[SMS-DEV] phone={} code={}", phone, code);
    }
}
