<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>反馈审核</h1>
        <p>反馈审核必须关联 POI 状态变化，形成地图数据修正闭环。</p>
      </div>
      <el-select v-model="status" style="width: 180px" @change="load">
        <el-option label="全部" value="" />
        <el-option label="待审核" value="PENDING" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已驳回" value="REJECTED" />
      </el-select>
    </div>

    <section class="table-panel">
      <el-table :data="items" height="620">
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
        <el-table-column prop="status" label="状态" width="110" />
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
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确认审核</el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppShell from '../../components/AppShell.vue'
import { feedbackApi, poiApi } from '../../api/modules'

const status = ref('PENDING')
const items = ref([])
const pois = ref([])
const visible = ref(false)
const current = ref(null)
const currentPoi = ref(null)
const review = reactive({ status: 'APPROVED', reviewNote: '', poiOpenStatus: '', poiRemark: '' })

onMounted(load)

async function load() {
  const [feedbackItems, poiItems] = await Promise.all([
    feedbackApi.adminList({ status: status.value }),
    poiApi.list({ enabledOnly: false })
  ])
  items.value = feedbackItems
  pois.value = poiItems
}

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

function buildPoiRemark(row, poi) {
  const addition = `已根据用户反馈补充：${row.content}`
  return poi?.remark ? `${poi.remark}；${addition}` : addition
}
</script>
