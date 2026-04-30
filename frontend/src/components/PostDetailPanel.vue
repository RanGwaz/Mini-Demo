<script setup lang="ts">
import { ArrowDown, ArrowUp, ChatDotRound, Pointer, Share, Star } from '@element-plus/icons-vue'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { CommentView, PostView } from '../types'

type DisplayAsset = {
  id?: number
  fileUrl?: string
  thumbUrl?: string
}

const props = defineProps<{
  post: PostView
  liked: boolean
  favorited: boolean
  following: boolean
  showFollow: boolean
  comments: CommentView[]
  commentsHasMore: boolean
  commentsLoading: boolean
  currentUserAvatar?: string | null
  showClose?: boolean
}>()

const commentText = defineModel<string>('commentText', { default: '' })

const emit = defineEmits<{
  close: []
  follow: []
  'toggle-like': []
  'toggle-favorite': []
  'submit-comment': []
  'load-more-comments': []
  'go-author': []
  share: []
}>()

const router = useRouter()
const coverReady = ref(false)
const activeAssetIndex = ref(0)
const coverCandidates = ref<string[]>(['/auto_picture.png'])
const coverCandidateIndex = ref(0)
const coverSrc = ref('/auto_picture.png')
const commentsExpanded = ref(false)

const displayAssets = computed<DisplayAsset[]>(() => {
  if (props.post.assets?.length) return props.post.assets
  return [{ id: props.post.id, fileUrl: props.post.coverUrl, thumbUrl: props.post.thumbUrl }]
})

const activeAsset = computed(() => displayAssets.value[Math.min(activeAssetIndex.value, displayAssets.value.length - 1)])
const authorAvatar = computed(() => normalizeMediaUrl(props.post.author.avatarUrl) || '/auto_picture.png')
const viewerAvatar = computed(() => normalizeMediaUrl(props.currentUserAvatar) || '/auto_picture.png')
const totalCommentCount = computed(() => Math.max(props.post.commentCount || 0, props.comments.length))

const hotComments = computed(() => {
  return [...props.comments]
    .map((item) => {
      const ageHours = Math.max(0, (Date.now() - new Date(item.createdAt).getTime()) / 3_600_000)
      const freshnessScore = Math.max(0, 48 - ageHours)
      const lengthScore = Math.min((item.content?.length || 0) / 8, 10)
      const replyScore = item.replyToUser ? 5 : 0
      return { item, score: freshnessScore + lengthScore + replyScore }
    })
    .sort((a, b) => b.score - a.score)
    .map((entry) => entry.item)
})

const previewComments = computed(() => hotComments.value.slice(0, 2))
const visibleComments = computed(() => (commentsExpanded.value ? props.comments : previewComments.value))
const canToggleComments = computed(() => totalCommentCount.value > 2 || props.commentsHasMore)

watch(
  () => props.post.id,
  () => {
    activeAssetIndex.value = 0
    commentsExpanded.value = false
    syncCoverState()
  },
  { immediate: true },
)

watch(activeAssetIndex, () => {
  syncCoverState()
})

function normalizeMediaUrl(url?: string | null) {
  if (!url) return ''
  return url.replace('http://localhost:9000', '/minio-img')
}

function handleAvatarError(event: Event) {
  const image = event.target instanceof HTMLImageElement ? event.target : null
  if (image) image.src = '/auto_picture.png'
}

function uniqueCandidates(candidates: Array<string | undefined | null>) {
  const result: string[] = []
  const seen = new Set<string>()
  for (const item of candidates) {
    const normalized = normalizeMediaUrl(item) || (item ? String(item) : '')
    if (!normalized || seen.has(normalized)) continue
    seen.add(normalized)
    result.push(normalized)
  }
  return result.length > 0 ? result : ['/auto_picture.png']
}

function syncCoverState() {
  coverReady.value = false
  coverCandidates.value = uniqueCandidates([
    activeAsset.value?.thumbUrl,
    props.post.thumbUrl,
    activeAsset.value?.fileUrl,
    props.post.coverUrl,
  ])
  coverCandidateIndex.value = 0
  coverSrc.value = coverCandidates.value[0]
}

function onCoverLoad() {
  coverReady.value = true
}

function onCoverError() {
  if (coverCandidateIndex.value < coverCandidates.value.length - 1) {
    coverCandidateIndex.value += 1
    coverSrc.value = coverCandidates.value[coverCandidateIndex.value]
    return
  }
  coverReady.value = true
  coverSrc.value = '/auto_picture.png'
}

function selectAsset(index: number) {
  activeAssetIndex.value = index
}

function toggleCommentsExpand() {
  commentsExpanded.value = !commentsExpanded.value
  if (commentsExpanded.value && props.comments.length < 6 && props.commentsHasMore && !props.commentsLoading) {
    emit('load-more-comments')
  }
}

function formatCommentTime(iso: string) {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const diff = Date.now() - date.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric' }).format(date)
}

function formatEngageCount(n: number) {
  if (n >= 10_000) {
    const w = n / 10_000
    return `${w >= 10 ? Math.round(w) : w.toFixed(1).replace(/\.0$/, '')}w`
  }
  if (n >= 1_000) {
    const k = n / 1_000
    return `${k.toFixed(1).replace(/\.0$/, '')}k`
  }
  return String(n ?? 0)
}

function formatPublishTime(iso: string) {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function goAuthor() {
  emit('go-author')
  void router.push(`/users/${props.post.author.id}`)
}
</script>

<template>
  <article class="detail-focus-card stage-surface">
    <div class="detail-focus-card__media">
      <button v-if="showClose" type="button" class="detail-focus-card__back" aria-label="返回"
        @click="emit('close')">←</button>

      <div class="detail-focus-card__media-shell">
        <div class="detail-focus-card__shimmer" :class="{ 'is-loaded': coverReady }" aria-hidden="true" />
        <img class="detail-focus-card__image" fetchpriority="high" loading="eager" decoding="async" :src="coverSrc"
          alt="post image" @load="onCoverLoad" @error="onCoverError" />
      </div>

      <div v-if="displayAssets.length > 1" class="detail-focus-card__thumbs">
        <button v-for="(asset, index) in displayAssets" :key="asset.id ?? index" type="button"
          class="detail-focus-card__thumb" :class="{ 'is-active': index === activeAssetIndex }"
          @click="selectAsset(index)">
          <img :src="normalizeMediaUrl(asset.thumbUrl || asset.fileUrl) || '/auto_picture.png'" alt="" loading="lazy"
            decoding="async" />
        </button>
      </div>
    </div>

    <div class="detail-focus-card__panel">
      <header class="detail-focus-card__author">
        <button type="button" class="detail-focus-card__author-main" @click="goAuthor">
          <img class="detail-focus-card__avatar" :src="authorAvatar" alt="" loading="lazy" decoding="async"
            @error="handleAvatarError" />
          <span class="detail-focus-card__author-copy">
            <strong>{{ post.author.nickname }}</strong>
            <small>@{{ post.author.username }}</small>
          </span>
        </button>

        <button v-if="showFollow" type="button" class="detail-focus-card__follow" :class="{ 'is-following': following }"
          @click="emit('follow')">
          {{ following ? '已关注' : '关注' }}
        </button>
      </header>

      <section class="detail-focus-card__story">
        <h2 class="detail-focus-card__title">{{ post.title || '未命名作品' }}</h2>
        <p v-if="post.content" class="detail-focus-card__body">{{ post.content }}</p>

        <div class="detail-focus-card__meta-line">
          <span>{{ formatPublishTime(post.createdAt) }}</span>
          <span>浏览 {{ formatEngageCount(post.viewCount || 0) }}</span>
        </div>
      </section>

      <div class="detail-focus-card__actions">
        <button type="button" class="detail-focus-card__action" :class="{ 'is-active': liked }"
          @click="emit('toggle-like')">
          <el-icon>
            <Pointer />
          </el-icon>
          <span>{{ formatEngageCount(post.likeCount) }}</span>
        </button>
        <button type="button" class="detail-focus-card__action" :class="{ 'is-active': favorited }"
          @click="emit('toggle-favorite')">
          <el-icon>
            <Star />
          </el-icon>
          <span>{{ formatEngageCount(post.favoriteCount) }}</span>
        </button>
        <button type="button" class="detail-focus-card__action" @click="toggleCommentsExpand">
          <el-icon>
            <ChatDotRound />
          </el-icon>
          <span>{{ formatEngageCount(totalCommentCount) }}</span>
        </button>
        <button type="button" class="detail-focus-card__action" @click="emit('share')">
          <el-icon>
            <Share />
          </el-icon>
        </button>
      </div>

      <div class="detail-focus-card__composer">
        <img class="detail-focus-card__composer-avatar" :src="viewerAvatar" alt="" loading="lazy" decoding="async"
          @error="handleAvatarError" />
        <el-input v-model="commentText" class="detail-focus-card__composer-input" placeholder="写下你的评论..."
          @keydown.enter.prevent="emit('submit-comment')" />
        <button type="button" class="detail-focus-card__composer-send" @click="emit('submit-comment')">发布</button>
      </div>

      <section class="detail-focus-card__comments">
        <div class="detail-focus-card__comments-head">
          <strong>{{ totalCommentCount }} 条评论</strong>
          <button v-if="canToggleComments" type="button" class="detail-focus-card__comments-toggle"
            :aria-expanded="commentsExpanded" @click="toggleCommentsExpand">
            <el-icon v-if="commentsExpanded">
              <ArrowUp />
            </el-icon>
            <el-icon v-else>
              <ArrowDown />
            </el-icon>
          </button>
        </div>

        <div class="detail-focus-card__comments-scroll" :class="{ 'is-expanded': commentsExpanded }">
          <ul v-if="visibleComments.length > 0" class="detail-focus-card__comments-list">
            <li v-for="item in visibleComments" :key="item.id" class="detail-focus-card__comment">
              <img class="detail-focus-card__comment-avatar"
                :src="normalizeMediaUrl(item.author.avatarUrl) || '/auto_picture.png'" alt="" loading="lazy"
                decoding="async" @error="handleAvatarError" />
              <div class="detail-focus-card__comment-main">
                <div class="detail-focus-card__comment-head">
                  <strong>{{ item.author.nickname }}</strong>
                  <span>{{ formatCommentTime(item.createdAt) }}</span>
                </div>
                <p class="detail-focus-card__comment-text">
                  <template v-if="item.replyToUser">回复 {{ item.replyToUser.nickname }}: </template>{{ item.content }}
                </p>
              </div>
            </li>
          </ul>

          <div v-else class="detail-focus-card__comments-empty">还没有评论，来留下第一条吧</div>

          <button v-if="commentsExpanded && commentsHasMore" type="button" class="detail-focus-card__comments-more"
            :disabled="commentsLoading" @click="emit('load-more-comments')">
            {{ commentsLoading ? '加载中...' : '加载更多评论' }}
          </button>
        </div>
      </section>
    </div>
  </article>
</template>

<style scoped>
.detail-focus-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 16px;
}

.stage-surface {
  border-radius: 16px;
  border: 1px solid #dedede;
  background: #fff;
  box-shadow: none;
}

.detail-focus-card__media {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-focus-card__back {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 3;
  border: none;
  border-radius: 999px;
  width: 42px;
  height: 42px;
  padding: 0;
  background: rgba(255, 255, 255, 0.92);
  color: #111;
  font-size: 28px;
  font-weight: 500;
  cursor: pointer;
}

.detail-focus-card__media-shell {
  position: relative;
  overflow: hidden;
  border-radius: 14px;
  min-height: 420px;
  background: #f1f1f1;
}

.detail-focus-card__shimmer {
  position: absolute;
  inset: 0;
  background: linear-gradient(110deg, rgba(255, 255, 255, 0.24) 30%, rgba(255, 255, 255, 0.55) 50%, rgba(255, 255, 255, 0.2) 70%);
  background-size: 200% 100%;
  animation: detail-card-shimmer var(--motion-duration-slow, 1.4s) linear infinite;
  transition: opacity var(--motion-duration-base, 0.24s) var(--motion-ease-standard, ease);
}

.detail-focus-card__shimmer.is-loaded {
  opacity: 0;
}

.detail-focus-card__image {
  position: relative;
  z-index: 1;
  display: block;
  width: 100%;
  max-height: min(66vh, 780px);
  object-fit: contain;
}

.detail-focus-card__thumbs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.detail-focus-card__thumb {
  width: 62px;
  height: 62px;
  padding: 0;
  overflow: hidden;
  border-radius: 14px;
  border: 2px solid transparent;
  background: transparent;
  cursor: pointer;
}

.detail-focus-card__thumb.is-active {
  border-color: var(--brand-accent, #ee4d2d);
}

.detail-focus-card__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail-focus-card__panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.detail-focus-card__author,
.detail-focus-card__comments-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.detail-focus-card__author-main {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.detail-focus-card__avatar,
.detail-focus-card__composer-avatar,
.detail-focus-card__comment-avatar {
  width: 38px;
  height: 38px;
  border-radius: 14px;
  object-fit: cover;
}

.detail-focus-card__author-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-focus-card__author-copy strong,
.detail-focus-card__comment-head strong {
  color: var(--text-primary, #0f172a);
  font-size: 14px;
}

.detail-focus-card__author-copy small,
.detail-focus-card__comment-head span {
  color: var(--text-muted, #64748b);
  font-size: 12px;
}

.detail-focus-card__story {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 2px 0 2px;
}

.detail-focus-card__title {
  margin: 0;
  font-size: clamp(20px, 2.2vw, 28px);
  line-height: 1.16;
  letter-spacing: -0.02em;
  color: var(--text-primary, #0f172a);
}

.detail-focus-card__body {
  margin: 0;
  color: var(--text-secondary, #334155);
  line-height: 1.72;
  font-size: 14px;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-focus-card__meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  color: var(--text-muted, #64748b);
  font-size: 12px;
}

.detail-focus-card__follow {
  border: none;
  border-radius: 999px;
  padding: 8px 14px;
  background: linear-gradient(135deg, #ee4d2d, #ff8458);
  color: #fff;
  font-weight: 600;
  cursor: pointer;
}

.detail-focus-card__follow.is-following {
  background: var(--bg-muted, #f8fafc);
  color: var(--text-secondary, #334155);
  box-shadow: inset 0 0 0 1px var(--border-subtle, rgba(15, 23, 42, 0.08));
}

.detail-focus-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-focus-card__action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: none;
  border-radius: 999px;
  background: color-mix(in srgb, var(--bg-muted, #f8fafc) 90%, transparent);
  color: var(--text-secondary, #334155);
  cursor: pointer;
  font-weight: 600;
  transition: transform var(--motion-duration-fast, 0.16s) var(--motion-ease-standard, ease);
}

.detail-focus-card__action:hover {
  transform: translateY(-1px);
}

.detail-focus-card__action.is-active {
  background: rgba(238, 77, 45, 0.12);
  color: #d94823;
}

.detail-focus-card__composer {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.detail-focus-card__composer-input {
  min-width: 0;
}

.detail-focus-card__composer-send {
  border: none;
  border-radius: 999px;
  padding: 8px 13px;
  background: #0f172a;
  color: #fff;
  cursor: pointer;
}

.detail-focus-card__comments {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-focus-card__comments-toggle {
  width: 30px;
  height: 30px;
  border: 1px solid var(--border-subtle);
  border-radius: 999px;
  background: var(--bg-muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.detail-focus-card__comments-scroll {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-focus-card__comments-scroll.is-expanded {
  max-height: 310px;
  overflow-y: auto;
  padding-right: 4px;
  border-radius: 12px;
}

.detail-focus-card__comments-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.detail-focus-card__comment {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid var(--border-subtle, rgba(15, 23, 42, 0.08));
}

.detail-focus-card__comment-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-focus-card__comment-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.detail-focus-card__comment-text {
  margin: 0;
  color: var(--text-secondary, #334155);
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}

.detail-focus-card__comments-empty {
  padding: 12px 0 4px;
  color: var(--text-muted, #64748b);
  font-size: 12px;
}

.detail-focus-card__comments-more {
  width: 100%;
  border: none;
  border-radius: 10px;
  padding: 10px 12px;
  background: var(--bg-muted);
  color: var(--text-secondary);
  cursor: pointer;
}

@keyframes detail-card-shimmer {
  0% {
    background-position: 200% 0;
  }

  100% {
    background-position: -200% 0;
  }
}

@media (max-width: 900px) {
  .detail-focus-card {
    padding: 12px;
    border-radius: 20px;
  }

  .detail-focus-card__media-shell {
    min-height: 260px;
  }

  .detail-focus-card__image {
    max-height: 56vh;
  }

  .detail-focus-card__composer {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .detail-focus-card__composer-avatar {
    display: none;
  }
}
</style>
