<script setup lang="ts">
defineOptions({ name: 'AnimeOutfitPublishForm' })

import ChannelExtraFields, { type PublishExtraField } from './ChannelExtraFields.vue'

defineProps<{ modelValue: Record<string, unknown> }>()
defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()

const fields: PublishExtraField[] = [
  { key: 'style', label: '风格', placeholder: '日系校园 / 洛丽塔 / 机能风' },
  { key: 'mainColor', label: '主色调', placeholder: '浅蓝色 / 黑白 / 奶油色' },
  { key: 'items', label: '穿搭单品', placeholder: '蓝色外套，白色百褶裙，帆布鞋', type: 'list' },
  { key: 'inspiration', label: '灵感来源', placeholder: '校园番女主穿搭' },
  { key: 'season', label: '季节', placeholder: '春季 / 夏日 / 秋冬' },
]
</script>

<template>
  <ChannelExtraFields :model-value="modelValue" :fields="fields" @update:model-value="$emit('update:modelValue', $event)" />
</template>
