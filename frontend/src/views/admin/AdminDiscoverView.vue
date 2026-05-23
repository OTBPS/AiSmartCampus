<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>{{ $t('admin.discoverManageTitle') }}</h1>
      </div>
      <el-button @click="load">{{ $t('common.refresh') }}</el-button>
    </div>

    <section class="table-panel">
      <div class="admin-filter-bar">
        <el-input
          v-model="filters.title"
          clearable
          :placeholder="localText('titleSearchPlaceholder')"
          style="max-width: 300px"
        />
        <el-input
          v-model="filters.poi"
          clearable
          :placeholder="localText('poiSearchPlaceholder')"
          style="max-width: 260px"
        />
        <span class="filter-count">{{ $t('common.itemsCount', { current: filteredPosts.length, total: posts.length }) }}</span>
      </div>

      <el-table :data="filteredPosts" height="620">
        <el-table-column prop="title" :label="$t('common.title')" min-width="220" />
        <el-table-column :label="localText('images')" width="110">
          <template #default="{ row }">
            <div v-if="postCover(row)" class="admin-note-image">
              <img :src="postCover(row)" :alt="row.title" />
              <span>{{ row.images?.length || 1 }}</span>
            </div>
            <span v-else class="admin-note-no-image">-</span>
          </template>
        </el-table-column>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import AppShell from '../../components/AppShell.vue'
import { discoverApi } from '../../api/modules'

const { t, locale } = useI18n()
const posts = ref([])
const filters = reactive({
  title: '',
  poi: ''
})

const filteredPosts = computed(() => {
  const titleKeyword = normalizeSearchText(filters.title)
  const poiKeyword = normalizeSearchText(filters.poi)
  return posts.value.filter((post) => {
    const matchesTitle = !titleKeyword || normalizeSearchText(post.title).includes(titleKeyword)
    const matchesPoi = !poiKeyword || normalizeSearchText(post.poiName).includes(poiKeyword)
    return matchesTitle && matchesPoi
  })
})

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

function postCover(post) {
  return post.images?.[0]?.imageUrl || post.coverUrl || ''
}

function normalizeSearchText(value) {
  return `${value || ''}`.toLowerCase().replace(/\s+/g, '')
}

function localText(key) {
  const zh = {
    author: '作者',
    images: '图片',
    stats: '喜欢/收藏/评论',
    titleSearchPlaceholder: '按标题关键词搜索',
    poiSearchPlaceholder: '按地点搜索',
    view: '查看',
    delete: '删除',
    deleteTitle: '删除 note',
    deleteMessage: '管理员只能删除 note，确定删除这条内容吗？',
    deleted: 'note 已删除'
  }
  const en = {
    author: 'Author',
    images: 'Images',
    stats: 'Likes/Saves/Comments',
    titleSearchPlaceholder: 'Search title keywords',
    poiSearchPlaceholder: 'Search related POI',
    view: 'View',
    delete: 'Delete',
    deleteTitle: 'Delete note',
    deleteMessage: 'Admins can only delete notes. Delete this note?',
    deleted: 'Note deleted'
  }
  return (locale.value === 'en-US' ? en : zh)[key] || key
}
</script>
