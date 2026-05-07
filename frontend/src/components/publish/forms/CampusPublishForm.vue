<script setup lang="ts">
defineOptions({ name: 'CampusPublishForm' })

import ChannelExtraFields, { type PublishExtraField } from './ChannelExtraFields.vue'

defineProps<{ modelValue: Record<string, unknown> }>()
defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()

const fields: PublishExtraField[] = [
  { key: 'campusScene', label: '校园场景', placeholder: '图书馆 / 食堂 / 宿舍 / 操场' },
  { key: 'mood', label: '心情', placeholder: '治愈 / 吐槽 / 热血 / 松弛' },
  { key: 'topic', label: '话题', placeholder: '自习日常 / 期末周 / 社团活动' },
]
</script>

<template>
  <ChannelExtraFields :model-value="modelValue" :fields="fields" @update:model-value="$emit('update:modelValue', $event)" />
</template>
