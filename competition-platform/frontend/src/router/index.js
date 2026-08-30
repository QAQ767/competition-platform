import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/', component: () => import('../views/Home.vue') },
  { path: '/teams', component: () => import('../views/TeamSquare.vue') },
  { path: '/teams/:id', component: () => import('../views/TeamDetail.vue') },
  { path: '/stats', component: () => import('../views/Stats.vue') },
  { path: '/achievements', component: () => import('../views/Achievements.vue') },
  { path: '/users/:id', component: () => import('../views/UserHome.vue') },
  { path: '/chat', component: () => import('../views/Chat.vue'), meta: { requiresAuth: true } },
  { path: '/feedback', component: () => import('../views/Feedback.vue'), meta: { requiresAuth: true } },
  { path: '/me', component: () => import('../views/Me.vue'), meta: { requiresAuth: true } },
  { path: '/admin', component: () => import('../views/Admin.vue'), meta: { requiresAuth: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    return '/login'
  }
  if (to.path === '/login' && token) {
    return '/'
  }
  return true
})

export default router
