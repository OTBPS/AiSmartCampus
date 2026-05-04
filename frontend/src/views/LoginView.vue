<template>
  <div class="auth-page">
    <section class="auth-hero">
      <span class="auth-school-badge-frame">
        <img class="auth-school-badge" :src="nuistBadge" alt="NUIST badge" />
      </span>
      <div class="auth-brand">
        <span class="auth-brand-kicker">NUIST</span>
        <h1>
          <span>SmartCampus</span>
          <span>Navigation</span>
        </h1>
      </div>
    </section>
    <section class="auth-panel">
      <div class="auth-locale">
        <LocaleSwitch compact />
      </div>
      <el-card class="auth-card" shadow="never">
        <template #header>
          <div>
            <strong>{{ mode === 'login' ? $t('auth.loginTitle') : $t('auth.registerTitle') }}</strong>
          </div>
        </template>
        <el-form ref="authFormRef" label-position="top" :model="form" :rules="rules" @submit.prevent>
          <el-form-item :label="$t('auth.username')" prop="username">
            <el-input v-model="form.username" :placeholder="$t('auth.usernamePlaceholder')" />
          </el-form-item>
          <el-form-item :label="$t('auth.password')" prop="password">
            <el-input v-model="form.password" type="password" show-password :placeholder="$t('auth.passwordPlaceholder')" />
          </el-form-item>
          <el-button type="primary" style="width: 100%" :loading="loading" @click="submit">
            {{ mode === 'login' ? $t('auth.login') : $t('auth.registerAndLogin') }}
          </el-button>
          <el-button link style="width: 100%; margin-top: 12px" @click="toggle">
            {{ mode === 'login' ? $t('auth.goRegister') : $t('auth.goLogin') }}
          </el-button>
        </el-form>
      </el-card>
    </section>
  </div>
</template>

<script setup>
import { nextTick, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { authApi } from '../api/modules'
import { useAuthStore } from '../stores/auth'
import LocaleSwitch from '../components/LocaleSwitch.vue'
import nuistBadge from '../assets/login/NUIST_badge2.png'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const { t } = useI18n()
const mode = ref('login')
const loading = ref(false)
const authFormRef = ref()
const usernamePattern = /^[A-Za-z_]{1,15}$/
const passwordPattern = /^[A-Za-z0-9]{6,20}$/
const form = reactive({
  username: 'student',
  password: '123456'
})
const rules = {
  username: [{ validator: validateUsername, trigger: 'blur' }],
  password: [{ validator: validatePassword, trigger: 'blur' }]
}

function toggle() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  form.username = mode.value === 'login' ? 'student' : ''
  form.password = mode.value === 'login' ? '123456' : ''
  nextTick(() => authFormRef.value?.clearValidate())
}

function validateUsername(_rule, value, callback) {
  if (!usernamePattern.test(value || '')) {
    callback(new Error(t('auth.usernameRule')))
    return
  }
  callback()
}

function validatePassword(_rule, value, callback) {
  if (!passwordPattern.test(value || '')) {
    callback(new Error(t('auth.passwordRule')))
    return
  }
  callback()
}

async function submit() {
  const valid = await authFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const data = mode.value === 'login'
      ? await authApi.login({ username: form.username, password: form.password })
      : await authApi.register({ username: form.username, password: form.password })
    auth.setSession(data)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const fallback = data.role === 'ADMIN' ? '/admin/dashboard' : '/map-chat'
    router.push(isSafeRedirect(redirect) ? redirect : fallback)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function isSafeRedirect(value) {
  return value.startsWith('/') && !value.startsWith('//')
}
</script>
