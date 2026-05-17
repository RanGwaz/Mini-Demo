<script setup lang="ts">
defineOptions({ name: 'PostDetailRenderer' })

import { computed } from 'vue'
import DefaultPostDetail from './layouts/DefaultPostDetail.vue'
import type { PostView } from '../../types'

const props = defineProps<{
  post: PostView
}>()

const DetailComponent = computed(() => {
  return DefaultPostDetail
})
</script>

<template>
  <component :is="DetailComponent" :post="post" />
</template>
