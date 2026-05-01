<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>反馈审核</h1>
        <p>反馈审核必须关联 POI 状态变化，形成地图数据修正闭环。</p>
      </div>
      <div class="page-actions">
        <el-input v-model="keyword" clearable placeholder="搜索反馈内容或地点" style="width: 230px" />
        <el-select v-model="status" style="width: 160px" @change="load">
          <el-option label="全部" value="" />
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
      </div>
    </div>

    <div class="admin-summary-grid">
      <section v-for="item in statusSummary" :key="item.status" class="admin-kpi">
        <span>{{ item.label }}</span>
        <strong>{{ item.count }}</strong>
      </section>
    </div>

    <section class="table-panel">
      <div class="admin-filter-bar">
        <span class="filter-count">当前 {{ filteredItems.length }} / {{ items.length }} 条</span>
      </div>
      <el-table :data="filteredItems" height="620">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="关联 POI" min-width="190">
          <template #default="{ row }">
            <div class="feedback-poi-cell">
              <strong>{{ poiFor(row.poiId)?.name || '未绑定地点' }}</strong>
              <span v-if="poiFor(row.poiId)">当前状态：{{ poiFor(row.poiId).openStatus }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="140" />
        <el-table-column prop="content" label="内容" />
        <el-table-column label="修正影响" min-width="180">
          <template #default="{ row }">
            <span class="impact-text">{{ impactText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="feedbackStatusType(row.status)" effect="plain">{{ feedbackStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="open(row)">审核</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" title="审核反馈" width="520px">
      <el-alert
        v-if="currentPoi"
        :title="`当前 POI：${currentPoi.name}`"
        :description="`状态：${currentPoi.openStatus}；备注：${currentPoi.remark || '暂无备注'}`"
        type="info"
        :closable="false"
        show-icon
      />
      <p class="review-content">用户反馈：{{ current?.content }}</p>
      <el-form label-position="top">
        <el-form-item label="审核结果">
          <el-select v-model="review.status" style="width: 100%">
            <el-option label="通过" value="APPROVED" />
            <el-option label="驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="同步 POI 状态">
          <el-select v-model="review.poiOpenStatus" clearable style="width: 100%">
            <el-option label="保持不变" value="" />
            <el-option label="开放" value="OPEN" />
            <el-option label="临时关闭" value="TEMP_CLOSED" />
            <el-option label="维护中" value="MAINTENANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核备注">
          <el-input v-model="review.reviewNote" type="textarea" />
        </el-form-item>
        <el-form-item label="审核后 POI 备注">
          <el-input
            v-model="review.poiRemark"
            type="textarea"
            :rows="3"
            placeholder="审核通过后同步写入地点详情，用户端可直接看到变化"
          />
        </el-form-item>
      </el-form>
      <div v-if="currentPoi" class="review-impact-card">
        <strong>审核通过后的用户端可见变化</strong>
        <span>地点状态：{{ currentPoi.openStatus }} -> {{ review.poiOpenStatus || currentPoi.openStatus }}</span>
        <span>地点备注：{{ review.poiRemark || currentPoi.remark || '暂无备注' }}</span>
      </div>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确认审核</el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppShell from '../../components/AppShell.vue'
import { feedbackApi, poiApi } from '../../api/modules'

const status = ref('PENDING')
const keyword = ref('')
const items = ref([])
const allItems = ref([])
const pois = ref([])
const visible = ref(false)
const current = ref(null)
const currentPoi = ref(null)
const review = reactive({ status: 'APPROVED', reviewNote: '', poiOpenStatus: '', poiRemark: '' })

onMounted(load)

async function load() {
  const [feedbackItems, allFeedbackItems, poiItems] = await Promise.all([
    feedbackApi.adminList({ status: status.value }),
    feedbackApi.adminList({}),
    poiApi.list({ enabledOnly: false })
  ])
  items.value = feedbackItems
  allItems.value = allFeedbackItems
  pois.value = poiItems
}

const filteredItems = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  if (!key) return items.value
  return items.value.filter((item) => {
    const poi = poiFor(item.poiId)
    return `${item.content || ''} ${item.type || ''} ${poi?.name || ''} ${poi?.locationText || ''}`.toLowerCase().includes(key)
  })
})

const statusSummary = computed(() => [
  { status: 'PENDING', label: '待审核', count: countStatus('PENDING') },
  { status: 'APPROVED', label: '已通过', count: countStatus('APPROVED') },
  { status: 'REJECTED', label: '已驳回', count: countStatus('REJECTED') }
])

function open(row) {
  current.value = row
  currentPoi.value = poiFor(row.poiId)
  review.status = 'APPROVED'
  review.reviewNote = '已核验，信息将同步到校园 POI 数据。'
  review.poiOpenStatus = row.type === 'TEMP_CLOSED' ? 'TEMP_CLOSED' : ''
  review.poiRemark = buildPoiRemark(row, currentPoi.value)
  visible.value = true
}

async function submit() {
  await feedbackApi.review(current.value.id, review)
  ElMessage.success('反馈已审核')
  visible.value = false
  await load()
}

function poiFor(poiId) {
  return pois.value.find((item) => item.id === poiId)
}

function countStatus(value) {
  return allItems.value.filter((item) => item.status === value).length
}

function feedbackStatusLabel(value) {
  return {
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已驳回'
  }[value] || value
}

function feedbackStatusType(value) {
  return {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger'
  }[value] || 'info'
}

function impactText(row) {
  return {
    LOCATION_ERROR: '位置或坐标修正',
    STATUS_ERROR: '开放状态修正',
    TEMP_CLOSED: '临时关闭提醒',
    INFO_UPDATE: '地点备注补充'
  }[row.type] || 'POI 信息补充'
}

function buildPoiRemark(row, poi) {
  const addition = `已根据用户反馈补充：${row.content}`
  return poi?.remark ? `${poi.remark}；${addition}` : addition
}
</script>
