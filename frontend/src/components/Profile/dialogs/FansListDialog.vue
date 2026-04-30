<script setup lang="ts">
import { Close, Search } from '@element-plus/icons-vue'
import { computed, nextTick, ref, watch } from 'vue'
import { api } from '../../../services/api'
import type { UserSummary } from '../../../types'
import { useInfiniteScroll } from '../../../composables/useInfiniteScroll'

const props = defineProps<{
  visible: boolean
  targetUserId: number | null
  total: number
  followTotal: number
}>()

const emit = defineEmits<{
  close: []
  openUser: [id: number]
  switchTab: [type: 'follow' | 'fans']
}>()

const keyword = ref('')
const items = ref<UserSummary[]>([])
const page = ref(1)
const pageSize = 20

function normalizeMediaUrl(url?: string | null) {
  if (!url) return ''
  return String(url).replace('http://localhost:9000', '/minio-img')
}

async function loadPage() {
  if (!props.targetUserId) return false
  const result = await api.followersPage(props.targetUserId, page.value, pageSize)
  const seen = new Set(items.value.map((item) => item.id))
  items.value.push(...result.records.filter((item) => !seen.has(item.id)))
  page.value += 1
  return items.value.length < result.total
}

const infinite = useInfiniteScroll(loadPage)

function setSentinel(el: unknown) {
  infinite.sentinel.value = el instanceof HTMLElement ? el : null
}

const filteredList = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return items.value
  return items.value.filter((item) => [item.nickname, item.username, item.userNo].join(' ').toLowerCase().includes(q))
})

async function resetDialog() {
  items.value = []
  page.value = 1
  keyword.value = ''
  infinite.resetInfiniteState()
  await nextTick()
  await infinite.runLoad()
}

watch(
  () => [props.visible, props.targetUserId] as const,
  ([visible]) => {
    if (visible) void resetDialog()
  },
)
</script>

<template>
  <Teleport to="body">
    <Transition name="relation-dialog">
      <div v-if="visible" class="relation-dialog__backdrop" @click.self="emit('close')">
        <section class="relation-dialog__panel" role="dialog" aria-modal="true" aria-label="粉丝列表">
          <header class="relation-dialog__header">
            <div class="relation-dialog__tabs">
              <button type="button" @click="emit('switchTab', 'follow')">关注 ({{ followTotal }})</button>
              <button type="button" class="is-active" @click="emit('switchTab', 'fans')">粉丝 ({{ total }})</button>
            </div>
            <button type="button" class="relation-dialog__close" aria-label="关闭" @click="emit('close')">
              <el-icon><Close /></el-icon>
            </button>
          </header>

          <label class="relation-dialog__search">
            <el-icon><Search /></el-icon>
            <input v-model="keyword" type="search" placeholder="搜索用户名或抖音号" />
          </label>

          <ul class="relation-dialog__list">
            <li v-for="user in filteredList" :key="user.id" class="relation-dialog__item" @click="emit('openUser', user.id)">
              <img :src="normalizeMediaUrl(user.avatarUrl) || '/auto_picture.png'" alt="" loading="lazy" decoding="async" />
              <div>
                <p>{{ user.nickname }}</p>
                <span>抖音号：{{ user.userNo || user.username }}</span>
              </div>
              <button type="button" @click.stop>回关</button>
              <button type="button" class="is-muted" @click.stop>移除</button>
            </li>
            <li :ref="setSentinel" class="relation-dialog__sentinel" aria-hidden="true" />
          </ul>

          <p v-if="infinite.loading.value" class="relation-dialog__state">加载中...</p>
          <p v-else-if="infinite.finished.value && items.length" class="relation-dialog__state">暂时没有更多了</p>
          <p v-else-if="!infinite.loading.value && !filteredList.length" class="relation-dialog__state">暂无粉丝</p>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.relation-dialog-enter-from,
.relation-dialog-leave-to {
  opacity: 0;
}

.relation-dialog-enter-from .relation-dialog__panel,
.relation-dialog-leave-to .relation-dialog__panel {
  transform: translateY(10px) scale(0.98);
}

.relation-dialog-enter-active {
  transition: opacity 0.18s ease;
}

.relation-dialog-enter-active .relation-dialog__panel {
  transition: transform 0.24s cubic-bezier(0.22, 1, 0.36, 1);
}

.relation-dialog-leave-active {
  transition: opacity 0.16s ease;
}

.relation-dialog-leave-active .relation-dialog__panel {
  transition: transform 0.16s ease-in;
}

.relation-dialog__backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(2px);
}

.relation-dialog__panel {
  width: min(560px, calc(100vw - 32px));
  height: min(75vh, 680px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border-radius: 14px;
  background: #fff;
  color: #161823;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.3);
}

.relation-dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 82px;
  padding: 24px 38px 12px;
}

.relation-dialog__tabs {
  display: flex;
  align-items: center;
  gap: 28px;
}

.relation-dialog__tabs button {
  position: relative;
  min-height: 36px;
  padding: 0;
  border: none;
  background: transparent;
  color: #8a8f99;
  cursor: pointer;
  font-size: 16px;
  font-weight: 700;
}

.relation-dialog__tabs button.is-active {
  color: #161823;
  font-weight: 900;
}

.relation-dialog__tabs button.is-active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 3px;
  border-radius: 999px;
  background: #fe2c55;
}

.relation-dialog__close {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #a3a8b3;
  cursor: pointer;
  font-size: 22px;
}

.relation-dialog__search {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 38px 14px;
  padding: 0 12px;
  min-height: 32px;
  border-radius: 8px;
  background: #f5f6f8;
  color: #64748b;
}

.relation-dialog__search input {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  color: #161823;
  font: inherit;
}

.relation-dialog__list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  margin: 0;
  padding: 0 38px 14px;
  list-style: none;
}

.relation-dialog__item {
  display: grid;
  grid-template-columns: 60px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 14px;
  min-height: 98px;
  padding: 14px 4px;
  border-bottom: 1px solid #eceef2;
  border-radius: 0;
  cursor: pointer;
}

.relation-dialog__item:hover {
  background: transparent;
}

.relation-dialog__item img {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  object-fit: cover;
}

.relation-dialog__item p,
.relation-dialog__item span {
  overflow: hidden;
  margin: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.relation-dialog__item p {
  font-weight: 850;
}

.relation-dialog__item span {
  display: block;
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.relation-dialog__item button {
  min-width: 88px;
  min-height: 36px;
  border: none;
  border-radius: 9px;
  padding: 0 16px;
  background: #fe2c55;
  color: #fff;
  cursor: pointer;
  font-weight: 800;
}

.relation-dialog__item button.is-muted {
  min-width: 72px;
  background: #f6f7f9;
  color: #161823;
}

.relation-dialog__state {
  margin: 0;
  padding: 12px;
  color: #64748b;
  text-align: center;
  font-size: 13px;
}

.relation-dialog__sentinel {
  display: block;
  height: 1px;
}
</style>
