<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  /** 无障碍：导航区域标签 */
  navAriaLabel: string
  placement: 'above' | 'below'
  currentPage: number
  totalPages: number
  totalItems: number | null
  loading: boolean
  pageItems: Array<number | 'ellipsis'>
  hasPrev: boolean
  hasNext: boolean
}>()

const emit = defineEmits<{
  prev: []
  next: []
  goto: [page: number]
  jump: [page: number]
}>()

const jumpInput = ref('')

watch(
  () => props.currentPage,
  (p) => {
    jumpInput.value = String(p)
  },
  { immediate: true },
)

function submitJump() {
  if (props.totalPages < 1 || props.loading) {
    return
  }
  const n = Number.parseInt(String(jumpInput.value).trim(), 10)
  if (!Number.isFinite(n)) {
    return
  }
  const clamped = Math.min(props.totalPages, Math.max(1, n))
  jumpInput.value = String(clamped)
  emit('jump', clamped)
}
</script>

<template>
  <nav
    class="feed-pagination feed-pagination--pager"
    :class="placement === 'above' ? 'feed-pagination--above' : 'feed-pagination--below'"
    :aria-label="navAriaLabel"
  >
    <div v-if="totalPages > 0" class="feed-pagination__summary">
      <span class="feed-pagination__summary-line">
        第 <strong>{{ currentPage }}</strong> / <strong>{{ totalPages }}</strong> 页
        <template v-if="totalItems != null">
          · 共 <strong>{{ totalItems }}</strong> 条
        </template>
      </span>
      <span v-if="loading" class="feed-pagination__summary-loading">加载中…</span>
    </div>
    <div class="feed-pagination-pager__row">
      <button
        type="button"
        class="feed-pagination-pager__nav feed-pagination-pager__nav--edge feed-pagination-pager__nav--compact"
        :disabled="currentPage <= 1 || loading"
        @click="emit('goto', 1)"
      >
        首页
      </button>
      <button
        type="button"
        class="feed-pagination-pager__nav feed-pagination-pager__nav--edge"
        :disabled="!hasPrev || loading"
        @click="emit('prev')"
      >
        上一页
      </button>
      <template v-for="(item, idx) in pageItems" :key="idx">
        <span v-if="item === 'ellipsis'" class="feed-pagination-pager__ellipsis" aria-hidden="true">...</span>
        <button
          v-else
          type="button"
          class="feed-pagination-pager__num"
          :class="{ 'feed-pagination-pager__num--active': item === currentPage }"
          :disabled="loading || item === currentPage"
          :aria-current="item === currentPage ? 'page' : undefined"
          @click="emit('goto', item)"
        >
          {{ item }}
        </button>
      </template>
      <button
        type="button"
        class="feed-pagination-pager__nav feed-pagination-pager__nav--edge"
        :disabled="!hasNext || loading"
        @click="emit('next')"
      >
        下一页
      </button>
      <button
        type="button"
        class="feed-pagination-pager__nav feed-pagination-pager__nav--edge feed-pagination-pager__nav--compact"
        :disabled="currentPage >= totalPages || loading || totalPages < 1"
        @click="emit('goto', totalPages)"
      >
        末页
      </button>
    </div>
    <div v-if="totalPages > 0" class="feed-pagination-pager__jump">
      <span class="feed-pagination-pager__jump-label">跳转至</span>
      <input
        v-model="jumpInput"
        class="feed-pagination-pager__jump-input"
        type="text"
        inputmode="numeric"
        pattern="[0-9]*"
        :disabled="loading"
        :aria-label="`页码，1 到 ${totalPages}`"
        @keydown.enter.prevent="submitJump"
      />
      <span class="feed-pagination-pager__jump-suffix">页</span>
      <button
        type="button"
        class="feed-pagination-pager__jump-btn"
        :disabled="loading"
        @click="submitJump"
      >
        确定
      </button>
    </div>
    <p
      v-if="placement === 'below' && !hasNext && !loading && totalPages > 0"
      class="feed-pagination__end"
    >
      已到达最后一页
    </p>
  </nav>
</template>
