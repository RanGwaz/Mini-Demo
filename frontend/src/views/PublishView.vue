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
  Promotion,
  Share,
  Star,
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

const sampleCoverCards = [
  { key: 'sample-1', title: '示例笔记标题1', author: '用户名', height: '182px' },
  { key: 'sample-2', title: '示例笔记标题2', author: '用户名', height: '214px' },
  { key: 'sample-3', title: '示例笔记标题3', author: '用户名', height: '190px' },
]

const titleCount = computed(() => form.title.length)
const contentCount = computed(() => form.content.length)
const currentChannelLabel = computed(() => publishChannels.find((item) => item.key === activeChannel.value)?.label || publishChannels[0]?.label || '')
const currentUserName = computed(() => authStore.currentUser?.nickname || 'Vibelo 用户')
const currentUserAvatar = computed(() => authStore.currentUser?.avatarUrl || 'https://api.dicebear.com/9.x/adventurer/svg?seed=creator')
const hasImages = computed(() => uploadedAssets.value.length > 0)
const currentCover = computed(() => selectedKind.value === 'image' && hasImages.value ? resolveAssetCover(uploadedAssets.value[0]) : '')
const previewParagraphs = computed(() => form.content.split('\n').map((line) => line.trim()).filter(Boolean).slice(0, 5))
const normalizedTagKeyword = computed(() => tagInput.value.trim().replace(/^#+/, '').replace(/\s+/g, ''))
const showHashtagPanel = computed(() => tagInput.value.trim().startsWith('#'))
const hashtagCandidates = computed(() => {
  const keyword = normalizedTagKeyword.value
  if (!keyword) return hashtagRecommendations.value
  return hashtagRecommendations.value.filter((item) => item.name.includes(keyword))
})
const coverPreviewCards = computed(() => [
  {
    key: 'current',
    title: form.title.trim() || '示例笔记标题1',
    author: currentUserName.value,
    cover: currentCover.value,
    content: form.content.trim(),
    current: true,
    height: '142px',
  },
  ...sampleCoverCards.map((item) => ({ ...item, cover: '', content: '', current: false })),
])
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
  if (selectedTags.value.length >= 10) {
    ElMessage.info('最多添加 10 个标签')
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
    const finalTags = uniqueTags(selectedTags.value).slice(0, 10)
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
            <strong>扩展信息</strong>
            <PublishFormRenderer v-model="channelExtra" :channel-code="activeChannel" />
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
            <header class="publish-phone__note-head">
              <button type="button">‹</button>
              <img :src="currentUserAvatar" alt="" />
              <span>
                <strong>{{ currentUserName }}</strong>
                <small>{{ currentChannelLabel }}</small>
              </span>
              <button type="button" class="is-follow">关注</button>
              <el-icon><Share /></el-icon>
            </header>

            <img v-if="currentCover" class="publish-phone__hero" :src="currentCover" alt="" />
            <section class="publish-phone__note-body" :class="{ 'is-text-only': !currentCover }">
              <h3>{{ form.title || '填写标题后在这里预览' }}</h3>
              <p v-if="previewParagraphs.length === 0">输入正文后，移动端笔记内容会在这里展示。</p>
              <p v-for="line in previewParagraphs" :key="line">{{ line }}</p>
              <div v-if="selectedTags.length > 0" class="publish-phone__tags">
                <span v-for="tag in selectedTags.slice(0, 4)" :key="tag">#{{ tag }}</span>
              </div>
            </section>

            <section class="publish-phone__comment-empty">
              <img :src="currentUserAvatar" alt="" />
              <div>说点什么，让大家认识这篇笔记</div>
            </section>

            <footer class="publish-phone__note-actions">
              <span><el-icon><Promotion /></el-icon> 说点什么...</span>
              <button type="button"><span>♡</span>点赞</button>
              <button type="button"><el-icon><Star /></el-icon>收藏</button>
              <button type="button"><el-icon><ChatLineRound /></el-icon>评论</button>
            </footer>
          </template>

          <template v-else>
            <header class="publish-phone__discover-head">
              <button type="button">☰</button>
              <nav>
                <span>关注</span>
                <strong>发现</strong>
                <span>附近</span>
              </nav>
              <button type="button">⌕</button>
            </header>
            <div class="publish-phone__discover-tabs">
              <strong>推荐</strong>
              <span>直播</span>
              <span>短剧</span>
              <span>穿搭</span>
              <span>旅行</span>
              <span>动漫</span>
            </div>
            <section class="publish-phone__waterfall">
              <article
                v-for="card in coverPreviewCards"
                :key="card.key"
                class="publish-phone__cover-card"
                :class="{ 'is-current': card.current, 'is-text-only': !card.cover }"
              >
                <img v-if="card.cover" :src="card.cover" alt="" />
                <div v-else class="publish-phone__cover-placeholder" :style="{ height: card.height || '148px' }">
                  <p v-if="card.current">{{ card.content || '纯文本笔记会以文字卡片形式出现在信息流。' }}</p>
                </div>
                <h4>{{ card.title }}</h4>
                <footer>
                  <span>{{ card.author }}</span>
                  <em>♡ 0</em>
                </footer>
              </article>
            </section>
            <footer class="publish-phone__tabbar">
              <strong>首页</strong>
              <span>市集</span>
              <b>+</b>
              <span>消息</span>
              <span>我</span>
            </footer>
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

.publish-phone__note-head,
.publish-phone__discover-head {
  display: grid;
  align-items: center;
  min-height: 44px;
  padding: 0 14px;
}

.publish-phone__note-head {
  grid-template-columns: 24px 34px minmax(0, 1fr) 54px 24px;
  gap: 8px;
}

.publish-phone__note-head button,
.publish-phone__discover-head button {
  border: none;
  background: transparent;
  color: #1f2632;
  cursor: pointer;
}

.publish-phone__note-head button:first-child {
  font-size: 28px;
  line-height: 1;
}

.publish-phone__note-head img {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
}

.publish-phone__note-head span {
  min-width: 0;
  display: grid;
}

.publish-phone__note-head strong {
  overflow: hidden;
  color: #18202d;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.publish-phone__note-head small {
  color: #8b93a1;
  font-size: 11px;
}

.publish-phone__note-head .is-follow {
  height: 26px;
  border: 1px solid #ff4560;
  border-radius: 999px;
  color: #ff3150;
  font-size: 12px;
  font-weight: 760;
}

.publish-phone__hero {
  display: block;
  width: 100%;
  max-height: 270px;
  object-fit: cover;
}

.publish-phone__note-body {
  min-height: 170px;
  padding: 18px 18px 10px;
}

.publish-phone__note-body.is-text-only {
  min-height: 310px;
}

.publish-phone__note-body h3 {
  margin: 0 0 10px;
  color: #20242f;
  font-size: 18px;
  line-height: 1.35;
}

.publish-phone__note-body p {
  margin: 0 0 8px;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.72;
  white-space: pre-wrap;
}

.publish-phone__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.publish-phone__tags span {
  color: #496fd2;
  font-size: 12px;
}

.publish-phone__comment-empty {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  margin: 0 18px;
  padding-top: 22px;
  color: #b0b5be;
  font-size: 12px;
}

.publish-phone__comment-empty img {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
}

.publish-phone__comment-empty div {
  height: 30px;
  padding: 0 14px;
  border-radius: 999px;
  background: #f5f5f6;
  line-height: 30px;
}

.publish-phone__note-actions {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto auto;
  align-items: center;
  gap: 10px;
  height: 58px;
  padding: 0 14px 8px;
  border-top: 1px solid #f0f1f3;
  background: #fff;
}

.publish-phone__note-actions span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background: #f5f6f8;
  color: #9aa1ad;
  font-size: 12px;
}

.publish-phone__note-actions button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  border: none;
  background: transparent;
  color: #20242f;
  cursor: pointer;
  font-size: 12px;
}

.publish-phone__discover-head {
  grid-template-columns: 30px minmax(0, 1fr) 30px;
}

.publish-phone__discover-head nav {
  display: flex;
  justify-content: center;
  gap: 25px;
  color: #a1a6af;
  font-size: 14px;
}

.publish-phone__discover-head strong {
  color: #20242f;
}

.publish-phone__discover-tabs {
  display: flex;
  gap: 18px;
  height: 30px;
  padding: 0 14px;
  overflow: hidden;
  color: #9ca2ad;
  font-size: 12px;
  white-space: nowrap;
}

.publish-phone__discover-tabs strong {
  color: #20242f;
}

.publish-phone__waterfall {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  height: calc(100% - 130px);
  padding: 0 12px 70px;
  overflow: hidden;
}

.publish-phone__cover-card {
  overflow: hidden;
  align-self: start;
  border-radius: 3px;
  background: #fff;
}

.publish-phone__cover-card img,
.publish-phone__cover-placeholder {
  display: block;
  width: 100%;
  min-height: 136px;
  border-radius: 3px;
  background: #e9e9e9;
  object-fit: cover;
}

.publish-phone__cover-card.is-current.is-text-only .publish-phone__cover-placeholder {
  min-height: 142px;
  padding: 12px;
  background: linear-gradient(180deg, #fbfcff, #f3f6fb);
}

.publish-phone__cover-placeholder p {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: #4b5563;
  font-size: 12px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 6;
}

.publish-phone__cover-card h4 {
  display: -webkit-box;
  margin: 7px 4px 4px;
  overflow: hidden;
  color: #222936;
  font-size: 13px;
  line-height: 1.35;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.publish-phone__cover-card footer {
  display: flex;
  justify-content: space-between;
  gap: 6px;
  padding: 0 4px 9px;
  color: #8b93a1;
  font-size: 11px;
}

.publish-phone__cover-card em {
  color: #20242f;
  font-style: normal;
}

.publish-phone__tabbar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  align-items: center;
  height: 58px;
  padding-bottom: 8px;
  border-top: 1px solid #f0f1f3;
  background: #fff;
  color: #979da8;
  text-align: center;
  font-size: 13px;
}

.publish-phone__tabbar strong {
  color: #20242f;
}

.publish-phone__tabbar b {
  display: grid;
  place-items: center;
  width: 34px;
  height: 30px;
  margin: 0 auto;
  border-radius: 9px;
  background: #ff3150;
  color: #fff;
  font-size: 22px;
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
