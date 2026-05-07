<script setup lang="ts">
defineOptions({ name: 'TechMomentDetail' })

import type { PostView } from '../../../types'
import DefaultPostDetail from './DefaultPostDetail.vue'

defineProps<{ post: PostView }>()
</script>

<template>
  <DefaultPostDetail :post="post" variant="tech" />
</template>
