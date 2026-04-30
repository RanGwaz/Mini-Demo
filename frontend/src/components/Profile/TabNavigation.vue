<script setup lang="ts">
import { Lock } from '@element-plus/icons-vue'

export interface ProfileTab {
  key: string
  label: string
  locked?: boolean
}

const props = defineProps<{
  tabs: ProfileTab[]
  activeTab: string
  showBatch?: boolean
}>()

const emit = defineEmits<{
  'update:activeTab': [key: string]
  locked: [tab: ProfileTab]
  batch: []
}>()

function handleTabClick(tab: ProfileTab) {
  if (tab.locked) {
    emit('locked', tab)
    return
  }
  emit('update:activeTab', tab.key)
}
</script>

<template>
  <nav class="tab-nav" aria-label="主页内容分区">
    <div class="tab-nav__scroll">
      <button
        v-for="tab in props.tabs"
        :key="tab.key"
        type="button"
        class="tab-nav__item"
        :class="{ 'is-active': activeTab === tab.key, 'is-locked': tab.locked }"
        @click="handleTabClick(tab)"
      >
        <span>{{ tab.label }}</span>
        <el-icon v-if="tab.locked"><Lock /></el-icon>
      </button>
    </div>
    <button v-if="showBatch" type="button" class="tab-nav__batch" @click="emit('batch')">批量管理</button>
  </nav>
</template>

<style scoped>
.tab-nav {
  position: sticky;
  top: 228px;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0 8px;
  background: color-mix(in srgb, var(--bg-page-end, #f8fafc) 82%, transparent);
  backdrop-filter: blur(14px);
}

.tab-nav__scroll {
  display: flex;
  gap: 4px;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.tab-nav__scroll::-webkit-scrollbar {
  display: none;
}

.tab-nav__item {
  position: relative;
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 42px;
  padding: 0 14px;
  border: none;
  border-radius: 0;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 15px;
  font-weight: 800;
}

.tab-nav__item::after {
  content: '';
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 0;
  height: 3px;
  border-radius: 999px;
  background: #161823;
  transform: scaleX(0);
  transform-origin: center;
  transition: transform 0.24s cubic-bezier(0.22, 1, 0.36, 1);
}

.tab-nav__item.is-active {
  color: #161823;
}

.tab-nav__item.is-active::after {
  transform: scaleX(1);
}

.tab-nav__item.is-locked {
  color: #94a3b8;
}

.tab-nav__item .el-icon {
  font-size: 13px;
}

.tab-nav__batch {
  flex: none;
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 999px;
  background: #fff;
  color: #161823;
  cursor: pointer;
  font-weight: 800;
}

@media (max-width: 760px) {
  .tab-nav {
    top: 0;
    padding-left: 4px;
  }

  .tab-nav__batch {
    display: none;
  }
}
</style>
