<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>{{ $t('admin.aiLogsTitle') }}</h1>
        <p>{{ $t('admin.aiLogsSubtitle') }}</p>
      </div>
      <el-button type="primary" @click="load">{{ $t('common.refresh') }}</el-button>
    </div>

    <section class="table-panel">
      <div class="admin-filter-bar">
        <el-input
          v-model="keyword"
          clearable
          :placeholder="$t('admin.searchLogsPlaceholder')"
          style="max-width: 340px"
        />
        <el-select v-model="intentFilter" clearable :placeholder="$t('admin.intentFilter')" style="width: 180px">
          <el-option v-for="item in intentOptions" :key="item" :label="intentLabel(item)" :value="item" />
        </el-select>
        <span class="filter-count">{{ $t('common.itemsCount', { current: filteredLogs.length, total: logs.length }) }}</span>
      </div>

      <el-table :data="filteredLogs" height="620">
        <el-table-column :label="$t('admin.userQuestion')" min-width="230">
          <template #default="{ row }">
            <div class="log-question">{{ row.question }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('admin.recognizedIntent')" width="135">
          <template #default="{ row }">
            <el-tag effect="plain">{{ intentLabel(row.intent) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('admin.toolCall')" min-width="210">
          <template #default="{ row }">
            <div class="action-chip-list compact">
              <el-tag v-for="item in toolLabels(row)" :key="item.key" type="info" effect="plain">{{ item.label }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('admin.mapAction')" min-width="250">
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
        <el-table-column :label="$t('admin.aiReply')" min-width="300">
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
import { useI18n } from 'vue-i18n'
import AppShell from '../../components/AppShell.vue'
import { aiApi } from '../../api/modules'

const { t } = useI18n()
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
  return t(`labels.intent.${intent}`, t('labels.intent.unknown'))
}

function toolLabel(tool) {
  return t(`labels.tool.${tool}`, tool)
}

function actionLabel(action) {
  const typeLabel = t(`labels.action.${action.type}`, action.type || t('labels.action.unknown'))
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
