<script setup lang="ts">
defineOptions({ name: 'DefaultPostDetail' })

import { computed } from 'vue'
import type { PostView } from '../../../types'

const props = withDefaults(defineProps<{
  post: PostView
  variant?: 'default' | 'campus' | 'anime' | 'pet' | 'photography' | 'tech'
}>(), {
  variant: 'default',
})

const extra = computed(() => props.post.extra || {})
const tags = computed(() => (props.post.tags || []).slice(0, 8).map((tag) => `#${tag}`))

function extraText(key: string) {
  const value = key.split('.').reduce<unknown>((current, part) => {
    if (!current || typeof current !== 'object') return undefined
    return (current as Record<string, unknown>)[part]
  }, extra.value)
  if (Array.isArray(value)) return value.filter(Boolean).join(' / ')
  if (value === null || value === undefined || typeof value === 'object') return ''
  return String(value).trim()
}

const detailRows = computed(() => {
  const rows = (() => {
    switch (props.variant) {
      case 'campus':
        return [
          ['校园场景', extraText('campusScene')],
          ['心情', extraText('mood')],
          ['话题', extraText('topic')],
        ]
      case 'anime':
        return [
          ['风格', extraText('style')],
          ['主色调', extraText('mainColor')],
          ['穿搭单品', extraText('items')],
          ['灵感来源', extraText('inspiration')],
          ['季节', extraText('season')],
        ]
      case 'pet':
        return [
          ['宠物名字', extraText('petName')],
          ['宠物类型', extraText('petType')],
          ['品种', extraText('petBreed')],
          ['年龄', extraText('petAge')],
          ['心情', extraText('mood')],
        ]
      case 'photography':
        return [
          ['地点', extraText('location')],
          ['相机', extraText('camera')],
          ['镜头', extraText('lens')],
          ['拍摄时间', extraText('shootingTime')],
          ['光圈', extraText('exif.aperture') || extraText('aperture')],
          ['ISO', extraText('exif.iso') || extraText('iso')],
          ['快门', extraText('exif.shutter') || extraText('shutter')],
        ]
      case 'tech':
        return [
          ['工具名称', extraText('toolName')],
          ['官网', extraText('toolUrl')],
          ['价格模式', extraText('priceType')],
          ['支持平台', extraText('platform')],
          ['优点', extraText('pros')],
          ['缺点', extraText('cons')],
          ['使用场景', extraText('useCase')],
        ]
      default:
        return []
    }
  })()
  return rows.filter((row) => row[1])
})
</script>

<template>
  <section class="channel-detail" :class="`channel-detail--${variant}`">
    <h1>{{ post.title || '未命名作品' }}</h1>
    <p v-if="post.content">{{ post.content }}</p>

    <dl v-if="detailRows.length > 0" class="channel-detail__facts">
      <template v-for="row in detailRows" :key="row[0]">
        <dt>{{ row[0] }}</dt>
        <dd>
          <a v-if="row[0] === '官网'" :href="row[1]" target="_blank" rel="noreferrer">{{ row[1] }}</a>
          <template v-else>{{ row[1] }}</template>
        </dd>
      </template>
    </dl>

    <div v-if="tags.length > 0" class="channel-detail__tags">
      <span v-for="tag in tags" :key="tag">{{ tag }}</span>
    </div>
  </section>
</template>

<style scoped>
.channel-detail {
  display: grid;
  gap: 10px;
}

.channel-detail h1 {
  margin: 2px 0 0;
  color: #1f2531;
  font-size: 32px;
  font-weight: 800;
  line-height: 1.35;
}

.channel-detail p {
  margin: 0;
  color: #4d5564;
  font-size: 15px;
  line-height: 1.72;
  white-space: pre-wrap;
  word-break: break-word;
}

.channel-detail__facts {
  display: grid;
  grid-template-columns: minmax(72px, auto) minmax(0, 1fr);
  gap: 8px 10px;
  margin: 4px 0 0;
  padding: 12px;
  border: 1px solid #edf0f4;
  border-radius: 8px;
  background: #fbfcfe;
}

.channel-detail__facts dt,
.channel-detail__facts dd {
  margin: 0;
  min-width: 0;
}

.channel-detail__facts dt {
  color: #8b93a1;
  font-size: 13px;
}

.channel-detail__facts dd,
.channel-detail__facts a {
  color: #303846;
  font-size: 13px;
  font-weight: 680;
  overflow-wrap: anywhere;
}

.channel-detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.channel-detail__tags span {
  color: #4f7ad8;
  font-size: 13px;
}

.channel-detail--photography h1 {
  letter-spacing: 0;
}

.channel-detail--tech .channel-detail__facts {
  background: #f7f9fc;
}

@media (max-width: 980px) {
  .channel-detail h1 {
    font-size: 22px;
  }
}
</style>
