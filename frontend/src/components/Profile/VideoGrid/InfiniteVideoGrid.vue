<script setup lang="ts">
import { toRef, watch } from 'vue'
import { useInfiniteScroll } from '../../../composables/useInfiniteScroll'
import { useVideoLoader } from '../../../composables/useVideoLoader'
import type { PostView } from '../../../types'
import VideoCard from './VideoCard.vue'

const props = withDefaults(
  defineProps<{
    videos: PostView[]
    sourceLoading?: boolean
    emptyText?: string
  }>(),
  {
    sourceLoading: false,
    emptyText: '暂时没有更多了',
  },
)

const emit = defineEmits<{
  open: [id: number]
}>()

const { visibleItems, resetAndLoad, loadNextPage } = useVideoLoader(toRef(props, 'videos'), 9)
const infinite = useInfiniteScroll(loadNextPage)

function setSentinel(el: unknown) {
  infinite.sentinel.value = el instanceof HTMLElement ? el : null
}

watch(
  () => props.videos,
  async () => {
    infinite.resetInfiniteState()
    const hasMore = await resetAndLoad()
    infinite.finished.value = !hasMore
  },
  { immediate: true },
)
</script>

<template>
  <section class="video-grid" aria-live="polite">
    <div v-if="sourceLoading" class="video-grid__wrapper">
      <div v-for="idx in 9" :key="idx" class="video-grid__skeleton" />
    </div>

    <div v-else-if="visibleItems.length" class="video-grid__wrapper">
      <VideoCard v-for="video in visibleItems" :key="video.id" :video="video" @open="emit('open', $event)" />
    </div>

    <div v-else class="video-grid__empty">{{ emptyText }}</div>

    <div :ref="setSentinel" class="video-grid__sentinel" aria-hidden="true" />
    <div v-if="infinite.loading.value && !sourceLoading" class="video-grid__loading">加载中...</div>
    <div v-else-if="infinite.finished.value && visibleItems.length" class="video-grid__done">暂时没有更多了</div>
  </section>
</template>

<style scoped>
.video-grid {
  min-width: 0;
}

.video-grid__wrapper {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 3px;
}

.video-grid__skeleton {
  min-height: 360px;
  border-radius: 4px;
  background: linear-gradient(110deg, var(--skeleton-base, #eceff4) 30%, var(--skeleton-highlight, #f7f9fc) 50%, var(--skeleton-base, #eceff4) 70%);
  background-size: 200% 100%;
  animation: video-grid-skeleton 1.2s linear infinite;
}

.video-grid__sentinel {
  height: 1px;
}

.video-grid__loading,
.video-grid__done,
.video-grid__empty {
  padding: 24px 12px;
  color: var(--text-muted, #64748b);
  text-align: center;
  font-size: 14px;
}

.video-grid__empty {
  border: 1px dashed rgba(15, 23, 42, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
}

@keyframes video-grid-skeleton {
  0% {
    background-position: 200% 0;
  }

  100% {
    background-position: -200% 0;
  }
}

@media (max-width: 560px) {
  .video-grid__wrapper {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
