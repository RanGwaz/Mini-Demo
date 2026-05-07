<script setup lang="ts">
defineOptions({ name: 'CampusDetail' })

import type { PostView } from '../../../types'
import DefaultPostDetail from './DefaultPostDetail.vue'

defineProps<{ post: PostView }>()
</script>

<template>
  <DefaultPostDetail :post="post" variant="campus" />
</template>
