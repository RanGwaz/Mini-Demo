<script setup lang="ts">
import {
  ArrowDown,
  ChatDotRound,
  EditPen,
  Plus,
  Search,
  Setting,
  SwitchButton,
  User,
  UserFilled,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthWallDialog from './AuthWallDialog.vue'
import { api } from '../services/api'
import { useAuthStore } from '../stores/auth'
import { normalizeMediaUrl } from '../utils/postMedia'

type NavItem = {
  key: 'creator' | 'admin'
  label: string
  path: string
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const globalKeyword = ref('')
const avatarBroken = ref(false)
const unreadMessages = ref(0)

authStore.hydrate()

const isAdmin = computed(() => (authStore.currentUser?.roles ?? '').split(',').map((role) => role.trim()).includes('ROLE_ADMIN'))

const navItems = computed<NavItem[]>(() => [
  { key: 'creator', label: '创作中心', path: '/publish' },
  ...(isAdmin.value ? [{ key: 'admin' as const, label: '运营后台', path: '/admin' }] : []),
])
const currentAvatarUrl = computed(() => avatarBroken.value ? '' : normalizeMediaUrl(authStore.currentUser?.avatarUrl))

function go(path: string) {
  void router.push(path)
}

function isNavActive(item: NavItem) {
  if (item.key === 'creator') return route.path.startsWith('/publish')
  if (item.key === 'admin') return route.path.startsWith('/admin')
  return false
}

function handleNav(item: NavItem) {
  if (isNavActive(item)) {
    window.scrollTo({ top: 0, behavior: 'smooth' })
    return
  }
  go(item.path)
}

function submitGlobalSearch() {
  const keyword = globalKeyword.value.trim()
  if (!keyword) return
  void router.push({ path: '/search', query: { q: keyword } })
}

function publish() {
  go('/publish')
}

function isTopActionActive(path: string) {
  if (path === '/messages') return route.path.startsWith('/messages') || route.path.startsWith('/notifications')
  return route.path.startsWith(path)
}

async function loadMessageSummary() {
  if (!authStore.accessToken) {
    unreadMessages.value = 0
    return
  }
  try {
    const summary = await api.messageSummary()
    unreadMessages.value = summary.unreadTotal
  } catch {
    unreadMessages.value = 0
  }
}

async function handleAvatarCommand(command: string) {
  if (!authStore.currentUser) {
    authStore.openAuthPrompt('manual')
    return
  }
  if (command === 'profile') {
    go('/profile')
    return
  }
  if (command === 'publish') {
    go('/publish')
    return
  }
  if (command === 'admin') {
    go('/admin')
    return
  }
  if (command === 'logout') {
    await authStore.logout()
    ElMessage.success('已退出登录')
    go('/home')
  }
}

watch(
  () => route.query.q,
  (value) => {
    if (typeof value === 'string') globalKeyword.value = value
    if (!value) globalKeyword.value = ''
  },
  { immediate: true },
)

watch(
  () => [authStore.accessToken, route.fullPath],
  () => void loadMessageSummary(),
  { immediate: true },
)

watch(
  () => authStore.currentUser?.avatarUrl,
  () => {
    avatarBroken.value = false
  },
)

onMounted(() => {
  window.addEventListener('message-center:updated', loadMessageSummary)
})

onUnmounted(() => {
  window.removeEventListener('message-center:updated', loadMessageSummary)
})
</script>

<template>
  <div class="app-shell">
    <header class="app-shell__topbar">
      <button type="button" class="app-shell__brand" aria-label="回到首页" @click="go('/home')">
        <span class="app-shell__brand-mark" aria-hidden="true">
          <i />
          <i />
          <i />
        </span>
        <span class="app-shell__brand-word">Vibelo</span>
      </button>

      <form class="app-shell__search" role="search" @submit.prevent="submitGlobalSearch">
        <el-icon><Search /></el-icon>
        <input v-model="globalKeyword" type="search" placeholder="搜索用户、内容、话题或地点" />
        <kbd>⌘ K</kbd>
      </form>

      <nav class="app-shell__nav" aria-label="主导航">
        <button
          v-for="item in navItems"
          :key="item.key"
          type="button"
          :class="{ 'is-active': isNavActive(item) }"
          @click="handleNav(item)"
        >
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <div class="app-shell__actions">
        <button
          type="button"
          class="app-shell__icon-action"
          :class="{ 'is-active': isTopActionActive('/messages') }"
          aria-label="消息"
          @click="go('/messages')"
        >
          <el-icon><ChatDotRound /></el-icon>
          <em v-if="unreadMessages">{{ unreadMessages > 99 ? '99+' : unreadMessages }}</em>
        </button>

        <el-dropdown trigger="click" placement="bottom-end" @command="handleAvatarCommand">
          <button type="button" class="app-shell__avatar-btn" aria-label="账户菜单" @click.stop>
            <img
              v-if="currentAvatarUrl"
              :src="currentAvatarUrl"
              :alt="authStore.currentUser?.nickname || '用户头像'"
              @error="avatarBroken = true"
            />
            <el-icon v-else><UserFilled /></el-icon>
            <span v-if="authStore.currentUser" />
            <el-icon class="app-shell__avatar-caret"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu class="app-shell__account-menu">
              <el-dropdown-item v-if="authStore.currentUser" command="profile">
                <el-icon><User /></el-icon>
                个人主页
              </el-dropdown-item>
              <el-dropdown-item v-if="authStore.currentUser" command="publish">
                <el-icon><EditPen /></el-icon>
                发布内容
              </el-dropdown-item>
              <el-dropdown-item v-if="isAdmin" command="admin">
                <el-icon><Setting /></el-icon>
                运营后台
              </el-dropdown-item>
              <el-dropdown-item v-if="authStore.currentUser" divided command="logout">
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
              <el-dropdown-item v-else command="login">
                <el-icon><User /></el-icon>
                登录 / 注册
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <button type="button" class="app-shell__publish" @click="publish">
          <el-icon><Plus /></el-icon>
          发布
        </button>
      </div>
    </header>

    <main class="app-shell__main">
      <slot />
    </main>

    <AuthWallDialog />
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: #f7f8fa;
}

.app-shell__topbar {
  position: fixed;
  inset: 0 0 auto 0;
  z-index: 90;
  display: grid;
  grid-template-columns: 250px minmax(240px, 420px) minmax(220px, 1fr) auto;
  align-items: center;
  gap: 18px;
  height: 74px;
  padding: 0 30px;
  border-bottom: 1px solid rgba(26, 31, 44, 0.08);
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: saturate(1.1) blur(16px);
  box-shadow: 0 6px 22px rgba(32, 36, 47, 0.05);
}

.app-shell__brand,
.app-shell__nav button,
.app-shell__icon-action,
.app-shell__avatar-btn,
.app-shell__publish {
  border: none;
  background: transparent;
  color: #20242f;
  font: inherit;
  cursor: pointer;
}

.app-shell__brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  justify-self: start;
  min-width: 0;
  padding: 0;
}

.app-shell__brand-mark {
  position: relative;
  display: inline-block;
  width: 42px;
  height: 38px;
}

.app-shell__brand-mark i {
  position: absolute;
  display: block;
  border-radius: 999px 999px 8px 8px;
  background: linear-gradient(180deg, #ff7b54 0%, #ff503e 100%);
  box-shadow: 0 10px 20px rgba(255, 90, 69, 0.28);
}

.app-shell__brand-mark i:nth-child(1) {
  left: 3px;
  top: 2px;
  width: 15px;
  height: 35px;
  transform: rotate(-34deg);
}

.app-shell__brand-mark i:nth-child(2) {
  left: 14px;
  top: 10px;
  width: 16px;
  height: 28px;
  transform: rotate(32deg);
  background: linear-gradient(180deg, #ff9c57 0%, #ff6849 100%);
}

.app-shell__brand-mark i:nth-child(3) {
  right: 3px;
  top: 1px;
  width: 14px;
  height: 25px;
  transform: rotate(34deg);
}

.app-shell__brand-word {
  color: #101522;
  font-size: 30px;
  font-weight: 900;
  line-height: 1;
}

.app-shell__search {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  height: 44px;
  padding: 0 12px 0 16px;
  border-radius: 999px;
  background: #f0f1f4;
  color: #7d8492;
}

.app-shell__search input {
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: #20242f;
  font-size: 14px;
}

.app-shell__search input::placeholder {
  color: #8a91a0;
}

.app-shell__search kbd {
  min-width: 42px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  color: #767e8d;
  font-family: inherit;
  font-size: 12px;
  font-weight: 760;
}

.app-shell__nav {
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 12px;
  min-width: 0;
  height: 100%;
}

.app-shell__nav button {
  position: relative;
  display: inline-flex;
  align-items: center;
  align-self: center;
  height: 42px;
  padding: 0 16px;
  border-radius: 999px;
  color: #161b27;
  font-size: 16px;
  font-weight: 780;
  white-space: nowrap;
  transition: background 0.18s ease, color 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
}

.app-shell__nav button::after {
  content: '';
  position: absolute;
  left: 18px;
  right: 18px;
  bottom: 7px;
  height: 3px;
  border-radius: 999px;
  background: #ff5a45;
  transform: scaleX(0);
  transition: transform 0.18s ease;
}

.app-shell__nav button:hover {
  background:
    linear-gradient(135deg, rgba(255, 90, 69, 0.13), rgba(255, 137, 92, 0.08)),
    #fff;
  color: #ff4f3b;
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(255, 90, 69, 0.13);
}

.app-shell__nav button.is-active {
  background: #fff1ed;
  color: #ff4f3b;
}

.app-shell__nav button.is-active::after {
  transform: scaleX(1);
}

.app-shell__actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  min-width: 0;
}

.app-shell__icon-action {
  position: relative;
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  font-size: 22px;
  transition: background 0.16s ease, color 0.16s ease, transform 0.16s ease, box-shadow 0.16s ease;
}

.app-shell__icon-action:hover,
.app-shell__icon-action.is-active {
  background: #fff1ed;
  color: #ff4f3b;
  transform: translateY(-1px);
  box-shadow: 0 10px 22px rgba(255, 90, 69, 0.12);
}

.app-shell__icon-action em {
  position: absolute;
  right: -2px;
  top: -3px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border: 2px solid #fff;
  border-radius: 999px;
  background: #ff4f3b;
  color: #fff;
  font-style: normal;
  font-size: 10px;
  font-weight: 900;
  line-height: 13px;
}

.app-shell__avatar-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 0;
}

.app-shell__avatar-btn img,
.app-shell__avatar-btn > .el-icon:first-child {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  object-fit: cover;
}

.app-shell__avatar-btn > .el-icon:first-child {
  display: grid;
  place-items: center;
  background: #eef1f6;
  color: #7c8494;
  font-size: 21px;
}

.app-shell__avatar-btn span {
  width: 9px;
  height: 9px;
  margin-left: -14px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #36c36b;
}

.app-shell__avatar-caret {
  color: #959cac;
  font-size: 12px;
}

.app-shell__publish {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 42px;
  padding: 0 20px;
  border-radius: 12px;
  background: linear-gradient(180deg, #ff7057 0%, #ff4d3c 100%);
  color: #fff;
  font-size: 17px;
  font-weight: 780;
  box-shadow: 0 12px 24px rgba(255, 90, 69, 0.22);
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

.app-shell__publish:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 28px rgba(255, 90, 69, 0.30);
}

.app-shell__main {
  padding-top: 74px;
}

:deep(.app-shell__account-menu .el-dropdown-menu__item) {
  min-width: 136px;
  gap: 7px;
}

@media (max-width: 1360px) {
  .app-shell__topbar {
    grid-template-columns: 220px minmax(220px, 360px) minmax(180px, 1fr) auto;
    gap: 14px;
    padding: 0 18px;
  }
}

@media (max-width: 1200px) {
  .app-shell__topbar {
    grid-template-columns: auto minmax(180px, 1fr) auto;
    height: 66px;
    padding: 0 12px;
    gap: 10px;
  }

  .app-shell__brand-word,
  .app-shell__search kbd,
  .app-shell__avatar-caret,
  .app-shell__nav {
    display: none;
  }

  .app-shell__brand-mark {
    width: 36px;
    height: 32px;
  }

  .app-shell__brand-mark i:nth-child(1) {
    left: 2px;
    top: 1px;
    width: 13px;
    height: 30px;
  }

  .app-shell__brand-mark i:nth-child(2) {
    left: 12px;
    top: 8px;
    width: 14px;
    height: 24px;
  }

  .app-shell__brand-mark i:nth-child(3) {
    right: 2px;
    top: 1px;
    width: 12px;
    height: 21px;
  }

  .app-shell__actions {
    gap: 8px;
  }

  .app-shell__icon-action {
    width: 34px;
    height: 34px;
    font-size: 18px;
  }

  .app-shell__publish {
    height: 36px;
    padding: 0 12px;
    border-radius: 10px;
    font-size: 14px;
  }

  .app-shell__main {
    padding-top: 66px;
  }
}
</style>
