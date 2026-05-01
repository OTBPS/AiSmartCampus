<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>{{ $t('admin.poiTitle') }}</h1>
        <p>{{ $t('admin.poiSubtitle') }}</p>
      </div>
      <div class="page-actions">
        <el-button @click="load">{{ $t('common.refresh') }}</el-button>
        <el-button type="primary" @click="openCreate">{{ $t('admin.addPoi') }}</el-button>
      </div>
    </div>

    <section class="table-panel">
      <div class="admin-filter-bar">
        <el-input v-model="filters.keyword" clearable :placeholder="$t('admin.searchPoiPlaceholder')" style="max-width: 300px" />
        <el-select v-model="filters.category" clearable :placeholder="$t('common.category')" style="width: 150px">
          <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="filters.openStatus" clearable :placeholder="$t('admin.statusFilter')" style="width: 150px">
          <el-option :label="$t('common.open')" value="OPEN" />
          <el-option :label="$t('common.tempClosed')" value="TEMP_CLOSED" />
          <el-option :label="$t('common.maintenance')" value="MAINTENANCE" />
        </el-select>
        <el-input v-model="filters.tag" clearable :placeholder="$t('admin.tagFilter')" style="max-width: 180px" />
        <el-switch v-model="filters.enabledOnly" :active-text="$t('admin.enabledOnly')" />
        <span class="filter-count">{{ $t('common.poiCount', { current: filteredPois.length, total: pois.length }) }}</span>
      </div>

      <el-table :data="filteredPois" height="620">
        <el-table-column prop="name" :label="$t('admin.name')" width="180" />
        <el-table-column prop="category" :label="$t('common.category')" width="120" />
        <el-table-column prop="mapRank" label="Map Rank" width="100" />
        <el-table-column prop="locationText" :label="$t('admin.locationText')" />
        <el-table-column :label="$t('admin.tags')" min-width="230">
          <template #default="{ row }">
            <div class="tag-cell">
              <el-tag v-for="tag in splitTags(row.tags)" :key="tag" effect="plain" size="small">{{ tag }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.openStatus)" effect="plain">{{ statusLabel(row.openStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('admin.available')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? $t('common.enabled') : $t('common.disabled') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.action')" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">{{ $t('common.edit') }}</el-button>
            <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggle(row)">
              {{ row.enabled ? $t('common.disabled') : $t('common.enabled') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="form.id ? $t('admin.editPoi') : $t('admin.createPoi')" width="620px">
      <el-form label-position="top">
        <el-form-item :label="$t('admin.name')"><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="$t('common.category')">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('admin.coordinates')">
          <el-input-number v-model="form.longitude" :precision="6" style="width: 48%" />
          <el-input-number v-model="form.latitude" :precision="6" style="width: 48%; margin-left: 4%" />
        </el-form-item>
        <CoordinatePreview
          :longitude="form.longitude"
          :latitude="form.latitude"
          @pick="updateCoordinates"
        />
        <el-form-item :label="$t('admin.locationText')"><el-input v-model="form.locationText" /></el-form-item>
        <el-form-item :label="$t('admin.tags')"><el-input v-model="form.tags" :placeholder="$t('admin.tagPlaceholder')" /></el-form-item>
        <el-form-item label="Map Rank">
          <el-input-number v-model="form.mapRank" :min="1" :max="20" :precision="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Source URL"><el-input v-model="form.sourceUrl" /></el-form-item>
        <el-form-item :label="$t('admin.statusFilter')">
          <el-select v-model="form.openStatus" style="width: 100%">
            <el-option :label="$t('common.open')" value="OPEN" />
            <el-option :label="$t('common.tempClosed')" value="TEMP_CLOSED" />
            <el-option :label="$t('common.maintenance')" value="MAINTENANCE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('admin.remark')"><el-input v-model="form.remark" type="textarea" /></el-form-item>
        <el-checkbox v-model="form.sheltered">{{ $t('admin.sheltered') }}</el-checkbox>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="save">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import AppShell from '../../components/AppShell.vue'
import CoordinatePreview from '../../components/CoordinatePreview.vue'
import { poiApi } from '../../api/modules'

const { t } = useI18n()
const categoryOptions = ['STUDY', 'TEACHING', 'DINING', 'DORM', 'SERVICE', 'SPORTS', 'TRANSPORT', 'LANDMARK']
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
    longitude: 118.71885,
    latitude: 32.20796,
    locationText: '',
    openStatus: 'OPEN',
    tags: '',
    sheltered: false,
    remark: '',
    enabled: true,
    mapRank: null,
    sourceUrl: ''
  }
}

async function load() {
  pois.value = await poiApi.list({ enabledOnly: false })
}

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

function updateCoordinates(point) {
  form.longitude = point.longitude
  form.latitude = point.latitude
}

async function save() {
  if (form.id) await poiApi.update(form.id, form)
  else await poiApi.create(form)
  ElMessage.success(t('admin.poiSaved'))
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
</script>
