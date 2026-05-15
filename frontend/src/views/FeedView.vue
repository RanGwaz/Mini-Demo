<script setup lang="ts">
defineOptions({ name: 'FeedView' })

import {
  ArrowRight,
  CirclePlus,
  Close,
  CollectionTag,
  RefreshRight,
  Search,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onActivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonLeftSidebar from '../components/CommonLeftSidebar.vue'
import FeedCardRenderer from '../components/feed/FeedCardRenderer.vue'
import {
  feedChannelTabs as fallbackChannelTabs,
  feedModeTabs,
  type FeedChannelDefinition,
  type FeedModeKey,
} from '../domain/contentTaxonomy'
import {
  api,
  type ChannelView,
  type FeedQueryFilters,
  type FeedRequestAuthMode,
  type TopicView,
  type UserInterestFacetPayload,
  type UserInterestFacetView,
} from '../services/api'
import { HttpError } from '../services/http'
import { useAuthStore } from '../stores/auth'
import type { PostView, UserSummary } from '../types'
import {
  DEFAULT_IMAGE_PLACEHOLDER,
  hasPostMedia as postHasRealMedia,
  normalizeMediaUrl as normalizePostMediaUrl,
} from '../utils/postMedia'

const FEED_ROWS_PER_PAGE = 3
const FEED_INITIAL_VISIBLE_ROWS = 2
const FEED_REVEAL_INTERVAL_MS = 380
const FEED_SKELETON_ROWS = 2
const FEED_MAX_PAGE_SIZE = 24
const FEED_MAX_ITEMS = 1000
const LOAD_MORE_DEBOUNCE_MS = 80
const LOAD_MORE_ROOT_MARGIN = '1600px'
const LOAD_MORE_SCROLL_THRESHOLD = 560
const LOAD_MORE_PREFETCH_VIEWPORTS = 3.4
const LOAD_MORE_TRIGGER_VIEWPORTS = 1.65
const FEED_AUTOFILL_THRESHOLD = 160
const FEED_BACKGROUND_LOAD_DELAY_MS = 650
const FEED_BACKGROUND_MIN_PAGES = 3
const FEED_BACKGROUND_MAX_PAGES = 6
const FEED_BACKGROUND_BUFFER_VIEWPORTS = 4.0
const FEED_SCROLL_Y_KEY = 'image-social-feed-scroll-y'
const FEED_SCROLL_RESTORE_KEY = 'image-social-feed-scroll-restore'
const FEED_REALTIME_REFRESH_KEY = 'image-social-feed-need-refresh'
const FEED_CACHE_TTL_MS = 5 * 60 * 1000
const FEED_SKELETON_ASPECT_RATIOS = ['3 / 4.7', '3 / 3.7', '3 / 4.25', '3 / 5.1', '3 / 3.35', '3 / 4.55']
const WATCH_LATER_KEY = 'vibelo-watch-later-posts'

type FeedPageChunk = {
  records: PostView[]
  total?: number | null
  requestSize?: number
  consumedPages?: number
  sourceHasMore?: boolean
}

type FeedLoadSource = 'scroll' | 'background'

type WatchLaterItem = {
  postId: number
  title: string
  meta: string
  image: string
  authorName: string
  savedAt: number
}

type CreatorRecommendation = {
  user: UserSummary
  bio: string
  postCount: number
  latestPostId?: number
}

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

authStore.hydrate()

const feedNextPage = ref(1)
const total = ref<number | null>(null)
const posts = ref<PostView[]>([])
const feedInitialLoading = ref(false)
const feedLoadingMore = ref(false)
const feedError = ref('')
const feedHasMore = ref(true)
const keyword = ref('')
const displayQuery = ref('')
const searchResultsTab = ref<'posts' | 'users'>('posts')
const searchedPosts = ref<PostView[]>([])
const searchedUsers = ref<UserSummary[]>([])
const searching = ref(false)
const columnCount = ref(4)
const followingAuthorIds = ref<Set<number>>(new Set())
const showBackTop = ref(false)
const likedPostIds = ref<Set<number>>(new Set())
const likeCountOverrides = ref<Record<number, number>>({})
const likingPostIds = ref<Set<number>>(new Set())
const feedSeed = ref(createFeedSeed())
const loadMoreTriggerRef = ref<HTMLElement | null>(null)
const prefetchedFeedPage = ref<{ page: number; data: FeedPageChunk } | null>(null)
const prefetchingFeed = ref(false)
const stagedFeedQueue = ref<PostView[]>([])
const followSetLoading = ref(false)
const friendSetLoading = ref(false)
const bootstrapped = ref(false)
const followSetResolvedUserId = ref<number | null>(null)
const friendSetResolvedUserId = ref<number | null>(null)
const feedRequestAuthMode = ref<FeedRequestAuthMode>('guest')
const fallbackFeedNoticeShown = ref(false)
const feedGuestFallbackActive = ref(false)
const friendAuthorIds = ref<Set<number>>(new Set())
const watchLaterItems = ref<WatchLaterItem[]>([])
const interestFacets = ref<UserInterestFacetView[]>([])
const interestDialogVisible = ref(false)
const interestSaving = ref(false)
const selectedInterestKeys = ref<Set<string>>(new Set())

const isSearchResults = computed(() => Boolean(displayQuery.value.trim()))
const showInitialFeedSkeleton = computed(() => !bootstrapped.value || (feedInitialLoading.value && posts.value.length === 0))
const searchedMasonryColumns = computed(() => distributeIntoColumns(searchedPosts.value))
const isGuestFallbackFeed = computed(() => Boolean(authStore.currentUser && feedGuestFallbackActive.value))
const skeletonColumns = computed(() => buildFeedSkeletonColumns(columnCount.value, FEED_SKELETON_ROWS))
const showProgressiveLoading = computed(() => feedLoadingMore.value || stagedFeedQueue.value.length > 0)
const emptyFeedMessage = computed(() => {
  if (isFollowingFeedMode() && !authStore.currentUser) return '登录后可查看关注动态'
  if (isFriendsFeedMode() && !authStore.currentUser) return '登录后可查看朋友动态'
  if (isFollowingFeedMode()) return '你关注的创作者暂时还没有新内容'
  if (isFriendsFeedMode()) return '你的朋友暂时还没有发布动态'
  if (activeChannel.value !== 'all') return `频道“${currentChannelMeta.value.label}”暂无内容`
  return '暂时没有可展示的内容'
})

type ChannelKey = string

const defaultFeedChannelTab: FeedChannelDefinition = fallbackChannelTabs.find((item) => item.key === 'all') || {
  key: 'all',
  label: '推荐',
  desc: '跨圈层内容流',
  signal: '实时更新',
  keywords: [],
  postType: 'general_post',
  waterfall: true,
}
const activeFeedMode = ref<FeedModeKey>('recommend')
const activeChannel = ref<ChannelKey>('all')
const dynamicChannelTabs = ref<FeedChannelDefinition[]>(fallbackChannelTabs.length > 0 ? fallbackChannelTabs : [defaultFeedChannelTab])
const hotTopicRows = ref<TopicView[]>([])

const currentChannelMeta = computed(() => (
  dynamicChannelTabs.value.find((item) => item.key === activeChannel.value) || dynamicChannelTabs.value[0] || defaultFeedChannelTab
))

const channelTabs = computed(() => dynamicChannelTabs.value)
const audienceSegments = computed(() => channelTabs.value.filter((item) => item.key !== 'all'))
const currentChannelAvatar = computed(() => currentChannelMeta.value.avatar || 'https://picsum.photos/seed/vibelo-recommend/160/160')
const currentChannelDescription = computed(() => currentChannelMeta.value.desc || '跨圈层内容流')
const currentChannelSignal = computed(() => currentChannelMeta.value.signal || '实时更新')
const isForYouChannel = computed(() => activeChannel.value === 'all')
const recommendationName = computed(() => authStore.currentUser?.nickname || authStore.currentUser?.username || 'Vibelo 用户')
const recommendationAvatar = computed(() => normalizeMediaUrl(authStore.currentUser?.avatarUrl) || 'https://api.dicebear.com/9.x/adventurer/svg?seed=vibelo-user')
const recommendationInterests = computed(() => audienceSegments.value.slice(0, 4))
const recommendationLeadPost = computed(() => isForYouChannel.value ? posts.value[0] : null)
const feedStreamPosts = computed(() => posts.value)
const masonryColumns = computed(() => distributeIntoColumns(feedStreamPosts.value))
const interestCandidateFacets = computed<UserInterestFacetView[]>(() => {
  const channelFacets = audienceSegments.value.map((item) => ({
    facetType: 'TOPIC',
    facetKey: item.key,
    facetLabel: item.label,
    weight: 1,
  }))
  const hotTopicFacets = hotTopicRows.value.slice(0, 12).map((item) => ({
    facetType: 'TAG',
    facetKey: item.slug || item.name,
    facetLabel: item.name,
    weight: 1,
  }))
  return dedupeInterestFacets([...channelFacets, ...hotTopicFacets])
})
const displayedInterestFacets = computed(() => {
  if (interestFacets.value.length > 0) return interestFacets.value.slice(0, 8)
  return interestCandidateFacets.value.slice(0, 6)
})
const interestProfileNote = computed(() => (
  authStore.currentUser
    ? interestFacets.value.length > 0
      ? `已订阅 ${interestFacets.value.length} 个兴趣，推荐会优先参考这些方向`
      : '还没有手动配置兴趣，当前使用浏览和频道偏好推断'
    : '登录后可以保存你的兴趣画像，并影响后续推荐'
))
const recommendedCreators = computed<CreatorRecommendation[]>(() => {
  const seen = new Map<number, CreatorRecommendation>()
  for (const post of posts.value) {
    const author = post.author
    if (!author?.id || author.id === authStore.currentUser?.id) continue
    const existing = seen.get(author.id)
    if (existing) {
      existing.postCount += 1
      continue
    }
    const labels = [
      post.channelCode || post.channel || '内容创作',
      ...(post.tags || []).slice(0, 2),
    ].filter(Boolean)
    seen.set(author.id, {
      user: author,
      bio: author.bio || labels.join(' | ') || '持续更新高质量内容',
      postCount: 1,
      latestPostId: post.id,
    })
  }
  return [...seen.values()]
    .sort((a, b) => Number(followingAuthorIds.value.has(a.user.id)) - Number(followingAuthorIds.value.has(b.user.id)))
    .slice(0, 5)
})
const watchLaterRows = computed(() => {
  if (watchLaterItems.value.length > 0) return watchLaterItems.value.slice(0, 4)
  return posts.value.slice(0, 3).map(toWatchLaterItem)
})

const recommendationTabs = [
  { key: 'recommend', label: '综合推荐', mode: 'recommend' },
  { key: 'following', label: '你关注的人', mode: 'following' },
  { key: 'trend', label: '趋势内容' },
  { key: 'nearby', label: '同城' },
  { key: 'fresh', label: '新鲜发布' },
  { key: 'longform', label: '长文精选' },
] satisfies Array<{ key: string; label: string; mode?: FeedModeKey }>

let intersectionObserver: IntersectionObserver | null = null
let loadMoreDebounceTimer: ReturnType<typeof setTimeout> | null = null
let feedRevealTimer: ReturnType<typeof setTimeout> | null = null
let backgroundFeedTimer: ReturnType<typeof setTimeout> | null = null
let backgroundFeedPageLoads = 0
let feedRequestSerial = 0

function isFeedBusy() {
  return feedInitialLoading.value || feedLoadingMore.value || stagedFeedQueue.value.length > 0
}

function isFeedNetworkBusy() {
  return feedInitialLoading.value || feedLoadingMore.value
}

function scheduleLoadMoreFeed() {
  if (isFeedBusy() || !feedHasMore.value || isSearchResults.value) return
  if (posts.value.length >= FEED_MAX_ITEMS) {
    feedHasMore.value = false
    return
  }
  if (loadMoreDebounceTimer !== null) clearTimeout(loadMoreDebounceTimer)
  loadMoreDebounceTimer = setTimeout(() => {
    void loadMoreFeed()
  }, LOAD_MORE_DEBOUNCE_MS)
}

function routeSearchKeyword() {
  const q = route.query.q
  if (Array.isArray(q)) return q[0]?.trim() || ''
  return typeof q === 'string' ? q.trim() : ''
}

function routeQueryValue(value: unknown) {
  if (Array.isArray(value)) return value[0] || ''
  return typeof value === 'string' ? value : ''
}

function resolveFeedModeFromRoute(): FeedModeKey {
  const value = routeQueryValue(route.query.feed)
  return feedModeTabs.some((item) => item.key === value)
    ? value as FeedModeKey
    : 'recommend'
}

function resolveChannelFromRoute(): ChannelKey {
  const value = routeQueryValue(route.query.channel)
  return channelTabs.value.some((item) => item.key === value)
    ? value
    : 'all'
}

function fallbackChannelMeta(code: string) {
  return fallbackChannelTabs.find((item) => item.key === code)
}

function buildChannelKeywords(channel: ChannelView, fallback?: FeedChannelDefinition) {
  return [
    channel.code,
    channel.name,
    channel.description,
    ...(fallback?.keywords || []),
  ]
    .filter((item): item is string => Boolean(item && item.trim()))
    .map((item) => item.trim())
}

function mapApiFeedChannel(channel: ChannelView): FeedChannelDefinition {
  const fallback = fallbackChannelMeta(channel.code)
  const filters: FeedQueryFilters = { channelCode: channel.code }
  return {
    key: channel.code,
    label: channel.name,
    desc: channel.description || fallback?.desc || '正在生长的内容频道',
    signal: fallback?.signal || (channel.waterfall ? '瀑布流展示' : '专题流展示'),
    topicPath: channel.name,
    filters,
    keywords: buildChannelKeywords(channel, fallback),
    avatar: channel.icon || fallback?.avatar || `https://picsum.photos/seed/channel-${channel.code}/80/80`,
    postType: channel.postType || fallback?.postType || 'general_post',
    waterfall: channel.waterfall ?? fallback?.waterfall ?? true,
  }
}

async function loadFeedChannels() {
  try {
    const channels = await api.channels()
    const next = channels
      .filter((item) => item.code && item.name)
      .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
      .map(mapApiFeedChannel)
    if (next.length > 0) {
      dynamicChannelTabs.value = [defaultFeedChannelTab, ...next]
    }
  } catch {
    dynamicChannelTabs.value = fallbackChannelTabs
  }
}

async function loadHotTopics() {
  try {
    hotTopicRows.value = await api.trendingTopics(8)
  } catch {
    hotTopicRows.value = []
  }
}

function formatTopicHeat(topic: TopicView) {
  const postCount = Number(topic.postCount || 0)
  if (postCount >= 10000) return `${(postCount / 10000).toFixed(1)}万篇`
  if (postCount > 0) return `${postCount}篇`
  const hotScore = Number(topic.hotScore || 0)
  if (hotScore > 0) return `热度 ${Math.round(hotScore)}`
  return '新话题'
}

function applyRouteFeedState() {
  activeFeedMode.value = resolveFeedModeFromRoute()
  activeChannel.value = resolveChannelFromRoute()
}

function syncFeedQueryToRoute() {
  const nextQuery = { ...route.query } as Record<string, string | string[] | null | undefined>
  if (activeFeedMode.value === 'recommend') delete nextQuery.feed
  else nextQuery.feed = activeFeedMode.value
  if (activeChannel.value === 'all') delete nextQuery.channel
  else nextQuery.channel = activeChannel.value
  void router.replace({ path: '/home', query: nextQuery })
}

function isFollowingFeedMode() {
  return activeFeedMode.value === 'following'
}

function isFriendsFeedMode() {
  return activeFeedMode.value === 'friends'
}

function currentChannelFilters() {
  const meta = channelTabs.value.find((item) => item.key === activeChannel.value)
  return meta?.filters
}

function normalizedText(value?: string | null) {
  return (value || '').toLowerCase()
}

function postSearchTokens(post: PostView) {
  const tokens = [
    normalizedText(post.title),
    normalizedText(post.content),
    normalizedText(post.channelCode),
    normalizedText(post.channel),
    normalizedText(post.postType),
    normalizedText(post.topicPath),
    ...(post.tags || []).map((item) => normalizedText(item)),
    ...(post.semanticTags || []).map((item) => normalizedText(item)),
    ...(post.styleTags || []).map((item) => normalizedText(item)),
  ].filter(Boolean)
  return tokens.join(' ')
}

function postMatchesCurrentChannel(post: PostView) {
  if (activeChannel.value === 'all') return true
  const channel = channelTabs.value.find((item) => item.key === activeChannel.value)
  if (!channel) return true
  if (post.channelCode === channel.key || post.channel === channel.key) return true
  const haystack = postSearchTokens(post)
  if (!haystack) return false
  return channel.keywords.some((keyword) => haystack.includes(normalizedText(keyword)))
}

function postMatchesFeedMode(post: PostView) {
  if (activeFeedMode.value === 'recommend') return true
  if (!authStore.currentUser) return false
  if (isFollowingFeedMode()) return followingAuthorIds.value.has(post.author.id)
  if (isFriendsFeedMode()) return friendAuthorIds.value.has(post.author.id)
  return true
}

function filterRecordsByActiveScope(records: PostView[]) {
  return records.filter((post) => postMatchesFeedMode(post) && postMatchesCurrentChannel(post))
}

function filterRecordsByActiveChannel(records: PostView[]) {
  return records.filter(postMatchesCurrentChannel)
}

function navigationType() {
  const entry = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming | undefined
  return entry?.type || ''
}

function createFeedSeed() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `feed-${Date.now()}-${Math.round(Math.random() * 1_000_000)}`
}

function refreshFeedSeed() {
  feedSeed.value = createFeedSeed()
}

function shouldUseFeedCacheOnEntry(navType = '') {
  return navType !== 'reload' && sessionStorage.getItem(FEED_SCROLL_RESTORE_KEY) === '1'
}

function resetFeedRequestAuthMode() {
  feedRequestAuthMode.value = authStore.currentUser ? 'session' : 'guest'
  feedGuestFallbackActive.value = false
  fallbackFeedNoticeShown.value = false
}

function normalizeMediaUrl(url?: string | null) {
  if (!url) return DEFAULT_IMAGE_PLACEHOLDER
  return url.replace('http://localhost:9000', '/minio-img')
}

function displayedLikeCount(post: PostView) {
  return likeCountOverrides.value[post.id] ?? post.likeCount
}

function isPostLiked(postId: number) {
  return likedPostIds.value.has(postId)
}

function isPostLiking(postId: number) {
  return likingPostIds.value.has(postId)
}

function creatorAvatar(index: number) {
  const sample = recommendedCreators.value[index]?.user || posts.value[index % Math.max(1, posts.value.length)]?.author
  return normalizePostMediaUrl(sample?.avatarUrl) || DEFAULT_IMAGE_PLACEHOLDER
}

function interestKey(facet: Pick<UserInterestFacetView, 'facetType' | 'facetKey'>) {
  return `${facet.facetType || 'TOPIC'}:${facet.facetKey}`
}

function dedupeInterestFacets(facets: UserInterestFacetView[]) {
  const seen = new Set<string>()
  const result: UserInterestFacetView[] = []
  for (const facet of facets) {
    if (!facet.facetKey || !facet.facetLabel) continue
    const key = interestKey(facet)
    if (seen.has(key)) continue
    seen.add(key)
    result.push(facet)
  }
  return result
}

function applySelectedInterestKeys(facets: UserInterestFacetView[]) {
  selectedInterestKeys.value = new Set(facets.map(interestKey))
}

async function loadUserInterests() {
  if (!authStore.currentUser) {
    interestFacets.value = []
    applySelectedInterestKeys([])
    return
  }
  try {
    const data = await api.myInterests()
    interestFacets.value = dedupeInterestFacets(data.facets || [])
    applySelectedInterestKeys(interestFacets.value)
  } catch {
    interestFacets.value = []
    applySelectedInterestKeys([])
  }
}

function openInterestDialog() {
  if (!authStore.currentUser) {
    authStore.openAuthPrompt('manual')
    return
  }
  applySelectedInterestKeys(interestFacets.value.length > 0 ? interestFacets.value : displayedInterestFacets.value)
  interestDialogVisible.value = true
}

function toggleInterestFacet(facet: UserInterestFacetView) {
  const next = new Set(selectedInterestKeys.value)
  const key = interestKey(facet)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  selectedInterestKeys.value = next
}

function openInterestFacet(facet: UserInterestFacetView) {
  if (channelTabs.value.some((item) => item.key === facet.facetKey)) {
    selectChannel(facet.facetKey)
    return
  }
  void router.push({ path: '/search', query: { q: facet.facetLabel || facet.facetKey } })
}

async function saveInterestProfile() {
  if (!authStore.currentUser) {
    authStore.openAuthPrompt('manual')
    return
  }
  const selected = interestCandidateFacets.value.filter((facet) => selectedInterestKeys.value.has(interestKey(facet)))
  const payload: UserInterestFacetPayload[] = selected.map((facet, index) => ({
    facetType: facet.facetType || 'TOPIC',
    facetKey: facet.facetKey,
    facetLabel: facet.facetLabel,
    weight: Number((1 + Math.max(0, selected.length - index) * 0.08).toFixed(2)),
  }))
  interestSaving.value = true
  try {
    const data = await api.updateMyInterests({ facets: payload })
    interestFacets.value = dedupeInterestFacets(data.facets || [])
    applySelectedInterestKeys(interestFacets.value)
    interestDialogVisible.value = false
    sessionStorage.setItem(FEED_REALTIME_REFRESH_KEY, '1')
    ElMessage.success('兴趣画像已更新')
    void reloadFeedByScope()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '兴趣画像保存失败')
  } finally {
    interestSaving.value = false
  }
}

function toWatchLaterItem(post: PostView): WatchLaterItem {
  return {
    postId: post.id,
    title: post.title || post.content?.slice(0, 24) || '无标题内容',
    meta: `${post.author?.nickname || '匿名用户'} · ${post.channelCode || post.channel || '推荐'}`,
    image: normalizeMediaUrl(post.thumbUrl || post.coverUrl || post.assets?.[0]?.thumbUrl || post.assets?.[0]?.fileUrl),
    authorName: post.author?.nickname || '匿名用户',
    savedAt: Date.now(),
  }
}

function loadWatchLater() {
  try {
    const raw = localStorage.getItem(WATCH_LATER_KEY)
    const parsed = raw ? JSON.parse(raw) as WatchLaterItem[] : []
    watchLaterItems.value = Array.isArray(parsed) ? parsed.filter((item) => item?.postId).slice(0, 30) : []
  } catch {
    watchLaterItems.value = []
  }
}

function persistWatchLater() {
  localStorage.setItem(WATCH_LATER_KEY, JSON.stringify(watchLaterItems.value.slice(0, 30)))
}

function addWatchLater(post?: PostView | null) {
  const target = post || recommendationLeadPost.value || posts.value[0]
  if (!target) {
    ElMessage.info('当前没有可加入的内容')
    return
  }
  const next = [toWatchLaterItem(target), ...watchLaterItems.value.filter((item) => item.postId !== target.id)]
  watchLaterItems.value = next.slice(0, 30)
  persistWatchLater()
  ElMessage.success('已加入稍后再看')
}

function removeWatchLater(postId: number) {
  watchLaterItems.value = watchLaterItems.value.filter((item) => item.postId !== postId)
  persistWatchLater()
}

function openWatchLater(item: WatchLaterItem) {
  void router.push(`/posts/${item.postId}`)
}

function computeColumnCount() {
  const width = window.innerWidth
  if (width >= 1880) return 5
  if (width >= 1320) return 4
  if (width >= 980) return 3
  if (width >= 640) return 2
  return 1
}

function updateColumnCount() {
  columnCount.value = computeColumnCount()
}

function currentFeedPageSize() {
  return Math.max(9, Math.min(FEED_MAX_PAGE_SIZE, columnCount.value * FEED_ROWS_PER_PAGE))
}

function initialVisibleCount() {
  return Math.max(6, Math.min(currentFeedPageSize(), columnCount.value * FEED_INITIAL_VISIBLE_ROWS))
}

function revealChunkSize() {
  return Math.max(3, Math.min(columnCount.value, 8))
}

function visibleScrollBuffer() {
  return document.documentElement.scrollHeight - window.innerHeight - window.scrollY
}

function buildFeedSkeletonColumns(count: number, rows: number) {
  const safeCount = Math.max(1, count)
  const safeRows = Math.max(1, rows)
  return Array.from({ length: safeCount }, (_, columnIndex) => (
    Array.from({ length: safeRows }, (_, rowIndex) => {
      const index = columnIndex * safeRows + rowIndex
      return {
        key: `feed-skeleton-${columnIndex}-${rowIndex}`,
        aspectRatio: FEED_SKELETON_ASPECT_RATIOS[index % FEED_SKELETON_ASPECT_RATIOS.length],
      }
    })
  ))
}

function clearFeedRevealTimer() {
  if (feedRevealTimer !== null) {
    clearTimeout(feedRevealTimer)
    feedRevealTimer = null
  }
}

function clearBackgroundFeedTimer() {
  if (backgroundFeedTimer !== null) {
    clearTimeout(backgroundFeedTimer)
    backgroundFeedTimer = null
  }
}

function clearFeedRevealQueue() {
  clearFeedRevealTimer()
  stagedFeedQueue.value = []
}

function resetBackgroundFeedWarmup() {
  clearBackgroundFeedTimer()
  backgroundFeedPageLoads = 0
}

function shouldWarmFeedInBackground() {
  if (isFeedBusy() || !feedHasMore.value || isSearchResults.value) return false
  if (posts.value.length >= FEED_MAX_ITEMS) return false
  if (backgroundFeedPageLoads < FEED_BACKGROUND_MIN_PAGES) return true
  if (backgroundFeedPageLoads >= FEED_BACKGROUND_MAX_PAGES) return false
  return visibleScrollBuffer() < window.innerHeight * FEED_BACKGROUND_BUFFER_VIEWPORTS
}

function scheduleBackgroundFeedWarmup() {
  if (!shouldWarmFeedInBackground()) return
  clearBackgroundFeedTimer()
  void prefetchFeedPage(feedNextPage.value)
  backgroundFeedTimer = setTimeout(() => {
    backgroundFeedTimer = null
    if (!shouldWarmFeedInBackground()) return
    void loadMoreFeed('background')
  }, FEED_BACKGROUND_LOAD_DELAY_MS)
}

function scheduleNextPassiveFeedLoad() {
  const scheduledImmediateFill = maybeAutofillFeed()
  if (!scheduledImmediateFill) {
    scheduleBackgroundFeedWarmup()
  }
}

function scheduleFeedReveal() {
  clearFeedRevealTimer()
  if (stagedFeedQueue.value.length === 0) return
  feedRevealTimer = setTimeout(() => {
    revealNextFeedChunk()
  }, FEED_REVEAL_INTERVAL_MS)
}

function revealNextFeedChunk() {
  feedRevealTimer = null
  const count = Math.min(revealChunkSize(), stagedFeedQueue.value.length)
  const next = stagedFeedQueue.value.slice(0, count)
  stagedFeedQueue.value = stagedFeedQueue.value.slice(count)
  if (next.length > 0) {
    posts.value = [...posts.value, ...next]
    saveFeedToCache()
  }
  if (stagedFeedQueue.value.length > 0) {
    scheduleFeedReveal()
    return
  }
  void nextTick(() => scheduleNextPassiveFeedLoad())
}

function queueFeedBatch(batch: PostView[], immediateCount: number) {
  const first = batch.slice(0, Math.max(0, immediateCount))
  const rest = batch.slice(first.length)
  if (first.length > 0) {
    posts.value = [...posts.value, ...first]
  }
  if (rest.length > 0) {
    stagedFeedQueue.value = [...stagedFeedQueue.value, ...rest]
    scheduleFeedReveal()
  }
}

function navigateToPost(post: PostView) {
  sessionStorage.setItem(FEED_SCROLL_Y_KEY, String(window.scrollY))
  sessionStorage.setItem(FEED_SCROLL_RESTORE_KEY, '1')
  void api.trackBehavior({
    postId: post.id,
    channelCode: post.channelCode || post.channel,
    behaviorType: 'click',
    scene: 'feed',
  }).catch(() => undefined)
  void router.push(`/posts/${post.id}`)
}

async function handleTogglePostLike(post: PostView) {
  if (!authStore.accessToken) {
    authStore.openAuthPrompt('manual')
    return
  }
  if (isPostLiking(post.id)) return

  likingPostIds.value = new Set([...likingPostIds.value, post.id])
  try {
    const data = await api.toggleLike(post.id)
    const nextLikedIds = new Set(likedPostIds.value)
    const previousKnownLiked = nextLikedIds.has(post.id)
    const baseCount = displayedLikeCount(post)
    if (data.active) {
      nextLikedIds.add(post.id)
      likeCountOverrides.value = {
        ...likeCountOverrides.value,
        [post.id]: previousKnownLiked ? baseCount : baseCount + 1,
      }
    } else {
      nextLikedIds.delete(post.id)
      likeCountOverrides.value = {
        ...likeCountOverrides.value,
        [post.id]: Math.max(0, previousKnownLiked ? baseCount - 1 : baseCount),
      }
    }
    likedPostIds.value = nextLikedIds
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '点赞失败')
  } finally {
    const nextLoading = new Set(likingPostIds.value)
    nextLoading.delete(post.id)
    likingPostIds.value = nextLoading
  }
}

function handleWindowScroll() {
  showBackTop.value = window.scrollY > 480
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function coverAspectRatio(postId: number) {
  const ratios = ['3 / 4.8', '3 / 3.9', '3 / 4.4', '3 / 3.6', '3 / 4.2', '3 / 5.1']
  return ratios[postId % ratios.length]
}

function estimatedCardHeight(post: PostView) {
  if (!postHasRealMedia(post)) return 0.72

  const asset = post.assets?.[0]
  const width = Number(asset?.width || 0)
  const height = Number(asset?.height || 0)
  if (width > 0 && height > 0) return height / width

  const [w, h] = coverAspectRatio(post.id).split('/').map((value) => Number(value.trim()))
  return (h || 4) / (w || 3)
}

function distributeIntoColumns(items: PostView[]) {
  const count = Math.max(1, columnCount.value)
  const columns: PostView[][] = Array.from({ length: count }, () => [])
  const heights: number[] = Array.from({ length: count }, () => 0)

  for (const post of items) {
    let minIndex = 0
    for (let i = 1; i < count; i++) {
      if (heights[i] < heights[minIndex]) minIndex = i
    }
    columns[minIndex].push(post)
    heights[minIndex] += estimatedCardHeight(post)
  }

  return columns
}

function pickMergeBatch(records: PostView[]) {
  const existingIds = new Set([
    ...posts.value.map((item) => item.id),
    ...stagedFeedQueue.value.map((item) => item.id),
  ])
  const unseen: PostView[] = []
  const unseenIds = new Set<number>()

  for (const item of records) {
    if (existingIds.has(item.id) || unseenIds.has(item.id)) continue
    unseen.push(item)
    unseenIds.add(item.id)
  }

  return unseen
}

function dedupeRecords(records: PostView[]) {
  const seen = new Set<number>()
  const unique: PostView[] = []
  for (const item of records || []) {
    if (seen.has(item.id)) continue
    seen.add(item.id)
    unique.push(item)
  }
  return unique
}

async function loadFollowSet() {
  if (!authStore.currentUser) {
    followingAuthorIds.value = new Set()
    followSetResolvedUserId.value = null
    return
  }
  if (followSetLoading.value || followSetResolvedUserId.value === authStore.currentUser.id) return
  followSetLoading.value = true
  try {
    const following = await api.following(authStore.currentUser.id)
    followingAuthorIds.value = new Set(following.map((item) => item.id))
    followSetResolvedUserId.value = authStore.currentUser.id
  } catch {
    followingAuthorIds.value = new Set()
  } finally {
    followSetLoading.value = false
  }
}

async function loadFriendSet() {
  if (!authStore.currentUser) {
    friendAuthorIds.value = new Set()
    friendSetResolvedUserId.value = null
    return
  }
  if (friendSetLoading.value || friendSetResolvedUserId.value === authStore.currentUser.id) return
  friendSetLoading.value = true
  try {
    const [following, followers] = await Promise.all([
      api.following(authStore.currentUser.id),
      api.followers(authStore.currentUser.id),
    ])
    const followerIds = new Set(followers.map((item) => item.id))
    friendAuthorIds.value = new Set(following.map((item) => item.id).filter((id) => followerIds.has(id)))
    followingAuthorIds.value = new Set(following.map((item) => item.id))
    friendSetResolvedUserId.value = authStore.currentUser.id
    followSetResolvedUserId.value = authStore.currentUser.id
  } catch {
    friendAuthorIds.value = new Set()
  } finally {
    friendSetLoading.value = false
  }
}

async function ensureFeedScopeDependencies() {
  if (isFollowingFeedMode()) {
    await loadFollowSet()
    return
  }
  if (isFriendsFeedMode()) {
    await loadFriendSet()
  }
}

function resetFeedState() {
  clearFeedRevealQueue()
  resetBackgroundFeedWarmup()
  feedNextPage.value = 1
  total.value = null
  posts.value = []
  feedError.value = ''
  feedHasMore.value = true
  prefetchedFeedPage.value = null
  prefetchingFeed.value = false
}

type FeedCachePayload = {
  cachedAt: number
  total: number | null
  nextPage: number
  hasMore: boolean
  records: PostView[]
}

function currentFeedCacheKey() {
  return `image-social-feed-cache:${authStore.currentUser?.id || 'guest'}:${activeFeedMode.value}:${activeChannel.value}`
}

function restoreFeedFromCache() {
  if (isSearchResults.value) return false
  try {
    const raw = sessionStorage.getItem(currentFeedCacheKey())
    if (!raw) return false
    const payload = JSON.parse(raw) as FeedCachePayload
    if (!payload || !Array.isArray(payload.records)) return false
    if (Date.now() - Number(payload.cachedAt || 0) > FEED_CACHE_TTL_MS) {
      sessionStorage.removeItem(currentFeedCacheKey())
      return false
    }
    clearFeedRevealQueue()
    clearBackgroundFeedTimer()
    posts.value = dedupeRecords(payload.records).slice(0, FEED_MAX_ITEMS)
    total.value = typeof payload.total === 'number' ? payload.total : null
    feedNextPage.value = Math.max(2, Number(payload.nextPage || 2))
    feedHasMore.value = Boolean(payload.hasMore) && posts.value.length < FEED_MAX_ITEMS
    feedError.value = ''
    return posts.value.length > 0
  } catch {
    sessionStorage.removeItem(currentFeedCacheKey())
    return false
  }
}

function saveFeedToCache() {
  if (isSearchResults.value || posts.value.length === 0) return
  const payload: FeedCachePayload = {
    cachedAt: Date.now(),
    total: total.value,
    nextPage: feedNextPage.value,
    hasMore: feedHasMore.value,
    records: dedupeRecords(posts.value).slice(0, Math.min(posts.value.length, 120)),
  }
  sessionStorage.setItem(currentFeedCacheKey(), JSON.stringify(payload))
}

function consumeRealtimeRefreshFlag() {
  if (sessionStorage.getItem(FEED_REALTIME_REFRESH_KEY) !== '1') return false
  sessionStorage.removeItem(FEED_REALTIME_REFRESH_KEY)
  return true
}

async function refreshFeedAfterRealtimeSignals() {
  if (!consumeRealtimeRefreshFlag()) return
  if (isSearchResults.value) return
  refreshFeedSeed()
  resetFeedState()
  await loadInitialFeed()
  window.scrollTo({ top: 0, behavior: 'auto' })
}

function rememberPrefetchedPage(page: number, data: FeedPageChunk) {
  prefetchedFeedPage.value = {
    page,
    data: {
      records: data.records || [],
      total: typeof data.total === 'number' ? data.total : null,
      requestSize: data.requestSize,
      consumedPages: data.consumedPages,
      sourceHasMore: data.sourceHasMore,
    },
  }
}

function takePrefetchedPage(page: number) {
  if (!prefetchedFeedPage.value || prefetchedFeedPage.value.page !== page) return null
  const payload = prefetchedFeedPage.value.data
  prefetchedFeedPage.value = null
  return payload
}

async function prefetchFeedPage(page: number) {
  if (prefetchingFeed.value || isFeedNetworkBusy() || !feedHasMore.value || isSearchResults.value) return
  if (prefetchedFeedPage.value?.page === page) return
  if (posts.value.length >= FEED_MAX_ITEMS) return

  const prefetchSignature = `${feedSeed.value}|${displayQuery.value}|${activeFeedMode.value}|${activeChannel.value}`
  const requestSize = currentFeedPageSize()
  prefetchingFeed.value = true
  try {
    const data = await requestFeedPage(page, requestSize)
    const latestSignature = `${feedSeed.value}|${displayQuery.value}|${activeFeedMode.value}|${activeChannel.value}`
    if (latestSignature !== prefetchSignature) return
    rememberPrefetchedPage(page, { ...data, requestSize })
  } catch {
    prefetchedFeedPage.value = null
  } finally {
    prefetchingFeed.value = false
  }
}

function applyLoadedFeedPage(page: number, data: FeedPageChunk) {
  const consumedPages = Math.max(1, Number(data.consumedPages || 1))
  feedNextPage.value = page + consumedPages
  const requestSize = data.requestSize || currentFeedPageSize()

  const orderedBatch = data.records || []
  let batch = pickMergeBatch(orderedBatch).slice(0, Math.max(0, FEED_MAX_ITEMS - posts.value.length))

  if (batch.length > 0) {
    queueFeedBatch(batch, posts.value.length === 0 ? initialVisibleCount() : revealChunkSize())
  }

  if (typeof data.total === 'number') total.value = data.total
  const serverHasMore = typeof data.sourceHasMore === 'boolean'
    ? data.sourceHasMore
    : computeServerHasMore(page, requestSize, data.records || [], data.total)
  feedHasMore.value = serverHasMore && posts.value.length < FEED_MAX_ITEMS
}

function canFallbackToGuestFeed(error: unknown) {
  if (!authStore.currentUser || feedRequestAuthMode.value === 'guest') return false
  if (!(error instanceof HttpError)) return true
  if (error.code === 'U001') return true
  if (error.status === 401 || error.status === 403) return false
  if (error.code === 'A002' || error.code === 'A003') return false
  if (error.code === 'HTTP_TIMEOUT') return true
  return typeof error.status === 'number' ? error.status >= 500 : true
}

function isStaleSessionUserError(error: unknown) {
  return error instanceof HttpError && error.code === 'U001'
}

async function requestFeedPageRaw(page: number, requestSize = currentFeedPageSize()): Promise<FeedPageChunk> {
  const filters = currentChannelFilters()
  if (feedRequestAuthMode.value === 'guest') {
    const data = await api.homeFeed(page, requestSize, feedSeed.value, filters, 'guest')
    return { ...data, requestSize }
  }

  try {
    const data = await api.homeFeed(page, requestSize, feedSeed.value, filters, 'session')
    feedGuestFallbackActive.value = false
    return { ...data, requestSize }
  } catch (error) {
    if (!canFallbackToGuestFeed(error)) throw error
    if (isStaleSessionUserError(error)) {
      authStore.clearSession()
      feedRequestAuthMode.value = 'guest'
      feedGuestFallbackActive.value = false
      fallbackFeedNoticeShown.value = true
      prefetchedFeedPage.value = null
      const data = await api.homeFeed(page, requestSize, feedSeed.value, filters, 'guest')
      return { ...data, requestSize }
    }
    feedGuestFallbackActive.value = true
    prefetchedFeedPage.value = null
    if (!fallbackFeedNoticeShown.value) {
      fallbackFeedNoticeShown.value = true
      ElMessage.warning('个性化推荐暂时较慢，已切换为探索流')
    }
    const data = await api.homeFeed(page, requestSize, feedSeed.value, filters, 'guest')
    return { ...data, requestSize }
  }
}

function computeServerHasMore(page: number, requestSize: number, records: PostView[], totalValue?: number | null) {
  return records.length >= requestSize
    || (typeof totalValue === 'number' && page * requestSize < totalValue)
}

async function requestFeedPage(page: number, requestSize = currentFeedPageSize()): Promise<FeedPageChunk> {
  if ((isFollowingFeedMode() || isFriendsFeedMode()) && !authStore.currentUser) {
    return {
      records: [],
      total: 0,
      requestSize,
      consumedPages: 1,
      sourceHasMore: false,
    }
  }

  if (isFollowingFeedMode() || isFriendsFeedMode()) {
    const mode = isFriendsFeedMode() ? 'friends' : 'following'
    const data = await api.socialFeed(mode, page, requestSize)
    const records = activeChannel.value === 'all'
      ? data.records || []
      : filterRecordsByActiveChannel(data.records || [])
    return {
      ...data,
      records,
      requestSize,
      consumedPages: 1,
      sourceHasMore: computeServerHasMore(page, requestSize, data.records || [], data.total),
    }
  }

  await ensureFeedScopeDependencies()

  const needExtraScan = isFollowingFeedMode() || isFriendsFeedMode() || activeChannel.value !== 'all'
  if (!needExtraScan) {
    const data = await requestFeedPageRaw(page, requestSize)
    return {
      ...data,
      records: filterRecordsByActiveScope(data.records || []),
      consumedPages: 1,
      sourceHasMore: computeServerHasMore(page, requestSize, data.records || [], data.total),
    }
  }

  const maxSweepPages = 5
  let cursor = page
  let consumedPages = 0
  let aggregated: PostView[] = []
  let totalValue: number | null = null
  let hasMore = true

  while (hasMore && consumedPages < maxSweepPages && aggregated.length < requestSize) {
    const data = await requestFeedPageRaw(cursor, requestSize)
    const filtered = filterRecordsByActiveScope(data.records || [])
    aggregated = dedupeRecords([...aggregated, ...filtered]).slice(0, requestSize)
    if (typeof data.total === 'number') totalValue = data.total
    hasMore = computeServerHasMore(cursor, requestSize, data.records || [], data.total)
    cursor += 1
    consumedPages += 1
  }

  return {
    records: aggregated,
    total: totalValue,
    requestSize,
    consumedPages: Math.max(1, consumedPages),
    sourceHasMore: hasMore,
  }
}

async function loadInitialFeed() {
  const hasVisiblePosts = posts.value.length > 0
  if (isSearchResults.value || feedLoadingMore.value || feedInitialLoading.value) return

  const requestId = ++feedRequestSerial
  if (!hasVisiblePosts) {
    feedInitialLoading.value = true
  }
  try {
    const requestSize = currentFeedPageSize()
    const data = await requestFeedPage(1, requestSize)
    if (requestId !== feedRequestSerial) return
    feedError.value = ''
    clearFeedRevealQueue()
    posts.value = []
    queueFeedBatch(dedupeRecords(data.records || []).slice(0, FEED_MAX_ITEMS), initialVisibleCount())
    total.value = typeof data.total === 'number' ? data.total : null
    feedNextPage.value = 1 + Math.max(1, Number(data.consumedPages || 1))
    prefetchedFeedPage.value = null
    const serverHasMore = typeof data.sourceHasMore === 'boolean'
      ? data.sourceHasMore
      : computeServerHasMore(1, requestSize, data.records || [], data.total)
    feedHasMore.value = serverHasMore
      && posts.value.length < FEED_MAX_ITEMS
      && (typeof data.total !== 'number' || posts.value.length < data.total)
    saveFeedToCache()
    await nextTick()
    if (stagedFeedQueue.value.length === 0) scheduleNextPassiveFeedLoad()
  } catch (error) {
    if (requestId !== feedRequestSerial) return
    const message = error instanceof Error ? error.message : '加载首页内容失败'
    if (posts.value.length === 0) {
      feedError.value = message
      ElMessage.error(message)
    } else {
      ElMessage.warning('首页内容刷新较慢，先展示缓存内容。')
    }
  } finally {
    if (requestId === feedRequestSerial) {
      feedInitialLoading.value = false
    }
    if (feedHasMore.value) void prefetchFeedPage(feedNextPage.value)
  }
}

async function loadMoreFeed(source: FeedLoadSource = 'scroll') {
  if (isFeedBusy() || !feedHasMore.value || isSearchResults.value) return
  clearBackgroundFeedTimer()
  if (posts.value.length >= FEED_MAX_ITEMS) {
    feedHasMore.value = false
    return
  }

  const page = feedNextPage.value
  const prefetched = takePrefetchedPage(page)
  if (prefetched) {
    applyLoadedFeedPage(page, prefetched)
    if (source === 'background') backgroundFeedPageLoads += 1
    saveFeedToCache()
    await nextTick()
    if (stagedFeedQueue.value.length === 0) scheduleNextPassiveFeedLoad()
    if (feedHasMore.value) void prefetchFeedPage(feedNextPage.value)
    return
  }

  feedLoadingMore.value = true
  try {
    const data = await requestFeedPage(page)
    applyLoadedFeedPage(page, data)
    if (source === 'background') backgroundFeedPageLoads += 1
    saveFeedToCache()
    await nextTick()
    if (stagedFeedQueue.value.length === 0) scheduleNextPassiveFeedLoad()
  } catch (error) {
    const message = error instanceof Error ? error.message : '加载更多内容失败'
    if (posts.value.length === 0) {
      feedError.value = message
    }
    ElMessage.error(message)
  } finally {
    feedLoadingMore.value = false
    if (feedHasMore.value) void prefetchFeedPage(feedNextPage.value)
  }
}

function setupInfiniteScroll() {
  if (intersectionObserver) intersectionObserver.disconnect()
  if (isSearchResults.value) return

  intersectionObserver = new IntersectionObserver(
    (entries) => {
      const entry = entries[0]
      if (!entry.isIntersecting || isFeedBusy() || !feedHasMore.value || isSearchResults.value) return
      scheduleLoadMoreFeed()
    },
    { rootMargin: LOAD_MORE_ROOT_MARGIN },
  )

  if (loadMoreTriggerRef.value) {
    intersectionObserver.observe(loadMoreTriggerRef.value)
  }
}

function handleFeedInfiniteScroll() {
  if (feedInitialLoading.value || !feedHasMore.value || isSearchResults.value) return
  const remaining = visibleScrollBuffer()
  if (remaining < window.innerHeight * LOAD_MORE_PREFETCH_VIEWPORTS) {
    void prefetchFeedPage(feedNextPage.value)
  }
  if (remaining < Math.max(LOAD_MORE_SCROLL_THRESHOLD, window.innerHeight * LOAD_MORE_TRIGGER_VIEWPORTS)) {
    scheduleLoadMoreFeed()
  }
}

function maybeAutofillFeed() {
  if (isFeedBusy() || !feedHasMore.value || isSearchResults.value) return false
  const remaining = visibleScrollBuffer()
  if (remaining < FEED_AUTOFILL_THRESHOLD) {
    scheduleLoadMoreFeed()
    return true
  }
  if (remaining < FEED_AUTOFILL_THRESHOLD * 2) {
    void prefetchFeedPage(feedNextPage.value)
  }
  return false
}

async function runSearch() {
  const query = keyword.value.trim()
  if (!query) {
    clearSearchResults()
    return
  }

  clearFeedRevealQueue()
  resetBackgroundFeedWarmup()
  searching.value = true
  try {
    displayQuery.value = query
    const [postsData, usersData] = await Promise.all([
      api.searchPostsPage(query, 1, 18),
      api.searchUsersPage(query, 1, 18),
    ])
    searchedPosts.value = postsData.records
    searchedUsers.value = usersData.records
    searchResultsTab.value = 'posts'
    if (authStore.currentUser && usersData.records.length > 0) {
      void loadFollowSet()
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '搜索失败')
  } finally {
    searching.value = false
  }
}

function clearSearchResults(reload = true) {
  keyword.value = ''
  displayQuery.value = ''
  searchResultsTab.value = 'posts'
  searchedPosts.value = []
  searchedUsers.value = []
  prefetchedFeedPage.value = null
  resetBackgroundFeedWarmup()
  if (reload) void loadInitialFeed()
}

async function reloadFeedByScope() {
  feedRequestSerial += 1
  feedInitialLoading.value = false
  feedLoadingMore.value = false
  if (isSearchResults.value) clearSearchResults(false)
  refreshFeedSeed()
  resetFeedState()
  await loadInitialFeed()
  window.scrollTo({ top: 0, behavior: 'auto' })
}

function selectChannel(channel: ChannelKey) {
  if (activeChannel.value === channel) return
  activeChannel.value = channel
  syncFeedQueryToRoute()
  void reloadFeedByScope()
}

function selectFeedMode(mode: FeedModeKey) {
  if (activeFeedMode.value === mode) return
  activeFeedMode.value = mode
  syncFeedQueryToRoute()
  void reloadFeedByScope()
}

function selectRecommendationTab(tab: { mode?: FeedModeKey }) {
  if (!tab.mode) return
  selectFeedMode(tab.mode)
}

async function handleToggleFollow(authorId: number) {
  if (!authStore.currentUser) {
    authStore.openAuthPrompt('manual')
    return
  }

  if (followingAuthorIds.value.has(authorId)) {
    await api.unfollow(authorId, 'feed_user_search')
    followingAuthorIds.value.delete(authorId)
    sessionStorage.setItem(FEED_REALTIME_REFRESH_KEY, '1')
    ElMessage.success('已取消关注')
  } else {
    await api.follow(authorId, 'feed_user_search')
    followingAuthorIds.value.add(authorId)
    sessionStorage.setItem(FEED_REALTIME_REFRESH_KEY, '1')
    ElMessage.success('关注成功')
  }
  followingAuthorIds.value = new Set(followingAuthorIds.value)
}

watch(
  () => posts.value.length,
  async () => {
    if (isSearchResults.value) return
    await nextTick()
    setupInfiniteScroll()
  },
)

watch(isSearchResults, async (value) => {
  await nextTick()
  if (value) {
    if (intersectionObserver) intersectionObserver.disconnect()
    clearFeedRevealQueue()
    resetBackgroundFeedWarmup()
    return
  }
  setupInfiniteScroll()
})

watch(
  () => route.query.q,
  async () => {
    if (!bootstrapped.value) return
    const nextKeyword = routeSearchKeyword()
    if (!nextKeyword) return
    keyword.value = nextKeyword
    await runSearch()
    const nextQuery = { ...route.query } as Record<string, string | string[] | null | undefined>
    delete nextQuery.q
    void router.replace({ path: route.path, query: nextQuery })
  },
)

watch(
  () => [route.query.feed, route.query.channel] as const,
  async () => {
    if (!bootstrapped.value) return
    const nextMode = resolveFeedModeFromRoute()
    const nextChannel = resolveChannelFromRoute()
    if (nextMode === activeFeedMode.value && nextChannel === activeChannel.value) return
    activeFeedMode.value = nextMode
    activeChannel.value = nextChannel
    await reloadFeedByScope()
  },
)

watch(
  () => [searchResultsTab.value, searchedUsers.value.length, authStore.currentUser?.id] as const,
  ([tab, count, userId]) => {
    if (tab !== 'users' || count === 0 || !userId) return
    void loadFollowSet()
  },
)

watch(
  () => authStore.currentUser?.id,
  (nextId, previousId) => {
    if (nextId === previousId) return
    followingAuthorIds.value = new Set()
    friendAuthorIds.value = new Set()
    followSetResolvedUserId.value = null
    friendSetResolvedUserId.value = null
    resetFeedRequestAuthMode()
    void loadUserInterests()
    if (!bootstrapped.value) return
    if (!isSearchResults.value) {
      refreshFeedSeed()
      resetFeedState()
      void loadInitialFeed()
    }
  },
)

onMounted(async () => {
  resetFeedRequestAuthMode()
  loadWatchLater()
  void loadUserInterests()
  void loadFollowSet()
  const hotTopicsTask = loadHotTopics()
  await loadFeedChannels()
  applyRouteFeedState()
  updateColumnCount()
  const navType = navigationType()
  const shouldRealtimeRefresh = consumeRealtimeRefreshFlag()
  const initialKeyword = routeSearchKeyword()
  if (initialKeyword) {
    keyword.value = initialKeyword
    await runSearch()
    const nextQuery = { ...route.query } as Record<string, string | string[] | null | undefined>
    delete nextQuery.q
    void router.replace({ query: nextQuery })
  } else if (shouldRealtimeRefresh) {
    refreshFeedSeed()
    resetFeedState()
    await loadInitialFeed()
    window.scrollTo({ top: 0, behavior: 'auto' })
  } else {
    const restored = shouldUseFeedCacheOnEntry(navType) && restoreFeedFromCache()
    if (!restored) {
      await loadInitialFeed()
    } else {
      void loadInitialFeed()
    }
  }
  bootstrapped.value = true
  await nextTick()
  setupInfiniteScroll()

  window.addEventListener('resize', updateColumnCount, { passive: true })
  window.addEventListener('scroll', handleWindowScroll, { passive: true })
  window.addEventListener('scroll', handleFeedInfiniteScroll, { passive: true })

  const savedScrollY = Number(sessionStorage.getItem(FEED_SCROLL_Y_KEY) || '0')
  const shouldRestoreScroll = shouldUseFeedCacheOnEntry(navType)
  if (shouldRestoreScroll && savedScrollY > 0) {
    requestAnimationFrame(() => {
      window.scrollTo({ top: savedScrollY, behavior: 'auto' })
    })
  } else if (navType === 'reload') {
    window.scrollTo({ top: 0, behavior: 'auto' })
  }
  sessionStorage.removeItem(FEED_SCROLL_RESTORE_KEY)
  sessionStorage.removeItem(FEED_SCROLL_Y_KEY)
  void hotTopicsTask
})

onActivated(() => {
  void refreshFeedAfterRealtimeSignals()
})

onUnmounted(() => {
  if (intersectionObserver) intersectionObserver.disconnect()
  clearFeedRevealTimer()
  clearBackgroundFeedTimer()
  if (loadMoreDebounceTimer !== null) clearTimeout(loadMoreDebounceTimer)
  window.removeEventListener('resize', updateColumnCount)
  window.removeEventListener('scroll', handleWindowScroll)
  window.removeEventListener('scroll', handleFeedInfiniteScroll)
})
</script>

<template>
  <div class="feed-home">
    <CommonLeftSidebar />

    <main class="feed-home__main">
      <section class="feed-home__content-panel">
        <div v-if="isGuestFallbackFeed" class="feed-home__fallback-banner">
          个性化推荐暂时较慢，当前展示探索流。
        </div>

        <section
          v-if="!isSearchResults"
          class="feed-home__channel-hero"
          :class="{ 'is-recommend': isForYouChannel }"
        >
          <template v-if="isForYouChannel">
            <img class="feed-home__hero-avatar" :src="recommendationAvatar" alt="" />
            <div class="feed-home__hero-copy">
              <span>为你整理</span>
              <h1>下午好，{{ recommendationName }}</h1>
              <p>根据你最近浏览的内容，为你优先整理摄影、宠物日常、校园生活和效率工具方向的推荐。</p>
              <div class="feed-home__interest-pills">
                <button
                  v-for="item in recommendationInterests"
                  :key="item.key"
                  type="button"
                  @click="selectChannel(item.key)"
                >
                  {{ item.label }}
                </button>
              </div>
            </div>
          </template>
          <template v-else>
            <img class="feed-home__hero-avatar" :src="currentChannelAvatar" alt="" />
            <div class="feed-home__hero-copy">
              <span>{{ currentChannelSignal }}</span>
              <h1>{{ currentChannelMeta.label }}</h1>
              <p>{{ currentChannelDescription }}</p>
            </div>
          </template>
          <button type="button" class="feed-home__publish-btn" @click="router.push('/publish')">发布内容</button>
        </section>

        <section v-if="!isSearchResults" class="feed-home__tabs-row">
          <div v-if="isForYouChannel" class="feed-home__tabs feed-home__tabs--recommend">
            <button
              v-for="item in recommendationTabs"
              :key="item.key"
              type="button"
              class="feed-home__tab"
              :class="{ 'is-active': item.mode ? activeFeedMode === item.mode : false }"
              @click="selectRecommendationTab(item)"
            >
              {{ item.label }}
            </button>
          </div>
          <div v-else class="feed-home__tabs">
            <button
              v-for="item in feedModeTabs"
              :key="item.key"
              type="button"
              class="feed-home__tab"
              :class="{ 'is-active': activeFeedMode === item.key }"
              @click="selectFeedMode(item.key)"
            >
              {{ item.label }}
            </button>
          </div>
          <div v-if="!isForYouChannel" class="feed-home__tabs feed-home__tabs--channel">
            <button
              v-for="item in channelTabs"
              :key="item.key"
              type="button"
              class="feed-home__tab feed-home__tab--channel"
              :class="{ 'is-active': activeChannel === item.key }"
              @click="selectChannel(item.key)"
            >
              {{ item.label }}
            </button>
          </div>
          <button type="button" class="feed-home__layout-btn" title="刷新" aria-label="刷新" @click="reloadFeedByScope">
            <el-icon><RefreshRight /></el-icon>
          </button>
        </section>

        <section v-if="isSearchResults" class="feed-home__search-result-head">
          <div>
            <el-icon>
              <Search />
            </el-icon>
            <span>搜索 “{{ displayQuery }}”</span>
          </div>
          <div class="feed-home__search-tabs" role="tablist">
            <button type="button" class="feed-home__search-tab" :class="{ 'is-active': searchResultsTab === 'posts' }"
              @click="searchResultsTab = 'posts'">
              图文
            </button>
            <button type="button" class="feed-home__search-tab" :class="{ 'is-active': searchResultsTab === 'users' }"
              @click="searchResultsTab = 'users'">
              用户
            </button>
          </div>
        </section>

        <template v-if="!isSearchResults">
          <div v-if="showInitialFeedSkeleton" class="feed-home__skeleton-grid"
            :style="{ '--column-count': String(columnCount) }">
            <div v-for="(column, columnIndex) in skeletonColumns" :key="`feed-skeleton-column-${columnIndex}`"
              class="feed-home__column">
              <div v-for="item in column" :key="item.key" class="feed-home__skeleton-card">
                <div class="feed-home__skeleton-media ui-skeleton" :style="{ aspectRatio: item.aspectRatio }" />
                <div class="feed-home__skeleton-body">
                  <span class="feed-home__skeleton-line ui-skeleton" />
                  <span class="feed-home__skeleton-line feed-home__skeleton-line--short ui-skeleton" />
                  <span class="feed-home__skeleton-line feed-home__skeleton-line--mini ui-skeleton" />
                </div>
              </div>
            </div>
          </div>

          <div v-else-if="feedError && posts.length === 0" class="ui-state ui-state--error feed-home__state">
            <p>{{ feedError }}</p>
            <button type="button" class="feed-home__state-btn" @click="loadInitialFeed">重试</button>
          </div>

          <div v-else-if="posts.length > 0" class="feed-home__stream">
            <section class="feed-home__waterfall" :style="{ '--column-count': String(columnCount) }">
              <div v-for="(column, columnIndex) in masonryColumns" :key="`col-${columnIndex}`"
                class="feed-home__column">
                <FeedCardRenderer
                  v-for="post in column"
                  :key="post.id"
                  :post="post"
                  :is-liked="isPostLiked(post.id)"
                  :is-liking="isPostLiking(post.id)"
                  :like-count="displayedLikeCount(post)"
                  @open="navigateToPost"
                  @like="handleTogglePostLike"
                />
              </div>
            </section>

            <div ref="loadMoreTriggerRef" class="feed-home__sentinel" aria-hidden="true" />

            <div v-if="showProgressiveLoading && posts.length > 0" class="feed-home__loading-more">
              <div class="feed-home__loading-dots"><span /><span /><span /></div>
              <span>正在为你加载更多内容...</span>
            </div>
            <p v-else-if="!feedHasMore && posts.length > 0" class="feed-home__ending">
              {{ posts.length >= FEED_MAX_ITEMS ? '已展示 1000 条内容' : '已经到底了' }}
            </p>
          </div>

          <div v-else class="ui-state ui-state--empty feed-home__state">{{ emptyFeedMessage }}</div>
        </template>

        <template v-else-if="searchResultsTab === 'posts'">
          <div v-if="searching"><el-skeleton animated :rows="10" /></div>
          <div v-else class="feed-home__stream">
            <section class="feed-home__waterfall" :style="{ '--column-count': String(columnCount) }">
              <div v-for="(column, columnIndex) in searchedMasonryColumns" :key="`search-col-${columnIndex}`"
                class="feed-home__column">
                <FeedCardRenderer
                  v-for="post in column"
                  :key="post.id"
                  :post="post"
                  :is-liked="isPostLiked(post.id)"
                  :is-liking="isPostLiking(post.id)"
                  :like-count="displayedLikeCount(post)"
                  @open="navigateToPost"
                  @like="handleTogglePostLike"
                />
              </div>
            </section>
            <div v-if="searchedPosts.length === 0" class="ui-state ui-state--empty feed-home__state">
              没有找到与“{{ displayQuery }}”相关的图文
            </div>
          </div>
        </template>

        <template v-else>
          <div v-if="searching"><el-skeleton animated :rows="10" /></div>
          <div v-else class="feed-home__users">
            <article v-for="user in searchedUsers" :key="user.id" class="feed-home__user-card"
              @click="router.push(`/users/${user.id}`)">
              <img class="feed-home__user-avatar" :src="normalizeMediaUrl(user.avatarUrl) || '/auto_picture.png'"
                alt="" />
              <div class="feed-home__user-meta">
                <div class="feed-home__user-name">{{ user.nickname }}</div>
                <div class="feed-home__user-id">@{{ user.username }}</div>
                <p v-if="user.bio" class="feed-home__user-bio">{{ user.bio }}</p>
              </div>
              <div class="feed-home__user-actions" @click.stop>
                <el-button v-if="authStore.currentUser && authStore.currentUser.id !== user.id" size="small" round
                  :type="followingAuthorIds.has(user.id) ? 'default' : 'primary'" @click="handleToggleFollow(user.id)">
                  {{ followingAuthorIds.has(user.id) ? '已关注' : '关注' }}
                </el-button>
                <el-button size="small" round plain @click="router.push(`/users/${user.id}`)">主页</el-button>
              </div>
            </article>
            <div v-if="searchedUsers.length === 0" class="ui-state ui-state--empty feed-home__state">
              没有找到与“{{ displayQuery }}”相关的用户
            </div>
          </div>
        </template>
      </section>
    </main>

    <aside class="feed-home__right-rail" aria-label="推荐信息">
      <template v-if="isForYouChannel">
        <section class="feed-home__right-card">
          <div class="feed-home__right-title">
            <strong>你的兴趣画像</strong>
            <button type="button" @click="openInterestDialog">编辑</button>
          </div>
          <div class="feed-home__interest-profile">
            <button
              v-for="item in displayedInterestFacets"
              :key="`profile-${interestKey(item)}`"
              type="button"
              @click="openInterestFacet(item)"
            >
              <el-icon><CollectionTag /></el-icon>
              {{ item.facetLabel }}
            </button>
          </div>
          <p class="feed-home__right-note">{{ interestProfileNote }}</p>
        </section>

        <section class="feed-home__right-card">
          <div class="feed-home__right-title">
            <strong>热门标签</strong>
            <button type="button">更多 <el-icon><ArrowRight /></el-icon></button>
          </div>
          <ol class="feed-home__topic-list">
            <li v-for="(topic, index) in hotTopicRows" :key="topic.name" @click="router.push({ path: '/search', query: { q: topic.name } })">
              <span>{{ index + 1 }}</span>
              <strong># {{ topic.name }}</strong>
              <em>{{ formatTopicHeat(topic) }}</em>
            </li>
            <li v-if="hotTopicRows.length === 0">
              <span>1</span>
              <strong># 校园生活</strong>
              <em>初始化中</em>
            </li>
          </ol>
        </section>
      </template>

      <section v-else class="feed-home__right-card">
        <div class="feed-home__right-title">
          <strong>人群频道</strong>
          <button type="button">更多 <el-icon>
              <ArrowRight />
            </el-icon></button>
        </div>
        <ol class="feed-home__topic-list">
          <li v-for="(segment, index) in audienceSegments" :key="segment.key"
            :class="{ 'is-active': segment.key === activeChannel }" @click="router.push(`/channels/${segment.key}`)">
            <span>{{ index + 1 }}</span>
            <strong>{{ segment.label }}</strong>
            <em>{{ segment.signal }}</em>
          </li>
        </ol>
      </section>

      <section class="feed-home__right-card">
        <div class="feed-home__right-title">
          <strong>推荐创作者</strong>
          <button type="button" @click="reloadFeedByScope">换一换</button>
        </div>
        <div class="feed-home__creator-list">
          <article v-for="(creator, index) in recommendedCreators" :key="creator.user.id">
            <img :src="creatorAvatar(index)" alt="" @click="router.push(`/users/${creator.user.id}`)" />
            <span @click="router.push(`/users/${creator.user.id}`)">
              <strong>{{ creator.user.nickname || creator.user.username }}</strong>
              <small>{{ creator.bio }} · {{ creator.postCount }} 篇</small>
            </span>
            <button
              type="button"
              :class="{ 'is-following': followingAuthorIds.has(creator.user.id) }"
              @click="handleToggleFollow(creator.user.id)"
            >
              {{ followingAuthorIds.has(creator.user.id) ? '已关注' : '关注' }}
            </button>
          </article>
          <p v-if="recommendedCreators.length === 0" class="feed-home__module-empty">推荐流加载后会展示适合关注的创作者</p>
        </div>
      </section>

      <section v-if="isForYouChannel" class="feed-home__right-card">
        <div class="feed-home__right-title">
          <strong>稍后再看</strong>
          <button type="button" @click="addWatchLater()">
            添加 <el-icon><CirclePlus /></el-icon>
          </button>
        </div>
        <div class="feed-home__watch-list">
          <article v-for="item in watchLaterRows" :key="item.postId" @click="openWatchLater(item)">
            <img :src="item.image" alt="" />
            <span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.meta }}</small>
            </span>
            <button
              v-if="watchLaterItems.some((saved) => saved.postId === item.postId)"
              type="button"
              aria-label="移除稍后再看"
              @click.stop="removeWatchLater(item.postId)"
            >
              <el-icon><Close /></el-icon>
            </button>
          </article>
          <p v-if="watchLaterRows.length === 0" class="feed-home__module-empty">还没有可稍后再看的内容</p>
        </div>
      </section>

      <footer class="feed-home__footer-card">
        关于 Vibelo · 帮助中心 · 隐私政策<br />
        用户协议 · 内容规范 · 招聘信息<br />
        © 2024 Vibelo，连接每一种热爱
      </footer>
    </aside>

    <el-dialog v-model="interestDialogVisible" class="feed-home__interest-dialog" title="编辑兴趣画像" width="520px">
      <div class="feed-home__interest-picker">
        <button
          v-for="facet in interestCandidateFacets"
          :key="`candidate-${interestKey(facet)}`"
          type="button"
          :class="{ 'is-selected': selectedInterestKeys.has(interestKey(facet)) }"
          @click="toggleInterestFacet(facet)"
        >
          # {{ facet.facetLabel }}
        </button>
      </div>
      <p class="feed-home__right-note">这些兴趣会写入账号画像，并影响“为你推荐”的召回和排序。</p>
      <template #footer>
        <el-button @click="interestDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="interestSaving" @click="saveInterestProfile">保存</el-button>
      </template>
    </el-dialog>

    <Transition name="feed-backtop">
      <button v-if="showBackTop" type="button" class="feed-home__backtop" title="回到顶部" aria-label="回到顶部"
        @click="scrollToTop">
        ↑
      </button>
    </Transition>
  </div>
</template>

<style scoped>
.feed-home {
  position: relative;
  display: block;
  width: 100%;
  max-width: none;
  min-height: calc(100vh - 74px);
  margin: 0;
  padding: 14px 16px 40px 246px;
  color: #20242f;
  background: #f7f8fa;
}

.feed-home button {
  font: inherit;
}

.feed-home__left-rail,
.feed-home__right-rail {
  position: fixed;
  top: 90px;
  z-index: 20;
  max-height: calc(100vh - 106px);
  overflow-y: auto;
  scrollbar-width: none;
}

.feed-home__right-rail::-webkit-scrollbar {
  display: none;
}

.feed-home__left-rail {
  left: 16px;
  width: 214px;
  min-height: calc(100vh - 112px);
  padding: 10px 8px;
  border: 1px solid rgba(26, 31, 44, 0.07);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(32, 36, 47, 0.06);
}

.feed-home__side-nav {
  display: grid;
  gap: 4px;
  padding-bottom: 14px;
  border-bottom: 1px solid #edf0f4;
}

.feed-home__side-item,
.feed-home__community-row,
.feed-home__create-community {
  width: 100%;
  border: none;
  background: transparent;
  cursor: pointer;
}

.feed-home__side-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 44px;
  padding: 0 12px;
  border-radius: 8px;
  color: #2f3441;
  font-size: 15px;
  font-weight: 650;
  text-align: left;
  transition: background 0.16s ease, color 0.16s ease, transform 0.16s ease;
}

.feed-home__side-item .el-icon {
  flex: 0 0 auto;
  font-size: 20px;
}

.feed-home__side-item em {
  margin-left: auto;
  padding: 2px 7px;
  border-radius: 999px;
  background: #fff1ed;
  color: #ff5a45;
  font-style: normal;
  font-size: 11px;
  font-weight: 800;
}

.feed-home__side-item:hover,
.feed-home__side-item.is-active {
  background: #fff0ed;
  color: #ff5a45;
}

.feed-home__side-item:active {
  transform: scale(0.98);
}

.feed-home__community {
  padding-top: 18px;
}

.feed-home__rail-title,
.feed-home__right-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.feed-home__rail-title span,
.feed-home__right-title strong {
  color: #20242f;
  font-size: 15px;
  font-weight: 800;
}

.feed-home__rail-title button,
.feed-home__right-title button {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  border: none;
  background: transparent;
  color: #8a91a0;
  font-size: 12px;
  cursor: pointer;
}

.feed-home__right-title button:hover {
  color: #ff5a45;
}

.feed-home__community-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  padding: 8px 2px;
  text-align: left;
}

.feed-home__community-row img,
.feed-home__creator-list img {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  object-fit: cover;
}

.feed-home__community-row span,
.feed-home__creator-list span,
.feed-home__card-author span {
  min-width: 0;
  display: grid;
  gap: 1px;
}

.feed-home__community-row strong,
.feed-home__creator-list strong,
.feed-home__card-author strong {
  overflow: hidden;
  color: #2a2f3b;
  font-size: 13px;
  font-weight: 760;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed-home__community-row small,
.feed-home__creator-list small,
.feed-home__card-author small {
  overflow: hidden;
  color: #9299a7;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed-home__community-row em {
  min-width: 22px;
  padding: 2px 5px;
  border-radius: 999px;
  background: #ff5a45;
  color: #fff;
  font-style: normal;
  font-size: 11px;
  font-weight: 800;
  text-align: center;
}

.feed-home__create-community {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 36px;
  margin-top: 10px;
  border-radius: 8px;
  background: #fff1ed;
  color: #ff5a45;
  font-size: 13px;
  font-weight: 760;
}

.feed-home__main {
  min-width: 0;
  margin-right: 308px;
}

.feed-home__content-panel,
.feed-home__right-card,
.feed-home__footer-card {
  border: 1px solid rgba(26, 31, 44, 0.07);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(32, 36, 47, 0.06);
}

.feed-home__content-panel {
  padding: 10px;
}

.feed-home__channel-hero {
  display: grid;
  grid-template-columns: 74px minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  min-height: 118px;
  margin-bottom: 12px;
  padding: 18px;
  border: 1px solid #edf0f4;
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(255, 90, 69, 0.10), rgba(72, 126, 255, 0.07)),
    #fff;
}

.feed-home__channel-hero.is-recommend {
  min-height: 144px;
  background:
    linear-gradient(135deg, rgba(255, 90, 69, 0.12), rgba(255, 202, 128, 0.12) 48%, rgba(72, 126, 255, 0.08)),
    #fff;
}

.feed-home__hero-avatar {
  width: 74px;
  height: 74px;
  border-radius: 8px;
  object-fit: cover;
  background: #f0f2f5;
}

.feed-home__hero-copy {
  min-width: 0;
}

.feed-home__hero-copy > span {
  color: #ff5a45;
  font-size: 12px;
  font-weight: 760;
}

.feed-home__hero-copy h1 {
  margin: 4px 0;
  color: #1f2531;
  font-size: 26px;
  font-weight: 840;
  letter-spacing: 0;
}

.feed-home__hero-copy p {
  margin: 0;
  color: #6d7481;
  font-size: 14px;
  line-height: 1.5;
}

.feed-home__interest-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.feed-home__interest-pills button {
  height: 30px;
  padding: 0 12px;
  border: 1px solid rgba(255, 90, 69, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: #ff5a45;
  cursor: pointer;
  font-size: 13px;
  font-weight: 720;
}

.feed-home__publish-btn {
  height: 36px;
  padding: 0 16px;
  border: none;
  border-radius: 8px;
  background: #ff5a45;
  color: #fff;
  cursor: pointer;
  font-size: 14px;
  font-weight: 760;
}

.feed-home__tabs-row {
  position: sticky;
  top: 74px;
  z-index: 30;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 0 0 12px;
  border-bottom: 1px solid #eef1f5;
  margin-bottom: 12px;
  background: #fff;
}

.feed-home__tabs {
  z-index: 29;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.feed-home__tabs::-webkit-scrollbar {
  display: none;
}

.feed-home__tab-block-title {
  color: #8c92a1;
  font-size: 12px;
  font-weight: 680;
  line-height: 1;
}

.feed-home__tabs--channel {
  gap: 8px;
}

.feed-home__tabs--recommend {
  grid-column: 1 / 3;
  gap: 22px;
  padding-left: 8px;
}

.feed-home__tab {
  flex: 0 0 auto;
  min-height: 32px;
  padding: 0 14px;
  border: 1px solid #e7eaf0;
  border-radius: 999px;
  background: #fff;
  color: #222733;
  font-size: 13px;
  font-weight: 690;
  cursor: pointer;
  transition: background 0.16s ease, border-color 0.16s ease, color 0.16s ease;
}

.feed-home__tab--channel {
  min-width: max-content;
  padding: 0 12px;
}

.feed-home__tab:hover,
.feed-home__tab.is-active {
  border-color: #ffd4cc;
  background: #fff1ed;
  color: #ff4f3b;
}

.feed-home__layout-btn {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid #e7eaf0;
  border-radius: 8px;
  background: #fff;
  color: #3c4250;
  cursor: pointer;
}

.feed-home__fallback-banner,
.feed-home__search-result-head {
  margin-bottom: 12px;
  border-radius: 8px;
  border: 1px solid rgba(255, 90, 69, 0.18);
  background: #fff7f5;
  color: #a83324;
}

.feed-home__fallback-banner {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 650;
}

.feed-home__search-result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 10px;
}

.feed-home__search-result-head>div:first-child {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: #2f3441;
  font-size: 14px;
  font-weight: 740;
}

.feed-home__search-tabs {
  display: inline-flex;
  gap: 6px;
  padding: 3px;
  border-radius: 999px;
  background: #fff;
}

.feed-home__search-tab {
  border: none;
  border-radius: 999px;
  padding: 6px 12px;
  background: transparent;
  color: #7f8796;
  cursor: pointer;
  font-size: 12px;
  font-weight: 760;
}

.feed-home__search-tab.is-active {
  background: #20242f;
  color: #fff;
}

.feed-home__stream {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feed-home__recommend-overview {
  display: grid;
  grid-template-columns: minmax(0, 1.75fr) minmax(260px, 0.95fr);
  gap: 12px;
}

.feed-home__recommend-overview :deep(.channel-card) {
  height: 100%;
}

.feed-home__recommend-lead :deep(.channel-card__image) {
  max-height: 360px;
}

.feed-home__recommend-stack {
  display: grid;
  gap: 12px;
}

.feed-home__recommend-stack article {
  display: grid;
  gap: 8px;
  min-height: 112px;
  padding: 16px;
  border: 1px solid #e9edf3;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  box-shadow: 0 8px 22px rgba(32, 36, 47, 0.045);
}

.feed-home__recommend-stack article:hover {
  border-color: rgba(255, 90, 69, 0.22);
}

.feed-home__recommend-stack span {
  color: #ff5a45;
  font-size: 12px;
  font-weight: 780;
}

.feed-home__recommend-stack strong {
  color: #222936;
  font-size: 15px;
  line-height: 1.42;
}

.feed-home__recommend-stack small {
  color: #8a91a0;
  font-size: 12px;
}

.feed-home__guess-section {
  display: grid;
  gap: 10px;
}

.feed-home__guess-section header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.feed-home__guess-section h2 {
  margin: 0;
  color: #1f2531;
  font-size: 18px;
  font-weight: 840;
}

.feed-home__guess-section button {
  border: none;
  background: transparent;
  color: #8a91a0;
  cursor: pointer;
  font-size: 12px;
}

.feed-home__guess-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.feed-home__guess-row :deep(.channel-card__desc),
.feed-home__guess-row :deep(.channel-card__meta) {
  display: none;
}

.feed-home__guess-row :deep(.channel-card__image) {
  min-height: 118px;
  max-height: 158px;
}

.feed-home__waterfall,
.feed-home__skeleton-grid {
  display: grid;
  grid-template-columns: repeat(var(--column-count), minmax(0, 1fr));
  gap: 10px;
  align-items: start;
}

.feed-home__column {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.feed-home__card {
  overflow: hidden;
  border: 1px solid #e8ebf0;
  border-radius: 16px;
  background: #fff;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(32, 36, 47, 0.05);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.feed-home__card:hover {
  transform: translateY(-2px);
  border-color: rgba(255, 90, 69, 0.22);
  box-shadow: 0 16px 34px rgba(32, 36, 47, 0.09);
}

.feed-home__card-media {
  position: relative;
  display: grid;
  overflow: hidden;
  border-radius: 15px 15px 0 0;
  background: #f0f2f5;
}

.feed-home__card-skeleton,
.feed-home__card-image {
  grid-area: 1 / 1;
}

.feed-home__card-skeleton {
  width: 100%;
}

.feed-home__card-image {
  display: block;
  width: 100%;
  height: auto;
  object-fit: cover;
  opacity: 0;
  transition: opacity 0.24s ease, transform 0.36s ease;
}

.feed-home__card:hover .feed-home__card-image {
  transform: scale(1.015);
}

.feed-home__card-image.is-visible {
  opacity: 1;
}

.feed-home__card-body {
  padding: 12px 16px 14px;
}

.feed-home__card-author {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}

.feed-home__card-author img {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
}

.feed-home__action-btn {
  border: none;
  background: transparent;
  color: #6f7582;
  cursor: pointer;
}

.feed-home__card-author strong {
  font-size: 15px;
}

.feed-home__card-author small {
  font-size: 13px;
}

.feed-home__card-body h3 {
  display: -webkit-box;
  margin: 14px 0 0;
  overflow: hidden;
  color: #3d4350;
  font-size: 17px;
  font-weight: 760;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.feed-home__card-desc {
  display: -webkit-box;
  margin: 7px 0 0;
  overflow: hidden;
  color: #5f6674;
  font-size: 14px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  word-break: break-word;
}

.feed-home__card-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 12px;
}

.feed-home__action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  min-height: 28px;
  padding: 0;
  color: #6f7582;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.feed-home__action-btn:disabled {
  cursor: wait;
  opacity: 0.66;
}

.feed-home__action-glyph {
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  font-size: 18px;
  line-height: 1;
}

.feed-home__heart-icon {
  font-size: 22px;
  font-weight: 500;
}

.feed-home__action-btn.is-liked,
.feed-home__action-btn:first-child {
  color: #ff5a45;
}

.feed-home__skeleton-card {
  overflow: hidden;
  width: 100%;
  border: 1px solid #e8ebf0;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 6px 18px rgba(32, 36, 47, 0.05);
}

.feed-home__skeleton-media {
  width: 100%;
  min-height: 132px;
  border-radius: 12px 12px 0 0;
}

.feed-home__skeleton-body {
  display: grid;
  gap: 10px;
  padding: 12px 14px 14px;
}

.feed-home__skeleton-line {
  display: block;
  width: 84%;
  height: 14px;
  border-radius: 999px;
}

.feed-home__skeleton-line--short {
  width: 62%;
}

.feed-home__skeleton-line--mini {
  width: 42%;
  height: 12px;
}

.feed-home__sentinel {
  height: 1px;
  margin-top: 12px;
}

.feed-home__loading-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 22px 0 8px;
  color: #8a91a0;
  font-size: 13px;
}

.feed-home__loading-dots {
  display: flex;
  gap: 6px;
}

.feed-home__loading-dots span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #ff5a45;
  animation: feed-dot-bounce 0.9s ease-in-out infinite both;
}

.feed-home__loading-dots span:nth-child(2) {
  animation-delay: 0.15s;
}

.feed-home__loading-dots span:nth-child(3) {
  animation-delay: 0.3s;
}

.feed-home__ending {
  margin: 0;
  padding: 18px 0 2px;
  text-align: center;
  color: #8a91a0;
  font-size: 12px;
}

.feed-home__right-rail {
  display: grid;
  gap: 14px;
  right: 16px;
  width: 292px;
}

.feed-home__right-card,
.feed-home__footer-card {
  padding: 16px;
}

.feed-home__topic-list {
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.feed-home__topic-list li {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.16s ease;
}

.feed-home__topic-list li:hover,
.feed-home__topic-list li.is-active {
  background: #fff1ed;
}

.feed-home__topic-list span {
  color: #ff7a26;
  font-size: 14px;
  font-weight: 820;
}

.feed-home__topic-list li:nth-child(n + 4) span {
  color: #8a91a0;
}

.feed-home__topic-list strong {
  overflow: hidden;
  color: #2f3441;
  font-size: 13px;
  font-weight: 720;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed-home__topic-list em {
  color: #8a91a0;
  font-style: normal;
  font-size: 12px;
  white-space: nowrap;
}

.feed-home__interest-profile {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.feed-home__interest-profile button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 10px;
  border: 1px solid rgba(255, 90, 69, 0.13);
  border-radius: 8px;
  background: linear-gradient(180deg, #fff7f5 0%, #fff 100%);
  color: #ff5a45;
  cursor: pointer;
  font-size: 12px;
  font-weight: 760;
  transition: transform 0.16s ease, box-shadow 0.16s ease, border-color 0.16s ease;
}

.feed-home__interest-profile button:hover {
  transform: translateY(-1px);
  border-color: rgba(255, 90, 69, 0.26);
  box-shadow: 0 8px 18px rgba(255, 90, 69, 0.10);
}

.feed-home__right-note {
  margin: 10px 0 0;
  color: #9aa1ad;
  font-size: 12px;
  line-height: 1.5;
}

.feed-home__creator-list {
  display: grid;
  gap: 12px;
}

.feed-home__creator-list article {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
}

.feed-home__creator-list img,
.feed-home__creator-list span,
.feed-home__watch-list article {
  cursor: pointer;
}

.feed-home__creator-list button {
  height: 30px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: #fff0ed;
  color: #ff4f3b;
  font-size: 13px;
  font-weight: 760;
  cursor: pointer;
}

.feed-home__creator-list button:hover {
  box-shadow: 0 8px 18px rgba(255, 90, 69, 0.12);
}

.feed-home__creator-list button.is-following {
  border-color: #dfe5ee;
  background: #f7f8fa;
  color: #687182;
}

.feed-home__live-card article {
  display: grid;
  grid-template-columns: 94px minmax(0, 1fr);
  gap: 12px;
}

.feed-home__live-cover {
  position: relative;
  overflow: hidden;
  height: 90px;
  border-radius: 8px;
  background: #eef1f5;
}

.feed-home__live-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.feed-home__live-cover span {
  position: absolute;
  right: 6px;
  bottom: 6px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 6px;
  border-radius: 5px;
  background: #ff4f3b;
  color: #fff;
  font-size: 10px;
  font-weight: 820;
}

.feed-home__live-card strong {
  display: block;
  margin: 3px 0 4px;
  color: #20242f;
  font-size: 14px;
  font-weight: 780;
  line-height: 1.35;
}

.feed-home__live-card small,
.feed-home__live-card em {
  display: block;
  color: #8a91a0;
  font-style: normal;
  font-size: 12px;
  line-height: 1.45;
}

.feed-home__live-card em {
  margin-top: 8px;
  color: #ff4f3b;
  font-weight: 760;
}

.feed-home__watch-list {
  display: grid;
  gap: 12px;
}

.feed-home__watch-list article {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  border-radius: 8px;
  transition: background 0.16s ease;
}

.feed-home__watch-list article:hover {
  background: #f7f8fb;
}

.feed-home__watch-list img {
  width: 72px;
  height: 52px;
  border-radius: 8px;
  object-fit: cover;
  background: #eef1f5;
}

.feed-home__watch-list span {
  min-width: 0;
}

.feed-home__watch-list strong {
  display: block;
  overflow: hidden;
  color: #2f3441;
  font-size: 13px;
  font-weight: 760;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed-home__watch-list small {
  color: #8a91a0;
  font-size: 12px;
}

.feed-home__watch-list button {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #9aa1ad;
  cursor: pointer;
}

.feed-home__watch-list button:hover {
  background: #fff1ed;
  color: #ff5a45;
}

.feed-home__module-empty {
  margin: 0;
  color: #9aa1ad;
  font-size: 12px;
  line-height: 1.5;
}

.feed-home__interest-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
}

.feed-home__interest-picker button {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid #e6eaf1;
  border-radius: 999px;
  background: #fff;
  color: #4f5868;
  cursor: pointer;
  font-size: 13px;
  font-weight: 720;
}

.feed-home__interest-picker button:hover,
.feed-home__interest-picker button.is-selected {
  border-color: #ffd1c8;
  background: #fff1ed;
  color: #ff4f3b;
}

.feed-home__footer-card {
  color: #8a91a0;
  font-size: 12px;
  line-height: 1.9;
}

.feed-home__users {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.feed-home__user-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid #ebedf2;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.feed-home__user-avatar {
  width: 54px;
  height: 54px;
  border-radius: 8px;
  object-fit: cover;
}

.feed-home__user-meta {
  min-width: 0;
}

.feed-home__user-name {
  color: #20242f;
  font-size: 15px;
  font-weight: 800;
}

.feed-home__user-id,
.feed-home__user-bio {
  color: #8a91a0;
  font-size: 12px;
}

.feed-home__user-bio {
  margin: 5px 0 0;
}

.feed-home__user-actions {
  display: flex;
  gap: 8px;
}

.feed-home__state {
  margin-top: 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.feed-home__state p {
  margin: 0;
}

.feed-home__state-btn {
  border: none;
  border-radius: 8px;
  background: #20242f;
  color: #fff;
  padding: 8px 14px;
  font-weight: 700;
  cursor: pointer;
}

.feed-home__backtop {
  position: fixed;
  right: 22px;
  bottom: 24px;
  z-index: 40;
  width: 46px;
  height: 46px;
  border: none;
  border-radius: 50%;
  background: #20242f;
  color: #fff;
  font-size: 20px;
  cursor: pointer;
  box-shadow: 0 20px 36px rgba(15, 23, 42, 0.24);
}

@keyframes feed-dot-bounce {

  0%,
  80%,
  100% {
    transform: scale(0.6);
    opacity: 0.4;
  }

  40% {
    transform: scale(1);
    opacity: 1;
  }
}

@media (max-width: 1280px) {
  .feed-home {
    padding-left: 222px;
  }

  .feed-home__right-rail {
    display: none;
  }

  .feed-home__left-rail {
    width: 190px;
  }

  .feed-home__main {
    margin-right: 0;
  }
}

@media (max-width: 900px) {
  .feed-home {
    padding: 12px 10px 88px;
  }

  .feed-home__left-rail {
    position: static;
    width: auto;
    max-height: none;
    overflow: visible;
    min-height: unset;
    padding: 8px;
  }

  .feed-home__main {
    margin-right: 0;
  }

  .feed-home__recommend-overview {
    grid-template-columns: minmax(0, 1fr);
  }

  .feed-home__guess-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .feed-home__side-nav {
    grid-auto-flow: column;
    grid-auto-columns: max-content;
    overflow-x: auto;
    padding-bottom: 0;
    border-bottom: none;
    scrollbar-width: none;
  }

  .feed-home__side-nav::-webkit-scrollbar {
    display: none;
  }

  .feed-home__side-item {
    min-height: 38px;
    white-space: nowrap;
  }

  .feed-home__community {
    display: none;
  }

  .feed-home__tabs-row {
    top: 62px;
  }

  .feed-home__channel-row {
    top: 114px;
  }

  .feed-home__user-card {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .feed-home__user-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }

  .feed-home__backtop {
    bottom: 84px;
  }
}

@media (max-width: 640px) {
  .feed-home__channel-hero {
    grid-template-columns: 52px minmax(0, 1fr);
    gap: 12px;
    min-height: 0;
    padding: 14px;
  }

  .feed-home__hero-avatar {
    width: 52px;
    height: 52px;
  }

  .feed-home__hero-copy h1 {
    font-size: 20px;
  }

  .feed-home__publish-btn {
    grid-column: 1 / -1;
    width: 100%;
  }

  .feed-home__interest-pills {
    grid-column: 1 / -1;
  }

  .feed-home__guess-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .feed-home__card-body {
    padding: 11px 14px 13px;
  }

  .feed-home__card-author {
    grid-template-columns: 38px minmax(0, 1fr);
  }

  .feed-home__card-author img {
    width: 38px;
    height: 38px;
  }

  .feed-home__card-body h3 {
    font-size: 16px;
  }

  .feed-home__card-desc {
    font-size: 13px;
  }

  .feed-home__card-actions {
    gap: 14px;
  }

  .feed-home__heart-icon {
    font-size: 23px;
  }

  .feed-home__content-panel {
    padding: 8px;
  }

  .feed-home__tabs-row {
    align-items: flex-start;
  }

  .feed-home__channel-row {
    top: 108px;
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .feed-home__tab {
    min-height: 32px;
    padding: 0 13px;
    font-size: 13px;
  }

  .feed-home__search-result-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .feed-home__state {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
