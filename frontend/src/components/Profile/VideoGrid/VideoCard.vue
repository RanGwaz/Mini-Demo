<script setup lang="ts">
import { ChatDotRound, StarFilled, VideoPlay } from '@element-plus/icons-vue'
import type { PostView } from '../../../types'

const props = defineProps<{
  video: PostView
}>()

const emit = defineEmits<{
  open: [id: number]
}>()

function normalizeMediaUrl(url?: string | null) {
  if (!url) return ''
  return String(url).replace('http://localhost:9000', '/minio-img')
}

function cardImage(post: PostView) {
  const asset = post.assets?.[0]
  return normalizeMediaUrl(asset?.thumbUrl || post.thumbUrl || post.coverUrl || asset?.fileUrl) || '/auto_picture.png'
}

function formatCount(value?: number) {
  const n = Number(value || 0)
  if (n >= 10000) {
    const w = n / 10000
    return (w >= 10 ? Math.round(w).toString() : w.toFixed(1).replace(/\.0$/, '')) + 'w'
  }
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(Math.max(0, n))
}
</script>

<template>
  <article class="video-card" tabindex="0" @click="emit('open', props.video.id)" @keyup.enter="emit('open', props.video.id)">
    <div class="video-card__media">
      <img class="video-card__thumb" :src="cardImage(video)" :alt="video.title || '作品封面'" loading="lazy" decoding="async" />
      <div class="video-card__views">
        <el-icon><VideoPlay /></el-icon>
        <span>{{ formatCount(video.viewCount) }}</span>
      </div>
    </div>
    <div class="video-card__body">
      <strong>{{ video.title || '作品 ' + video.id }}</strong>
      <p v-if="video.content">{{ video.content }}</p>
      <div class="video-card__meta">
        <span>
          <el-icon><StarFilled /></el-icon>
          {{ formatCount(video.likeCount + video.favoriteCount) }}
        </span>
        <span>
          <el-icon><ChatDotRound /></el-icon>
          {{ formatCount(video.commentCount) }}
        </span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.video-card {
  min-width: 0;
  overflow: hidden;
  border: none;
  border-radius: 4px;
  background: var(--bg-solid, #fff);
  cursor: pointer;
  outline: none;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease;
}

.video-card:hover,
.video-card:focus-visible {
  transform: translateY(-2px);
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.14);
}

.video-card:focus-visible {
  box-shadow:
    0 0 0 3px rgba(254, 44, 85, 0.22),
    0 14px 30px rgba(15, 23, 42, 0.14);
}

.video-card__media {
  position: relative;
  overflow: hidden;
  background: linear-gradient(110deg, var(--skeleton-base, #eceff4) 30%, var(--skeleton-highlight, #f7f9fc) 50%, var(--skeleton-base, #eceff4) 70%);
  background-size: 200% 100%;
  animation: video-card-shimmer 1.2s linear infinite;
}

.video-card__thumb {
  display: block;
  width: 100%;
  aspect-ratio: 9 / 16;
  object-fit: cover;
}

.video-card__views {
  position: absolute;
  left: 8px;
  bottom: 8px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.72);
}

.video-card__body {
  padding: 9px 8px 10px;
}

.video-card__body strong {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-primary, #161823);
  font-size: 14px;
  line-height: 1.35;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.video-card__body p {
  display: -webkit-box;
  overflow: hidden;
  margin: 5px 0 0;
  color: var(--text-muted, #64748b);
  font-size: 12px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.video-card__meta {
  display: flex;
  gap: 12px;
  margin-top: 8px;
  color: var(--text-muted, #64748b);
  font-size: 12px;
  font-weight: 700;
}

.video-card__meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

@keyframes video-card-shimmer {
  0% {
    background-position: 200% 0;
  }

  100% {
    background-position: -200% 0;
  }
}
</style>
