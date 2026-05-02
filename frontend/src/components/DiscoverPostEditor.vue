<template>
  <el-dialog
    :model-value="modelValue"
    :title="post?.id ? localText('editTitle') : localText('createTitle')"
    width="620px"
    class="note-editor-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-position="top" class="note-form">
      <el-form-item :label="localText('title')">
        <el-input v-model="form.title" maxlength="120" show-word-limit />
      </el-form-item>
      <el-form-item :label="localText('place')">
        <el-select
          v-model="form.poiId"
          filterable
          remote
          reserve-keyword
          :remote-method="searchPois"
          :loading="poiLoading"
          :placeholder="localText('placePlaceholder')"
          style="width: 100%"
        >
          <el-option v-for="poi in poiOptions" :key="poi.id" :label="poi.name" :value="poi.id">
            <div class="poi-option">
              <strong>{{ poi.name }}</strong>
              <span>{{ poi.category }} · {{ poi.locationText }}</span>
            </div>
          </el-option>
        </el-select>
      </el-form-item>
      <div v-if="selectedPoi" class="selected-poi-strip">
        <el-tag size="small" effect="plain">{{ selectedPoi.category }}</el-tag>
        <span>{{ selectedPoi.locationText }}</span>
      </div>
      <el-form-item :label="localText('rating')">
        <el-rate v-model="form.rating" :max="5" />
      </el-form-item>
      <el-form-item :label="localText('body')">
        <el-input v-model="form.body" type="textarea" :rows="8" maxlength="3000" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ localText('cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="save">{{ localText('save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { discoverApi, poiApi } from '../api/modules'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  post: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])
const { locale } = useI18n()

const saving = ref(false)
const poiLoading = ref(false)
const poiOptions = ref([])
const form = reactive(emptyForm())

const selectedPoi = computed(() => poiOptions.value.find((poi) => poi.id === form.poiId))

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) hydrateForm()
  }
)

watch(
  () => props.post,
  () => {
    if (props.modelValue) hydrateForm()
  }
)

function emptyForm() {
  return { title: '', body: '', poiId: null, rating: 4 }
}

function hydrateForm() {
  Object.assign(form, emptyForm(), {
    title: props.post?.title || '',
    body: props.post?.body || '',
    poiId: props.post?.poiId || null,
    rating: props.post?.rating || 4
  })
  if (props.post?.poiId) {
    poiOptions.value = [{
      id: props.post.poiId,
      name: props.post.poiName,
      category: props.post.poiCategory || props.post.category,
      locationText: props.post.poiLocationText || ''
    }]
  } else {
    poiOptions.value = []
  }
}

async function searchPois(keyword) {
  const query = `${keyword || ''}`.trim()
  if (!query) {
    poiOptions.value = []
    return
  }
  poiLoading.value = true
  try {
    poiOptions.value = await poiApi.list({ keyword: query, enabledOnly: true, limit: 20 })
  } finally {
    poiLoading.value = false
  }
}

async function save() {
  if (!form.title.trim() || !form.body.trim() || !form.poiId) {
    ElMessage.warning(localText('required'))
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      body: form.body.trim(),
      poiId: form.poiId,
      rating: form.rating || 4
    }
    const saved = props.post?.id
      ? await discoverApi.update(props.post.id, payload)
      : await discoverApi.create(payload)
    emit('saved', saved)
    emit('update:modelValue', false)
    ElMessage.success(localText('saved'))
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

function localText(key) {
  const zh = {
    createTitle: '发布 note',
    editTitle: '编辑 note',
    title: '标题',
    place: '关联地点',
    placePlaceholder: '输入关键词搜索地点',
    rating: '地点评价',
    body: '正文',
    cancel: '取消',
    save: '保存',
    required: '请填写标题、正文并选择地点',
    saved: 'note 已保存'
  }
  const en = {
    createTitle: 'Create note',
    editTitle: 'Edit note',
    title: 'Title',
    place: 'Place',
    placePlaceholder: 'Search places by keyword',
    rating: 'Place rating',
    body: 'Body',
    cancel: 'Cancel',
    save: 'Save',
    required: 'Enter title, body, and place',
    saved: 'Note saved'
  }
  return (locale.value === 'en-US' ? en : zh)[key] || key
}
</script>
