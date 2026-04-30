<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>POI 管理</h1>
        <p>维护自建校园地点数据，支撑 AI 推荐和地图展示。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增地点</el-button>
    </div>

    <section class="table-panel">
      <el-table :data="pois" height="620">
        <el-table-column prop="name" label="名称" width="180" />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="locationText" label="位置描述" />
        <el-table-column prop="tags" label="标签" />
        <el-table-column prop="openStatus" label="状态" width="110" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggle(row)">
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="form.id ? '编辑 POI' : '新增 POI'" width="620px">
      <el-form label-position="top">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="form.category" placeholder="STUDY / DINING / SERVICE" /></el-form-item>
        <el-form-item label="坐标">
          <el-input-number v-model="form.longitude" :precision="6" style="width: 48%" />
          <el-input-number v-model="form.latitude" :precision="6" style="width: 48%; margin-left: 4%" />
        </el-form-item>
        <el-form-item label="位置描述"><el-input v-model="form.locationText" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="安静,有插座,遮蔽" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
        <el-checkbox v-model="form.sheltered">遮蔽地点</el-checkbox>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppShell from '../../components/AppShell.vue'
import { poiApi } from '../../api/modules'

const pois = ref([])
const visible = ref(false)
const form = reactive(emptyForm())

onMounted(load)

function emptyForm() {
  return {
    id: null,
    name: '',
    category: 'STUDY',
    longitude: 113.9345,
    latitude: 22.5331,
    locationText: '',
    openStatus: 'OPEN',
    tags: '',
    sheltered: false,
    remark: '',
    enabled: true
  }
}

async function load() {
  pois.value = await poiApi.list({ enabledOnly: false })
}

function openCreate() {
  Object.assign(form, emptyForm())
  visible.value = true
}

function openEdit(row) {
  Object.assign(form, row)
  visible.value = true
}

async function save() {
  if (form.id) await poiApi.update(form.id, form)
  else await poiApi.create(form)
  ElMessage.success('POI 已保存')
  visible.value = false
  load()
}

async function toggle(row) {
  await poiApi.updateStatus(row.id, { enabled: !row.enabled })
  load()
}
</script>

