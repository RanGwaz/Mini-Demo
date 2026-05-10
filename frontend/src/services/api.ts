import http, { guestHttp, LONG_REQUEST_TIMEOUT_MS, unwrap } from './http'
import type {
  AuthTokenResponse,
  BindPhoneRequest,
  ChangeUserNoRequest,
  CommentView,
  HttpMethod,
  PageResponse,
  PhoneSmsLoginRequest,
  PostInteractionStatus,
  PostView,
  SearchResult,
  FollowStatus,
  ToggleResult,
  UploadResponse,
  UserStats,
  UserSummary,
} from '../types'

export interface CreatePostAssetPayload {
  objectKey: string
  fileUrl: string
  fileType: string
  thumbUrl?: string
  width?: number
  height?: number
  sortOrder: number
}

export interface FeedQueryFilters {
  channelCode?: string
  topic?: string
  topicId?: number
  topicSlug?: string
  style?: string
  tag?: string
}

export type FeedRequestAuthMode = 'session' | 'guest'

export interface ChannelView {
  code: string
  name: string
  description: string
  icon?: string
  sortOrder: number
  postType: string
  waterfall: boolean
}

export interface TopicView {
  id: number
  name: string
  slug: string
  description?: string
  coverUrl?: string
  status?: string
  riskLevel?: string
  topicType?: string
  postCount?: number
  followerCount?: number
  hotScore?: number
}

export interface AdminOverview {
  activeChannels: number
  activeTopics: number
  approvedPosts: number
  pendingPosts: number
  rejectedPosts: number
  importBatches: number
}

export interface AdminChannelView {
  id: number
  code: string
  name: string
  description?: string
  icon?: string
  coverUrl?: string
  sortOrder?: number
  status?: string
  enabled?: boolean
  navGroup?: string
  defaultPostType?: string
  waterfallEnabled?: boolean
  publishEnabled?: boolean
  recommendEnabled?: boolean
  configJson?: string
}

export interface AdminTopicView {
  id: number
  name: string
  slug: string
  description?: string
  coverUrl?: string
  status?: string
  riskLevel?: string
  topicType?: string
  source?: string
  postCount?: number
  followerCount?: number
  hotScore?: number
}

export interface TopicAliasView {
  id: number
  alias: string
  normalizedAlias: string
  source?: string
}

export interface TopicBindingView {
  channelCode: string
  weight?: number
  status?: string
}

export interface AdminTopicDetail {
  topic: AdminTopicView
  aliases: TopicAliasView[]
  bindings: TopicBindingView[]
}

export interface AdminPostView {
  post: PostView
  auditStatus?: string
  visibility?: string
  qualityScore?: number
  safetyScore?: number
  hotScore?: number
}

export interface ContentImportBatchView {
  id: number
  name: string
  description?: string
  sourceType?: string
  status?: string
  totalCount?: number
  successCount?: number
  failedCount?: number
  operatorId?: number
  startedAt?: string
  finishedAt?: string
  createdAt?: string
}

export interface ContentImportItemView {
  id: number
  batchId: number
  postId?: number
  title?: string
  content?: string
  channelCode?: string
  topics: string[]
  imageUrls: string[]
  status?: string
  errorMessage?: string
  createdAt?: string
}

export interface ContentRebuildTaskView {
  id: number
  taskType: string
  status: string
  scopeType: string
  scopeId?: string
  batchId?: number
  postId?: number
  totalCount?: number
  successCount?: number
  failedCount?: number
  paramsJson?: string
  errorMessage?: string
  operatorId?: number
  startedAt?: string
  finishedAt?: string
  createdAt?: string
}

export interface FeedRequestLogView {
  id: number
  requestId: string
  userId?: number
  surface: string
  pageNo: number
  pageSize: number
  seed?: string
  filtersJson?: string
  userSegment?: string
  experimentId?: string
  experimentBucket?: string
  totalCandidates?: number
  returnedCount?: number
  latencyMs?: number
  degraded?: boolean
  createdAt?: string
}

export interface FeedImpressionLogView {
  id: number
  requestId: string
  userId?: number
  postId: number
  rankPosition: number
  recallSource?: string
  rankScore?: number
  channelCode?: string
  topicNames?: string
  reason?: string
  createdAt?: string
}

export type AdminChannelPayload = Partial<Omit<AdminChannelView, 'id'>>
export type AdminTopicPayload = Partial<Omit<AdminTopicView, 'id'>> & { channelCodes?: string[] }
export type AdminImportItemPayload = {
  title?: string
  content?: string
  channelCode?: string
  topics?: string[]
  imageUrls?: string[]
}
export type AdminRebuildTaskPayload = {
  taskType: string
  scopeType?: string
  scopeId?: string
  batchId?: number
  postId?: number
  params?: Record<string, unknown>
}

const slowRequestConfig = {
  timeout: LONG_REQUEST_TIMEOUT_MS,
}

export const api = {
  register(payload: { username: string; password: string; nickname: string }) {
    return unwrap<AuthTokenResponse>(http.post('/api/auth/register', payload))
  },
  login(payload: { username: string; password: string }) {
    return unwrap<AuthTokenResponse>(http.post('/api/auth/login', payload))
  },
  sendSmsCode(phone: string) {
    return unwrap<void>(http.post('/api/auth/sms/send', { phone }))
  },
  phoneSmsLogin(payload: PhoneSmsLoginRequest) {
    return unwrap<AuthTokenResponse>(http.post('/api/auth/sms/login', payload))
  },
  logout() {
    return unwrap<void>(http.post('/api/auth/logout'))
  },
  bindPhone(payload: BindPhoneRequest) {
    return unwrap<void>(http.post('/api/auth/bind-phone', payload))
  },
  changeUserNo(payload: ChangeUserNoRequest) {
    return unwrap<AuthTokenResponse>(http.put('/api/auth/user-no', payload))
  },
  me() {
    return unwrap<AuthTokenResponse>(http.get('/api/auth/me'))
  },
  homeFeed(page = 1, size = 50, seed?: string, filters?: FeedQueryFilters, authMode: FeedRequestAuthMode = 'session') {
    const client = authMode === 'guest' ? guestHttp : http
    return unwrap<PageResponse<PostView>>(client.get('/api/feed', {
      ...slowRequestConfig,
      params: {
        page,
        pageSize: size,
        ...(seed ? { seed } : {}),
        ...(filters?.channelCode ? { channelCode: filters.channelCode } : {}),
        ...(filters?.topic ? { topic: filters.topic } : {}),
        ...(filters?.topicId ? { topicId: filters.topicId } : {}),
        ...(filters?.topicSlug ? { topicSlug: filters.topicSlug } : {}),
        ...(filters?.style ? { style: filters.style } : {}),
        ...(filters?.tag ? { tag: filters.tag } : {}),
      },
    }))
  },
  channels() {
    return unwrap<ChannelView[]>(guestHttp.get('/api/channels'))
  },
  channelDetail(code: string) {
    return unwrap<ChannelView>(guestHttp.get(`/api/channels/${code}`))
  },
  channelTopics(code: string, limit = 30) {
    return unwrap<TopicView[]>(guestHttp.get(`/api/channels/${code}/topics`, { params: { limit } }))
  },
  channelPosts(code: string, page = 1, size = 24, sort: 'hot' | 'latest' = 'hot') {
    return unwrap<PageResponse<PostView>>(guestHttp.get(`/api/channels/${code}/posts`, {
      ...slowRequestConfig,
      params: { page, size, sort },
    }))
  },
  searchTopics(keyword = '', limit = 20) {
    return unwrap<TopicView[]>(guestHttp.get('/api/topics/search', {
      params: {
        ...(keyword ? { keyword } : {}),
        limit,
      },
    }))
  },
  trendingTopics(limit = 20) {
    return unwrap<TopicView[]>(guestHttp.get('/api/topics/trending', { params: { limit } }))
  },
  topicDetail(slug: string) {
    return unwrap<TopicView>(guestHttp.get(`/api/topics/${slug}`))
  },
  topicPosts(slug: string, page = 1, size = 24, sort: 'hot' | 'latest' = 'hot') {
    return unwrap<PageResponse<PostView>>(guestHttp.get(`/api/topics/${slug}/posts`, {
      ...slowRequestConfig,
      params: { page, size, sort },
    }))
  },
  relatedTopics(slug: string, limit = 12) {
    return unwrap<TopicView[]>(guestHttp.get(`/api/topics/${slug}/related`, { params: { limit } }))
  },
  followTopic(topicId: number) {
    return unwrap<void>(http.post(`/api/topics/${topicId}/follow`))
  },
  unfollowTopic(topicId: number) {
    return unwrap<void>(http.delete(`/api/topics/${topicId}/follow`))
  },
  similarPosts(postId: number, page = 1, size = 24, filters?: FeedQueryFilters, authMode: FeedRequestAuthMode = 'session') {
    const client = authMode === 'guest' ? guestHttp : http
    return unwrap<PageResponse<PostView>>(client.get(`/api/feed/posts/${postId}/similar`, {
      ...slowRequestConfig,
      params: {
        page,
        size,
        ...(filters?.topic ? { topic: filters.topic } : {}),
        ...(filters?.topicId ? { topicId: filters.topicId } : {}),
        ...(filters?.topicSlug ? { topicSlug: filters.topicSlug } : {}),
        ...(filters?.style ? { style: filters.style } : {}),
        ...(filters?.tag ? { tag: filters.tag } : {}),
      },
    }))
  },
  uploadImage(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return unwrap<UploadResponse>(
      http.post('/api/media/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }),
    )
  },
  createPost(payload: {
    title: string
    content: string
    channel: string
    channelCode?: string
    postType?: string
    imageUrls?: string[]
    extra?: Record<string, unknown>
    tags?: string[]
    topicIds?: number[]
    topics?: string[]
    assets?: CreatePostAssetPayload[]
  }) {
    return unwrap<PostView>(http.post('/api/posts', payload))
  },
  trackBehavior(payload: {
    postId: number
    channelCode?: string
    behaviorType: string
    duration?: number
    scene?: string
    position?: number
  }) {
    return unwrap<void>(http.post('/api/behaviors', payload))
  },
  postDetail(postId: number, scene = 'detail', authMode: FeedRequestAuthMode = 'session') {
    const client = authMode === 'guest' ? guestHttp : http
    return unwrap<PostView>(client.get(`/api/posts/${postId}`, { params: { scene } }))
  },
  trackPostClick(postId: number, payload?: { scene?: string; position?: number; method?: HttpMethod }) {
    const scene = payload?.scene ?? 'feed'
    const position = payload?.position
    const method = payload?.method ?? 'post'
    if (method === 'get') {
      return unwrap<void>(http.get(`/api/posts/${postId}/click`, { params: { scene, position } }))
    }
    return unwrap<void>(http.post(`/api/posts/${postId}/click`, null, { params: { scene, position } }))
  },
  trackPostShare(postId: number, scene = 'detail') {
    return unwrap<void>(http.post(`/api/posts/${postId}/share`, null, { params: { scene } }))
  },
  deletePost(postId: number) {
    return unwrap<void>(http.delete(`/api/posts/${postId}`))
  },
  profile(userId: number) {
    return unwrap<UserSummary>(http.get(`/api/users/${userId}`))
  },
  userStats(userId: number) {
    return unwrap<UserStats>(http.get(`/api/users/${userId}/stats`))
  },
  updateProfile(payload: { nickname?: string; avatarUrl?: string; backgroundUrl?: string; bio?: string }) {
    return unwrap<UserSummary>(http.put('/api/users/me', payload))
  },
  userPosts(userId: number, limit = 20) {
    return unwrap<PostView[]>(http.get(`/api/users/${userId}/posts`, { params: { limit } }))
  },
  following(userId: number) {
    return unwrap<UserSummary[]>(http.get(`/api/social/following/${userId}`))
  },
  followingPage(userId: number, page = 1, size = 20) {
    return unwrap<PageResponse<UserSummary>>(http.get(`/api/social/following/${userId}/page`, { params: { page, size } }))
  },
  followers(userId: number) {
    return unwrap<UserSummary[]>(http.get(`/api/social/followers/${userId}`))
  },
  followersPage(userId: number, page = 1, size = 20) {
    return unwrap<PageResponse<UserSummary>>(http.get(`/api/social/followers/${userId}/page`, { params: { page, size } }))
  },
  follow(userId: number, scene = 'unknown') {
    return unwrap<void>(http.post(`/api/social/follow/${userId}`, null, { params: { scene } }))
  },
  unfollow(userId: number, scene = 'unknown') {
    return unwrap<void>(http.delete(`/api/social/follow/${userId}`, { params: { scene } }))
  },
  followStatus(userId: number) {
    return unwrap<FollowStatus>(http.get(`/api/social/follow-status/${userId}`))
  },
  like(postId: number) {
    return unwrap<void>(http.post(`/api/interactions/posts/${postId}/like`))
  },
  toggleLike(postId: number) {
    return unwrap<ToggleResult>(http.post(`/api/interactions/posts/${postId}/like/toggle`))
  },
  unlike(postId: number) {
    return unwrap<void>(http.delete(`/api/interactions/posts/${postId}/like`))
  },
  favorite(postId: number) {
    return unwrap<void>(http.post(`/api/interactions/posts/${postId}/favorite`))
  },
  toggleFavorite(postId: number) {
    return unwrap<ToggleResult>(http.post(`/api/interactions/posts/${postId}/favorite/toggle`))
  },
  unfavorite(postId: number) {
    return unwrap<void>(http.delete(`/api/interactions/posts/${postId}/favorite`))
  },
  comments(postId: number) {
    return unwrap<CommentView[]>(http.get(`/api/interactions/posts/${postId}/comments`))
  },
  commentsPage(postId: number, page = 1, size = 20) {
    return unwrap<PageResponse<CommentView>>(guestHttp.get(`/api/interactions/posts/${postId}/comments/page`, { params: { page, size } }))
  },
  interactionStatus(postId: number) {
    return unwrap<PostInteractionStatus>(http.get(`/api/interactions/posts/${postId}/status`))
  },
  comment(postId: number, content: string, parentCommentId?: number) {
    return unwrap<CommentView>(http.post(`/api/interactions/posts/${postId}/comments`, { content, parentCommentId }))
  },
  deleteComment(postId: number, commentId: number) {
    return unwrap<void>(http.delete(`/api/interactions/posts/${postId}/comments/${commentId}`))
  },
  negativeFeedback(postId: number, payload: { feedbackType: string; reason?: string }) {
    return unwrap<void>(http.post(`/api/interactions/posts/${postId}/negative-feedback`, payload))
  },
  report(postId: number, reason: string) {
    return unwrap<void>(http.post(`/api/interactions/posts/${postId}/report`, { reason }))
  },
  blockUser(userId: number) {
    return unwrap<void>(http.post(`/api/interactions/posts/block-user/${userId}`))
  },
  unblockUser(userId: number) {
    return unwrap<void>(http.delete(`/api/interactions/posts/block-user/${userId}`))
  },
  search(keyword: string) {
    return unwrap<SearchResult>(http.get('/api/search', { ...slowRequestConfig, params: { keyword } }))
  },
  searchPosts(keyword: string) {
    return unwrap<PostView[]>(http.get('/api/search/posts', { ...slowRequestConfig, params: { keyword } }))
  },
  searchUsers(keyword: string) {
    return unwrap<UserSummary[]>(http.get('/api/search/users', { ...slowRequestConfig, params: { keyword } }))
  },
  searchChannels(keyword: string) {
    return unwrap<ChannelView[]>(guestHttp.get('/api/search/channels', { ...slowRequestConfig, params: { keyword } }))
  },
  searchPostsPage(keyword: string, page = 1, size = 12) {
    return unwrap<PageResponse<PostView>>(http.get('/api/search/posts/page', {
      ...slowRequestConfig,
      params: { keyword, page, size },
    }))
  },
  searchUsersPage(keyword: string, page = 1, size = 12) {
    return unwrap<PageResponse<UserSummary>>(http.get('/api/search/users/page', {
      ...slowRequestConfig,
      params: { keyword, page, size },
    }))
  },
  adminOverview() {
    return unwrap<AdminOverview>(http.get('/api/admin/overview'))
  },
  adminChannels(params?: { keyword?: string; status?: string; page?: number; size?: number }) {
    return unwrap<PageResponse<AdminChannelView>>(http.get('/api/admin/channels', { params }))
  },
  adminCreateChannel(payload: AdminChannelPayload) {
    return unwrap<AdminChannelView>(http.post('/api/admin/channels', payload))
  },
  adminUpdateChannel(code: string, payload: AdminChannelPayload) {
    return unwrap<AdminChannelView>(http.put(`/api/admin/channels/${code}`, payload))
  },
  adminUpdateChannelStatus(code: string, payload: { status?: string; enabled?: boolean }) {
    return unwrap<void>(http.patch(`/api/admin/channels/${code}/status`, payload))
  },
  adminReorderChannels(items: { code: string; sortOrder: number }[]) {
    return unwrap<void>(http.post('/api/admin/channels/reorder', { items }))
  },
  adminTopics(params?: { keyword?: string; status?: string; channelCode?: string; page?: number; size?: number }) {
    return unwrap<PageResponse<AdminTopicView>>(http.get('/api/admin/topics', { params }))
  },
  adminTopicDetail(id: number) {
    return unwrap<AdminTopicDetail>(http.get(`/api/admin/topics/${id}`))
  },
  adminCreateTopic(payload: AdminTopicPayload) {
    return unwrap<AdminTopicView>(http.post('/api/admin/topics', payload))
  },
  adminUpdateTopic(id: number, payload: AdminTopicPayload) {
    return unwrap<AdminTopicView>(http.put(`/api/admin/topics/${id}`, payload))
  },
  adminUpdateTopicStatus(id: number, payload: { status?: string }) {
    return unwrap<void>(http.patch(`/api/admin/topics/${id}/status`, payload))
  },
  adminAddTopicAlias(id: number, payload: { alias: string; source?: string }) {
    return unwrap<TopicAliasView>(http.post(`/api/admin/topics/${id}/aliases`, payload))
  },
  adminDeleteTopicAlias(aliasId: number) {
    return unwrap<void>(http.delete(`/api/admin/topic-aliases/${aliasId}`))
  },
  adminUpsertTopicBinding(id: number, payload: { channelCode: string; weight?: number; status?: string }) {
    return unwrap<TopicBindingView>(http.put(`/api/admin/topics/${id}/bindings`, payload))
  },
  adminDeleteTopicBinding(id: number, channelCode: string) {
    return unwrap<void>(http.delete(`/api/admin/topics/${id}/bindings/${channelCode}`))
  },
  adminMergeTopics(payload: { fromTopicId: number; toTopicId: number; reason?: string }) {
    return unwrap<void>(http.post('/api/admin/topics/merge', payload))
  },
  adminPosts(params?: { keyword?: string; channelCode?: string; auditStatus?: string; visibility?: string; page?: number; size?: number }) {
    return unwrap<PageResponse<AdminPostView>>(http.get('/api/admin/posts', { params }))
  },
  adminModeratePost(id: number, payload: { auditStatus?: string; visibility?: string; qualityScore?: number; safetyScore?: number }) {
    return unwrap<AdminPostView>(http.patch(`/api/admin/posts/${id}/moderation`, payload))
  },
  adminImportBatches(params?: { status?: string; page?: number; size?: number }) {
    return unwrap<PageResponse<ContentImportBatchView>>(http.get('/api/admin/import-batches', { params }))
  },
  adminCreateImportBatch(payload: { name: string; description?: string; sourceType?: string }) {
    return unwrap<ContentImportBatchView>(http.post('/api/admin/import-batches', payload))
  },
  adminUpdateImportBatchStatus(id: number, payload: { status?: string }) {
    return unwrap<ContentImportBatchView>(http.patch(`/api/admin/import-batches/${id}/status`, payload))
  },
  adminPublishImportBatch(id: number) {
    return unwrap<ContentImportBatchView>(http.post(`/api/admin/import-batches/${id}/publish`))
  },
  adminRollbackImportBatch(id: number) {
    return unwrap<ContentImportBatchView>(http.post(`/api/admin/import-batches/${id}/rollback`))
  },
  adminImportItems(batchId: number, page = 1, size = 50) {
    return unwrap<PageResponse<ContentImportItemView>>(http.get(`/api/admin/import-batches/${batchId}/items`, { params: { page, size } }))
  },
  adminCreateImportItem(batchId: number, payload: AdminImportItemPayload) {
    return unwrap<ContentImportItemView>(http.post(`/api/admin/import-batches/${batchId}/items`, payload))
  },
  adminPublishImportItem(id: number) {
    return unwrap<ContentImportItemView>(http.post(`/api/admin/import-items/${id}/publish`))
  },
  adminRebuildTasks(params?: { taskType?: string; status?: string; page?: number; size?: number }) {
    return unwrap<PageResponse<ContentRebuildTaskView>>(http.get('/api/admin/rebuild-tasks', { params }))
  },
  adminCreateRebuildTask(payload: AdminRebuildTaskPayload) {
    return unwrap<ContentRebuildTaskView>(http.post('/api/admin/rebuild-tasks', payload))
  },
  adminUpdateRebuildTaskStatus(id: number, payload: { status?: string; totalCount?: number; successCount?: number; failedCount?: number; errorMessage?: string }) {
    return unwrap<ContentRebuildTaskView>(http.patch(`/api/admin/rebuild-tasks/${id}/status`, payload))
  },
  adminFeedRequests(params?: { surface?: string; experimentId?: string; page?: number; size?: number }) {
    return unwrap<PageResponse<FeedRequestLogView>>(http.get('/api/admin/feed-requests', { params }))
  },
  adminFeedImpressions(params?: { requestId?: string; postId?: number; page?: number; size?: number }) {
    return unwrap<PageResponse<FeedImpressionLogView>>(http.get('/api/admin/feed-impressions', { params }))
  },
}
