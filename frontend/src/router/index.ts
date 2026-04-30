import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const FeedView = () => import('../views/FeedView.vue')
const LoginView = () => import('../views/LoginView.vue')
const LiveRoomView = () => import('../views/LiveRoomView.vue')
const LiveView = () => import('../views/LiveView.vue')
const PostDetailView = () => import('../views/PostDetailView.vue')
const ProfileView = () => import('../views/ProfileView.vue')
const PublishView = () => import('../views/PublishView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/feed',
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
      path: '/live',
      name: 'live',
      component: LiveView,
    },
    {
      path: '/live/:id',
      name: 'live-room',
      component: LiveRoomView,
    },
    {
      path: '/publish',
      name: 'publish',
      component: PublishView,
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

router.beforeEach((to) => {
  const authStore = useAuthStore()
  authStore.hydrate()
  if (to.meta.requiresAuth && !authStore.accessToken) {
    authStore.setPendingRedirect(to.fullPath)
    authStore.openAuthPrompt('manual')
    return false
  }
  if (to.name === 'login') {
    return authStore.accessToken ? { name: 'feed' } : { name: 'feed', query: { auth: '1' } }
  }
  return true
})

export default router
