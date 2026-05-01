<template>
  <div class="shell">
    <aside class="side-nav">
      <div class="brand">
        <span class="brand-mark">SC</span>
        <span>
          <strong>SmartCampus</strong>
          <span>{{ $t('nav.subtitle') }}</span>
        </span>
      </div>

      <div class="nav-section">
        <p class="nav-section-title">{{ $t('nav.user') }}</p>
        <RouterLink class="nav-link" to="/map-chat"><MapLocation />{{ $t('nav.map') }}</RouterLink>
        <RouterLink class="nav-link" to="/discover"><Collection />{{ $t('nav.discover') }}</RouterLink>
        <RouterLink class="nav-link" to="/profile"><User />{{ $t('nav.profile') }}</RouterLink>
      </div>

      <div v-if="auth.isAdmin" class="nav-section">
        <p class="nav-section-title">{{ $t('nav.admin') }}</p>
        <RouterLink class="nav-link" to="/admin/dashboard"><DataAnalysis />{{ $t('nav.dashboard') }}</RouterLink>
        <RouterLink class="nav-link" to="/admin/pois"><Location />{{ $t('nav.poiManage') }}</RouterLink>
        <RouterLink class="nav-link" to="/admin/feedback"><Tickets />{{ $t('nav.feedbackReview') }}</RouterLink>
        <RouterLink class="nav-link" to="/admin/discover"><Document />{{ $t('nav.discoverManage') }}</RouterLink>
        <RouterLink class="nav-link" to="/admin/ai-logs"><ChatLineRound />{{ $t('nav.aiLogs') }}</RouterLink>
      </div>

      <div class="nav-section">
        <LocaleSwitch />
      </div>

      <div class="nav-section">
        <p class="nav-section-title">{{ auth.user?.displayName }}</p>
        <button class="nav-link" type="button" @click="logout"><SwitchButton />{{ $t('nav.logout') }}</button>
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
import LocaleSwitch from './LocaleSwitch.vue'

const auth = useAuthStore()
const router = useRouter()

function logout() {
  auth.logout()
  router.push('/login')
}
</script>
