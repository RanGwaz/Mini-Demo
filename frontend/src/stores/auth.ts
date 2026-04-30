import { defineStore } from 'pinia'
import type { AuthTokenResponse, UserSummary } from '../types'

const ACCESS_TOKEN_KEY = 'image-social-access-token'
const CURRENT_USER_KEY = 'image-social-current-user'

export const LOGIN_WALL_DISMISSED_KEY = 'image-social-login-wall-dismissed'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: '' as string,
    currentUser: null as UserSummary | null,
    authModalOpen: false,
    authModalSource: 'manual' as 'welcome' | 'manual',
    pendingRedirect: null as string | null,
  }),
  actions: {
    hydrate() {
      if (!this.accessToken) {
        this.accessToken = localStorage.getItem(ACCESS_TOKEN_KEY) ?? ''
      }
      if (!this.currentUser) {
        const raw = localStorage.getItem(CURRENT_USER_KEY)
        this.currentUser = raw ? (JSON.parse(raw) as UserSummary) : null
      }
    },
    setSession(payload: AuthTokenResponse) {
      this.accessToken = payload.accessToken
      this.currentUser = payload.me
      localStorage.setItem(ACCESS_TOKEN_KEY, payload.accessToken)
      localStorage.setItem(CURRENT_USER_KEY, JSON.stringify(payload.me))
    },
    clearSession() {
      this.accessToken = ''
      this.currentUser = null
      localStorage.removeItem(ACCESS_TOKEN_KEY)
      localStorage.removeItem(CURRENT_USER_KEY)
      localStorage.removeItem(LOGIN_WALL_DISMISSED_KEY)
    },
    async logout(options?: { notifyServer?: boolean }) {
      const notifyServer = options?.notifyServer !== false
      this.clearSession()
      if (!notifyServer) return
      try {
        const { api } = await import('../services/api')
        await api.logout()
      } catch {
        // Ignore logout request failures after the local session is cleared.
      }
    },
    openAuthPrompt(source: 'welcome' | 'manual' = 'manual') {
      this.authModalSource = source
      this.authModalOpen = true
    },
    closeAuthModal() {
      this.authModalOpen = false
    },
    setPendingRedirect(path: string) {
      this.pendingRedirect = path
    },
    clearPendingRedirect() {
      this.pendingRedirect = null
    },
    afterAuthModalClosed(success: boolean) {
      if (!success && this.authModalSource === 'welcome') {
        localStorage.setItem(LOGIN_WALL_DISMISSED_KEY, '1')
      }
    },
  },
})
