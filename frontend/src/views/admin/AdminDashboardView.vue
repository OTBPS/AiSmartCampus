<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>{{ $t('admin.dashboardTitle') }}</h1>
      </div>
      <el-button type="primary" @click="load">{{ $t('common.refreshData') }}</el-button>
    </div>

    <div class="card-grid metric-grid">
      <section class="metric-card">
        <strong>{{ stats.poiCount }}</strong>
        <span>{{ $t('admin.campusPoi') }}</span>
      </section>
      <section class="metric-card highlight">
        <strong>{{ stats.pendingFeedbackCount }}</strong>
        <span>{{ $t('admin.pendingFeedback') }}</span>
      </section>
      <section class="metric-card">
        <strong>{{ stats.aiQueryCount }}</strong>
        <span>{{ $t('admin.aiQueryRecords') }}</span>
      </section>
      <section class="metric-card">
        <strong>{{ stats.discoverPostCount }}</strong>
        <span>{{ $t('admin.discoverCards') }}</span>
      </section>
    </div>

    <div class="dashboard-grid">
      <section class="table-panel dashboard-panel">
        <div class="panel-title-row">
          <div>
            <h2>{{ $t('admin.intentDistribution') }}</h2>
          </div>
        </div>
        <div class="stat-list">
          <div v-for="item in stats.aiIntentStats" :key="item.key" class="stat-row">
            <div class="stat-line">
              <strong>{{ intentLabel(item.key) }}</strong>
              <span>{{ $t('admin.countTimes', { count: item.count }) }}</span>
            </div>
            <div class="stat-track">
              <span :style="{ width: barWidth(item, stats.aiIntentStats) }" />
            </div>
          </div>
          <el-empty v-if="!stats.aiIntentStats.length" :description="$t('admin.noAiRecords')" :image-size="70" />
        </div>
      </section>

      <section class="table-panel dashboard-panel">
        <div class="panel-title-row">
          <div>
            <h2>{{ $t('admin.mapActionDistribution') }}</h2>
          </div>
        </div>
        <div v-if="stats.mapActionStats.length" class="map-action-chart" role="list" :aria-label="$t('admin.mapActionDistribution')">
          <div class="map-action-plot">
            <div v-for="item in stats.mapActionStats" :key="item.key" class="map-action-bar-item" role="listitem">
              <div class="map-action-bar-stack">
                <strong class="map-action-value">{{ item.count }}</strong>
                <span class="map-action-bar-well">
                  <span class="map-action-bar" :style="{ height: actionBarHeight(item) }" aria-hidden="true" />
                </span>
              </div>
              <span class="map-action-label">{{ actionLabel(item.key) }}</span>
            </div>
          </div>
        </div>
        <div v-else class="action-empty-state">
          <el-empty :description="$t('admin.noMapActions')" :image-size="70" />
        </div>
      </section>

      <section class="table-panel dashboard-panel">
        <div class="panel-title-row">
          <div>
            <h2>{{ $t('admin.feedbackStatus') }}</h2>
          </div>
        </div>
        <div class="status-grid">
          <div v-for="item in stats.feedbackStatusStats" :key="item.key" class="status-card">
            <span>{{ feedbackStatusLabel(item.key) }}</span>
            <strong>{{ item.count }}</strong>
            <small>{{ item.percent }}%</small>
          </div>
          <el-empty v-if="!stats.feedbackStatusStats.length" :description="$t('admin.noFeedback')" :image-size="70" />
        </div>
      </section>

      <section class="table-panel dashboard-panel">
        <div class="panel-title-row">
          <div>
            <h2>{{ $t('admin.hotPois') }}</h2>
          </div>
        </div>
        <el-table :data="stats.hotPois" height="245">
          <el-table-column prop="name" :label="$t('admin.poiName')" min-width="150" />
          <el-table-column prop="category" :label="$t('common.category')" width="100" />
          <el-table-column :label="$t('common.status')" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.openStatus)" effect="plain">{{ statusLabel(row.openStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="heat" :label="$t('admin.heat')" width="90" />
        </el-table>
      </section>
    </div>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import AppShell from '../../components/AppShell.vue'
import { adminApi } from '../../api/modules'

const { t } = useI18n()
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

const mapActionMax = computed(() => Math.max(...stats.mapActionStats.map((item) => item.count), 1))
const mapActionAxisMax = computed(() => niceAxisMax(mapActionMax.value))

async function load() {
  Object.assign(stats, await adminApi.dashboard())
}

function barWidth(item, items) {
  const max = Math.max(...items.map((stat) => stat.count), 1)
  return `${Math.max(10, Math.round((item.count / max) * 100))}%`
}

function actionBarHeight(item) {
  return `${Math.max(8, Math.round((item.count / mapActionAxisMax.value) * 100))}%`
}

function niceAxisMax(value) {
  const raw = Math.max(Number(value) || 0, 1)
  const step = raw <= 10 ? 2 : raw <= 50 ? 10 : raw <= 100 ? 20 : Math.ceil(raw / 50) * 10
  return Math.ceil(raw / step) * step
}

function statusLabel(status) {
  return {
    OPEN: t('common.open'),
    TEMP_CLOSED: t('common.tempClosed'),
    MAINTENANCE: t('common.maintenance')
  }[status] || status || t('common.unknown')
}

function statusTag(status) {
  return {
    OPEN: 'success',
    TEMP_CLOSED: 'warning',
    MAINTENANCE: 'danger'
  }[status] || 'info'
}

function intentLabel(intent) {
  return t(`labels.intent.${intent}`, intent)
}

function actionLabel(action) {
  return t(`labels.action.${action}`, action)
}

function feedbackStatusLabel(status) {
  return {
    PENDING: t('common.pending'),
    APPROVED: t('common.approved'),
    REJECTED: t('common.rejected')
  }[status] || status
}
</script>
