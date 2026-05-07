<script setup lang="ts">
defineOptions({ name: 'PublishFormRenderer' })

import { computed } from 'vue'
import { channelConfig, defaultChannelConfig, resolveChannelCode } from '../../config/channelConfig'

const props = defineProps<{
  channelCode: string
  modelValue: Record<string, unknown>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
}>()

const FormComponent = computed(() => {
  const code = resolveChannelCode(props.channelCode)
  return code ? channelConfig[code].publishFormComponent : defaultChannelConfig.publishFormComponent
})
</script>

<template>
  <component
    :is="FormComponent"
    :model-value="modelValue"
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>
