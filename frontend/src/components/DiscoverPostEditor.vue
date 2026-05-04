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
      <el-form-item :label="localText('images')">
        <div class="note-image-uploader">
          <div v-if="imagePreviews.length" class="note-image-preview-grid">
            <div v-for="image in imagePreviews" :key="image.key" class="note-image-preview">
              <img :src="image.url" :alt="localText('imageAlt')" />
              <button class="note-image-remove" type="button" :aria-label="localText('removeImage')" @click="removeImage(image)">
                <el-icon><CloseBold /></el-icon>
              </button>
            </div>
          </div>
          <button
            v-if="imagePreviews.length < maxNoteImages"
            class="note-image-add"
            type="button"
            @click="triggerImagePicker"
          >
            <el-icon><Plus /></el-icon>
            {{ localText('addImages') }}
          </button>
          <input
            ref="imageInput"
            class="note-image-input"
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif"
            multiple
            @change="selectImages"
          />
          <p>{{ localText('imageHint') }}</p>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ localText('cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="save">{{ localText('save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { CloseBold, Plus } from '@element-plus/icons-vue'
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
const imageInput = ref(null)
const existingImages = ref([])
const removedImageIds = ref([])
const pendingImages = ref([])
const form = reactive(emptyForm())
const maxNoteImages = 3
const maxNoteImageBytes = 5 * 1024 * 1024
const allowedNoteImageTypes = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])

const selectedPoi = computed(() => poiOptions.value.find((poi) => poi.id === form.poiId))
const imagePreviews = computed(() => [
  ...existingImages.value.map((image) => ({
    key: `existing-${image.id}`,
    id: image.id,
    url: image.imageUrl,
    existing: true
  })),
  ...pendingImages.value.map((image) => ({
    key: image.key,
    url: image.previewUrl,
    existing: false
  }))
])

onBeforeUnmount(clearPendingImageUrls)

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
  clearPendingImageUrls()
  pendingImages.value = []
  removedImageIds.value = []
  existingImages.value = [...(props.post?.images || [])]
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
    let saved = props.post?.id
      ? await discoverApi.update(props.post.id, payload)
      : await discoverApi.create(payload)
    let imageFailed = false
    try {
      for (const imageId of removedImageIds.value) {
        saved = await discoverApi.deleteImage(saved.id, imageId)
      }
      if (pendingImages.value.length) {
        saved = await discoverApi.uploadImages(saved.id, pendingImages.value.map((image) => image.file))
      }
    } catch (error) {
      imageFailed = true
      ElMessage.warning(localText('imageUploadFailed'))
    }
    emit('saved', saved)
    emit('update:modelValue', false)
    clearPendingImageUrls()
    pendingImages.value = []
    if (!imageFailed) {
      ElMessage.success(localText('saved'))
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

function triggerImagePicker() {
  imageInput.value?.click()
}

function selectImages(event) {
  const files = Array.from(event.target.files || [])
  const availableSlots = maxNoteImages - imagePreviews.value.length
  if (availableSlots <= 0) {
    ElMessage.warning(localText('imageLimit'))
    event.target.value = ''
    return
  }
  if (files.length > availableSlots) {
    ElMessage.warning(localText('imageLimit'))
  }
  files.slice(0, availableSlots).forEach((file) => {
    if (!allowedNoteImageTypes.has(file.type)) {
      ElMessage.warning(localText('imageTypeError'))
      return
    }
    if (file.size > maxNoteImageBytes) {
      ElMessage.warning(localText('imageSizeError'))
      return
    }
    pendingImages.value.push({
      key: `pending-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      file,
      previewUrl: URL.createObjectURL(file)
    })
  })
  event.target.value = ''
}

function removeImage(image) {
  if (image.existing) {
    existingImages.value = existingImages.value.filter((item) => item.id !== image.id)
    removedImageIds.value = [...removedImageIds.value, image.id]
    return
  }
  const removed = pendingImages.value.find((item) => item.key === image.key)
  if (removed) URL.revokeObjectURL(removed.previewUrl)
  pendingImages.value = pendingImages.value.filter((item) => item.key !== image.key)
}

function clearPendingImageUrls() {
  pendingImages.value.forEach((image) => URL.revokeObjectURL(image.previewUrl))
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
    images: '图片',
    addImages: '添加图片',
    imageHint: '最多 3 张，支持 JPG、PNG、WEBP、GIF，单张不超过 5MB',
    imageAlt: 'note 图片',
    removeImage: '删除图片',
    cancel: '取消',
    save: '保存',
    required: '请填写标题、正文并选择地点',
    saved: 'note 已保存',
    imageLimit: '每条 note 最多上传 3 张图片',
    imageTypeError: '图片只支持 JPG、PNG、WEBP 或 GIF',
    imageSizeError: '单张图片不能超过 5MB',
    imageUploadFailed: 'note 已保存，但图片上传失败'
  }
  const en = {
    createTitle: 'Create note',
    editTitle: 'Edit note',
    title: 'Title',
    place: 'Place',
    placePlaceholder: 'Search places by keyword',
    rating: 'Place rating',
    body: 'Body',
    images: 'Images',
    addImages: 'Add images',
    imageHint: 'Up to 3 images. JPG, PNG, WEBP, or GIF. Max 5MB each.',
    imageAlt: 'Note image',
    removeImage: 'Remove image',
    cancel: 'Cancel',
    save: 'Save',
    required: 'Enter title, body, and place',
    saved: 'Note saved',
    imageLimit: 'Each note can have at most 3 images',
    imageTypeError: 'Images must be JPG, PNG, WEBP, or GIF',
    imageSizeError: 'Each image must be 5MB or smaller',
    imageUploadFailed: 'Note saved, but image upload failed'
  }
  return (locale.value === 'en-US' ? en : zh)[key] || key
}
</script>
