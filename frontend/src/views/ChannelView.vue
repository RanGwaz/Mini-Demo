<script setup lang="ts">
defineOptions({ name: 'ChannelView' })

import { ArrowLeft, EditPen, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
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

const channelCode = computed(() => String(route.params.code || ''))
const hasMore = computed(() => posts.value.length < total.value)
const channelTitle = computed(() => channel.value?.name || '频道')
const channelDescription = computed(() => channel.value?.description || '正在生长的中文内容频道')

watch(channelCode, () => {
  void loadChannelPage()
})

watch(sortMode, () => {
  void loadPosts(true)
})

onMounted(() => {
  void loadChannelPage()
})

function formatCount(value?: number | null) {
  const count = Number(value || 0)
  if (count >= 10000) return `${(count / 10000).toFixed(1)}万`
  return String(Math.max(0, count))
}

function openPost(post: PostView) {
  void router.push(`/posts/${post.id}`)
}

function openTopic(topic: TopicView) {
  void router.push(`/topics/${topic.slug}`)
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
    const data = await api.channelPosts(channelCode.value, page.value, 24, sortMode.value)
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
          <em>{{ topics.length }} 个话题</em>
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
          <h2>频道话题</h2>
          <button type="button" @click="loadChannelPage">
            <el-icon><RefreshRight /></el-icon>
          </button>
        </div>
        <div class="channel-page__topic-row">
          <button v-for="topic in topics" :key="topic.id" type="button" @click="openTopic(topic)">
            <strong>#{{ topic.name }}</strong>
            <span>{{ formatCount(topic.postCount) }} 篇</span>
          </button>
          <p v-if="!loading && topics.length === 0">这个频道还没有绑定话题</p>
        </div>
      </section>

      <section class="channel-page__content">
        <div class="channel-page__section-head">
          <h2>频道内容</h2>
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
        <div v-else class="channel-page__grid">
          <FeedCardRenderer v-for="post in posts" :key="post.id" :post="post" @open="openPost" />
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
  min-height: calc(100vh - 74px);
  padding: 18px 18px 48px;
  color: #20242f;
  background: #f7f8fa;
}

.channel-page button {
  font: inherit;
}

.channel-page__hero,
.channel-page__topics,
.channel-page__content {
  max-width: 1180px;
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

.channel-page__topic-row span {
  color: #8a91a0;
  font-size: 12px;
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

.channel-page__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(236px, 1fr));
  gap: 12px;
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
