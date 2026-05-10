<script setup lang="ts">
defineOptions({ name: 'TopicView' })

import { ArrowLeft, EditPen, Star, StarFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FeedCardRenderer from '../components/feed/FeedCardRenderer.vue'
import { api, type TopicView as TopicInfo } from '../services/api'
import { useAuthStore } from '../stores/auth'
import type { PostView } from '../types'

type SortMode = 'hot' | 'latest'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
authStore.hydrate()

const topic = ref<TopicInfo | null>(null)
const relatedTopics = ref<TopicInfo[]>([])
const posts = ref<PostView[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const followLoading = ref(false)
const followed = ref(false)
const error = ref('')
const page = ref(1)
const total = ref(0)
const sortMode = ref<SortMode>('hot')

const slug = computed(() => String(route.params.slug || ''))
const topicName = computed(() => topic.value?.name || '话题')
const hasMore = computed(() => posts.value.length < total.value)

watch(slug, () => {
  followed.value = false
  void loadTopicPage()
})

watch(sortMode, () => {
  void loadPosts(true)
})

onMounted(() => {
  void loadTopicPage()
})

function formatCount(value?: number | null) {
  const count = Number(value || 0)
  if (count >= 10000) return `${(count / 10000).toFixed(1)}万`
  return String(Math.max(0, count))
}

function openPost(post: PostView) {
  void router.push(`/posts/${post.id}`)
}

function openRelated(topicItem: TopicInfo) {
  void router.push(`/topics/${topicItem.slug}`)
}

async function loadTopicPage() {
  if (!slug.value) return
  loading.value = true
  error.value = ''
  try {
    const [topicData, relatedData] = await Promise.all([
      api.topicDetail(slug.value),
      api.relatedTopics(slug.value, 12),
    ])
    topic.value = topicData
    relatedTopics.value = relatedData
    await loadPosts(true)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '话题加载失败'
  } finally {
    loading.value = false
  }
}

async function loadPosts(reset = false) {
  if (!slug.value) return
  if (reset) {
    page.value = 1
    posts.value = []
  }
  loadingMore.value = true
  try {
    const data = await api.topicPosts(slug.value, page.value, 24, sortMode.value)
    total.value = Number(data.total || 0)
    posts.value = reset ? data.records : [...posts.value, ...data.records]
    page.value += 1
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '内容加载失败')
  } finally {
    loadingMore.value = false
  }
}

async function toggleFollow() {
  if (!topic.value) return
  if (!authStore.accessToken) {
    authStore.openAuthPrompt('manual')
    return
  }
  followLoading.value = true
  try {
    if (followed.value) {
      await api.unfollowTopic(topic.value.id)
      followed.value = false
      ElMessage.success('已取消关注')
    } else {
      await api.followTopic(topic.value.id)
      followed.value = true
      ElMessage.success('已关注话题')
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '操作失败')
  } finally {
    followLoading.value = false
  }
}
</script>

<template>
  <div class="topic-page">
    <header class="topic-page__hero">
      <button type="button" class="topic-page__back" @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <div class="topic-page__cover">
        <img v-if="topic?.coverUrl" :src="topic.coverUrl" alt="" />
        <span v-else>#</span>
      </div>
      <div class="topic-page__copy">
        <span>话题</span>
        <h1>#{{ topicName }}</h1>
        <p>{{ topic?.description || '围绕这个话题发现内容、参与讨论，也可以从这里进入发布。' }}</p>
        <div>
          <em>{{ formatCount(topic?.postCount) }} 篇内容</em>
          <em>{{ formatCount(topic?.followerCount) }} 人关注</em>
        </div>
      </div>
      <div class="topic-page__actions">
        <button type="button" class="topic-page__follow" :disabled="followLoading" @click="toggleFollow">
          <el-icon><component :is="followed ? StarFilled : Star" /></el-icon>
          {{ followed ? '已关注' : '关注话题' }}
        </button>
        <button type="button" class="topic-page__publish" @click="router.push({ path: '/publish', query: { topic: topic?.name } })">
          <el-icon><EditPen /></el-icon>
          参与发布
        </button>
      </div>
    </header>

    <main class="topic-page__body">
      <aside class="topic-page__side">
        <section>
          <h2>相关话题</h2>
          <button v-for="item in relatedTopics" :key="item.id" type="button" @click="openRelated(item)">
            <strong>#{{ item.name }}</strong>
            <span>{{ formatCount(item.postCount) }} 篇</span>
          </button>
          <p v-if="!loading && relatedTopics.length === 0">暂无相关话题</p>
        </section>
      </aside>

      <section class="topic-page__content">
        <div class="topic-page__section-head">
          <h2>话题内容</h2>
          <div class="topic-page__sort">
            <button type="button" :class="{ 'is-active': sortMode === 'hot' }" @click="sortMode = 'hot'">热门</button>
            <button type="button" :class="{ 'is-active': sortMode === 'latest' }" @click="sortMode = 'latest'">最新</button>
          </div>
        </div>

        <div v-if="loading" class="topic-page__state">
          <el-skeleton animated :rows="8" />
        </div>
        <div v-else-if="error" class="topic-page__state topic-page__state--error">
          <p>{{ error }}</p>
          <button type="button" @click="loadTopicPage">重试</button>
        </div>
        <div v-else-if="posts.length === 0" class="topic-page__state">
          <p>这个话题暂时还没有内容</p>
        </div>
        <div v-else class="topic-page__grid">
          <FeedCardRenderer v-for="post in posts" :key="post.id" :post="post" @open="openPost" />
        </div>

        <button v-if="hasMore" type="button" class="topic-page__more" :disabled="loadingMore" @click="loadPosts(false)">
          {{ loadingMore ? '加载中...' : '加载更多' }}
        </button>
      </section>
    </main>
  </div>
</template>

<style scoped>
.topic-page {
  min-height: calc(100vh - 74px);
  padding: 18px 18px 48px;
  color: #20242f;
  background: #f7f8fa;
}

.topic-page button {
  font: inherit;
}

.topic-page__hero,
.topic-page__side section,
.topic-page__content {
  border: 1px solid rgba(26, 31, 44, 0.07);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(32, 36, 47, 0.06);
}

.topic-page__hero {
  display: grid;
  grid-template-columns: auto 86px minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  max-width: 1180px;
  min-height: 156px;
  margin: 0 auto;
  padding: 22px;
}

.topic-page__back {
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

.topic-page__cover {
  display: grid;
  place-items: center;
  width: 86px;
  height: 86px;
  border-radius: 18px;
  background: #eaf2ff;
  color: #3f6fd8;
  font-size: 38px;
  font-weight: 900;
}

.topic-page__cover img {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}

.topic-page__copy > span {
  color: #3f6fd8;
  font-size: 13px;
  font-weight: 800;
}

.topic-page__copy h1 {
  margin: 4px 0;
  font-size: 30px;
}

.topic-page__copy p {
  margin: 0;
  color: #6d7481;
  line-height: 1.55;
}

.topic-page__copy div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.topic-page__copy em {
  padding: 5px 9px;
  border-radius: 999px;
  background: #f4f6f9;
  color: #626b7a;
  font-style: normal;
  font-size: 12px;
}

.topic-page__actions {
  display: inline-flex;
  gap: 8px;
}

.topic-page__follow,
.topic-page__publish {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 14px;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 780;
}

.topic-page__follow {
  border: 1px solid #e4e8ef;
  background: #fff;
  color: #303744;
}

.topic-page__publish {
  border: none;
  background: #ff5a45;
  color: #fff;
}

.topic-page__body {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 14px;
  max-width: 1180px;
  margin: 14px auto 0;
}

.topic-page__side section,
.topic-page__content {
  padding: 16px;
}

.topic-page__side h2,
.topic-page__section-head h2 {
  margin: 0;
  font-size: 18px;
}

.topic-page__side section {
  display: grid;
  gap: 8px;
}

.topic-page__side button {
  display: grid;
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid #e8ebf0;
  border-radius: 8px;
  background: #fbfcfe;
  color: #303744;
  cursor: pointer;
  text-align: left;
}

.topic-page__side span {
  color: #8a91a0;
  font-size: 12px;
}

.topic-page__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.topic-page__sort {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border-radius: 999px;
  background: #f1f3f6;
}

.topic-page__sort button {
  height: 30px;
  padding: 0 12px;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #6c7482;
  cursor: pointer;
}

.topic-page__sort button.is-active {
  background: #fff;
  color: #3f6fd8;
  font-weight: 800;
}

.topic-page__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(236px, 1fr));
  gap: 12px;
}

.topic-page__state {
  padding: 26px 8px;
  color: #8a91a0;
}

.topic-page__state--error {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.topic-page__state--error button,
.topic-page__more {
  border: none;
  border-radius: 8px;
  background: #20242f;
  color: #fff;
  cursor: pointer;
}

.topic-page__state--error button {
  height: 34px;
  padding: 0 14px;
}

.topic-page__more {
  display: block;
  height: 38px;
  margin: 18px auto 0;
  padding: 0 20px;
}

.topic-page__more:disabled {
  opacity: 0.6;
  cursor: wait;
}

@media (max-width: 920px) {
  .topic-page__body {
    grid-template-columns: 1fr;
  }

  .topic-page__side section {
    grid-auto-flow: column;
    grid-auto-columns: minmax(136px, max-content);
    overflow-x: auto;
  }
}

@media (max-width: 760px) {
  .topic-page {
    padding: 10px 10px 36px;
  }

  .topic-page__hero {
    grid-template-columns: auto 58px minmax(0, 1fr);
    gap: 10px;
    padding: 14px;
  }

  .topic-page__cover {
    width: 58px;
    height: 58px;
    border-radius: 12px;
    font-size: 28px;
  }

  .topic-page__actions {
    grid-column: 1 / -1;
  }

  .topic-page__actions button {
    flex: 1;
    justify-content: center;
  }
}
</style>
