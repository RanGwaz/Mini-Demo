import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '../stores/auth'
import type { ApiResponse } from '../types'

function resolveTimeout(value: unknown, fallback: number) {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''
export const REQUEST_TIMEOUT_MS = resolveTimeout(import.meta.env.VITE_API_TIMEOUT_MS, 30000)
export const LONG_REQUEST_TIMEOUT_MS = resolveTimeout(import.meta.env.VITE_API_LONG_TIMEOUT_MS, 60000)

type ErrorPayload = {
  code?: string
  message?: string
}

function applyAuthorizationHeader(config: InternalAxiosRequestConfig, token: string) {
  const headers = config.headers as InternalAxiosRequestConfig['headers'] & {
    Authorization?: string
    set?: (name: string, value: string) => void
  }

  if (typeof headers?.set === 'function') {
    headers.set('Authorization', `Bearer ${token}`)
    return
  }

  if (headers) {
    headers.Authorization = `Bearer ${token}`
  } else {
    config.headers = { Authorization: `Bearer ${token}` } as InternalAxiosRequestConfig['headers']
  }
}

export class HttpError extends Error {
  code?: string
  status?: number

  constructor(message: string, options?: { code?: string; status?: number }) {
    super(message)
    this.name = 'HttpError'
    this.code = options?.code
    this.status = options?.status
  }
}

const SUCCESS_CODE = '0'

function createHttpClient(options: { attachAuth: boolean }) {
  const client = axios.create({
    baseURL: API_BASE_URL,
    timeout: REQUEST_TIMEOUT_MS,
  })

  if (options.attachAuth) {
    client.interceptors.request.use((config) => {
      const authStore = useAuthStore()
      authStore.hydrate()
      if (authStore.accessToken) {
        applyAuthorizationHeader(config, authStore.accessToken)
      }
      return config
    })
  }

  client.interceptors.response.use(
    (response) => {
      const payload = response.data as ApiResponse<unknown> | undefined
      if (payload && typeof payload === 'object' && 'success' in payload && 'code' in payload) {
        if (payload.code !== SUCCESS_CODE || payload.success === false) {
          return Promise.reject(
            new HttpError(payload.message || '请求失败', {
              code: payload.code,
              status: response.status,
            }),
          )
        }
      }
      return response
    },
    (error: AxiosError<ErrorPayload>) => {
      const authStore = useAuthStore()
      authStore.hydrate()

      const status = error.response?.status as number | undefined
      const code = error.response?.data?.code as string | undefined
      const rawMessage = String(error.response?.data?.message ?? error.message ?? '请求失败')
      const isTimeoutError = error.code === 'ECONNABORTED' || rawMessage.toLowerCase().includes('timeout')

      if (isTimeoutError) {
        return Promise.reject(
          new HttpError('请求超时，请检查后端 8080 和推荐服务 18080 是否已启动，或稍后重试', {
            code: code ?? 'HTTP_TIMEOUT',
            status,
          }),
        )
      }

      if (options.attachAuth && (status === 401 || code === 'A002')) {
        const hadSession = Boolean(authStore.accessToken)
        authStore.clearSession()
        if (hadSession) {
          const rawRedirect = window.location.pathname + window.location.search
          authStore.setPendingRedirect(rawRedirect)
          authStore.openAuthPrompt('manual')
        }
        return Promise.reject(
          new HttpError(hadSession ? '登录已过期，请重新登录' : '需要登录', { code: code ?? 'A002', status }),
        )
      }

      if (status === 403 || code === 'A003') {
        return Promise.reject(new HttpError('没有权限执行该操作', { code: code ?? 'A003', status }))
      }

      return Promise.reject(new HttpError(rawMessage || '请求失败', { code, status }))
    },
  )

  return client
}

const http = createHttpClient({ attachAuth: true })
export const guestHttp = createHttpClient({ attachAuth: false })

export async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const response = await promise
  if (response.data.data === null || response.data.data === undefined) {
    return undefined as T
  }
  return response.data.data
}

export type HttpMethod = 'get' | 'post'

export default http
