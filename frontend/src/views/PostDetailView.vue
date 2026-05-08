<script setup lang="ts">
defineOptions({ name: 'PostDetailView' })

import {
  ArrowLeft,
  ArrowRight,
  ChatLineRound,
  Loading,
  MoreFilled,
  Promotion,
  RefreshRight,
  Share,
  Star,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonLeftSidebar from '../components/CommonLeftSidebar.vue'
import PostDetailRenderer from '../components/detail/PostDetailRenderer.vue'
import { api, type FeedRequestAuthMode } from '../services/api'
import { HttpError } from '../services/http'
import { useAuthStore } from '../stores/auth'
import type { CommentView, PostView } from '../types'
import {
  DEFAULT_IMAGE_PLACEHOLDER,
  getPostFullMediaUrl,
  getPostMediaAssets,
  getPostMediaCandidates,
  getPostMediaUrl,
  hasPostMedia,
  normalizeMediaUrl,
} from '../utils/postMedia'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
authStore.hydrate()

const detailLoading = ref(false)
const detailError = ref('')
const post = ref<PostView | null>(null)
const liked = ref(false)
const favorited = ref(false)
const followingAuthor = ref(false)
const activeAssetIndex = ref(0)

const comments = ref<CommentView[]>([])
const commentsLoading = ref(false)
const commentsPage = ref(1)
const commentsHasMore = ref(true)
const commentDraft = ref('')
const lightboxOpen = ref(false)

const relatedPosts = ref<PostView[]>([])
const relatedLoading = ref(false)
const relatedError = ref('')
const relatedPage = ref(1)
const relatedHasMore = ref(true)
const relatedSentinelRef = ref<HTMLElement | null>(null)
const relatedColumnCount = ref(4)
const mediaRef = ref<HTMLElement | null>(null)
const metaMaxHeight = ref<number | null>(null)

const COMMENT_PAGE_SIZE = 12
const RELATED_PAGE_SIZE = 18
const coverFallbackMap = ref<Record<number, string>>({})

let requestSerial = 0
let relatedObserver: IntersectionObserver | null = null

const postId = computed(() => Number(route.params.id || 0))
const authMode = computed<FeedRequestAuthMode>(() => (authStore.currentUser ? 'session' : 'guest'))
const canFollow = computed(() => Boolean(post.value && authStore.currentUser && authStore.currentUser.id !== post.value.author.id))
const authorAvatar = computed(() => normalizeMediaUrl(post.value?.author.avatarUrl) || DEFAULT_IMAGE_PLACEHOLDER)

const postAssets = computed(() => getPostMediaAssets(post.value))
const hasDetailMedia = computed(() => postAssets.value.length > 0)

const activeCoverUrl = computed(() => {
  const current = post.value
  if (!current || !hasDetailMedia.value) return ''
  const fallback = coverFallbackMap.value[current.id]
  if (fallback) return fallback
  return getPostMediaUrl(current, Math.min(activeAssetIndex.value, postAssets.value.length - 1))
})

const lightboxImageUrl = computed(() => {
  if (!post.value || !hasDetailMedia.value) return ''
  return getPostFullMediaUrl(post.value, Math.min(activeAssetIndex.value, postAssets.value.length - 1)) || activeCoverUrl.value
})
const relatedMasonryColumns = computed(() => distributeRelatedIntoColumns(relatedPosts.value))

function formatCount(value?: number | null) {
  const n = Number(value || 0)
  if (n >= 10000) return `${(n / 10000).toFixed(n >= 100000 ? 0 : 1)}万`
  if (n >= 1000) return `${(n / 1000).toFixed(n >= 10000 ? 0 : 1)}k`
  return String(Math.max(0, n))
}

function formatRelativeTime(createdAt?: string) {
  return formatRelativeTimeZh(createdAt)
}

function resolveCover(postItem: PostView) {
  const fallback = coverFallbackMap.value[postItem.id]
  if (fallback) return fallback
  return getPostMediaUrl(postItem)
}

function onPostCoverError(postItem: PostView) {
  const candidates = getPostMediaCandidates(postItem)

  for (const candidate of candidates) {
    if (coverFallbackMap.value[postItem.id] !== candidate) {
      coverFallbackMap.value = { ...coverFallbackMap.value, [postItem.id]: candidate }
      return
    }
  }
}

function openLightbox() {
  if (!lightboxImageUrl.value) return
  lightboxOpen.value = true
}

function closeLightbox() {
  lightboxOpen.value = false
}

function backToFeed() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  void router.push('/feed')
}

function showPrevAsset() {
  const total = postAssets.value.length
  if (total <= 1) return
  activeAssetIndex.value = (activeAssetIndex.value - 1 + total) % total
  void nextTick(syncMetaHeight)
}

function showNextAsset() {
  const total = postAssets.value.length
  if (total <= 1) return
  activeAssetIndex.value = (activeAssetIndex.value + 1) % total
  void nextTick(syncMetaHeight)
}

function selectAsset(index: number) {
  activeAssetIndex.value = index
  void nextTick(syncMetaHeight)
}

function openAuthor() {
  if (!post.value) return
  void router.push(`/users/${post.value.author.id}`)
}

function computeRelatedColumnCount() {
  const width = window.innerWidth
  if (width >= 1880) return 5
  if (width >= 1480) return 4
  if (width >= 1120) return 3
  if (width >= 760) return 2
  return 1
}

function updateRelatedColumnCount() {
  relatedColumnCount.value = computeRelatedColumnCount()
}

function syncMetaHeight() {
  if (typeof window === 'undefined' || !hasDetailMedia.value || window.innerWidth <= 1400) {
    metaMaxHeight.value = null
    return
  }

  const mediaElement = mediaRef.value
  if (!mediaElement) return

  const height = Math.floor(mediaElement.getBoundingClientRect().height)
  if (height > 0) metaMaxHeight.value = height
}

function handleWindowResize() {
  updateRelatedColumnCount()
  syncMetaHeight()
}

function relatedFallbackRatio(postItemId: number) {
  const ratios = ['3 / 4.8', '3 / 3.9', '3 / 4.4', '3 / 3.6', '3 / 4.2', '3 / 5.1']
  return ratios[postItemId % ratios.length]
}

function relatedCoverAspectRatio(postItem: PostView) {
  const asset = postItem.assets?.[0]
  const width = Number(asset?.width || 0)
  const height = Number(asset?.height || 0)
  return width > 0 && height > 0 ? `${width} / ${height}` : relatedFallbackRatio(postItem.id)
}

function relatedEstimatedHeight(postItem: PostView) {
  if (!hasPostMedia(postItem)) return 0.82
  const asset = postItem.assets?.[0]
  const width = Number(asset?.width || 0)
  const height = Number(asset?.height || 0)
  const mediaRatio = width > 0 && height > 0
    ? height / width
    : (() => {
        const [w, h] = relatedFallbackRatio(postItem.id).split('/').map((value) => Number(value.trim()))
        return (h || 4) / (w || 3)
      })()
  const textWeight = Math.min((postItem.title?.length || 0) / 48 + (postItem.content?.length || 0) / 180, 0.95)
  return mediaRatio + 0.62 + textWeight
}

function distributeRelatedIntoColumns(items: PostView[]) {
  const count = Math.max(1, relatedColumnCount.value)
  const columns: PostView[][] = Array.from({ length: count }, () => [])
  const heights: number[] = Array.from({ length: count }, () => 0)
  for (const item of items) {
    let minIndex = 0
    for (let i = 1; i < count; i++) {
      if (heights[i] < heights[minIndex]) minIndex = i
    }
    columns[minIndex].push(item)
    heights[minIndex] += relatedEstimatedHeight(item)
  }
  return columns
}

function resetCommentState() {
  comments.value = []
  commentsPage.value = 1
  commentsHasMore.value = true
}

function resetRelatedState() {
  relatedPosts.value = []
  relatedPage.value = 1
  relatedHasMore.value = true
  relatedError.value = ''
}

async function loadMoreComments() {
  if (!post.value || commentsLoading.value || !commentsHasMore.value) return
  commentsLoading.value = true
  try {
    const page = commentsPage.value
    const response = await api.commentsPage(post.value.id, page, COMMENT_PAGE_SIZE)
    const known = new Set(comments.value.map((item) => item.id))
    const incoming = (response.records || []).filter((item) => !known.has(item.id))
    comments.value = [...comments.value, ...incoming]
    commentsPage.value = page + 1
    commentsHasMore.value = comments.value.length < response.total && incoming.length > 0
  } catch {
    commentsHasMore.value = false
  } finally {
    commentsLoading.value = false
  }
}

async function loadMoreRelated() {
  if (!post.value || relatedLoading.value || !relatedHasMore.value) return
  relatedLoading.value = true
  relatedError.value = ''
  try {
    const page = relatedPage.value
    const response = await api.similarPosts(post.value.id, page, RELATED_PAGE_SIZE, undefined, authMode.value)
    const seen = new Set(relatedPosts.value.map((item) => item.id))
    seen.add(post.value.id)
    const incoming = (response.records || []).filter((item) => !seen.has(item.id))
    relatedPosts.value = [...relatedPosts.value, ...incoming]
    relatedPage.value = page + 1
    relatedHasMore.value = relatedPosts.value.length < response.total && incoming.length > 0
  } catch (error) {
    relatedError.value = error instanceof Error ? error.message : '加载更多内容失败'
  } finally {
    relatedLoading.value = false
  }
}

async function ensureInteractionStatus(targetId: number) {
  if (!authStore.accessToken) {
    liked.value = false
    favorited.value = false
    return
  }
  try {
    const status = await api.interactionStatus(targetId)
    liked.value = status.liked
    favorited.value = status.favorited
  } catch {
    liked.value = false
    favorited.value = false
  }
}

async function ensureFollowStatus() {
  if (!post.value || !authStore.accessToken || !canFollow.value) {
    followingAuthor.value = false
    return
  }
  try {
    const status = await api.followStatus(post.value.author.id)
    followingAuthor.value = status.following
  } catch {
    followingAuthor.value = false
  }
}

async function toggleLike() {
  if (!post.value) return
  if (!authStore.accessToken) {
    authStore.openAuthPrompt('manual')
    return
  }
  try {
    const result = await api.toggleLike(post.value.id)
    liked.value = result.active
    post.value = {
      ...post.value,
      likeCount: Math.max(0, post.value.likeCount + (result.active ? 1 : -1)),
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '点赞失败')
  }
}

async function toggleFavorite() {
  if (!post.value) return
  if (!authStore.accessToken) {
    authStore.openAuthPrompt('manual')
    return
  }
  try {
    const result = await api.toggleFavorite(post.value.id)
    favorited.value = result.active
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '收藏失败')
  }
}

async function toggleFollow() {
  if (!post.value) return
  if (!authStore.accessToken) {
    authStore.openAuthPrompt('manual')
    return
  }
  try {
    if (followingAuthor.value) {
      await api.unfollow(post.value.author.id, 'detail')
      followingAuthor.value = false
    } else {
      await api.follow(post.value.author.id, 'detail')
      followingAuthor.value = true
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '关注操作失败')
  }
}

async function submitComment() {
  if (!post.value) return
  const currentPostId = post.value.id
  const content = commentDraft.value.trim()
  if (!content) return
  if (!authStore.accessToken) {
    authStore.openAuthPrompt('manual')
    return
  }
  try {
    const created = await api.comment(currentPostId, content)
    comments.value = [created, ...comments.value.filter((item) => item.id !== created.id)]
    commentDraft.value = ''
    if (post.value) post.value.commentCount += 1
    await nextTick()
    commentsListRef.value?.scrollTo({ top: 0, behavior: 'smooth' })
    void refreshCommentsAfterSubmit(currentPostId)
    ElMessage.success('评论成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '评论失败')
  }
}

async function refreshCommentsAfterSubmit(postId: number) {
  try {
    const response = await api.commentsPage(postId, 1, COMMENT_PAGE_SIZE)
    comments.value = response.records || []
    commentsPage.value = 2
    commentsHasMore.value = comments.value.length < response.total
  } catch {
    // optimistic list already updated in submitComment; ignore refresh failures
  }
}

async function trackShare() {
  if (!post.value) return
  try {
    await api.trackPostShare(post.value.id, 'detail')
    ElMessage.success('已记录分享')
  } catch {
    ElMessage.success('分享成功')
  }
}

function openRelatedPost(targetId: number) {
  if (targetId === postId.value) return
  void router.push(`/posts/${targetId}`)
}

function setupRelatedObserver() {
  relatedObserver?.disconnect()
  const target = relatedSentinelRef.value
  if (!target) return
  relatedObserver = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) {
      void loadMoreRelated()
    }
  }, { rootMargin: '1200px 0px 1200px 0px' })
  relatedObserver.observe(target)
}

async function loadDetail(targetId: number) {
  if (!Number.isFinite(targetId) || targetId <= 0) {
    detailError.value = '帖子不存在'
    post.value = null
    detailLoading.value = false
    return
  }
  detailLoading.value = true
  detailError.value = ''
  post.value = null
  activeAssetIndex.value = 0
  lightboxOpen.value = false
  resetCommentState()
  resetRelatedState()
  const requestId = ++requestSerial
  try {
    const detail = await api.postDetail(targetId, 'detail', authMode.value)
    if (requestId !== requestSerial) return
    post.value = detail
    await Promise.all([
      ensureInteractionStatus(targetId),
      ensureFollowStatus(),
      loadMoreComments(),
      loadMoreRelated(),
    ])
    await nextTick()
    syncMetaHeight()
    setupRelatedObserver()
  } catch (error) {
    if (requestId !== requestSerial) return
    detailError.value = error instanceof HttpError ? error.message : '详情加载失败'
  } finally {
    if (requestId === requestSerial) detailLoading.value = false
  }
}

watch(() => route.params.id, async (value) => {
  const id = Number(value)
  window.scrollTo({ top: 0, behavior: 'auto' })
  await loadDetail(id)
}, { immediate: true })

watch(() => authStore.accessToken, async () => {
  if (!post.value) return
  await Promise.all([ensureInteractionStatus(post.value.id), ensureFollowStatus()])
})

watch(() => hasDetailMedia.value, async () => {
  await nextTick()
  syncMetaHeight()
})

onMounted(() => {
  handleWindowResize()
  window.addEventListener('resize', handleWindowResize)
  setupRelatedObserver()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleWindowResize)
  relatedObserver?.disconnect()
  relatedObserver = null
})
</script>

<template>
  <div class="detail-page">
    <CommonLeftSidebar />

    <main class="detail-page__main">
      <section class="detail-page__focus">
        <button type="button" class="detail-page__back-btn" @click="backToFeed">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </button>

        <div v-if="detailLoading" class="detail-page__state">
          <el-icon class="is-loading"><Loading /></el-icon>
          正在加载详情...
        </div>

        <div v-else-if="detailError" class="detail-page__state detail-page__state--error">
          <span>{{ detailError }}</span>
          <button type="button" @click="loadDetail(postId)">重新加载</button>
        </div>

        <div v-else-if="post" class="detail-page__panel" :class="{ 'is-text-only': !hasDetailMedia }">
          <div v-if="hasDetailMedia" class="detail-page__media">
            <div class="detail-page__media-wrap">
              <img :src="activeCoverUrl" :alt="post.title || '作品图片'" @click="openLightbox" @error="onPostCoverError(post)" />
              <button type="button" class="detail-page__zoom-btn" @click="openLightbox">查看大图</button>
              <button v-if="postAssets.length > 1" type="button" class="detail-page__asset-arrow is-left" @click="showPrevAsset">
                <el-icon><ArrowLeft /></el-icon>
              </button>
              <button v-if="postAssets.length > 1" type="button" class="detail-page__asset-arrow is-right" @click="showNextAsset">
                <el-icon><ArrowRight /></el-icon>
              </button>
            </div>
            <div v-if="postAssets.length > 1" class="detail-page__asset-dots">
              <button
                v-for="(_, index) in postAssets"
                :key="index"
                type="button"
                :class="{ 'is-active': activeAssetIndex === index }"
                @click="selectAsset(index)"
              />
            </div>
          </div>

          <div class="detail-page__meta" :class="{ 'is-scrollable': isMetaScrollMode }" :style="metaScrollStyle">
            <header class="detail-page__author">
              <button type="button" class="detail-page__author-main" @click="openAuthor">
                <img :src="authorAvatar" :alt="post.author.nickname" />
                <span>
                  <strong>{{ post.author.nickname }}</strong>
                  <small>{{ formatRelativeTime(post.createdAt) }} · 作者</small>
                </span>
              </button>
              <div class="detail-page__author-actions">
                <button v-if="canFollow" type="button" :class="['detail-page__follow', { 'is-following': followingAuthor }]" @click="toggleFollow">
                  {{ followingAuthor ? '已关注' : '+ 关注' }}
                </button>
                <button type="button" class="detail-page__icon-btn"><el-icon><MoreFilled /></el-icon></button>
              </div>
            </header>

            <PostDetailRenderer :post="post" />

            <div class="detail-page__actions">
              <button type="button" :class="{ 'is-active': liked }" @click="toggleLike"><span>♡</span>{{ formatCount(post.likeCount) }}</button>
              <button type="button"><el-icon><ChatLineRound /></el-icon>{{ formatCount(post.commentCount) }}</button>
              <button type="button" @click="trackShare"><el-icon><Share /></el-icon>{{ formatCount(post.shareCount) }}</button>
              <button type="button" :class="{ 'is-active': favorited }" @click="toggleFavorite"><el-icon><Star /></el-icon>收藏</button>
            </div>

            <section class="detail-page__comments">
              <div class="detail-page__comments-head">
                <strong>评论 ({{ post.commentCount }})</strong>
                <button v-if="commentsHasMore" type="button" @click="loadMoreComments">
                  查看全部
                  <el-icon><ArrowRight /></el-icon>
                </button>
              </div>

              <div ref="commentsListRef" class="detail-page__comments-list">
                <article v-for="item in comments.slice(0, 6)" :key="item.id">
                  <img :src="normalizeMediaUrl(item.author.avatarUrl) || '/auto_picture.png'" :alt="item.author.nickname" />
                  <div>
                    <p><strong>{{ item.author.nickname }}</strong><span>{{ formatRelativeTime(item.createdAt) }}</span></p>
                    <p class="comment-content">{{ item.content }}</p>
                  </div>
                </article>
                <div v-if="commentsLoading" class="detail-page__sub-state">评论加载中...</div>
                <div v-else-if="comments.length === 0" class="detail-page__sub-state">还没有评论，来抢沙发吧</div>
              </div>

              <div class="detail-page__comment-editor">
                <el-input v-model="commentDraft" placeholder="说点什么..." maxlength="280" @keydown.enter.prevent="submitComment" />
                <button type="button" @click="submitComment"><el-icon><Promotion /></el-icon></button>
              </div>
            </section>
          </div>
        </div>

        <div v-if="lightboxOpen && lightboxImageUrl" class="detail-page__lightbox" @click.self="closeLightbox">
          <button type="button" class="detail-page__lightbox-close" aria-label="关闭大图" @click="closeLightbox">×</button>
          <button v-if="postAssets.length > 1" type="button" class="detail-page__lightbox-arrow is-left" aria-label="上一张" @click="showPrevAsset">
            <el-icon><ArrowLeft /></el-icon>
          </button>
          <img :src="lightboxImageUrl" :alt="post?.title || '作品图片'" />
          <button v-if="postAssets.length > 1" type="button" class="detail-page__lightbox-arrow is-right" aria-label="下一张" @click="showNextAsset">
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
      </section>

      <section class="detail-page__related">
        <header class="detail-page__related-head">
          <h2>继续浏览</h2>
          <button type="button" @click="loadMoreRelated">
            <el-icon><RefreshRight /></el-icon>
            换一换
          </button>
        </header>

        <div class="detail-page__related-waterfall" :style="{ '--column-count': String(relatedColumnCount) }">
          <div v-for="(column, columnIndex) in relatedMasonryColumns" :key="`related-col-${columnIndex}`" class="detail-page__related-column">
            <article
              v-for="item in column"
              :key="item.id"
              class="detail-page__related-card"
              :class="{ 'is-text-only': !hasPostMedia(item) }"
              @click="openRelatedPost(item.id)"
            >
              <div v-if="hasPostMedia(item)" class="detail-page__related-cover" :style="{ aspectRatio: relatedCoverAspectRatio(item) }">
                <img :src="resolveCover(item)" :alt="item.title || '帖子图片'" loading="lazy" @error="onPostCoverError(item)" />
              </div>
              <div class="detail-page__related-body">
                <header>
                  <img :src="normalizeMediaUrl(item.author.avatarUrl) || '/auto_picture.png'" :alt="item.author.nickname" loading="lazy" />
                  <span>
                    <strong>{{ item.author.nickname }}</strong>
                    <small>{{ formatRelativeTime(item.createdAt) }}</small>
                  </span>
                </header>
                <h3>{{ item.title || '未命名作品' }}</h3>
                <p v-if="item.content">{{ item.content }}</p>
                <div class="detail-page__related-actions">
                  <span>♡ {{ formatCount(item.likeCount) }}</span>
                  <span>💬 {{ formatCount(item.commentCount) }}</span>
                </div>
              </div>
            </article>
          </div>
        </div>

        <p v-if="relatedLoading" class="detail-page__related-state"><el-icon class="is-loading"><Loading /></el-icon> 正在加载更多相关内容...</p>
        <p v-if="relatedError" class="detail-page__related-state is-error">{{ relatedError }}</p>
        <p v-if="!relatedHasMore && relatedPosts.length > 0" class="detail-page__related-state">已经到底了</p>
        <div ref="relatedSentinelRef" class="detail-page__sentinel" />
      </section>
    </main>
  </div>
</template>

<style scoped>
.detail-page {
  position: relative;
  width: 100%;
  min-height: calc(100vh - 74px);
  padding: 12px 14px 28px 244px;
  background: #f7f8fa;
}

.detail-page__main {
  min-width: 0;
}

.detail-page__focus,
.detail-page__related {
  border: 1px solid rgba(26, 31, 44, 0.07);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 30px rgba(32, 36, 47, 0.05);
}

.detail-page__focus {
  padding: 10px 14px 14px;
}

.detail-page__back-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  color: #555d6b;
  font-size: 16px;
  font-weight: 650;
  cursor: pointer;
}

.detail-page__state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 420px;
  color: #68707f;
  font-size: 15px;
}

.detail-page__state--error {
  flex-direction: column;
}

.detail-page__state--error button {
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: 8px;
  background: #20242f;
  color: #fff;
  cursor: pointer;
}

.detail-page__panel {
  display: grid;
  grid-template-columns: minmax(0, 52%) minmax(0, 48%);
  align-items: start;
  gap: 18px;
}

.detail-page__panel.is-text-only {
  grid-template-columns: minmax(0, 820px);
  justify-content: center;
}

.detail-page__panel.is-text-only .detail-page__meta {
  min-height: 520px;
  padding: 18px 10px 4px;
}

.detail-page__panel.is-text-only .detail-page__content {
  padding: 18px 0 10px;
}

.detail-page__panel.is-text-only .detail-page__content h1 {
  max-width: 780px;
  font-size: 34px;
  line-height: 1.28;
}

.detail-page__panel.is-text-only .detail-page__content p {
  max-width: 760px;
  color: #303846;
  font-size: 16px;
  line-height: 1.9;
}

.detail-page__media-wrap {
  position: relative;
  overflow: hidden;
  border-radius: 12px;
  border: 1px solid #e7ebf0;
  background: #f2f4f8;
}

.detail-page__media-wrap img {
  width: 100%;
  height: auto;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  cursor: zoom-in;
}

.detail-page__zoom-btn {
  position: absolute;
  right: 12px;
  bottom: 12px;
  height: 34px;
  padding: 0 13px;
  border: none;
  border-radius: 999px;
  background: rgba(20, 24, 32, 0.72);
  color: #fff;
  cursor: zoom-in;
  font-size: 13px;
  font-weight: 700;
  backdrop-filter: blur(10px);
}

.detail-page__asset-arrow {
  position: absolute;
  top: 50%;
  width: 42px;
  height: 42px;
  border: none;
  border-radius: 50%;
  background: rgba(35, 40, 48, 0.45);
  color: #fff;
  transform: translateY(-50%);
  cursor: pointer;
}

.detail-page__asset-arrow.is-left { left: 12px; }
.detail-page__asset-arrow.is-right { right: 12px; }

.detail-page__asset-dots {
  display: flex;
  justify-content: center;
  gap: 7px;
  padding-top: 9px;
}

.detail-page__asset-dots button {
  width: 7px;
  height: 7px;
  border: none;
  border-radius: 999px;
  background: #d0d5dd;
  cursor: pointer;
}

.detail-page__asset-dots button.is-active {
  width: 18px;
  background: #ff5a45;
}

.detail-page__lightbox {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: grid;
  place-items: center;
  padding: 42px;
  background: rgba(8, 10, 15, 0.88);
}

.detail-page__lightbox img {
  display: block;
  max-width: min(1180px, 92vw);
  max-height: 88vh;
  border-radius: 8px;
  object-fit: contain;
  box-shadow: 0 28px 80px rgba(0, 0, 0, 0.42);
}

.detail-page__lightbox-close,
.detail-page__lightbox-arrow {
  position: fixed;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  cursor: pointer;
  backdrop-filter: blur(12px);
}

.detail-page__lightbox-close {
  top: 24px;
  right: 28px;
  width: 42px;
  height: 42px;
  font-size: 26px;
}

.detail-page__lightbox-arrow {
  top: 50%;
  width: 46px;
  height: 46px;
  transform: translateY(-50%);
  font-size: 20px;
}

.detail-page__lightbox-arrow.is-left { left: 28px; }
.detail-page__lightbox-arrow.is-right { right: 28px; }

.detail-page__meta {
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  gap: 10px;
  min-height: 0;
}

.detail-page__meta.is-scrollable {
  overflow-y: auto;
  padding-right: 6px;
  scrollbar-gutter: stable;
}

.detail-page__meta.is-scrollable::-webkit-scrollbar {
  width: 8px;
}

.detail-page__meta.is-scrollable::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(132, 141, 156, 0.45);
}

.detail-page__author {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.detail-page__author-main {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 10px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.detail-page__author-main img {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
}

.detail-page__author-main span {
  min-width: 0;
  display: grid;
}

.detail-page__author-main strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 16px;
}

.detail-page__author-main small {
  color: #8c94a2;
  font-size: 13px;
}

.detail-page__author-actions {
  display: inline-flex;
  gap: 8px;
}

.detail-page__follow {
  height: 36px;
  padding: 0 16px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #ff6d52 0%, #ff4f3b 100%);
  color: #fff;
  font-size: 15px;
  font-weight: 760;
  box-shadow: 0 10px 22px rgba(255, 90, 69, 0.24);
  cursor: pointer;
}

.detail-page__follow.is-following {
  background: #fff0ed;
  color: #ff5a45;
  box-shadow: none;
}

.detail-page__icon-btn {
  width: 36px;
  border: none;
  border-radius: 10px;
  background: #f4f6f9;
  color: #7f8796;
  font-size: 18px;
  cursor: pointer;
}

.detail-page__content h1 {
  margin: 2px 0 6px;
  color: #1f2531;
  font-size: 33px;
  font-weight: 800;
  line-height: 1.35;
}

.detail-page__content p {
  margin: 0;
  color: #4d5564;
  font-size: 15px;
  line-height: 1.72;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-page__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.detail-page__tags span {
  color: #4f7ad8;
  font-size: 13px;
}

.detail-page__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #eceff4;
}

.detail-page__actions button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 32px;
  padding: 0 7px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #6f7582;
  font-size: 14px;
  cursor: pointer;
}

.detail-page__actions button.is-active {
  color: #ff5a45;
}

.detail-page__actions button span {
  font-size: 20px;
}

.detail-page__comments {
  min-height: 0;
}

.detail-page__comments-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.detail-page__comments-head strong {
  font-size: 15px;
}

.detail-page__comments-head button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: transparent;
  color: #7f8796;
  font-size: 13px;
  cursor: pointer;
}

.detail-page__comments-list {
  min-height: 72px;
  max-height: 220px;
  overflow-y: auto;
  margin-top: 8px;
  margin-bottom: 9px;
  padding-right: 3px;
}

.detail-page__comments-list article {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 8px;
  padding: 7px 0;
}

.detail-page__comments-list img {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  object-fit: cover;
}

.detail-page__comments-list p {
  margin: 0;
}

.detail-page__comments-list p:first-child {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-page__comments-list strong {
  font-size: 13px;
}

.detail-page__comments-list span {
  color: #939baa;
  font-size: 12px;
}

.comment-content {
  color: #424956;
  font-size: 13px;
  line-height: 1.5;
}

.detail-page__sub-state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 120px;
  color: #9098a7;
  font-size: 13px;
}

.detail-page__comment-editor {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 36px;
  gap: 8px;
}

.detail-page__comment-editor button {
  border: 1px solid #e5e8ef;
  border-radius: 8px;
  background: #fff;
  color: #727b8a;
  cursor: pointer;
}

.detail-page__related {
  margin-top: 10px;
  padding: 10px;
}

.detail-page__related-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.detail-page__related-head h2 {
  margin: 0;
  font-size: 26px;
}

.detail-page__related-head button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  color: #8b93a1;
  font-size: 13px;
  cursor: pointer;
}

.detail-page__related-waterfall {
  display: grid;
  grid-template-columns: repeat(var(--column-count), minmax(0, 1fr));
  gap: 10px;
  align-items: start;
}

.detail-page__related-column {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.detail-page__related-card {
  overflow: hidden;
  border: 1px solid #e8ebf0;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

.detail-page__related-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(20, 25, 38, 0.09);
}

.detail-page__related-card.is-text-only {
  background: linear-gradient(180deg, #ffffff 0%, #fbfcfe 100%);
}

.detail-page__related-card.is-text-only .detail-page__related-body {
  padding: 12px;
}

.detail-page__related-card.is-text-only h3 {
  -webkit-line-clamp: 3;
}

.detail-page__related-card.is-text-only p {
  -webkit-line-clamp: 5;
}

.detail-page__related-cover {
  overflow: hidden;
  background: #f1f3f7;
}

.detail-page__related-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail-page__related-body {
  padding: 7px 9px 9px;
}

.detail-page__related-body header {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  align-items: center;
  gap: 6px;
}

.detail-page__related-body header img {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}

.detail-page__related-body header span {
  min-width: 0;
  display: grid;
}

.detail-page__related-body header strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.detail-page__related-body header small {
  overflow: hidden;
  color: #959daa;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}

.detail-page__related-body h3 {
  display: -webkit-box;
  margin: 7px 0 0;
  overflow: hidden;
  color: #2e3441;
  font-size: 15px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.detail-page__related-body p {
  display: -webkit-box;
  margin: 4px 0 0;
  overflow: hidden;
  color: #646d7d;
  font-size: 13px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.detail-page__related-actions {
  display: flex;
  gap: 12px;
  margin-top: 7px;
  color: #6f7582;
  font-size: 13px;
}

.detail-page__related-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 10px 0 2px;
  color: #8a91a0;
  font-size: 13px;
}

.detail-page__related-state.is-error {
  color: #d94134;
}

.detail-page__sentinel {
  height: 1px;
}

@media (max-width: 1400px) {
  .detail-page {
    padding-left: 226px;
  }

  .detail-page__panel {
    grid-template-columns: minmax(0, 1fr);
  }

  .detail-page__comments-list {
    max-height: 300px;
  }
}

@media (max-width: 980px) {
  .detail-page {
    padding: 10px;
  }

  .detail-page__focus {
    padding: 8px;
  }

  .detail-page__content h1 {
    font-size: 22px;
  }

  .detail-page__content p {
    font-size: 14px;
  }

  .detail-page__panel.is-text-only .detail-page__content h1 {
    font-size: 24px;
  }

  .detail-page__panel.is-text-only .detail-page__content p {
    font-size: 15px;
  }

  .detail-page__lightbox {
    padding: 18px;
  }

  .detail-page__lightbox-arrow {
    width: 40px;
    height: 40px;
  }

  .detail-page__related-head h2 {
    font-size: 19px;
  }
}
</style>
