<script setup lang="ts">
import { ChatDotRound, MoreFilled, Star } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../services/api'
import { useAuthStore } from '../stores/auth'
import type { CommentView, PostView } from '../types'
import { getPostMediaUrl, hasPostMedia } from '../utils/postMedia'

const props = defineProps<{
  post: PostView
  showFollow?: boolean
  showDelete?: boolean
  showDetailEntry?: boolean
  useDialogDetail?: boolean
  followingAuthor?: boolean
}>()

const emit = defineEmits<{
  refresh: []
  openDetail: [postId: number]
  toggleFollowAuthor: [authorId: number]
}>()

const authStore = useAuthStore()
const router = useRouter()
const commentText = ref('')
const commentsVisible = ref(false)
const comments = ref<CommentView[]>([])
const loadingComments = ref(false)
const reportReason = ref('')
const replyingTo = ref<CommentView | null>(null)
const likedByMe = ref(false)
const favoritedByMe = ref(false)

const isOwnPost = computed(() => authStore.currentUser?.id === props.post.author.id)
const canShowDetailEntry = computed(() => props.showDetailEntry !== false)
const coverUrl = computed(() => getPostMediaUrl(props.post))
const hasMedia = computed(() => hasPostMedia(props.post))

async function toggleLike() {
  if (!authStore.accessToken) {
    ElMessage.warning('请先登录')
    return
  }
  const data = await api.toggleLike(props.post.id)
  likedByMe.value = data.active
  ElMessage.success(data.active ? '点赞成功' : '已取消点赞')
  emit('refresh')
}

async function toggleFavorite() {
  if (!authStore.accessToken) {
    ElMessage.warning('请先登录')
    return
  }
  const data = await api.toggleFavorite(props.post.id)
  favoritedByMe.value = data.active
  ElMessage.success(data.active ? '收藏成功' : '已取消收藏')
  emit('refresh')
}

function toggleFollowAuthor() {
  if (!authStore.accessToken) {
    ElMessage.warning('请先登录')
    return
  }
  emit('toggleFollowAuthor', props.post.author.id)
}

async function toggleComments() {
  commentsVisible.value = !commentsVisible.value
  if (!commentsVisible.value) {
    return
  }
  loadingComments.value = true
  try {
    comments.value = await api.comments(props.post.id)
  } finally {
    loadingComments.value = false
  }
}

async function submitComment() {
  if (!commentText.value.trim()) {
    return
  }
  await api.comment(props.post.id, commentText.value, replyingTo.value?.id)
  commentText.value = ''
  replyingTo.value = null
  ElMessage.success('评论成功')
  if (!commentsVisible.value) {
    commentsVisible.value = true
  }
  await loadComments()
  emit('refresh')
}

async function loadComments() {
  loadingComments.value = true
  try {
    comments.value = await api.comments(props.post.id)
  } finally {
    loadingComments.value = false
  }
}

async function deleteComment(commentId: number, authorId: number) {
  if (authStore.currentUser?.id !== authorId) {
    ElMessage.warning('只能删除自己的评论')
    return
  }
  await api.deleteComment(props.post.id, commentId)
  ElMessage.success('评论已删除')
  await loadComments()
  emit('refresh')
}

async function dislike() {
  if (!authStore.accessToken) {
    ElMessage.warning('请先登录')
    return
  }
  await api.negativeFeedback(props.post.id, { feedbackType: 'NOT_INTERESTED', reason: '用户主动降低类似内容' })
  ElMessage.success('后续会减少类似内容')
  emit('refresh')
}

async function blockAuthor() {
  if (!authStore.accessToken) {
    ElMessage.warning('请先登录')
    return
  }
  await api.blockUser(props.post.author.id)
  ElMessage.success('已屏蔽该作者')
  emit('refresh')
}

async function report() {
  if (!authStore.accessToken) {
    ElMessage.warning('请先登录')
    return
  }
  const reason = reportReason.value.trim() || '疑似违规内容'
  await api.report(props.post.id, reason)
  reportReason.value = ''
  ElMessage.success('举报已提交')
}

function startReply(comment: CommentView) {
  replyingTo.value = comment
  commentText.value = `@${comment.author.nickname} `
}

function cancelReply() {
  replyingTo.value = null
  commentText.value = ''
}

async function deletePost() {
  if (!isOwnPost.value) {
    return
  }
  await api.deletePost(props.post.id)
  ElMessage.success('帖子已删除')
  emit('refresh')
}

function openDetail() {
  void api.trackPostClick(props.post.id, { scene: props.useDialogDetail ? 'dialog' : 'feed' }).catch(() => undefined)
  if (props.useDialogDetail) {
    emit('openDetail', props.post.id)
    return
  }
  void router.push(`/posts/${props.post.id}`)
}

function openAuthorProfile() {
  router.push(`/users/${props.post.author.id}`)
}
</script>

<template>
  <el-card class="post-card" shadow="hover">
    <div class="post-card__header">
      <div>
        <div class="post-card__author-block" @click="openAuthorProfile">
          <img
            class="post-card__author-avatar"
            :src="post.author.avatarUrl || 'https://placehold.co/80x80?text=A'"
            alt="author-avatar"
          />
          <div>
            <div class="post-card__author">{{ post.author.nickname }}</div>
            <div class="post-card__username">@{{ post.author.username }}</div>
          </div>
        </div>
        <div class="post-card__meta">
          <span>{{ new Date(post.createdAt).toLocaleString() }}</span>
        </div>
      </div>
      <div class="post-card__toolbar">
        <el-button v-if="showFollow" size="small" @click="toggleFollowAuthor">
          {{ followingAuthor ? '取消关注' : '关注作者' }}
        </el-button>
        <el-button v-if="showDelete && isOwnPost" size="small" type="danger" plain @click="deletePost">删除</el-button>
        <el-dropdown trigger="click">
          <el-button size="small" text :icon="MoreFilled">更多</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="!isOwnPost" @click="dislike">减少类似内容</el-dropdown-item>
              <el-dropdown-item v-if="!isOwnPost" @click="blockAuthor">屏蔽作者</el-dropdown-item>
              <el-dropdown-item v-if="!isOwnPost" @click="report">举报内容</el-dropdown-item>
              <el-dropdown-item v-if="isOwnPost" disabled>这是你自己的帖子</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div class="post-card__body" @click="openDetail">
      <img v-if="hasMedia" :src="coverUrl" :alt="post.title" class="post-card__cover" />
      <div class="post-card__content">
        <h3>{{ post.title }}</h3>
        <p>{{ post.content || '作者没有填写正文。' }}</p>
      </div>
    </div>

    <div class="post-card__actions">
      <el-button v-if="canShowDetailEntry" text @click="openDetail">详情</el-button>
      <el-button text :type="likedByMe ? 'primary' : undefined" @click="toggleLike">
        {{ likedByMe ? '取消赞' : '点赞' }}
      </el-button>
      <el-button text :type="favoritedByMe ? 'warning' : undefined" :icon="Star" @click="toggleFavorite">
        {{ favoritedByMe ? '取消藏' : '收藏' }}
      </el-button>
      <el-button text :icon="ChatDotRound" @click="toggleComments">评论 {{ post.commentCount }}</el-button>
      <span class="view-count">浏览 {{ post.viewCount }}</span>
    </div>

    <div v-if="commentsVisible" class="post-card__comments">
      <el-skeleton v-if="loadingComments" animated :rows="3" />
      <template v-else>
        <el-empty v-if="comments.length === 0" description="还没有评论" />
        <el-card v-for="item in comments" :key="item.id" shadow="never" class="comment-item">
          <div class="comment-item__header">
            <strong>{{ item.author.nickname }}</strong>
            <span>{{ new Date(item.createdAt).toLocaleString() }}</span>
          </div>
          <div class="comment-item__body">
            <span v-if="item.replyToUser" class="reply-target">回复 {{ item.replyToUser.nickname }}：</span>
            {{ item.content }}
          </div>
          <el-button text size="small" @click="startReply(item)">回复</el-button>
          <el-button
            v-if="authStore.currentUser?.id === item.author.id"
            text
            size="small"
            type="danger"
            @click="deleteComment(item.id, item.author.id)"
          >
            删除
          </el-button>
        </el-card>
      </template>
      <div v-if="replyingTo" class="replying-tip">
        正在回复 {{ replyingTo.author.nickname }}
        <el-button text size="small" @click="cancelReply">取消</el-button>
      </div>
      <div class="comment-editor">
        <el-input v-model="commentText" placeholder="写下你的评论" />
        <el-button type="primary" @click="submitComment">发送</el-button>
      </div>
      <div v-if="!isOwnPost" class="comment-editor">
        <el-input v-model="reportReason" placeholder="可选：举报原因" />
        <el-button @click="report">举报当前帖子</el-button>
      </div>
    </div>
  </el-card>
</template>
