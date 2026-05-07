<script setup lang="ts">
defineOptions({ name: 'FeedCardRenderer' })

import { computed } from 'vue'
import { channelConfig, defaultChannelConfig, resolveChannelCode } from '../../config/channelConfig'
import type { PostView } from '../../types'

const props = defineProps<{
  post: PostView
  isLiked?: boolean
  isLiking?: boolean
  likeCount?: number
}>()

const emit = defineEmits<{
  open: [post: PostView]
  like: [post: PostView]
}>()

const CardComponent = computed(() => {
  const code = resolveChannelCode(props.post.channelCode || props.post.channel)
  return code ? channelConfig[code].cardComponent : defaultChannelConfig.cardComponent
})
</script>

<template>
  <component
    :is="CardComponent"
    :post="post"
    :is-liked="isLiked"
    :is-liking="isLiking"
    :like-count="likeCount"
    @open="emit('open', $event)"
    @like="emit('like', $event)"
  />
</template>
