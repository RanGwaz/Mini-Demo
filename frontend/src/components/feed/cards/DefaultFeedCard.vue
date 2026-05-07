<script setup lang="ts">
defineOptions({ name: 'DefaultFeedCard' })

import { computed, ref, watch } from 'vue'
import type { PostView } from '../../../types'
import { DEFAULT_IMAGE_PLACEHOLDER, getPostMediaUrl, hasPostMedia, normalizeMediaUrl } from '../../../utils/postMedia'
import { formatRelativeTimeZh } from '../../../utils/relativeTime'

type FeedCardVariant = 'default' | 'campus' | 'anime' | 'pet' | 'photography' | 'tech'

const props = withDefaults(defineProps<{
  post: PostView
  isLiked?: boolean
  isLiking?: boolean
  likeCount?: number
  variant?: FeedCardVariant
}>(), {
  isLiked: false,
  isLiking: false,
  likeCount: undefined,
  variant: 'default',
})

const emit = defineEmits<{
  open: [post: PostView]
  like: [post: PostView]
}>()

const imageVisible = ref(false)
const imageFailed = ref(false)

watch(() => props.post.id, () => {
  imageVisible.value = false
  imageFailed.value = false
})

const extra = computed(() => props.post.extra || {})
const mediaUrl = computed(() => getPostMediaUrl(props.post) || DEFAULT_IMAGE_PLACEHOLDER)
const shouldShowMedia = computed(() => hasPostMedia(props.post) && !imageFailed.value && props.variant !== 'tech')
const channelCode = computed(() => props.post.channelCode || props.post.channel || 'general')
const channelLabel = computed(() => {
  switch (channelCode.value) {
    case 'campus': return '校园生活'
    case 'anime_outfit': return '二次元穿搭'
    case 'pet': return '宠物日常'
    case 'photography': return '摄影分享'
    case 'tech_moment': return '程序员摸鱼'
    default: return '动态'
  }
})
const authorName = computed(() => props.post.author?.nickname || '匿名用户')
const authorAvatar = computed(() => normalizeMediaUrl(props.post.author?.avatarUrl) || DEFAULT_IMAGE_PLACEHOLDER)
const displayedLikeCount = computed(() => props.likeCount ?? props.post.likeCount ?? 0)
const collectCount = computed(() => props.post.collectCount ?? props.post.favoriteCount ?? 0)

function extraText(key: string) {
  const value = extra.value[key]
  if (Array.isArray(value)) return value.filter(Boolean).join(' / ')
  if (value === null || value === undefined || typeof value === 'object') return ''
  return String(value).trim()
}

const metaItems = computed(() => {
  const items = (() => {
    switch (props.variant) {
      case 'campus':
        return [
          { label: '场景', value: extraText('campusScene') },
          { label: '心情', value: extraText('mood') },
          { label: '话题', value: extraText('topic') },
        ]
      case 'anime':
        return [
          { label: '风格', value: extraText('style') },
          { label: '主色', value: extraText('mainColor') },
          { label: '季节', value: extraText('season') },
        ]
      case 'pet':
        return [
          { label: '名字', value: extraText('petName') },
          { label: '类型', value: extraText('petType') },
          { label: '品种', value: extraText('petBreed') },
          { label: '心情', value: extraText('mood') },
        ]
      case 'photography':
        return [
          { label: '地点', value: extraText('location') },
          { label: '相机', value: extraText('camera') },
          { label: '镜头', value: extraText('lens') },
        ]
      case 'tech':
        return [
          { label: '工具', value: extraText('toolName') },
          { label: '价格', value: extraText('priceType') },
          { label: '平台', value: extraText('platform') },
        ]
      default:
        return []
    }
  })()
  return items.filter((item) => item.value)
})

const tagList = computed(() => (props.post.tags || []).filter(Boolean).slice(0, 4))
const actionText = computed(() => props.isLiked ? '♥' : '♡')

function formatCount(value?: number | null) {
  const count = Number(value || 0)
  if (count >= 10000) return `${(count / 10000).toFixed(count >= 100000 ? 0 : 1)}万`
  if (count >= 1000) return `${(count / 1000).toFixed(count >= 10000 ? 0 : 1)}k`
  return String(Math.max(0, count))
}
</script>

<template>
  <article class="channel-card" :class="`channel-card--${variant}`" @click="emit('open', post)">
    <div v-if="shouldShowMedia" class="channel-card__media">
      <div v-if="!imageVisible" class="channel-card__skeleton ui-skeleton" />
      <img
        class="channel-card__image"
        :class="{ 'is-visible': imageVisible }"
        :src="mediaUrl"
        :alt="post.title || 'post image'"
        loading="lazy"
        decoding="async"
        @load="imageVisible = true"
        @error="imageFailed = true"
      />
    </div>

    <div class="channel-card__body">
      <div class="channel-card__topline">
        <span class="channel-card__channel">{{ channelLabel }}</span>
        <span>{{ formatRelativeTimeZh(post.createdAt) }}</span>
      </div>

      <div class="channel-card__author">
        <img :src="authorAvatar" alt="" />
        <strong>{{ authorName }}</strong>
      </div>

      <h3>{{ post.title || '分享一刻值得收藏的日常' }}</h3>
      <p v-if="post.content?.trim()" class="channel-card__desc">{{ post.content }}</p>

      <dl v-if="metaItems.length > 0" class="channel-card__meta">
        <template v-for="item in metaItems" :key="item.label">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </template>
      </dl>

      <div v-if="tagList.length > 0" class="channel-card__tags">
        <span v-for="tag in tagList" :key="tag">#{{ tag }}</span>
      </div>

      <div class="channel-card__actions">
        <button
          type="button"
          :class="{ 'is-liked': isLiked }"
          :disabled="isLiking"
          aria-label="点赞"
          @click.stop="emit('like', post)"
        >
          <span>{{ actionText }}</span>{{ formatCount(displayedLikeCount) }}
        </button>
        <span>评 {{ formatCount(post.commentCount) }}</span>
        <span>藏 {{ formatCount(collectCount) }}</span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.channel-card {
  overflow: hidden;
  border: 1px solid #e8ebf0;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(32, 36, 47, 0.05);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.channel-card:hover {
  transform: translateY(-2px);
  border-color: rgba(255, 90, 69, 0.22);
  box-shadow: 0 16px 34px rgba(32, 36, 47, 0.09);
}

.channel-card__media {
  display: grid;
  overflow: hidden;
  min-height: 132px;
  background: #f0f2f5;
}

.channel-card__skeleton,
.channel-card__image {
  grid-area: 1 / 1;
}

.channel-card__image {
  display: block;
  width: 100%;
  min-height: 132px;
  max-height: 420px;
  object-fit: cover;
  opacity: 0;
  transition: opacity 0.22s ease, transform 0.36s ease;
}

.channel-card:hover .channel-card__image {
  transform: scale(1.015);
}

.channel-card__image.is-visible {
  opacity: 1;
}

.channel-card__body {
  display: grid;
  gap: 9px;
  padding: 12px 14px 14px;
}

.channel-card__topline,
.channel-card__actions,
.channel-card__tags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.channel-card__topline {
  justify-content: space-between;
  color: #9aa1ad;
  font-size: 12px;
}

.channel-card__channel {
  color: #ff5a45;
  font-weight: 760;
}

.channel-card__author {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.channel-card__author img {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
}

.channel-card__author strong,
.channel-card h3 {
  overflow: hidden;
  text-overflow: ellipsis;
}

.channel-card__author strong {
  color: #2a2f3b;
  font-size: 13px;
  white-space: nowrap;
}

.channel-card h3 {
  display: -webkit-box;
  margin: 0;
  color: #303744;
  font-size: 16px;
  line-height: 1.48;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.channel-card__desc {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: #5f6674;
  font-size: 13px;
  line-height: 1.55;
  word-break: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.channel-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
}

.channel-card__meta dt,
.channel-card__meta dd {
  margin: 0;
}

.channel-card__meta dt {
  color: #9aa1ad;
  font-size: 12px;
}

.channel-card__meta dd {
  max-width: 100%;
  padding: 2px 7px;
  border-radius: 999px;
  background: #f5f7fb;
  color: #4d5564;
  font-size: 12px;
  font-weight: 680;
}

.channel-card__tags span {
  color: #4f73d8;
  font-size: 12px;
}

.channel-card__actions {
  gap: 13px;
  color: #6f7582;
  font-size: 13px;
  font-weight: 700;
}

.channel-card__actions button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 26px;
  padding: 0;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
}

.channel-card__actions button.is-liked,
.channel-card__actions button:first-child {
  color: #ff5a45;
}

.channel-card__actions button:disabled {
  cursor: wait;
  opacity: 0.66;
}

.channel-card--photography .channel-card__body {
  padding-top: 14px;
}

.channel-card--photography .channel-card__image {
  min-height: 180px;
}

.channel-card--tech {
  border-color: #dfe5ee;
  background: linear-gradient(180deg, #ffffff 0%, #fbfcfe 100%);
}

.channel-card--tech .channel-card__body {
  gap: 11px;
}

.channel-card--tech h3 {
  font-size: 17px;
}
</style>
