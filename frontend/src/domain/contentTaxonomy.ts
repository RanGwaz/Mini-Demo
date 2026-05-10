import type { FeedQueryFilters } from '../services/api'

export type FeedModeKey = 'recommend' | 'following' | 'friends'
export type ContentChannelKey = 'campus' | 'anime_outfit' | 'pet' | 'photography' | 'tech_moment'
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
  postType: string
  waterfall: boolean
}

export interface FeedChannelDefinition extends Omit<ContentChannelDefinition, 'key' | 'topicPath' | 'avatar'> {
  key: string
  topicPath?: string
  avatar?: string
}

export interface PublishChannelDefinition extends Omit<ContentChannelDefinition, 'key'> {
  key: PublishChannelKey
}

export const feedModeTabs: FeedModeDefinition[] = [
  { key: 'recommend', label: '推荐' },
  { key: 'following', label: '关注' },
  { key: 'friends', label: '朋友动态' },
]

export const contentChannels: ContentChannelDefinition[] = [
  {
    key: 'campus',
    label: '校园生活',
    desc: '宿舍、课堂、社团和校园日常',
    signal: '12.8万活跃',
    topicPath: '校园生活',
    filters: { channelCode: 'campus', topic: '校园生活 校园 大学生 宿舍 社团 期末 课程 campus_life' },
    keywords: ['campus', 'campus_life', '校园', '大学生', '宿舍', '社团', '课程', '期末'],
    avatar: 'https://picsum.photos/seed/sidebar-campus/80/80',
    postType: 'campus_post',
    waterfall: true,
  },
  {
    key: 'anime_outfit',
    label: '二次元穿搭',
    desc: '谷子、漫展、cos与日常搭配',
    signal: '6.9万活跃',
    topicPath: '二次元穿搭',
    filters: { channelCode: 'anime_outfit', topic: '二次元穿搭 二次元 漫展 cos 穿搭 谷子 痛包 anime_outfit' },
    keywords: ['anime_outfit', '二次元', '漫展', 'cos', '穿搭', '谷子', '痛包'],
    avatar: 'https://picsum.photos/seed/sidebar-anime/80/80',
    postType: 'anime_outfit_post',
    waterfall: true,
  },
  {
    key: 'pet',
    label: '宠物日常',
    desc: '萌宠瞬间与科学养宠经验',
    signal: '15.1万活跃',
    topicPath: '宠物日常',
    filters: { channelCode: 'pet', topic: '宠物日常 宠物 猫 狗 萌宠 养宠 pets' },
    keywords: ['pet', 'pets', '宠物', '猫', '狗', '萌宠', '养宠'],
    avatar: 'https://picsum.photos/seed/sidebar-pet/80/80',
    postType: 'pet_post',
    waterfall: true,
  },
  {
    key: 'photography',
    label: '摄影分享',
    desc: '器材、后期、街拍和作品交流',
    signal: '9.6万活跃',
    topicPath: '摄影分享',
    filters: { channelCode: 'photography', topic: '摄影 摄影分享 相机 镜头 后期 街拍 构图 胶片' },
    keywords: ['photography', '摄影', '摄影分享', '相机', '镜头', '后期', '街拍', '构图'],
    avatar: 'https://picsum.photos/seed/sidebar-photo/80/80',
    postType: 'photography_post',
    waterfall: true,
  },
  {
    key: 'tech_moment',
    label: '程序员摸鱼',
    desc: 'AI 工具、效率工作流和技术趣事',
    signal: '5.4万活跃',
    topicPath: '程序员摸鱼',
    filters: { channelCode: 'tech_moment', topic: '程序员 摸鱼 AI工具 效率工具 开发工具 tech_moment tool_post' },
    keywords: ['tech_moment', 'tool_post', '程序员', '摸鱼', 'AI工具', '效率工具', '开发工具'],
    avatar: 'https://picsum.photos/seed/sidebar-tech/80/80',
    postType: 'tech_moment_post',
    waterfall: false,
  },
]

export const feedChannelTabs: FeedChannelDefinition[] = [
  {
    key: 'all',
    label: '推荐',
    desc: '跨圈层内容流',
    signal: '实时更新',
    keywords: [],
    postType: 'general_post',
    waterfall: true,
  },
  ...contentChannels,
]
export const publishChannels: PublishChannelDefinition[] = contentChannels
export const defaultPublishChannelKey: PublishChannelKey = 'campus'
