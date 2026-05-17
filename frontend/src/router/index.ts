import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const FeedView = () => import('../views/FeedView.vue')
const LoginView = () => import('../views/LoginView.vue')
const PostDetailView = () => import('../views/PostDetailView.vue')
const ProfileView = () => import('../views/ProfileView.vue')
const PublishView = () => import('../views/PublishView.vue')
const SearchDiscoverView = () => import('../views/SearchDiscoverView.vue')
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
  if (to.name === 'login') {
    return authStore.accessToken ? { name: 'home' } : { name: 'home', query: { auth: '1' } }
  }
  return true
})

export default router
