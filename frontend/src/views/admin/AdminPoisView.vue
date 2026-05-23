<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>{{ readOnly ? $t('admin.poiSearchTitle') : $t('admin.poiTitle') }}</h1>
        <p v-if="!readOnly">{{ $t('admin.poiSubtitle') }}</p>
      </div>
      <div class="page-actions">
        <el-button @click="load">{{ $t('common.refresh') }}</el-button>
        <el-button v-if="!readOnly" type="primary" @click="openCreate">{{ $t('admin.addPoi') }}</el-button>
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
        <el-switch v-if="!readOnly" v-model="filters.enabledOnly" :active-text="$t('admin.enabledOnly')" />
        <span class="filter-count">{{ $t('common.poiCount', { current: filteredPois.length, total: pois.length }) }}</span>
      </div>

      <el-table
        :data="filteredPois"
        height="620"
        :row-class-name="poiRowClassName"
        @row-click="handlePoiRowClick"
      >
        <el-table-column prop="name" :label="$t('admin.name')" width="180" />
        <el-table-column prop="category" :label="$t('common.category')" width="120" />
        <el-table-column :label="localText('poiImage')" width="92">
          <template #default="{ row }">
            <img v-if="row.imageUrl" class="poi-table-image" :src="row.imageUrl" :alt="row.name" />
            <span v-else class="poi-table-no-image">--</span>
          </template>
        </el-table-column>
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
        <el-table-column v-if="!readOnly" :label="$t('common.action')" width="160">
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
          <el-input-number v-model="form.mapRank" :min="1" :max="20" :precision="0" :value-on-clear="null" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Source URL"><el-input v-model="form.sourceUrl" /></el-form-item>
        <el-form-item :label="localText('poiImage')">
          <div class="poi-image-field">
            <div v-if="poiImagePreview" class="poi-image-preview">
              <img :src="poiImagePreview" :alt="form.name || localText('poiImage')" />
            </div>
            <div v-else class="poi-image-empty">{{ localText('noImage') }}</div>
            <div class="poi-image-actions">
              <el-upload
                accept="image/jpeg,image/png,image/webp,image/gif"
                :auto-upload="false"
                :show-file-list="false"
                :on-change="selectPoiImage"
              >
                <el-button :icon="Upload">{{ localText('chooseImage') }}</el-button>
              </el-upload>
              <el-button
                v-if="poiImagePreview"
                :icon="Delete"
                type="danger"
                plain
                @click="clearPoiImage"
              >
                {{ localText('removeImage') }}
              </el-button>
            </div>
            <span class="poi-image-hint">{{ localText('imageHint') }}</span>
          </div>
        </el-form-item>
        <el-form-item :label="$t('admin.statusFilter')">
          <el-select v-model="form.openStatus" style="width: 100%">
            <el-option :label="$t('common.open')" value="OPEN" />
            <el-option :label="$t('common.tempClosed')" value="TEMP_CLOSED" />
            <el-option :label="$t('common.maintenance')" value="MAINTENANCE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('admin.remark')"><el-input v-model="form.remark" type="textarea" /></el-form-item>
        <el-checkbox v-model="form.sheltered">{{ $t('admin.sheltered') }}</el-checkbox>
        <section v-if="form.id" class="poi-delete-zone">
          <div>
            <strong>{{ localText('deletePoiTitle') }}</strong>
            <p>{{ localText('deletePoiHint') }}</p>
          </div>
          <el-button
            type="danger"
            plain
            :icon="Delete"
            :loading="deleting"
            :disabled="saving"
            @click="confirmDeletePoi"
          >
            {{ localText('deletePoi') }}
          </el-button>
        </section>
      </el-form>
      <template #footer>
        <el-button :disabled="deleting" @click="visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" :disabled="deleting" @click="save">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Upload } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppShell from '../../components/AppShell.vue'
import CoordinatePreview from '../../components/CoordinatePreview.vue'
import { poiApi } from '../../api/modules'

const { t, locale } = useI18n()
const router = useRouter()
const props = defineProps({
  readOnly: { type: Boolean, default: false }
})
const readOnly = computed(() => props.readOnly)
const categoryOptions = ['STUDY', 'TEACHING', 'DINING', 'DORM', 'SERVICE', 'SPORTS', 'TRANSPORT', 'LANDMARK']
const pois = ref([])
const visible = ref(false)
const saving = ref(false)
const deleting = ref(false)
const pendingImageFile = ref(null)
const pendingImagePreview = ref('')
const imageMarkedForDelete = ref(false)
const form = reactive(emptyForm())
const filters = reactive({
  keyword: '',
  category: '',
  openStatus: '',
  tag: '',
  enabledOnly: props.readOnly
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
    sourceUrl: '',
    imageUrl: ''
  }
}

async function load() {
  pois.value = await poiApi.list({ enabledOnly: props.readOnly })
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
const poiImagePreview = computed(() => pendingImagePreview.value || (imageMarkedForDelete.value ? '' : normalizeNullableText(form.imageUrl)))

onUnmounted(clearPendingImage)

function poiRowClassName() {
  return props.readOnly ? 'poi-search-row' : ''
}

function handlePoiRowClick(row) {
  if (!props.readOnly || !row?.id) return
  router.push({ path: '/map-chat', query: { poiId: row.id, from: 'poi-search' } })
}

function openCreate() {
  if (props.readOnly) return
  resetImageState()
  Object.assign(form, emptyForm())
  visible.value = true
}

function openEdit(row) {
  if (props.readOnly) return
  resetImageState()
  Object.assign(form, emptyForm(), row, {
    tags: row.tags || '',
    remark: row.remark || '',
    sourceUrl: normalizeNullableText(row.sourceUrl),
    imageUrl: normalizeNullableText(row.imageUrl),
    mapRank: row.mapRank ?? null,
    enabled: row.enabled !== false,
    sheltered: row.sheltered === true
  })
  visible.value = true
}

function updateCoordinates(point) {
  form.longitude = point.longitude
  form.latitude = point.latitude
}

async function save() {
  if (props.readOnly) return
  saving.value = true
  try {
    const payload = buildPayload()
    let savedPoi = form.id ? await poiApi.update(form.id, payload) : await poiApi.create(payload)
    if (pendingImageFile.value) {
      savedPoi = await poiApi.uploadImage(savedPoi.id, pendingImageFile.value)
    } else if (imageMarkedForDelete.value && savedPoi.id) {
      savedPoi = await poiApi.deleteImage(savedPoi.id)
    }
    Object.assign(form, emptyForm(), savedPoi, {
      tags: savedPoi.tags || '',
      remark: savedPoi.remark || '',
      sourceUrl: normalizeNullableText(savedPoi.sourceUrl),
      imageUrl: normalizeNullableText(savedPoi.imageUrl),
      mapRank: savedPoi.mapRank ?? null
    })
    resetImageState()
    ElMessage.success(t('admin.poiSaved'))
    visible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  if (props.readOnly) return
  await poiApi.updateStatus(row.id, { enabled: !row.enabled })
  load()
}

async function confirmDeletePoi() {
  if (props.readOnly) return
  if (!form.id) return
  try {
    await ElMessageBox.confirm(localText('deletePoiMessage', { name: form.name || `#${form.id}` }), localText('deletePoiTitle'), {
      confirmButtonText: localText('deletePoi'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    })
    deleting.value = true
    await poiApi.remove(form.id)
    ElMessage.success(localText('deletePoiSuccess'))
    visible.value = false
    Object.assign(form, emptyForm())
    resetImageState()
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || `${error}`)
    }
  } finally {
    deleting.value = false
  }
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

function buildPayload() {
  return {
    name: normalizeText(form.name),
    category: normalizeText(form.category),
    longitude: normalizeNumber(form.longitude),
    latitude: normalizeNumber(form.latitude),
    locationText: normalizeText(form.locationText),
    openStatus: normalizeText(form.openStatus) || 'OPEN',
    tags: normalizeText(form.tags),
    sheltered: form.sheltered === true,
    remark: normalizeText(form.remark),
    enabled: form.enabled !== false,
    mapRank: normalizeMapRank(form.mapRank),
    sourceUrl: normalizeNullableText(form.sourceUrl)
  }
}

function normalizeText(value) {
  return value == null ? '' : `${value}`.trim()
}

function normalizeNullableText(value) {
  const text = normalizeText(value)
  return text.toLowerCase() === 'null' ? '' : text
}

function normalizeNumber(value) {
  if (value === null || value === undefined || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function normalizeMapRank(value) {
  const number = normalizeNumber(value)
  return number == null ? null : Math.trunc(number)
}

function selectPoiImage(uploadFile) {
  const file = uploadFile?.raw
  if (!file) return false
  if (!/^image\/(jpeg|png|webp|gif)$/.test(file.type || '')) {
    ElMessage.error(localText('imageTypeError'))
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error(localText('imageSizeError'))
    return false
  }
  clearPendingImage()
  pendingImageFile.value = file
  pendingImagePreview.value = URL.createObjectURL(file)
  imageMarkedForDelete.value = false
  return false
}

function clearPoiImage() {
  clearPendingImage()
  form.imageUrl = ''
  imageMarkedForDelete.value = true
}

function resetImageState() {
  clearPendingImage()
  imageMarkedForDelete.value = false
}

function clearPendingImage() {
  if (pendingImagePreview.value) {
    URL.revokeObjectURL(pendingImagePreview.value)
  }
  pendingImageFile.value = null
  pendingImagePreview.value = ''
}

function localText(key, params = {}) {
  const zh = {
    poiImage: '地点图片',
    noImage: '暂无图片',
    chooseImage: '选择图片',
    removeImage: '移除图片',
    imageHint: '每个地点只能保留一张图片；重新选择会替换原图。支持 JPG、PNG、WEBP、GIF，最大 5MB。',
    imageTypeError: '只支持 JPG、PNG、WEBP 或 GIF 图片',
    imageSizeError: '图片不能超过 5MB',
    deletePoiTitle: '删除 POI',
    deletePoiHint: '永久删除该地点和系统托管的地点图片。此操作不可撤销。',
    deletePoi: '删除 POI',
    deletePoiMessage: '确定要删除“{name}”吗？删除后该地点将从地图和管理列表移除。',
    deletePoiSuccess: 'POI 已删除'
  }
  const en = {
    poiImage: 'Place image',
    noImage: 'No image',
    chooseImage: 'Choose image',
    removeImage: 'Remove image',
    imageHint: 'Each place keeps one image only. Choosing another image replaces the current one. JPG, PNG, WEBP, GIF, up to 5 MB.',
    imageTypeError: 'Only JPG, PNG, WEBP, or GIF images are supported',
    imageSizeError: 'Image must be 5 MB or smaller',
    deletePoiTitle: 'Delete POI',
    deletePoiHint: 'Permanently deletes this place and any managed place image. This cannot be undone.',
    deletePoi: 'Delete POI',
    deletePoiMessage: 'Delete "{name}"? It will be removed from the map and admin list.',
    deletePoiSuccess: 'POI deleted'
  }
  const text = (locale.value === 'en-US' ? en : zh)[key] || key
  return Object.entries(params).reduce((value, [name, replacement]) => {
    return value.replaceAll(`{${name}}`, replacement)
  }, text)
}
</script>
