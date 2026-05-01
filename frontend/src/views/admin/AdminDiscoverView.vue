<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>{{ $t('admin.discoverManageTitle') }}</h1>
        <p>{{ $t('admin.discoverManageSubtitle') }}</p>
      </div>
      <el-button type="primary" @click="openCreate">{{ $t('admin.addCard') }}</el-button>
    </div>

    <section class="table-panel">
      <el-table :data="posts" height="620">
        <el-table-column prop="title" :label="$t('common.title')" />
        <el-table-column prop="category" :label="$t('common.type')" width="120" />
        <el-table-column prop="poiId" :label="$t('admin.relatedPoiId')" width="120" />
        <el-table-column :label="$t('common.status')" width="120">
          <template #default="{ row }">
            {{ discoverStatusLabel(row.status) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.action')" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">{{ $t('common.edit') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="form.id ? $t('admin.editCard') : $t('admin.createCard')" width="560px">
      <el-form label-position="top">
        <el-form-item :label="$t('common.title')"><el-input v-model="form.title" /></el-form-item>
        <el-form-item :label="$t('common.summary')"><el-input v-model="form.summary" type="textarea" /></el-form-item>
        <el-form-item :label="$t('common.type')"><el-input v-model="form.category" placeholder="STUDY / ROUTE / SERVICE" /></el-form-item>
        <el-form-item :label="$t('admin.relatedPoiId')"><el-input-number v-model="form.poiId" :min="1" /></el-form-item>
        <el-form-item :label="$t('common.status')">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :label="$t('common.published')" value="PUBLISHED" />
            <el-option :label="$t('common.hidden')" value="HIDDEN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="save">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import AppShell from '../../components/AppShell.vue'
import { discoverApi } from '../../api/modules'

const { t } = useI18n()
const posts = ref([])
const visible = ref(false)
const form = reactive(emptyForm())

onMounted(load)

function emptyForm() {
  return { id: null, title: '', summary: '', category: 'STUDY', poiId: null, coverUrl: '', status: 'PUBLISHED' }
}

async function load() {
  posts.value = await discoverApi.adminPosts()
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
  if (form.id) await discoverApi.update(form.id, form)
  else await discoverApi.create(form)
  ElMessage.success(t('admin.cardSaved'))
  visible.value = false
  load()
}

function discoverStatusLabel(status) {
  return {
    PUBLISHED: t('common.published'),
    HIDDEN: t('common.hidden')
  }[status] || status
}
</script>
