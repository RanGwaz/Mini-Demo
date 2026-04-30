<script setup lang="ts">
import { EditPen, Plus, VideoCameraFilled } from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useInjectedDialogState } from '../../composables/useDialogState'
import type { UserStats, UserSummary } from '../../types'

const props = defineProps<{
  user: UserSummary
  stats: UserStats
  avatarUrl: string
  coverUrl: string
  likeCount: number
  isMine: boolean
  isFollowing: boolean
  followLoading: boolean
  isLive?: boolean
  partner?: string
}>()

const emit = defineEmits<{
  edit: []
  publish: []
  toggleFollow: []
}>()

const dialogState = useInjectedDialogState()

const coverStyle = computed(() => ({ backgroundImage: `url("${props.coverUrl}")` }))

function formatCount(value?: number) {
  const n = Number(value || 0)
  if (n >= 10000) {
    const w = n / 10000
    return (w >= 10 ? Math.round(w).toString() : w.toFixed(1).replace(/\.0$/, '')) + 'w'
  }
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(Math.max(0, n))
}

function openDialog(type: 'follow' | 'fans') {
  dialogState?.openDialog(type)
}
</script>

<template>
  <section class="profile-header">
    <div class="profile-header__cover" :style="coverStyle" aria-hidden="true" />
    <div class="profile-header__content">
      <div class="profile-header__avatar-wrap" :class="{ 'is-live': isLive }">
        <img class="profile-header__avatar" :src="avatarUrl" :alt="user.nickname" loading="eager" decoding="async" />
        <span v-if="isLive" class="profile-header__live">
          <el-icon><VideoCameraFilled /></el-icon>
          28人在直播
        </span>
      </div>

      <div class="profile-header__info">
        <div class="profile-header__title-row">
          <div class="profile-header__title">
            <h1>{{ user.nickname }}</h1>
            <p>{{ user.bio || '用图文记录审美、灵感和生活现场。' }}</p>
          </div>

          <div class="profile-header__actions">
            <button v-if="isMine" type="button" class="profile-header__btn profile-header__btn--primary" @click="emit('edit')">
              <el-icon><EditPen /></el-icon>
              <span>编辑主页</span>
            </button>
            <button v-if="isMine" type="button" class="profile-header__btn" @click="emit('publish')">
              <el-icon><Plus /></el-icon>
              <span>发布作品</span>
            </button>
            <button
              v-else
              type="button"
              class="profile-header__btn profile-header__btn--primary"
              :class="{ 'is-following': isFollowing }"
              :disabled="followLoading"
              @click="emit('toggleFollow')"
            >
              {{ isFollowing ? '已关注' : '关注' }}
            </button>
          </div>
        </div>

        <div class="profile-header__stats" aria-label="主页统计">
          <button type="button" class="profile-header__stat" @click="openDialog('follow')">
            <strong>{{ formatCount(stats.followingCount) }}</strong>
            <span>关注</span>
          </button>
          <button type="button" class="profile-header__stat" @click="openDialog('fans')">
            <strong>{{ formatCount(stats.followerCount) }}</strong>
            <span>粉丝</span>
          </button>
          <div class="profile-header__stat">
            <strong>{{ formatCount(likeCount) }}</strong>
            <span>获赞</span>
          </div>
        </div>

        <p class="profile-header__meta">
          抖音号：{{ user.userNo || user.username }} · @{{ user.username }} · LOOP 社区创作者
        </p>

        <div v-if="partner" class="profile-header__partner">
          <span>恋人：@{{ partner }}</span>
          <b>❤</b>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.profile-header {
  position: sticky;
  top: 10px;
  z-index: 50;
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 22px;
  background: color-mix(in srgb, var(--bg-solid, #fff) 92%, transparent);
  box-shadow: 0 16px 42px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(18px);
}

.profile-header__cover {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center;
  filter: blur(18px) saturate(1.08);
  opacity: 0.34;
  transform: scale(1.08);
}

.profile-header__cover::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.92), rgba(255, 255, 255, 0.72)),
    linear-gradient(135deg, rgba(254, 44, 85, 0.18), rgba(37, 244, 238, 0.13));
}

.profile-header__content {
  position: relative;
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr);
  gap: 22px;
  padding: 26px;
}

.profile-header__avatar-wrap {
  position: relative;
  align-self: start;
  width: 124px;
  height: 124px;
}

.profile-header__avatar-wrap.is-live::before {
  content: '';
  position: absolute;
  inset: -7px;
  border-radius: 50%;
  background: rgba(254, 44, 85, 0.22);
  animation: profile-live-pulse 1.5s infinite;
}

.profile-header__avatar {
  position: relative;
  display: block;
  width: 124px;
  height: 124px;
  border-radius: 50%;
  object-fit: cover;
  box-shadow:
    0 0 0 3px #fff,
    0 18px 38px rgba(15, 23, 42, 0.24);
}

.profile-header__live {
  position: absolute;
  left: 50%;
  bottom: -10px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: max-content;
  padding: 6px 10px;
  border-radius: 999px;
  background: linear-gradient(90deg, #fe2c55, #ff6b6b);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  transform: translateX(-50%);
  box-shadow: 0 10px 22px rgba(254, 44, 85, 0.28);
}

.profile-header__info {
  min-width: 0;
}

.profile-header__title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.profile-header__title {
  min-width: 0;
}

.profile-header__title h1 {
  margin: 2px 0 8px;
  color: #161823;
  font-size: clamp(28px, 4vw, 44px);
  line-height: 1.06;
  letter-spacing: 0;
  overflow-wrap: anywhere;
}

.profile-header__title p,
.profile-header__meta {
  margin: 0;
  color: var(--text-secondary, #334155);
  line-height: 1.65;
}

.profile-header__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.profile-header__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 40px;
  padding: 0 16px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 999px;
  background: #fff;
  color: #161823;
  cursor: pointer;
  font-weight: 800;
  transition:
    transform 0.16s ease,
    border-color 0.16s ease,
    background 0.16s ease;
}

.profile-header__btn:hover {
  transform: translateY(-1px);
  border-color: rgba(22, 24, 35, 0.28);
}

.profile-header__btn--primary {
  border-color: #161823;
  background: #161823;
  color: #fff;
}

.profile-header__btn--primary.is-following {
  border-color: rgba(15, 23, 42, 0.12);
  background: #f1f5f9;
  color: #161823;
}

.profile-header__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  margin-top: 18px;
}

.profile-header__stat {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 0;
  border: none;
  background: transparent;
  color: #161823;
}

button.profile-header__stat {
  cursor: pointer;
}

button.profile-header__stat:hover span {
  text-decoration: underline;
}

.profile-header__stat strong {
  font-size: 22px;
  font-weight: 800;
}

.profile-header__stat span {
  color: #64748b;
  font-size: 14px;
}

.profile-header__meta {
  margin-top: 12px;
  font-size: 14px;
}

.profile-header__partner {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-top: 12px;
  padding: 7px 10px;
  border: 1px solid rgba(254, 44, 85, 0.2);
  border-radius: 999px;
  background: rgba(254, 44, 85, 0.08);
  color: #b91c3a;
  font-size: 13px;
  font-weight: 800;
}

.profile-header__partner b {
  color: #fe2c55;
}

@keyframes profile-live-pulse {
  0%,
  100% {
    transform: scale(0.96);
    opacity: 0.72;
  }

  50% {
    transform: scale(1.08);
    opacity: 0.2;
  }
}

@media (max-width: 760px) {
  .profile-header {
    position: relative;
    top: auto;
    border-radius: 18px;
  }

  .profile-header__content {
    grid-template-columns: 1fr;
    gap: 14px;
    padding: 20px;
  }

  .profile-header__avatar-wrap,
  .profile-header__avatar {
    width: 104px;
    height: 104px;
  }

  .profile-header__title-row,
  .profile-header__actions {
    display: grid;
    justify-content: stretch;
  }

  .profile-header__actions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
