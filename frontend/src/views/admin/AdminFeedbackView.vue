<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>{{ $t('admin.feedbackTitle') }}</h1>
        <p>{{ $t('admin.feedbackSubtitle') }}</p>
      </div>
      <div class="page-actions">
        <el-input v-model="keyword" clearable :placeholder="$t('admin.searchFeedbackPlaceholder')" style="width: 230px" />
        <el-select v-model="status" style="width: 160px" @change="load">
          <el-option :label="$t('common.all')" value="" />
          <el-option :label="$t('common.pending')" value="PENDING" />
          <el-option :label="$t('common.approved')" value="APPROVED" />
          <el-option :label="$t('common.rejected')" value="REJECTED" />
        </el-select>
      </div>
    </div>

    <div class="admin-summary-grid">
      <section v-for="item in statusSummary" :key="item.status" class="admin-kpi">
        <span>{{ feedbackStatusLabel(item.status) }}</span>
        <strong>{{ item.count }}</strong>
      </section>
    </div>

    <section class="table-panel">
      <div class="admin-filter-bar">
        <span class="filter-count">{{ $t('common.itemsCount', { current: filteredItems.length, total: items.length }) }}</span>
      </div>
      <el-table :data="filteredItems" height="620">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column :label="$t('admin.relatedPoi')" min-width="190">
          <template #default="{ row }">
            <div class="feedback-poi-cell">
              <strong>{{ poiFor(row.poiId)?.name || $t('admin.unboundPoi') }}</strong>
              <span v-if="poiFor(row.poiId)">{{ $t('admin.currentStatus', { status: statusLabel(poiFor(row.poiId).openStatus) }) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="type" :label="$t('common.type')" width="140" />
        <el-table-column prop="content" :label="$t('common.content')" />
        <el-table-column :label="$t('admin.correctionImpact')" min-width="180">
          <template #default="{ row }">
            <span class="impact-text">{{ impactText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.status')" width="110">
          <template #default="{ row }">
            <el-tag :type="feedbackStatusType(row.status)" effect="plain">{{ feedbackStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.action')" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="open(row)">{{ $t('admin.review') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="$t('admin.reviewDialog')" width="520px">
      <el-alert
        v-if="currentPoi"
        :title="$t('admin.currentPoi', { name: currentPoi.name })"
        :description="$t('admin.poiDescription', { status: statusLabel(currentPoi.openStatus), remark: currentPoi.remark || $t('common.noRemark') })"
        type="info"
        :closable="false"
        show-icon
      />
      <p class="review-content">{{ $t('admin.userFeedback', { content: current?.content || '' }) }}</p>
      <el-form label-position="top">
        <el-form-item :label="$t('admin.reviewResult')">
          <el-select v-model="review.status" style="width: 100%">
            <el-option :label="$t('admin.approve')" value="APPROVED" />
            <el-option :label="$t('admin.reject')" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('admin.syncPoiStatus')">
          <el-select v-model="review.poiOpenStatus" clearable style="width: 100%">
            <el-option :label="$t('admin.keepUnchanged')" value="" />
            <el-option :label="$t('common.open')" value="OPEN" />
            <el-option :label="$t('common.tempClosed')" value="TEMP_CLOSED" />
            <el-option :label="$t('common.maintenance')" value="MAINTENANCE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('admin.reviewNote')">
          <el-input v-model="review.reviewNote" type="textarea" />
        </el-form-item>
        <el-form-item :label="$t('admin.reviewedPoiRemark')">
          <el-input
            v-model="review.poiRemark"
            type="textarea"
            :rows="3"
            :placeholder="$t('admin.reviewRemarkPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <div v-if="currentPoi" class="review-impact-card">
        <strong>{{ $t('admin.visibleChange') }}</strong>
        <span>{{ $t('admin.poiStatusChange', { from: statusLabel(currentPoi.openStatus), to: statusLabel(review.poiOpenStatus || currentPoi.openStatus) }) }}</span>
        <span>{{ $t('admin.poiRemarkChange', { remark: review.poiRemark || currentPoi.remark || $t('common.noRemark') }) }}</span>
      </div>
      <template #footer>
        <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submit">{{ $t('admin.confirmReview') }}</el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import AppShell from '../../components/AppShell.vue'
import { feedbackApi, poiApi } from '../../api/modules'

const { t } = useI18n()
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
  { status: 'PENDING', count: countStatus('PENDING') },
  { status: 'APPROVED', count: countStatus('APPROVED') },
  { status: 'REJECTED', count: countStatus('REJECTED') }
])

function open(row) {
  current.value = row
  currentPoi.value = poiFor(row.poiId)
  review.status = 'APPROVED'
  review.reviewNote = t('admin.defaultReviewNote')
  review.poiOpenStatus = row.type === 'TEMP_CLOSED' ? 'TEMP_CLOSED' : ''
  review.poiRemark = buildPoiRemark(row, currentPoi.value)
  visible.value = true
}

async function submit() {
  await feedbackApi.review(current.value.id, review)
  ElMessage.success(t('admin.feedbackReviewed'))
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
    PENDING: t('common.pending'),
    APPROVED: t('common.approved'),
    REJECTED: t('common.rejected')
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
    LOCATION_ERROR: t('admin.impactLocation'),
    COORDINATE_ERROR: t('admin.impactLocation'),
    STATUS_ERROR: t('admin.impactStatus'),
    TEMP_CLOSED: t('admin.impactClosed'),
    INFO_UPDATE: t('admin.impactInfo'),
    INFO_ERROR: t('admin.impactInfo')
  }[row.type] || t('admin.impactDefault')
}

function buildPoiRemark(row, poi) {
  const addition = t('admin.addedByFeedback', { content: row.content })
  return poi?.remark ? `${poi.remark}；${addition}` : addition
}

function statusLabel(status) {
  return {
    OPEN: t('common.open'),
    TEMP_CLOSED: t('common.tempClosed'),
    MAINTENANCE: t('common.maintenance')
  }[status] || status || t('common.unknown')
}
</script>
