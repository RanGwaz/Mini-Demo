<script setup lang="ts">
defineOptions({ name: 'ProfileView' })

import {
  ArrowDown,
  ArrowRight,
  ChatLineRound,
  Location,
  MoreFilled,
  Search,
  Star,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonLeftSidebar from '../components/CommonLeftSidebar.vue'
import defaultProfileCover from '../assets/viewDesign/PC/light_index.png'
import { api } from '../services/api'
import { useAuthStore } from '../stores/auth'
import type { PostView, UserStats, UserSummary } from '../types'
import { getPostMediaUrl, hasPostMedia } from '../utils/postMedia'

type SortMode = 'all' | 'latest' | 'hot'
type ProfileTab = 'posts' | 'video' | 'favorite' | 'community' | 'replay' | 'about'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
authStore.hydrate()

const loading = ref(false)
const postsLoading = ref(false)
const followLoading = ref(false)
const activeTab = ref<ProfileTab>('posts')
const sortMode = ref<SortMode>('all')
const postKeyword = ref('')
const userProfile = ref<UserSummary | null>(null)
const userStats = ref<UserStats>({ postCount: 0, followingCount: 0, followerCount: 0 })
const posts = ref<PostView[]>([])
const followingAuthor = ref(false)

const tabs: Array<{ key: ProfileTab; label: string }> = [
  { key: 'posts', label: '动态' },
  { key: 'video', label: '视频' },
  { key: 'favorite', label: '收藏' },
  { key: 'community', label: '社群' },
  { key: 'replay', label: '直播回放' },
  { key: 'about', label: '关于TA' },
]

const suggestedCreators = [
  { name: '阿亦不知晓', desc: '旅行摄影师 · 12.8万粉丝' },
  { name: '一只鹿鹿', desc: '旅行博主 · 9.4万粉丝' },
  { name: '摄影师小北', desc: '风光摄影 · 15.2万粉丝' },
]

function resolveTargetUserId() {
  const raw = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id
  const routeId = Number(raw)
  if (Number.isFinite(routeId) && routeId > 0) return routeId
  return authStore.currentUser?.id ?? null
}

const targetUserId = computed(() => resolveTargetUserId())
const isMine = computed(() => Boolean(authStore.currentUser?.id && targetUserId.value === authStore.currentUser.id))

const avatarUrl = computed(() => normalizeMediaUrl(userProfile.value?.avatarUrl) || '/auto_picture.png')
const coverUrl = computed(() => normalizeMediaUrl(userProfile.value?.backgroundUrl) || defaultProfileCover)
const totalLikes = computed(() => posts.value.reduce((sum, item) => sum + Number(item.likeCount || 0), 0))
const dynamicCount = computed(() => userStats.value.postCount || posts.value.length)
const liveCount = computed(() => Math.max(0, Math.floor(posts.value.reduce((sum, item) => sum + Number(item.viewCount || 0), 0) / 12000)))
const aiFriendNames = computed(() => posts.value.slice(0, 4).map((item) => item.author.nickname).join('、'))

const profileTags = computed(() => {
  const tags = new Set<string>()
  for (const post of posts.value) {
    for (const tag of post.tags || []) {
      if (!tag) continue
      tags.add(tag)
      if (tags.size >= 6) return [...tags]
    }
  }
  return [...tags]
})

const filteredPosts = computed(() => {
  const keyword = postKeyword.value.trim().toLowerCase()
  let pool = [...posts.value]

  if (keyword) {
    pool = pool.filter((item) => {
      const searchable = [item.title, item.content || '', item.author.nickname].join(' ').toLowerCase()
      return searchable.includes(keyword)
    })
  }

  if (sortMode.value === 'hot') {
    pool.sort((a, b) => {
      const scoreA = Number(a.viewCount || 0) + Number(a.likeCount || 0) * 6 + Number(a.commentCount || 0) * 4
      const scoreB = Number(b.viewCount || 0) + Number(b.likeCount || 0) * 6 + Number(b.commentCount || 0) * 4
      return scoreB - scoreA
    })
    return pool
  }

  if (sortMode.value === 'latest') {
    pool.sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
    return pool
  }

  return pool
})

function normalizeMediaUrl(url?: string | null) {
  if (!url) return ''
  return String(url).replace('http://localhost:9000', '/minio-img')
}

function postImage(post: PostView) {
  return getPostMediaUrl(post)
}

function formatCount(value?: number | null) {
  const n = Number(value || 0)
  if (n >= 10000) return `${(n / 10000).toFixed(n >= 100000 ? 0 : 1)}万`
  if (n >= 1000) return `${(n / 1000).toFixed(n >= 10000 ? 0 : 1)}k`
  return String(Math.max(0, n))
}

function openPost(postId: number) {
  void router.push(`/posts/${postId}`)
}

async function loadFollowState(userId: number) {
  if (!authStore.currentUser || authStore.currentUser.id === userId) {
    followingAuthor.value = false
    return
  }
  try {
    const status = await api.followStatus(userId)
    if (targetUserId.value === userId) followingAuthor.value = Boolean(status.following)
  } catch {
    followingAuthor.value = false
  }
}

async function loadProfile() {
  const userId = targetUserId.value
  if (!userId) {
    authStore.openAuthPrompt('manual')
    void router.push('/feed')
    return
  }

  loading.value = true
  postsLoading.value = true
  try {
    const [profile, stats, postList] = await Promise.all([
      api.profile(userId),
      api.userStats(userId),
      api.userPosts(userId, 180),
    ])
    userProfile.value = profile
    userStats.value = stats
    posts.value = postList
    void loadFollowState(userId)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资料页加载失败')
    if (!isMine.value) void router.push('/feed')
  } finally {
    loading.value = false
    postsLoading.value = false
  }
}

async function toggleFollow() {
  const userId = targetUserId.value
  if (!userId) return
  if (!authStore.currentUser) {
    authStore.openAuthPrompt('manual')
    return
  }
  if (authStore.currentUser.id === userId) return

  followLoading.value = true
  try {
    if (followingAuthor.value) {
      await api.unfollow(userId, 'profile')
      followingAuthor.value = false
      userStats.value = { ...userStats.value, followerCount: Math.max(0, userStats.value.followerCount - 1) }
      ElMessage.success('已取消关注')
    } else {
      await api.follow(userId, 'profile')
      followingAuthor.value = true
      userStats.value = { ...userStats.value, followerCount: userStats.value.followerCount + 1 }
      ElMessage.success('关注成功')
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '关注操作失败')
  } finally {
    followLoading.value = false
  }
}

function handleTabChange(tab: ProfileTab) {
  activeTab.value = tab
  if (tab !== 'posts') ElMessage.info(`${tabs.find((item) => item.key === tab)?.label || '该分区'}正在完善中`)
}

watch(() => route.params.id, () => void loadProfile())
watch(() => authStore.currentUser?.id, () => void loadProfile())

onMounted(() => {
  void loadProfile()
})
</script>

<template>
  <div class="profile-home">
    <CommonLeftSidebar />

    <main class="profile-home__main">
      <section v-if="loading" class="profile-home__panel profile-home__loading">正在加载资料页...</section>

      <template v-else-if="userProfile">
        <section class="profile-home__panel profile-home__hero">
          <div class="profile-home__cover">
            <img :src="coverUrl" :alt="`${userProfile.nickname} 背景图`" />
          </div>

          <div class="profile-home__hero-main">
            <div class="profile-home__avatar-wrap">
              <img :src="avatarUrl" :alt="userProfile.nickname" />
              <span>✓</span>
            </div>

            <div class="profile-home__intro">
              <div class="profile-home__intro-head">
                <div class="profile-home__identity">
                  <h1>
                    {{ userProfile.nickname }}
                    <em>✓</em>
                    <b>年度优质创作者</b>
                  </h1>
                  <p>{{ userProfile.bio || '用镜头记录世界的温度' }}</p>
                  <small>
                    <el-icon><Location /></el-icon>
                    杭州 · 中国
                    <span>·</span>
                    自由摄影师
                  </small>
                  <div class="profile-home__tag-row">
                    <span v-for="tag in profileTags" :key="tag">{{ tag }}</span>
                  </div>
                </div>

                <div class="profile-home__hero-actions">
                  <template v-if="isMine">
                    <button type="button" @click="router.push('/publish')">发布</button>
                    <button type="button" class="is-ghost" @click="ElMessage.info('编辑资料功能正在完善中')">编辑资料</button>
                  </template>
                  <template v-else>
                    <button type="button" :class="{ 'is-followed': followingAuthor }" :disabled="followLoading" @click="toggleFollow">
                      {{ followingAuthor ? '已关注' : '+ 关注' }}
                      <el-icon v-if="followingAuthor"><ArrowDown /></el-icon>
                    </button>
                    <button type="button" class="is-ghost" @click="ElMessage.info('私信功能正在完善中')">私信</button>
                    <button type="button" class="icon-btn"><el-icon><MoreFilled /></el-icon></button>
                  </template>
                </div>
              </div>
            </div>
          </div>

          <div class="profile-home__metrics-row">
            <div class="profile-home__metrics">
              <div><strong>{{ formatCount(userStats.followerCount) }}</strong><span>粉丝</span></div>
              <div><strong>{{ formatCount(totalLikes) }}</strong><span>获赞</span></div>
              <div><strong>{{ formatCount(dynamicCount) }}</strong><span>动态</span></div>
              <div><strong>{{ formatCount(liveCount) }}</strong><span>直播</span></div>
            </div>
            <p>{{ aiFriendNames || 'Frank、阿May、摄影师小北 等 56 位共同关注' }}</p>
          </div>

          <div class="profile-home__toolbar">
            <div class="profile-home__tabs">
              <button
                v-for="tab in tabs"
                :key="tab.key"
                type="button"
                :class="{ 'is-active': activeTab === tab.key }"
                @click="handleTabChange(tab.key)"
              >
                {{ tab.label }}
              </button>
            </div>
            <div class="profile-home__filter">
              <div class="profile-home__search">
                <el-icon><Search /></el-icon>
                <input v-model="postKeyword" type="text" placeholder="搜索TA的动态" />
              </div>
              <el-select v-model="sortMode" class="profile-home__sort">
                <el-option label="全部" value="all" />
                <el-option label="最新" value="latest" />
                <el-option label="最热" value="hot" />
              </el-select>
            </div>
          </div>
        </section>

        <section class="profile-home__stream">
          <div v-if="postsLoading" class="profile-home__loading">正在加载作品...</div>
          <div v-else-if="filteredPosts.length === 0" class="profile-home__panel profile-home__empty">暂无内容</div>

          <div v-else class="profile-home__card-grid">
            <article
              v-for="(item, index) in filteredPosts"
              :key="item.id"
              class="profile-home__card"
              :class="{ 'is-text-only': !hasPostMedia(item) }"
              @click="openPost(item.id)"
            >
              <div v-if="hasPostMedia(item)" class="profile-home__card-cover">
                <img :src="postImage(item)" :alt="item.title || '帖子封面'" />
                <span v-if="index % 4 === 2">Vlog</span>
              </div>

              <div class="profile-home__card-body">
                <h3>{{ item.title || '未命名动态' }}</h3>
                <p :class="{ 'is-empty': !item.content }">{{ item.content || ' ' }}</p>
                <small :class="{ 'is-empty': !item.tags?.length }">
                  <template v-if="item.tags?.length">
                    <template v-for="tag in item.tags.slice(0, 3)" :key="tag">#{{ tag }} </template>
                  </template>
                  <template v-else>&nbsp;</template>
                </small>
                <div class="profile-home__card-actions">
                  <span>♡ {{ formatCount(item.likeCount) }}</span>
                  <span><el-icon><ChatLineRound /></el-icon> {{ formatCount(item.commentCount) }}</span>
                  <span class="bookmark"><el-icon><Star /></el-icon></span>
                </div>
              </div>
            </article>
          </div>
        </section>
      </template>

      <section v-else class="profile-home__panel profile-home__empty">资料页加载失败，请稍后重试</section>
    </main>

    <aside class="profile-home__right-rail">
      <section class="profile-home__right-card">
        <div class="profile-home__right-head">
          <strong>AI兴趣星图</strong>
          <button type="button">更多 <el-icon><ArrowRight /></el-icon></button>
        </div>
        <svg viewBox="0 0 220 170" class="profile-home__radar">
          <polygon points="110,18 169,45 169,103 110,132 51,103 51,45" fill="rgba(255,90,69,0.08)" stroke="#ffd2c8" />
          <polyline points="110,28 156,52 150,95 110,116 70,96 64,53 110,28" fill="none" stroke="#ff5a45" stroke-width="2" />
          <line x1="110" y1="18" x2="110" y2="132" stroke="#eee" />
          <line x1="51" y1="45" x2="169" y2="103" stroke="#eee" />
          <line x1="169" y1="45" x2="51" y2="103" stroke="#eee" />
        </svg>
      </section>

      <section class="profile-home__right-card">
        <div class="profile-home__right-head">
          <strong>内容足迹地图</strong>
          <button type="button">更多 <el-icon><ArrowRight /></el-icon></button>
        </div>
        <p>已点亮 <em>37</em> 个城市</p>
        <div class="profile-home__map">
          <span />
          <span />
          <span />
          <span />
          <span />
        </div>
      </section>

      <section class="profile-home__right-card profile-home__task-card">
        <strong>本周灵感计划</strong>
        <p>发布 3 条旅行视频，瓜分 10,000 灵感值</p>
        <button type="button">去参与</button>
      </section>

      <section class="profile-home__right-card">
        <div class="profile-home__right-head">
          <strong>AI同好推荐</strong>
          <button type="button">换一批</button>
        </div>
        <article v-for="creator in suggestedCreators" :key="creator.name" class="profile-home__creator-row">
          <img src="/auto_picture.png" alt="" />
          <div>
            <strong>{{ creator.name }}</strong>
            <small>{{ creator.desc }}</small>
          </div>
          <button type="button">+关注</button>
        </article>
      </section>

      <section class="profile-home__right-card">
        <div class="profile-home__right-head">
          <strong>粉丝共创提案</strong>
          <button type="button">更多 <el-icon><ArrowRight /></el-icon></button>
        </div>
        <p>一艘船「夏日海岛」系列短片</p>
        <small>已约 <em>326</em> 人，进行中</small>
      </section>

      <section class="profile-home__right-card">
        <div class="profile-home__right-head">
          <strong>品牌合作入口</strong>
          <button type="button">更多 <el-icon><ArrowRight /></el-icon></button>
        </div>
        <p>Vibelo 商业合作平台</p>
        <small>优质创作者专属通道</small>
      </section>
    </aside>
  </div>
</template>

<style scoped>
.profile-home {
  position: relative;
  width: 100%;
  min-height: calc(100vh - 74px);
  padding: 12px 306px 28px 244px;
  background: #f7f8fa;
  color: #20242f;
}

.profile-home__main {
  min-width: 0;
}

.profile-home__panel,
.profile-home__right-card {
  border: 1px solid rgba(26, 31, 44, 0.07);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 30px rgba(32, 36, 47, 0.05);
}

.profile-home__hero {
  overflow: hidden;
}

.profile-home__cover {
  height: 240px;
  background: #eef1f5;
}

.profile-home__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-home__hero-main {
  display: grid;
  grid-template-columns: 148px minmax(0, 1fr);
  gap: 14px;
  padding: 0 14px;
  margin-top: -54px;
}

.profile-home__avatar-wrap {
  position: relative;
  width: 128px;
  height: 128px;
  border-radius: 50%;
  box-shadow: 0 0 0 4px #fff;
}

.profile-home__avatar-wrap img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}

.profile-home__avatar-wrap span {
  position: absolute;
  right: 0;
  bottom: 4px;
  width: 28px;
  height: 28px;
  border: 3px solid #fff;
  border-radius: 50%;
  background: #ff5a45;
  color: #fff;
  font-size: 15px;
  font-weight: 800;
  line-height: 22px;
  text-align: center;
}

.profile-home__intro {
  padding-top: 58px;
}

.profile-home__intro-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.profile-home__identity h1 {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 42px;
  font-weight: 820;
  line-height: 1.18;
}

.profile-home__identity h1 em {
  color: #ff9a1f;
  font-style: normal;
  font-size: 19px;
}

.profile-home__identity h1 b {
  padding: 2px 8px;
  border-radius: 999px;
  background: #f5efe3;
  color: #8b7250;
  font-size: 12px;
  font-weight: 700;
}

.profile-home__identity p {
  margin: 4px 0 0;
  color: #5f6674;
  font-size: 15px;
}

.profile-home__identity small {
  margin-top: 8px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #8b93a1;
  font-size: 13px;
}

.profile-home__tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.profile-home__tag-row span {
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background: #f3f5f8;
  color: #667085;
  font-size: 13px;
  line-height: 28px;
}

.profile-home__hero-actions {
  display: inline-flex;
  align-items: flex-start;
  gap: 8px;
  padding-top: 6px;
}

.profile-home__hero-actions button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 36px;
  padding: 0 14px;
  border: none;
  border-radius: 10px;
  background: #ff5a45;
  color: #fff;
  font-size: 15px;
  font-weight: 760;
  cursor: pointer;
}

.profile-home__hero-actions button.is-followed,
.profile-home__hero-actions button.is-ghost {
  border: 1px solid #e7eaf0;
  background: #fff;
  color: #2f3441;
}

.profile-home__hero-actions button.icon-btn {
  width: 36px;
  padding: 0;
  color: #727b8a;
}

.profile-home__metrics-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-top: 14px;
  padding: 14px;
  border-top: 1px solid #eceff4;
}

.profile-home__metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}

.profile-home__metrics div {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
}

.profile-home__metrics strong {
  font-size: 22px;
  font-weight: 730;
  line-height: 1.2;
}

.profile-home__metrics span {
  color: #7c8492;
  font-size: 14px;
  font-weight: 600;
}

.profile-home__metrics-row p {
  margin: 0;
  color: #8a91a0;
  font-size: 14px;
  text-align: right;
  white-space: nowrap;
}

.profile-home__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 14px;
  border-top: 1px solid #eceff4;
}

.profile-home__tabs {
  display: flex;
  gap: 16px;
}

.profile-home__tabs button {
  position: relative;
  border: none;
  background: transparent;
  color: #2f3441;
  font-size: 20px;
  font-weight: 720;
  line-height: 52px;
  cursor: pointer;
}

.profile-home__tabs button::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  border-radius: 999px;
  background: #ff5a45;
  transform: scaleX(0);
}

.profile-home__tabs button.is-active {
  color: #ff5a45;
}

.profile-home__tabs button.is-active::after {
  transform: scaleX(1);
}

.profile-home__filter {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.profile-home__search {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: 300px;
  height: 38px;
  padding: 0 12px;
  border-radius: 999px;
  background: #f2f4f7;
  color: #8a91a0;
}

.profile-home__search input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  color: #20242f;
  font-size: 14px;
}

.profile-home__sort {
  width: 100px;
}

.profile-home__stream {
  margin-top: 10px;
}

.profile-home__card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 10px;
}

.profile-home__card {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  border: 1px solid #e8ebf0;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

.profile-home__card:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 24px rgba(20, 25, 38, 0.09);
}

.profile-home__card.is-text-only {
  background: linear-gradient(180deg, #ffffff 0%, #fbfcfe 100%);
}

.profile-home__card.is-text-only .profile-home__card-body {
  padding: 14px;
}

.profile-home__card.is-text-only .profile-home__card-body h3 {
  -webkit-line-clamp: 3;
}

.profile-home__card.is-text-only .profile-home__card-body p {
  -webkit-line-clamp: 6;
}

.profile-home__card-cover {
  position: relative;
  overflow: hidden;
  aspect-ratio: 16 / 10;
  background: #f1f3f7;
}

.profile-home__card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-home__card-cover span {
  position: absolute;
  right: 7px;
  top: 7px;
  padding: 2px 7px;
  border-radius: 999px;
  background: rgba(32, 36, 47, 0.68);
  color: #fff;
  font-size: 11px;
}

.profile-home__card-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 140px;
  padding: 8px 10px;
}

.profile-home__card-body h3 {
  display: -webkit-box;
  min-height: calc(1.45em * 2);
  margin: 0;
  overflow: hidden;
  color: #2e3441;
  font-size: 16px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.profile-home__card-body p {
  display: -webkit-box;
  min-height: calc(1.5em * 2);
  margin: 5px 0 0;
  overflow: hidden;
  color: #646d7d;
  font-size: 14px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.profile-home__card-body small {
  display: block;
  min-height: 1.3em;
  margin-top: 6px;
  color: #5a7cd4;
  font-size: 13px;
}

.profile-home__card-body p.is-empty,
.profile-home__card-body small.is-empty {
  visibility: hidden;
}

.profile-home__card-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: auto;
  padding-top: 8px;
  color: #6f7582;
  font-size: 13px;
}

.profile-home__card-actions span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.profile-home__card-actions .bookmark {
  margin-left: auto;
}

.profile-home__right-rail {
  position: fixed;
  right: 14px;
  top: 88px;
  width: 292px;
  display: grid;
  gap: 10px;
  max-height: calc(100vh - 102px);
  overflow-y: auto;
  padding-right: 2px;
}

.profile-home__right-card {
  padding: 12px;
}

.profile-home__right-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.profile-home__right-head strong {
  font-size: 18px;
}

.profile-home__right-head button {
  border: none;
  background: transparent;
  color: #8a91a0;
  font-size: 12px;
  cursor: pointer;
}

.profile-home__radar {
  width: 100%;
  height: 140px;
}

.profile-home__right-card p {
  margin: 0;
  color: #5f6674;
  font-size: 13px;
}

.profile-home__right-card p em,
.profile-home__right-card small em {
  color: #ff5a45;
  font-style: normal;
}

.profile-home__map {
  position: relative;
  height: 110px;
  margin-top: 8px;
  border-radius: 8px;
  background: linear-gradient(180deg, #f7f9fc, #f0f3f8);
}

.profile-home__map span {
  position: absolute;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #ff6f52;
}

.profile-home__map span:nth-child(1) { left: 24%; top: 36%; }
.profile-home__map span:nth-child(2) { left: 36%; top: 58%; }
.profile-home__map span:nth-child(3) { left: 58%; top: 41%; }
.profile-home__map span:nth-child(4) { left: 68%; top: 63%; }
.profile-home__map span:nth-child(5) { left: 79%; top: 46%; }

.profile-home__task-card button {
  margin-top: 10px;
  height: 30px;
  padding: 0 14px;
  border: none;
  border-radius: 999px;
  background: #fff0ed;
  color: #ff5a45;
  cursor: pointer;
}

.profile-home__creator-row {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  margin-top: 9px;
}

.profile-home__creator-row img {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  object-fit: cover;
}

.profile-home__creator-row div {
  min-width: 0;
  display: grid;
}

.profile-home__creator-row strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.profile-home__creator-row small {
  overflow: hidden;
  color: #8a91a0;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}

.profile-home__creator-row button {
  height: 28px;
  padding: 0 10px;
  border: none;
  border-radius: 999px;
  background: #fff0ed;
  color: #ff5a45;
  font-size: 12px;
  cursor: pointer;
}

.profile-home__loading,
.profile-home__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  color: #8a91a0;
  font-size: 14px;
}

@media (max-width: 1360px) {
  .profile-home {
    padding-right: 10px;
  }

  .profile-home__right-rail {
    display: none;
  }
}

@media (max-width: 980px) {
  .profile-home {
    padding: 10px;
  }

  .profile-home__hero-main {
    grid-template-columns: 1fr;
    margin-top: -34px;
  }

  .profile-home__intro {
    padding-top: 0;
  }

  .profile-home__intro-head {
    display: grid;
  }

  .profile-home__identity h1 {
    font-size: 28px;
  }

  .profile-home__identity p {
    font-size: 14px;
  }

  .profile-home__identity small {
    font-size: 12px;
  }

  .profile-home__metrics-row {
    display: grid;
    gap: 10px;
  }

  .profile-home__metrics {
    gap: 12px;
  }

  .profile-home__metrics strong {
    font-size: 20px;
  }

  .profile-home__metrics span {
    font-size: 13px;
  }

  .profile-home__metrics-row p {
    text-align: left;
    white-space: normal;
  }

  .profile-home__toolbar {
    display: grid;
    padding-bottom: 8px;
  }

  .profile-home__tabs {
    overflow-x: auto;
    scrollbar-width: none;
  }

  .profile-home__tabs::-webkit-scrollbar {
    display: none;
  }

  .profile-home__tabs button {
    flex: 0 0 auto;
    font-size: 15px;
    line-height: 44px;
  }

  .profile-home__filter {
    display: grid;
  }

  .profile-home__search {
    width: 100%;
  }

  .profile-home__sort {
    width: 100%;
  }
}
</style>
