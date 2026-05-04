<template>
  <div class="shell">
    <aside class="side-nav">
      <div class="brand">
        <img class="brand-mark" :src="nuistBadge" alt="NUIST badge" />
        <span>
          <strong>SmartCampus</strong>
          <span>{{ $t('nav.subtitle') }}</span>
        </span>
      </div>

      <div class="nav-section">
        <p class="nav-section-title">{{ $t('nav.user') }}</p>
        <RouterLink class="nav-link nav-link-map" to="/map-chat"><MapLocation />{{ $t('nav.map') }}</RouterLink>
        <RouterLink class="nav-link" to="/weather"><Sunny />{{ $t('nav.weather') }}</RouterLink>
        <RouterLink class="nav-link" to="/discover"><Collection />{{ $t('nav.discover') }}</RouterLink>
        <RouterLink class="nav-link" to="/profile"><User />{{ $t('nav.profile') }}</RouterLink>
      </div>

      <div v-if="auth.isAdmin" class="nav-section">
        <p class="nav-section-title">{{ $t('nav.admin') }}</p>
        <RouterLink class="nav-link" to="/admin/dashboard"><DataAnalysis />{{ $t('nav.dashboard') }}</RouterLink>
        <RouterLink class="nav-link" to="/admin/pois"><Location />{{ $t('nav.poiManage') }}</RouterLink>
        <RouterLink class="nav-link" to="/admin/accounts"><UserFilled />{{ $t('nav.accountManage') }}</RouterLink>
        <RouterLink class="nav-link" to="/admin/feedback"><Tickets />{{ $t('nav.feedbackReview') }}</RouterLink>
        <RouterLink class="nav-link" to="/admin/discover"><Document />{{ $t('nav.discoverManage') }}</RouterLink>
        <RouterLink class="nav-link" to="/admin/ai-logs"><ChatLineRound />{{ $t('nav.aiLogs') }}</RouterLink>
      </div>

      <div class="nav-section">
        <LocaleSwitch />
      </div>

      <div class="nav-section user-account-section">
        <p class="nav-user-label">{{ $t('nav.userLabel') }}:</p>
        <p class="nav-user-name">{{ auth.user?.displayName }}</p>
        <button class="nav-link logout-link" type="button" @click="logout"><SwitchButton />{{ $t('nav.logout') }}</button>
      </div>

      <section class="peak-reminder" :class="{ 'is-peak': isPeakNow }" :aria-label="$t('nav.peakTitle')">
        <div class="peak-reminder-head">
          <Clock />
          <div>
            <p>{{ $t('nav.peakTitle') }}</p>
          </div>
        </div>
        <div class="peak-time-grid">
          <span v-for="window in peakWindows" :key="window.label" :class="{ active: window.active }">{{ window.label }}</span>
        </div>
        <p class="peak-reminder-note" :class="{ 'is-peak': isPeakNow }">
          {{ isPeakNow ? $t('nav.peakActiveTip') : $t('nav.peakOffTip') }}
        </p>
      </section>
    </aside>

    <main class="content">
      <slot />
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LocaleSwitch from './LocaleSwitch.vue'
import nuistBadge from '../assets/login/NUIST_badge.png'

const auth = useAuthStore()
const router = useRouter()
const PEAK_WINDOW_SCHEDULE = [
  { label: '07:40-08:00', start: 460, end: 480 },
  { label: '09:50-10:10', start: 590, end: 610 },
  { label: '13:25-13:45', start: 805, end: 825 },
  { label: '15:35-15:55', start: 935, end: 955 },
  { label: '18:25-18:45', start: 1105, end: 1125 }
]
const now = ref(new Date())
let peakTimer

const currentMinutes = computed(() => now.value.getHours() * 60 + now.value.getMinutes())
const isWeekday = computed(() => {
  const day = now.value.getDay()
  return day >= 1 && day <= 5
})
const peakWindows = computed(() => PEAK_WINDOW_SCHEDULE.map((window) => ({
  ...window,
  active: isWeekday.value && currentMinutes.value >= window.start && currentMinutes.value < window.end
})))
const isPeakNow = computed(() => peakWindows.value.some((window) => window.active))

onMounted(() => {
  peakTimer = window.setInterval(() => {
    now.value = new Date()
  }, 30000)
})

onBeforeUnmount(() => {
  if (peakTimer) window.clearInterval(peakTimer)
})

function logout() {
  auth.logout()
  router.push('/login')
}
</script>
