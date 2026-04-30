<script setup lang="ts">
defineOptions({ name: 'PublishView' })

import {
  ArrowLeft,
  Calendar,
  ChatLineRound,
  Check,
  CircleCheck,
  Clock,
  Document,
  Location,
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
  User,
  VideoCamera,
  Warning,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../services/api'
import { useAuthStore } from '../stores/auth'
import type { UploadResponse } from '../types'

type PublishKind = 'image' | 'short' | 'long' | 'live' | 'topic'
type PreviewMode = 'desktop' | 'mobile'

const router = useRouter()
const authStore = useAuthStore()
authStore.hydrate()

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const selectedKind = ref<PublishKind>('image')
const previewMode = ref<PreviewMode>('desktop')
const selectedCoverId = ref('sample-1')
const uploadedAssets = ref<UploadResponse[]>([])
const visibility = ref('公开 - 所有人可见')
const publishMode = ref<'now' | 'schedule'>('now')
const scheduledAt = ref('2024-05-26 18:00')

const form = reactive({
  title: '在圣托里尼等一场浪漫的日落 🌅',
  content: '圣托里尼的日落真的是太美了！\n蓝白色的房子，爱琴海的风，还有橘子味的天空🍊\n这一刻，时间仿佛都慢了下来...\n\n#圣托里尼旅行 #日落 #治愈系',
})

const publishTypes = [
  { key: 'image', label: '图文', icon: Picture },
  { key: 'short', label: '短视频', icon: VideoCamera },
  { key: 'long', label: '长视频', icon: Document },
  { key: 'live', label: '直播预告', icon: Calendar },
  { key: 'topic', label: '话题帖子', icon: PriceTag },
] satisfies Array<{ key: PublishKind; label: string; icon: typeof Picture }>

const sampleCovers = [
  { id: 'sample-1', url: 'https://picsum.photos/seed/vibelo-santorini-sunset/760/500', label: '封面' },
  { id: 'sample-2', url: 'https://picsum.photos/seed/vibelo-aegean-coast/760/500', label: '' },
  { id: 'sample-3', url: 'https://picsum.photos/seed/vibelo-white-town/760/500', label: '' },
  { id: 'sample-4', url: 'https://picsum.photos/seed/vibelo-evening-sea/760/500', label: '' },
]

const drafts = [
  { title: '希腊圣托里尼日落...', time: '今天 14:30', status: '编辑中', image: 'https://picsum.photos/seed/draft-sunset/120/90' },
  { title: '健身日常｜新手友好...', time: '今天 10:12', status: '编辑中', image: 'https://picsum.photos/seed/draft-fitness/120/90' },
  { title: '东京旅行攻略&美食...', time: '昨天 22:45', status: '编辑中', image: 'https://picsum.photos/seed/draft-tokyo/120/90' },
  { title: '我的桌面分享', time: '05-20 16:30', status: '已保存', image: 'https://picsum.photos/seed/draft-desk/120/90' },
  { title: '周末露营 Vlog', time: '05-19 09:15', status: '编辑中', image: 'https://picsum.photos/seed/draft-camp/120/90' },
]

const tags = ref(['旅行攻略', '圣托里尼', '日落', '海岛旅行', '治愈系'])

const syncCommunities = [
  { name: '旅行日记', members: '12.9万成员', avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=travel' },
  { name: '摄影分享会', members: '8.7万成员', avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=camera' },
  { name: '环球美食家', members: '5.3万成员', avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=food' },
]

const aiTitleSuggestions = [
  '圣托里尼的日落，就是浪漫本身',
  '在圣托里尼，我等到了橘色的海',
  '爱琴海的日落，治愈了所有疲惫',
]

const aiTags = ['# 爱琴海', '# 希腊旅行', '# 浪漫时刻', '# 摄影分享', '# 小众旅行地']

const previewComments = [
  { name: '阿卡的夏天', text: '太美了！我也计划明年去，求攻略~', time: '1小时前', avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=summer' },
  { name: '奶茶不加糖', text: '图像和文字都好治愈，调调太气质了', time: '50分钟前', avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=milk' },
  { name: '一只咸鱼', text: '日落真的能治愈人心 🌅', time: '30分钟前', avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=fish' },
]

const titleCount = computed(() => form.title.length)
const contentCount = computed(() => form.content.length)
const currentUserName = computed(() => authStore.currentUser?.nickname || '小米在旅行')
const currentUserAvatar = computed(() => authStore.currentUser?.avatarUrl || 'https://api.dicebear.com/9.x/adventurer/svg?seed=creator')
const uploadedCoverItems = computed(() => uploadedAssets.value.map((asset, index) => ({
  id: `upload-${asset.objectKey || index}`,
  url: resolveAssetCover(asset),
  label: index === 0 ? '封面' : '',
})))
const coverChoices = computed(() => [...uploadedCoverItems.value, ...sampleCovers].slice(0, 5))
const currentCover = computed(() => coverChoices.value.find((item) => item.id === selectedCoverId.value)?.url || coverChoices.value[0]?.url || '/auto_picture.png')
const previewParagraphs = computed(() => form.content.split('\n').filter(Boolean).slice(0, 4))
const canPublish = computed(() => form.title.trim().length > 0 && uploadedAssets.value.length > 0 && !loading.value && !uploading.value)

function resolveAssetCover(asset: UploadResponse) {
  return (asset.thumbUrl || asset.fileUrl).replace('http://localhost:9000', '/minio-img')
}

async function onSelectFile(uploadFile: { raw?: File }) {
  if (!uploadFile.raw || uploading.value) return
  uploading.value = true
  try {
    const response = await api.uploadImage(uploadFile.raw)
    uploadedAssets.value.push(response)
    selectedCoverId.value = `upload-${response.objectKey || uploadedAssets.value.length - 1}`
    ElMessage.success('封面上传成功')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败')
  } finally {
    uploading.value = false
  }
}

function saveDraft() {
  saving.value = true
  window.setTimeout(() => {
    saving.value = false
    ElMessage.success('草稿已保存')
  }, 450)
}

function addTag() {
  if (tags.value.length >= 8) {
    ElMessage.info('最多添加 8 个标签')
    return
  }
  tags.value = [...tags.value, `新标签${tags.value.length + 1}`]
}

async function submit() {
  if (!form.title.trim()) {
    ElMessage.warning('请先填写标题')
    return
  }
  if (uploadedAssets.value.length === 0) {
    ElMessage.warning('请先上传至少一张封面图片')
    return
  }

  loading.value = true
  try {
    await api.createPost({
      title: form.title.trim(),
      content: form.content.trim(),
      tags: tags.value,
      assets: uploadedAssets.value.map((item, index) => ({
        objectKey: item.objectKey,
        fileUrl: item.fileUrl,
        fileType: item.fileType,
        thumbUrl: item.thumbUrl,
        width: item.width,
        height: item.height,
        sortOrder: index,
      })),
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
          <strong>草稿箱 <em>(12)</em></strong>
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
        存稿自动保存于 14:35:50
      </footer>
    </aside>

    <main class="publish-studio__editor">
      <section class="publish-studio__editor-card">
        <div class="publish-studio__editor-head">
          <button type="button" @click="router.back()">
            <el-icon><ArrowLeft /></el-icon>
          </button>
          <h1>新建图文</h1>
          <span>
            <el-icon><Check /></el-icon>
            已保存
          </span>
        </div>

        <label class="publish-studio__title-field">
          <input v-model="form.title" maxlength="100" placeholder="写一个吸引人的标题" />
          <span>{{ titleCount }}/100</span>
        </label>

        <section class="publish-studio__text-editor">
          <div class="publish-studio__toolbar">
            <button type="button">正文</button>
            <button type="button"><strong>B</strong></button>
            <button type="button"><i>I</i></button>
            <button type="button"><u>U</u></button>
            <button type="button">S</button>
            <button type="button">•</button>
            <button type="button">1.</button>
            <button type="button">“</button>
            <button type="button">🔗</button>
            <button type="button">▣</button>
          </div>
          <textarea v-model="form.content" maxlength="1024" placeholder="分享这一刻的灵感、故事或攻略" />
          <span>{{ contentCount }}/1024</span>
        </section>

        <section class="publish-studio__setting">
          <h3>草稿设置</h3>
          <div class="publish-studio__covers">
            <button
              v-for="cover in coverChoices"
              :key="cover.id"
              type="button"
              :class="{ 'is-active': selectedCoverId === cover.id }"
              @click="selectedCoverId = cover.id"
            >
              <img :src="cover.url" alt="" />
              <span v-if="cover.label">{{ cover.label }}</span>
            </button>
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              :disabled="uploading"
              :on-change="onSelectFile"
            >
              <button type="button" class="publish-studio__upload">
                <el-icon><UploadFilled /></el-icon>
                {{ uploading ? '上传中' : '上传封面' }}
              </button>
            </el-upload>
          </div>
        </section>

        <section class="publish-studio__form-stack">
          <div class="publish-studio__row">
            <strong>位置</strong>
            <div class="publish-studio__pill-field">
              <el-icon><Location /></el-icon>
              圣托里尼，希腊
              <button type="button">×</button>
            </div>
          </div>

          <div class="publish-studio__row">
            <strong>标签</strong>
            <div class="publish-studio__tag-list">
              <span v-for="tag in tags" :key="tag"># {{ tag }}</span>
              <button type="button" @click="addTag">
                <el-icon><Plus /></el-icon>
                添加标签
              </button>
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
              <button type="button">
                <el-icon><Plus /></el-icon>
                选择更多社群
              </button>
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
              <input v-model="publishMode" type="radio" value="now" />
              立即发布
            </label>
            <label>
              <input v-model="publishMode" type="radio" value="schedule" />
              定时发布
            </label>
            <input v-model="scheduledAt" type="text" />
          </div>
        </section>
      </section>

      <section class="publish-studio__bottom-bar">
        <button type="button" @click="saveDraft">{{ saving ? '保存中...' : '存为草稿' }}</button>
        <div>
          <button type="button" class="is-ghost">预览</button>
          <button type="button" class="is-primary" :disabled="loading" @click="submit">
            {{ loading ? '发布中...' : canPublish ? '发布' : '发布' }}
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
              <small>刚刚 · 圣托里尼，希腊</small>
            </div>
            <el-icon><MoreFilled /></el-icon>
          </header>
          <h3>{{ form.title }}</h3>
          <p v-for="line in previewParagraphs" :key="line">{{ line }}</p>
          <div class="publish-studio__preview-tags">
            <span v-for="tag in tags.slice(0, 3)" :key="tag">#{{ tag }}</span>
          </div>
          <img class="publish-studio__preview-cover" :src="currentCover" alt="" />
          <div class="publish-studio__preview-actions">
            <span class="is-like">♥ 832</span>
            <span><el-icon><ChatLineRound /></el-icon> 56</span>
            <span><el-icon><Share /></el-icon> 128</span>
            <span><el-icon><Star /></el-icon></span>
          </div>
          <div class="publish-studio__preview-comments">
            <h4>评论（56）</h4>
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
          <strong><el-icon><MagicStick /></el-icon> AI 标题建议</strong>
          <button type="button"><el-icon><RefreshRight /></el-icon></button>
        </div>
        <ol>
          <li v-for="title in aiTitleSuggestions" :key="title">{{ title }}</li>
        </ol>
      </section>

      <section class="publish-studio__ai-card">
        <div class="publish-studio__ai-head">
          <strong><el-icon><PriceTag /></el-icon> 智能标签推荐</strong>
          <button type="button"><el-icon><RefreshRight /></el-icon></button>
        </div>
        <div class="publish-studio__ai-tags">
          <span v-for="tag in aiTags" :key="tag">{{ tag }}</span>
        </div>
      </section>

      <section class="publish-studio__ai-card">
        <div class="publish-studio__ai-head">
          <strong><el-icon><Picture /></el-icon> 爆款封面建议</strong>
          <button type="button">换一批</button>
        </div>
        <div class="publish-studio__ai-covers">
          <img v-for="cover in sampleCovers.slice(0, 3)" :key="cover.id" :src="cover.url" alt="" />
        </div>
      </section>

      <section class="publish-studio__ai-card publish-studio__time-card">
        <strong><el-icon><Clock /></el-icon> 最佳发布时间预测</strong>
        <p>根据你的受众活跃数据，推荐发布时间</p>
        <b>今天 18:00 - 20:00</b>
        <em>预计阅读量 +32%</em>
      </section>

      <section class="publish-studio__ai-card publish-studio__risk-card">
        <strong><el-icon><Warning /></el-icon> 风险提示</strong>
        <span>内容健康度良好，无敏感风险</span>
      </section>

      <section class="publish-studio__ai-card publish-studio__audience-card">
        <strong><el-icon><User /></el-icon> 受众匹配分析</strong>
        <div>
          <span>预计触达受众</span>
          <b>12.8万</b>
        </div>
        <div>
          <span>匹配度</span>
          <b>92%</b>
        </div>
        <i />
        <p>主要受众：18-35岁，女性，旅行爱好者</p>
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
.publish-studio__ai-head {
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
.publish-studio__text-editor > span {
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

.publish-studio__toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 42px;
  padding: 0 12px;
  border-bottom: 1px solid #eef1f5;
}

.publish-studio__toolbar button {
  min-width: 28px;
  height: 28px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: #596171;
  cursor: pointer;
}

.publish-studio__toolbar button:first-child {
  min-width: 52px;
  color: #303744;
  text-align: left;
}

.publish-studio__text-editor textarea {
  width: 100%;
  min-height: 190px;
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

.publish-studio__setting h3 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 820;
}

.publish-studio__covers {
  display: grid;
  grid-template-columns: repeat(5, minmax(88px, 1fr)) 112px;
  gap: 10px;
}

.publish-studio__covers button {
  position: relative;
  overflow: hidden;
  min-height: 84px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: #f1f3f7;
  cursor: pointer;
}

.publish-studio__covers button.is-active {
  border-color: #ff5a45;
  box-shadow: 0 0 0 2px rgba(255, 90, 69, 0.14);
}

.publish-studio__covers img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.publish-studio__covers span {
  position: absolute;
  left: 6px;
  top: 6px;
  padding: 2px 7px;
  border-radius: 6px;
  background: rgba(25, 29, 38, 0.55);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.publish-studio__upload {
  display: grid !important;
  place-items: center;
  gap: 5px;
  width: 112px;
  height: 84px;
  border: 1px dashed #d8dde6 !important;
  background: #fff !important;
  color: #535c6b;
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
  grid-template-columns: 70px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
}

.publish-studio__row > strong {
  color: #242b38;
  font-size: 15px;
  font-weight: 820;
}

.publish-studio__pill-field {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 12px;
  border-radius: 8px;
  background: #f6f7f9;
  color: #4b5361;
  font-size: 14px;
}

.publish-studio__pill-field button {
  margin-left: auto;
  border: none;
  background: transparent;
  color: #8a91a0;
  cursor: pointer;
}

.publish-studio__tag-list,
.publish-studio__community-sync {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.publish-studio__tag-list span,
.publish-studio__tag-list button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid #e8ebf0;
  border-radius: 999px;
  background: #fff;
  color: #4e5665;
  font-size: 13px;
}

.publish-studio__tag-list button {
  cursor: pointer;
}

.publish-studio__community-sync > span {
  color: #9aa1ad;
  font-size: 13px;
}

.publish-studio__community-sync article,
.publish-studio__community-sync button {
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

.publish-studio__community-sync button {
  display: inline-flex;
  cursor: pointer;
  color: #4e5665;
}

.publish-studio__select {
  width: 210px;
}

.publish-studio__publish-time {
  grid-template-columns: 70px auto auto 192px;
}

.publish-studio__publish-time label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #4e5665;
  font-size: 14px;
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
  margin-top: 0;
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

.publish-studio__bottom-bar .is-ghost {
  min-width: 68px;
}

.publish-studio__bottom-bar .is-primary {
  min-width: 76px;
  border-color: #ff5a45;
  background: #ff5a45;
  color: #fff;
}

.publish-studio__bottom-bar .is-primary:disabled {
  opacity: 0.6;
  cursor: wait;
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
.publish-studio__time-card strong,
.publish-studio__risk-card strong,
.publish-studio__audience-card strong {
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

.publish-studio__ai-covers {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-top: 12px;
}

.publish-studio__ai-covers img {
  width: 100%;
  aspect-ratio: 1 / 1;
  border-radius: 8px;
  object-fit: cover;
}

.publish-studio__time-card,
.publish-studio__risk-card,
.publish-studio__audience-card {
  display: grid;
  gap: 8px;
}

.publish-studio__time-card p,
.publish-studio__audience-card p {
  margin: 0;
  color: #7e8795;
  font-size: 13px;
  line-height: 1.5;
}

.publish-studio__time-card b {
  color: #20242f;
  font-size: 15px;
}

.publish-studio__time-card em {
  color: #ff5a45;
  font-style: normal;
  font-size: 13px;
  font-weight: 760;
}

.publish-studio__risk-card {
  background: #f8fff9;
}

.publish-studio__risk-card span {
  color: #35a766;
  font-size: 13px;
}

.publish-studio__audience-card > div {
  display: flex;
  justify-content: space-between;
  color: #7e8795;
  font-size: 13px;
}

.publish-studio__audience-card b {
  color: #20242f;
}

.publish-studio__audience-card i {
  height: 5px;
  border-radius: 999px;
  background: linear-gradient(90deg, #35b56a 0 92%, #edf1f5 92% 100%);
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
    padding: 10px;
  }

  .publish-studio__type-card {
    display: flex;
    gap: 8px;
    overflow-x: auto;
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
