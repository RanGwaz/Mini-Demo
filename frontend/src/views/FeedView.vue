<script setup lang="ts">
defineOptions({ name: 'FeedView' })

import { ElMessage } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import CommonLeftSidebar from '../components/CommonLeftSidebar.vue'
import { api, type FeedRequestAuthMode } from '../services/api'
import { HttpError } from '../services/http'
import { useAuthStore } from '../stores/auth'
import type { PostView } from '../types'
import {
  DEFAULT_IMAGE_PLACEHOLDER,
  getPostMediaUrl,
  hasPostMedia,
  normalizeMediaUrl,
} from '../utils/postMedia'
import { formatRelativeTimeZh } from '../utils/relativeTime'

const PAGE_SIZE = 30
const MAX_PAGE_SIZE = 40
const MIN_COLUMN_WIDTH = 232
const COLUMN_GAP = 14
const LOAD_MORE_ROOT_MARGIN = '1200px 0px'
const SKELETON_RATIOS = ['3 / 4.2', '3 / 3.5', '3 / 4.8', '3 / 3.9', '3 / 4.5', '3 / 3.3']
const FALLBACK_RATIOS = ['3 / 4.2', '3 / 3.6', '3 / 4.8', '3 / 3.9', '3 / 4.5', '3 / 3.35', '3 / 5']

const router = useRouter()
const authStore = useAuthStore()

authStore.hydrate()

const shellRef = ref<HTMLElement | null>(null)
const sentinelRef = ref<HTMLElement | null>(null)
const posts = ref<PostView[]>([])
const nextPage = ref(1)
const total = ref<number | null>(null)
const loadingInitial = ref(false)
const loadingMore = ref(false)
const feedError = ref('')
const hasMore = ref(true)
const columnCount = ref(5)
const feedSeed = ref(createFeedSeed())
const feedAuthMode = ref<FeedRequestAuthMode>(authStore.currentUser ? 'session' : 'guest')
const likedPostIds = ref<Set<number>>(new Set())
const likingPostIds = ref<Set<number>>(new Set())
const likeCountOverrides = ref<Record<number, number>>({})

let intersectionObserver: IntersectionObserver | null = null
let resizeObserver: ResizeObserver | null = null
let requestSerial = 0

const showInitialSkeleton = computed(() => loadingInitial.value && posts.value.length === 0)
const masonryColumns = computed(() => distributeIntoColumns(posts.value, columnCount.value))
const skeletonColumns = computed(() => buildSkeletonColumns(columnCount.value, 3))
const hasReachedEnd = computed(() => !loadingInitial.value && !loadingMore.value && posts.value.length > 0 && !hasMore.value)

function createFeedSeed() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `feed-${Date.now()}-${Math.round(Math.random() * 1_000_000)}`
}

function updateColumnCount() {
  const width = shellRef.value?.clientWidth || Math.max(320, window.innerWidth - 112)
  const next = Math.floor((width + COLUMN_GAP) / (MIN_COLUMN_WIDTH + COLUMN_GAP))
  columnCount.value = Math.max(1, Math.min(7, next || 1))
}

function distributeIntoColumns(items: PostView[], count: number) {
  const safeCount = Math.max(1, count)
  const columns = Array.from({ length: safeCount }, () => ({ height: 0, records: [] as PostView[] }))
  for (const post of items) {
    const target = columns.reduce((shortest, column) => (column.height < shortest.height ? column : shortest), columns[0])
    target.records.push(post)
    target.height += estimatedCardUnits(post)
  }
  return columns.map((column) => column.records)
}

function buildSkeletonColumns(count: number, rows: number) {
  return Array.from({ length: Math.max(1, count) }, (_, columnIndex) => (
    Array.from({ length: rows }, (_, rowIndex) => SKELETON_RATIOS[(columnIndex + rowIndex) % SKELETON_RATIOS.length])
  ))
}

function estimatedCardUnits(post: PostView) {
  const ratio = ratioNumber(postAspectRatio(post))
  const bodyUnits = post.title ? 0.36 : 0.18
  return (1 / ratio) + bodyUnits
}

function ratioNumber(raw: string) {
  const [w, h] = raw.split('/').map((item) => Number(item.trim()))
  if (!Number.isFinite(w) || !Number.isFinite(h) || w <= 0 || h <= 0) return 0.75
  return w / h
}

function postAspectRatio(post: PostView) {
  const measuredAsset = post.assets?.find((item) => Number(item.width) > 0 && Number(item.height) > 0)
    || post.images?.find((item) => Number(item.width) > 0 && Number(item.height) > 0)
  const width = Number(measuredAsset?.width || 0)
  const height = Number(measuredAsset?.height || 0)
  if (width > 0 && height > 0) {
    const mediaRatio = height / width
    if (mediaRatio >= 0.55 && mediaRatio <= 2.35) return `${width} / ${height}`
  }
  return FALLBACK_RATIOS[Math.abs(Number(post.id || 0)) % FALLBACK_RATIOS.length]
}

function postImage(post: PostView) {
  const url = getPostMediaUrl(post)
    || normalizeMediaUrl(post.thumbUrl)
    || normalizeMediaUrl(post.coverUrl)
  return url || DEFAULT_IMAGE_PLACEHOLDER
}

function authorAvatar(post: PostView) {
  return normalizeMediaUrl(post.author?.avatarUrl) || DEFAULT_IMAGE_PLACEHOLDER
}

function titleText(post: PostView) {
  return post.title?.trim() || '未命名内容'
}

function displayedLikeCount(post: PostView) {
  return likeCountOverrides.value[post.id] ?? post.likeCount ?? 0
}

function isPostLiked(postId: number) {
  return likedPostIds.value.has(postId)
}

function isPostLiking(postId: number) {
  return likingPostIds.value.has(postId)
}

function mergeUniquePosts(nextRecords: PostView[]) {
  if (nextRecords.length === 0) return
  const seen = new Set(posts.value.map((post) => post.id))
  const merged = [...posts.value]
  for (const post of nextRecords) {
    if (seen.has(post.id)) continue
    seen.add(post.id)
    merged.push(post)
  }
  posts.value = merged
}

async function loadFirstPage() {
  const serial = ++requestSerial
  loadingInitial.value = true
  loadingMore.value = false
  feedError.value = ''
  hasMore.value = true
  nextPage.value = 1
  total.value = null
  posts.value = []
  feedSeed.value = createFeedSeed()
  feedAuthMode.value = authStore.currentUser ? 'session' : 'guest'

  try {
    await loadFeedPage(1, serial)
  } finally {
    if (serial === requestSerial) loadingInitial.value = false
  }
}

async function loadMoreFeed() {
  if (loadingInitial.value || loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  const serial = requestSerial
  try {
    await loadFeedPage(nextPage.value, serial)
  } finally {
    if (serial === requestSerial) loadingMore.value = false
  }
}

async function loadFeedPage(page: number, serial: number) {
  try {
    const response = await api.homeFeed(page, Math.min(MAX_PAGE_SIZE, PAGE_SIZE), feedSeed.value, undefined, feedAuthMode.value)
    if (serial !== requestSerial) return
    const records = response.records || []
    mergeUniquePosts(records)
    total.value = typeof response.total === 'number' ? response.total : total.value
    nextPage.value = page + 1
    hasMore.value = records.length >= Math.min(MAX_PAGE_SIZE, PAGE_SIZE)
      && (total.value == null || posts.value.length < total.value)
    feedError.value = ''
  } catch (error) {
    if (serial !== requestSerial) return
    if (error instanceof HttpError && feedAuthMode.value === 'session' && (error.status === 401 || error.code === 'A002')) {
      feedAuthMode.value = 'guest'
      await loadFeedPage(page, serial)
      return
    }
    feedError.value = error instanceof Error ? error.message : '推荐流加载失败'
    hasMore.value = posts.value.length > 0
  }
}

async function refreshFeed() {
  await loadFirstPage()
  await nextTick()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function setupIntersectionObserver() {
  intersectionObserver?.disconnect()
  if (!sentinelRef.value) return
  intersectionObserver = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) {
      void loadMoreFeed()
    }
  }, {
    root: null,
    rootMargin: LOAD_MORE_ROOT_MARGIN,
    threshold: 0.01,
  })
  intersectionObserver.observe(sentinelRef.value)
}

function setupResizeObserver() {
  updateColumnCount()
  resizeObserver?.disconnect()
  if (shellRef.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(updateColumnCount)
    resizeObserver.observe(shellRef.value)
  } else {
    window.addEventListener('resize', updateColumnCount)
  }
}

function openPost(post: PostView, position: number) {
  void api.trackPostClick(post.id, { scene: 'home', position }).catch(() => undefined)
  if (authStore.currentUser) {
    void api.trackBehavior({
      postId: post.id,
      behaviorType: 'click',
      scene: 'home',
      position,
    }).catch(() => undefined)
  }
  void router.push(`/posts/${post.id}`)
}

async function toggleLike(post: PostView) {
  if (!authStore.currentUser) {
    authStore.openAuthPrompt('manual')
    return
  }
  if (likingPostIds.value.has(post.id)) return
  likingPostIds.value = new Set(likingPostIds.value).add(post.id)
  const wasLiked = likedPostIds.value.has(post.id)
  const nextLiked = new Set(likedPostIds.value)
  const nextCount = Math.max(0, displayedLikeCount(post) + (wasLiked ? -1 : 1))
  if (wasLiked) nextLiked.delete(post.id)
  else nextLiked.add(post.id)
  likedPostIds.value = nextLiked
  likeCountOverrides.value = { ...likeCountOverrides.value, [post.id]: nextCount }

  try {
    const result = await api.toggleLike(post.id)
    const syncedLiked = new Set(likedPostIds.value)
    if (result.active) syncedLiked.add(post.id)
    else syncedLiked.delete(post.id)
    likedPostIds.value = syncedLiked
  } catch {
    const rollback = new Set(likedPostIds.value)
    if (wasLiked) rollback.add(post.id)
    else rollback.delete(post.id)
    likedPostIds.value = rollback
    likeCountOverrides.value = { ...likeCountOverrides.value, [post.id]: post.likeCount ?? 0 }
    ElMessage.warning('点赞失败，稍后再试')
  } finally {
    const nextLiking = new Set(likingPostIds.value)
    nextLiking.delete(post.id)
    likingPostIds.value = nextLiking
  }
}

watch(() => authStore.currentUser?.id, () => {
  void loadFirstPage()
})

onMounted(async () => {
  setupResizeObserver()
  await loadFirstPage()
  await nextTick()
  setupIntersectionObserver()
})

onUnmounted(() => {
  intersectionObserver?.disconnect()
  resizeObserver?.disconnect()
  window.removeEventListener('resize', updateColumnCount)
})
</script>

<template>
  <div class="feed-home">
    <CommonLeftSidebar />

    <main ref="shellRef" class="feed-home__main">
      <section
        v-if="showInitialSkeleton"
        class="feed-home__waterfall"
        :style="{ '--feed-columns': columnCount }"
        aria-label="推荐内容加载中"
      >
        <div v-for="(column, columnIndex) in skeletonColumns" :key="`skeleton-column-${columnIndex}`" class="feed-home__column">
          <article
            v-for="(ratio, rowIndex) in column"
            :key="`skeleton-${columnIndex}-${rowIndex}`"
            class="feed-card feed-card--skeleton"
          >
            <div class="feed-card__media ui-skeleton" :style="{ aspectRatio: ratio }" />
            <div class="feed-card__body">
              <span class="feed-card__line ui-skeleton" />
              <span class="feed-card__line feed-card__line--short ui-skeleton" />
            </div>
          </article>
        </div>
      </section>

      <section
        v-else
        class="feed-home__waterfall"
        :style="{ '--feed-columns': columnCount }"
        aria-label="为你推荐"
      >
        <div v-for="(column, columnIndex) in masonryColumns" :key="`column-${columnIndex}`" class="feed-home__column">
          <article
            v-for="post in column"
            :key="post.id"
            class="feed-card"
            tabindex="0"
            @click="openPost(post, posts.findIndex((item) => item.id === post.id) + 1)"
            @keydown.enter="openPost(post, posts.findIndex((item) => item.id === post.id) + 1)"
          >
            <div class="feed-card__media" :class="{ 'has-placeholder': !hasPostMedia(post) }" :style="{ aspectRatio: postAspectRatio(post) }">
              <img
                :src="postImage(post)"
                :alt="titleText(post)"
                loading="lazy"
                decoding="async"
              />
            </div>

            <div class="feed-card__body">
              <h2>{{ titleText(post) }}</h2>
              <div class="feed-card__meta">
                <img :src="authorAvatar(post)" alt="" loading="lazy" decoding="async" />
                <span>{{ post.author?.nickname || post.author?.username || '用户' }}</span>
                <small>{{ formatRelativeTimeZh(post.createdAt) }}</small>
              </div>
              <div class="feed-card__actions">
                <button
                  type="button"
                  :class="{ 'is-active': isPostLiked(post.id) }"
                  :disabled="isPostLiking(post.id)"
                  @click.stop="toggleLike(post)"
                >
                  ♥ {{ displayedLikeCount(post) }}
                </button>
                <span>评 {{ post.commentCount || 0 }}</span>
                <span>藏 {{ post.collectCount ?? post.favoriteCount ?? 0 }}</span>
              </div>
            </div>
          </article>
        </div>
      </section>

      <div v-if="feedError" class="feed-home__state">
        <span>{{ feedError }}</span>
        <button type="button" @click="refreshFeed">重新加载</button>
      </div>

      <div ref="sentinelRef" class="feed-home__sentinel" />

      <div v-if="loadingMore" class="feed-home__loading">
        <span />
        <span />
        <span />
      </div>
      <p v-else-if="hasReachedEnd" class="feed-home__ending">已经到底了</p>
      <p v-else-if="!loadingInitial && posts.length === 0 && !feedError" class="feed-home__ending">暂无推荐内容</p>
    </main>
  </div>
</template>

<style scoped>
.feed-home {
  min-height: calc(100vh - 74px);
  padding: 14px 18px 72px 112px;
  background:
    radial-gradient(circle at 18% 0%, rgba(255, 122, 87, 0.08), transparent 30%),
    linear-gradient(180deg, #fbfbfc 0%, #f4f6f9 100%);
}

.feed-home__main {
  width: 100%;
  min-width: 0;
}

.feed-home__waterfall {
  display: grid;
  grid-template-columns: repeat(var(--feed-columns), minmax(0, 1fr));
  gap: 14px;
  align-items: start;
}

.feed-home__column {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.feed-card {
  overflow: hidden;
  border: 1px solid rgba(31, 41, 55, 0.08);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.98);
  cursor: pointer;
  box-shadow: 0 12px 30px rgba(31, 41, 55, 0.07);
  transition: transform 0.16s ease, box-shadow 0.16s ease, border-color 0.16s ease;
}

.feed-card:hover,
.feed-card:focus-visible {
  transform: translateY(-2px);
  border-color: rgba(255, 90, 69, 0.28);
  box-shadow: 0 18px 38px rgba(31, 41, 55, 0.12);
  outline: none;
}

.feed-card__media {
  display: grid;
  overflow: hidden;
  min-height: 136px;
  background: #eceff3;
}

.feed-card__media.has-placeholder {
  background:
    linear-gradient(135deg, rgba(255, 90, 69, 0.10), rgba(17, 24, 39, 0.06)),
    #eef1f5;
}

.feed-card__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.32s ease;
}

.feed-card:hover .feed-card__media img {
  transform: scale(1.018);
}

.feed-card__body {
  display: grid;
  gap: 8px;
  padding: 11px 12px 12px;
}

.feed-card__body h2 {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: #20242f;
  font-size: 15px;
  font-weight: 800;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.feed-card__meta {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: 7px;
  color: #8b93a2;
  font-size: 12px;
}

.feed-card__meta img {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
  background: #eef1f5;
}

.feed-card__meta span {
  overflow: hidden;
  color: #3d4452;
  font-weight: 720;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed-card__meta small {
  color: #9aa1ad;
  font-size: 12px;
  white-space: nowrap;
}

.feed-card__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #757d8d;
  font-size: 12px;
  font-weight: 720;
}

.feed-card__actions button {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
}

.feed-card__actions button.is-active,
.feed-card__actions button:hover {
  color: #ff5a45;
}

.feed-card__actions button:disabled {
  cursor: wait;
  opacity: 0.65;
}

.feed-card--skeleton {
  cursor: default;
}

.feed-card--skeleton:hover {
  transform: none;
  border-color: rgba(31, 41, 55, 0.08);
  box-shadow: 0 12px 30px rgba(31, 41, 55, 0.07);
}

.feed-card__line {
  display: block;
  width: 86%;
  height: 14px;
  border-radius: 999px;
}

.feed-card__line--short {
  width: 56%;
}

.feed-home__sentinel {
  height: 1px;
}

.feed-home__state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
  color: #7b8495;
  font-size: 13px;
}

.feed-home__state button {
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: 999px;
  background: #111827;
  color: #fff;
  cursor: pointer;
  font-weight: 760;
}

.feed-home__loading {
  display: flex;
  justify-content: center;
  gap: 7px;
  padding: 28px 0 4px;
}

.feed-home__loading span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #ff5a45;
  animation: feed-dot-bounce 0.9s ease-in-out infinite both;
}

.feed-home__loading span:nth-child(2) {
  animation-delay: 0.15s;
}

.feed-home__loading span:nth-child(3) {
  animation-delay: 0.3s;
}

.feed-home__ending {
  margin: 0;
  padding: 24px 0 2px;
  text-align: center;
  color: #9aa1ad;
  font-size: 12px;
}

@keyframes feed-dot-bounce {
  0%,
  80%,
  100% {
    transform: scale(0.6);
    opacity: 0.35;
  }

  40% {
    transform: scale(1);
    opacity: 1;
  }
}

@media (max-width: 780px) {
  .feed-home {
    padding: 10px 10px 72px;
  }
}
</style>
