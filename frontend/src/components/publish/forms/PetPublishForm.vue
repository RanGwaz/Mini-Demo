<script setup lang="ts">
defineOptions({ name: 'PetPublishForm' })

import ChannelExtraFields, { type PublishExtraField } from './ChannelExtraFields.vue'

defineProps<{ modelValue: Record<string, unknown> }>()
defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()

const fields: PublishExtraField[] = [
  { key: 'petName', label: '宠物名字', placeholder: '奶盖' },
  { key: 'petType', label: '宠物类型', placeholder: '猫 / 狗 / 兔子' },
  { key: 'petBreed', label: '品种', placeholder: '英短 / 柯基 / 布偶' },
  { key: 'petAge', label: '年龄', placeholder: '2岁' },
  { key: 'mood', label: '心情', placeholder: '犯困 / 撒娇 / 活力满满' },
]
</script>

<template>
  <ChannelExtraFields :model-value="modelValue" :fields="fields" @update:model-value="$emit('update:modelValue', $event)" />
</template>
