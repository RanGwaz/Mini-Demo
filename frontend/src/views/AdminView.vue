<script setup lang="ts">
defineOptions({ name: 'AdminView' })

import {
  Check,
  Close,
  Delete,
  EditPen,
  Plus,
  RefreshRight,
  Search,
  Setting,
  UploadFilled,
  View,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  api,
  type AdminChannelPayload,
  type AdminChannelView,
  type AdminImportItemPayload,
  type AdminOverview,
  type AdminPostView,
  type AdminTopicDetail,
  type AdminTopicPayload,
  type AdminTopicView,
  type ContentImportBatchView,
  type ContentImportItemView,
  type ContentRebuildTaskView,
  type FeedImpressionLogView,
  type FeedRequestLogView,
} from '../services/api'

type AdminTab = 'overview' | 'channels' | 'topics' | 'posts' | 'imports' | 'rebuild' | 'recommendation'
type UploadError = Parameters<NonNullable<UploadRequestOptions['onError']>>[0]

const activeTab = ref<AdminTab>('overview')
const loading = ref(false)
const overview = ref<AdminOverview | null>(null)

const channelFilters = reactive({ keyword: '', status: '', page: 1, size: 50 })
const channelRows = ref<AdminChannelView[]>([])
const channelTotal = ref(0)
const channelDialogVisible = ref(false)
const channelSaving = ref(false)
const editingChannelCode = ref('')
const channelForm = reactive<AdminChannelPayload>({
  code: '',
  name: '',
  description: '',
  icon: '',
  coverUrl: '',
  sortOrder: 0,
  status: 'ACTIVE',
  enabled: true,
  navGroup: 'MAIN',
  defaultPostType: 'general_post',
  waterfallEnabled: true,
  publishEnabled: true,
  recommendEnabled: true,
  configJson: '{}',
})

const topicFilters = reactive({ keyword: '', status: '', channelCode: '', page: 1, size: 50 })
const topicRows = ref<AdminTopicView[]>([])
const topicTotal = ref(0)
const topicDialogVisible = ref(false)
const topicDrawerVisible = ref(false)
const topicSaving = ref(false)
const editingTopicId = ref<number | null>(null)
const selectedTopicDetail = ref<AdminTopicDetail | null>(null)
const topicForm = reactive<AdminTopicPayload>({
  name: '',
  slug: '',
  description: '',
  coverUrl: '',
  status: 'ACTIVE',
  riskLevel: 'NORMAL',
  topicType: 'GENERAL',
  source: 'ADMIN',
  hotScore: 0,
  channelCodes: [],
})
const aliasForm = reactive({ alias: '', source: 'ADMIN' })
const bindingForm = reactive({ channelCode: '', weight: 1, status: 'ACTIVE' })
const mergeForm = reactive({ fromTopicId: undefined as number | undefined, toTopicId: undefined as number | undefined, reason: '' })

const postFilters = reactive({ keyword: '', channelCode: '', auditStatus: '', visibility: '', page: 1, size: 30 })
const postRows = ref<AdminPostView[]>([])
const postTotal = ref(0)

const batchFilters = reactive({ status: '', page: 1, size: 30 })
const batchRows = ref<ContentImportBatchView[]>([])
const batchTotal = ref(0)
const selectedBatch = ref<ContentImportBatchView | null>(null)
const importItems = ref<ContentImportItemView[]>([])
const itemTotal = ref(0)
const itemLoading = ref(false)
const batchForm = reactive({ name: '', description: '', sourceType: 'EDITORIAL' })
const itemForm = reactive({
  title: '',
  content: '',
  channelCode: 'campus',
  topicText: '',
  imageText: '',
  imageUrls: [] as string[],
})

const rebuildFilters = reactive({ taskType: '', status: '', page: 1, size: 30 })
const rebuildRows = ref<ContentRebuildTaskView[]>([])
const rebuildTotal = ref(0)
const rebuildForm = reactive({
  taskType: 'ALL',
  scopeType: 'ALL',
  scopeId: '',
  batchId: undefined as number | undefined,
  postId: undefined as number | undefined,
  paramsText: '{}',
})

const feedRequestFilters = reactive({ surface: 'home_feed', experimentId: '', page: 1, size: 30 })
const feedRequestRows = ref<FeedRequestLogView[]>([])
const feedRequestTotal = ref(0)
const impressionFilters = reactive({ requestId: '', postId: undefined as number | undefined, page: 1, size: 50 })
const impressionRows = ref<FeedImpressionLogView[]>([])
const impressionTotal = ref(0)

const statusOptions = [
  { label: '全部', value: '' },
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'INACTIVE' },
  { label: '合并', value: 'MERGED' },
]

const auditOptions = [
  { label: '全部', value: '' },
  { label: '通过', value: 'APPROVED' },
  { label: '待审', value: 'PENDING_REVIEW' },
  { label: '拒绝', value: 'REJECTED' },
]

const visibilityOptions = [
  { label: '全部', value: '' },
  { label: '公开', value: 'PUBLIC' },
  { label: '私密', value: 'PRIVATE' },
  { label: '下架', value: 'HIDDEN' },
]

const batchStatusOptions = [
  { label: '全部', value: '' },
  { label: '草稿', value: 'DRAFT' },
  { label: '处理中', value: 'RUNNING' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '部分成功', value: 'PARTIAL_SUCCESS' },
  { label: '已回滚', value: 'ROLLED_BACK' },
  { label: '失败', value: 'FAILED' },
]

const rebuildTypeOptions = [
  { label: '全部流水线', value: 'ALL' },
  { label: '搜索索引', value: 'SEARCH_INDEX' },
  { label: '图片向量', value: 'EMBEDDING' },
  { label: '语义标签', value: 'SEMANTIC' },
  { label: '特征工程', value: 'FEATURE' },
  { label: '相似内容', value: 'I2I' },
  { label: '缩略图', value: 'THUMBNAIL' },
]

const rebuildStatusOptions = [
  { label: '全部', value: '' },
  { label: '排队中', value: 'PENDING' },
  { label: '运行中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
  { label: '取消', value: 'CANCELED' },
]

const statCards = computed(() => [
  { label: '启用频道', value: overview.value?.activeChannels ?? 0 },
  { label: '启用话题', value: overview.value?.activeTopics ?? 0 },
  { label: '通过内容', value: overview.value?.approvedPosts ?? 0 },
  { label: '待审内容', value: overview.value?.pendingPosts ?? 0 },
  { label: '拒绝内容', value: overview.value?.rejectedPosts ?? 0 },
  { label: '导入批次', value: overview.value?.importBatches ?? 0 },
])

const channelOptions = computed(() => channelRows.value.map((channel) => ({
  label: `${channel.name} / ${channel.code}`,
  value: channel.code,
})))

const selectedTopic = computed(() => selectedTopicDetail.value?.topic ?? null)

onMounted(async () => {
  await loadAdminPage()
})

async function loadAdminPage() {
  loading.value = true
  try {
    await Promise.all([
      loadOverview(),
      loadChannels(),
      loadTopics(),
      loadPosts(),
      loadBatches(),
      loadRebuildTasks(),
      loadFeedRequests(),
    ])
  } finally {
    loading.value = false
  }
}

async function loadOverview() {
  overview.value = await api.adminOverview()
}

async function loadChannels() {
  const data = await api.adminChannels(channelFilters)
  channelRows.value = data.records
  channelTotal.value = data.total
}

async function loadTopics() {
  const data = await api.adminTopics(topicFilters)
  topicRows.value = data.records
  topicTotal.value = data.total
}

async function loadPosts() {
  const data = await api.adminPosts(postFilters)
  postRows.value = data.records
  postTotal.value = data.total
}

async function loadBatches() {
  const data = await api.adminImportBatches(batchFilters)
  batchRows.value = data.records
  batchTotal.value = data.total
  if (!selectedBatch.value && data.records.length > 0) {
    await selectBatch(data.records[0])
  }
}

async function loadRebuildTasks() {
  const data = await api.adminRebuildTasks(rebuildFilters)
  rebuildRows.value = data.records
  rebuildTotal.value = data.total
}

async function loadFeedRequests() {
  const data = await api.adminFeedRequests(feedRequestFilters)
  feedRequestRows.value = data.records
  feedRequestTotal.value = data.total
  if (!impressionFilters.requestId && data.records.length > 0) {
    await selectFeedRequest(data.records[0])
  }
}

async function loadFeedImpressions() {
  const data = await api.adminFeedImpressions(impressionFilters)
  impressionRows.value = data.records
  impressionTotal.value = data.total
}

function openCreateChannel() {
  editingChannelCode.value = ''
  Object.assign(channelForm, {
    code: '',
    name: '',
    description: '',
    icon: '',
    coverUrl: '',
    sortOrder: 0,
    status: 'ACTIVE',
    enabled: true,
    navGroup: 'MAIN',
    defaultPostType: 'general_post',
    waterfallEnabled: true,
    publishEnabled: true,
    recommendEnabled: true,
    configJson: '{}',
  })
  channelDialogVisible.value = true
}

function openEditChannel(row: AdminChannelView) {
  editingChannelCode.value = row.code
  Object.assign(channelForm, {
    code: row.code,
    name: row.name,
    description: row.description || '',
    icon: row.icon || '',
    coverUrl: row.coverUrl || '',
    sortOrder: row.sortOrder ?? 0,
    status: row.status || 'ACTIVE',
    enabled: row.enabled !== false,
    navGroup: row.navGroup || 'MAIN',
    defaultPostType: row.defaultPostType || 'general_post',
    waterfallEnabled: row.waterfallEnabled !== false,
    publishEnabled: row.publishEnabled !== false,
    recommendEnabled: row.recommendEnabled !== false,
    configJson: row.configJson || '{}',
  })
  channelDialogVisible.value = true
}

async function saveChannel() {
  if (!channelForm.name?.trim()) {
    ElMessage.warning('频道名称不能为空')
    return
  }
  channelSaving.value = true
  try {
    if (editingChannelCode.value) {
      await api.adminUpdateChannel(editingChannelCode.value, channelForm)
      ElMessage.success('频道已更新')
    } else {
      await api.adminCreateChannel(channelForm)
      ElMessage.success('频道已创建')
    }
    channelDialogVisible.value = false
    await Promise.all([loadChannels(), loadOverview()])
  } finally {
    channelSaving.value = false
  }
}

async function toggleChannel(row: AdminChannelView) {
  const nextActive = row.status !== 'ACTIVE' || row.enabled === false
  await api.adminUpdateChannelStatus(row.code, { status: nextActive ? 'ACTIVE' : 'INACTIVE', enabled: nextActive })
  ElMessage.success(nextActive ? '频道已启用' : '频道已停用')
  await Promise.all([loadChannels(), loadOverview()])
}

async function saveChannelOrder() {
  await api.adminReorderChannels(channelRows.value.map((row) => ({
    code: row.code,
    sortOrder: Number(row.sortOrder || 0),
  })))
  ElMessage.success('频道排序已保存')
  await loadChannels()
}

function openCreateTopic() {
  editingTopicId.value = null
  Object.assign(topicForm, {
    name: '',
    slug: '',
    description: '',
    coverUrl: '',
    status: 'ACTIVE',
    riskLevel: 'NORMAL',
    topicType: 'GENERAL',
    source: 'ADMIN',
    hotScore: 0,
    channelCodes: [],
  })
  topicDialogVisible.value = true
}

function openEditTopic(row: AdminTopicView) {
  editingTopicId.value = row.id
  Object.assign(topicForm, {
    name: row.name,
    slug: row.slug,
    description: row.description || '',
    coverUrl: row.coverUrl || '',
    status: row.status || 'ACTIVE',
    riskLevel: row.riskLevel || 'NORMAL',
    topicType: row.topicType || 'GENERAL',
    source: row.source || 'ADMIN',
    hotScore: row.hotScore ?? 0,
    channelCodes: selectedTopicDetail.value?.topic.id === row.id
      ? selectedTopicDetail.value.bindings.map((binding) => binding.channelCode)
      : [],
  })
  topicDialogVisible.value = true
}

async function saveTopic() {
  if (!topicForm.name?.trim()) {
    ElMessage.warning('话题名称不能为空')
    return
  }
  topicSaving.value = true
  try {
    if (editingTopicId.value) {
      await api.adminUpdateTopic(editingTopicId.value, topicForm)
      ElMessage.success('话题已更新')
    } else {
      await api.adminCreateTopic(topicForm)
      ElMessage.success('话题已创建')
    }
    topicDialogVisible.value = false
    await Promise.all([loadTopics(), loadOverview()])
  } finally {
    topicSaving.value = false
  }
}

async function toggleTopic(row: AdminTopicView) {
  const nextStatus = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  await api.adminUpdateTopicStatus(row.id, { status: nextStatus })
  ElMessage.success(nextStatus === 'ACTIVE' ? '话题已启用' : '话题已停用')
  await Promise.all([loadTopics(), loadOverview()])
}

async function openTopicDetail(row: AdminTopicView) {
  selectedTopicDetail.value = await api.adminTopicDetail(row.id)
  bindingForm.channelCode = ''
  aliasForm.alias = ''
  topicDrawerVisible.value = true
}

async function refreshTopicDetail() {
  if (!selectedTopic.value) return
  selectedTopicDetail.value = await api.adminTopicDetail(selectedTopic.value.id)
}

async function addAlias() {
  if (!selectedTopic.value || !aliasForm.alias.trim()) return
  await api.adminAddTopicAlias(selectedTopic.value.id, { alias: aliasForm.alias, source: aliasForm.source || 'ADMIN' })
  aliasForm.alias = ''
  ElMessage.success('别名已添加')
  await refreshTopicDetail()
}

async function deleteAlias(aliasId: number) {
  await api.adminDeleteTopicAlias(aliasId)
  ElMessage.success('别名已删除')
  await refreshTopicDetail()
}

async function saveBinding() {
  if (!selectedTopic.value || !bindingForm.channelCode) return
  await api.adminUpsertTopicBinding(selectedTopic.value.id, {
    channelCode: bindingForm.channelCode,
    weight: Number(bindingForm.weight || 1),
    status: bindingForm.status || 'ACTIVE',
  })
  bindingForm.channelCode = ''
  ElMessage.success('频道绑定已保存')
  await refreshTopicDetail()
}

async function deleteBinding(channelCode: string) {
  if (!selectedTopic.value) return
  await api.adminDeleteTopicBinding(selectedTopic.value.id, channelCode)
  ElMessage.success('绑定已删除')
  await refreshTopicDetail()
}

async function mergeTopics() {
  if (!mergeForm.fromTopicId || !mergeForm.toTopicId) {
    ElMessage.warning('请选择来源话题和目标话题')
    return
  }
  await ElMessageBox.confirm('合并后来源话题会被标记为 MERGED，相关内容会迁移到目标话题。', '确认合并话题', {
    type: 'warning',
  })
  await api.adminMergeTopics({
    fromTopicId: mergeForm.fromTopicId,
    toTopicId: mergeForm.toTopicId,
    reason: mergeForm.reason || '运营后台合并',
  })
  Object.assign(mergeForm, { fromTopicId: undefined, toTopicId: undefined, reason: '' })
  ElMessage.success('话题已合并')
  await Promise.all([loadTopics(), loadOverview()])
}

async function moderatePost(row: AdminPostView) {
  await api.adminModeratePost(row.post.id, {
    auditStatus: row.auditStatus || 'APPROVED',
    visibility: row.visibility || 'PUBLIC',
    qualityScore: Number(row.qualityScore ?? 0),
    safetyScore: Number(row.safetyScore ?? 1),
  })
  ElMessage.success('内容状态已更新')
  await Promise.all([loadPosts(), loadOverview()])
}

async function quickOffline(row: AdminPostView) {
  row.visibility = 'HIDDEN'
  row.auditStatus = 'REJECTED'
  await moderatePost(row)
}

async function createBatch() {
  if (!batchForm.name.trim()) {
    ElMessage.warning('批次名称不能为空')
    return
  }
  const batch = await api.adminCreateImportBatch(batchForm)
  Object.assign(batchForm, { name: '', description: '', sourceType: 'EDITORIAL' })
  selectedBatch.value = batch
  ElMessage.success('导入批次已创建')
  await Promise.all([loadBatches(), loadOverview()])
  await selectBatch(batch)
}

async function selectBatch(batch: ContentImportBatchView) {
  selectedBatch.value = batch
  itemLoading.value = true
  try {
    const data = await api.adminImportItems(batch.id)
    importItems.value = data.records
    itemTotal.value = data.total
  } finally {
    itemLoading.value = false
  }
}

async function createImportItem() {
  if (!selectedBatch.value) {
    ElMessage.warning('请先选择导入批次')
    return
  }
  const payload: AdminImportItemPayload = {
    title: itemForm.title,
    content: itemForm.content,
    channelCode: itemForm.channelCode || 'campus',
    topics: splitText(itemForm.topicText),
    imageUrls: [...itemForm.imageUrls, ...splitText(itemForm.imageText)],
  }
  if (!payload.content && !payload.title && (!payload.imageUrls || payload.imageUrls.length === 0)) {
    ElMessage.warning('至少填写正文、标题或图片')
    return
  }
  await api.adminCreateImportItem(selectedBatch.value.id, payload)
  Object.assign(itemForm, { title: '', content: '', channelCode: 'campus', topicText: '', imageText: '', imageUrls: [] })
  ElMessage.success('草稿已加入批次')
  await Promise.all([selectBatch(selectedBatch.value), loadBatches(), loadOverview()])
}

async function publishItem(item: ContentImportItemView) {
  await api.adminPublishImportItem(item.id)
  ElMessage.success('导入项已发布')
  if (selectedBatch.value) await Promise.all([selectBatch(selectedBatch.value), loadBatches(), loadOverview()])
}

async function publishBatch(batch: ContentImportBatchView) {
  await ElMessageBox.confirm('将批次内未发布草稿写入正式内容池。', '批量发布', { type: 'warning' })
  const updated = await api.adminPublishImportBatch(batch.id)
  selectedBatch.value = updated
  ElMessage.success('批次发布完成')
  await Promise.all([loadBatches(), selectBatch(updated), loadOverview()])
}

async function rollbackBatch(batch: ContentImportBatchView) {
  await ElMessageBox.confirm('会将该批次已生成内容下架并标记拒绝，不会物理删除内容。', '回滚批次', { type: 'warning' })
  const updated = await api.adminRollbackImportBatch(batch.id)
  selectedBatch.value = updated
  ElMessage.success('批次已回滚')
  await Promise.all([loadBatches(), selectBatch(updated), loadOverview()])
}

async function uploadImportImage(options: UploadRequestOptions) {
  try {
    const result = await api.uploadImage(options.file as File)
    itemForm.imageUrls.push(result.fileUrl)
    options.onSuccess?.(result)
    ElMessage.success('图片已上传')
  } catch (error) {
    const message = error instanceof Error ? error.message : '图片上传失败'
    options.onError?.(new Error(message) as UploadError)
    ElMessage.error(message)
  }
}

async function createRebuildTask() {
  let params: Record<string, unknown> = {}
  if (rebuildForm.paramsText.trim()) {
    try {
      params = JSON.parse(rebuildForm.paramsText) as Record<string, unknown>
    } catch {
      ElMessage.warning('参数 JSON 格式不正确')
      return
    }
  }
  await api.adminCreateRebuildTask({
    taskType: rebuildForm.taskType,
    scopeType: rebuildForm.scopeType,
    scopeId: rebuildForm.scopeId,
    batchId: rebuildForm.batchId,
    postId: rebuildForm.postId,
    params,
  })
  ElMessage.success('重建任务已创建')
  Object.assign(rebuildForm, {
    taskType: 'ALL',
    scopeType: 'ALL',
    scopeId: '',
    batchId: undefined,
    postId: undefined,
    paramsText: '{}',
  })
  await loadRebuildTasks()
}

async function updateRebuildTask(row: ContentRebuildTaskView, status: string) {
  await api.adminUpdateRebuildTaskStatus(row.id, { status })
  ElMessage.success('重建任务状态已更新')
  await loadRebuildTasks()
}

async function selectFeedRequest(row: FeedRequestLogView) {
  impressionFilters.requestId = row.requestId
  impressionFilters.postId = undefined
  await loadFeedImpressions()
}

function removeImportImage(url: string) {
  itemForm.imageUrls = itemForm.imageUrls.filter((item) => item !== url)
}

function splitText(raw: string) {
  return raw
    .split(/[\n,，#\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function imageUrl(url?: string) {
  return (url || '').replace('http://localhost:9000', '/minio-img')
}

function shortText(value?: string, max = 56) {
  const text = value || ''
  return text.length > max ? `${text.slice(0, max)}...` : text
}

function formatDate(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function tagType(status?: string) {
  if (['ACTIVE', 'APPROVED', 'PUBLISHED'].includes(status || '')) return 'success'
  if (['PENDING_REVIEW', 'RUNNING', 'PARTIAL_SUCCESS'].includes(status || '')) return 'warning'
  if (['REJECTED', 'FAILED', 'HIDDEN', 'ROLLED_BACK'].includes(status || '')) return 'danger'
  return 'info'
}
</script>

<template>
  <div class="admin-page">
    <aside class="admin-page__rail">
      <div class="admin-page__brand">
        <el-icon><Setting /></el-icon>
        <div>
          <strong>运营后台</strong>
          <span>Vibelo Admin</span>
        </div>
      </div>
      <button
        v-for="item in [
          { key: 'overview', label: '总览' },
          { key: 'channels', label: '频道' },
          { key: 'topics', label: '话题' },
          { key: 'posts', label: '内容' },
          { key: 'imports', label: '导入' },
          { key: 'rebuild', label: '重建' },
          { key: 'recommendation', label: '推荐' },
        ]"
        :key="item.key"
        type="button"
        :class="{ 'is-active': activeTab === item.key }"
        @click="activeTab = item.key as AdminTab"
      >
        {{ item.label }}
      </button>
    </aside>

    <main class="admin-page__main" v-loading="loading">
      <header class="admin-page__header">
        <div>
          <span>OPERATION CENTER</span>
          <h1>内容运营工作台</h1>
        </div>
        <el-button :icon="RefreshRight" @click="loadAdminPage">刷新数据</el-button>
      </header>

      <section class="admin-page__stats">
        <div v-for="card in statCards" :key="card.label" class="admin-stat">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
        </div>
      </section>

      <section v-show="activeTab === 'overview'" class="admin-panel">
        <div class="admin-panel__head">
          <div>
            <h2>运营总览</h2>
            <p>频道、话题、内容审核和冷启动导入的入口都在这里收口。</p>
          </div>
        </div>
        <div class="admin-overview-grid">
          <div>
            <h3>今日重点</h3>
            <p>优先处理待审内容、失败导入项和低安全分内容；频道与话题调整会立即影响发布和发现链路。</p>
          </div>
          <div>
            <h3>推荐链路保护</h3>
            <p>下架内容通过 `visibility` 和 `auditStatus` 控制，不删除推荐召回代码；导入回滚只软下架已生成内容。</p>
          </div>
          <div>
            <h3>冷启动准备</h3>
            <p>先用导入批次沉淀中文内容池，再进入 P6/P7 的数据清理、MinIO/Milvus 和特征重建。</p>
          </div>
        </div>
      </section>

      <section v-show="activeTab === 'channels'" class="admin-panel">
        <div class="admin-panel__head">
          <div>
            <h2>频道管理</h2>
            <p>新增、编辑、启停、排序和默认发布类型。</p>
          </div>
          <div class="admin-actions">
            <el-button :icon="Plus" type="primary" @click="openCreateChannel">新建频道</el-button>
            <el-button :icon="Check" @click="saveChannelOrder">保存排序</el-button>
          </div>
        </div>

        <div class="admin-filters">
          <el-input v-model="channelFilters.keyword" clearable placeholder="搜索频道" :prefix-icon="Search" @keyup.enter="loadChannels" />
          <el-select v-model="channelFilters.status" placeholder="状态" clearable>
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button :icon="Search" @click="loadChannels">查询</el-button>
        </div>

        <el-table :data="channelRows" class="admin-table" height="520">
          <el-table-column label="排序" width="92">
            <template #default="{ row }">
              <el-input-number v-model="row.sortOrder" :min="0" :controls="false" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="频道" min-width="220">
            <template #default="{ row }">
              <div class="admin-name-cell">
                <span class="admin-avatar">{{ row.icon || row.name?.slice(0, 1) }}</span>
                <div>
                  <strong>{{ row.name }}</strong>
                  <em>{{ row.code }}</em>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="defaultPostType" label="发布类型" width="150" />
          <el-table-column label="能力" width="220">
            <template #default="{ row }">
              <div class="admin-tags">
                <el-tag size="small" :type="row.publishEnabled ? 'success' : 'info'">发布</el-tag>
                <el-tag size="small" :type="row.recommendEnabled ? 'success' : 'info'">推荐</el-tag>
                <el-tag size="small" :type="row.waterfallEnabled ? 'success' : 'info'">瀑布流</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)">{{ row.enabled === false ? 'DISABLED' : row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="EditPen" @click="openEditChannel(row)">编辑</el-button>
              <el-button link :type="row.status === 'ACTIVE' && row.enabled !== false ? 'danger' : 'success'" @click="toggleChannel(row)">
                {{ row.status === 'ACTIVE' && row.enabled !== false ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <p class="admin-total">共 {{ channelTotal }} 个频道</p>
      </section>

      <section v-show="activeTab === 'topics'" class="admin-panel">
        <div class="admin-panel__head">
          <div>
            <h2>话题管理</h2>
            <p>话题、别名、频道绑定、风险等级和合并治理。</p>
          </div>
          <el-button :icon="Plus" type="primary" @click="openCreateTopic">新建话题</el-button>
        </div>

        <div class="admin-filters">
          <el-input v-model="topicFilters.keyword" clearable placeholder="搜索话题" :prefix-icon="Search" @keyup.enter="loadTopics" />
          <el-select v-model="topicFilters.channelCode" clearable filterable placeholder="频道">
            <el-option v-for="item in channelOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="topicFilters.status" clearable placeholder="状态">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button :icon="Search" @click="loadTopics">查询</el-button>
        </div>

        <el-table :data="topicRows" class="admin-table" height="500">
          <el-table-column label="话题" min-width="260">
            <template #default="{ row }">
              <div class="admin-topic-cell">
                <strong>#{{ row.name }}</strong>
                <span>{{ row.slug }}</span>
                <p>{{ shortText(row.description, 72) }}</p>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="topicType" label="类型" width="120" />
          <el-table-column prop="riskLevel" label="风险" width="110" />
          <el-table-column label="热度" width="130">
            <template #default="{ row }">{{ row.hotScore ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="内容/关注" width="140">
            <template #default="{ row }">{{ row.postCount || 0 }} / {{ row.followerCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" @click="openTopicDetail(row)">治理</el-button>
              <el-button link type="primary" :icon="EditPen" @click="openEditTopic(row)">编辑</el-button>
              <el-button link :type="row.status === 'ACTIVE' ? 'danger' : 'success'" @click="toggleTopic(row)">
                {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="admin-merge">
          <strong>话题合并</strong>
          <el-select v-model="mergeForm.fromTopicId" filterable placeholder="来源话题">
            <el-option v-for="topic in topicRows" :key="topic.id" :label="`${topic.name} / ${topic.id}`" :value="topic.id" />
          </el-select>
          <el-select v-model="mergeForm.toTopicId" filterable placeholder="目标话题">
            <el-option v-for="topic in topicRows" :key="topic.id" :label="`${topic.name} / ${topic.id}`" :value="topic.id" />
          </el-select>
          <el-input v-model="mergeForm.reason" placeholder="合并原因" />
          <el-button type="warning" @click="mergeTopics">确认合并</el-button>
        </div>
        <p class="admin-total">共 {{ topicTotal }} 个话题</p>
      </section>

      <section v-show="activeTab === 'posts'" class="admin-panel">
        <div class="admin-panel__head">
          <div>
            <h2>内容管理</h2>
            <p>审核状态、可见性、质量分和安全分会影响 Feed、搜索和话题页展示。</p>
          </div>
        </div>

        <div class="admin-filters">
          <el-input v-model="postFilters.keyword" clearable placeholder="搜索内容" :prefix-icon="Search" @keyup.enter="loadPosts" />
          <el-select v-model="postFilters.channelCode" clearable filterable placeholder="频道">
            <el-option v-for="item in channelOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="postFilters.auditStatus" clearable placeholder="审核">
            <el-option v-for="item in auditOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="postFilters.visibility" clearable placeholder="可见性">
            <el-option v-for="item in visibilityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button :icon="Search" @click="loadPosts">查询</el-button>
        </div>

        <el-table :data="postRows" class="admin-table" height="560">
          <el-table-column label="内容" min-width="340">
            <template #default="{ row }">
              <div class="admin-post-cell">
                <img v-if="row.post.coverUrl" :src="imageUrl(row.post.coverUrl)" alt="" />
                <span v-else>文</span>
                <div>
                  <strong>{{ row.post.title || '无标题内容' }}</strong>
                  <p>{{ shortText(row.post.content, 96) }}</p>
                  <em>{{ row.post.author?.nickname || row.post.author?.username }} · {{ formatDate(row.post.createdAt) }}</em>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="频道" width="120">
            <template #default="{ row }">{{ row.post.channelCode }}</template>
          </el-table-column>
          <el-table-column label="审核" width="150">
            <template #default="{ row }">
              <el-select v-model="row.auditStatus" size="small">
                <el-option label="通过" value="APPROVED" />
                <el-option label="待审" value="PENDING_REVIEW" />
                <el-option label="拒绝" value="REJECTED" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="可见性" width="140">
            <template #default="{ row }">
              <el-select v-model="row.visibility" size="small">
                <el-option label="公开" value="PUBLIC" />
                <el-option label="私密" value="PRIVATE" />
                <el-option label="下架" value="HIDDEN" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="质量/安全" width="180">
            <template #default="{ row }">
              <div class="score-inputs">
                <el-input-number v-model="row.qualityScore" :min="0" :max="1" :step="0.05" size="small" />
                <el-input-number v-model="row.safetyScore" :min="0" :max="1" :step="0.05" size="small" />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="互动" width="120">
            <template #default="{ row }">{{ row.post.likeCount }} 赞 / {{ row.post.commentCount }} 评</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Check" @click="moderatePost(row)">保存</el-button>
              <el-button link type="danger" :icon="Close" @click="quickOffline(row)">下架</el-button>
            </template>
          </el-table-column>
        </el-table>
        <p class="admin-total">共 {{ postTotal }} 条内容</p>
      </section>

      <section v-show="activeTab === 'imports'" class="admin-panel admin-imports">
        <div class="admin-panel__head">
          <div>
            <h2>导入批次</h2>
            <p>用于冷启动中文内容池，支持草稿、上传图片、单条发布、批量发布和软回滚。</p>
          </div>
        </div>

        <div class="admin-import-layout">
          <div class="admin-import-left">
            <div class="admin-form-inline">
              <el-input v-model="batchForm.name" placeholder="批次名称" />
              <el-input v-model="batchForm.description" placeholder="说明" />
              <el-select v-model="batchForm.sourceType">
                <el-option label="编辑精选" value="EDITORIAL" />
                <el-option label="合作内容" value="PARTNER" />
                <el-option label="手工导入" value="MANUAL" />
              </el-select>
              <el-button type="primary" :icon="Plus" @click="createBatch">创建批次</el-button>
            </div>

            <div class="admin-filters admin-filters--compact">
              <el-select v-model="batchFilters.status" clearable placeholder="批次状态">
                <el-option v-for="item in batchStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-button :icon="Search" @click="loadBatches">查询</el-button>
            </div>

            <el-table :data="batchRows" class="admin-table" height="420" highlight-current-row @row-click="selectBatch">
              <el-table-column label="批次" min-width="180">
                <template #default="{ row }">
                  <strong>{{ row.name }}</strong>
                  <p>{{ shortText(row.description, 48) }}</p>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="tagType(row.status)">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="进度" width="130">
                <template #default="{ row }">{{ row.successCount || 0 }}/{{ row.totalCount || 0 }}，失败 {{ row.failedCount || 0 }}</template>
              </el-table-column>
              <el-table-column label="操作" width="190" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" :icon="Check" @click.stop="publishBatch(row)">发布</el-button>
                  <el-button link type="danger" :icon="Delete" @click.stop="rollbackBatch(row)">回滚</el-button>
                </template>
              </el-table-column>
            </el-table>
            <p class="admin-total">共 {{ batchTotal }} 个批次</p>
          </div>

          <div class="admin-import-right">
            <div class="admin-selected-batch">
              <strong>{{ selectedBatch?.name || '未选择批次' }}</strong>
              <span v-if="selectedBatch">状态 {{ selectedBatch.status }} · {{ itemTotal }} 条草稿</span>
            </div>

            <div class="admin-import-editor">
              <el-input v-model="itemForm.title" placeholder="标题，可选" />
              <el-input v-model="itemForm.content" type="textarea" :rows="4" placeholder="正文内容，可选；图片内容也可以只有图片和话题" />
              <div class="admin-import-editor__row">
                <el-select v-model="itemForm.channelCode" filterable placeholder="频道">
                  <el-option v-for="item in channelOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
                <el-input v-model="itemForm.topicText" placeholder="话题，用空格、逗号或 # 分隔" />
              </div>
              <el-input v-model="itemForm.imageText" type="textarea" :rows="2" placeholder="图片 URL，可粘贴多行；也可以直接上传" />
              <div class="admin-upload-line">
                <el-upload :show-file-list="false" :http-request="uploadImportImage" accept="image/*">
                  <el-button :icon="UploadFilled">上传图片</el-button>
                </el-upload>
                <div v-if="itemForm.imageUrls.length" class="admin-upload-preview">
                  <button v-for="url in itemForm.imageUrls" :key="url" type="button" @click="removeImportImage(url)">
                    <img :src="imageUrl(url)" alt="" />
                    <span>移除</span>
                  </button>
                </div>
              </div>
              <el-button type="primary" :icon="Plus" @click="createImportItem">加入批次草稿</el-button>
            </div>

            <el-table v-loading="itemLoading" :data="importItems" class="admin-table" height="360">
              <el-table-column label="草稿" min-width="240">
                <template #default="{ row }">
                  <strong>{{ row.title || '无标题草稿' }}</strong>
                  <p>{{ shortText(row.content, 80) }}</p>
                  <div class="admin-tags">
                    <el-tag v-for="topic in row.topics" :key="topic" size="small">#{{ topic }}</el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="频道" width="100" prop="channelCode" />
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="tagType(row.status)">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" :disabled="row.status === 'PUBLISHED'" @click="publishItem(row)">发布</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </section>

      <section v-show="activeTab === 'rebuild'" class="admin-panel">
        <div class="admin-panel__head">
          <div>
            <h2>重建流水线</h2>
            <p>为 P7 准备搜索、缩略图、向量、语义、特征和 I2I 重建任务，脚本按任务队列执行。</p>
          </div>
        </div>

        <div class="admin-rebuild-create">
          <el-select v-model="rebuildForm.taskType" placeholder="任务类型">
            <el-option v-for="item in rebuildTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="rebuildForm.scopeType" placeholder="范围">
            <el-option label="全部内容" value="ALL" />
            <el-option label="导入批次" value="BATCH" />
            <el-option label="单条内容" value="POST" />
            <el-option label="频道" value="CHANNEL" />
          </el-select>
          <el-input v-model="rebuildForm.scopeId" placeholder="范围 ID，可选" />
          <el-input-number v-model="rebuildForm.batchId" :min="1" placeholder="批次 ID" />
          <el-input-number v-model="rebuildForm.postId" :min="1" placeholder="内容 ID" />
          <el-input v-model="rebuildForm.paramsText" type="textarea" :rows="2" placeholder='参数 JSON，例如 {"dryRun": true}' />
          <el-button type="primary" :icon="Plus" @click="createRebuildTask">创建重建任务</el-button>
        </div>

        <div class="admin-filters">
          <el-select v-model="rebuildFilters.taskType" clearable placeholder="任务类型">
            <el-option v-for="item in rebuildTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="rebuildFilters.status" clearable placeholder="状态">
            <el-option v-for="item in rebuildStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button :icon="Search" @click="loadRebuildTasks">查询</el-button>
        </div>

        <el-table :data="rebuildRows" class="admin-table" height="520">
          <el-table-column label="任务" min-width="220">
            <template #default="{ row }">
              <strong>{{ row.taskType }}</strong>
              <p>{{ row.scopeType }} {{ row.scopeId || row.batchId || row.postId || '' }}</p>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="进度" width="160">
            <template #default="{ row }">{{ row.successCount || 0 }}/{{ row.totalCount || 0 }}，失败 {{ row.failedCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="参数" min-width="220">
            <template #default="{ row }">{{ shortText(row.paramsJson, 90) }}</template>
          </el-table-column>
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="warning" :disabled="row.status !== 'PENDING'" @click="updateRebuildTask(row, 'RUNNING')">开始</el-button>
              <el-button link type="success" :disabled="row.status === 'SUCCESS'" @click="updateRebuildTask(row, 'SUCCESS')">成功</el-button>
              <el-button link type="danger" :disabled="row.status === 'FAILED'" @click="updateRebuildTask(row, 'FAILED')">失败</el-button>
            </template>
          </el-table-column>
        </el-table>
        <p class="admin-total">共 {{ rebuildTotal }} 个重建任务</p>
      </section>

      <section v-show="activeTab === 'recommendation'" class="admin-panel">
        <div class="admin-panel__head">
          <div>
            <h2>推荐观测</h2>
            <p>查看 Feed 请求、实验桶、延迟、返回数量，以及每条曝光内容的来源、位置和分数。</p>
          </div>
        </div>

        <div class="admin-reco-layout">
          <div>
            <div class="admin-filters">
              <el-input v-model="feedRequestFilters.surface" clearable placeholder="surface" />
              <el-input v-model="feedRequestFilters.experimentId" clearable placeholder="experimentId" />
              <el-button :icon="Search" @click="loadFeedRequests">查询请求</el-button>
            </div>
            <el-table :data="feedRequestRows" class="admin-table" height="520" highlight-current-row @row-click="selectFeedRequest">
              <el-table-column label="Request" min-width="230">
                <template #default="{ row }">
                  <strong>{{ row.requestId }}</strong>
                  <p>{{ row.userSegment }} · {{ formatDate(row.createdAt) }}</p>
                </template>
              </el-table-column>
              <el-table-column label="实验" width="170">
                <template #default="{ row }">{{ row.experimentId }} / {{ row.experimentBucket }}</template>
              </el-table-column>
              <el-table-column label="页码" width="90">
                <template #default="{ row }">{{ row.pageNo }} / {{ row.pageSize }}</template>
              </el-table-column>
              <el-table-column label="结果" width="110">
                <template #default="{ row }">{{ row.returnedCount }}/{{ row.totalCandidates }}</template>
              </el-table-column>
              <el-table-column label="延迟" width="90">
                <template #default="{ row }">{{ row.latencyMs || 0 }}ms</template>
              </el-table-column>
            </el-table>
            <p class="admin-total">共 {{ feedRequestTotal }} 条请求</p>
          </div>

          <div>
            <div class="admin-filters">
              <el-input v-model="impressionFilters.requestId" clearable placeholder="requestId" />
              <el-input-number v-model="impressionFilters.postId" :min="1" placeholder="postId" />
              <el-button :icon="Search" @click="loadFeedImpressions">查询曝光</el-button>
            </div>
            <el-table :data="impressionRows" class="admin-table" height="520">
              <el-table-column label="位置" width="70" prop="rankPosition" />
              <el-table-column label="内容" width="100" prop="postId" />
              <el-table-column label="来源" min-width="180" prop="recallSource" />
              <el-table-column label="频道/话题" min-width="180">
                <template #default="{ row }">
                  <strong>{{ row.channelCode }}</strong>
                  <p>{{ shortText(row.topicNames, 64) }}</p>
                </template>
              </el-table-column>
              <el-table-column label="分数" width="90" prop="rankScore" />
              <el-table-column label="原因" min-width="180">
                <template #default="{ row }">{{ shortText(row.reason, 80) }}</template>
              </el-table-column>
            </el-table>
            <p class="admin-total">共 {{ impressionTotal }} 条曝光</p>
          </div>
        </div>
      </section>
    </main>

    <el-dialog v-model="channelDialogVisible" :title="editingChannelCode ? '编辑频道' : '新建频道'" width="720px">
      <el-form label-width="120px" class="admin-dialog-form">
        <el-form-item label="频道编码">
          <el-input v-model="channelForm.code" :disabled="Boolean(editingChannelCode)" placeholder="campus_life" />
        </el-form-item>
        <el-form-item label="频道名称">
          <el-input v-model="channelForm.name" placeholder="校园生活" />
        </el-form-item>
        <el-form-item label="频道描述">
          <el-input v-model="channelForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="图标/封面">
          <el-input v-model="channelForm.icon" placeholder="图标字符或 URL" />
          <el-input v-model="channelForm.coverUrl" placeholder="封面 URL" />
        </el-form-item>
        <el-form-item label="排序/分组">
          <el-input-number v-model="channelForm.sortOrder" :min="0" />
          <el-input v-model="channelForm.navGroup" placeholder="MAIN" />
        </el-form-item>
        <el-form-item label="默认发布类型">
          <el-input v-model="channelForm.defaultPostType" placeholder="general_post" />
        </el-form-item>
        <el-form-item label="能力开关">
          <el-switch v-model="channelForm.enabled" active-text="启用" />
          <el-switch v-model="channelForm.publishEnabled" active-text="可发布" />
          <el-switch v-model="channelForm.recommendEnabled" active-text="进推荐" />
          <el-switch v-model="channelForm.waterfallEnabled" active-text="瀑布流" />
        </el-form-item>
        <el-form-item label="配置 JSON">
          <el-input v-model="channelForm.configJson" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="channelDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="channelSaving" @click="saveChannel">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="topicDialogVisible" :title="editingTopicId ? '编辑话题' : '新建话题'" width="720px">
      <el-form label-width="120px" class="admin-dialog-form">
        <el-form-item label="话题名称">
          <el-input v-model="topicForm.name" placeholder="周末去哪儿" />
        </el-form-item>
        <el-form-item label="Slug">
          <el-input v-model="topicForm.slug" :disabled="Boolean(editingTopicId)" placeholder="可留空自动生成" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="topicForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="封面 URL">
          <el-input v-model="topicForm.coverUrl" />
        </el-form-item>
        <el-form-item label="状态/风险">
          <el-select v-model="topicForm.status">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
          <el-select v-model="topicForm.riskLevel">
            <el-option label="普通" value="NORMAL" />
            <el-option label="敏感" value="SENSITIVE" />
            <el-option label="高风险" value="HIGH" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型/热度">
          <el-input v-model="topicForm.topicType" placeholder="GENERAL" />
          <el-input-number v-model="topicForm.hotScore" :min="0" :step="1" />
        </el-form-item>
        <el-form-item label="绑定频道">
          <el-select v-model="topicForm.channelCodes" multiple filterable placeholder="可选多个频道">
            <el-option v-for="item in channelOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="topicDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="topicSaving" @click="saveTopic">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="topicDrawerVisible" size="520px" title="话题治理">
      <div v-if="selectedTopicDetail" class="admin-topic-drawer">
        <h3>#{{ selectedTopicDetail.topic.name }}</h3>
        <p>{{ selectedTopicDetail.topic.description || '暂无描述' }}</p>

        <section>
          <h4>别名</h4>
          <div class="admin-form-inline">
            <el-input v-model="aliasForm.alias" placeholder="添加别名" />
            <el-button :icon="Plus" @click="addAlias">添加</el-button>
          </div>
          <div class="admin-chip-list">
            <el-tag v-for="alias in selectedTopicDetail.aliases" :key="alias.id" closable @close="deleteAlias(alias.id)">
              {{ alias.alias }}
            </el-tag>
          </div>
        </section>

        <section>
          <h4>频道绑定</h4>
          <div class="admin-form-inline">
            <el-select v-model="bindingForm.channelCode" filterable placeholder="频道">
              <el-option v-for="item in channelOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-input-number v-model="bindingForm.weight" :min="0" :step="0.1" />
            <el-button :icon="Check" @click="saveBinding">保存</el-button>
          </div>
          <div class="admin-binding-list">
            <div v-for="binding in selectedTopicDetail.bindings" :key="binding.channelCode">
              <span>{{ binding.channelCode }}</span>
              <em>权重 {{ binding.weight || 1 }} · {{ binding.status }}</em>
              <el-button link type="danger" @click="deleteBinding(binding.channelCode)">删除</el-button>
            </div>
          </div>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.admin-page {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  min-height: calc(100vh - 74px);
  background: #f5f6f8;
  color: #171b25;
}

.admin-page__rail {
  position: sticky;
  top: 74px;
  align-self: start;
  height: calc(100vh - 74px);
  padding: 24px 18px;
  border-right: 1px solid rgba(21, 26, 36, 0.08);
  background: #fff;
}

.admin-page__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  margin-bottom: 20px;
  border-radius: 8px;
  background: #f2f4f7;
}

.admin-page__brand .el-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #ff5a45;
  color: #fff;
  font-size: 20px;
}

.admin-page__brand strong,
.admin-page__brand span {
  display: block;
}

.admin-page__brand strong {
  font-size: 17px;
}

.admin-page__brand span {
  color: #7c8492;
  font-size: 12px;
}

.admin-page__rail button {
  display: flex;
  width: 100%;
  align-items: center;
  height: 42px;
  margin-bottom: 6px;
  padding: 0 14px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #4f5968;
  font: inherit;
  font-weight: 760;
  cursor: pointer;
}

.admin-page__rail button:hover,
.admin-page__rail button.is-active {
  background: #fff0ed;
  color: #ff4f3b;
}

.admin-page__main {
  min-width: 0;
  padding: 28px;
}

.admin-page__header,
.admin-panel__head,
.admin-actions,
.admin-filters,
.admin-form-inline,
.admin-upload-line {
  display: flex;
  align-items: center;
}

.admin-page__header {
  justify-content: space-between;
  margin-bottom: 20px;
}

.admin-page__header span {
  color: #8b94a3;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0;
}

.admin-page__header h1 {
  margin: 4px 0 0;
  font-size: 30px;
  line-height: 1.2;
}

.admin-page__stats {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.admin-stat {
  padding: 16px;
  border: 1px solid rgba(21, 26, 36, 0.08);
  border-radius: 8px;
  background: #fff;
}

.admin-stat span {
  display: block;
  color: #7d8593;
  font-size: 13px;
  font-weight: 700;
}

.admin-stat strong {
  display: block;
  margin-top: 10px;
  font-size: 28px;
  line-height: 1;
}

.admin-panel {
  border: 1px solid rgba(21, 26, 36, 0.08);
  border-radius: 8px;
  background: #fff;
  padding: 20px;
}

.admin-panel__head {
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.admin-panel__head h2 {
  margin: 0;
  font-size: 22px;
}

.admin-panel__head p,
.admin-overview-grid p,
.admin-topic-cell p,
.admin-import-left p,
.admin-post-cell p {
  margin: 6px 0 0;
  color: #747d8c;
  line-height: 1.6;
}

.admin-overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.admin-overview-grid > div {
  min-height: 150px;
  padding: 18px;
  border-radius: 8px;
  background: #f6f7f9;
}

.admin-overview-grid h3 {
  margin: 0 0 10px;
  font-size: 18px;
}

.admin-actions,
.admin-filters,
.admin-form-inline {
  gap: 10px;
}

.admin-filters {
  margin-bottom: 14px;
}

.admin-filters .el-input {
  max-width: 280px;
}

.admin-filters .el-select {
  width: 180px;
}

.admin-filters--compact {
  margin-top: 14px;
}

.admin-table {
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
}

.admin-name-cell,
.admin-post-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-avatar {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  background: #fff0ed;
  color: #ff4f3b;
  font-weight: 900;
}

.admin-name-cell strong,
.admin-name-cell em,
.admin-topic-cell strong,
.admin-topic-cell span,
.admin-post-cell strong,
.admin-post-cell em {
  display: block;
}

.admin-name-cell em,
.admin-topic-cell span,
.admin-post-cell em {
  color: #8c94a3;
  font-style: normal;
  font-size: 12px;
}

.admin-tags,
.admin-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.admin-topic-cell strong {
  font-size: 15px;
}

.admin-post-cell img,
.admin-post-cell > span {
  width: 58px;
  height: 58px;
  flex: 0 0 auto;
  border-radius: 8px;
  object-fit: cover;
  background: #edf0f4;
}

.admin-post-cell > span {
  display: grid;
  place-items: center;
  color: #7d8797;
  font-weight: 800;
}

.score-inputs {
  display: grid;
  gap: 6px;
}

.score-inputs :deep(.el-input-number) {
  width: 120px;
}

.admin-total {
  margin: 12px 0 0;
  color: #88909f;
  font-size: 13px;
}

.admin-merge {
  display: grid;
  grid-template-columns: auto 1fr 1fr 1.4fr auto;
  gap: 10px;
  align-items: center;
  margin-top: 14px;
  padding: 12px;
  border-radius: 8px;
  background: #f7f8fa;
}

.admin-import-layout {
  display: grid;
  grid-template-columns: minmax(420px, 0.9fr) minmax(460px, 1.1fr);
  gap: 18px;
}

.admin-import-left,
.admin-import-right {
  min-width: 0;
}

.admin-form-inline {
  flex-wrap: wrap;
}

.admin-form-inline .el-input {
  width: 180px;
}

.admin-form-inline .el-select {
  width: 150px;
}

.admin-selected-batch {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 46px;
  margin-bottom: 12px;
  padding: 0 14px;
  border-radius: 8px;
  background: #f3f5f8;
}

.admin-selected-batch span {
  color: #7b8494;
  font-size: 13px;
}

.admin-import-editor {
  display: grid;
  gap: 10px;
  margin-bottom: 14px;
  padding: 14px;
  border-radius: 8px;
  background: #f8f9fb;
}

.admin-rebuild-create {
  display: grid;
  grid-template-columns: 170px 150px minmax(160px, 1fr) 140px 140px minmax(240px, 1.4fr) auto;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
  padding: 14px;
  border-radius: 8px;
  background: #f8f9fb;
}

.admin-reco-layout {
  display: grid;
  grid-template-columns: minmax(430px, 0.95fr) minmax(480px, 1.05fr);
  gap: 18px;
}

.admin-import-editor__row {
  display: grid;
  grid-template-columns: 190px minmax(0, 1fr);
  gap: 10px;
}

.admin-upload-line {
  align-items: flex-start;
  gap: 12px;
}

.admin-upload-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.admin-upload-preview button {
  position: relative;
  width: 54px;
  height: 54px;
  padding: 0;
  border: none;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
}

.admin-upload-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.admin-upload-preview span {
  position: absolute;
  inset: auto 0 0;
  background: rgba(0, 0, 0, 0.56);
  color: #fff;
  font-size: 11px;
}

.admin-dialog-form :deep(.el-form-item__content) {
  gap: 10px;
}

.admin-dialog-form .el-input,
.admin-dialog-form .el-select {
  flex: 1;
}

.admin-topic-drawer {
  display: grid;
  gap: 22px;
}

.admin-topic-drawer h3,
.admin-topic-drawer h4 {
  margin: 0;
}

.admin-topic-drawer p {
  margin: -14px 0 0;
  color: #717b8d;
  line-height: 1.6;
}

.admin-binding-list {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}

.admin-binding-list > div {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 8px;
  background: #f6f7f9;
}

.admin-binding-list em {
  color: #7b8494;
  font-style: normal;
  font-size: 12px;
}

@media (max-width: 1180px) {
  .admin-page {
    grid-template-columns: 1fr;
  }

  .admin-page__rail {
    position: static;
    display: flex;
    height: auto;
    gap: 8px;
    overflow-x: auto;
    padding: 12px;
  }

  .admin-page__brand {
    min-width: 190px;
    margin: 0;
  }

  .admin-page__rail button {
    width: auto;
    min-width: 84px;
    margin: 0;
  }

  .admin-page__stats,
  .admin-overview-grid,
  .admin-import-layout {
    grid-template-columns: 1fr;
  }

  .admin-merge {
    grid-template-columns: 1fr;
  }

  .admin-rebuild-create {
    grid-template-columns: 1fr;
  }

  .admin-reco-layout {
    grid-template-columns: 1fr;
  }
}
</style>
