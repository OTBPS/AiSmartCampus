<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>POI 管理</h1>
        <p>维护自建校园地点数据，支撑 AI 推荐和地图展示。</p>
      </div>
      <div class="page-actions">
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="openCreate">新增地点</el-button>
      </div>
    </div>

    <section class="table-panel">
      <div class="admin-filter-bar">
        <el-input v-model="filters.keyword" clearable placeholder="搜索名称、位置或标签" style="max-width: 300px" />
        <el-select v-model="filters.category" clearable placeholder="分类" style="width: 150px">
          <el-option v-for="item in categories" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="filters.openStatus" clearable placeholder="开放状态" style="width: 150px">
          <el-option label="开放" value="OPEN" />
          <el-option label="临时关闭" value="TEMP_CLOSED" />
          <el-option label="维护中" value="MAINTENANCE" />
        </el-select>
        <el-input v-model="filters.tag" clearable placeholder="标签筛选" style="max-width: 180px" />
        <el-switch v-model="filters.enabledOnly" active-text="仅启用" />
        <span class="filter-count">当前 {{ filteredPois.length }} / {{ pois.length }} 个地点</span>
      </div>

      <el-table :data="filteredPois" height="620">
        <el-table-column prop="name" label="名称" width="180" />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="locationText" label="位置描述" />
        <el-table-column label="标签" min-width="230">
          <template #default="{ row }">
            <div class="tag-cell">
              <el-tag v-for="tag in splitTags(row.tags)" :key="tag" effect="plain" size="small">{{ tag }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.openStatus)" effect="plain">{{ statusLabel(row.openStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="可用" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
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
        <el-form-item label="开放状态">
          <el-select v-model="form.openStatus" style="width: 100%">
            <el-option label="开放" value="OPEN" />
            <el-option label="临时关闭" value="TEMP_CLOSED" />
            <el-option label="维护中" value="MAINTENANCE" />
          </el-select>
        </el-form-item>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppShell from '../../components/AppShell.vue'
import { poiApi } from '../../api/modules'

const pois = ref([])
const visible = ref(false)
const form = reactive(emptyForm())
const filters = reactive({
  keyword: '',
  category: '',
  openStatus: '',
  tag: '',
  enabledOnly: false
})

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

const categories = computed(() => Array.from(new Set(pois.value.map((item) => item.category).filter(Boolean))))

const filteredPois = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  const tag = filters.tag.trim().toLowerCase()
  return pois.value.filter((item) => {
    const text = `${item.name || ''} ${item.locationText || ''} ${item.tags || ''} ${item.remark || ''}`.toLowerCase()
    const matchesKeyword = !keyword || text.includes(keyword)
    const matchesTag = !tag || `${item.tags || ''}`.toLowerCase().includes(tag)
    const matchesCategory = !filters.category || item.category === filters.category
    const matchesStatus = !filters.openStatus || item.openStatus === filters.openStatus
    const matchesEnabled = !filters.enabledOnly || item.enabled
    return matchesKeyword && matchesTag && matchesCategory && matchesStatus && matchesEnabled
  })
})

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

function splitTags(tags) {
  return `${tags || ''}`.split(/[,，]/).map((tag) => tag.trim()).filter(Boolean)
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
