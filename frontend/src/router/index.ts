import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue') },
    { path: '/', redirect: '/home' },
    { path: '/home', component: () => import('@/views/HomeView.vue'), meta: { requiresAuth: true } },
    { path: '/schedule', component: () => import('@/views/ScheduleView.vue'), meta: { requiresAuth: true } },
    { path: '/memo', component: () => import('@/views/MemoView.vue'), meta: { requiresAuth: true } },
    { path: '/notify', component: () => import('@/views/NotifyView.vue'), meta: { requiresAuth: true } },
    { path: '/travel', component: () => import('@/views/TravelView.vue'), meta: { requiresAuth: true } },
    { path: '/guide', component: () => import('@/views/GuideView.vue'), meta: { requiresAuth: true } },
    { path: '/chat', component: () => import('@/views/ChatView.vue'), meta: { requiresAuth: true } },
    { path: '/settings', component: () => import('@/views/SettingsView.vue'), meta: { requiresAuth: true } }
  ]
})

router.beforeEach(to => {
  if (to.meta.requiresAuth && !localStorage.getItem('token')) return '/login'
  return true
})

export default router
