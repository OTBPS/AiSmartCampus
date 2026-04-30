<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>个人中心</h1>
        <p>第一版只保留用户信息和反馈记录，避免偏离 AI 地图主线。</p>
      </div>
    </div>

    <div class="card-grid" style="grid-template-columns: 1fr 2fr">
      <section class="metric-card">
        <strong>{{ auth.user?.displayName }}</strong>
        <span>{{ auth.user?.username }} · {{ auth.user?.role }}</span>
      </section>
      <section class="table-panel">
        <h3>我的反馈</h3>
        <el-table :data="feedback" height="420">
          <el-table-column prop="type" label="类型" width="140" />
          <el-table-column prop="content" label="内容" />
          <el-table-column prop="status" label="状态" width="120" />
        </el-table>
      </section>
    </div>
  </AppShell>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AppShell from '../../components/AppShell.vue'
import { feedbackApi } from '../../api/modules'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const feedback = ref([])
onMounted(async () => {
  feedback.value = await feedbackApi.mine()
})
</script>

