<script setup lang="ts">
defineOptions({ name: 'PostDetailRenderer' })

import { computed } from 'vue'
import { channelConfig, defaultChannelConfig, resolveChannelCode } from '../../config/channelConfig'
import type { PostView } from '../../types'

const props = defineProps<{
  post: PostView
}>()

const DetailComponent = computed(() => {
  const code = resolveChannelCode(props.post.channelCode || props.post.channel)
  return code ? channelConfig[code].detailComponent : defaultChannelConfig.detailComponent
})
</script>

<template>
  <component :is="DetailComponent" :post="post" />
</template>
