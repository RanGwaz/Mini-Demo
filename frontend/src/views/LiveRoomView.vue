<script setup lang="ts">
defineOptions({ name: 'LiveRoomView' })

import {
  ChatLineRound,
  Check,
  CircleClose,
  Connection,
  DataAnalysis,
  FullScreen,
  Microphone,
  MoreFilled,
  Mute,
  Operation,
  Plus,
  Present,
  RefreshRight,
  Share,
  ShoppingCart,
  Star,
  VideoPause,
  View,
} from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

type RelatedLive = {
  id: number
  title: string
  host: string
  viewers: string
  cover: string
}

const route = useRoute()
const router = useRouter()

const roomId = computed(() => {
  const raw = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id
  const id = Number(raw)
  return Number.isFinite(id) && id > 0 ? id : 1
})

const heroImage = computed(() => `https://picsum.photos/seed/vibelo-live-room-${roomId.value}/1280/760`)

const host = {
  name: '小米在旅行',
  level: 'Lv.26',
  title: '高效燃脂 · 全身循环训练｜新手友好 · 暴汗必练',
  followers: '68.7万',
  online: '1.2万',
  likes: '89.3万',
  avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=room-host',
}

const floatingMessages = [
  { name: '瑜伽爱好者', text: '这个动作对肩颈太友好了！', color: '#7f61ff' },
  { name: '小太阳', text: '主播身材管理太绝了，求饮食清单💪', color: '#ff9d35' },
  { name: 'Sunny', text: '已加入连麦申请', color: '#4aa4ff' },
  { name: '官方管理员', text: '欢迎来到直播间！请文明发言哦~', color: '#ff5a45' },
]

const chatMessages = [
  { name: '瑜伽爱好者', text: '这个动作对肩颈太友好了！', tag: '铁粉 26' },
  { name: '小太阳', text: '主播身材管理太绝了，求饮食清单💪', tag: '铁粉 12' },
  { name: '运动小白', text: '已加入连麦申请', tag: '+15' },
  { name: '官方管理员', text: '欢迎来到直播间！请文明发言哦~', tag: '管理员' },
  { name: 'Sweety', text: '已送出 小心心 x 1 💗', tag: '7' },
  { name: '卡卡罗特', text: '这个训练强度怎么样？', tag: '21' },
  { name: '小米在旅行', text: '@卡卡罗特 适合大多数人哦，按自己的节奏来~', tag: '主播' },
  { name: '一只眼眠', text: '主播，我腰椎不好，能做吗？', tag: '8' },
]

const relatedLives: RelatedLive[] = [
  { id: 21, title: '普拉提 · 核心力量训练', host: '静静的普拉提', viewers: '3289 在线', cover: 'https://picsum.photos/seed/related-pilates/420/240' },
  { id: 22, title: 'HIIT · 高强度燃脂', host: 'Kevin教练', viewers: '4521 在线', cover: 'https://picsum.photos/seed/related-hiit/420/240' },
  { id: 23, title: '产后修复 · 骨盆矫正', host: '小鹿妈妈', viewers: '2678 在线', cover: 'https://picsum.photos/seed/related-recovery/420/240' },
  { id: 24, title: '瑜伽 · 柔韧性提升', host: 'Yuki 瑜伽', viewers: '1836 在线', cover: 'https://picsum.photos/seed/related-yoga/420/240' },
]

const emotionStats = [
  { label: '积极', value: 78, color: '#55d26a' },
  { label: '专注', value: 15, color: '#ff9f40' },
  { label: '平静', value: 5, color: '#5fd3ff' },
  { label: '其他', value: 2, color: '#a989ff' },
]

const voteOptions = [
  { label: '上肢', value: 32, count: 1243 },
  { label: '下肢', value: 48, count: 1867 },
  { label: '核心', value: 15, count: 582 },
  { label: '全身', value: 5, count: 223 },
]

const cohostRequests = [
  { name: '运动小白', reason: '动作指导', avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=cohost-1' },
  { name: '瑜伽爱好者', reason: '请教体式细节', avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=cohost-2' },
  { name: 'Sunny', reason: '分享减脂心得', avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=cohost-3' },
]

function openRoom(id: number) {
  void router.push(`/live/${id}`)
}
</script>

<template>
  <div class="live-room">
    <section class="live-room__top">
      <section class="live-room__player" :style="{ backgroundImage: `linear-gradient(90deg, rgba(0,0,0,.35), rgba(0,0,0,.08)), url(${heroImage})` }">
        <div class="live-room__badges">
          <span class="is-live">LIVE</span>
          <span><el-icon><View /></el-icon> 1.2万 在线</span>
          <span>1080P</span>
          <span><el-icon><Connection /></el-icon> 连麦</span>
        </div>

        <div class="live-room__floating-chat">
          <article v-for="message in floatingMessages" :key="message.name">
            <b :style="{ background: message.color }">{{ message.name }}</b>
            <span>{{ message.text }}</span>
          </article>
        </div>

        <article class="live-room__sale-card">
          <img src="https://picsum.photos/seed/live-product/120/84" alt="" />
          <div>
            <strong>高效燃脂 · 全身循环训练</strong>
            <em>¥69.90</em>
          </div>
          <button type="button"><el-icon><ShoppingCart /></el-icon></button>
        </article>

        <div class="live-room__insights">
          <article>
            <header>
              <strong>AI 实时摘要 <small>Beta</small></strong>
              <button type="button">×</button>
            </header>
            <p>主播正在讲解高频爆发的关键动作：核心收紧、呼吸配合、肩胛稳定。</p>
            <button type="button">展开全部</button>
          </article>
          <article>
            <header><strong><el-icon><Microphone /></el-icon> 实时字幕 · 中文</strong></header>
            <p>主播：大家好，欢迎来到今天的直播间！我们今天会进行一套高效燃脂的全身循环训练。</p>
          </article>
          <article>
            <header><strong><el-icon><Microphone /></el-icon> 同声传译 · 英文</strong></header>
            <p>Host: Hello everyone, welcome to the live stream! Today we'll be doing a full-body fat-burning circuit.</p>
          </article>
        </div>

        <div class="live-room__action-rail">
          <button type="button"><span>👍</span><b>3.2万</b></button>
          <button type="button"><el-icon><Present /></el-icon><b>送礼</b></button>
          <button type="button"><el-icon><MoreFilled /></el-icon><b>更多</b></button>
        </div>

        <footer class="live-room__controls">
          <button type="button"><el-icon><VideoPause /></el-icon></button>
          <button type="button">10</button>
          <button type="button">10</button>
          <button type="button"><el-icon><Mute /></el-icon></button>
          <div><span /></div>
          <button type="button"><el-icon><Operation /></el-icon></button>
          <button type="button"><el-icon><ChatLineRound /></el-icon></button>
          <button type="button"><el-icon><FullScreen /></el-icon></button>
        </footer>
      </section>

      <aside class="live-room__chat">
        <nav>
          <button type="button" class="is-active">聊天</button>
          <button type="button">观众(1.2万)</button>
          <button type="button">排行榜</button>
          <button type="button">连麦申请(12)</button>
        </nav>
        <section class="live-room__notice">
          <strong>房间公告</strong>
          <button type="button"><el-icon><MoreFilled /></el-icon></button>
          <p>欢迎来到小米的健身直播间！每天19:00直播，记得点个关注哦~</p>
        </section>
        <div class="live-room__chat-list">
          <article v-for="message in chatMessages" :key="`${message.name}-${message.text}`">
            <b>{{ message.tag }}</b>
            <span><strong>{{ message.name }}：</strong>{{ message.text }}</span>
          </article>
        </div>
        <footer>
          <input type="text" placeholder="说点什么..." />
          <button type="button">😊</button>
          <button type="button"><el-icon><Plus /></el-icon></button>
          <button type="button">🎁</button>
        </footer>
      </aside>
    </section>

    <section class="live-room__host-row">
      <article class="live-room__host-card">
        <img :src="host.avatar" alt="" />
        <div>
          <h1>{{ host.name }} <span>{{ host.level }}</span></h1>
          <p>{{ host.title }}</p>
          <div class="live-room__host-tags">
            <span>健身</span>
            <span>运动</span>
            <span>减脂</span>
            <span>#新手友好</span>
            <span>#马甲线养成</span>
          </div>
        </div>
        <button type="button">+ 关注</button>
        <em>粉丝 {{ host.followers }}</em>
        <strong>{{ host.online }}<small>在线</small></strong>
        <strong>{{ host.likes }}<small>点赞</small></strong>
        <div class="live-room__host-actions">
          <button type="button"><el-icon><Share /></el-icon> 分享</button>
          <button type="button"><el-icon><Star /></el-icon> 收藏</button>
          <button type="button"><el-icon><MoreFilled /></el-icon></button>
        </div>
      </article>

      <article class="live-room__small-card">
        <strong>商品推荐 🎁</strong>
        <div>
          <img src="https://picsum.photos/seed/product-set/100/76" alt="" />
          <span>运动速干瑜伽套装 <b>¥199.00</b></span>
          <button type="button"><el-icon><ShoppingCart /></el-icon></button>
        </div>
      </article>

      <article class="live-room__small-card">
        <strong>活动</strong>
        <div>
          <img src="https://picsum.photos/seed/live-activity/100/76" alt="" />
          <span>7天打卡挑战赛 <small>参与赢取运动礼包</small></span>
          <button type="button">去参与</button>
        </div>
      </article>

      <article class="live-room__small-card">
        <strong>话题</strong>
        <div>
          <img src="https://api.dicebear.com/9.x/adventurer/svg?seed=topic" alt="" />
          <span># 我的减脂日记 <small>2.3万人参与</small></span>
        </div>
      </article>
    </section>

    <section class="live-room__bottom">
      <section class="live-room__related">
        <div class="live-room__section-head">
          <strong>相关直播推荐</strong>
          <button type="button"><el-icon><RefreshRight /></el-icon> 换一换</button>
        </div>
        <div class="live-room__related-grid">
          <article v-for="item in relatedLives" :key="item.id" @click="openRoom(item.id)">
            <img :src="item.cover" :alt="item.title" />
            <span>LIVE</span>
            <h3>{{ item.title }}</h3>
            <p>{{ item.host }} <em><el-icon><View /></el-icon> {{ item.viewers }}</em></p>
          </article>
        </div>
      </section>

      <section class="live-room__emotion">
        <strong><el-icon><DataAnalysis /></el-icon> 观众情绪热力 <small>Beta</small></strong>
        <div class="live-room__human" />
        <ul>
          <li v-for="item in emotionStats" :key="item.label">
            <span :style="{ background: item.color }" />
            {{ item.label }}
            <b>{{ item.value }}%</b>
          </li>
        </ul>
      </section>

      <section class="live-room__vote">
        <strong>投票互动</strong>
        <p>当前话题：你更想练哪个部位？</p>
        <article v-for="item in voteOptions" :key="item.label" :class="{ 'is-active': item.label === '下肢' }">
          <span>{{ item.label }}</span>
          <div><i :style="{ width: `${item.value}%` }" /></div>
          <em>{{ item.value }}% ({{ item.count }})</em>
        </article>
        <footer>已有 3915 人参与 <button type="button">已投票</button></footer>
      </section>

      <section class="live-room__cohost">
        <strong>连麦申请面板</strong>
        <article v-for="item in cohostRequests" :key="item.name">
          <img :src="item.avatar" alt="" />
          <span><b>{{ item.name }}</b><small>申请：{{ item.reason }}</small></span>
          <button type="button" class="is-accept"><el-icon><Check /></el-icon></button>
          <button type="button"><el-icon><CircleClose /></el-icon></button>
        </article>
        <button type="button" class="live-room__all-requests">查看全部申请</button>
      </section>
    </section>
  </div>
</template>

<style scoped>
.live-room {
  min-height: calc(100vh - 74px);
  padding: 16px 18px 26px;
  color: #20242f;
  background: #f7f8fa;
}

.live-room button,
.live-room input {
  font: inherit;
}

.live-room__top {
  display: grid;
  grid-template-columns: minmax(720px, 1fr) 520px;
  gap: 12px;
}

.live-room__player,
.live-room__chat,
.live-room__host-card,
.live-room__small-card,
.live-room__related,
.live-room__emotion,
.live-room__vote,
.live-room__cohost {
  border: 1px solid rgba(26, 31, 44, 0.07);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(32, 36, 47, 0.05);
}

.live-room__player {
  position: relative;
  overflow: hidden;
  min-height: 536px;
  background-position: center;
  background-size: cover;
}

.live-room__badges {
  position: absolute;
  z-index: 4;
  top: 18px;
  left: 18px;
  display: flex;
  gap: 9px;
}

.live-room__badges span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 11px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.52);
  color: #fff;
  font-size: 13px;
  font-weight: 760;
}

.live-room__badges .is-live {
  background: #ff4f3b;
  font-size: 18px;
  font-weight: 900;
}

.live-room__floating-chat {
  position: absolute;
  z-index: 4;
  left: 20px;
  bottom: 116px;
  display: grid;
  gap: 8px;
  width: 360px;
}

.live-room__floating-chat article {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
  padding: 4px 10px 4px 4px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.34);
  color: #fff;
  font-size: 13px;
}

.live-room__floating-chat b {
  padding: 4px 8px;
  border-radius: 7px;
  font-size: 12px;
}

.live-room__sale-card {
  position: absolute;
  z-index: 5;
  left: 20px;
  bottom: 66px;
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr) 38px;
  gap: 10px;
  align-items: center;
  width: 330px;
  padding: 8px;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.18);
}

.live-room__sale-card img {
  width: 86px;
  height: 58px;
  border-radius: 8px;
  object-fit: cover;
}

.live-room__sale-card div {
  min-width: 0;
  display: grid;
  gap: 5px;
}

.live-room__sale-card strong {
  overflow: hidden;
  color: #303744;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.live-room__sale-card em {
  color: #ff5a45;
  font-style: normal;
  font-size: 17px;
  font-weight: 860;
}

.live-room__sale-card button {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 50%;
  background: #ff5a45;
  color: #fff;
  cursor: pointer;
}

.live-room__insights {
  position: absolute;
  z-index: 4;
  top: 22px;
  right: 18px;
  display: grid;
  gap: 10px;
  width: 292px;
}

.live-room__insights article {
  padding: 14px;
  border-radius: 10px;
  background: rgba(16, 18, 24, 0.72);
  color: #fff;
  backdrop-filter: blur(16px);
}

.live-room__insights header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.live-room__insights strong {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 14px;
}

.live-room__insights small {
  color: #cfd5df;
}

.live-room__insights button {
  border: none;
  background: transparent;
  color: #dce1e8;
}

.live-room__insights article > button {
  display: block;
  width: 130px;
  height: 32px;
  margin: 12px auto 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
}

.live-room__insights p {
  margin: 8px 0 0;
  color: #e3e7ee;
  font-size: 13px;
  line-height: 1.6;
}

.live-room__action-rail {
  position: absolute;
  z-index: 5;
  right: 286px;
  bottom: 82px;
  display: grid;
  gap: 10px;
}

.live-room__action-rail button {
  display: grid;
  place-items: center;
  gap: 3px;
  width: 58px;
  min-height: 58px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
  cursor: pointer;
  backdrop-filter: blur(12px);
}

.live-room__action-rail b {
  font-size: 12px;
}

.live-room__controls {
  position: absolute;
  z-index: 5;
  left: 0;
  right: 0;
  bottom: 0;
  display: grid;
  grid-template-columns: repeat(4, 34px) minmax(0, 1fr) repeat(3, 34px);
  gap: 8px;
  align-items: center;
  height: 54px;
  padding: 0 18px;
  background: linear-gradient(180deg, transparent, rgba(0, 0, 0, 0.52));
}

.live-room__controls button {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #fff;
  cursor: pointer;
}

.live-room__controls div {
  height: 3px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.42);
}

.live-room__controls div span {
  display: block;
  width: 48%;
  height: 100%;
  border-radius: inherit;
  background: #fff;
}

.live-room__chat {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr) auto;
  min-height: 536px;
}

.live-room__chat nav {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  border-bottom: 1px solid #eef1f5;
}

.live-room__chat nav button {
  position: relative;
  height: 44px;
  border: none;
  background: transparent;
  color: #596171;
  cursor: pointer;
  font-size: 13px;
  font-weight: 760;
}

.live-room__chat nav button.is-active {
  color: #ff5a45;
}

.live-room__chat nav button.is-active::after {
  content: '';
  position: absolute;
  left: 18px;
  right: 18px;
  bottom: 0;
  height: 2px;
  background: #ff5a45;
}

.live-room__notice {
  padding: 12px 16px;
  border-bottom: 1px solid #eef1f5;
}

.live-room__notice strong {
  color: #303744;
  font-size: 14px;
}

.live-room__notice button {
  float: right;
  border: none;
  background: transparent;
  color: #8a91a0;
}

.live-room__notice p {
  margin: 10px 0 0;
  padding: 10px;
  border-radius: 8px;
  background: #f7f8fa;
  color: #7b8493;
  font-size: 12px;
}

.live-room__chat-list {
  overflow-y: auto;
  padding: 12px 16px;
}

.live-room__chat-list article {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  color: #4b5361;
  font-size: 13px;
  line-height: 1.5;
}

.live-room__chat-list b {
  flex: 0 0 auto;
  height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  background: #f1f3f7;
  color: #7b8493;
  font-size: 11px;
  line-height: 20px;
}

.live-room__chat-list strong {
  color: #303744;
}

.live-room__chat footer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) repeat(3, 34px);
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #eef1f5;
}

.live-room__chat footer input {
  min-width: 0;
  height: 38px;
  padding: 0 12px;
  border: 1px solid #e8ebf0;
  border-radius: 8px;
  outline: none;
}

.live-room__chat footer button {
  border: none;
  border-radius: 8px;
  background: #f6f7f9;
  color: #596171;
  cursor: pointer;
}

.live-room__host-row {
  display: grid;
  grid-template-columns: minmax(520px, 1fr) 300px 300px 300px;
  gap: 12px;
  margin-top: 12px;
}

.live-room__host-card {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr) auto auto auto auto;
  gap: 14px;
  align-items: center;
  padding: 12px 16px;
}

.live-room__host-card > img {
  width: 56px;
  height: 56px;
  border-radius: 50%;
}

.live-room__host-card h1 {
  margin: 0;
  font-size: 16px;
}

.live-room__host-card h1 span {
  margin-left: 6px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #fff0ed;
  color: #ff5a45;
  font-size: 11px;
}

.live-room__host-card p {
  margin: 4px 0 8px;
  color: #303744;
  font-size: 14px;
  font-weight: 760;
}

.live-room__host-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.live-room__host-tags span {
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: #f2f4f7;
  color: #687180;
  font-size: 12px;
  line-height: 22px;
}

.live-room__host-card > button {
  height: 36px;
  padding: 0 18px;
  border: none;
  border-radius: 8px;
  background: #ff5a45;
  color: #fff;
  cursor: pointer;
  font-weight: 760;
}

.live-room__host-card > em {
  color: #8a91a0;
  font-style: normal;
  font-size: 13px;
  white-space: nowrap;
}

.live-room__host-card > strong {
  display: grid;
  min-width: 70px;
  text-align: center;
  color: #20242f;
  font-size: 16px;
}

.live-room__host-card > strong small {
  color: #8a91a0;
  font-size: 12px;
  font-weight: 500;
}

.live-room__host-actions {
  display: inline-flex;
  gap: 8px;
}

.live-room__host-actions button {
  height: 34px;
  padding: 0 12px;
  border: 1px solid #e8ebf0;
  border-radius: 999px;
  background: #fff;
  color: #596171;
  cursor: pointer;
}

.live-room__small-card {
  padding: 12px;
}

.live-room__small-card > strong,
.live-room__section-head strong,
.live-room__emotion > strong,
.live-room__vote > strong,
.live-room__cohost > strong {
  color: #303744;
  font-size: 15px;
  font-weight: 820;
}

.live-room__small-card div {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  margin-top: 10px;
}

.live-room__small-card img {
  width: 72px;
  height: 52px;
  border-radius: 8px;
  object-fit: cover;
}

.live-room__small-card span {
  min-width: 0;
  display: grid;
  color: #303744;
  font-size: 13px;
}

.live-room__small-card b {
  color: #ff5a45;
}

.live-room__small-card small {
  color: #8a91a0;
}

.live-room__small-card button {
  min-width: 34px;
  height: 34px;
  border: none;
  border-radius: 999px;
  background: #fff0ed;
  color: #ff5a45;
  cursor: pointer;
}

.live-room__bottom {
  display: grid;
  grid-template-columns: minmax(560px, 1.5fr) 300px 300px 300px;
  gap: 12px;
  margin-top: 12px;
}

.live-room__related,
.live-room__emotion,
.live-room__vote,
.live-room__cohost {
  padding: 14px;
}

.live-room__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.live-room__section-head button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  color: #8a91a0;
  cursor: pointer;
  font-size: 12px;
}

.live-room__related-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.live-room__related-grid article {
  position: relative;
  overflow: hidden;
  cursor: pointer;
}

.live-room__related-grid img {
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 8px;
  object-fit: cover;
}

.live-room__related-grid span {
  position: absolute;
  top: 7px;
  left: 7px;
  padding: 3px 6px;
  border-radius: 5px;
  background: #ff4f3b;
  color: #fff;
  font-size: 10px;
  font-weight: 860;
}

.live-room__related-grid h3 {
  margin: 8px 0 4px;
  color: #303744;
  font-size: 14px;
}

.live-room__related-grid p {
  display: flex;
  justify-content: space-between;
  margin: 0;
  color: #7b8493;
  font-size: 12px;
}

.live-room__related-grid em {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-style: normal;
}

.live-room__emotion {
  position: relative;
  overflow: hidden;
  min-height: 220px;
  background: #161d2a;
  color: #fff;
}

.live-room__emotion > strong {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #fff;
}

.live-room__emotion small {
  color: #8a91a0;
}

.live-room__human {
  position: absolute;
  left: 22px;
  bottom: 12px;
  width: 108px;
  height: 150px;
  border-radius: 44% 44% 24% 24%;
  background: radial-gradient(circle at 50% 20%, #ff597a, transparent 26%),
    radial-gradient(circle at 45% 44%, #ffd166, transparent 30%),
    radial-gradient(circle at 50% 70%, #42d392, transparent 34%),
    radial-gradient(circle at 50% 95%, #53d8ff, transparent 30%);
  filter: blur(1px) saturate(1.2);
  opacity: 0.92;
}

.live-room__emotion ul {
  display: grid;
  gap: 12px;
  margin: 34px 0 0 140px;
  padding: 0;
  list-style: none;
  color: #dbe4ef;
  font-size: 13px;
}

.live-room__emotion li {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.live-room__emotion li span {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.live-room__vote {
  display: grid;
  gap: 10px;
}

.live-room__vote p {
  margin: 0;
  color: #7b8493;
  font-size: 13px;
}

.live-room__vote article {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  color: #7b8493;
  font-size: 12px;
}

.live-room__vote article div {
  height: 4px;
  border-radius: 999px;
  background: #edf1f5;
}

.live-room__vote article i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #d7dce4;
}

.live-room__vote article.is-active {
  color: #ff5a45;
}

.live-room__vote article.is-active i {
  background: #ff9d8e;
}

.live-room__vote footer {
  display: flex;
  justify-content: space-between;
  color: #9aa1ad;
  font-size: 12px;
}

.live-room__vote footer button,
.live-room__all-requests {
  height: 30px;
  border: none;
  border-radius: 999px;
  background: #f4f6f8;
  color: #8a91a0;
  cursor: pointer;
}

.live-room__vote footer button {
  padding: 0 14px;
}

.live-room__cohost {
  display: grid;
  gap: 12px;
}

.live-room__cohost article {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) 28px 28px;
  gap: 8px;
  align-items: center;
}

.live-room__cohost img {
  width: 36px;
  height: 36px;
  border-radius: 50%;
}

.live-room__cohost span {
  min-width: 0;
  display: grid;
}

.live-room__cohost b {
  color: #303744;
  font-size: 13px;
}

.live-room__cohost small {
  overflow: hidden;
  color: #8a91a0;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.live-room__cohost article button {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: #fff0ed;
  color: #ff5a45;
  cursor: pointer;
}

.live-room__cohost article button.is-accept {
  background: #ecfff2;
  color: #35b56a;
}

.live-room__all-requests {
  width: 100%;
  color: #ff5a45;
  background: #fff0ed;
}

@media (max-width: 1440px) {
  .live-room__top,
  .live-room__host-row,
  .live-room__bottom {
    grid-template-columns: 1fr;
  }

  .live-room__host-card {
    grid-template-columns: 56px minmax(0, 1fr) auto;
  }

  .live-room__host-card > em,
  .live-room__host-card > strong,
  .live-room__host-actions {
    display: none;
  }
}

@media (max-width: 860px) {
  .live-room {
    padding: 10px;
  }

  .live-room__player {
    min-height: 460px;
  }

  .live-room__insights,
  .live-room__action-rail {
    display: none;
  }

  .live-room__floating-chat,
  .live-room__sale-card {
    width: calc(100% - 40px);
  }

  .live-room__related-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .live-room__badges {
    flex-wrap: wrap;
  }

  .live-room__host-card {
    grid-template-columns: 46px minmax(0, 1fr);
  }

  .live-room__host-card > button {
    grid-column: 1 / -1;
  }

  .live-room__related-grid {
    grid-template-columns: 1fr;
  }
}
</style>
