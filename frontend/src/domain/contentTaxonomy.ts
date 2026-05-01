import type { FeedQueryFilters } from '../services/api'

export type FeedModeKey = 'recommend' | 'following' | 'friends'
export type ContentChannelKey = 'general' | 'campus_life' | 'photography' | 'anime_outfit' | 'pets' | 'overseas'
export type FeedChannelKey = 'all' | ContentChannelKey
export type PublishChannelKey = ContentChannelKey

export interface FeedModeDefinition {
  key: FeedModeKey
  label: string
}

export interface ContentChannelDefinition {
  key: ContentChannelKey
  label: string
  desc: string
  signal: string
  topicPath: string
  filters?: FeedQueryFilters
  keywords: string[]
  avatar: string
}

export interface FeedChannelDefinition extends Omit<ContentChannelDefinition, 'key' | 'topicPath' | 'avatar'> {
  key: FeedChannelKey
  topicPath?: string
  avatar?: string
}

export const feedModeTabs: FeedModeDefinition[] = [
  { key: 'recommend', label: '推荐' },
  { key: 'following', label: '关注' },
  { key: 'friends', label: '朋友动态' },
]

export const contentChannels: ContentChannelDefinition[] = [
  {
    key: 'general',
    label: '综合',
    desc: '泛生活、观点和轻分享',
    signal: '实时更新',
    topicPath: '综合',
    filters: { topic: '综合 日常 生活 分享 观点' },
    keywords: ['综合', '日常', '生活', '分享', '观点'],
    avatar: 'https://picsum.photos/seed/sidebar-general/80/80',
  },
  {
    key: 'campus_life',
    label: '大学生校园生活',
    desc: '宿舍、社团、期末周和校园灵感',
    signal: '12.8万活跃',
    topicPath: '大学生校园生活',
    filters: { topic: '大学生校园生活 校园 大学生 宿舍 社团 期末 课程' },
    keywords: ['大学生', '校园', '宿舍', '社团', '期末', '课程'],
    avatar: 'https://picsum.photos/seed/sidebar-campus/80/80',
  },
  {
    key: 'photography',
    label: '摄影爱好者',
    desc: '器材、后期、扫街和作品互评',
    signal: '9.6万活跃',
    topicPath: '摄影爱好者',
    filters: { topic: '摄影爱好者 摄影 相机 镜头 扫街 后期 构图 胶片' },
    keywords: ['摄影', '相机', '镜头', '扫街', '后期', '构图', '胶片'],
    avatar: 'https://picsum.photos/seed/sidebar-photo/80/80',
  },
  {
    key: 'anime_outfit',
    label: '二次元穿搭',
    desc: '谷子、痛包、漫展和日常搭配',
    signal: '6.9万活跃',
    topicPath: '二次元穿搭',
    filters: { topic: '二次元穿搭 二次元 穿搭 漫展 cos 痛包 谷子' },
    keywords: ['二次元', '穿搭', '漫展', 'cos', '痛包', '谷子'],
    avatar: 'https://picsum.photos/seed/sidebar-anime/80/80',
  },
  {
    key: 'pets',
    label: '宠物日常',
    desc: '猫狗日记、萌宠瞬间和养宠经验',
    signal: '15.1万活跃',
    topicPath: '宠物日常',
    filters: { topic: '宠物日常 宠物 猫 狗 萌宠 养宠' },
    keywords: ['宠物', '猫', '狗', '萌宠', '养宠'],
    avatar: 'https://picsum.photos/seed/sidebar-pet/80/80',
  },
  {
    key: 'overseas',
    label: '留学生生活',
    desc: '租房、做饭、课程和异国日常',
    signal: '5.4万活跃',
    topicPath: '留学生生活',
    filters: { topic: '留学生生活 留学 海外 租房 异国 交换生 课程' },
    keywords: ['留学', '海外', '租房', '异国', '交换', '课程'],
    avatar: 'https://picsum.photos/seed/sidebar-abroad/80/80',
  },
]

export const feedChannelTabs: FeedChannelDefinition[] = [
  {
    key: 'all',
    label: '全部频道',
    desc: '跨圈层内容流',
    signal: '实时更新',
    keywords: [],
  },
  ...contentChannels,
]

export const publishChannels = contentChannels
export const defaultPublishChannelKey: PublishChannelKey = 'general'
export const featuredAudienceChannels = contentChannels.filter((channel) => channel.key !== 'general')

export const tagGroups = [
  {
    title: '内容话题',
    tags: ['校园', '宿舍', '摄影', '扫街', '穿搭', '漫展', '宠物', '猫狗日记', '留学', '租房'],
  },
  {
    title: '内容形式',
    tags: ['学习笔记', '日常记录', '经验分享', '效率工具', '灵感收藏', '作品展示'],
  },
]
