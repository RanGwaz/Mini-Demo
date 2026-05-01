<script setup lang="ts">
defineOptions({ name: 'PublishView' })

import {
  ArrowLeft,
  ChatLineRound,
  Check,
  CircleCheck,
  Close,
  Document,
  MagicStick,
  MoreFilled,
  Picture,
  Plus,
  PriceTag,
  Promotion,
  RefreshRight,
  Share,
  Star,
  UploadFilled,
  Warning,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  defaultPublishChannelKey,
  featuredAudienceChannels,
  publishChannels,
  tagGroups,
  type PublishChannelKey,
} from '../domain/contentTaxonomy'
import { api } from '../services/api'
import { useAuthStore } from '../stores/auth'
import type { UploadResponse } from '../types'

type PublishKind = 'text' | 'image'
type PreviewMode = 'desktop' | 'mobile'

const router = useRouter()
const authStore = useAuthStore()
authStore.hydrate()

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const selectedKind = ref<PublishKind>('text')
const previewMode = ref<PreviewMode>('desktop')
const uploadedAssets = ref<UploadResponse[]>([])
const customTag = ref('')
const activeChannel = ref<PublishChannelKey>(defaultPublishChannelKey)
const visibility = ref('公开 - 所有人可见')

const form = reactive({
  title: '程序员期末周摸鱼自救指南',
  content: '图书馆写作业写到一半，突然发现番茄钟、AI总结和校园咖啡续命真的可以组成一个稳定工作流。\n\n今天先记录几个很实用的小技巧：先列任务，再让 AI 拆成 25 分钟小块，最后用照片或文字复盘当天进度。',
})

const publishTypes = [
  { key: 'text', label: '纯文字', icon: Document },
  { key: 'image', label: '图文', icon: Picture },
] satisfies Array<{ key: PublishKind; label: string; icon: typeof Document }>

const drafts = [
  { title: '宿舍桌面改造记录', time: '今天 14:30', status: '编辑中', image: 'https://picsum.photos/seed/draft-dorm/120/90' },
  { title: 'AI工具学习笔记', time: '今天 10:12', status: '编辑中', image: 'https://picsum.photos/seed/draft-ai/120/90' },
  { title: '校园咖啡地图', time: '昨天 22:45', status: '已保存', image: 'https://picsum.photos/seed/draft-campus/120/90' },
]

const selectedTags = ref(['学习笔记', '效率工具'])

const syncCommunities = featuredAudienceChannels.slice(0, 3).map((channel) => ({
  name: channel.label,
  members: channel.signal.replace('活跃', '成员'),
  avatar: `https://api.dicebear.com/9.x/adventurer/svg?seed=${channel.key}`,
}))

const aiTitleSuggestions = [
  '期末周也能稳住节奏的校园效率流',
  '一个学生党也能马上用起来的 AI 学习流程',
  '从摸鱼到复盘：我的校园任务管理小方法',
]

const aiTags = tagGroups.flatMap((group) => group.tags).slice(0, 5).map((tag) => `# ${tag}`)

const previewComments = [
  { name: '课间小岛', text: '这个拆任务方法太适合期末周了', time: '12分钟前', avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=comment-1' },
  { name: '代码摸鱼中', text: '番茄钟 + AI 总结，我今晚就试试', time: '8分钟前', avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=comment-2' },
]

const titleCount = computed(() => form.title.length)
const contentCount = computed(() => form.content.length)
const currentChannelLabel = computed(() => publishChannels.find((item) => item.key === activeChannel.value)?.label || publishChannels[0]?.label || '综合')
const currentUserName = computed(() => authStore.currentUser?.nickname || 'Vibelo 用户')
const currentUserAvatar = computed(() => authStore.currentUser?.avatarUrl || 'https://api.dicebear.com/9.x/adventurer/svg?seed=creator')
const hasImages = computed(() => uploadedAssets.value.length > 0)
const currentCover = computed(() => hasImages.value ? resolveAssetCover(uploadedAssets.value[0]) : '')
const previewParagraphs = computed(() => form.content.split('\n').filter(Boolean).slice(0, 4))
const canPublish = computed(() => {
  if (loading.value || uploading.value) return false
  if (!form.title.trim() || !form.content.trim()) return false
  if (selectedKind.value === 'image' && uploadedAssets.value.length === 0) return false
  return true
})

function resolveAssetCover(asset: UploadResponse) {
  return (asset.thumbUrl || asset.fileUrl).replace('http://localhost:9000', '/minio-img')
}

function toggleTag(tag: string) {
  selectedTags.value = selectedTags.value.includes(tag)
    ? selectedTags.value.filter((item) => item !== tag)
    : [...selectedTags.value, tag]
}

function removeTag(tag: string) {
  selectedTags.value = selectedTags.value.filter((item) => item !== tag)
}

function uniqueTags(tags: string[]) {
  const seen = new Set<string>()
  const result: string[] = []
  for (const item of tags) {
    const normalized = item.trim()
    if (!normalized || seen.has(normalized)) continue
    seen.add(normalized)
    result.push(normalized)
  }
  return result
}

function selectPublishChannel(channelKey: PublishChannelKey) {
  if (activeChannel.value === channelKey) return
  activeChannel.value = channelKey
}

function addCustomTag() {
  const tag = customTag.value.trim().replace(/^#/, '')
  if (!tag) return
  if (selectedTags.value.includes(tag)) {
    customTag.value = ''
    return
  }
  if (selectedTags.value.length >= 10) {
    ElMessage.info('最多选择 10 个标签')
    return
  }
  selectedTags.value = [...selectedTags.value, tag]
  customTag.value = ''
}

async function onSelectFile(uploadFile: { raw?: File }) {
  if (!uploadFile.raw || uploading.value) return
  uploading.value = true
  try {
    const response = await api.uploadImage(uploadFile.raw)
    uploadedAssets.value.push(response)
    selectedKind.value = 'image'
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
  }
}

function saveDraft() {
  saving.value = true
  window.setTimeout(() => {
    saving.value = false
    ElMessage.success('草稿已保存')
  }, 450)
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
    ElMessage.warning('图文模式请上传图片，或切换为纯文字')
    return
  }

  loading.value = true
  try {
    const finalTags = uniqueTags(selectedTags.value).slice(0, 10)
    const assets = uploadedAssets.value.map((item, index) => ({
      objectKey: item.objectKey,
      fileUrl: item.fileUrl,
      fileType: item.fileType,
      thumbUrl: item.thumbUrl,
      width: item.width,
      height: item.height,
      sortOrder: index,
    }))
    await api.createPost({
      title: form.title.trim(),
      content: form.content.trim(),
      channel: activeChannel.value,
      tags: finalTags,
      ...(assets.length > 0 ? { assets } : {}),
    })
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
        草稿自动保存于 14:35:50
      </footer>
    </aside>

    <main class="publish-studio__editor">
      <section class="publish-studio__editor-card">
        <div class="publish-studio__editor-head">
          <button type="button" @click="router.back()">
            <el-icon><ArrowLeft /></el-icon>
          </button>
          <h1>{{ selectedKind === 'image' ? '新建图文' : '新建纯文字' }}</h1>
          <span>
            <el-icon><Check /></el-icon>
            已保存
          </span>
        </div>

        <label class="publish-studio__title-field">
          <input v-model="form.title" maxlength="100" placeholder="写一个清楚、有辨识度的标题" />
          <span>{{ titleCount }}/100</span>
        </label>

        <section class="publish-studio__text-editor">
          <textarea v-model="form.content" maxlength="1024" placeholder="分享你的经验、日常、灵感或观点" />
          <span>{{ contentCount }}/1024</span>
        </section>

        <section class="publish-studio__setting">
          <div class="publish-studio__section-head">
            <h3>图片</h3>
            <small>纯文字可不上传，图文模式至少上传 1 张</small>
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
                {{ uploading ? '上传中' : '上传图片' }}
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
              <div class="publish-studio__selected-tags">
                <span v-for="tag in selectedTags" :key="tag">
                  # {{ tag }}
                  <button type="button" @click="removeTag(tag)">×</button>
                </span>
              </div>

              <section v-for="group in tagGroups" :key="group.title" class="publish-studio__tag-group">
                <h4>{{ group.title }}</h4>
                <button
                  v-for="tag in group.tags"
                  :key="tag"
                  type="button"
                  :class="{ 'is-selected': selectedTags.includes(tag) }"
                  @click="toggleTag(tag)"
                >
                  # {{ tag }}
                </button>
              </section>

              <form class="publish-studio__custom-tag" @submit.prevent="addCustomTag">
                <input v-model="customTag" maxlength="18" placeholder="自定义标签" />
                <button type="submit">
                  <el-icon><Plus /></el-icon>
                  添加
                </button>
              </form>
            </div>
          </div>

          <div class="publish-studio__row">
            <strong>社群同步</strong>
            <div class="publish-studio__community-sync">
              <span>同时发布到</span>
              <article v-for="community in syncCommunities" :key="community.name">
                <img :src="community.avatar" alt="" />
                <b>{{ community.name }}</b>
                <small>{{ community.members }}</small>
              </article>
            </div>
          </div>

          <div class="publish-studio__row">
            <strong>可见范围</strong>
            <el-select v-model="visibility" class="publish-studio__select">
              <el-option label="公开 - 所有人可见" value="公开 - 所有人可见" />
              <el-option label="仅粉丝可见" value="仅粉丝可见" />
              <el-option label="仅自己可见" value="仅自己可见" />
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
          <button type="button" class="is-ghost">预览</button>
          <button type="button" class="is-primary" :disabled="!canPublish" @click="submit">
            {{ loading ? '发布中...' : '发布' }}
          </button>
        </div>
      </section>
    </main>

    <aside class="publish-studio__preview">
      <section class="publish-studio__preview-card">
        <h2>预览</h2>
        <div class="publish-studio__preview-tabs">
          <button type="button" :class="{ 'is-active': previewMode === 'desktop' }" @click="previewMode = 'desktop'">桌面端</button>
          <button type="button" :class="{ 'is-active': previewMode === 'mobile' }" @click="previewMode = 'mobile'">移动端</button>
        </div>

        <article :class="['publish-studio__post-preview', `is-${previewMode}`]">
          <header>
            <img :src="currentUserAvatar" alt="" />
            <div>
              <strong>{{ currentUserName }}</strong>
              <small>刚刚 · {{ selectedKind === 'image' ? '图文' : '纯文字' }} · {{ currentChannelLabel }}</small>
            </div>
            <el-icon><MoreFilled /></el-icon>
          </header>
          <h3>{{ form.title || '未填写标题' }}</h3>
          <p v-for="line in previewParagraphs" :key="line">{{ line }}</p>
          <div class="publish-studio__preview-tags">
            <span v-for="tag in selectedTags.slice(0, 4)" :key="tag">#{{ tag }}</span>
          </div>
          <img v-if="currentCover" class="publish-studio__preview-cover" :src="currentCover" alt="" />
          <div class="publish-studio__preview-actions">
            <span class="is-like">♡ 0</span>
            <span><el-icon><ChatLineRound /></el-icon> 0</span>
            <span><el-icon><Share /></el-icon></span>
            <span><el-icon><Star /></el-icon></span>
          </div>
          <div class="publish-studio__preview-comments">
            <h4>评论预览</h4>
            <article v-for="comment in previewComments" :key="comment.name">
              <img :src="comment.avatar" alt="" />
              <span>
                <strong>{{ comment.name }}</strong>
                {{ comment.text }}
              </span>
              <small>{{ comment.time }}</small>
            </article>
          </div>
          <footer>
            <input type="text" placeholder="说点什么..." />
            <el-icon><Promotion /></el-icon>
          </footer>
        </article>
      </section>
    </aside>

    <aside class="publish-studio__ai">
      <section class="publish-studio__ai-card publish-studio__ai-tabs">
        <button type="button" class="is-active">AI 助手</button>
        <button type="button">创作灵感</button>
      </section>

      <section class="publish-studio__ai-card">
        <div class="publish-studio__ai-head">
          <strong><el-icon><MagicStick /></el-icon> 标题建议</strong>
          <button type="button"><el-icon><RefreshRight /></el-icon></button>
        </div>
        <ol>
          <li v-for="title in aiTitleSuggestions" :key="title">{{ title }}</li>
        </ol>
      </section>

      <section class="publish-studio__ai-card">
        <div class="publish-studio__ai-head">
          <strong><el-icon><PriceTag /></el-icon> 标签推荐</strong>
          <button type="button"><el-icon><RefreshRight /></el-icon></button>
        </div>
        <div class="publish-studio__ai-tags">
          <span v-for="tag in aiTags" :key="tag">{{ tag }}</span>
        </div>
      </section>

      <section class="publish-studio__ai-card publish-studio__risk-card">
        <strong><el-icon><Warning /></el-icon> 发布检查</strong>
        <span>{{ selectedKind === 'image' && !hasImages ? '图文模式还需要上传图片' : '内容结构完整，可以发布' }}</span>
      </section>
    </aside>
  </div>
</template>

<style scoped>
.publish-studio {
  display: grid;
  grid-template-columns: 232px minmax(560px, 1fr) 370px 300px;
  gap: 16px;
  min-height: calc(100vh - 74px);
  padding: 14px 16px 22px;
  color: #20242f;
  background: #f7f8fa;
}

.publish-studio button,
.publish-studio input,
.publish-studio textarea {
  font: inherit;
}

.publish-studio__left,
.publish-studio__preview,
.publish-studio__ai {
  position: sticky;
  top: 88px;
  align-self: start;
  max-height: calc(100vh - 104px);
}

.publish-studio__left {
  display: grid;
  gap: 12px;
  min-height: calc(100vh - 104px);
}

.publish-studio__preview,
.publish-studio__ai {
  overflow-y: auto;
  padding-right: 2px;
}

.publish-studio__side-card,
.publish-studio__editor-card,
.publish-studio__preview-card,
.publish-studio__ai-card,
.publish-studio__bottom-bar {
  border: 1px solid rgba(26, 31, 44, 0.07);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(32, 36, 47, 0.05);
}

.publish-studio__type-card {
  padding: 18px 14px 20px;
}

.publish-studio__type-card h2,
.publish-studio__preview-card h2 {
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
.publish-studio__ai-head,
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

.publish-studio__side-title button,
.publish-studio__ai-head button {
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
  align-items: center;
  gap: 7px;
  align-self: end;
  color: #8a91a0;
  font-size: 12px;
}

.publish-studio__autosave .el-icon {
  color: #35b56a;
}

.publish-studio__editor {
  min-width: 0;
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
  min-height: 210px;
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

.publish-studio__tag-panel {
  display: grid;
  gap: 12px;
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

.publish-studio__selected-tags,
.publish-studio__tag-group,
.publish-studio__community-sync {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.publish-studio__selected-tags span,
.publish-studio__tag-group button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid #e8ebf0;
  border-radius: 999px;
  background: #fff;
  color: #4e5665;
  font-size: 13px;
}

.publish-studio__selected-tags span {
  border-color: #ffd4cc;
  background: #fff0ed;
  color: #ff5a45;
}

.publish-studio__selected-tags button {
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.publish-studio__tag-group h4 {
  flex: 0 0 100%;
  margin: 0;
  color: #8a91a0;
  font-size: 12px;
}

.publish-studio__tag-group button {
  cursor: pointer;
}

.publish-studio__tag-group button.is-selected {
  border-color: #ff5a45;
  color: #ff5a45;
}

.publish-studio__custom-tag {
  display: flex;
  gap: 8px;
}

.publish-studio__custom-tag input {
  width: 180px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid #e8ebf0;
  border-radius: 8px;
  outline: none;
}

.publish-studio__custom-tag button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 12px;
  border: none;
  border-radius: 8px;
  background: #fff0ed;
  color: #ff5a45;
  cursor: pointer;
}

.publish-studio__community-sync > span {
  color: #9aa1ad;
  font-size: 13px;
}

.publish-studio__community-sync article {
  display: inline-grid;
  grid-template-columns: 26px auto;
  align-items: center;
  column-gap: 7px;
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid #e8ebf0;
  border-radius: 10px;
  background: #fff;
}

.publish-studio__community-sync article img {
  grid-row: span 2;
  width: 26px;
  height: 26px;
  border-radius: 50%;
}

.publish-studio__community-sync article b {
  color: #303744;
  font-size: 12px;
}

.publish-studio__community-sync article small {
  color: #9aa1ad;
  font-size: 11px;
}

.publish-studio__select {
  width: 210px;
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
  padding: 18px 16px;
}

.publish-studio__preview-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  width: 210px;
  height: 38px;
  margin: 0 auto 16px;
  padding: 4px;
  border-radius: 8px;
  background: #f1f3f6;
}

.publish-studio__preview-tabs button {
  border: none;
  border-radius: 7px;
  background: transparent;
  color: #7e8795;
  cursor: pointer;
  font-size: 13px;
  font-weight: 760;
}

.publish-studio__preview-tabs button.is-active {
  background: #fff;
  color: #303744;
  box-shadow: 0 6px 14px rgba(32, 36, 47, 0.08);
}

.publish-studio__post-preview {
  overflow: hidden;
  margin: 0 auto;
  border: 1px solid #e8ebf0;
  border-radius: 14px;
  background: #fff;
}

.publish-studio__post-preview.is-mobile {
  max-width: 300px;
}

.publish-studio__post-preview header {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 16px 16px 10px;
}

.publish-studio__post-preview header img {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
}

.publish-studio__post-preview header div {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.publish-studio__post-preview header strong {
  color: #20242f;
  font-size: 14px;
}

.publish-studio__post-preview header small {
  color: #9aa1ad;
  font-size: 12px;
}

.publish-studio__post-preview h3 {
  margin: 8px 16px;
  color: #1f2632;
  font-size: 17px;
  line-height: 1.45;
}

.publish-studio__post-preview p {
  margin: 0 16px 2px;
  color: #333b49;
  font-size: 14px;
  line-height: 1.45;
}

.publish-studio__preview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 10px 16px 12px;
}

.publish-studio__preview-tags span {
  color: #4c79d8;
  font-size: 13px;
}

.publish-studio__preview-cover {
  display: block;
  width: calc(100% - 32px);
  aspect-ratio: 16 / 10;
  margin: 0 16px 12px;
  border-radius: 8px;
  object-fit: cover;
}

.publish-studio__preview-actions {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 0 16px 12px;
  border-bottom: 1px solid #eef1f5;
  color: #596171;
  font-size: 13px;
}

.publish-studio__preview-actions span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.publish-studio__preview-actions .is-like {
  color: #ff5a45;
}

.publish-studio__preview-comments {
  padding: 12px 16px 4px;
}

.publish-studio__preview-comments h4 {
  margin: 0 0 10px;
  font-size: 13px;
}

.publish-studio__preview-comments article {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  gap: 7px;
  align-items: center;
  margin-bottom: 8px;
  color: #5f6674;
  font-size: 12px;
}

.publish-studio__preview-comments img {
  width: 24px;
  height: 24px;
  border-radius: 50%;
}

.publish-studio__preview-comments span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.publish-studio__preview-comments strong {
  margin-right: 5px;
  color: #303744;
}

.publish-studio__preview-comments small {
  color: #a0a7b3;
}

.publish-studio__post-preview footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px 16px;
}

.publish-studio__post-preview footer input {
  min-width: 0;
  flex: 1;
  height: 36px;
  padding: 0 12px;
  border: 1px solid #e8ebf0;
  border-radius: 8px;
  outline: none;
}

.publish-studio__ai {
  display: grid;
  gap: 12px;
}

.publish-studio__ai-card {
  padding: 16px;
}

.publish-studio__ai-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: 0;
  border-bottom: 1px solid #eef1f5;
  box-shadow: none;
}

.publish-studio__ai-tabs button {
  position: relative;
  height: 52px;
  border: none;
  background: transparent;
  color: #7e8795;
  cursor: pointer;
  font-weight: 760;
}

.publish-studio__ai-tabs button.is-active {
  color: #ff5a45;
}

.publish-studio__ai-tabs button.is-active::after {
  content: '';
  position: absolute;
  left: 22px;
  right: 22px;
  bottom: 0;
  height: 2px;
  background: #ff5a45;
}

.publish-studio__ai-head strong,
.publish-studio__risk-card strong {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #303744;
  font-size: 14px;
  font-weight: 820;
}

.publish-studio__ai-card ol {
  display: grid;
  gap: 10px;
  margin: 14px 0 0;
  padding-left: 18px;
  color: #586171;
  font-size: 13px;
  line-height: 1.5;
}

.publish-studio__ai-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.publish-studio__ai-tags span {
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #f6f7f9;
  color: #586171;
  font-size: 12px;
  line-height: 28px;
}

.publish-studio__risk-card {
  display: grid;
  gap: 8px;
  background: #f8fff9;
}

.publish-studio__risk-card span {
  color: #35a766;
  font-size: 13px;
}

@media (max-width: 1580px) {
  .publish-studio {
    grid-template-columns: 220px minmax(560px, 1fr) 350px;
  }

  .publish-studio__ai {
    display: none;
  }
}

@media (max-width: 1220px) {
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
    padding: 10px;
  }

  .publish-studio__left,
  .publish-studio__preview,
  .publish-studio__ai {
    position: static;
    max-height: none;
  }

  .publish-studio__left {
    min-height: 0;
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
