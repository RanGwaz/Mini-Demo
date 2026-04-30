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
  topic?: string
  style?: string
}

export type FeedRequestAuthMode = 'session' | 'guest'

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
    return unwrap<PageResponse<PostView>>(client.get('/api/feed/home', {
      ...slowRequestConfig,
      params: {
        page,
        size,
        ...(seed ? { seed } : {}),
        ...(filters?.topic ? { topic: filters.topic } : {}),
        ...(filters?.style ? { style: filters.style } : {}),
      },
    }))
  },
  similarPosts(postId: number, page = 1, size = 24, filters?: FeedQueryFilters, authMode: FeedRequestAuthMode = 'session') {
    const client = authMode === 'guest' ? guestHttp : http
    return unwrap<PageResponse<PostView>>(client.get(`/api/feed/posts/${postId}/similar`, {
      ...slowRequestConfig,
      params: {
        page,
        size,
        ...(filters?.topic ? { topic: filters.topic } : {}),
        ...(filters?.style ? { style: filters.style } : {}),
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
    tags: string[]
    assets: CreatePostAssetPayload[]
  }) {
    return unwrap<PostView>(http.post('/api/posts', payload))
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
}
