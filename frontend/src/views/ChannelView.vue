<script setup lang="ts">
defineOptions({ name: 'ChannelView' })

import { ArrowLeft, EditPen, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FeedCardRenderer from '../components/feed/FeedCardRenderer.vue'
import { api, type ChannelView as ChannelInfo, type TopicView } from '../services/api'
import type { PostView } from '../types'

type SortMode = 'hot' | 'latest'

const route = useRoute()
const router = useRouter()

const channel = ref<ChannelInfo | null>(null)
const topics = ref<TopicView[]>([])
const posts = ref<PostView[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')
const page = ref(1)
const total = ref(0)
const sortMode = ref<SortMode>('hot')
const selectedTopicSlug = ref('')
const columnCount = ref(5)

const channelCode = computed(() => String(route.params.code || ''))
const hasMore = computed(() => posts.value.length < total.value)
const channelTitle = computed(() => channel.value?.name || '频道')
const channelDescription = computed(() => channel.value?.description || '正在生长的中文内容频道')
const selectedTopicName = computed(() => topics.value.find((topic) => topic.slug === selectedTopicSlug.value)?.name || '')
const masonryColumns = computed(() => distributeIntoColumns(posts.value))

watch(channelCode, () => {
  selectedTopicSlug.value = ''
  void loadChannelPage()
})

watch([sortMode, selectedTopicSlug], () => {
  void loadPosts(true)
})

onMounted(() => {
  updateColumnCount()
  window.addEventListener('resize', updateColumnCount)
  void loadChannelPage()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateColumnCount)
})

function formatCount(value?: number | null) {
  const count = Number(value || 0)
  if (count >= 10000) return `${(count / 10000).toFixed(1)}万`
  return String(Math.max(0, count))
}

function openPost(post: PostView) {
  void router.push(`/posts/${post.id}`)
}

function toggleTopicFilter(topic: TopicView) {
  selectedTopicSlug.value = selectedTopicSlug.value === topic.slug ? '' : topic.slug
}

function clearTopicFilter() {
  selectedTopicSlug.value = ''
}

function computeColumnCount() {
  if (typeof window === 'undefined') return 5
  const width = window.innerWidth
  if (width >= 1680) return 6
  if (width >= 1360) return 5
  if (width >= 1080) return 4
  if (width >= 780) return 3
  if (width >= 540) return 2
  return 1
}

function updateColumnCount() {
  columnCount.value = computeColumnCount()
}

function currentColumnWidth() {
  if (typeof window === 'undefined') return 252
  const pageWidth = Math.min(window.innerWidth - 56, 1580)
  const contentPadding = 32
  const gapTotal = Math.max(0, columnCount.value - 1) * 14
  return Math.max(180, (pageWidth - contentPadding - gapTotal) / Math.max(1, columnCount.value))
}

function estimatePostHeight(post: PostView) {
  const width = currentColumnWidth()
  const asset = post.assets?.[0] || post.images?.[0]
  const assetWidth = Number(asset?.width || 0)
  const assetHeight = Number(asset?.height || 0)
  const ratio = assetWidth > 0 && assetHeight > 0
    ? assetHeight / assetWidth
    : 1.28 + (post.id % 5) * 0.18
  const mediaHeight = asset ? Math.min(420, Math.max(132, width * ratio)) : 0
  const titleLines = Math.min(2, Math.max(1, Math.ceil((post.title || '').length / 16)))
  const contentLines = post.content ? Math.min(3, Math.ceil(post.content.length / 28)) : 0
  const tagRows = Math.ceil(Math.min(post.tags?.length || 0, 4) / 3)
  return mediaHeight + 88 + titleLines * 24 + contentLines * 20 + tagRows * 22
}

function distributeIntoColumns(items: PostView[]) {
  const count = Math.max(1, columnCount.value)
  const columns: PostView[][] = Array.from({ length: count }, () => [])
  const heights = Array.from({ length: count }, () => 0)
  for (const item of items) {
    let targetIndex = 0
    for (let i = 1; i < count; i++) {
      if (heights[i] < heights[targetIndex]) targetIndex = i
    }
    columns[targetIndex].push(item)
    heights[targetIndex] += estimatePostHeight(item)
  }
  return columns
}

async function loadChannelPage() {
  if (!channelCode.value) return
  loading.value = true
  error.value = ''
  try {
    const [channelData, topicData] = await Promise.all([
      api.channelDetail(channelCode.value),
      api.channelTopics(channelCode.value, 30),
    ])
    channel.value = channelData
    topics.value = topicData
    await loadPosts(true)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '频道加载失败'
  } finally {
    loading.value = false
  }
}

async function loadPosts(reset = false) {
  if (!channelCode.value) return
  if (reset) {
    page.value = 1
    posts.value = []
  }
  loadingMore.value = true
  try {
    const data = await api.channelPosts(channelCode.value, page.value, 24, sortMode.value, selectedTopicSlug.value)
    total.value = Number(data.total || 0)
    posts.value = reset ? data.records : [...posts.value, ...data.records]
    page.value += 1
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '内容加载失败')
  } finally {
    loadingMore.value = false
  }
}
</script>

<template>
  <div class="channel-page">
    <header class="channel-page__hero">
      <button type="button" class="channel-page__back" @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <div class="channel-page__avatar">
        <img v-if="channel?.icon" :src="channel.icon" alt="" />
        <span v-else>{{ channelTitle.slice(0, 1) }}</span>
      </div>
      <div class="channel-page__copy">
        <span>内容频道</span>
        <h1>{{ channelTitle }}</h1>
        <p>{{ channelDescription }}</p>
        <div>
          <em>{{ formatCount(total) }} 篇内容</em>
          <em>{{ topics.length }} 个标签</em>
        </div>
      </div>
      <button type="button" class="channel-page__publish" @click="router.push({ path: '/publish', query: { channel: channelCode } })">
        <el-icon><EditPen /></el-icon>
        参与发布
      </button>
    </header>

    <main class="channel-page__body">
      <section class="channel-page__topics">
        <div class="channel-page__section-head">
          <h2>频道标签</h2>
          <button type="button" @click="loadChannelPage">
            <el-icon><RefreshRight /></el-icon>
          </button>
        </div>
        <div class="channel-page__topic-row">
          <button
            v-for="topic in topics"
            :key="topic.id"
            type="button"
            :class="{ 'is-active': selectedTopicSlug === topic.slug }"
            @click="toggleTopicFilter(topic)"
          >
            <strong>#{{ topic.name }}</strong>
            <span>{{ formatCount(topic.postCount) }} 篇</span>
          </button>
          <p v-if="!loading && topics.length === 0">这个频道还没有标签</p>
        </div>
      </section>

      <section class="channel-page__content">
        <div class="channel-page__section-head">
          <h2>频道内容</h2>
          <button v-if="selectedTopicSlug" type="button" class="channel-page__filter-clear" @click="clearTopicFilter">
            #{{ selectedTopicName }} ×
          </button>
          <div class="channel-page__sort">
            <button type="button" :class="{ 'is-active': sortMode === 'hot' }" @click="sortMode = 'hot'">热门</button>
            <button type="button" :class="{ 'is-active': sortMode === 'latest' }" @click="sortMode = 'latest'">最新</button>
          </div>
        </div>

        <div v-if="loading" class="channel-page__state">
          <el-skeleton animated :rows="8" />
        </div>
        <div v-else-if="error" class="channel-page__state channel-page__state--error">
          <p>{{ error }}</p>
          <button type="button" @click="loadChannelPage">重试</button>
        </div>
        <div v-else-if="posts.length === 0" class="channel-page__state">
          <p>这个频道暂时还没有内容</p>
        </div>
        <div v-else class="channel-page__waterfall" :style="{ '--column-count': String(columnCount) }">
          <div v-for="(column, columnIndex) in masonryColumns" :key="`channel-col-${columnIndex}`" class="channel-page__column">
            <FeedCardRenderer v-for="post in column" :key="post.id" :post="post" @open="openPost" />
          </div>
        </div>

        <button v-if="hasMore" type="button" class="channel-page__more" :disabled="loadingMore" @click="loadPosts(false)">
          {{ loadingMore ? '加载中...' : '加载更多' }}
        </button>
      </section>
    </main>
  </div>
</template>

<style scoped>
.channel-page {
  --channel-page-max-width: 1580px;
  min-height: calc(100vh - 74px);
  padding: 18px clamp(14px, 2vw, 28px) 48px;
  color: #20242f;
  background: #f7f8fa;
}

.channel-page button {
  font: inherit;
}

.channel-page__hero,
.channel-page__topics,
.channel-page__content {
  width: 100%;
  max-width: var(--channel-page-max-width);
  margin: 0 auto;
  border: 1px solid rgba(26, 31, 44, 0.07);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(32, 36, 47, 0.06);
}

.channel-page__hero {
  display: grid;
  grid-template-columns: auto 82px minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  min-height: 152px;
  padding: 22px;
}

.channel-page__back,
.channel-page__section-head button {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 8px;
  background: #f4f6f9;
  color: #495365;
  cursor: pointer;
}

.channel-page__avatar {
  display: grid;
  place-items: center;
  width: 82px;
  height: 82px;
  border-radius: 18px;
  background: #fff0ed;
  color: #ff5a45;
  font-size: 32px;
  font-weight: 900;
}

.channel-page__avatar img {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}

.channel-page__copy {
  min-width: 0;
}

.channel-page__copy > span {
  color: #ff5a45;
  font-size: 13px;
  font-weight: 800;
}

.channel-page__copy h1 {
  margin: 4px 0;
  font-size: 30px;
  line-height: 1.2;
}

.channel-page__copy p {
  margin: 0;
  color: #6d7481;
  line-height: 1.55;
}

.channel-page__copy div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.channel-page__copy em {
  padding: 5px 9px;
  border-radius: 999px;
  background: #f4f6f9;
  color: #626b7a;
  font-style: normal;
  font-size: 12px;
}

.channel-page__publish {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 15px;
  border: none;
  border-radius: 8px;
  background: #ff5a45;
  color: #fff;
  cursor: pointer;
  font-weight: 780;
}

.channel-page__body {
  display: grid;
  gap: 14px;
  margin-top: 14px;
}

.channel-page__topics,
.channel-page__content {
  padding: 16px;
}

.channel-page__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.channel-page__section-head h2 {
  margin: 0;
  font-size: 18px;
}

.channel-page__topic-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.channel-page__topic-row button {
  flex: 0 0 auto;
  display: grid;
  gap: 3px;
  min-width: 128px;
  padding: 10px 12px;
  border: 1px solid #e8ebf0;
  border-radius: 8px;
  background: #fbfcfe;
  color: #303744;
  cursor: pointer;
  text-align: left;
}

.channel-page__topic-row button.is-active {
  border-color: rgba(255, 90, 69, 0.28);
  background: #fff1ed;
  color: #ff4f3b;
}

.channel-page__topic-row span {
  color: #8a91a0;
  font-size: 12px;
}

.channel-page__filter-clear {
  height: 30px;
  margin-left: auto;
  padding: 0 10px;
  border: 1px solid rgba(255, 90, 69, 0.22);
  border-radius: 999px;
  background: #fff7f5;
  color: #ff4f3b;
  cursor: pointer;
  font-size: 13px;
  font-weight: 760;
}

.channel-page__sort {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: 999px;
  background: #f1f3f6;
}

.channel-page__sort button {
  height: 30px;
  padding: 0 12px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #6c7482;
  cursor: pointer;
}

.channel-page__sort button.is-active {
  background: #fff;
  color: #ff5a45;
  font-weight: 800;
}

.channel-page__waterfall {
  display: grid;
  grid-template-columns: repeat(var(--column-count), minmax(0, 1fr));
  gap: 14px;
  align-items: start;
}

.channel-page__column {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.channel-page__state {
  padding: 26px 8px;
  color: #8a91a0;
}

.channel-page__state--error {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.channel-page__state--error button,
.channel-page__more {
  border: none;
  border-radius: 8px;
  background: #20242f;
  color: #fff;
  cursor: pointer;
}

.channel-page__state--error button {
  height: 34px;
  padding: 0 14px;
}

.channel-page__more {
  display: block;
  height: 38px;
  margin: 18px auto 0;
  padding: 0 20px;
}

.channel-page__more:disabled {
  opacity: 0.6;
  cursor: wait;
}

@media (max-width: 760px) {
  .channel-page {
    padding: 10px 10px 36px;
  }

  .channel-page__hero {
    grid-template-columns: auto 58px minmax(0, 1fr);
    gap: 10px;
    padding: 14px;
  }

  .channel-page__avatar {
    width: 58px;
    height: 58px;
    border-radius: 12px;
    font-size: 24px;
  }

  .channel-page__publish {
    grid-column: 1 / -1;
    justify-content: center;
  }

  .channel-page__copy h1 {
    font-size: 22px;
  }
}
</style>
