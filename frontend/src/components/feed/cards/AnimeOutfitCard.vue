<script setup lang="ts">
defineOptions({ name: 'AnimeOutfitCard' })

import type { PostView } from '../../../types'
import DefaultFeedCard from './DefaultFeedCard.vue'

defineProps<{
  post: PostView
  isLiked?: boolean
  isLiking?: boolean
  likeCount?: number
}>()

defineEmits<{
  open: [post: PostView]
  like: [post: PostView]
}>()
</script>

<template>
  <DefaultFeedCard
    :post="post"
    :is-liked="isLiked"
    :is-liking="isLiking"
    :like-count="likeCount"
    variant="anime"
    @open="$emit('open', $event)"
    @like="$emit('like', $event)"
  />
</template>
