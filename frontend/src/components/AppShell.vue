<script setup lang="ts">
import {
  ArrowDown,
  Bell,
  ChatDotRound,
  Plus,
  Search,
  UserFilled,
  VideoPlay,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AuthWallDialog from './AuthWallDialog.vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const globalKeyword = ref('')

authStore.hydrate()

const navItems = [
  { key: 'feed', label: '首页', path: '/feed' },
  { key: 'discover', label: '发现' },
  { key: 'following', label: '关注' },
  { key: 'video', label: '视频', icon: VideoPlay },
  { key: 'live', label: '直播', path: '/live' },
  { key: 'community', label: '社群' },
  { key: 'creator', label: '创作中心', path: '/publish' },
]

function go(path: string) {
  void router.push(path)
}

function handleDeveloping(name: string) {
  ElMessage.info(`${name} 功能正在完善中`)
}

function isNavActive(item: (typeof navItems)[number]) {
  if (!item.path) return false
  if (item.path === '/live') return route.path.startsWith('/live')
  if (item.path === '/publish') return route.path.startsWith('/publish')
  return route.path === item.path
}

function handleNav(item: (typeof navItems)[number]) {
  if (item.path) {
    if (isNavActive(item)) {
      window.scrollTo({ top: 0, behavior: 'smooth' })
      return
    }
    go(item.path)
    return
  }
  handleDeveloping(item.label)
}

function submitGlobalSearch() {
  const keyword = globalKeyword.value.trim()
  if (!keyword) return
  void router.push({ path: '/feed', query: { q: keyword } })
}

function openProfile() {
  if (!authStore.currentUser) {
    authStore.openAuthPrompt('manual')
    return
  }
  go('/profile')
}

function publish() {
  go('/publish')
}

watch(
  () => route.query.q,
  (value) => {
    if (typeof value === 'string') globalKeyword.value = value
  },
  { immediate: true },
)
</script>

<template>
  <div class="app-shell">
    <header class="app-shell__topbar">
      <button type="button" class="app-shell__brand" aria-label="回到首页" @click="go('/feed')">
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
          <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <div class="app-shell__actions">
        <button type="button" class="app-shell__icon-action" aria-label="消息" @click="handleDeveloping('消息')">
          <el-icon><ChatDotRound /></el-icon>
          <em>3</em>
        </button>
        <button type="button" class="app-shell__icon-action" aria-label="通知" @click="handleDeveloping('通知')">
          <el-icon><Bell /></el-icon>
          <em>12</em>
        </button>
        <button type="button" class="app-shell__avatar-btn" aria-label="个人主页" @click="openProfile">
          <img
            v-if="authStore.currentUser?.avatarUrl"
            :src="authStore.currentUser.avatarUrl"
            :alt="authStore.currentUser.nickname"
          />
          <el-icon v-else><UserFilled /></el-icon>
          <span v-if="authStore.currentUser" />
          <el-icon class="app-shell__avatar-caret"><ArrowDown /></el-icon>
        </button>
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
  grid-template-columns: 250px minmax(240px, 380px) minmax(500px, 1fr) auto;
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
  letter-spacing: 0;
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
  gap: 20px;
  min-width: 0;
  height: 100%;
}

.app-shell__nav button {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0 2px;
  color: #161b27;
  font-size: 16px;
  font-weight: 780;
  white-space: nowrap;
}

.app-shell__nav button::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  border-radius: 999px;
  background: #ff5a45;
  transform: scaleX(0);
  transition: transform 0.18s ease;
}

.app-shell__nav button:hover,
.app-shell__nav button.is-active {
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
}

.app-shell__icon-action:hover {
  background: #f2f4f7;
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
  gap: 7px;
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
  background: #eef1f5;
  color: #6a7280;
  font-size: 20px;
}

.app-shell__avatar-btn span {
  width: 10px;
  height: 10px;
  margin-left: -18px;
  align-self: end;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #45b56a;
}

.app-shell__avatar-caret {
  color: #798190;
  font-size: 14px;
}

.app-shell__publish {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 42px;
  min-width: 86px;
  padding: 0 18px;
  border-radius: 8px;
  background: #ff5a45;
  color: #fff;
  font-size: 15px;
  font-weight: 820;
  box-shadow: 0 10px 22px rgba(255, 90, 69, 0.26);
}

.app-shell__publish:hover {
  background: #f04835;
}

.app-shell__main {
  min-height: 100vh;
  padding-top: 74px;
}

@media (max-width: 1360px) {
  .app-shell__topbar {
    grid-template-columns: 210px minmax(220px, 1fr) auto;
  }

  .app-shell__nav {
    display: none;
  }
}

@media (max-width: 760px) {
  .app-shell__topbar {
    grid-template-columns: auto minmax(0, 1fr) auto;
    gap: 10px;
    height: 62px;
    padding: 0 10px;
  }

  .app-shell__brand-word,
  .app-shell__search kbd,
  .app-shell__icon-action,
  .app-shell__avatar-caret {
    display: none;
  }

  .app-shell__brand-mark {
    width: 34px;
    height: 32px;
  }

  .app-shell__brand-mark i:nth-child(1) {
    width: 12px;
    height: 29px;
  }

  .app-shell__brand-mark i:nth-child(2) {
    left: 12px;
    width: 13px;
    height: 23px;
  }

  .app-shell__brand-mark i:nth-child(3) {
    width: 12px;
    height: 21px;
  }

  .app-shell__search {
    height: 40px;
    padding: 0 12px;
  }

  .app-shell__publish {
    min-width: 42px;
    width: 42px;
    padding: 0;
    border-radius: 8px;
    font-size: 0;
  }

  .app-shell__publish .el-icon {
    font-size: 18px;
  }

  .app-shell__main {
    padding-top: 62px;
  }
}

@media (max-width: 480px) {
  .app-shell__avatar-btn {
    display: none;
  }
}
</style>
