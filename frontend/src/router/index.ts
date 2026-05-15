import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const FeedView = () => import('../views/FeedView.vue')
const LoginView = () => import('../views/LoginView.vue')
const PostDetailView = () => import('../views/PostDetailView.vue')
const ProfileView = () => import('../views/ProfileView.vue')
const PublishView = () => import('../views/PublishView.vue')
const ChannelView = () => import('../views/ChannelView.vue')
const SearchDiscoverView = () => import('../views/SearchDiscoverView.vue')
const AdminView = () => import('../views/AdminView.vue')
const MessagesView = () => import('../views/MessagesView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/home',
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { hideShell: true },
    },
    {
      path: '/feed',
      name: 'feed',
      component: FeedView,
    },
    {
      path: '/home',
      name: 'home',
      component: FeedView,
    },
    {
      path: '/publish',
      name: 'publish',
      component: PublishView,
      meta: { requiresAuth: true },
    },
    {
      path: '/channels/:code',
      name: 'channel',
      component: ChannelView,
    },
    {
      path: '/search',
      name: 'search',
      component: SearchDiscoverView,
    },
    {
      path: '/messages',
      name: 'messages',
      component: MessagesView,
      meta: { requiresAuth: true },
    },
    {
      path: '/notifications',
      name: 'notifications',
      redirect: { name: 'messages', query: { tab: 'notifications' } },
      meta: { requiresAuth: true },
    },
    {
      path: '/profile',
      name: 'profile',
      component: ProfileView,
      meta: { requiresAuth: true },
    },
    {
      path: '/users/:id',
      name: 'user-profile',
      component: ProfileView,
    },
    {
      path: '/posts/:id',
      name: 'post-detail',
      component: PostDetailView,
    },
    {
      path: '/admin',
      name: 'admin',
      component: AdminView,
      meta: { requiresAuth: true, requiresAdmin: true },
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  authStore.hydrate()
  if (to.meta.requiresAuth && !authStore.accessToken) {
    authStore.setPendingRedirect(to.fullPath)
    authStore.openAuthPrompt('manual')
    return false
  }
  if (to.meta.requiresAdmin) {
    let roles = authStore.currentUser?.roles ?? ''
    if (!roles && authStore.accessToken) {
      try {
        const { api } = await import('../services/api')
        const session = await api.me()
        authStore.setSession(session)
        roles = session.me.roles ?? ''
      } catch {
        roles = ''
      }
    }
    if (!roles.split(',').map((role) => role.trim()).includes('ROLE_ADMIN')) {
      ElMessage.warning('需要管理员权限')
      return { name: 'home' }
    }
  }
  if (to.name === 'login') {
    return authStore.accessToken ? { name: 'home' } : { name: 'home', query: { auth: '1' } }
  }
  return true
})

export default router
