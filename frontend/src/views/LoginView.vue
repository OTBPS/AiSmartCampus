<template>
  <div class="auth-page">
    <section class="auth-hero">
      <h1>
        <span>SmartCampus</span>
        <span>Navigation</span>
      </h1>
      <p>{{ $t('auth.intro') }}</p>
    </section>
    <section class="auth-panel">
      <div class="auth-locale">
        <LocaleSwitch compact />
      </div>
      <el-card class="auth-card" shadow="never">
        <template #header>
          <div>
            <strong>{{ mode === 'login' ? $t('auth.loginTitle') : $t('auth.registerTitle') }}</strong>
            <p style="margin: 6px 0 0; color: var(--scn-muted)">{{ $t('auth.defaultAccount') }}</p>
          </div>
        </template>
        <el-form label-position="top" :model="form" @submit.prevent>
          <el-form-item :label="$t('auth.username')">
            <el-input v-model="form.username" :placeholder="$t('auth.usernamePlaceholder')" />
          </el-form-item>
          <el-form-item v-if="mode === 'register'" :label="$t('auth.displayName')">
            <el-input v-model="form.displayName" :placeholder="$t('auth.displayNamePlaceholder')" />
          </el-form-item>
          <el-form-item :label="$t('auth.password')">
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
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api/modules'
import { useAuthStore } from '../stores/auth'
import LocaleSwitch from '../components/LocaleSwitch.vue'

const router = useRouter()
const auth = useAuthStore()
const mode = ref('login')
const loading = ref(false)
const form = reactive({
  username: 'student',
  displayName: '',
  password: '123456'
})

function toggle() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  form.username = mode.value === 'login' ? 'student' : ''
  form.password = mode.value === 'login' ? '123456' : ''
}

async function submit() {
  loading.value = true
  try {
    const data = mode.value === 'login'
      ? await authApi.login({ username: form.username, password: form.password })
      : await authApi.register({ username: form.username, password: form.password, displayName: form.displayName || form.username })
    auth.setSession(data)
    router.push(data.role === 'ADMIN' ? '/admin/dashboard' : '/map-chat')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}
</script>
