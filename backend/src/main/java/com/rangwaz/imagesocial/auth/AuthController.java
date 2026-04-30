package com.rangwaz.imagesocial.auth;

import com.rangwaz.imagesocial.auth.dto.AuthTokenResponse;
import com.rangwaz.imagesocial.auth.dto.BindPhoneRequest;
import com.rangwaz.imagesocial.auth.dto.ChangeUserNoRequest;
import com.rangwaz.imagesocial.auth.dto.LoginRequest;
import com.rangwaz.imagesocial.auth.dto.OAuthCallbackRequest;
import com.rangwaz.imagesocial.auth.dto.PhoneSmsLoginRequest;
import com.rangwaz.imagesocial.auth.dto.RegisterRequest;
import com.rangwaz.imagesocial.auth.dto.SendSmsCodeRequest;
import com.rangwaz.imagesocial.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SmsCodeService smsCodeService;

    public AuthController(AuthService authService, SmsCodeService smsCodeService) {
        this.authService = authService;
        this.smsCodeService = smsCodeService;
    }

    // ------------------------------------------------------------------
    // 用户名 + 密码
    // ------------------------------------------------------------------

    /** 用户名注册 */
    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request), "注册成功");
    }

    /**
     * 用户名 + 密码登录。
     * 生产环境建议在 Nginx/Gateway 层加 IP 限流；此处记录 IP 供审计使用。
     */
    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(authService.login(request, resolveClientIp(httpRequest)), "登录成功");
    }

    // ------------------------------------------------------------------
    // 手机号 + 验证码
    // ------------------------------------------------------------------

    /**
     * 发送短信验证码。
     * Redis 限流：同一手机号 1 分钟内最多发 1 次（SmsCodeService 内控制）。
     */
    @PostMapping("/sms/send")
    public ApiResponse<Void> sendSmsCode(@Valid @RequestBody SendSmsCodeRequest request) {
        smsCodeService.sendCode(request.phone());
        return ApiResponse.success(null, "验证码已发送，请注意查收");
    }

    /**
     * 手机号 + 验证码登录（未注册自动注册）。
     */
    @PostMapping("/sms/login")
    public ApiResponse<AuthTokenResponse> phoneSmsLogin(
            @Valid @RequestBody PhoneSmsLoginRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.success(
                authService.phoneSmsLogin(request, resolveClientIp(httpRequest)), "登录成功");
    }

    // ------------------------------------------------------------------
    // 第三方 OAuth（预留）
    // ------------------------------------------------------------------

    /** 第三方 OAuth 回调登录入口（微信/Google等，待接入 SDK 后实现） */
    @PostMapping("/oauth/callback")
    public ApiResponse<AuthTokenResponse> oauthCallback(
            @Valid @RequestBody OAuthCallbackRequest request) {
        return ApiResponse.success(
                authService.oauthLogin(request, null), "登录成功");
    }

    // ------------------------------------------------------------------
    // 已登录操作
    // ------------------------------------------------------------------

    /** 获取当前登录用户信息 */
    @GetMapping("/me")
    public ApiResponse<AuthTokenResponse> me() {
        return ApiResponse.success(authService.currentUser(SecurityUtils.currentUserIdOrThrow()));
    }

    /** 刷新 Token（无状态 JWT，直接重新签发） */
    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh() {
        return ApiResponse.success(authService.currentUser(SecurityUtils.currentUserIdOrThrow()), "刷新成功");
    }

    /**
     * 退出登录。
     * 纯 JWT 无状态架构下服务端无需任何操作；
     * 前端删除本地 token 即完成登出。
     * 此接口保留用于：未来接入 Redis token 黑名单、审计日志等扩展点。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        // 扩展点：将当前 JWT 加入 Redis 黑名单（jti → 过期时间）
        return ApiResponse.success(null, "已退出登录");
    }

    /** 绑定 / 换绑手机号（需已登录） */
    @PostMapping("/bind-phone")
    public ApiResponse<Void> bindPhone(@Valid @RequestBody BindPhoneRequest request) {
        authService.bindPhone(SecurityUtils.currentUserIdOrThrow(), request);
        return ApiResponse.success(null, "手机号绑定成功");
    }

    /** 修改对外用户ID（半年一次限制） */
    @PutMapping("/user-no")
    public ApiResponse<AuthTokenResponse> changeUserNo(@Valid @RequestBody ChangeUserNoRequest request) {
        return ApiResponse.success(
                authService.changeUserNo(SecurityUtils.currentUserIdOrThrow(), request), "用户ID修改成功");
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 从请求头中提取真实客户端 IP（兼容反向代理） */
    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
