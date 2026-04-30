<script setup lang="ts">
defineOptions({ name: 'LiveView' })

import {
  ArrowRight,
  Calendar,
  Headset,
  Location,
  RefreshRight,
  Star,
  TrendCharts,
  VideoCamera,
  View,
} from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import CommonLeftSidebar from '../components/CommonLeftSidebar.vue'

type LiveRoom = {
  id: number
  title: string
  host: string
  category: string
  viewers: string
  likes: string
  cover: string
  avatar: string
}

const router = useRouter()

const categories = ['推荐', '同城', '连麦', '音乐', '健身', '游戏', '情感', '学习', '24小时回放']

const liveRooms: LiveRoom[] = [
  {
    id: 1,
    title: '治愈系歌声｜与你共度温柔夜晚',
    host: '小米在旅行',
    category: '音乐 · 治愈',
    viewers: '2.3万人',
    likes: '8.7万',
    cover: 'https://picsum.photos/seed/vibelo-live-guitar/920/520',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=live-1',
  },
  {
    id: 2,
    title: '居家燃脂训练 全身爆汗卡卡要运动',
    host: '卡卡要运动',
    category: '健身 · 瑜伽',
    viewers: '1.2万人',
    likes: '8.7万人',
    cover: 'https://picsum.photos/seed/vibelo-live-fitness/520/260',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=live-2',
  },
  {
    id: 3,
    title: '峡谷带崽 三排连胜中',
    host: '宅男小明',
    category: '游戏 · 英雄联盟',
    viewers: '8,762',
    likes: '8,762',
    cover: 'https://picsum.photos/seed/vibelo-live-game/520/260',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=live-3',
  },
  {
    id: 4,
    title: '晚安电台｜陪你说晚安',
    host: '暖眠小筑',
    category: '情感 · 助眠',
    viewers: '6,532',
    likes: '6万人',
    cover: 'https://picsum.photos/seed/vibelo-live-radio/520/260',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=live-4',
  },
  {
    id: 5,
    title: '一起学日语｜零基础入门',
    host: '一只桃酱',
    category: '学习 · 日语',
    viewers: '1.1万人',
    likes: '1.1万人',
    cover: 'https://picsum.photos/seed/vibelo-live-study/420/280',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=live-5',
  },
  {
    id: 6,
    title: '深圳华侨城徒步约拍',
    host: '同城今天拍照了吗',
    category: '同城 · 旅行',
    viewers: '4,231',
    likes: '4,231',
    cover: 'https://picsum.photos/seed/vibelo-live-city/420/280',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=live-6',
  },
  {
    id: 7,
    title: '萌宠营业中｜泰迪小泡芙',
    host: '泡芙日记',
    category: '宠物 · 萌宠',
    viewers: '2,548',
    likes: '2,548',
    cover: 'https://picsum.photos/seed/vibelo-live-pet/420/280',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=live-7',
  },
  {
    id: 8,
    title: '晨间瑜伽｜开启活力一天',
    host: '卡卡要运动',
    category: '健身 · 瑜伽',
    viewers: '8,762',
    likes: '8,762',
    cover: 'https://picsum.photos/seed/vibelo-live-yoga/650/300',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=live-8',
  },
  {
    id: 9,
    title: '国服中单｜冲分教学',
    host: '峡谷小导师',
    category: '游戏 · 英雄联盟',
    viewers: '6,231',
    likes: '6,231',
    cover: 'https://picsum.photos/seed/vibelo-live-esport/420/280',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=live-9',
  },
  {
    id: 10,
    title: '护肤分享｜秋冬维稳好物',
    host: '美妆研究所',
    category: '美妆 · 护肤',
    viewers: '3,421',
    likes: '3,421',
    cover: 'https://picsum.photos/seed/vibelo-live-beauty/420/280',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=live-10',
  },
  {
    id: 11,
    title: '助眠ASMR｜雨声白噪音',
    host: '晚安电台',
    category: '情感 · 助眠',
    viewers: '5,632',
    likes: '5,632',
    cover: 'https://picsum.photos/seed/vibelo-live-asmr/420/280',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=live-11',
  },
  {
    id: 12,
    title: '原创弹唱｜不一样的烟火',
    host: '春原小明',
    category: '音乐 · 原创',
    viewers: '3,281',
    likes: '3,281',
    cover: 'https://picsum.photos/seed/vibelo-live-singer/650/300',
    avatar: 'https://api.dicebear.com/9.x/adventurer/svg?seed=live-12',
  },
]

const featuredRoom = liveRooms[0]
const sideRooms = liveRooms.slice(1, 4)
const gridRooms = liveRooms.slice(4)

const hotRank = [
  { name: '小米在旅行', tag: '音乐 · 治愈', value: '2.3万人', avatar: liveRooms[0].avatar },
  { name: '卡卡要运动', tag: '健身 · 瑜伽', value: '1.2万人', avatar: liveRooms[1].avatar },
  { name: '宅男小明', tag: '游戏 · 英雄联盟', value: '8,762', avatar: liveRooms[2].avatar },
  { name: '暖眠小筑', tag: '情感 · 助眠', value: '6,532', avatar: liveRooms[3].avatar },
  { name: '一只桃酱', tag: '学习 · 日语', value: '5,128', avatar: liveRooms[4].avatar },
]

const schedule = [
  { time: '20:00', title: '音乐现场｜点歌阳唱', host: '小宇宙', count: '289人预约' },
  { time: '21:00', title: '电影解说｜经典回顾', host: '影评小站', count: '156人预约' },
  { time: '22:00', title: '深夜电台｜情感树洞', host: '安安', count: '342人预约' },
]

function openRoom(id: number) {
  void router.push(`/live/${id}`)
}
</script>

<template>
  <div class="live-channel">
    <CommonLeftSidebar />

    <main class="live-channel__main">
      <section class="live-channel__panel">
        <header class="live-channel__head">
          <h1>直播频道</h1>
          <div class="live-channel__chips">
            <button v-for="category in categories" :key="category" type="button" :class="{ 'is-active': category === '推荐' }">
              {{ category }}
            </button>
          </div>
        </header>

        <section class="live-channel__featured">
          <article class="live-channel__hero-card" @click="openRoom(featuredRoom.id)">
            <img :src="featuredRoom.cover" :alt="featuredRoom.title" />
            <span class="live-channel__pick"><el-icon><TrendCharts /></el-icon> 精选直播</span>
            <span class="live-channel__live-badge">LIVE</span>
            <div class="live-channel__hero-content">
              <h2>{{ featuredRoom.title }}</h2>
              <div>
                <img :src="featuredRoom.avatar" alt="" />
                <strong>{{ featuredRoom.host }}</strong>
                <span>{{ featuredRoom.category }}</span>
                <em><el-icon><View /></el-icon> {{ featuredRoom.viewers }}</em>
              </div>
            </div>
            <div class="live-channel__watchers">
              <img v-for="room in sideRooms" :key="room.id" :src="room.avatar" alt="" />
              <button type="button"><el-icon><Star /></el-icon></button>
            </div>
          </article>

          <div class="live-channel__side-live">
            <article v-for="room in sideRooms" :key="room.id" @click="openRoom(room.id)">
              <img :src="room.cover" :alt="room.title" />
              <span>LIVE</span>
              <div>
                <h3>{{ room.title }}</h3>
                <p>{{ room.host }}</p>
                <em><el-icon><View /></el-icon> {{ room.viewers }}</em>
                <em><el-icon><Star /></el-icon> {{ room.likes }}</em>
              </div>
            </article>
          </div>
        </section>

        <section class="live-channel__grid">
          <article
            v-for="room in gridRooms"
            :key="room.id"
            :class="{ 'is-wide': room.id === 8 || room.id === 12 }"
            @click="openRoom(room.id)"
          >
            <div class="live-channel__cover">
              <img :src="room.cover" :alt="room.title" />
              <b>LIVE</b>
            </div>
            <div class="live-channel__card-body">
              <img :src="room.avatar" alt="" />
              <div>
                <h3>{{ room.title }}</h3>
                <p>{{ room.host }}</p>
              </div>
              <span><el-icon><View /></el-icon> {{ room.viewers }}</span>
            </div>
          </article>
        </section>

        <footer class="live-channel__loading">
          <span />
          正在加载更多精彩直播...
        </footer>
      </section>
    </main>

    <aside class="live-channel__right">
      <section class="live-channel__right-card">
        <div class="live-channel__right-head">
          <strong><el-icon><TrendCharts /></el-icon> 热门直播榜</strong>
          <button type="button">更多 <el-icon><ArrowRight /></el-icon></button>
        </div>
        <ol class="live-channel__rank">
          <li v-for="(item, index) in hotRank" :key="item.name">
            <b>{{ index + 1 }}</b>
            <img :src="item.avatar" alt="" />
            <span>
              <strong>{{ item.name }}</strong>
              <small>{{ item.tag }}</small>
            </span>
            <em>{{ item.value }}</em>
          </li>
        </ol>
      </section>

      <section class="live-channel__right-card">
        <div class="live-channel__right-head">
          <strong><el-icon><Calendar /></el-icon> 即将开播日历</strong>
          <button type="button">更多 <el-icon><ArrowRight /></el-icon></button>
        </div>
        <div class="live-channel__schedule">
          <article v-for="item in schedule" :key="item.time">
            <time>{{ item.time }}</time>
            <span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.host }} · {{ item.count }}</small>
            </span>
            <button type="button">预约</button>
          </article>
        </div>
      </section>

      <section class="live-channel__right-card live-channel__match-card">
        <div class="live-channel__right-head">
          <strong>AI 匹配直播间</strong>
          <button type="button"><el-icon><RefreshRight /></el-icon> 换一批</button>
        </div>
        <p>为你推荐的直播间</p>
        <small>基于你的兴趣和观看习惯</small>
        <div>
          <img v-for="room in liveRooms.slice(0, 6)" :key="room.id" :src="room.avatar" alt="" />
        </div>
        <button type="button">进入直播间</button>
      </section>

      <section class="live-channel__right-card">
        <div class="live-channel__right-head">
          <strong><el-icon><Headset /></el-icon> 心情频道</strong>
          <button type="button">更多 <el-icon><ArrowRight /></el-icon></button>
        </div>
        <div class="live-channel__moods">
          <span>放松🌙</span>
          <span>开心😊</span>
          <span>治愈🍃</span>
          <span>助眠🌘</span>
          <span>励志💪</span>
          <span>孤独中</span>
        </div>
      </section>

      <section class="live-channel__right-card">
        <div class="live-channel__right-head">
          <strong><el-icon><Location /></el-icon> 同城派对</strong>
          <button type="button">更多 <el-icon><ArrowRight /></el-icon></button>
        </div>
        <div class="live-channel__cities">
          <span>深圳</span>
          <span>广州</span>
          <span>上海</span>
          <span>北京</span>
          <span>成都</span>
          <span>杭州</span>
        </div>
      </section>

      <section class="live-channel__right-card">
        <div class="live-channel__right-head">
          <strong><el-icon><VideoCamera /></el-icon> 精彩切片</strong>
          <button type="button">更多 <el-icon><ArrowRight /></el-icon></button>
        </div>
        <div class="live-channel__clips">
          <img v-for="room in liveRooms.slice(1, 4)" :key="room.id" :src="room.cover" alt="" />
          <button type="button">+</button>
        </div>
      </section>
    </aside>
  </div>
</template>

<style scoped>
.live-channel {
  position: relative;
  min-height: calc(100vh - 74px);
  padding: 14px 318px 34px 246px;
  color: #20242f;
  background: #f7f8fa;
}

.live-channel button {
  font: inherit;
}

.live-channel__main {
  min-width: 0;
}

.live-channel__panel,
.live-channel__right-card {
  border: 1px solid rgba(26, 31, 44, 0.07);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(32, 36, 47, 0.05);
}

.live-channel__panel {
  padding: 20px;
}

.live-channel__head h1 {
  margin: 0 0 14px;
  color: #151b27;
  font-size: 26px;
  font-weight: 860;
}

.live-channel__chips {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
  scrollbar-width: none;
}

.live-channel__chips::-webkit-scrollbar {
  display: none;
}

.live-channel__chips button {
  flex: 0 0 auto;
  height: 34px;
  padding: 0 18px;
  border: 1px solid #e8ebf0;
  border-radius: 999px;
  background: #fff;
  color: #303744;
  cursor: pointer;
  font-size: 14px;
  font-weight: 760;
}

.live-channel__chips button.is-active,
.live-channel__chips button:hover {
  border-color: #ffd4cc;
  background: #fff0ed;
  color: #ff5a45;
}

.live-channel__featured {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr);
  gap: 12px;
  margin-top: 18px;
}

.live-channel__hero-card,
.live-channel__side-live article,
.live-channel__grid article {
  cursor: pointer;
}

.live-channel__hero-card {
  position: relative;
  overflow: hidden;
  min-height: 360px;
  border-radius: 10px;
  background: #20242f;
}

.live-channel__hero-card::after,
.live-channel__side-live article::after,
.live-channel__cover::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.04), rgba(0, 0, 0, 0.64));
}

.live-channel__hero-card > img,
.live-channel__side-live article > img,
.live-channel__cover img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  transition: transform 0.22s ease;
}

.live-channel__hero-card:hover > img,
.live-channel__side-live article:hover > img,
.live-channel__grid article:hover .live-channel__cover img {
  transform: scale(1.03);
}

.live-channel__pick,
.live-channel__live-badge,
.live-channel__cover b,
.live-channel__side-live article > span {
  position: absolute;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: 6px;
  color: #fff;
  font-size: 12px;
  font-weight: 860;
}

.live-channel__pick {
  left: 16px;
  top: 14px;
  padding: 5px 10px;
  background: rgba(32, 25, 18, 0.74);
}

.live-channel__live-badge,
.live-channel__cover b,
.live-channel__side-live article > span {
  right: 10px;
  top: 10px;
  padding: 4px 7px;
  background: #ff4f3b;
}

.live-channel__hero-content {
  position: absolute;
  z-index: 2;
  left: 18px;
  right: 18px;
  bottom: 18px;
  color: #fff;
}

.live-channel__hero-content h2 {
  margin: 0 0 18px;
  font-size: 28px;
  font-weight: 860;
}

.live-channel__hero-content div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.live-channel__hero-content img,
.live-channel__card-body > img {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
}

.live-channel__hero-content span,
.live-channel__hero-content em {
  color: rgba(255, 255, 255, 0.82);
  font-style: normal;
  font-size: 13px;
}

.live-channel__hero-content em {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.live-channel__watchers {
  position: absolute;
  z-index: 3;
  right: 18px;
  bottom: 18px;
  display: inline-flex;
  align-items: center;
}

.live-channel__watchers img {
  width: 34px;
  height: 34px;
  margin-left: -8px;
  border: 2px solid #fff;
  border-radius: 50%;
}

.live-channel__watchers button {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  margin-left: 8px;
  border: 1px solid rgba(255, 255, 255, 0.45);
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.34);
  color: #fff;
}

.live-channel__side-live {
  display: grid;
  gap: 10px;
}

.live-channel__side-live article {
  position: relative;
  overflow: hidden;
  min-height: 112px;
  border-radius: 10px;
  background: #20242f;
}

.live-channel__side-live article div {
  position: absolute;
  z-index: 2;
  inset: 0 0 0 46%;
  display: grid;
  align-content: center;
  gap: 4px;
  padding: 12px;
  color: #fff;
}

.live-channel__side-live h3,
.live-channel__grid h3 {
  margin: 0;
  font-size: 14px;
  line-height: 1.4;
}

.live-channel__side-live p,
.live-channel__side-live em,
.live-channel__grid p,
.live-channel__grid span {
  margin: 0;
  color: rgba(255, 255, 255, 0.82);
  font-style: normal;
  font-size: 12px;
}

.live-channel__side-live em {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.live-channel__grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.live-channel__grid article {
  overflow: hidden;
  border: 1px solid #e8ebf0;
  border-radius: 8px;
  background: #fff;
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

.live-channel__grid article:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 28px rgba(32, 36, 47, 0.09);
}

.live-channel__grid article.is-wide {
  grid-column: span 2;
}

.live-channel__cover {
  position: relative;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  background: #eef1f5;
}

.live-channel__card-body {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  padding: 8px 10px 10px;
}

.live-channel__card-body h3 {
  color: #303744;
}

.live-channel__card-body p {
  color: #8a91a0;
}

.live-channel__card-body span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: #6f7786;
  white-space: nowrap;
}

.live-channel__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 26px 0 4px;
  color: #8a91a0;
  font-size: 13px;
}

.live-channel__loading span {
  width: 16px;
  height: 16px;
  border: 2px solid #dde2ea;
  border-top-color: #ff5a45;
  border-radius: 50%;
  animation: live-spin 0.8s linear infinite;
}

.live-channel__right {
  position: fixed;
  top: 88px;
  right: 16px;
  display: grid;
  gap: 12px;
  width: 292px;
  max-height: calc(100vh - 104px);
  overflow-y: auto;
  padding-right: 2px;
}

.live-channel__right-card {
  padding: 14px;
}

.live-channel__right-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.live-channel__right-head strong,
.live-channel__right-head button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.live-channel__right-head strong {
  color: #20242f;
  font-size: 15px;
  font-weight: 820;
}

.live-channel__right-head button {
  border: none;
  background: transparent;
  color: #8a91a0;
  cursor: pointer;
  font-size: 12px;
}

.live-channel__rank {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.live-channel__rank li {
  display: grid;
  grid-template-columns: 18px 32px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.live-channel__rank b {
  color: #ff5a45;
  font-size: 14px;
}

.live-channel__rank li:nth-child(n + 4) b {
  color: #8a91a0;
}

.live-channel__rank img {
  width: 32px;
  height: 32px;
  border-radius: 50%;
}

.live-channel__rank span {
  min-width: 0;
  display: grid;
}

.live-channel__rank strong,
.live-channel__schedule strong {
  overflow: hidden;
  color: #303744;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.live-channel__rank small,
.live-channel__schedule small {
  overflow: hidden;
  color: #8a91a0;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.live-channel__rank em {
  color: #7c8492;
  font-style: normal;
  font-size: 12px;
}

.live-channel__schedule {
  display: grid;
  gap: 14px;
}

.live-channel__schedule article {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
}

.live-channel__schedule time {
  color: #4d5564;
  font-size: 13px;
  font-weight: 760;
}

.live-channel__schedule span {
  min-width: 0;
  display: grid;
}

.live-channel__schedule button,
.live-channel__match-card > button {
  height: 30px;
  border: none;
  border-radius: 999px;
  background: #ff5a45;
  color: #fff;
  cursor: pointer;
  font-size: 12px;
  font-weight: 760;
}

.live-channel__schedule button {
  padding: 0 12px;
}

.live-channel__match-card {
  background: linear-gradient(180deg, #fff7f4, #fff);
}

.live-channel__match-card p,
.live-channel__match-card small {
  display: block;
  margin: 0;
  color: #6f7786;
  font-size: 13px;
}

.live-channel__match-card div {
  display: flex;
  margin: 12px 0;
}

.live-channel__match-card img {
  width: 26px;
  height: 26px;
  margin-right: -6px;
  border: 2px solid #fff;
  border-radius: 50%;
}

.live-channel__match-card > button {
  width: 100%;
  background: #fff0ed;
  color: #ff5a45;
}

.live-channel__moods,
.live-channel__cities {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.live-channel__moods span,
.live-channel__cities span {
  height: 28px;
  padding: 0 9px;
  border-radius: 999px;
  background: #f5f7fa;
  color: #5f6674;
  font-size: 12px;
  line-height: 28px;
}

.live-channel__cities span {
  min-width: 36px;
  text-align: center;
}

.live-channel__clips {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.live-channel__clips img,
.live-channel__clips button {
  width: 100%;
  aspect-ratio: 1.35 / 1;
  border-radius: 8px;
}

.live-channel__clips img {
  object-fit: cover;
}

.live-channel__clips button {
  border: none;
  background: #f1f3f7;
  color: #4d5564;
  cursor: pointer;
  font-size: 20px;
}

@keyframes live-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1360px) {
  .live-channel {
    padding-right: 14px;
  }

  .live-channel__right {
    display: none;
  }

  .live-channel__grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .live-channel {
    padding: 10px;
  }

  .live-channel__featured {
    grid-template-columns: 1fr;
  }

  .live-channel__side-live {
    grid-template-columns: repeat(3, 1fr);
  }

  .live-channel__side-live article div {
    inset: auto 0 0 0;
  }

  .live-channel__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .live-channel__panel {
    padding: 12px;
  }

  .live-channel__hero-card {
    min-height: 260px;
  }

  .live-channel__hero-content h2 {
    font-size: 20px;
  }

  .live-channel__side-live,
  .live-channel__grid {
    grid-template-columns: 1fr;
  }

  .live-channel__grid article.is-wide {
    grid-column: auto;
  }
}
</style>
