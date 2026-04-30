package com.rangwaz.imagesocial.auth;

import com.rangwaz.imagesocial.auth.dto.AuthTokenResponse;
import com.rangwaz.imagesocial.auth.dto.BindPhoneRequest;
import com.rangwaz.imagesocial.auth.dto.ChangeUserNoRequest;
import com.rangwaz.imagesocial.auth.dto.LoginRequest;
import com.rangwaz.imagesocial.auth.dto.OAuthCallbackRequest;
import com.rangwaz.imagesocial.auth.dto.PhoneSmsLoginRequest;
import com.rangwaz.imagesocial.auth.dto.RegisterRequest;
import com.rangwaz.imagesocial.common.api.ErrorCode;
import com.rangwaz.imagesocial.common.exception.BusinessException;
import com.rangwaz.imagesocial.config.JwtProperties;
import com.rangwaz.imagesocial.domain.entity.User;
import com.rangwaz.imagesocial.user.UserService;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final SmsCodeService smsCodeService;

    public AuthService(UserService userService,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       JwtProperties jwtProperties,
                       SmsCodeService smsCodeService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.smsCodeService = smsCodeService;
    }

    // -------------------------------------------------------------------------
    // 用户名 + 密码 注册
    // -------------------------------------------------------------------------

    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        if (userService.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setRoles("ROLE_USER");
        user.setStatus("ACTIVE");
        user.setUserNo(generateUniqueUserNo());
        user.setUserNoUpdatedAt(LocalDateTime.now());
        userService.save(user);
        return buildToken(user);
    }

    // -------------------------------------------------------------------------
    // 用户名 + 密码 登录
    // -------------------------------------------------------------------------

    @Transactional
    public AuthTokenResponse login(LoginRequest request, String ip) {
        User user = userService.requireByUsername(request.username());
        assertNotBanned(user);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.WRONG_CREDENTIALS);
        }
        refreshLoginMeta(user, ip);
        return buildToken(user);
    }

    // -------------------------------------------------------------------------
    // 手机号 + 验证码 登录（未注册自动注册）
    // -------------------------------------------------------------------------

    @Transactional
    public AuthTokenResponse phoneSmsLogin(PhoneSmsLoginRequest request, String ip) {
        smsCodeService.verifyAndConsume(request.phone(), request.code());

        User user = userService.findByPhone(request.phone());
        // 每次登录都轮换 salt+hash，防止旧 salt 泄露
        String newSalt = PhoneHashUtil.generateSalt();
        String newHash = PhoneHashUtil.hash(request.phone(), newSalt);

        if (user == null) {
            // 手机号未注册 → 自动注册
            user = new User();
            String nickname = (request.nickname() != null && !request.nickname().isBlank())
                    ? request.nickname()
                    : "用户" + request.phone().substring(7);
            user.setUsername("phone_" + request.phone());
            user.setPasswordHash("");
            user.setNickname(nickname);
            user.setRoles("ROLE_USER");
            user.setStatus("ACTIVE");
            user.setUserNo(generateUniqueUserNo());
            user.setUserNoUpdatedAt(LocalDateTime.now());
            user.setPhoneSalt(newSalt);
            user.setPhoneHash(newHash);
            userService.save(user);
        } else {
            assertNotBanned(user);
            user.setPhoneSalt(newSalt);
            user.setPhoneHash(newHash);
        }
        refreshLoginMeta(user, ip);
        return buildToken(user);
    }

    // -------------------------------------------------------------------------
    // 绑定 / 换绑 手机号（需已登录）
    // -------------------------------------------------------------------------

    @Transactional
    public void bindPhone(Long userId, BindPhoneRequest request) {
        smsCodeService.verifyAndConsume(request.phone(), request.code());

        // 检查手机号是否已被其他账号绑定
        if (userService.existsByPhone(request.phone(), userId)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_BOUND);
        }

        String salt = PhoneHashUtil.generateSalt();
        String hash = PhoneHashUtil.hash(request.phone(), salt);

        User user = userService.requireById(userId);
        user.setPhoneSalt(salt);
        user.setPhoneHash(hash);
        userService.update(user);
    }

    // -------------------------------------------------------------------------
    // 修改用户ID（半年一次）
    // -------------------------------------------------------------------------

    @Transactional
    public AuthTokenResponse changeUserNo(Long userId, ChangeUserNoRequest request) {
        User user = userService.requireById(userId);

        // 检查半年冷却
        if (user.getUserNoUpdatedAt() != null) {
            LocalDateTime cooldownEnd = user.getUserNoUpdatedAt().plusDays(180);
            if (LocalDateTime.now().isBefore(cooldownEnd)) {
                throw new BusinessException(ErrorCode.USER_NO_CHANGE_TOO_SOON,
                        "用户ID下次可修改时间：" + cooldownEnd.toLocalDate());
            }
        }

        // 检查唯一性
        if (userService.existsByUserNo(request.userNo())) {
            throw new BusinessException(ErrorCode.USER_NO_ALREADY_EXISTS);
        }

        user.setUserNo(request.userNo());
        user.setUserNoUpdatedAt(LocalDateTime.now());
        userService.update(user);
        return buildToken(user);
    }

    // -------------------------------------------------------------------------
    // 第三方 OAuth 登录（预留接口，待接入真实 SDK）
    // -------------------------------------------------------------------------

    public AuthTokenResponse oauthLogin(OAuthCallbackRequest request, String ignoredIp) {
        // TODO: 根据 provider 调用对应 OAuth SDK 换取 openId
        // 示例流程：
        //   1. 用 code 换 access_token
        //   2. 用 access_token 获取 openId / sub
        //   3. 查 user_oauth 表，找到关联 user_id
        //   4. 若未绑定则自动注册新用户并写入 user_oauth
        //   5. 返回 JWT
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "第三方登录暂未开放，敬请期待");
    }

    // -------------------------------------------------------------------------
    // 当前用户信息
    // -------------------------------------------------------------------------

    public AuthTokenResponse currentUser(Long userId) {
        User user = userService.requireById(userId);
        return buildToken(user);
    }

    // -------------------------------------------------------------------------
    // 私有工具方法
    // -------------------------------------------------------------------------

    private AuthTokenResponse buildToken(User user) {
        return new AuthTokenResponse(
                jwtTokenService.generateAccessToken(user),
                "Bearer",
                jwtProperties.accessTokenExpireSeconds(),
                userService.toSummary(user));
    }

    private void assertNotBanned(User user) {
        if ("BANNED".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_BANNED);
        }
    }

    private void refreshLoginMeta(User user, String ip) {
        // 重新查询以获取最新的版本号，避免乐观锁冲突
        User freshUser = userService.requireById(user.getId());
        freshUser.setLastLoginAt(LocalDateTime.now());
        freshUser.setLoginIp(ip);
        userService.update(freshUser);
    }

    private String generateUniqueUserNo() {
        for (int i = 0; i < 10; i++) {
            String candidate = UserNoGenerator.generate();
            if (!userService.existsByUserNo(candidate)) {
                return candidate;
            }
        }
        // 极低概率：10次全碰撞，抛异常让调用方重试
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "用户ID生成失败，请重试");
    }
}
