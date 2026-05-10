<script setup lang="ts">
defineOptions({ name: 'SearchDiscoverView' })

import { ArrowRight, Search } from '@element-plus/icons-vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FeedCardRenderer from '../components/feed/FeedCardRenderer.vue'
import { api } from '../services/api'
import type { PostView, SearchResult } from '../types'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const loading = ref(false)
const error = ref('')
const result = ref<SearchResult>({
  posts: [],
  users: [],
  topics: [],
  channels: [],
})

const trimmedKeyword = computed(() => keyword.value.trim())
const hasAnyResult = computed(() => (
  result.value.posts.length > 0
  || result.value.users.length > 0
  || result.value.topics.length > 0
  || result.value.channels.length > 0
))

watch(
  () => route.query.q,
  () => {
    keyword.value = routeKeyword()
    void runSearch()
  },
)

onMounted(() => {
  keyword.value = routeKeyword()
  void runSearch()
})

function routeKeyword() {
  const q = route.query.q
  if (Array.isArray(q)) return q[0]?.trim() || ''
  return typeof q === 'string' ? q.trim() : ''
}

function normalizeMediaUrl(url?: string | null) {
  return (url || '').replace('http://localhost:9000', '/minio-img')
}

function formatCount(value?: number | null) {
  const count = Number(value || 0)
  if (count >= 10000) return `${(count / 10000).toFixed(1)}万`
  return String(Math.max(0, count))
}

function openPost(post: PostView) {
  void router.push(`/posts/${post.id}`)
}

function submitSearch() {
  if (!trimmedKeyword.value) return
  void router.push({ path: '/search', query: { q: trimmedKeyword.value } })
}

async function runSearch() {
  if (!trimmedKeyword.value) {
    result.value = { posts: [], users: [], topics: [], channels: [] }
    return
  }
  loading.value = true
  error.value = ''
  try {
    const data = await api.search(trimmedKeyword.value)
    result.value = {
      posts: data.posts || [],
      users: data.users || [],
      topics: data.topics || [],
      channels: data.channels || [],
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '搜索失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="search-page">
    <header class="search-page__head">
      <form class="search-page__search" @submit.prevent="submitSearch">
        <el-icon><Search /></el-icon>
        <input v-model="keyword" type="search" placeholder="搜索笔记、用户、标签、频道" />
        <button type="submit">搜索</button>
      </form>
      <p v-if="trimmedKeyword">搜索 “{{ trimmedKeyword }}”</p>
      <p v-else>输入关键词发现内容、用户、标签和频道</p>
    </header>

    <main class="search-page__body">
      <div v-if="loading" class="search-page__state">
        <el-skeleton animated :rows="12" />
      </div>
      <div v-else-if="error" class="search-page__state search-page__state--error">
        <p>{{ error }}</p>
        <button type="button" @click="runSearch">重试</button>
      </div>
      <div v-else-if="trimmedKeyword && !hasAnyResult" class="search-page__state">
        <p>没有找到与 “{{ trimmedKeyword }}” 相关的结果</p>
      </div>

      <template v-else>
        <section v-if="result.channels.length > 0" class="search-page__section">
          <div class="search-page__section-head">
            <h2>频道</h2>
          </div>
          <div class="search-page__channel-grid">
            <button v-for="channel in result.channels" :key="channel.code" type="button" @click="router.push(`/channels/${channel.code}`)">
              <span>{{ channel.name.slice(0, 1) }}</span>
              <strong>{{ channel.name }}</strong>
              <small>{{ channel.description || '内容频道' }}</small>
              <em><ArrowRight /></em>
            </button>
          </div>
        </section>

        <section v-if="result.topics.length > 0" class="search-page__section">
          <div class="search-page__section-head">
            <h2>标签</h2>
          </div>
          <div class="search-page__topic-row">
            <button v-for="topic in result.topics" :key="topic.id" type="button" @click="router.push({ path: '/search', query: { q: topic.name } })">
              <strong>#{{ topic.name }}</strong>
              <span>{{ formatCount(topic.postCount) }} 篇内容</span>
            </button>
          </div>
        </section>

        <section v-if="result.users.length > 0" class="search-page__section">
          <div class="search-page__section-head">
            <h2>用户</h2>
          </div>
          <div class="search-page__user-list">
            <article v-for="user in result.users" :key="user.id" @click="router.push(`/users/${user.id}`)">
              <img :src="normalizeMediaUrl(user.avatarUrl) || '/auto_picture.png'" alt="" />
              <span>
                <strong>{{ user.nickname }}</strong>
                <small>@{{ user.username }}</small>
              </span>
              <button type="button">主页</button>
            </article>
          </div>
        </section>

        <section v-if="result.posts.length > 0" class="search-page__section">
          <div class="search-page__section-head">
            <h2>笔记</h2>
          </div>
          <div class="search-page__post-grid">
            <FeedCardRenderer v-for="post in result.posts" :key="post.id" :post="post" @open="openPost" />
          </div>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.search-page {
  min-height: calc(100vh - 74px);
  padding: 18px 18px 48px;
  color: #20242f;
  background: #f7f8fa;
}

.search-page button,
.search-page input {
  font: inherit;
}

.search-page__head,
.search-page__body {
  max-width: 1180px;
  margin: 0 auto;
}

.search-page__head {
  display: grid;
  gap: 10px;
  padding: 18px;
  border: 1px solid rgba(26, 31, 44, 0.07);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(32, 36, 47, 0.06);
}

.search-page__search {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  height: 46px;
  padding: 0 8px 0 15px;
  border-radius: 999px;
  background: #f0f2f5;
  color: #7b8493;
}

.search-page__search input {
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: #20242f;
}

.search-page__search button {
  height: 34px;
  padding: 0 16px;
  border: none;
  border-radius: 999px;
  background: #ff5a45;
  color: #fff;
  cursor: pointer;
  font-weight: 780;
}

.search-page__head p {
  margin: 0;
  color: #7f8796;
}

.search-page__body {
  display: grid;
  gap: 14px;
  margin-top: 14px;
}

.search-page__section,
.search-page__state {
  border: 1px solid rgba(26, 31, 44, 0.07);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(32, 36, 47, 0.06);
}

.search-page__section {
  padding: 16px;
}

.search-page__section-head {
  margin-bottom: 12px;
}

.search-page__section-head h2 {
  margin: 0;
  font-size: 18px;
}

.search-page__channel-grid,
.search-page__post-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 12px;
}

.search-page__channel-grid button {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e8ebf0;
  border-radius: 8px;
  background: #fbfcfe;
  color: #303744;
  cursor: pointer;
  text-align: left;
}

.search-page__channel-grid span {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  background: #fff0ed;
  color: #ff5a45;
  font-weight: 900;
}

.search-page__channel-grid strong,
.search-page__channel-grid small {
  grid-column: 2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-page__channel-grid small {
  color: #8a91a0;
  font-size: 12px;
}

.search-page__channel-grid em {
  grid-row: 1 / 3;
  grid-column: 3;
  color: #9aa1ad;
}

.search-page__topic-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.search-page__topic-row button {
  display: grid;
  gap: 2px;
  min-width: 140px;
  padding: 10px 12px;
  border: 1px solid #dfe9ff;
  border-radius: 8px;
  background: #f8fbff;
  color: #303744;
  cursor: pointer;
  text-align: left;
}

.search-page__topic-row span {
  color: #7f8796;
  font-size: 12px;
}

.search-page__user-list {
  display: grid;
  gap: 8px;
}

.search-page__user-list article {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #e8ebf0;
  border-radius: 8px;
  cursor: pointer;
}

.search-page__user-list img {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
}

.search-page__user-list span {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.search-page__user-list strong,
.search-page__user-list small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-page__user-list small {
  color: #8a91a0;
  font-size: 12px;
}

.search-page__user-list button {
  height: 30px;
  padding: 0 12px;
  border: none;
  border-radius: 8px;
  background: #fff0ed;
  color: #ff5a45;
  cursor: pointer;
  font-weight: 760;
}

.search-page__state {
  padding: 26px;
  color: #8a91a0;
}

.search-page__state--error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.search-page__state--error button {
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: 8px;
  background: #20242f;
  color: #fff;
  cursor: pointer;
}

@media (max-width: 760px) {
  .search-page {
    padding: 10px 10px 36px;
  }

  .search-page__post-grid {
    grid-template-columns: 1fr;
  }
}
</style>
