<script setup lang="ts">
defineOptions({ name: 'AnimeOutfitDetail' })

import type { PostView } from '../../../types'
import DefaultPostDetail from './DefaultPostDetail.vue'

defineProps<{ post: PostView }>()
</script>

<template>
  <DefaultPostDetail :post="post" variant="anime" />
</template>
