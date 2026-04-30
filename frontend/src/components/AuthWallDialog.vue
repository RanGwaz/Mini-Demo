<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { HttpError } from '../services/http'
import { api } from '../services/api'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()
const router = useRouter()

const activeTab = ref<'phone' | 'password'>('phone')
const pwdTab = ref<'login' | 'register'>('login')
const submitting = ref(false)
const loginSucceeded = ref(false)

// ── 手机验证码 ──
const smsForm = reactive({ phone: '', code: '', nickname: '' })
const smsCooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null

async function sendCode() {
  if (!/^1[3-9]\d{9}$/.test(smsForm.phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  try {
    await api.sendSmsCode(smsForm.phone)
    ElMessage.success('验证码已发送（开发环境请查看后端日志）')
    smsCooldown.value = 60
    cooldownTimer = setInterval(() => {
      smsCooldown.value--
      if (smsCooldown.value <= 0 && cooldownTimer) { clearInterval(cooldownTimer); cooldownTimer = null }
    }, 1000)
  } catch (e) { ElMessage.error(resolveError(e)) }
}

async function submitSmsLogin() {
  if (!smsForm.phone || !smsForm.code) { ElMessage.warning('请填写手机号和验证码'); return }
  submitting.value = true
  try {
    const data = await api.phoneSmsLogin({ phone: smsForm.phone, code: smsForm.code, nickname: smsForm.nickname || undefined })
    authStore.setSession(data)
    ElMessage.success('登录成功')
    await finishLogin()
  } catch (e) { ElMessage.error(resolveError(e)) }
  finally { submitting.value = false }
}

// ── 用户名密码登录 ──
const loginForm = reactive({ username: '', password: '' })

async function submitLogin() {
  if (!loginForm.username.trim() || !loginForm.password.trim()) { ElMessage.warning('请输入用户名和密码'); return }
  submitting.value = true
  try {
    const data = await api.login({ username: loginForm.username.trim(), password: loginForm.password.trim() })
    authStore.setSession(data)
    ElMessage.success('登录成功')
    await finishLogin()
  } catch (e) { ElMessage.error(resolveError(e)) }
  finally { submitting.value = false }
}

// ── 用户名密码注册 ──
const registerForm = reactive({ username: '', password: '', nickname: '' })

async function submitRegister() {
  if (!registerForm.username.trim() || !registerForm.password.trim()) { ElMessage.warning('请填写用户名和密码'); return }
  submitting.value = true
  try {
    const data = await api.register({
      username: registerForm.username.trim(),
      password: registerForm.password.trim(),
      nickname: registerForm.nickname.trim() || registerForm.username.trim(),
    })
    authStore.setSession(data)
    ElMessage.success('注册并登录成功')
    await finishLogin()
  } catch (e) { ElMessage.error(resolveError(e)) }
  finally { submitting.value = false }
}

watch(() => authStore.authModalOpen, (open) => { if (open) loginSucceeded.value = false })

function handleClosed() {
  authStore.afterAuthModalClosed(loginSucceeded.value)
  loginSucceeded.value = false
}

async function finishLogin() {
  loginSucceeded.value = true
  const path = authStore.pendingRedirect
  authStore.clearPendingRedirect()
  authStore.closeAuthModal()
  if (path) { try { await router.push(path) } catch { await router.push('/feed') } }
}

function resolveError(error: unknown): string {
  if (error instanceof HttpError) {
    const map: Record<string, string> = {
      U001: '用户不存在', U002: '用户名已存在，请更换后重试', U003: '密码错误',
      U004: '账号已被封禁', U006: '验证码错误或已过期', U007: '发送过于频繁，请稍后再试', A001: '请检查输入内容格式',
    }
    return map[error.code ?? ''] ?? error.message ?? '操作失败'
  }
  return error instanceof Error ? error.message : '操作失败'
}
</script>

<template>
  <el-dialog :model-value="authStore.authModalOpen" class="auth-wall-dialog" modal-class="auth-wall-overlay"
    width="min(860px, 94vw)" top="6vh" :align-center="true" :show-close="true" :close-on-click-modal="true"
    :close-on-press-escape="true" :lock-scroll="false" append-to-body :destroy-on-close="false" title=" "
    @update:model-value="(v: boolean) => !v && authStore.closeAuthModal()" @closed="handleClosed">
    <div class="auth-wall">
      <!-- 左侧品牌区 -->
      <div class="auth-wall__left">
        <div class="auth-wall__badge">登录后推荐更懂你的笔记</div>
        <div class="auth-wall__qr-placeholder">
          <div class="auth-wall__qr-grid" />
          <p class="auth-wall__qr-hint">使用右侧方式登录 / 注册</p>
          <p class="auth-wall__qr-sub">新用户首次登录将自动创建账号</p>
        </div>
      </div>

      <!-- 右侧表单区 -->
      <div class="auth-wall__right">
        <h2 class="auth-wall__title">欢迎回来</h2>

        <el-tabs v-model="activeTab" stretch class="auth-tabs">

          <!-- 手机验证码 -->
          <el-tab-pane name="phone" label="手机验证码">
            <el-form class="auth-wall__form" label-position="top" @submit.prevent="submitSmsLogin">
              <el-form-item label="手机号">
                <el-input v-model="smsForm.phone" size="large" placeholder="请输入手机号" maxlength="11" clearable>
                  <template #prefix><span class="phone-prefix">+86</span></template>
                </el-input>
              </el-form-item>
              <el-form-item label="验证码">
                <div class="code-row">
                  <el-input v-model="smsForm.code" size="large" placeholder="6 位验证码" maxlength="6" class="code-input" />
                  <el-button class="send-code-btn" size="large" :disabled="smsCooldown > 0" @click="sendCode">
                    {{ smsCooldown > 0 ? `${smsCooldown}s` : '获取验证码' }}
                  </el-button>
                </div>
              </el-form-item>
              <el-form-item label="昵称（可选）">
                <el-input v-model="smsForm.nickname" size="large" placeholder="首次注册时使用，留空自动生成" />
              </el-form-item>
              <el-button class="auth-wall__submit" type="primary" size="large" :loading="submitting"
                native-type="submit" round @click="submitSmsLogin">
                登录 / 注册
              </el-button>
              <p class="auth-wall__footer-hint">未注册手机号将自动创建新账号</p>
            </el-form>
          </el-tab-pane>

          <!-- 用户名密码 -->
          <el-tab-pane name="password" label="用户名密码">
            <el-tabs v-model="pwdTab" class="sub-tabs">
              <el-tab-pane name="login" label="登录">
                <el-form class="auth-wall__form" label-position="top" @submit.prevent="submitLogin">
                  <el-form-item label="用户名">
                    <el-input v-model="loginForm.username" size="large" placeholder="用户名" clearable />
                  </el-form-item>
                  <el-form-item label="密码">
                    <el-input v-model="loginForm.password" size="large" type="password" show-password placeholder="密码"
                      clearable />
                  </el-form-item>
                  <el-button class="auth-wall__submit" type="primary" size="large" :loading="submitting"
                    native-type="submit" round @click="submitLogin">登录</el-button>
                </el-form>
              </el-tab-pane>
              <el-tab-pane name="register" label="注册">
                <el-form class="auth-wall__form" label-position="top" @submit.prevent="submitRegister">
                  <el-form-item label="用户名">
                    <el-input v-model="registerForm.username" size="large" placeholder="至少 4 位字符" clearable />
                  </el-form-item>
                  <el-form-item label="昵称">
                    <el-input v-model="registerForm.nickname" size="large" placeholder="选填" clearable />
                  </el-form-item>
                  <el-form-item label="密码">
                    <el-input v-model="registerForm.password" size="large" type="password" show-password
                      placeholder="至少 6 位字符" clearable />
                  </el-form-item>
                  <el-button class="auth-wall__submit" type="primary" size="large" :loading="submitting"
                    native-type="submit" round @click="submitRegister">注册并登录</el-button>
                </el-form>
              </el-tab-pane>
            </el-tabs>
          </el-tab-pane>

        </el-tabs>

        <!-- 第三方登录占位 -->
        <div class="oauth-row">
          <span class="oauth-sep"></span>
          <span class="oauth-sep-label">或</span>
          <span class="oauth-sep"></span>
        </div>
        <div class="oauth-btns">
          <el-tooltip content="微信登录（即将开放）" placement="top">
            <button class="oauth-btn" disabled><span class="oauth-icon wechat">W</span>微信</button>
          </el-tooltip>
          <el-tooltip content="Google 登录（即将开放）" placement="top">
            <button class="oauth-btn" disabled><span class="oauth-icon google">G</span>Google</button>
          </el-tooltip>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.auth-wall {
  display: flex;
  min-height: 420px;
}

/* ── 左侧 ── */
.auth-wall__left {
  width: 240px;
  flex-shrink: 0;
  background: linear-gradient(
    160deg,
    color-mix(in srgb, var(--brand-primary, #0f766e) 20%, var(--bg-solid, #fff)),
    color-mix(in srgb, var(--brand-accent, #ff6b35) 18%, var(--bg-solid, #fff))
  );
  border-radius: 10px;
  padding: 28px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

:global(html[data-theme='dark']) .auth-wall__left {
  background: linear-gradient(
    160deg,
    color-mix(in srgb, var(--brand-primary, #63e6d1) 16%, var(--bg-solid, #111722)),
    color-mix(in srgb, var(--brand-accent, #ff8c42) 14%, var(--bg-solid, #111722))
  );
}

.auth-wall__badge {
  font-size: 12px;
  color: color-mix(in srgb, var(--brand-primary, #0f766e) 78%, var(--text-primary, #0f172a));
  background: color-mix(in srgb, var(--brand-primary, #0f766e) 15%, transparent);
  border: 1px solid color-mix(in srgb, var(--brand-primary, #0f766e) 25%, var(--border-subtle));
  border-radius: 20px;
  padding: 4px 12px;
  text-align: center;
  line-height: 1.5;
}

.auth-wall__qr-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  flex: 1;
  justify-content: center;
}

.auth-wall__qr-grid {
  width: 120px;
  height: 120px;
  background:
    repeating-linear-gradient(0deg, rgba(165, 180, 252, 0.15) 0px, rgba(165, 180, 252, 0.15) 1px, transparent 1px, transparent 10px),
    repeating-linear-gradient(90deg, rgba(165, 180, 252, 0.15) 0px, rgba(165, 180, 252, 0.15) 1px, transparent 1px, transparent 10px);
  border: 1px solid rgba(165, 180, 252, 0.2);
  border-radius: 8px;
}

.auth-wall__qr-hint {
  margin: 0;
  font-size: 12px;
  color: var(--text-secondary, #334155);
  text-align: center;
}

.auth-wall__qr-sub {
  margin: 0;
  font-size: 11px;
  color: var(--text-muted, #64748b);
  text-align: center;
}

/* ── 右侧 ── */
.auth-wall__right {
  flex: 1;
  padding: 28px 32px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.auth-wall__title {
  margin: 0 0 16px;
  font-size: 20px;
  font-weight: 700;
}

/* ── Tabs ── */
.auth-tabs :deep(.el-tabs__nav-wrap::after) {
  background: var(--border-subtle, rgba(15, 23, 42, 0.08));
}

.auth-tabs :deep(.el-tabs__item) {
  font-size: 14px;
}

.auth-tabs :deep(.el-tabs__item.is-active) {
  font-weight: 600;
}

.sub-tabs :deep(.el-tabs__item) {
  font-size: 13px;
}

.sub-tabs :deep(.el-tabs__nav-wrap::after) {
  background: var(--border-subtle, rgba(15, 23, 42, 0.08));
}

/* ── 表单 ── */
.auth-wall__form {
  margin-top: 12px;
}

.auth-wall__form :deep(.el-input__wrapper) {
  background: var(--field-bg-soft, var(--bg-muted, #f1f5f9)) !important;
  box-shadow: 0 0 0 1px var(--field-border, var(--border-default, rgba(15, 23, 42, 0.1))) inset !important;
}

.auth-wall__form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--border-strong, rgba(15, 23, 42, 0.14)) inset !important;
}

.auth-wall__form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--brand-accent, #ff6b35) 45%, var(--field-border)) inset !important;
}

.phone-prefix {
  font-size: 13px;
  color: var(--text-muted, #64748b);
  padding-right: 6px;
  border-right: 1px solid var(--border-default, rgba(15, 23, 42, 0.1));
  margin-right: 4px;
}

.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.code-input {
  flex: 1;
}

.send-code-btn {
  flex-shrink: 0;
  white-space: nowrap;
}

.auth-wall__submit {
  width: 100%;
  margin-top: 6px;
}

.auth-wall__footer-hint {
  margin: 8px 0 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-muted, #64748b);
}

/* ── OAuth ── */
.oauth-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 20px;
  margin-bottom: 12px;
}

.oauth-sep {
  flex: 1;
  height: 1px;
  background: var(--border-subtle, rgba(15, 23, 42, 0.08));
}

.oauth-sep-label {
  font-size: 12px;
  color: var(--text-muted, #64748b);
}

.oauth-btns {
  display: flex;
  gap: 10px;
}

.oauth-btn {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 36px;
  padding: 0 16px;
  border-radius: 8px;
  border: 1px solid var(--border-default, rgba(15, 23, 42, 0.1));
  background: var(--bg-muted, #f1f5f9);
  color: var(--text-muted, #64748b);
  font-size: 13px;
  cursor: not-allowed;
}

.oauth-icon {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
}

.oauth-icon.wechat {
  background: #07c160;
  color: #fff;
}

.oauth-icon.google {
  background: #fff;
  color: #4285f4;
  border: 1px solid #e0e0e0;
}

/* ── 响应式：窄屏隐藏左侧 ── */
@media (max-width: 600px) {
  .auth-wall__left {
    display: none;
  }

  .auth-wall__right {
    padding: 20px;
  }
}
</style>
