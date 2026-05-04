<script setup lang="ts">
defineOptions({ name: 'PublishView' })

import {
  ArrowLeft,
  Check,
  CircleCheck,
  Close,
  Document,
  Picture,
  Plus,
  UploadFilled,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { defaultPublishChannelKey, publishChannels, type PublishChannelKey } from '../domain/contentTaxonomy'
import { api, type CreatePostAssetPayload, type PublishTagSuggestion } from '../services/api'
import { useAuthStore } from '../stores/auth'
import type { UploadResponse } from '../types'

type PublishKind = 'text' | 'image'
type PreviewMode = 'note' | 'cover'
const MAX_TAGS_PER_POST = 7

type CoverPreviewCard = {
  key: string
  title: string
  author: string
  avatar: string
  cover: string
  content: string
  current: boolean
  height: string
  likes: number
  comments: number
  tag: string
  duration?: string
}

const router = useRouter()
const authStore = useAuthStore()
authStore.hydrate()

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const tagLoading = ref(false)
const selectedKind = ref<PublishKind>('text')
const previewMode = ref<PreviewMode>('note')
const uploadedAssets = ref<UploadResponse[]>([])
const selectedTags = ref<string[]>([])
const tagInput = ref('')
const quickTags = ref<string[]>([])
const hashtagRecommendations = ref<PublishTagSuggestion[]>([])
const activeChannel = ref<PublishChannelKey>(defaultPublishChannelKey)
const visibility = ref('public')
let tagSearchTimer: number | undefined

const form = reactive({
  title: '',
  content: '',
})

const publishTypes = [
  { key: 'text', label: '纯文本', icon: Document },
  { key: 'image', label: '图文', icon: Picture },
] satisfies Array<{ key: PublishKind; label: string; icon: typeof Document }>

const drafts = [
  { title: '宿舍桌面改造记录', time: '今天 14:30', status: '编辑中', image: 'https://picsum.photos/seed/draft-dorm/120/90' },
  { title: 'AI学习流的三步法', time: '今天 10:12', status: '编辑中', image: 'https://picsum.photos/seed/draft-ai/120/90' },
  { title: '校园晚饭打卡', time: '昨天 22:45', status: '已保存', image: 'https://picsum.photos/seed/draft-campus/120/90' },
]

const sampleCoverCards: CoverPreviewCard[] = [
  {
    key: 'sample-1',
    title: '圣托里尼的日落，永远看不够',
    author: '小米在旅行',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=travel',
    cover: 'https://picsum.photos/seed/preview-sunset/500/640',
    content: '',
    current: false,
    height: '176px',
    likes: 832,
    comments: 56,
    tag: '#旅行打卡',
  },
  {
    key: 'sample-2',
    title: '沉浸式肩背训练，今日份挥汗',
    author: '卡卡爱健身',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=fit',
    cover: 'https://picsum.photos/seed/preview-fitness/500/650',
    content: '',
    current: false,
    height: '186px',
    likes: 921,
    comments: 42,
    tag: '#健身打卡',
    duration: '00:15',
  },
  {
    key: 'sample-3',
    title: '周末做的日式豚骨拉面，灵魂是溏心蛋',
    author: '吃货小圆',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=food',
    cover: 'https://picsum.photos/seed/preview-ramen/500/620',
    content: '',
    current: false,
    height: '172px',
    likes: 489,
    comments: 16,
    tag: '#今日美食',
    duration: '1/6',
  },
  {
    key: 'sample-4',
    title: '今天也是被治愈的一天',
    author: '奶茶不加糖',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=pet',
    cover: 'https://picsum.photos/seed/preview-dog/500/560',
    content: '',
    current: false,
    height: '154px',
    likes: 643,
    comments: 23,
    tag: '#日常碎片',
  },
]

const titleCount = computed(() => form.title.length)
const contentCount = computed(() => form.content.length)
const currentChannelLabel = computed(() => publishChannels.find((item) => item.key === activeChannel.value)?.label || publishChannels[0]?.label || '')
const currentUserName = computed(() => authStore.currentUser?.nickname || 'Vibelo 用户')
const currentUserAvatar = computed(() => authStore.currentUser?.avatarUrl || 'https://api.dicebear.com/9.x/adventurer/svg?seed=creator')
const hasImages = computed(() => uploadedAssets.value.length > 0)
const currentCover = computed(() => selectedKind.value === 'image' && hasImages.value ? resolveAssetCover(uploadedAssets.value[0]) : '')
const previewParagraphs = computed(() => form.content.split('\n').map((line) => line.trim()).filter(Boolean).slice(0, 5))
const previewTagList = computed(() => {
  if (selectedTags.value.length > 0) return selectedTags.value.slice(0, 4).map((item) => `#${item}`)
  return ['#旅行记录', '#城市漫游']
})
const currentPostTag = computed(() => previewTagList.value[0] || '#发现灵感')
const normalizedTagKeyword = computed(() => tagInput.value.trim().replace(/^#+/, '').replace(/\s+/g, ''))
const showHashtagPanel = computed(() => tagInput.value.trim().startsWith('#'))
const hashtagCandidates = computed(() => {
  const keyword = normalizedTagKeyword.value
  if (!keyword) return hashtagRecommendations.value
  return hashtagRecommendations.value.filter((item) => item.name.includes(keyword))
})
const coverPreviewCards = computed<CoverPreviewCard[]>(() => [
  {
    key: 'current',
    title: form.title.trim() || '示例笔记标题',
    author: currentUserName.value,
    avatar: currentUserAvatar.value,
    cover: currentCover.value,
    content: form.content.trim() || '纯文本笔记会以简洁的文字卡片展示在信息流中。',
    current: true,
    height: currentCover.value ? '176px' : '152px',
    likes: 0,
    comments: 0,
    tag: currentPostTag.value,
  },
  ...sampleCoverCards,
])
const notePreviewComments = computed(() => [
  {
    id: 'c-1',
    user: '爱吃冰激凌',
    content: form.title.trim() ? `太赞了！「${form.title.trim().slice(0, 14)}」很想看完整版。` : '太赞了，下次一定要去一次！',
    likes: 23,
  },
  {
    id: 'c-2',
    user: '请益焦糖',
    content: currentCover.value ? '画面很有氛围感，求具体地点。' : '这段文字很有共鸣，收藏了。',
    likes: 8,
  },
])
const noteRelatedCards = computed(() => coverPreviewCards.value.slice(0, 4))
const canPublish = computed(() => {
  if (loading.value || uploading.value) return false
  if (!form.title.trim() || !form.content.trim()) return false
  if (selectedKind.value === 'image' && uploadedAssets.value.length === 0) return false
  return true
})

onMounted(() => {
  void loadPublishSuggestions()
})

watch(activeChannel, () => {
  void loadPublishSuggestions()
})

watch(normalizedTagKeyword, (keyword) => {
  if (!showHashtagPanel.value) return
  if (tagSearchTimer) window.clearTimeout(tagSearchTimer)
  tagSearchTimer = window.setTimeout(() => {
    void loadPublishSuggestions(keyword)
  }, 180)
})

watch(showHashtagPanel, (show) => {
  if (show) void loadPublishSuggestions(normalizedTagKeyword.value)
})

function resolveAssetCover(asset: UploadResponse) {
  return (asset.thumbUrl || asset.fileUrl).replace('http://localhost:9000', '/minio-img')
}

function normalizeTag(raw: string) {
  return raw.trim().replace(/^#+/, '').replace(/\s+/g, '')
}

async function loadPublishSuggestions(keyword = '') {
  tagLoading.value = true
  try {
    const response = await api.publishSuggestions(activeChannel.value, keyword)
    quickTags.value = response.quickTags || []
    hashtagRecommendations.value = response.trendingTags || []
  } catch {
    quickTags.value = []
    hashtagRecommendations.value = []
  } finally {
    tagLoading.value = false
  }
}

function addTag(raw: string) {
  const normalized = normalizeTag(raw)
  if (!normalized) return
  if (selectedTags.value.includes(normalized)) {
    tagInput.value = ''
    return
  }
  if (selectedTags.value.length >= MAX_TAGS_PER_POST) {
    ElMessage.info(`最多添加 ${MAX_TAGS_PER_POST} 个标签`)
    return
  }
  selectedTags.value = [...selectedTags.value, normalized]
  tagInput.value = ''
}

function addTagFromInput() {
  if (!showHashtagPanel.value) return
  if (!normalizedTagKeyword.value) {
    ElMessage.info('请输入 #标签 名称')
    return
  }
  addTag(normalizedTagKeyword.value)
}

function removeTag(tag: string) {
  selectedTags.value = selectedTags.value.filter((item) => item !== tag)
}

function selectPublishChannel(channelKey: PublishChannelKey) {
  if (activeChannel.value === channelKey) return
  activeChannel.value = channelKey
}

async function onSelectFile(uploadFile: { raw?: File }) {
  if (!uploadFile.raw || uploading.value) return
  uploading.value = true
  try {
    const response = await api.uploadImage(uploadFile.raw)
    uploadedAssets.value.push(response)
    selectedKind.value = 'image'
    previewMode.value = 'cover'
    ElMessage.success('图片上传成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败')
  } finally {
    uploading.value = false
  }
}

function removeAsset(index: number) {
  uploadedAssets.value.splice(index, 1)
  if (uploadedAssets.value.length === 0 && selectedKind.value === 'image') {
    selectedKind.value = 'text'
    previewMode.value = 'note'
  }
}

function saveDraft() {
  saving.value = true
  window.setTimeout(() => {
    saving.value = false
    ElMessage.success('草稿已保存')
  }, 450)
}

function uniqueTags(tags: string[]) {
  const seen = new Set<string>()
  const result: string[] = []
  for (const item of tags) {
    const normalized = normalizeTag(item)
    if (!normalized || seen.has(normalized)) continue
    seen.add(normalized)
    result.push(normalized)
  }
  return result
}

function toAssetsPayload(): CreatePostAssetPayload[] {
  return uploadedAssets.value.map((item, index) => ({
    objectKey: item.objectKey,
    fileUrl: item.fileUrl,
    fileType: item.fileType,
    thumbUrl: item.thumbUrl,
    width: item.width,
    height: item.height,
    sortOrder: index,
  }))
}

async function submit() {
  if (!form.title.trim()) {
    ElMessage.warning('请先填写标题')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('请先填写正文')
    return
  }
  if (selectedKind.value === 'image' && uploadedAssets.value.length === 0) {
    ElMessage.warning('图文模式请先上传图片，或切换为纯文本')
    return
  }

  loading.value = true
  try {
    const finalTags = uniqueTags(selectedTags.value).slice(0, MAX_TAGS_PER_POST)
    const assets = selectedKind.value === 'image' ? toAssetsPayload() : []
    const payload: {
      title: string
      content: string
      channel: string
      tags?: string[]
      assets?: CreatePostAssetPayload[]
    } = {
      title: form.title.trim(),
      content: form.content.trim(),
      channel: activeChannel.value,
      ...(finalTags.length > 0 ? { tags: finalTags } : {}),
      ...(assets.length > 0 ? { assets } : {}),
    }

    await api.createPost(payload)
    sessionStorage.setItem('image-social-feed-need-refresh', '1')
    ElMessage.success('发布成功')
    void router.push('/feed')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发布失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="publish-studio">
    <aside class="publish-studio__left">
      <section class="publish-studio__side-card publish-studio__type-card">
        <h2>发布内容</h2>
        <button
          v-for="item in publishTypes"
          :key="item.key"
          type="button"
          :class="{ 'is-active': selectedKind === item.key }"
          @click="selectedKind = item.key"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </button>
      </section>

      <section class="publish-studio__side-card publish-studio__draft-card">
        <div class="publish-studio__side-title">
          <strong>草稿箱 <em>(3)</em></strong>
          <button type="button">管理</button>
        </div>
        <article v-for="draft in drafts" :key="draft.title">
          <img :src="draft.image" alt="" />
          <span>
            <strong>{{ draft.title }}</strong>
            <small>{{ draft.time }}</small>
          </span>
          <em :class="{ 'is-saved': draft.status === '已保存' }">{{ draft.status }}</em>
        </article>
        <button type="button" class="publish-studio__new-draft">
          <el-icon><Plus /></el-icon>
          新建草稿
        </button>
      </section>

      <footer class="publish-studio__autosave">
        <el-icon><CircleCheck /></el-icon>
        草稿自动保存中
      </footer>
    </aside>

    <main class="publish-studio__editor">
      <section class="publish-studio__editor-card">
        <div class="publish-studio__editor-head">
          <button type="button" @click="router.back()">
            <el-icon><ArrowLeft /></el-icon>
          </button>
          <h1>{{ selectedKind === 'image' ? '新建图文' : '新建纯文本' }}</h1>
          <span>
            <el-icon><Check /></el-icon>
            已保存
          </span>
        </div>

        <label class="publish-studio__title-field">
          <input v-model="form.title" maxlength="100" placeholder="填写一个清晰具体的标题" />
          <span>{{ titleCount }}/100</span>
        </label>

        <section class="publish-studio__text-editor">
          <textarea v-model="form.content" maxlength="1024" placeholder="输入正文描述，分享有价值的信息或观点" />
          <span>{{ contentCount }}/1024</span>
        </section>

        <section class="publish-studio__setting">
          <div class="publish-studio__section-head">
            <h3>图片</h3>
            <small>纯文本可不上传，图文模式至少上传 1 张</small>
          </div>
          <div class="publish-studio__covers">
            <article v-for="(asset, index) in uploadedAssets" :key="asset.objectKey" class="publish-studio__asset">
              <img :src="resolveAssetCover(asset)" alt="" />
              <button type="button" aria-label="移除图片" @click="removeAsset(index)">
                <el-icon><Close /></el-icon>
              </button>
            </article>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              multiple
              :disabled="uploading"
              :on-change="onSelectFile"
            >
              <button type="button" class="publish-studio__upload">
                <el-icon><UploadFilled /></el-icon>
                {{ uploading ? '上传中...' : '上传图片' }}
              </button>
            </el-upload>
          </div>
        </section>

        <section class="publish-studio__form-stack">
          <div class="publish-studio__row publish-studio__row--top">
            <strong>频道</strong>
            <div class="publish-studio__channel-tabs">
              <button
                v-for="channel in publishChannels"
                :key="channel.key"
                type="button"
                :class="{ 'is-selected': activeChannel === channel.key }"
                @click="selectPublishChannel(channel.key)"
              >
                {{ channel.label }}
              </button>
            </div>
          </div>

          <div class="publish-studio__row publish-studio__row--top">
            <strong>标签</strong>
            <div class="publish-studio__tag-panel">
              <div v-if="selectedTags.length > 0" class="publish-studio__selected-tags">
                <span v-for="tag in selectedTags" :key="tag">
                  #{{ tag }}
                  <button type="button" @click="removeTag(tag)">×</button>
                </span>
              </div>

              <div class="publish-studio__tag-entry">
                <input
                  v-model="tagInput"
                  maxlength="24"
                  placeholder="输入 # 获取热门话题，不输入 # 可直接点下方标签"
                  @keydown.enter.prevent="addTagFromInput"
                />
                <button
                  v-if="showHashtagPanel && normalizedTagKeyword"
                  type="button"
                  class="publish-studio__tag-add"
                  @click="addTag(normalizedTagKeyword)"
                >
                  添加
                </button>
              </div>

              <div v-if="showHashtagPanel" class="publish-studio__hashtag-list">
                <button v-for="item in hashtagCandidates" :key="item.name" type="button" @click="addTag(item.name)">
                  <strong>#{{ item.name }}</strong>
                  <small>{{ item.heat }}</small>
                </button>
                <p v-if="tagLoading">正在加载话题...</p>
                <p v-else-if="hashtagCandidates.length === 0">没有匹配到话题</p>
              </div>

              <div v-else class="publish-studio__quick-tags">
                <button v-for="tag in quickTags" :key="tag" type="button" @click="addTag(tag)">
                  #{{ tag }}
                </button>
                <span v-if="tagLoading">正在加载标签...</span>
              </div>
            </div>
          </div>

          <div class="publish-studio__row">
            <strong>可见范围</strong>
            <el-select v-model="visibility" class="publish-studio__select">
              <el-option label="公开 - 所有人可见" value="public" />
              <el-option label="仅关注者可见" value="follower" />
              <el-option label="仅自己可见" value="private" />
            </el-select>
          </div>

          <div class="publish-studio__row publish-studio__publish-time">
            <strong>发布时间</strong>
            <label>
              <input type="radio" checked />
              立即发布
            </label>
            <label class="is-disabled" title="定时发布暂未开放">
              <input type="radio" disabled />
              定时发布
            </label>
            <input type="text" disabled value="暂未开放" />
          </div>
        </section>
      </section>

      <section class="publish-studio__bottom-bar">
        <button type="button" @click="saveDraft">{{ saving ? '保存中...' : '存为草稿' }}</button>
        <div>
          <button type="button" class="is-ghost" @click="previewMode = 'note'">预览</button>
          <button type="button" class="is-primary" :disabled="!canPublish" @click="submit">
            {{ loading ? '发布中...' : '发布' }}
          </button>
        </div>
      </section>
    </main>

    <aside class="publish-studio__preview">
      <section class="publish-studio__preview-card">
        <div class="publish-studio__preview-tabs">
          <button type="button" :class="{ 'is-active': previewMode === 'note' }" @click="previewMode = 'note'">笔记预览</button>
          <button type="button" :class="{ 'is-active': previewMode === 'cover' }" @click="previewMode = 'cover'">封面预览</button>
        </div>

        <div class="publish-phone" :class="{ 'is-cover-mode': previewMode === 'cover' }">
          <div class="publish-phone__status">
            <strong>9:41</strong>
            <span><i /> <i /> <b /></span>
          </div>

          <template v-if="previewMode === 'note'">
            <div class="publish-phone__note-shell">
              <header class="publish-phone__note-topbar">
                <button type="button">‹</button>
                <strong>Vibelo</strong>
                <span>
                  <button type="button">⤴</button>
                  <button type="button">⋯</button>
                </span>
              </header>

              <section class="publish-phone__note-scroll">
                <article class="publish-phone__note-article">
                  <div v-if="currentCover" class="publish-phone__note-hero-wrap">
                    <img class="publish-phone__note-hero" :src="currentCover" alt="" />
                    <em class="publish-phone__note-index">1/{{ Math.max(1, uploadedAssets.length) }}</em>
                  </div>

                  <div class="publish-phone__note-author">
                    <img :src="currentUserAvatar" alt="" />
                    <span>
                      <strong>{{ currentUserName }}</strong>
                      <small>{{ currentChannelLabel }} · 摄影博主</small>
                    </span>
                    <button type="button">关注</button>
                  </div>

                  <section class="publish-phone__note-copy" :class="{ 'is-text-only': !currentCover }">
                    <h3>{{ form.title || '填写标题后在这里预览' }}</h3>
                    <p v-if="previewParagraphs.length === 0">输入正文后，详情页会按移动端样式展示。</p>
                    <p v-for="line in previewParagraphs" :key="line">{{ line }}</p>
                    <div class="publish-phone__note-topics">
                      <span v-for="tag in previewTagList" :key="tag">{{ tag }}</span>
                    </div>
                  </section>

                  <footer class="publish-phone__note-metrics">
                    <span>❤ 832</span>
                    <span>💬 56</span>
                    <span>⤴ 128</span>
                    <span>☆ 收藏</span>
                  </footer>
                  <p class="publish-phone__note-meta">发布于 05-12 18:42 · {{ currentPostTag }}</p>
                </article>

                <section class="publish-phone__note-comments">
                  <header>
                    <strong>评论 56</strong>
                    <span>查看全部 ›</span>
                  </header>
                  <article v-for="comment in notePreviewComments" :key="comment.id">
                    <img :src="`https://api.dicebear.com/9.x/adventurer/svg?seed=${comment.id}`" alt="" />
                    <span>
                      <strong>{{ comment.user }}</strong>
                      <p>{{ comment.content }}</p>
                    </span>
                    <em>{{ comment.likes }}</em>
                  </article>
                  <div class="publish-phone__note-comment-input">说点什么...</div>
                </section>

                <section class="publish-phone__note-related">
                  <h4>相似内容</h4>
                  <div class="publish-phone__note-related-grid">
                    <article v-for="card in noteRelatedCards" :key="`related-${card.key}`" :class="{ 'is-text': !card.cover }">
                      <img v-if="card.cover" :src="card.cover" alt="" />
                      <div v-else class="publish-phone__note-related-text">{{ card.content }}</div>
                      <p>{{ card.title }}</p>
                      <footer>
                        <span>{{ card.author }}</span>
                        <em>♡ {{ card.likes }}</em>
                      </footer>
                    </article>
                  </div>
                </section>
              </section>

              <footer class="publish-phone__note-bottom">
                <button type="button">
                  <img :src="currentUserAvatar" alt="" />
                  <span>{{ currentUserName }}</span>
                  <strong>关注</strong>
                </button>
                <div>
                  <span>❤ 832</span>
                  <span>💬 56</span>
                  <span>⤴ 128</span>
                  <span>☆</span>
                </div>
              </footer>
            </div>
          </template>

          <template v-else>
            <div class="publish-phone__feed-shell">
              <header class="publish-phone__feed-topbar">
                <strong>Vibelo</strong>
                <div>搜索用户、内容、话题</div>
                <span><i>8</i><i>12</i></span>
              </header>

              <div class="publish-phone__feed-filters">
                <strong>推荐</strong>
                <span>关注</span>
                <span>视频</span>
                <span>图文</span>
                <span>热门</span>
                <span>同城</span>
              </div>

              <section class="publish-phone__feed-waterfall">
                <article
                  v-for="card in coverPreviewCards"
                  :key="card.key"
                  class="publish-phone__feed-card"
                  :class="{ 'is-current': card.current, 'is-text': !card.cover }"
                >
                  <div class="publish-phone__feed-media" :style="card.cover ? { minHeight: card.height } : undefined">
                    <img v-if="card.cover" :src="card.cover" alt="" />
                    <div v-else class="publish-phone__feed-text">{{ card.content }}</div>
                    <small v-if="card.duration">{{ card.duration }}</small>
                  </div>
                  <div class="publish-phone__feed-meta">
                    <div class="publish-phone__feed-user">
                      <img :src="card.avatar" alt="" />
                      <span>{{ card.author }}</span>
                    </div>
                    <h4 v-if="card.cover">{{ card.title }}</h4>
                    <p>{{ card.tag }}</p>
                    <footer>
                      <span>♡ {{ card.likes }}</span>
                      <span>💬 {{ card.comments }}</span>
                    </footer>
                  </div>
                </article>
              </section>

              <footer class="publish-phone__tabbar">
                <strong>首页</strong>
                <span>发现</span>
                <b>+</b>
                <span>消息</span>
                <span>我的</span>
              </footer>
            </div>
          </template>
        </div>
      </section>
    </aside>
  </div>
</template>

<style scoped>
.publish-studio {
  display: grid;
  grid-template-columns: 232px minmax(640px, 1fr) 410px;
  gap: 16px;
  height: calc(100vh - 74px);
  padding: 14px 16px 16px;
  color: #20242f;
  background: #f7f8fa;
  overflow: hidden;
}

.publish-studio button,
.publish-studio input,
.publish-studio textarea {
  font: inherit;
}

.publish-studio__left,
.publish-studio__preview,
.publish-studio__editor {
  min-height: 0;
  height: 100%;
}

.publish-studio__left {
  display: grid;
  grid-template-rows: auto auto 1fr;
  gap: 12px;
}

.publish-studio__preview,
.publish-studio__editor {
  overflow-y: auto;
}

.publish-studio__editor {
  min-width: 0;
  padding-right: 4px;
}

.publish-studio__side-card,
.publish-studio__editor-card,
.publish-studio__preview-card,
.publish-studio__bottom-bar {
  border: 1px solid rgba(26, 31, 44, 0.07);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(32, 36, 47, 0.05);
}

.publish-studio__type-card {
  padding: 18px 14px 20px;
}

.publish-studio__type-card h2 {
  margin: 0 0 16px;
  color: #151b27;
  font-size: 18px;
  font-weight: 820;
}

.publish-studio__type-card button {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  height: 44px;
  padding: 0 14px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #3c4350;
  cursor: pointer;
  font-size: 15px;
  font-weight: 700;
}

.publish-studio__type-card button.is-active,
.publish-studio__type-card button:hover {
  background: #fff0ed;
  color: #ff5a45;
}

.publish-studio__draft-card {
  padding: 14px;
}

.publish-studio__side-title,
.publish-studio__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.publish-studio__side-title strong {
  color: #20242f;
  font-size: 15px;
}

.publish-studio__side-title em {
  color: #8a91a0;
  font-style: normal;
}

.publish-studio__side-title button {
  border: none;
  background: transparent;
  color: #8a91a0;
  cursor: pointer;
  font-size: 12px;
}

.publish-studio__draft-card article {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  margin-top: 12px;
}

.publish-studio__draft-card article img {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  object-fit: cover;
}

.publish-studio__draft-card article span {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.publish-studio__draft-card article strong {
  overflow: hidden;
  color: #303744;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.publish-studio__draft-card article small {
  color: #9aa1ad;
  font-size: 12px;
}

.publish-studio__draft-card article em {
  color: #ff7d2d;
  font-style: normal;
  font-size: 12px;
  white-space: nowrap;
}

.publish-studio__draft-card article em.is-saved {
  color: #35b56a;
}

.publish-studio__new-draft {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 36px;
  margin-top: 16px;
  border: none;
  border-radius: 8px;
  background: #fff0ed;
  color: #ff5a45;
  cursor: pointer;
  font-size: 13px;
  font-weight: 760;
}

.publish-studio__autosave {
  display: flex;
  align-items: end;
  gap: 7px;
  margin-top: auto;
  color: #8a91a0;
  font-size: 12px;
}

.publish-studio__autosave .el-icon {
  color: #35b56a;
}

.publish-studio__editor-card {
  padding: 18px;
}

.publish-studio__editor-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.publish-studio__editor-head button {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #3a414d;
  cursor: pointer;
}

.publish-studio__editor-head h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 820;
}

.publish-studio__editor-head span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: #5abc78;
  font-size: 12px;
}

.publish-studio__title-field {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  height: 58px;
  padding: 0 16px;
  border: 1px solid #eceff4;
  border-radius: 8px;
  background: #fff;
}

.publish-studio__title-field input {
  min-width: 0;
  border: none;
  outline: none;
  color: #151b27;
  font-size: 22px;
  font-weight: 820;
}

.publish-studio__title-field span,
.publish-studio__text-editor > span,
.publish-studio__section-head small {
  color: #9aa1ad;
  font-size: 12px;
}

.publish-studio__text-editor {
  position: relative;
  margin-top: 10px;
  border: 1px solid #eceff4;
  border-radius: 8px;
  overflow: hidden;
}

.publish-studio__text-editor textarea {
  width: 100%;
  min-height: 220px;
  padding: 18px 16px 38px;
  border: none;
  outline: none;
  resize: vertical;
  color: #2d3440;
  font-size: 15px;
  line-height: 1.75;
}

.publish-studio__text-editor > span {
  position: absolute;
  right: 14px;
  bottom: 10px;
}

.publish-studio__setting {
  margin-top: 18px;
}

.publish-studio__setting h3,
.publish-studio__section-head h3 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 820;
}

.publish-studio__covers {
  display: grid;
  grid-template-columns: repeat(5, minmax(88px, 1fr)) 112px;
  gap: 10px;
}

.publish-studio__asset {
  position: relative;
  overflow: hidden;
  min-height: 84px;
  border: 1px solid #e8ebf0;
  border-radius: 8px;
  background: #f1f3f7;
}

.publish-studio__asset img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.publish-studio__asset button {
  position: absolute;
  right: 6px;
  top: 6px;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  color: #3c4350;
  cursor: pointer;
}

.publish-studio__upload {
  display: grid !important;
  place-items: center;
  gap: 5px;
  width: 112px;
  height: 84px;
  border: 1px dashed #d8dde6 !important;
  border-radius: 8px;
  background: #fff !important;
  color: #535c6b;
  cursor: pointer;
}

.publish-studio__upload .el-icon {
  font-size: 24px;
}

.publish-studio__form-stack {
  display: grid;
  gap: 16px;
  margin-top: 18px;
}

.publish-studio__row {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
}

.publish-studio__row--top {
  align-items: start;
}

.publish-studio__row > strong {
  color: #242b38;
  font-size: 15px;
  font-weight: 820;
}

.publish-studio__channel-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.publish-studio__channel-tabs button {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 12px;
  border: 1px solid #e8ebf0;
  border-radius: 999px;
  background: #fff;
  color: #4e5665;
  font-size: 13px;
  cursor: pointer;
}

.publish-studio__channel-tabs button.is-selected {
  border-color: #ffd4cc;
  background: #fff0ed;
  color: #ff5a45;
}

.publish-studio__tag-panel {
  display: grid;
  gap: 10px;
}

.publish-studio__selected-tags,
.publish-studio__quick-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.publish-studio__selected-tags span,
.publish-studio__quick-tags button {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 10px;
  border: 1px solid #d4e4ff;
  border-radius: 999px;
  background: #edf4ff;
  color: #3e6ad6;
  font-size: 13px;
}

.publish-studio__selected-tags span {
  gap: 6px;
}

.publish-studio__selected-tags button {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.publish-studio__quick-tags button {
  background: #f6faff;
  cursor: pointer;
}

.publish-studio__quick-tags span {
  color: #9aa1ad;
  font-size: 13px;
}

.publish-studio__tag-entry {
  display: flex;
  align-items: center;
  gap: 8px;
}

.publish-studio__tag-entry input {
  width: 100%;
  height: 34px;
  padding: 0 12px;
  border: 1px solid #e8ebf0;
  border-radius: 8px;
  outline: none;
}

.publish-studio__tag-entry input:focus {
  border-color: #a9c3ff;
}

.publish-studio__tag-add {
  height: 34px;
  padding: 0 12px;
  border: none;
  border-radius: 8px;
  background: #edf4ff;
  color: #3e6ad6;
  cursor: pointer;
}

.publish-studio__hashtag-list {
  display: grid;
  gap: 2px;
  max-height: 256px;
  padding: 8px;
  border: 1px solid #e8ebf0;
  border-radius: 8px;
  overflow-y: auto;
  background: #fff;
}

.publish-studio__hashtag-list button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 36px;
  padding: 0 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.publish-studio__hashtag-list button:hover {
  background: #f5f8ff;
}

.publish-studio__hashtag-list strong {
  color: #253247;
  font-size: 14px;
  font-weight: 650;
}

.publish-studio__hashtag-list small,
.publish-studio__hashtag-list p {
  color: #8a91a0;
  font-size: 12px;
}

.publish-studio__hashtag-list p {
  margin: 8px;
}

.publish-studio__select {
  width: 230px;
}

.publish-studio__publish-time {
  grid-template-columns: 82px auto auto 160px;
}

.publish-studio__publish-time label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #4e5665;
  font-size: 14px;
}

.publish-studio__publish-time label.is-disabled {
  color: #a7aeba;
}

.publish-studio__publish-time input[type='text'] {
  height: 34px;
  padding: 0 12px;
  border: 1px solid #e8ebf0;
  border-radius: 8px;
  outline: none;
}

.publish-studio__publish-time input[type='radio'] {
  accent-color: #ff5a45;
}

.publish-studio__bottom-bar {
  position: sticky;
  bottom: 0;
  z-index: 10;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  border-top-left-radius: 0;
  border-top-right-radius: 0;
}

.publish-studio__bottom-bar button {
  height: 38px;
  padding: 0 18px;
  border: 1px solid #e4e8ef;
  border-radius: 8px;
  background: #fff;
  color: #2d3440;
  cursor: pointer;
  font-size: 14px;
  font-weight: 760;
}

.publish-studio__bottom-bar div {
  display: inline-flex;
  gap: 10px;
}

.publish-studio__bottom-bar .is-primary {
  min-width: 76px;
  border-color: #ff5a45;
  background: #ff5a45;
  color: #fff;
}

.publish-studio__bottom-bar .is-primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.publish-studio__preview-card {
  min-height: 100%;
  padding: 12px;
}

.publish-studio__preview-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  height: 42px;
  margin-bottom: 12px;
  padding: 4px;
  border-radius: 999px;
  background: #f1f1f1;
}

.publish-studio__preview-tabs button {
  border: none;
  border-radius: 999px;
  background: transparent;
  color: #8b8f98;
  cursor: pointer;
  font-size: 14px;
  font-weight: 720;
}

.publish-studio__preview-tabs button.is-active {
  background: #fff;
  color: #20242f;
  box-shadow: 0 8px 18px rgba(30, 35, 48, 0.08);
}

.publish-phone {
  position: relative;
  overflow: hidden;
  width: min(100%, 356px);
  height: 720px;
  margin: 0 auto;
  border: 6px solid #565656;
  border-radius: 42px;
  background: #fff;
}

.publish-phone__status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 42px;
  padding: 0 24px;
  color: #111;
  font-size: 14px;
}

.publish-phone__status span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.publish-phone__status i {
  display: block;
  width: 7px;
  height: 11px;
  border-radius: 4px 4px 1px 1px;
  background: #111;
}

.publish-phone__status i:first-child {
  height: 8px;
}

.publish-phone__status b {
  display: block;
  width: 21px;
  height: 10px;
  border: 2px solid #111;
  border-radius: 6px;
}

.publish-phone__note-shell,
.publish-phone__feed-shell {
  display: grid;
  height: calc(100% - 42px);
}

.publish-phone__note-shell {
  grid-template-rows: auto minmax(0, 1fr) auto;
}

.publish-phone__note-topbar {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  height: 42px;
  border-bottom: 1px solid #f0f2f5;
}

.publish-phone__note-topbar button {
  border: none;
  background: transparent;
  color: #1d2633;
  cursor: pointer;
  font-size: 20px;
}

.publish-phone__note-topbar > strong {
  color: #20242f;
  font-size: 17px;
  font-weight: 800;
}

.publish-phone__note-topbar > span {
  display: inline-flex;
  gap: 8px;
}

.publish-phone__note-scroll {
  overflow-y: auto;
  padding-bottom: 10px;
  background: #fcfcfd;
}

.publish-phone__note-article {
  background: #fff;
}

.publish-phone__note-hero-wrap {
  position: relative;
}

.publish-phone__note-hero {
  display: block;
  width: 100%;
  max-height: 260px;
  object-fit: cover;
}

.publish-phone__note-index {
  position: absolute;
  top: 10px;
  right: 10px;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(19, 24, 33, 0.65);
  color: #fff;
  font-size: 11px;
  font-style: normal;
  line-height: 22px;
}

.publish-phone__note-author {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 56px;
  align-items: center;
  gap: 8px;
  padding: 10px 12px 8px;
}

.publish-phone__note-author img {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
}

.publish-phone__note-author span {
  min-width: 0;
  display: grid;
  gap: 1px;
}

.publish-phone__note-author strong {
  overflow: hidden;
  color: #1f2633;
  font-size: 13px;
  font-weight: 760;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.publish-phone__note-author small {
  overflow: hidden;
  color: #9aa1ad;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.publish-phone__note-author button {
  height: 24px;
  border: 1px solid #ff4966;
  border-radius: 999px;
  background: #fff;
  color: #ff3a5b;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
}

.publish-phone__note-copy {
  padding: 0 12px 8px;
}

.publish-phone__note-copy.is-text-only {
  margin-top: 6px;
}

.publish-phone__note-copy h3 {
  margin: 0 0 8px;
  color: #20242f;
  font-size: 22px;
  line-height: 1.32;
}

.publish-phone__note-copy p {
  margin: 0 0 6px;
  color: #3f4652;
  font-size: 13px;
  line-height: 1.66;
}

.publish-phone__note-topics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 7px;
}

.publish-phone__note-topics span {
  color: #4f73d8;
  font-size: 12px;
}

.publish-phone__note-metrics {
  display: flex;
  gap: 18px;
  padding: 0 12px;
  color: #6d7380;
  font-size: 12px;
}

.publish-phone__note-meta {
  margin: 7px 0 0;
  padding: 0 12px 10px;
  border-bottom: 1px solid #f0f2f5;
  color: #a0a7b3;
  font-size: 11px;
}

.publish-phone__note-comments {
  margin-top: 8px;
  padding: 10px 12px;
  background: #fff;
}

.publish-phone__note-comments > header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.publish-phone__note-comments > header strong {
  color: #1d2431;
  font-size: 13px;
}

.publish-phone__note-comments > header span {
  color: #939aa8;
  font-size: 11px;
}

.publish-phone__note-comments article {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr) auto;
  align-items: start;
  gap: 8px;
  margin-bottom: 10px;
}

.publish-phone__note-comments article img {
  width: 26px;
  height: 26px;
  border-radius: 50%;
}

.publish-phone__note-comments article span {
  min-width: 0;
}

.publish-phone__note-comments article strong {
  display: block;
  color: #2d3441;
  font-size: 12px;
}

.publish-phone__note-comments article p {
  margin: 2px 0 0;
  color: #5a6271;
  font-size: 12px;
  line-height: 1.5;
}

.publish-phone__note-comments article em {
  color: #8f96a4;
  font-size: 11px;
  font-style: normal;
}

.publish-phone__note-comment-input {
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: #f5f6f8;
  color: #a4acb8;
  font-size: 12px;
  line-height: 30px;
}

.publish-phone__note-related {
  margin-top: 8px;
  padding: 10px 12px 14px;
  background: #fff;
}

.publish-phone__note-related h4 {
  margin: 0 0 9px;
  color: #1f2733;
  font-size: 13px;
}

.publish-phone__note-related-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.publish-phone__note-related-grid article {
  overflow: hidden;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #eceff3;
}

.publish-phone__note-related-grid img {
  width: 100%;
  height: 94px;
  object-fit: cover;
}

.publish-phone__note-related-grid .is-text .publish-phone__note-related-text {
  height: 94px;
  padding: 9px;
  background: #f5f7fb;
  color: #4d5562;
  font-size: 12px;
  line-height: 1.5;
}

.publish-phone__note-related-grid p {
  display: -webkit-box;
  margin: 6px 8px 4px;
  overflow: hidden;
  color: #252c38;
  font-size: 12px;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.publish-phone__note-related-grid footer {
  display: flex;
  justify-content: space-between;
  margin: 0 8px 8px;
  color: #8f97a5;
  font-size: 11px;
}

.publish-phone__note-related-grid em {
  font-style: normal;
}

.publish-phone__note-bottom {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  height: 56px;
  padding: 0 12px;
  border-top: 1px solid #eceff3;
  background: #fff;
}

.publish-phone__note-bottom > button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 10px 0 4px;
  border: none;
  border-radius: 999px;
  background: #fff5f2;
  color: #20242f;
}

.publish-phone__note-bottom > button img {
  width: 24px;
  height: 24px;
  border-radius: 50%;
}

.publish-phone__note-bottom > button span {
  max-width: 74px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}

.publish-phone__note-bottom > button strong {
  color: #ff4a5f;
  font-size: 12px;
}

.publish-phone__note-bottom > div {
  display: inline-flex;
  gap: 10px;
  color: #646d7b;
  font-size: 12px;
}

.publish-phone__feed-shell {
  grid-template-rows: auto auto minmax(0, 1fr) auto;
}

.publish-phone__feed-topbar {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  height: 42px;
}

.publish-phone__feed-topbar > strong {
  color: #ff5a45;
  font-size: 19px;
  font-weight: 860;
}

.publish-phone__feed-topbar > div {
  overflow: hidden;
  height: 26px;
  padding: 0 12px;
  border-radius: 999px;
  background: #f3f4f6;
  color: #a4abb7;
  font-size: 11px;
  line-height: 26px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.publish-phone__feed-topbar > span {
  display: inline-flex;
  gap: 4px;
}

.publish-phone__feed-topbar > span i {
  min-width: 16px;
  height: 16px;
  padding: 0 3px;
  border-radius: 999px;
  background: #ff5a45;
  color: #fff;
  font-size: 10px;
  font-style: normal;
  line-height: 16px;
  text-align: center;
}

.publish-phone__feed-filters {
  display: flex;
  gap: 8px;
  padding: 6px 10px 9px;
  overflow: hidden;
  white-space: nowrap;
}

.publish-phone__feed-filters strong,
.publish-phone__feed-filters span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
}

.publish-phone__feed-filters strong {
  background: #fff0ed;
  color: #ff5a45;
}

.publish-phone__feed-filters span {
  border: 1px solid #eceff3;
  color: #6d7482;
}

.publish-phone__feed-waterfall {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 0 10px 10px;
  overflow-y: auto;
}

.publish-phone__feed-card {
  align-self: start;
  border: 1px solid #edf0f4;
  border-radius: 10px;
  background: #fff;
}

.publish-phone__feed-media {
  position: relative;
  overflow: hidden;
  border-radius: 10px 10px 0 0;
  background: #e8ecf1;
}

.publish-phone__feed-media img {
  width: 100%;
  min-height: 128px;
  object-fit: cover;
}

.publish-phone__feed-text {
  display: -webkit-box;
  padding: 10px;
  overflow: hidden;
  color: #48505d;
  font-size: 12px;
  line-height: 1.58;
  white-space: normal;
  word-break: break-word;
  text-overflow: ellipsis;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 10;
}

.publish-phone__feed-card.is-text .publish-phone__feed-media {
  background: #f5f7fb;
}

.publish-phone__feed-media small {
  position: absolute;
  right: 7px;
  bottom: 7px;
  height: 19px;
  padding: 0 6px;
  border-radius: 999px;
  background: rgba(16, 20, 28, 0.75);
  color: #fff;
  font-size: 10px;
  line-height: 19px;
}

.publish-phone__feed-meta {
  padding: 7px 8px;
}

.publish-phone__feed-user {
  display: flex;
  align-items: center;
  gap: 6px;
}

.publish-phone__feed-user img {
  width: 16px;
  height: 16px;
  border-radius: 50%;
}

.publish-phone__feed-user span {
  overflow: hidden;
  color: #5d6573;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.publish-phone__feed-meta h4 {
  display: -webkit-box;
  margin: 6px 0 4px;
  overflow: hidden;
  color: #1f2633;
  font-size: 12px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.publish-phone__feed-meta p {
  margin: 0;
  color: #4f73d8;
  font-size: 11px;
}

.publish-phone__feed-meta footer {
  display: flex;
  gap: 10px;
  margin-top: 6px;
  color: #7f8796;
  font-size: 11px;
}

.publish-phone__tabbar {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  align-items: center;
  height: 58px;
  border-top: 1px solid #eceff3;
  background: #fff;
  color: #8f96a4;
  text-align: center;
  font-size: 12px;
}

.publish-phone__tabbar strong {
  color: #ff5a45;
}

.publish-phone__tabbar b {
  display: grid;
  place-items: center;
  width: 33px;
  height: 33px;
  margin: 0 auto;
  border-radius: 50%;
  background: #ff5a45;
  color: #fff;
  font-size: 24px;
  line-height: 1;
}

@media (max-width: 1360px) {
  .publish-studio {
    grid-template-columns: 220px minmax(560px, 1fr) 380px;
  }
}

@media (max-width: 1180px) {
  .publish-studio {
    grid-template-columns: 210px minmax(0, 1fr);
  }

  .publish-studio__preview {
    display: none;
  }
}

@media (max-width: 860px) {
  .publish-studio {
    display: block;
    height: auto;
    min-height: calc(100vh - 74px);
    padding: 10px;
    overflow: visible;
  }

  .publish-studio__left,
  .publish-studio__editor {
    height: auto;
    overflow: visible;
  }

  .publish-studio__left {
    grid-template-rows: auto;
    margin-bottom: 12px;
  }

  .publish-studio__draft-card,
  .publish-studio__autosave {
    display: none;
  }

  .publish-studio__type-card {
    display: flex;
    gap: 8px;
    overflow-x: auto;
    padding: 10px;
  }

  .publish-studio__type-card h2 {
    display: none;
  }

  .publish-studio__type-card button {
    flex: 0 0 auto;
    width: auto;
    height: 38px;
  }

  .publish-studio__title-field input {
    font-size: 18px;
  }

  .publish-studio__covers {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .publish-studio__upload {
    width: 100%;
  }

  .publish-studio__row,
  .publish-studio__publish-time {
    grid-template-columns: 1fr;
    align-items: start;
  }
}
</style>
