<script setup lang="ts">
defineOptions({ name: 'ChannelExtraFields' })

export interface PublishExtraField {
  key: string
  label: string
  placeholder: string
  type?: 'text' | 'textarea' | 'list'
}

const props = defineProps<{
  modelValue: Record<string, unknown>
  fields: PublishExtraField[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
}>()

function fieldValue(field: PublishExtraField) {
  const value = props.modelValue[field.key]
  if (Array.isArray(value)) return value.join('，')
  if (value === null || value === undefined || typeof value === 'object') return ''
  return String(value)
}

function normalizeFieldValue(field: PublishExtraField, raw: string) {
  if (field.type !== 'list') return raw
  return raw
    .split(/[，,\n]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function updateField(field: PublishExtraField, raw: string) {
  emit('update:modelValue', {
    ...props.modelValue,
    [field.key]: normalizeFieldValue(field, raw),
  })
}

function eventValue(event: Event) {
  const target = event.target
  return target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement ? target.value : ''
}
</script>

<template>
  <section v-if="fields.length > 0" class="channel-extra-fields">
    <div class="channel-extra-fields__head">
      <strong>频道字段</strong>
      <small>差异化信息会写入 extra</small>
    </div>
    <label v-for="field in fields" :key="field.key">
      <span>{{ field.label }}</span>
      <textarea
        v-if="field.type === 'textarea'"
        :value="fieldValue(field)"
        :placeholder="field.placeholder"
        rows="3"
        @input="updateField(field, eventValue($event))"
      />
      <input
        v-else
        :value="fieldValue(field)"
        :placeholder="field.placeholder"
        @input="updateField(field, eventValue($event))"
      />
    </label>
  </section>
</template>

<style scoped>
.channel-extra-fields {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid #eceff4;
  border-radius: 8px;
  background: #fbfcfe;
}

.channel-extra-fields__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.channel-extra-fields__head strong {
  color: #242b38;
  font-size: 15px;
}

.channel-extra-fields__head small {
  color: #9aa1ad;
  font-size: 12px;
}

.channel-extra-fields label {
  display: grid;
  gap: 6px;
}

.channel-extra-fields label span {
  color: #4d5564;
  font-size: 13px;
  font-weight: 720;
}

.channel-extra-fields input,
.channel-extra-fields textarea {
  width: 100%;
  border: 1px solid #e1e6ef;
  border-radius: 8px;
  outline: none;
  color: #2d3440;
  font: inherit;
  background: #fff;
}

.channel-extra-fields input {
  height: 36px;
  padding: 0 11px;
}

.channel-extra-fields textarea {
  min-height: 74px;
  padding: 10px 11px;
  resize: vertical;
  line-height: 1.55;
}

.channel-extra-fields input:focus,
.channel-extra-fields textarea:focus {
  border-color: #ffb8ac;
}
</style>
