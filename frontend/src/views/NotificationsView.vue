<script setup lang="ts">
defineOptions({ name: 'NotificationsView' })

import { Bell, ChatDotRound, Check, CollectionTag, Star, UserFilled } from '@element-plus/icons-vue'
import { computed, ref } from 'vue'
import CommonLeftSidebar from '../components/CommonLeftSidebar.vue'

type NoticeType = 'all' | 'interaction' | 'system'

type NoticeItem = {
  id: number
  type: Exclude<NoticeType, 'all'>
  title: string
  content: string
  time: string
  read: boolean
}

const activeType = ref<NoticeType>('all')
const notices = ref<NoticeItem[]>([
  { id: 1, type: 'interaction', title: '新的评论', content: '有人回复了你的校园生活笔记。', time: '5 分钟前', read: false },
  { id: 2, type: 'interaction', title: '收藏提醒', content: '你的摄影内容被收藏 3 次。', time: '18 分钟前', read: false },
  { id: 3, type: 'system', title: '内容安全提示', content: '平台已更新图文发布规范，请留意标题和封面质量。', time: '今天 09:30', read: true },
  { id: 4, type: 'system', title: '推荐链路日报', content: '今日推荐服务运行正常，探索流无降级。', time: '昨天', read: true },
])

const tabs = [
  { key: 'all' as const, label: '全部', icon: Bell },
  { key: 'interaction' as const, label: '互动', icon: ChatDotRound },
  { key: 'system' as const, label: '系统', icon: CollectionTag },
]

const filteredNotices = computed(() => (
  activeType.value === 'all'
    ? notices.value
    : notices.value.filter((item) => item.type === activeType.value)
))

const unreadCount = computed(() => notices.value.filter((item) => !item.read).length)

function markRead(id: number) {
  const target = notices.value.find((item) => item.id === id)
  if (target) target.read = true
}

function markAllRead() {
  notices.value = notices.value.map((item) => ({ ...item, read: true }))
}
</script>

<template>
  <div class="notifications-page">
    <CommonLeftSidebar />

    <main class="notifications-page__main">
      <section class="notifications-page__hero">
        <div>
          <span>通知中心</span>
          <h1>站内通知</h1>
          <p>互动、审核、推荐服务和系统公告会在这里集中展示。</p>
        </div>
        <button type="button" :disabled="unreadCount === 0" @click="markAllRead">
          <el-icon><Check /></el-icon>
          全部已读
        </button>
      </section>

      <section class="notifications-page__panel">
        <nav class="notifications-page__tabs" aria-label="通知分类">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            :class="{ 'is-active': activeType === tab.key }"
            @click="activeType = tab.key"
          >
            <el-icon><component :is="tab.icon" /></el-icon>
            {{ tab.label }}
          </button>
        </nav>

        <div class="notifications-page__list">
          <article
            v-for="item in filteredNotices"
            :key="item.id"
            :class="{ 'is-unread': !item.read }"
            @click="markRead(item.id)"
          >
            <div class="notifications-page__icon">
              <el-icon v-if="item.type === 'interaction'"><Star /></el-icon>
              <el-icon v-else><UserFilled /></el-icon>
            </div>
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.content }}</p>
              <small>{{ item.time }}</small>
            </div>
            <em v-if="!item.read">未读</em>
          </article>
          <div v-if="filteredNotices.length === 0" class="notifications-page__empty">暂无通知</div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.notifications-page {
  min-height: calc(100vh - 74px);
  padding: 14px 16px 36px 246px;
  background: #f7f8fa;
}

.notifications-page button {
  font: inherit;
}

.notifications-page__main {
  max-width: 1080px;
  margin: 0 auto;
}

.notifications-page__hero,
.notifications-page__panel {
  border: 1px solid rgba(26, 31, 44, 0.08);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 18px 42px rgba(32, 36, 47, 0.07);
}

.notifications-page__hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 132px;
  padding: 24px;
  background:
    linear-gradient(135deg, rgba(255, 90, 69, 0.12), rgba(72, 126, 255, 0.06)),
    #fff;
}

.notifications-page__hero span {
  color: #ff5a45;
  font-size: 12px;
  font-weight: 780;
}

.notifications-page__hero h1 {
  margin: 6px 0;
  color: #1f2531;
  font-size: 28px;
  font-weight: 860;
}

.notifications-page__hero p {
  margin: 0;
  color: #6f7684;
  font-size: 14px;
}

.notifications-page__hero button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 38px;
  padding: 0 14px;
  border: none;
  border-radius: 8px;
  background: #ff5a45;
  color: #fff;
  cursor: pointer;
  font-weight: 760;
}

.notifications-page__hero button:disabled {
  background: #d9dee7;
  cursor: not-allowed;
}

.notifications-page__panel {
  margin-top: 14px;
  overflow: hidden;
}

.notifications-page__tabs {
  display: flex;
  gap: 8px;
  padding: 14px;
  border-bottom: 1px solid #edf0f4;
}

.notifications-page__tabs button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 36px;
  padding: 0 14px;
  border: 1px solid #e5e9f1;
  border-radius: 999px;
  background: #fff;
  color: #4d5665;
  cursor: pointer;
  font-weight: 720;
}

.notifications-page__tabs button:hover,
.notifications-page__tabs button.is-active {
  border-color: #ffd1c8;
  background: #fff1ed;
  color: #ff4f3b;
}

.notifications-page__list {
  display: grid;
  gap: 0;
}

.notifications-page__list article {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  gap: 12px;
  padding: 18px;
  border-bottom: 1px solid #edf0f4;
  cursor: pointer;
}

.notifications-page__list article:hover {
  background: #fbfcfe;
}

.notifications-page__list article.is-unread {
  background: #fffaf8;
}

.notifications-page__icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: #fff1ed;
  color: #ff5a45;
  font-size: 19px;
}

.notifications-page__list strong {
  color: #20242f;
  font-size: 15px;
  font-weight: 800;
}

.notifications-page__list p {
  margin: 5px 0 4px;
  color: #5f6775;
  font-size: 14px;
  line-height: 1.55;
}

.notifications-page__list small {
  color: #9aa1ad;
  font-size: 12px;
}

.notifications-page__list em {
  align-self: start;
  padding: 3px 7px;
  border-radius: 999px;
  background: #ff5a45;
  color: #fff;
  font-style: normal;
  font-size: 11px;
  font-weight: 820;
}

.notifications-page__empty {
  padding: 42px;
  color: #98a1af;
  text-align: center;
}

@media (max-width: 980px) {
  .notifications-page {
    padding: 12px 10px 88px;
  }

  .notifications-page__hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
