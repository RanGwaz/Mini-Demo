import type { Component } from 'vue'
import AnimeOutfitDetail from '../components/detail/layouts/AnimeOutfitDetail.vue'
import CampusDetail from '../components/detail/layouts/CampusDetail.vue'
import DefaultPostDetail from '../components/detail/layouts/DefaultPostDetail.vue'
import PetDetail from '../components/detail/layouts/PetDetail.vue'
import PhotographyDetail from '../components/detail/layouts/PhotographyDetail.vue'
import TechMomentDetail from '../components/detail/layouts/TechMomentDetail.vue'
import AnimeOutfitCard from '../components/feed/cards/AnimeOutfitCard.vue'
import CampusCard from '../components/feed/cards/CampusCard.vue'
import DefaultFeedCard from '../components/feed/cards/DefaultFeedCard.vue'
import PetCard from '../components/feed/cards/PetCard.vue'
import PhotographyCard from '../components/feed/cards/PhotographyCard.vue'
import TechMomentCard from '../components/feed/cards/TechMomentCard.vue'
import AnimeOutfitPublishForm from '../components/publish/forms/AnimeOutfitPublishForm.vue'
import CampusPublishForm from '../components/publish/forms/CampusPublishForm.vue'
import DefaultPublishForm from '../components/publish/forms/DefaultPublishForm.vue'
import PetPublishForm from '../components/publish/forms/PetPublishForm.vue'
import PhotographyPublishForm from '../components/publish/forms/PhotographyPublishForm.vue'
import TechMomentPublishForm from '../components/publish/forms/TechMomentPublishForm.vue'

export type ChannelCode = 'campus' | 'anime_outfit' | 'pet' | 'photography' | 'tech_moment'

export interface ChannelConfigItem {
  code: ChannelCode
  name: string
  postType: string
  cardComponent: Component
  detailComponent: Component
  publishFormComponent: Component
  waterfall: boolean
}

export const channelConfig = {
  campus: {
    code: 'campus',
    name: '校园生活',
    postType: 'campus_post',
    cardComponent: CampusCard,
    detailComponent: CampusDetail,
    publishFormComponent: CampusPublishForm,
    waterfall: true,
  },
  anime_outfit: {
    code: 'anime_outfit',
    name: '二次元穿搭',
    postType: 'anime_outfit_post',
    cardComponent: AnimeOutfitCard,
    detailComponent: AnimeOutfitDetail,
    publishFormComponent: AnimeOutfitPublishForm,
    waterfall: true,
  },
  pet: {
    code: 'pet',
    name: '宠物日常',
    postType: 'pet_post',
    cardComponent: PetCard,
    detailComponent: PetDetail,
    publishFormComponent: PetPublishForm,
    waterfall: true,
  },
  photography: {
    code: 'photography',
    name: '摄影分享',
    postType: 'photography_post',
    cardComponent: PhotographyCard,
    detailComponent: PhotographyDetail,
    publishFormComponent: PhotographyPublishForm,
    waterfall: true,
  },
  tech_moment: {
    code: 'tech_moment',
    name: '程序员摸鱼',
    postType: 'tech_moment_post',
    cardComponent: TechMomentCard,
    detailComponent: TechMomentDetail,
    publishFormComponent: TechMomentPublishForm,
    waterfall: false,
  },
} satisfies Record<ChannelCode, ChannelConfigItem>

export const defaultChannelConfig = {
  code: 'campus',
  name: '通用内容',
  postType: 'general_post',
  cardComponent: DefaultFeedCard,
  detailComponent: DefaultPostDetail,
  publishFormComponent: DefaultPublishForm,
  waterfall: true,
} satisfies ChannelConfigItem

export function resolveChannelCode(raw?: string | null): ChannelCode | undefined {
  if (!raw) return undefined
  const normalized = raw.trim().toLowerCase().replaceAll('-', '_')
  if (normalized === 'campus_life') return 'campus'
  if (normalized === 'pets') return 'pet'
  if (normalized === 'tool_post' || normalized === 'ai_tool' || normalized === 'overseas') return 'tech_moment'
  return Object.prototype.hasOwnProperty.call(channelConfig, normalized)
    ? normalized as ChannelCode
    : undefined
}
