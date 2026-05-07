<script setup lang="ts">
defineOptions({ name: 'TechMomentPublishForm' })

import ChannelExtraFields, { type PublishExtraField } from './ChannelExtraFields.vue'

defineProps<{ modelValue: Record<string, unknown> }>()
defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()

const fields: PublishExtraField[] = [
  { key: 'toolName', label: '工具名称', placeholder: 'Cursor / Claude Code / Raycast' },
  { key: 'toolUrl', label: '官网链接', placeholder: 'https://cursor.com' },
  { key: 'priceType', label: '价格模式', placeholder: 'free / freemium / paid' },
  { key: 'platform', label: '支持平台', placeholder: 'Mac，Windows，Web', type: 'list' },
  { key: 'pros', label: '优点', placeholder: '代码补全强，上下文理解不错', type: 'list' },
  { key: 'cons', label: '缺点', placeholder: '免费额度有限', type: 'list' },
  { key: 'useCase', label: '使用场景', placeholder: '适合前端页面和代码重构', type: 'textarea' },
]
</script>

<template>
  <ChannelExtraFields :model-value="modelValue" :fields="fields" @update:model-value="$emit('update:modelValue', $event)" />
</template>
