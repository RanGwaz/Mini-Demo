<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../services/api'
import { HttpError } from '../services/http'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const activeTab = ref<'phone' | 'password'>('phone')
const pwdTab = ref<'login' | 'register'>('login')
const loading = ref(false)

// ── 手机验证码表单 ─────────────────────────────────────
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
      if (smsCooldown.value <= 0 && cooldownTimer) {
        clearInterval(cooldownTimer)
        cooldownTimer = null
      }
    }, 1000)
  } catch (e) {
    ElMessage.error(resolveError(e))
  }
}

async function submitSmsLogin() {
  if (!smsForm.phone || !smsForm.code) {
    ElMessage.warning('请填写手机号和验证码')
    return
  }
  loading.value = true
  try {
    const data = await api.phoneSmsLogin({
      phone: smsForm.phone,
      code: smsForm.code,
      nickname: smsForm.nickname || undefined,
    })
    authStore.setSession(data)
    ElMessage.success('登录成功')
    redirectAfterLogin()
  } catch (e) {
    ElMessage.error(resolveError(e))
  } finally {
    loading.value = false
  }
}

// ── 用户名密码登录 ─────────────────────────────────────
const loginForm = reactive({ username: '', password: '' })

async function submitLogin() {
  loading.value = true
  try {
    const data = await api.login(loginForm)
    authStore.setSession(data)
    ElMessage.success('登录成功')
    redirectAfterLogin()
  } catch (e) {
    ElMessage.error(resolveError(e))
  } finally {
    loading.value = false
  }
}

// ── 用户名密码注册 ─────────────────────────────────────
const registerForm = reactive({ username: '', password: '', nickname: '' })

async function submitRegister() {
  loading.value = true
  try {
    const data = await api.register(registerForm)
    authStore.setSession(data)
    ElMessage.success('注册成功')
    redirectAfterLogin()
  } catch (e) {
    ElMessage.error(resolveError(e))
  } finally {
    loading.value = false
  }
}

function redirectAfterLogin() {
  const pending = authStore.pendingRedirect
  authStore.clearPendingRedirect()
  router.push(pending ?? String(route.query.redirect ?? '/feed'))
}

function resolveError(error: unknown): string {
  if (error instanceof HttpError) {
    const map: Record<string, string> = {
      U001: '用户不存在',
      U002: '用户名已存在，请更换后重试',
      U003: '密码错误',
      U004: '账号已被封禁，如有疑问请联系客服',
      U006: '验证码错误或已过期',
      U007: '发送过于频繁，请稍后再试',
      A001: '请检查输入内容格式',
    }
    return map[error.code ?? ''] ?? error.message ?? '操作失败'
  }
  return error instanceof Error ? error.message : '操作失败'
}
</script>

<template>
  <div class="login-page">
    <div class="login-layout">
      <!-- 左侧品牌区 -->
      <section class="login-brand">
        <div class="brand-logo">IS</div>
        <div class="login-brand__eyebrow">Quiet visual system</div>
        <h1>更安静地发现，<br />更高级地表达。</h1>
        <p>加入 ImageSocial，用更干净的界面、更稳定的阅读节奏和更清晰的层级感进入内容世界。</p>
        <div class="login-brand__points">
          <span>极简布局</span>
          <span>高质量内容流</span>
          <span>轻盈交互</span>
        </div>
      </section>

      <!-- 右侧表单卡片 -->
      <el-card class="login-card" shadow="never">
        <div class="login-card__head">
          <span class="login-card__eyebrow">ACCOUNT ACCESS</span>
          <h2>欢迎回来</h2>
          <p>选择你的登录方式继续</p>
        </div>

        <el-tabs v-model="activeTab" stretch class="login-tabs">

          <!-- ══ Tab 1：手机号 + 验证码 ══ -->
          <el-tab-pane name="phone" label="手机验证码">
            <el-form label-position="top" class="auth-form" @submit.prevent="submitSmsLogin">
              <el-form-item label="手机号">
                <el-input
                  v-model="smsForm.phone"
                  placeholder="请输入手机号"
                  maxlength="11"
                  clearable
                >
                  <template #prefix>
                    <span class="phone-prefix">+86</span>
                  </template>
                </el-input>
              </el-form-item>

              <el-form-item label="验证码">
                <div class="code-row">
                  <el-input
                    v-model="smsForm.code"
                    placeholder="6 位验证码"
                    maxlength="6"
                    class="code-input"
                  />
                  <el-button
                    class="send-btn"
                    :disabled="smsCooldown > 0"
                    @click="sendCode"
                  >
                    {{ smsCooldown > 0 ? `${smsCooldown}s 后重发` : '获取验证码' }}
                  </el-button>
                </div>
              </el-form-item>

              <el-form-item label="昵称（可选，首次注册时使用）">
                <el-input v-model="smsForm.nickname" placeholder="留空则自动生成" />
              </el-form-item>

              <el-button
                type="primary"
                :loading="loading"
                class="submit-btn"
                native-type="submit"
                @click="submitSmsLogin"
              >
                登录 / 注册
              </el-button>

              <p class="form-tip">未注册的手机号将自动创建新账号</p>
            </el-form>
          </el-tab-pane>

          <!-- ══ Tab 2：用户名 + 密码 ══ -->
          <el-tab-pane name="password" label="用户名密码">
            <el-tabs v-model="pwdTab" class="sub-tabs">

              <el-tab-pane name="login" label="登录">
                <el-form label-position="top" class="auth-form" @submit.prevent="submitLogin">
                  <el-form-item label="用户名">
                    <el-input v-model="loginForm.username" placeholder="请输入用户名" clearable />
                  </el-form-item>
                  <el-form-item label="密码">
                    <el-input v-model="loginForm.password" show-password placeholder="请输入密码" />
                  </el-form-item>
                  <el-button
                    type="primary"
                    :loading="loading"
                    class="submit-btn"
                    native-type="submit"
                    @click="submitLogin"
                  >
                    登录
                  </el-button>
                </el-form>
              </el-tab-pane>

              <el-tab-pane name="register" label="注册">
                <el-form label-position="top" class="auth-form" @submit.prevent="submitRegister">
                  <el-form-item label="用户名">
                    <el-input v-model="registerForm.username" placeholder="至少 4 位字符" clearable />
                  </el-form-item>
                  <el-form-item label="昵称">
                    <el-input v-model="registerForm.nickname" placeholder="展示在内容卡片上" clearable />
                  </el-form-item>
                  <el-form-item label="密码">
                    <el-input v-model="registerForm.password" show-password placeholder="至少 6 位字符" />
                  </el-form-item>
                  <el-button
                    type="primary"
                    :loading="loading"
                    class="submit-btn"
                    native-type="submit"
                    @click="submitRegister"
                  >
                    注册并登录
                  </el-button>
                </el-form>
              </el-tab-pane>

            </el-tabs>
          </el-tab-pane>

        </el-tabs>

        <!-- 第三方登录占位 -->
        <div class="oauth-section">
          <div class="oauth-divider"><span>其他登录方式</span></div>
          <div class="oauth-buttons">
            <el-tooltip content="微信登录（即将开放）" placement="top">
              <button class="oauth-btn" disabled>
                <span class="oauth-icon oauth-icon--wechat">W</span>
                微信
              </button>
            </el-tooltip>
            <el-tooltip content="Google 登录（即将开放）" placement="top">
              <button class="oauth-btn" disabled>
                <span class="oauth-icon oauth-icon--google">G</span>
                Google
              </button>
            </el-tooltip>
          </div>
        </div>

      </el-card>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f0f14 0%, #1a1a2e 50%, #16213e 100%);
  padding: 24px;
}

.login-layout {
  display: flex;
  gap: 64px;
  align-items: center;
  max-width: 900px;
  width: 100%;
}

/* ── 品牌区 ── */
.login-brand {
  flex: 1;
  color: #fff;
  min-width: 0;
}

.brand-logo {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: linear-gradient(135deg, #6366f1, #ec4899);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 20px;
  letter-spacing: -1px;
  margin-bottom: 28px;
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.4);
}

.login-brand h1 {
  font-size: clamp(2rem, 4vw, 3.2rem);
  font-weight: 800;
  line-height: 1.15;
  margin: 0 0 16px;
  background: linear-gradient(135deg, #fff 30%, #a5b4fc);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.login-brand p {
  color: rgba(255, 255, 255, 0.55);
  font-size: 15px;
  margin: 0;
  line-height: 1.6;
}

/* ── 卡片 ── */
.login-card {
  width: 400px;
  flex-shrink: 0;
  border-radius: 20px !important;
  border: 1px solid rgba(255, 255, 255, 0.08) !important;
  background: rgba(255, 255, 255, 0.04) !important;
  backdrop-filter: blur(24px);
}

.login-card :deep(.el-card__body) {
  padding: 32px;
}

.login-card__head {
  margin-bottom: 24px;
}

.login-card__head h2 {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 700;
  color: #fff;
}

.login-card__head p {
  margin: 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.45);
}

/* ── Tabs ── */
.login-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.login-tabs :deep(.el-tabs__item) {
  color: rgba(255, 255, 255, 0.45);
  font-size: 14px;
}

.login-tabs :deep(.el-tabs__item.is-active) {
  color: #a5b4fc;
  font-weight: 600;
}

.login-tabs :deep(.el-tabs__active-bar) {
  background: #6366f1;
}

.sub-tabs :deep(.el-tabs__item) {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.4);
}

.sub-tabs :deep(.el-tabs__item.is-active) {
  color: #c7d2fe;
}

.sub-tabs :deep(.el-tabs__active-bar) {
  background: #818cf8;
}

.sub-tabs :deep(.el-tabs__nav-wrap::after) {
  background: rgba(255, 255, 255, 0.06);
}

/* ── 表单 ── */
.auth-form {
  margin-top: 16px;
}

.auth-form :deep(.el-form-item__label) {
  color: rgba(255, 255, 255, 0.65);
  font-size: 13px;
  padding-bottom: 4px;
}

.auth-form :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.06) !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1) inset !important;
  border-radius: 10px;
}

.auth-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #6366f1 inset !important;
}

.auth-form :deep(.el-input__inner) {
  color: #fff;
}

.auth-form :deep(.el-input__inner::placeholder) {
  color: rgba(255, 255, 255, 0.25);
}

.phone-prefix {
  color: rgba(255, 255, 255, 0.4);
  font-size: 13px;
  padding-right: 6px;
  border-right: 1px solid rgba(255, 255, 255, 0.1);
  margin-right: 6px;
}

.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.code-input {
  flex: 1;
}

.send-btn {
  flex-shrink: 0;
  white-space: nowrap;
  background: rgba(99, 102, 241, 0.15) !important;
  border: 1px solid rgba(99, 102, 241, 0.35) !important;
  color: #a5b4fc !important;
  border-radius: 10px !important;
  font-size: 13px;
  transition: all 0.2s;
}

.send-btn:not(:disabled):hover {
  background: rgba(99, 102, 241, 0.3) !important;
  border-color: #6366f1 !important;
}

.send-btn:disabled {
  opacity: 0.45;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
  height: 44px;
  border-radius: 12px !important;
  font-size: 15px;
  font-weight: 600;
  background: linear-gradient(135deg, #6366f1, #818cf8) !important;
  border: none !important;
  box-shadow: 0 4px 20px rgba(99, 102, 241, 0.45);
  transition: opacity 0.2s, transform 0.1s;
}

.submit-btn:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

.submit-btn:active {
  transform: translateY(0);
}

.form-tip {
  margin: 10px 0 0;
  text-align: center;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.3);
}

/* ── OAuth ── */
.oauth-section {
  margin-top: 24px;
}

.oauth-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  color: rgba(255, 255, 255, 0.2);
  font-size: 12px;
  margin-bottom: 16px;
}

.oauth-divider::before,
.oauth-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: rgba(255, 255, 255, 0.1);
}

.oauth-buttons {
  display: flex;
  gap: 12px;
}

.oauth-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 40px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.04);
  color: rgba(255, 255, 255, 0.4);
  font-size: 13px;
  cursor: not-allowed;
  transition: all 0.2s;
}

.oauth-icon {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
}

.oauth-icon--wechat {
  background: #07c160;
  color: #fff;
}

.oauth-icon--google {
  background: #fff;
  color: #4285f4;
}

/* ── 响应式 ── */
@media (max-width: 700px) {
  .login-layout {
    flex-direction: column;
    gap: 32px;
  }
  .login-brand h1 {
    font-size: 2rem;
  }
  .login-card {
    width: 100%;
  }
}
</style>