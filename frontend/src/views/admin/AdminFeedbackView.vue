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
        <el-table-column prop="poiId" label="POI" width="90" />
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
      <p>{{ current?.content }}</p>
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
import { feedbackApi } from '../../api/modules'

const status = ref('PENDING')
const items = ref([])
const visible = ref(false)
const current = ref(null)
const review = reactive({ status: 'APPROVED', reviewNote: '', poiOpenStatus: '' })

onMounted(load)

async function load() {
  items.value = await feedbackApi.adminList({ status: status.value })
}

function open(row) {
  current.value = row
  review.status = 'APPROVED'
  review.reviewNote = ''
  review.poiOpenStatus = ''
  visible.value = true
}

async function submit() {
  await feedbackApi.review(current.value.id, review)
  ElMessage.success('反馈已审核')
  visible.value = false
  load()
}
</script>

