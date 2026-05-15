<script setup lang="ts">
defineOptions({ name: 'MessagesView' })

import { Bell, ChatDotRound, Check, Clock, Position, Search, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CommonLeftSidebar from '../components/CommonLeftSidebar.vue'
import { api } from '../services/api'
import type { MessageConversationView, MessageItemView, MessageSummaryResponse, UserSummary } from '../types'
import { normalizeMediaUrl } from '../utils/postMedia'

type ActiveBox = 'direct' | 'interactions'
type InteractionFilter = 'all' | 'interaction' | 'system'

const route = useRoute()
const router = useRouter()

const activeBox = ref<ActiveBox>('direct')
const interactionFilter = ref<InteractionFilter>('all')
const keyword = ref('')
const draftMessage = ref('')
const selectedPeerId = ref<number | null>(null)
const conversations = ref<MessageConversationView[]>([])
const interactions = ref<MessageItemView[]>([])
const thread = ref<MessageItemView[]>([])
const summary = ref<MessageSummaryResponse>({ unreadDirect: 0, unreadNotifications: 0, unreadTotal: 0 })
const conversationPage = ref(1)
const conversationTotal = ref(0)
const interactionPage = ref(1)
const interactionTotal = ref(0)
const loadingConversations = ref(false)
const loadingInteractions = ref(false)
const loadingThread = ref(false)
const sending = ref(false)
const threadEl = ref<HTMLElement | null>(null)

let keywordTimer: ReturnType<typeof setTimeout> | undefined

const selectedConversation = computed(() => conversations.value.find((item) => item.peerId === selectedPeerId.value) ?? null)
const conversationHasMore = computed(() => conversations.value.length < conversationTotal.value)
const interactionHasMore = computed(() => interactions.value.length < interactionTotal.value)

function emitMessageUpdate() {
  window.dispatchEvent(new CustomEvent('message-center:updated'))
}

async function loadSummary() {
  try {
    summary.value = await api.messageSummary()
    emitMessageUpdate()
  } catch {
    summary.value = { unreadDirect: 0, unreadNotifications: 0, unreadTotal: 0 }
  }
}

async function loadConversations(reset = false) {
  if (loadingConversations.value) return
  loadingConversations.value = true
  try {
    const page = reset ? 1 : conversationPage.value
    const result = await api.messageConversations({
      keyword: keyword.value.trim(),
      page,
      size: 30,
    })
    conversationPage.value = page + 1
    conversationTotal.value = result.total
    conversations.value = reset ? result.records : [...conversations.value, ...result.records]
    if (!selectedPeerId.value && conversations.value.length) {
      await selectPeer(conversations.value[0].peerId, false)
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '会话加载失败')
  } finally {
    loadingConversations.value = false
  }
}

async function loadInteractions(reset = false) {
  if (loadingInteractions.value) return
  loadingInteractions.value = true
  try {
    const page = reset ? 1 : interactionPage.value
    const result = await api.messageNotifications({
      type: interactionFilter.value,
      page,
      size: 30,
    })
    interactionPage.value = page + 1
    interactionTotal.value = result.total
    interactions.value = reset ? result.records : [...interactions.value, ...result.records]
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '互动消息加载失败')
  } finally {
    loadingInteractions.value = false
  }
}

async function ensureConversation(peerId: number) {
  if (conversations.value.some((item) => item.peerId === peerId)) return
  const peer = await api.profile(peerId)
  conversations.value = [{
    peerId,
    peer,
    lastMessage: '还没有消息，打个招呼吧',
    unreadCount: 0,
    messageCount: 0,
  }, ...conversations.value]
}

async function selectPeer(peerId: number, syncRoute = true) {
  selectedPeerId.value = peerId
  activeBox.value = 'direct'
  if (syncRoute) {
    void router.replace({ path: '/messages', query: { tab: 'direct', peerId } })
  }
  await ensureConversation(peerId)
  await loadThread(peerId)
}

async function loadThread(peerId: number) {
  loadingThread.value = true
  try {
    const result = await api.messageThread(peerId, 1, 80)
    thread.value = result.records
    const target = conversations.value.find((item) => item.peerId === peerId)
    if (target) target.unreadCount = 0
    await loadSummary()
    await nextTick()
    threadEl.value?.scrollTo({ top: threadEl.value.scrollHeight })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '消息加载失败')
  } finally {
    loadingThread.value = false
  }
}

async function sendMessage() {
  const peerId = selectedPeerId.value
  const content = draftMessage.value.trim()
  if (!peerId || !content || sending.value) return
  sending.value = true
  try {
    const message = await api.sendDirectMessage(peerId, content)
    thread.value = [...thread.value, message]
    draftMessage.value = ''
    const target = conversations.value.find((item) => item.peerId === peerId)
    if (target) {
      target.lastMessage = content
      target.lastMessageAt = message.createdAt
      target.messageCount += 1
    }
    await nextTick()
    threadEl.value?.scrollTo({ top: threadEl.value.scrollHeight, behavior: 'smooth' })
    await loadSummary()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发送失败')
  } finally {
    sending.value = false
  }
}

function openDirect() {
  activeBox.value = 'direct'
  void router.replace({
    path: '/messages',
    query: selectedPeerId.value ? { tab: 'direct', peerId: selectedPeerId.value } : { tab: 'direct' },
  })
}

function openInteractions() {
  activeBox.value = 'interactions'
  void router.replace({ path: '/messages', query: { tab: 'interactions' } })
}

async function markAllCurrentRead() {
  const box = activeBox.value === 'interactions' ? 'notifications' : 'direct'
  try {
    await api.markAllMessagesRead(box)
    if (box === 'direct') {
      conversations.value.forEach((item) => { item.unreadCount = 0 })
    } else {
      interactions.value.forEach((item) => { item.read = true })
    }
    await loadSummary()
    ElMessage.success('已全部标记为已读')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function handleInteractionClick(item: MessageItemView) {
  if (!item.read) {
    try {
      await api.markMessageRead(item.id)
      item.read = true
      await loadSummary()
    } catch {
      // 点击互动消息时，跳转优先于已读状态更新。
    }
  }
  if (item.actionUrl?.startsWith('/')) {
    void router.push(item.actionUrl)
  }
}

function changeInteractionFilter(type: InteractionFilter) {
  interactionFilter.value = type
  void loadInteractions(true)
}

function openProfile(user?: UserSummary) {
  if (!user?.id) return
  void router.push(`/users/${user.id}`)
}

function avatarUrl(user?: UserSummary) {
  const normalized = normalizeMediaUrl(user?.avatarUrl)
  if (normalized) return normalized
  const seed = encodeURIComponent(user?.nickname || user?.username || 'vibelo-user')
  return `https://api.dicebear.com/9.x/adventurer/svg?seed=${seed}`
}

function displayName(user?: UserSummary) {
  return user?.nickname || user?.username || '系统消息'
}

function interactionTitle(item: MessageItemView) {
  return item.title || (item.kind === 'SYSTEM' ? '系统消息' : '互动消息')
}

function formatTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const diff = Date.now() - date.getTime()
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)}分钟前`
  if (diff < day) return `${Math.floor(diff / hour)}小时前`
  if (diff < day * 7) return `${Math.floor(diff / day)}天前`
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

async function applyRouteState() {
  const tab = String(route.query.tab || '')
  const peerId = Number(route.query.peerId)
  if (tab === 'notifications' || tab === 'interactions') {
    activeBox.value = 'interactions'
    return
  }
  activeBox.value = 'direct'
  if (Number.isFinite(peerId) && peerId > 0) {
    await selectPeer(peerId, false)
  }
}

watch(keyword, () => {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    void loadConversations(true)
  }, 260)
})

watch(
  () => [route.query.tab, route.query.peerId],
  () => {
    void applyRouteState()
  },
)

onMounted(async () => {
  await Promise.all([
    loadSummary(),
    loadConversations(true),
    loadInteractions(true),
  ])
  await applyRouteState()
})
</script>

<template>
  <div class="message-center">
    <CommonLeftSidebar />

    <main class="message-center__main">
      <section class="message-center__shell">
        <aside class="message-center__side">
          <header class="message-center__side-head">
            <div>
              <span>消息中心</span>
              <strong>{{ activeBox === 'direct' ? '聊天' : '互动' }}</strong>
            </div>
            <button type="button" @click="markAllCurrentRead">
              <el-icon><Check /></el-icon>
            </button>
          </header>

          <div class="message-center__tabs">
            <button type="button" :class="{ 'is-active': activeBox === 'direct' }" @click="openDirect">
              <el-icon><ChatDotRound /></el-icon>
              聊天
              <em v-if="summary.unreadDirect">{{ summary.unreadDirect }}</em>
            </button>
            <button type="button" :class="{ 'is-active': activeBox === 'interactions' }" @click="openInteractions">
              <el-icon><Bell /></el-icon>
              互动
              <em v-if="summary.unreadNotifications">{{ summary.unreadNotifications }}</em>
            </button>
          </div>

          <label v-if="activeBox === 'direct'" class="message-center__search">
            <el-icon><Search /></el-icon>
            <input v-model="keyword" placeholder="搜索聊天" />
          </label>

          <div v-else class="message-center__filters">
            <button type="button" :class="{ 'is-active': interactionFilter === 'all' }" @click="changeInteractionFilter('all')">全部</button>
            <button type="button" :class="{ 'is-active': interactionFilter === 'interaction' }" @click="changeInteractionFilter('interaction')">互动</button>
            <button type="button" :class="{ 'is-active': interactionFilter === 'system' }" @click="changeInteractionFilter('system')">系统</button>
          </div>

          <div class="message-center__list">
            <template v-if="activeBox === 'direct'">
              <button
                v-for="item in conversations"
                :key="item.peerId"
                type="button"
                class="message-center__conversation"
                :class="{ 'is-active': selectedPeerId === item.peerId }"
                @click="selectPeer(item.peerId)"
              >
                <img :src="avatarUrl(item.peer)" alt="" />
                <span>
                  <strong>{{ displayName(item.peer) }}</strong>
                  <small>{{ item.lastMessage || '还没有消息' }}</small>
                </span>
                <time>{{ formatTime(item.lastMessageAt) }}</time>
                <em v-if="item.unreadCount">{{ item.unreadCount }}</em>
              </button>

              <button
                v-if="conversationHasMore"
                type="button"
                class="message-center__load-more"
                :disabled="loadingConversations"
                @click="loadConversations(false)"
              >
                {{ loadingConversations ? '加载中...' : '加载更多' }}
              </button>

              <div v-if="!loadingConversations && conversations.length === 0" class="message-center__empty-small">
                暂无聊天，去用户主页点击“私信”开始对话。
              </div>
            </template>

            <template v-else>
              <button
                v-for="item in interactions"
                :key="item.id"
                type="button"
                class="message-center__interaction-row"
                :class="{ 'is-unread': !item.read }"
                @click="handleInteractionClick(item)"
              >
                <span>{{ interactionTitle(item) }}</span>
                <small>{{ item.content }}</small>
                <time>{{ formatTime(item.createdAt) }}</time>
              </button>

              <button
                v-if="interactionHasMore"
                type="button"
                class="message-center__load-more"
                :disabled="loadingInteractions"
                @click="loadInteractions(false)"
              >
                {{ loadingInteractions ? '加载中...' : '加载更多' }}
              </button>

              <div v-if="!loadingInteractions && interactions.length === 0" class="message-center__empty-small">
                暂无关注、点赞、收藏或评论消息。
              </div>
            </template>
          </div>
        </aside>

        <section v-if="activeBox === 'direct'" class="message-center__panel">
          <header v-if="selectedConversation" class="message-center__chat-head">
            <img :src="avatarUrl(selectedConversation.peer)" alt="" />
            <div>
              <strong>{{ displayName(selectedConversation.peer) }}</strong>
              <span>{{ selectedConversation.peer.bio || selectedConversation.peer.username }}</span>
            </div>
            <button type="button" @click="openProfile(selectedConversation.peer)">
              <el-icon><User /></el-icon>
              主页
            </button>
          </header>

          <header v-else class="message-center__chat-head">
            <div>
              <strong>选择一个聊天</strong>
              <span>私信会显示在这里。</span>
            </div>
          </header>

          <div ref="threadEl" class="message-center__thread">
            <div v-if="loadingThread" class="message-center__empty-state">正在加载消息...</div>
            <template v-else-if="thread.length">
              <article
                v-for="message in thread"
                :key="message.id"
                class="message-center__bubble"
                :class="{ 'is-me': message.fromMe }"
              >
                <p>{{ message.content }}</p>
                <small>{{ formatTime(message.createdAt) }}</small>
              </article>
            </template>
            <div v-else class="message-center__empty-state">
              <el-icon><ChatDotRound /></el-icon>
              <strong>还没有聊天记录</strong>
              <span>输入一句话开始聊天。</span>
            </div>
          </div>

          <footer class="message-center__composer">
            <input
              v-model="draftMessage"
              :disabled="!selectedPeerId || sending"
              placeholder="输入消息，Enter 发送"
              @keydown.enter.prevent="sendMessage"
            />
            <button type="button" :disabled="!selectedPeerId || !draftMessage.trim() || sending" @click="sendMessage">
              <el-icon><Position /></el-icon>
            </button>
          </footer>
        </section>

        <section v-else class="message-center__panel message-center__interactions">
          <header class="message-center__interactions-head">
            <div>
              <strong>互动消息</strong>
              <span>关注、点赞、收藏、评论都会在这里。</span>
            </div>
            <button type="button" @click="markAllCurrentRead">
              <el-icon><Check /></el-icon>
              全部已读
            </button>
          </header>

          <div class="message-center__interaction-list">
            <article
              v-for="item in interactions"
              :key="item.id"
              class="message-center__interaction-card"
              :class="{ 'is-unread': !item.read }"
              @click="handleInteractionClick(item)"
            >
              <div class="message-center__interaction-icon">
                <el-icon><Bell /></el-icon>
              </div>
              <div>
                <strong>{{ interactionTitle(item) }}</strong>
                <p>{{ item.content }}</p>
                <small>
                  <template v-if="item.sender">{{ displayName(item.sender) }} · </template>
                  <el-icon><Clock /></el-icon>
                  {{ formatTime(item.createdAt) }}
                </small>
              </div>
            </article>

            <button
              v-if="interactionHasMore"
              type="button"
              class="message-center__load-more"
              :disabled="loadingInteractions"
              @click="loadInteractions(false)"
            >
              {{ loadingInteractions ? '加载中...' : '加载更多互动' }}
            </button>

            <div v-if="!loadingInteractions && interactions.length === 0" class="message-center__empty-state">
              <el-icon><Bell /></el-icon>
              <strong>暂无互动消息</strong>
              <span>当有人关注、点赞、收藏或评论你的内容时，会出现在这里。</span>
            </div>
          </div>
        </section>
      </section>
    </main>
  </div>
</template>

<style scoped>
.message-center {
  min-height: calc(100vh - 74px);
  padding: 14px 18px 20px 246px;
  background: #f6f7f9;
  color: #20242f;
}

.message-center button,
.message-center input {
  font: inherit;
}

.message-center__shell {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 12px;
  min-height: calc(100vh - 108px);
}

.message-center__side,
.message-center__panel {
  min-width: 0;
  overflow: hidden;
  border: 1px solid rgba(26, 31, 44, 0.08);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 12px 32px rgba(32, 36, 47, 0.06);
}

.message-center__side {
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  gap: 12px;
  padding: 14px;
}

.message-center__side-head,
.message-center__chat-head,
.message-center__interactions-head,
.message-center__composer {
  display: flex;
  align-items: center;
  gap: 12px;
}

.message-center__side-head,
.message-center__interactions-head {
  justify-content: space-between;
}

.message-center__side-head span,
.message-center__interactions-head span {
  display: block;
  color: #8a91a0;
  font-size: 12px;
}

.message-center__side-head strong,
.message-center__chat-head strong,
.message-center__interactions-head strong {
  color: #1f2531;
  font-size: 20px;
  font-weight: 840;
}

.message-center__side-head button,
.message-center__chat-head button,
.message-center__interactions-head button,
.message-center__composer button {
  border: none;
  border-radius: 10px;
  background: #f2f4f7;
  color: #4f5868;
  cursor: pointer;
}

.message-center__side-head button {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
}

.message-center__tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 4px;
  border-radius: 14px;
  background: #f2f4f7;
}

.message-center__tabs button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 38px;
  border: none;
  border-radius: 11px;
  background: transparent;
  color: #667085;
  cursor: pointer;
  font-weight: 760;
}

.message-center__tabs button.is-active {
  background: #fff;
  color: #ff4f3b;
  box-shadow: 0 8px 18px rgba(32, 36, 47, 0.08);
}

.message-center__tabs em,
.message-center__conversation em {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #ff4f3b;
  color: #fff;
  font-style: normal;
  font-size: 11px;
  font-weight: 900;
  line-height: 18px;
  text-align: center;
}

.message-center__search {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  height: 42px;
  padding: 0 12px;
  border-radius: 999px;
  background: #f2f4f7;
  color: #8a91a0;
}

.message-center__search input {
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: #20242f;
}

.message-center__filters {
  display: flex;
  gap: 8px;
}

.message-center__filters button {
  flex: 1;
  height: 34px;
  border: 1px solid #e7eaf0;
  border-radius: 999px;
  background: #fff;
  color: #667085;
  cursor: pointer;
}

.message-center__filters button.is-active {
  border-color: #ffb2a7;
  background: #fff1ed;
  color: #ff4f3b;
  font-weight: 800;
}

.message-center__list,
.message-center__thread,
.message-center__interaction-list {
  overflow-y: auto;
}

.message-center__list {
  display: grid;
  align-content: start;
  gap: 8px;
}

.message-center__conversation,
.message-center__interaction-row {
  position: relative;
  width: 100%;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: #20242f;
  cursor: pointer;
  text-align: left;
}

.message-center__conversation {
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 72px;
  padding: 10px;
}

.message-center__conversation:hover,
.message-center__conversation.is-active,
.message-center__interaction-row:hover {
  background: #fff1ed;
}

.message-center__conversation img,
.message-center__chat-head img {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  object-fit: cover;
}

.message-center__conversation span {
  min-width: 0;
  display: grid;
  gap: 5px;
}

.message-center__conversation strong,
.message-center__conversation small,
.message-center__interaction-row span,
.message-center__interaction-row small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-center__conversation strong,
.message-center__interaction-row span {
  font-size: 14px;
  font-weight: 820;
}

.message-center__conversation small,
.message-center__chat-head span,
.message-center__bubble small,
.message-center__interaction-row small,
.message-center__interaction-card small,
.message-center__empty-small {
  color: #8a91a0;
  font-size: 12px;
}

.message-center__conversation time {
  align-self: start;
  color: #9aa1ad;
  font-size: 12px;
}

.message-center__conversation em {
  position: absolute;
  right: 10px;
  bottom: 10px;
}

.message-center__interaction-row {
  display: grid;
  gap: 5px;
  padding: 12px;
  border: 1px solid transparent;
}

.message-center__interaction-row.is-unread {
  border-color: #ffd2c8;
  background: #fff8f6;
}

.message-center__interaction-row time {
  color: #a0a7b4;
  font-size: 12px;
}

.message-center__panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
}

.message-center__chat-head,
.message-center__interactions-head {
  min-height: 72px;
  padding: 14px 18px;
  border-bottom: 1px solid #edf0f4;
}

.message-center__chat-head div,
.message-center__interactions-head div {
  min-width: 0;
  display: grid;
  gap: 3px;
  margin-right: auto;
}

.message-center__chat-head button,
.message-center__interactions-head button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 12px;
}

.message-center__thread {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  background: #fbfcfe;
}

.message-center__bubble {
  max-width: min(72%, 620px);
}

.message-center__bubble.is-me {
  align-self: flex-end;
  text-align: right;
}

.message-center__bubble p {
  margin: 0 0 5px;
  padding: 11px 14px;
  border-radius: 14px;
  background: #eef1f5;
  color: #2f3643;
  line-height: 1.6;
}

.message-center__bubble.is-me p {
  background: #ff5a45;
  color: #fff;
}

.message-center__composer {
  padding: 14px 16px;
  border-top: 1px solid #edf0f4;
  background: #fff;
}

.message-center__composer input {
  min-width: 0;
  flex: 1;
  height: 42px;
  border: 1px solid #e3e7ee;
  border-radius: 999px;
  outline: none;
  padding: 0 15px;
}

.message-center__composer button {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  background: #ff5a45;
  color: #fff;
}

.message-center__composer button:disabled,
.message-center__load-more:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.message-center__interactions {
  grid-template-rows: auto minmax(0, 1fr);
}

.message-center__interaction-list {
  display: grid;
  align-content: start;
  gap: 10px;
  padding: 14px;
  background: #fbfcfe;
}

.message-center__interaction-card {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
  padding: 14px;
  border: 1px solid #edf0f4;
  border-radius: 14px;
  background: #fff;
  cursor: pointer;
}

.message-center__interaction-card.is-unread {
  border-color: #ffb2a7;
  background: #fff8f6;
}

.message-center__interaction-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: #fff1ed;
  color: #ff4f3b;
  font-size: 20px;
}

.message-center__interaction-card strong {
  color: #20242f;
  font-size: 16px;
}

.message-center__interaction-card p {
  margin: 7px 0 9px;
  color: #566071;
  line-height: 1.6;
}

.message-center__interaction-card small {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.message-center__load-more {
  width: 100%;
  height: 40px;
  border: 1px solid #e5e8ef;
  border-radius: 999px;
  background: #fff;
  color: #667085;
  cursor: pointer;
}

.message-center__empty-state {
  min-height: 240px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: #8a91a0;
  text-align: center;
}

.message-center__empty-state .el-icon {
  font-size: 34px;
  color: #ff8a75;
}

.message-center__empty-state strong {
  color: #2f3643;
  font-size: 18px;
}

.message-center__empty-small {
  padding: 18px 10px;
  text-align: center;
}

@media (max-width: 980px) {
  .message-center {
    padding: 12px 10px 88px;
  }

  .message-center__shell {
    grid-template-columns: 1fr;
  }

  .message-center__panel {
    min-height: 68vh;
  }
}
</style>
