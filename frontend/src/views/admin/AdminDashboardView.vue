<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>管理员首页</h1>
        <p>围绕 POI 数据质量、反馈闭环和 AI 地图链路的管理工作台。</p>
      </div>
      <el-button type="primary" @click="load">刷新数据</el-button>
    </div>

    <div class="card-grid metric-grid">
      <section class="metric-card">
        <strong>{{ stats.poiCount }}</strong>
        <span>校园 POI</span>
      </section>
      <section class="metric-card highlight">
        <strong>{{ stats.pendingFeedbackCount }}</strong>
        <span>待审核反馈</span>
      </section>
      <section class="metric-card">
        <strong>{{ stats.aiQueryCount }}</strong>
        <span>AI 查询记录</span>
      </section>
      <section class="metric-card">
        <strong>{{ stats.discoverPostCount }}</strong>
        <span>地点经验卡片</span>
      </section>
    </div>

    <div class="dashboard-grid">
      <section class="table-panel dashboard-panel">
        <div class="panel-title-row">
          <div>
            <h2>AI 意图分布</h2>
            <p>用于判断用户是否真的围绕地图查询。</p>
          </div>
        </div>
        <div class="stat-list">
          <div v-for="item in stats.aiIntentStats" :key="item.key" class="stat-row">
            <div class="stat-line">
              <strong>{{ item.label }}</strong>
              <span>{{ item.count }} 次</span>
            </div>
            <div class="stat-track">
              <span :style="{ width: barWidth(item, stats.aiIntentStats) }" />
            </div>
          </div>
          <el-empty v-if="!stats.aiIntentStats.length" description="暂无 AI 记录" :image-size="70" />
        </div>
      </section>

      <section class="table-panel dashboard-panel">
        <div class="panel-title-row">
          <div>
            <h2>地图动作分布</h2>
            <p>展示 AI 是否触发了高亮、详情和路线等地图动作。</p>
          </div>
        </div>
        <div class="action-chip-list">
          <el-tag v-for="item in stats.mapActionStats" :key="item.key" size="large" effect="plain">
            {{ item.label }} · {{ item.count }}
          </el-tag>
          <el-empty v-if="!stats.mapActionStats.length" description="暂无地图动作" :image-size="70" />
        </div>
      </section>

      <section class="table-panel dashboard-panel">
        <div class="panel-title-row">
          <div>
            <h2>反馈状态</h2>
            <p>用于检查反馈闭环是否持续推进。</p>
          </div>
        </div>
        <div class="status-grid">
          <div v-for="item in stats.feedbackStatusStats" :key="item.key" class="status-card">
            <span>{{ item.label }}</span>
            <strong>{{ item.count }}</strong>
            <small>{{ item.percent }}%</small>
          </div>
          <el-empty v-if="!stats.feedbackStatusStats.length" description="暂无反馈" :image-size="70" />
        </div>
      </section>

      <section class="table-panel dashboard-panel">
        <div class="panel-title-row">
          <div>
            <h2>热门地点</h2>
            <p>按 AI 地图动作和反馈关联度估算。</p>
          </div>
        </div>
        <el-table :data="stats.hotPois" height="245">
          <el-table-column prop="name" label="地点" min-width="150" />
          <el-table-column prop="category" label="分类" width="100" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.openStatus)" effect="plain">{{ statusLabel(row.openStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="heat" label="热度" width="90" />
        </el-table>
      </section>
    </div>
  </AppShell>
</template>

<script setup>
import { onMounted, reactive } from 'vue'
import AppShell from '../../components/AppShell.vue'
import { adminApi } from '../../api/modules'

const stats = reactive({
  poiCount: 0,
  pendingFeedbackCount: 0,
  aiQueryCount: 0,
  discoverPostCount: 0,
  aiIntentStats: [],
  mapActionStats: [],
  feedbackStatusStats: [],
  hotPois: []
})

onMounted(load)

async function load() {
  Object.assign(stats, await adminApi.dashboard())
}

function barWidth(item, items) {
  const max = Math.max(...items.map((stat) => stat.count), 1)
  return `${Math.max(10, Math.round((item.count / max) * 100))}%`
}

function statusLabel(status) {
  return {
    OPEN: '开放',
    TEMP_CLOSED: '临时关闭',
    MAINTENANCE: '维护中'
  }[status] || status || '未知'
}

function statusTag(status) {
  return {
    OPEN: 'success',
    TEMP_CLOSED: 'warning',
    MAINTENANCE: 'danger'
  }[status] || 'info'
}
</script>
