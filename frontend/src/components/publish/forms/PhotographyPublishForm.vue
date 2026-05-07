<script setup lang="ts">
defineOptions({ name: 'PhotographyPublishForm' })

import ChannelExtraFields, { type PublishExtraField } from './ChannelExtraFields.vue'

defineProps<{ modelValue: Record<string, unknown> }>()
defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()

const fields: PublishExtraField[] = [
  { key: 'camera', label: '相机', placeholder: 'Sony A7M4' },
  { key: 'lens', label: '镜头', placeholder: '35mm F1.8' },
  { key: 'location', label: '地点', placeholder: '东京街头' },
  { key: 'shootingTime', label: '拍摄时间', placeholder: '傍晚' },
  { key: 'aperture', label: '光圈', placeholder: 'f/1.8' },
  { key: 'iso', label: 'ISO', placeholder: '400' },
  { key: 'shutter', label: '快门', placeholder: '1/250s' },
]
</script>

<template>
  <ChannelExtraFields :model-value="modelValue" :fields="fields" @update:model-value="$emit('update:modelValue', $event)" />
</template>
