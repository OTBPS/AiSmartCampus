<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>AI 交互记录</h1>
        <p>用于展示用户问题、识别意图和地图动作，不扩展成复杂日志平台。</p>
      </div>
      <el-button type="primary" @click="load">刷新</el-button>
    </div>

    <section class="table-panel">
      <div class="admin-filter-bar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索用户问题、回复或地图动作"
          style="max-width: 340px"
        />
        <el-select v-model="intentFilter" clearable placeholder="意图筛选" style="width: 180px">
          <el-option v-for="item in intentOptions" :key="item" :label="intentLabel(item)" :value="item" />
        </el-select>
        <span class="filter-count">当前 {{ filteredLogs.length }} / {{ logs.length }} 条</span>
      </div>

      <el-table :data="filteredLogs" height="620">
        <el-table-column label="用户问题" min-width="230">
          <template #default="{ row }">
            <div class="log-question">{{ row.question }}</div>
          </template>
        </el-table-column>
        <el-table-column label="识别意图" width="135">
          <template #default="{ row }">
            <el-tag effect="plain">{{ intentLabel(row.intent) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="工具调用" min-width="210">
          <template #default="{ row }">
            <div class="action-chip-list compact">
              <el-tag v-for="item in toolLabels(row)" :key="item.key" type="info" effect="plain">{{ item.label }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="地图动作" min-width="250">
          <template #default="{ row }">
            <div class="action-chip-list compact">
              <el-tag
                v-for="item in actionLabels(row)"
                :key="item.key"
                :type="actionTagType(item.type)"
                effect="plain"
              >
                {{ item.label }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="AI 回复" min-width="300">
          <template #default="{ row }">
            <div class="log-reply">{{ row.reply }}</div>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppShell from '../../components/AppShell.vue'
import { aiApi } from '../../api/modules'

const logs = ref([])
const keyword = ref('')
const intentFilter = ref('')
onMounted(load)

async function load() {
  logs.value = await aiApi.logs()
}

const intentOptions = computed(() => Array.from(new Set(logs.value.map((item) => item.intent).filter(Boolean))))

const filteredLogs = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  return logs.value.filter((item) => {
    const matchedIntent = !intentFilter.value || item.intent === intentFilter.value
    const text = `${item.question || ''} ${item.reply || ''} ${item.toolCallsJson || ''} ${item.mapActionsJson || ''}`.toLowerCase()
    return matchedIntent && (!key || text.includes(key))
  })
})

function safeArray(value) {
  try {
    const parsed = JSON.parse(value || '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch (error) {
    return []
  }
}

function toolLabels(row) {
  return safeArray(row.toolCallsJson).map((item, index) => ({
    key: `${item.name || item.tool || 'tool'}-${index}`,
    label: toolLabel(item.name || item.tool || 'tool')
  }))
}

function actionLabels(row) {
  return safeArray(row.mapActionsJson).map((item, index) => ({
    key: `${item.type || 'unknown'}-${index}`,
    type: item.type || 'unknown',
    label: actionLabel(item)
  }))
}

function intentLabel(intent) {
  return {
    find_poi: '找地点',
    recommend_place: '条件推荐',
    route_help: '路线帮助'
  }[intent] || '未识别'
}

function toolLabel(tool) {
  return {
    searchPoi: 'POI 检索',
    searchPoiByTags: '标签推荐',
    planCampusRouteFallback: '路线兜底'
  }[tool] || tool
}

function actionLabel(action) {
  const typeLabel = {
    highlight_pois: '高亮地点',
    open_poi_detail: '打开详情',
    draw_route: '绘制路线'
  }[action.type] || action.type || '未知动作'
  if (action.type === 'draw_route' && action.payload?.from && action.payload?.to) {
    return `${typeLabel}: ${action.payload.from} -> ${action.payload.to}`
  }
  return typeLabel
}

function actionTagType(action) {
  return {
    highlight_pois: 'success',
    open_poi_detail: 'warning',
    draw_route: 'primary'
  }[action] || 'info'
}
</script>
