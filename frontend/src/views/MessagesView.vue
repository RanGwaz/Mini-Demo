<script setup lang="ts">
defineOptions({ name: 'MessagesView' })

import { ChatLineRound, MoreFilled, Position, Search, User } from '@element-plus/icons-vue'
import { computed, ref } from 'vue'
import CommonLeftSidebar from '../components/CommonLeftSidebar.vue'

type Conversation = {
  id: number
  name: string
  role: string
  avatar: string
  unread: number
  lastMessage: string
  updatedAt: string
  messages: Array<{
    id: number
    fromMe: boolean
    content: string
    time: string
  }>
}

const keyword = ref('')
const draftMessage = ref('')
const selectedConversationId = ref(1)

const conversations = ref<Conversation[]>([
  {
    id: 1,
    name: '课间小岛',
    role: '校园生活创作者',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=campus-creator',
    unread: 2,
    lastMessage: '你收藏的那篇宿舍改造，我补了一版清单。',
    updatedAt: '10:24',
    messages: [
      { id: 1, fromMe: false, content: '你收藏的那篇宿舍改造，我补了一版清单。', time: '10:18' },
      { id: 2, fromMe: false, content: '如果你也在做桌面整理，可以先看第二张图。', time: '10:24' },
    ],
  },
  {
    id: 2,
    name: '快门慢慢按',
    role: '摄影分享创作者',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=photo-creator',
    unread: 0,
    lastMessage: '周末扫街路线已经整理好了。',
    updatedAt: '昨天',
    messages: [
      { id: 1, fromMe: false, content: '周末扫街路线已经整理好了。', time: '昨天 19:32' },
      { id: 2, fromMe: true, content: '收到，我晚点看一下。', time: '昨天 20:01' },
    ],
  },
  {
    id: 3,
    name: '效率工具研究员',
    role: 'AI/效率工具',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=tool-creator',
    unread: 1,
    lastMessage: '这版模板更适合做内容运营排期。',
    updatedAt: '周一',
    messages: [
      { id: 1, fromMe: false, content: '这版模板更适合做内容运营排期。', time: '周一 14:16' },
    ],
  },
])

const filteredConversations = computed(() => {
  const value = keyword.value.trim().toLowerCase()
  if (!value) return conversations.value
  return conversations.value.filter((item) => (
    item.name.toLowerCase().includes(value)
    || item.role.toLowerCase().includes(value)
    || item.lastMessage.toLowerCase().includes(value)
  ))
})

const selectedConversation = computed(() => (
  conversations.value.find((item) => item.id === selectedConversationId.value) || conversations.value[0]
))

function selectConversation(id: number) {
  selectedConversationId.value = id
  const target = conversations.value.find((item) => item.id === id)
  if (target) target.unread = 0
}

function sendMessage() {
  const content = draftMessage.value.trim()
  const target = selectedConversation.value
  if (!content || !target) return
  target.messages.push({
    id: Date.now(),
    fromMe: true,
    content,
    time: '刚刚',
  })
  target.lastMessage = content
  target.updatedAt = '刚刚'
  draftMessage.value = ''
}
</script>

<template>
  <div class="messages-page">
    <CommonLeftSidebar />

    <main class="messages-page__main">
      <section class="messages-page__panel">
        <aside class="messages-page__list">
          <header>
            <div>
              <span>消息中心</span>
              <strong>私信</strong>
            </div>
            <button type="button" aria-label="更多">
              <el-icon><MoreFilled /></el-icon>
            </button>
          </header>

          <label class="messages-page__search">
            <el-icon><Search /></el-icon>
            <input v-model="keyword" placeholder="搜索会话或创作者" />
          </label>

          <button
            v-for="item in filteredConversations"
            :key="item.id"
            type="button"
            class="messages-page__conversation"
            :class="{ 'is-active': selectedConversation?.id === item.id }"
            @click="selectConversation(item.id)"
          >
            <img :src="item.avatar" alt="" />
            <span>
              <strong>{{ item.name }}</strong>
              <small>{{ item.lastMessage }}</small>
            </span>
            <em v-if="item.unread">{{ item.unread }}</em>
            <time>{{ item.updatedAt }}</time>
          </button>
        </aside>

        <section v-if="selectedConversation" class="messages-page__chat">
          <header>
            <img :src="selectedConversation.avatar" alt="" />
            <div>
              <strong>{{ selectedConversation.name }}</strong>
              <span>{{ selectedConversation.role }}</span>
            </div>
            <button type="button">
              <el-icon><User /></el-icon>
              主页
            </button>
          </header>

          <div class="messages-page__thread">
            <article
              v-for="message in selectedConversation.messages"
              :key="message.id"
              :class="{ 'is-me': message.fromMe }"
            >
              <p>{{ message.content }}</p>
              <small>{{ message.time }}</small>
            </article>
          </div>

          <footer>
            <el-icon><ChatLineRound /></el-icon>
            <input v-model="draftMessage" placeholder="输入消息..." @keydown.enter.prevent="sendMessage" />
            <button type="button" @click="sendMessage">
              <el-icon><Position /></el-icon>
            </button>
          </footer>
        </section>
      </section>
    </main>
  </div>
</template>

<style scoped>
.messages-page {
  min-height: calc(100vh - 74px);
  padding: 14px 16px 36px 246px;
  background: #f7f8fa;
}

.messages-page button,
.messages-page input {
  font: inherit;
}

.messages-page__main {
  max-width: 1280px;
  margin: 0 auto;
}

.messages-page__panel {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  min-height: calc(100vh - 112px);
  overflow: hidden;
  border: 1px solid rgba(26, 31, 44, 0.08);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 18px 42px rgba(32, 36, 47, 0.07);
}

.messages-page__list {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 12px;
  padding: 16px;
  border-right: 1px solid #edf0f4;
}

.messages-page__list header,
.messages-page__chat header,
.messages-page__chat footer {
  display: flex;
  align-items: center;
  gap: 12px;
}

.messages-page__list header {
  justify-content: space-between;
}

.messages-page__list header span {
  display: block;
  color: #ff5a45;
  font-size: 12px;
  font-weight: 780;
}

.messages-page__list header strong,
.messages-page__chat header strong {
  color: #1f2531;
  font-size: 20px;
  font-weight: 840;
}

.messages-page__list header button,
.messages-page__chat header button,
.messages-page__chat footer button {
  border: none;
  border-radius: 8px;
  background: #f4f6f9;
  color: #4f5868;
  cursor: pointer;
}

.messages-page__list header button {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
}

.messages-page__search {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 12px;
  border-radius: 999px;
  background: #f2f4f7;
  color: #8a91a0;
}

.messages-page__search input {
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: #20242f;
}

.messages-page__conversation {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 68px;
  padding: 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #20242f;
  cursor: pointer;
  text-align: left;
}

.messages-page__conversation:hover,
.messages-page__conversation.is-active {
  background: #fff1ed;
}

.messages-page__conversation img,
.messages-page__chat header img {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
}

.messages-page__conversation span {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.messages-page__conversation strong,
.messages-page__conversation small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.messages-page__conversation strong {
  font-size: 14px;
}

.messages-page__conversation small,
.messages-page__chat header span,
.messages-page__thread small {
  color: #8a91a0;
  font-size: 12px;
}

.messages-page__conversation em {
  grid-column: 3;
  min-width: 18px;
  padding: 2px 6px;
  border-radius: 999px;
  background: #ff5a45;
  color: #fff;
  font-style: normal;
  font-size: 11px;
  font-weight: 820;
  text-align: center;
}

.messages-page__conversation time {
  grid-column: 3;
  color: #9aa1ad;
  font-size: 12px;
}

.messages-page__chat {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-width: 0;
}

.messages-page__chat header {
  padding: 18px 20px;
  border-bottom: 1px solid #edf0f4;
}

.messages-page__chat header div {
  min-width: 0;
  display: grid;
  gap: 2px;
  margin-right: auto;
}

.messages-page__chat header button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 12px;
}

.messages-page__thread {
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  padding: 20px;
  background: linear-gradient(180deg, #fbfcfe, #fff);
}

.messages-page__thread article {
  max-width: min(70%, 520px);
}

.messages-page__thread article.is-me {
  align-self: flex-end;
  text-align: right;
}

.messages-page__thread p {
  margin: 0 0 5px;
  padding: 11px 13px;
  border-radius: 12px;
  background: #f2f4f7;
  color: #2f3643;
  line-height: 1.55;
}

.messages-page__thread article.is-me p {
  background: #ff5a45;
  color: #fff;
}

.messages-page__chat footer {
  padding: 14px 18px;
  border-top: 1px solid #edf0f4;
  background: #fff;
}

.messages-page__chat footer input {
  min-width: 0;
  flex: 1;
  height: 38px;
  border: 1px solid #e3e7ee;
  border-radius: 999px;
  outline: none;
  padding: 0 14px;
}

.messages-page__chat footer button {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  background: #ff5a45;
  color: #fff;
}

@media (max-width: 980px) {
  .messages-page {
    padding: 12px 10px 88px;
  }

  .messages-page__panel {
    grid-template-columns: 1fr;
  }

  .messages-page__list {
    border-right: none;
    border-bottom: 1px solid #edf0f4;
  }
}
</style>
