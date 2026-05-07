<script setup lang="ts">
defineOptions({ name: 'DefaultPublishForm' })

defineProps<{ modelValue: Record<string, unknown> }>()
defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()
</script>

<template>
  <section class="default-publish-form">
    <strong>通用内容</strong>
    <span>这个频道暂无扩展字段，会按统一 Post 结构发布。</span>
  </section>
</template>

<style scoped>
.default-publish-form {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #eceff4;
  border-radius: 8px;
  background: #fbfcfe;
}

.default-publish-form strong {
  color: #242b38;
  font-size: 15px;
}

.default-publish-form span {
  color: #8d95a3;
  font-size: 13px;
}
</style>
