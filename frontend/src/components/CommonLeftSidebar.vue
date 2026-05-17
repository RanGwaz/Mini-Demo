<script setup lang="ts">
import { HomeFilled, Plus } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'

defineOptions({ name: 'CommonLeftSidebar' })

const props = withDefaults(defineProps<{
  fixed?: boolean
}>(), {
  fixed: true,
})

const route = useRoute()
const router = useRouter()

const navItems = [
  { key: 'home', label: '首页', icon: HomeFilled, path: '/home' },
  { key: 'publish', label: '发布', icon: Plus, path: '/publish' },
]

function isActive(path: string) {
  if (path === '/home') return route.path === '/' || route.path === '/home' || route.path === '/feed'
  return route.path.startsWith(path)
}

function go(path: string) {
  if (isActive(path)) {
    window.scrollTo({ top: 0, behavior: 'smooth' })
    return
  }
  void router.push(path)
}
</script>

<template>
  <aside :class="['common-left-rail', { 'is-fixed': props.fixed }]" aria-label="页面导航">
    <nav class="common-left-rail__nav">
      <button
        v-for="item in navItems"
        :key="item.key"
        type="button"
        class="common-left-rail__item"
        :class="{ 'is-active': isActive(item.path) }"
        @click="go(item.path)"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </button>
    </nav>
  </aside>
</template>

<style scoped>
.common-left-rail {
  display: flex;
  flex-direction: column;
  width: 92px;
  min-height: calc(100vh - 96px);
  padding: 10px;
  border-right: 1px solid rgba(31, 41, 55, 0.08);
  background: rgba(255, 255, 255, 0.96);
}

.common-left-rail.is-fixed {
  position: fixed;
  top: 74px;
  left: 0;
  z-index: 20;
}

.common-left-rail__nav {
  display: grid;
  gap: 10px;
}

.common-left-rail__item {
  display: grid;
  place-items: center;
  gap: 6px;
  min-height: 68px;
  border: none;
  border-radius: 18px;
  background: transparent;
  color: #232936;
  cursor: pointer;
  font-size: 13px;
  font-weight: 760;
  transition: background 0.16s ease, color 0.16s ease, transform 0.16s ease;
}

.common-left-rail__item .el-icon {
  font-size: 24px;
}

.common-left-rail__item:hover,
.common-left-rail__item.is-active {
  background: #111827;
  color: #fff;
}

.common-left-rail__item:hover {
  transform: translateY(-1px);
}

@media (max-width: 780px) {
  .common-left-rail {
    position: sticky;
    top: 62px;
    z-index: 18;
    width: 100%;
    min-height: 0;
    padding: 8px 10px;
    border-right: none;
    border-bottom: 1px solid rgba(31, 41, 55, 0.08);
  }

  .common-left-rail.is-fixed {
    position: sticky;
    top: 62px;
    left: auto;
  }

  .common-left-rail__nav {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .common-left-rail__item {
    min-height: 44px;
    grid-auto-flow: column;
    justify-content: center;
  }
}
</style>
