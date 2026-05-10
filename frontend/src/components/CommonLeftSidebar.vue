<script setup lang="ts">
import {
  ArrowRight,
  Brush,
  Camera,
  ChatDotRound,
  Compass,
  Cpu,
  Food,
  HomeFilled,
  House,
  MagicStick,
  Notebook,
  Opportunity,
  Place,
  Reading,
  School,
  Suitcase,
  UserFilled,
} from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { contentChannels } from '../domain/contentTaxonomy'
import { api, type ChannelView } from '../services/api'

const props = withDefaults(defineProps<{
  fixed?: boolean
}>(), {
  fixed: true,
})

const router = useRouter()
const route = useRoute()

const channelIcons: Record<string, typeof HomeFilled> = {
  campus: Notebook,
  anime_outfit: Brush,
  pet: Opportunity,
  photography: Camera,
  tech_moment: School,
  overseas_life: Compass,
  ai_tools: Cpu,
  food_explore: Food,
  weekend_trip: Suitcase,
  home_life: House,
}

type SidebarChannel = {
  key: string
  label: string
  signal: string
  avatar: string
}

type NavItem = {
  key: string
  label: string
  icon: typeof HomeFilled
  query?: { feed?: string }
  path?: string
}

const sidebarChannels = ref<SidebarChannel[]>(contentChannels.map(mapStaticSidebarChannel))

const primaryNavItems = computed<NavItem[]>(() => [
  { key: 'recommend', label: '为你推荐', icon: HomeFilled, query: { feed: 'recommend' } },
  ...sidebarChannels.value
    .map((channel) => ({
      key: channel.key,
      label: channel.label,
      icon: resolveChannelIcon(channel.key, channel.label),
      path: `/channels/${channel.key}`,
    })),
])

const bottomNavItems: NavItem[] = [
  { key: 'following', label: '关注', icon: UserFilled, query: { feed: 'following' } },
  { key: 'friends', label: '朋友动态', icon: ChatDotRound, query: { feed: 'friends' } },
]

const audienceRows = computed(() => sidebarChannels.value.slice(2, 5))

onMounted(() => {
  void loadSidebarChannels()
})

function mapStaticSidebarChannel(channel: (typeof contentChannels)[number]): SidebarChannel {
  return {
    key: channel.key,
    label: channel.label,
    signal: channel.signal,
    avatar: channel.avatar,
  }
}

function mapApiSidebarChannel(channel: ChannelView): SidebarChannel {
  const fallback = contentChannels.find((item) => item.key === channel.code)
  return {
    key: channel.code,
    label: channel.name,
    signal: fallback?.signal || (channel.waterfall ? '瀑布流展示' : '专题流展示'),
    avatar: channel.icon || fallback?.avatar || `https://picsum.photos/seed/sidebar-${channel.code}/80/80`,
  }
}

function resolveChannelIcon(key: string, label = '') {
  if (channelIcons[key]) return channelIcons[key]
  if (label.includes('摄影')) return Camera
  if (label.includes('校园')) return Notebook
  if (label.includes('宠物') || label.includes('猫') || label.includes('狗')) return Opportunity
  if (label.includes('二次元') || label.includes('穿搭')) return Brush
  if (label.includes('AI') || label.includes('效率') || label.includes('工具')) return Cpu
  if (label.includes('美食') || label.includes('探店')) return Food
  if (label.includes('旅行') || label.includes('周末')) return Suitcase
  if (label.includes('留学') || label.includes('海外')) return Compass
  if (label.includes('家居') || label.includes('生活')) return House
  if (label.includes('学习') || label.includes('读书')) return Reading
  if (label.includes('地点') || label.includes('城市')) return Place
  return MagicStick
}

async function loadSidebarChannels() {
  try {
    const channels = await api.channels()
    const next = channels
      .filter((item) => item.code && item.name)
      .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
      .map(mapApiSidebarChannel)
    if (next.length > 0) sidebarChannels.value = next
  } catch {
    sidebarChannels.value = contentChannels.map(mapStaticSidebarChannel)
  }
}

function routeQueryValue(value: unknown) {
  if (Array.isArray(value)) return value[0] || ''
  return typeof value === 'string' ? value : ''
}

function feedHomePath() {
  return '/home'
}

function navQuery(item: NavItem) {
  const query: Record<string, string> = {}
  const feed = item.query?.feed
  if (feed && feed !== 'recommend') query.feed = feed
  return query
}

function isActive(item: NavItem) {
  if (item.path) return route.path === item.path
  if (!(route.path === '/feed' || route.path === '/home' || route.path.startsWith('/posts/'))) return false
  const currentFeed = routeQueryValue(route.query.feed) || 'recommend'
  const currentChannel = routeQueryValue(route.query.channel) || 'all'
  if (item.query?.feed) return currentChannel === 'all' && currentFeed === item.query.feed
  return false
}

function handleNav(item: NavItem) {
  if (isActive(item)) {
    window.scrollTo({ top: 0, behavior: 'smooth' })
    return
  }
  if (item.path) {
    void router.push(item.path)
    return
  }
  void router.push({ path: feedHomePath(), query: navQuery(item) })
}

function jumpToChannel(channel: string) {
  void router.push(`/channels/${channel}`)
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
  border: 1px solid rgba(26, 31, 44, 0.08);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(250, 251, 253, 0.98)),
    #fff;
  box-shadow: 0 18px 42px rgba(32, 36, 47, 0.08);
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
  position: relative;
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
  transition: background 0.16s ease, color 0.16s ease, transform 0.16s ease, box-shadow 0.16s ease;
}

.common-left-rail__side-item .el-icon {
  flex: 0 0 auto;
  font-size: 20px;
  transition: transform 0.16s ease;
}

.common-left-rail__side-item:hover,
.common-left-rail__side-item.is-active {
  background:
    linear-gradient(90deg, rgba(255, 90, 69, 0.13), rgba(255, 90, 69, 0.04)),
    #fff;
  color: #ff5a45;
  box-shadow: inset 0 0 0 1px rgba(255, 90, 69, 0.08);
}

.common-left-rail__side-item:hover {
  transform: translateX(2px);
}

.common-left-rail__side-item:hover .el-icon,
.common-left-rail__side-item.is-active .el-icon {
  transform: scale(1.06);
}

.common-left-rail__side-item.is-active::before {
  content: '';
  position: absolute;
  left: 4px;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 999px;
  background: #ff5a45;
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

.common-left-rail__title button:hover {
  color: #ff5a45;
}

.common-left-rail__community-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  padding: 8px 2px;
  border-radius: 8px;
  text-align: left;
  transition: background 0.16s ease, transform 0.16s ease;
}

.common-left-rail__community-row:hover {
  background: #f7f8fb;
  transform: translateX(2px);
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
