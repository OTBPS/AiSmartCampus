<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>发现内容管理</h1>
        <p>只管理地点经验卡片，避免变成普通社区后台。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增卡片</el-button>
    </div>

    <section class="table-panel">
      <el-table :data="posts" height="620">
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="category" label="类型" width="120" />
        <el-table-column prop="poiId" label="关联 POI" width="120" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="form.id ? '编辑卡片' : '新增卡片'" width="560px">
      <el-form label-position="top">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="摘要"><el-input v-model="form.summary" type="textarea" /></el-form-item>
        <el-form-item label="类型"><el-input v-model="form.category" placeholder="STUDY / ROUTE / SERVICE" /></el-form-item>
        <el-form-item label="关联 POI ID"><el-input-number v-model="form.poiId" :min="1" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="发布" value="PUBLISHED" />
            <el-option label="隐藏" value="HIDDEN" />
          </el-select>
        </el-form-item>
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
import { discoverApi } from '../../api/modules'

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
  ElMessage.success('卡片已保存')
  visible.value = false
  load()
}
</script>

