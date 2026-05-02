<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>{{ $t('admin.discoverManageTitle') }}</h1>
        <p>{{ $t('admin.discoverManageSubtitle') }}</p>
      </div>
      <el-button @click="load">{{ $t('common.refresh') }}</el-button>
    </div>

    <section class="table-panel">
      <el-table :data="posts" height="620">
        <el-table-column prop="title" :label="$t('common.title')" min-width="220" />
        <el-table-column prop="authorName" :label="localText('author')" width="130" />
        <el-table-column prop="poiName" :label="$t('admin.relatedPoi')" min-width="180" />
        <el-table-column :label="localText('stats')" width="150">
          <template #default="{ row }">
            <span class="admin-note-stats">{{ row.likeCount }} / {{ row.favoriteCount }} / {{ row.commentCount }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.status')" width="120">
          <template #default="{ row }">
            {{ discoverStatusLabel(row.status) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.action')" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/discover/${row.id}`)">{{ localText('view') }}</el-button>
            <el-button link type="danger" @click="confirmDelete(row)">{{ localText('delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </AppShell>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import AppShell from '../../components/AppShell.vue'
import { discoverApi } from '../../api/modules'

const { t, locale } = useI18n()
const posts = ref([])

onMounted(load)

async function load() {
  posts.value = await discoverApi.adminPosts()
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm(localText('deleteMessage'), localText('deleteTitle'), {
      confirmButtonText: localText('delete'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
    await discoverApi.adminDelete(row.id)
    posts.value = posts.value.filter((item) => item.id !== row.id)
    ElMessage.success(localText('deleted'))
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message)
  }
}

function discoverStatusLabel(status) {
  return {
    PUBLISHED: t('common.published'),
    HIDDEN: t('common.hidden')
  }[status] || status
}

function localText(key) {
  const zh = {
    author: '作者',
    stats: '喜欢/收藏/评论',
    view: '查看',
    delete: '删除',
    deleteTitle: '删除 note',
    deleteMessage: '管理员只能删除 note，确定删除这条内容吗？',
    deleted: 'note 已删除'
  }
  const en = {
    author: 'Author',
    stats: 'Likes/Saves/Comments',
    view: 'View',
    delete: 'Delete',
    deleteTitle: 'Delete note',
    deleteMessage: 'Admins can only delete notes. Delete this note?',
    deleted: 'Note deleted'
  }
  return (locale.value === 'en-US' ? en : zh)[key] || key
}
</script>
