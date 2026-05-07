<script setup lang="ts">
import {
  ArrowRight,
  Brush,
  Camera,
  HomeFilled,
  Notebook,
  Opportunity,
  School,
  Star,
  UserFilled,
} from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { contentChannels, type ContentChannelKey } from '../domain/contentTaxonomy'

const props = withDefaults(defineProps<{
  fixed?: boolean
}>(), {
  fixed: true,
})

const router = useRouter()
const route = useRoute()

const channelIcons: Record<ContentChannelKey, typeof HomeFilled> = {
  campus: Notebook,
  anime_outfit: Brush,
  pet: Opportunity,
  photography: Camera,
  tech_moment: School,
}

type NavItem = {
  key: string
  label: string
  icon: typeof HomeFilled
  query: { feed?: string; channel?: string }
}

const primaryNavItems: NavItem[] = [
  { key: 'recommend', label: '为你推荐', icon: HomeFilled, query: { feed: 'recommend' } },
  ...contentChannels
    .map((channel) => ({
      key: channel.key,
      label: channel.label,
      icon: channelIcons[channel.key],
      query: { channel: channel.key },
    })),
]

const bottomNavItems: NavItem[] = [
  { key: 'following', label: '关注', icon: UserFilled, query: { feed: 'following' } },
  { key: 'friends', label: '朋友动态', icon: Star, query: { feed: 'friends' } },
]

const audienceRows = contentChannels.slice(2, 5)

function routeQueryValue(value: unknown) {
  if (Array.isArray(value)) return value[0] || ''
  return typeof value === 'string' ? value : ''
}

function feedHomePath() {
  return '/home'
}

function navQuery(item: NavItem) {
  const query: Record<string, string> = {}
  const feed = item.query.feed
  const channel = item.query.channel
  if (feed && feed !== 'recommend') query.feed = feed
  if (channel && channel !== 'all') query.channel = channel
  return query
}

function isActive(item: NavItem) {
  if (!(route.path === '/feed' || route.path === '/home' || route.path.startsWith('/posts/'))) return false
  const currentFeed = routeQueryValue(route.query.feed) || 'recommend'
  const currentChannel = routeQueryValue(route.query.channel) || 'all'
  if (item.query.feed) return currentChannel === 'all' && currentFeed === item.query.feed
  if (item.query.channel) return currentChannel === item.query.channel
  return false
}

function handleNav(item: NavItem) {
  if (isActive(item)) {
    window.scrollTo({ top: 0, behavior: 'smooth' })
    return
  }
  void router.push({ path: feedHomePath(), query: navQuery(item) })
}

function jumpToChannel(channel: string) {
  void router.push({ path: feedHomePath(), query: { channel } })
}
</script>

<template>
  <aside :class="['common-left-rail', { 'is-fixed': props.fixed }]" aria-label="页面导航">
    <nav class="common-left-rail__side-nav common-left-rail__side-nav--main">
      <button
        v-for="item in primaryNavItems"
        :key="item.key"
        type="button"
        class="common-left-rail__side-item"
        :class="{ 'is-active': isActive(item) }"
        @click="handleNav(item)"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </button>
    </nav>

    <nav class="common-left-rail__side-nav common-left-rail__side-nav--bottom">
      <button
        v-for="item in bottomNavItems"
        :key="item.key"
        type="button"
        class="common-left-rail__side-item"
        :class="{ 'is-active': isActive(item) }"
        @click="handleNav(item)"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </button>
    </nav>

    <section class="common-left-rail__community">
      <div class="common-left-rail__title">
        <span>细分人群</span>
        <button type="button">
          更多
          <el-icon><ArrowRight /></el-icon>
        </button>
      </div>

      <button
        v-for="audience in audienceRows"
        :key="audience.key"
        type="button"
        class="common-left-rail__community-row"
        @click="jumpToChannel(audience.key)"
      >
        <img :src="audience.avatar" alt="" />
        <span>
          <strong>{{ audience.label }}</strong>
          <small>{{ audience.signal }}</small>
        </span>
      </button>
    </section>
  </aside>
</template>

<style scoped>
.common-left-rail {
  display: flex;
  flex-direction: column;
  width: 214px;
  min-height: calc(100vh - 106px);
  max-height: calc(100vh - 102px);
  overflow: hidden;
  padding: 10px 8px;
  border: 1px solid rgba(26, 31, 44, 0.07);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(32, 36, 47, 0.06);
}

.common-left-rail.is-fixed {
  position: fixed;
  top: 88px;
  left: 14px;
  z-index: 20;
}

.common-left-rail__side-nav {
  display: grid;
  gap: 4px;
}

.common-left-rail__side-nav--main {
  padding-bottom: 14px;
  border-bottom: 1px solid #edf0f4;
}

.common-left-rail__side-item,
.common-left-rail__community-row {
  width: 100%;
  border: none;
  background: transparent;
  cursor: pointer;
}

.common-left-rail__side-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 44px;
  padding: 0 12px;
  border-radius: 8px;
  color: #2f3441;
  font-size: 15px;
  font-weight: 650;
  text-align: left;
  transition: background 0.16s ease, color 0.16s ease;
}

.common-left-rail__side-item .el-icon {
  flex: 0 0 auto;
  font-size: 20px;
}

.common-left-rail__side-item:hover,
.common-left-rail__side-item.is-active {
  background: #fff0ed;
  color: #ff5a45;
}

.common-left-rail__community {
  margin-top: 10px;
  padding-top: 14px;
  border-top: 1px solid #edf0f4;
}

.common-left-rail__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.common-left-rail__title span {
  color: #20242f;
  font-size: 15px;
  font-weight: 800;
}

.common-left-rail__title button {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  border: none;
  background: transparent;
  color: #8a91a0;
  font-size: 12px;
  cursor: pointer;
}

.common-left-rail__community-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  padding: 8px 2px;
  text-align: left;
}

.common-left-rail__community-row img {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  object-fit: cover;
}

.common-left-rail__community-row span {
  min-width: 0;
  display: grid;
  gap: 1px;
}

.common-left-rail__community-row strong {
  overflow: hidden;
  color: #2a2f3b;
  font-size: 13px;
  font-weight: 760;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.common-left-rail__community-row small {
  overflow: hidden;
  color: #9299a7;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.common-left-rail__side-nav--bottom {
  margin-top: 6px;
  padding-top: 12px;
  border-top: 1px solid #edf0f4;
}

@media (max-width: 1280px) {
  .common-left-rail {
    width: 190px;
  }
}

@media (max-width: 980px) {
  .common-left-rail {
    position: static;
    width: auto;
    max-height: none;
    min-height: 0;
    overflow: visible;
    margin-bottom: 8px;
  }

  .common-left-rail__side-nav {
    grid-auto-flow: column;
    grid-auto-columns: max-content;
    overflow-x: auto;
    padding: 0;
    border: none;
    scrollbar-width: none;
  }

  .common-left-rail__side-nav--bottom {
    margin-top: 8px;
    border-top: none;
  }

  .common-left-rail__side-nav::-webkit-scrollbar {
    display: none;
  }

  .common-left-rail__side-item {
    min-height: 38px;
    white-space: nowrap;
  }

  .common-left-rail__community {
    display: none;
  }
}
</style>
