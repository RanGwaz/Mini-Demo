<script setup lang="ts">
import {
  ArrowRight,
  Camera,
  CoffeeCup,
  HomeFilled,
  MagicStick,
  Notebook,
  Star,
  UserFilled,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

const props = withDefaults(defineProps<{
  fixed?: boolean
}>(), {
  fixed: true,
})

const router = useRouter()
const route = useRoute()

const navItems = [
  { key: 'recommend', label: '为你推荐', icon: HomeFilled, path: '/feed' },
  { key: 'following', label: '关注', icon: UserFilled },
  { key: 'friends', label: '朋友动态', icon: Star },
  { key: 'campus', label: '大学生校园生活', icon: Notebook },
  { key: 'photo', label: '摄影爱好者', icon: Camera },
  { key: 'ai', label: 'AI工具分享', icon: MagicStick },
  { key: 'dev', label: '程序员摸鱼社区', icon: CoffeeCup },
] satisfies Array<{
  key: string
  label: string
  icon: typeof HomeFilled
  path?: string
}>

const audienceRows = [
  { name: '二次元穿搭', members: '6.9万活跃', avatar: 'https://picsum.photos/seed/sidebar-anime/80/80' },
  { name: '宠物日常', members: '15.1万活跃', avatar: 'https://picsum.photos/seed/sidebar-pet/80/80' },
  { name: '留学生生活', members: '5.4万活跃', avatar: 'https://picsum.photos/seed/sidebar-abroad/80/80' },
]

function isActive(itemPath?: string) {
  if (!itemPath) return false
  if (itemPath === '/feed') return route.path === '/feed' || route.path.startsWith('/posts/')
  return route.path === itemPath
}

function handleNav(item: (typeof navItems)[number]) {
  if (item.path) {
    if (isActive(item.path)) {
      window.scrollTo({ top: 0, behavior: 'smooth' })
      return
    }
    void router.push(item.path)
    return
  }
  ElMessage.info(`${item.label} 内容筛选正在接入中`)
}
</script>

<template>
  <aside :class="['common-left-rail', { 'is-fixed': props.fixed }]" aria-label="页面导航">
    <nav class="common-left-rail__side-nav">
      <button
        v-for="item in navItems"
        :key="item.key"
        type="button"
        class="common-left-rail__side-item"
        :class="{ 'is-active': isActive(item.path) }"
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
        :key="audience.name"
        type="button"
        class="common-left-rail__community-row"
      >
        <img :src="audience.avatar" alt="" />
        <span>
          <strong>{{ audience.name }}</strong>
          <small>{{ audience.members }}</small>
        </span>
      </button>
    </section>
  </aside>
</template>

<style scoped>
.common-left-rail {
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
  padding-top: 18px;
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
    padding-bottom: 0;
    border-bottom: none;
    scrollbar-width: none;
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
