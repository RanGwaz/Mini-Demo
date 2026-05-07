export interface ApiResponse<T> {
  success: boolean
  code: string
  data: T | null
  message: string
  timestamp: string
}

export type HttpMethod = 'get' | 'post'

export interface UserSummary {
  id: number
  username: string
  userNo?: string
  nickname: string
  avatarUrl?: string
  backgroundUrl?: string
  bio?: string
  phoneHash?: string
  lastLoginAt?: string
}

export interface AuthTokenResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  me: UserSummary
}

export interface SendSmsCodeRequest {
  phone: string
}

export interface PhoneSmsLoginRequest {
  phone: string
  code: string
  nickname?: string
}

export interface BindPhoneRequest {
  phone: string
  code: string
}

export interface ChangeUserNoRequest {
  userNo: string
}

export interface UploadResponse {
  objectKey: string
  fileUrl: string
  fileType: string
  thumbUrl?: string
  width?: number
  height?: number
}

export interface PostAssetView {
  id: number
  objectKey: string
  fileUrl: string
  fileType: string
  thumbUrl?: string
  width?: number
  height?: number
  sortOrder: number
}

export interface PostImageView {
  url: string
  width?: number
  height?: number
}

export interface PostView {
  id: number
  author: UserSummary
  title: string
  content?: string
  tags: string[]
  channel?: string
  channelCode?: string
  postType?: string
  topicPath?: string
  semanticTags?: string[]
  styleTags?: string[]
  assets: PostAssetView[]
  images?: PostImageView[]
  coverUrl: string
  thumbUrl?: string
  extra?: Record<string, unknown>
  likeCount: number
  favoriteCount: number
  collectCount?: number
  commentCount: number
  shareCount?: number
  viewCount: number
  recommendationReason?: string
  createdAt: string
}

export interface SearchResult {
  users: UserSummary[]
  posts: PostView[]
}

export interface CommentView {
  id: number
  author: UserSummary
  parentCommentId?: number
  replyToUser?: UserSummary
  content: string
  createdAt: string
}

export interface UserStats {
  postCount: number
  followingCount: number
  followerCount: number
}

export interface ToggleResult {
  active: boolean
}

export interface FollowStatus {
  following: boolean
}

export interface PageResponse<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface PostInteractionStatus {
  liked: boolean
  favorited: boolean
}
