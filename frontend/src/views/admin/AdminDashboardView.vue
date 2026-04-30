<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>管理员首页</h1>
        <p>只展示与 POI 数据质量、反馈闭环和 AI 地图链路相关的指标。</p>
      </div>
    </div>
    <div class="card-grid">
      <section class="metric-card"><strong>{{ stats.poiCount }}</strong><span>校园 POI</span></section>
      <section class="metric-card"><strong>{{ stats.pendingFeedbackCount }}</strong><span>待审核反馈</span></section>
      <section class="metric-card"><strong>{{ stats.aiQueryCount }}</strong><span>AI 查询记录</span></section>
      <section class="metric-card"><strong>{{ stats.discoverPostCount }}</strong><span>地点经验卡片</span></section>
    </div>
  </AppShell>
</template>

<script setup>
import { onMounted, reactive } from 'vue'
import AppShell from '../../components/AppShell.vue'
import { adminApi } from '../../api/modules'

const stats = reactive({ poiCount: 0, pendingFeedbackCount: 0, aiQueryCount: 0, discoverPostCount: 0 })
onMounted(async () => Object.assign(stats, await adminApi.dashboard()))
</script>

