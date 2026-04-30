<template>
  <div class="auth-page">
    <section class="auth-hero">
      <h1>
        <span>SmartCampus</span>
        <span>Navigation</span>
      </h1>
      <p>围绕 AI 地图交互、自建校园 POI 数据和反馈修正闭环构建的智慧校园导航第一版演示系统。</p>
    </section>
    <section class="auth-panel">
      <el-card class="auth-card" shadow="never">
        <template #header>
          <div>
            <strong>{{ mode === 'login' ? '登录系统' : '注册用户' }}</strong>
            <p style="margin: 6px 0 0; color: var(--scn-muted)">默认用户 student / 123456，管理员 admin / 123456</p>
          </div>
        </template>
        <el-form label-position="top" :model="form" @submit.prevent>
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item v-if="mode === 'register'" label="显示名称">
            <el-input v-model="form.displayName" placeholder="请输入显示名称" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-button type="primary" style="width: 100%" :loading="loading" @click="submit">
            {{ mode === 'login' ? '登录' : '注册并登录' }}
          </el-button>
          <el-button link style="width: 100%; margin-top: 12px" @click="toggle">
            {{ mode === 'login' ? '没有账号？注册' : '已有账号？登录' }}
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
