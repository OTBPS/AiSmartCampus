import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import MapChatView from '../views/user/MapChatView.vue'
import DiscoverView from '../views/user/DiscoverView.vue'
import DiscoverDetailView from '../views/user/DiscoverDetailView.vue'
import WeatherView from '../views/user/WeatherView.vue'
import ProfileView from '../views/user/ProfileView.vue'
import AdminDashboardView from '../views/admin/AdminDashboardView.vue'
import AdminPoisView from '../views/admin/AdminPoisView.vue'
import AdminFeedbackView from '../views/admin/AdminFeedbackView.vue'
import AdminDiscoverView from '../views/admin/AdminDiscoverView.vue'
import AdminAiLogsView from '../views/admin/AdminAiLogsView.vue'
import AdminAccountsView from '../views/admin/AdminAccountsView.vue'

const routes = [
  { path: '/', redirect: '/map-chat' },
  { path: '/login', component: LoginView, meta: { public: true } },
  { path: '/map-chat', component: MapChatView },
  { path: '/weather', component: WeatherView },
  { path: '/discover', component: DiscoverView },
  { path: '/discover/:id', component: DiscoverDetailView },
  { path: '/profile', component: ProfileView },
  { path: '/admin', redirect: '/admin/dashboard', meta: { admin: true } },
  { path: '/admin/dashboard', component: AdminDashboardView, meta: { admin: true } },
  { path: '/admin/pois', component: AdminPoisView, meta: { admin: true } },
  { path: '/admin/accounts', component: AdminAccountsView, meta: { admin: true } },
  { path: '/admin/feedback', component: AdminFeedbackView, meta: { admin: true } },
  { path: '/admin/discover', component: AdminDiscoverView, meta: { admin: true } },
  { path: '/admin/ai-logs', component: AdminAiLogsView, meta: { admin: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLoggedIn) {
    return '/login'
  }
  if (to.meta.admin && !auth.isAdmin) {
    return '/map-chat'
  }
  return true
})

export default router
