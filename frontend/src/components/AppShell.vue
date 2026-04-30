<template>
  <div class="shell">
    <aside class="side-nav">
      <div class="brand">
        <span class="brand-mark">SC</span>
        <span>
          <strong>SmartCampus</strong>
          <span>AI 地图导航</span>
        </span>
      </div>

      <div class="nav-section">
        <p class="nav-section-title">用户端</p>
        <RouterLink class="nav-link" to="/map-chat"><MapLocation />AI 地图</RouterLink>
        <RouterLink class="nav-link" to="/discover"><Collection />发现</RouterLink>
        <RouterLink class="nav-link" to="/profile"><User />个人中心</RouterLink>
      </div>

      <div v-if="auth.isAdmin" class="nav-section">
        <p class="nav-section-title">管理员</p>
        <RouterLink class="nav-link" to="/admin/dashboard"><DataAnalysis />首页</RouterLink>
        <RouterLink class="nav-link" to="/admin/pois"><Location />POI 管理</RouterLink>
        <RouterLink class="nav-link" to="/admin/feedback"><Tickets />反馈审核</RouterLink>
        <RouterLink class="nav-link" to="/admin/discover"><Document />发现管理</RouterLink>
        <RouterLink class="nav-link" to="/admin/ai-logs"><ChatLineRound />AI 记录</RouterLink>
      </div>

      <div class="nav-section">
        <p class="nav-section-title">{{ auth.user?.displayName }}</p>
        <button class="nav-link" type="button" @click="logout"><SwitchButton />退出登录</button>
      </div>
    </aside>

    <main class="content">
      <slot />
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

