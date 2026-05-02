<template>
  <AppShell>
    <div class="page-head discover-head">
      <div>
        <h1>{{ $t('discover.title') }}</h1>
      </div>
      <div class="discover-head-actions">
        <el-button class="discover-create-button" type="primary" @click="openCreate">{{ localText('create') }}</el-button>
        <el-radio-group v-model="sortMode" @change="load">
          <el-radio-button value="TIME">{{ localText('sortTime') }}</el-radio-button>
          <el-radio-button value="LIKES">{{ localText('sortLikes') }}</el-radio-button>
        </el-radio-group>
        <el-button @click="load">{{ $t('common.refresh') }}</el-button>
      </div>
    </div>

    <div v-if="posts.length" class="card-grid discover-note-grid">
      <article
        v-for="post in posts"
        :key="post.id"
        class="discover-card note-card"
        tabindex="0"
        role="button"
        @click="openPost(post)"
        @keydown.enter.prevent="openPost(post)"
      >
        <div class="note-card-top">
          <el-tag size="small" effect="plain">{{ post.category }}</el-tag>
          <span>{{ formatTime(post.createdAt) }}</span>
        </div>
        <h3>{{ post.title }}</h3>
        <p>{{ post.summary }}</p>
        <div class="note-place-line">
          <el-icon><Location /></el-icon>
          <span>{{ post.poiName || localText('unknownPlace') }}</span>
        </div>
        <div class="note-card-foot">
          <el-rate :model-value="post.rating || 0" disabled size="small" />
          <div class="note-actions" @click.stop>
            <button
              class="note-icon-action"
              :class="{ active: post.liked }"
              type="button"
              :aria-label="localText('like')"
              @click="toggleLike(post)"
            >
              <svg class="note-heart-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 21s-6.9-4.4-9.3-8.4C.8 9.3 2.4 5.3 6 4.5c2-.4 3.8.5 5 2 1.2-1.5 3-2.4 5-2 3.6.8 5.2 4.8 3.3 8.1C18.9 16.6 12 21 12 21Z" />
              </svg>
              <span>{{ post.likeCount }}</span>
            </button>
            <button
              class="note-icon-action favorite"
              :class="{ active: post.favorited }"
              type="button"
              :aria-label="localText('favorite')"
              @click="toggleFavorite(post)"
            >
              <el-icon><CollectionTag /></el-icon>
              <span>{{ post.favoriteCount }}</span>
            </button>
            <span class="note-comment-count">
              <el-icon><ChatDotRound /></el-icon>
              {{ post.commentCount }}
            </span>
          </div>
        </div>
      </article>
    </div>
    <el-empty v-else :description="localText('empty')" />

    <DiscoverPostEditor v-model="editorVisible" @saved="handleSaved" />
  </AppShell>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppShell from '../../components/AppShell.vue'
import DiscoverPostEditor from '../../components/DiscoverPostEditor.vue'
import { discoverApi } from '../../api/modules'

const { locale } = useI18n()
const router = useRouter()
const posts = ref([])
const sortMode = ref('TIME')
const editorVisible = ref(false)

onMounted(load)

async function load() {
  posts.value = await discoverApi.posts({ sort: sortMode.value })
}

function openCreate() {
  editorVisible.value = true
}

function openPost(post) {
  router.push(`/discover/${post.id}`)
}

function handleSaved(saved) {
  if (saved?.id) {
    router.push(`/discover/${saved.id}`)
    return
  }
  load()
}

async function toggleLike(post) {
  try {
    const updated = post.liked ? await discoverApi.unlike(post.id) : await discoverApi.like(post.id)
    syncPost(updated)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function toggleFavorite(post) {
  try {
    const updated = post.favorited ? await discoverApi.unfavorite(post.id) : await discoverApi.favorite(post.id)
    syncPost(updated)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function syncPost(updated) {
  const index = posts.value.findIndex((item) => item.id === updated.id)
  if (index >= 0) posts.value[index] = { ...posts.value[index], ...updated }
}

function formatTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleDateString(locale.value === 'en-US' ? 'en-US' : 'zh-CN', { month: 'short', day: 'numeric' })
}

function localText(key) {
  const zh = {
    sortTime: '按时间',
    sortLikes: '按喜欢',
    create: '发布 note',
    unknownPlace: '未知地点',
    like: '喜欢',
    favorite: '收藏',
    empty: '暂无发现 note'
  }
  const en = {
    sortTime: 'Latest',
    sortLikes: 'Most liked',
    create: 'Create note',
    unknownPlace: 'Unknown place',
    like: 'Like',
    favorite: 'Save',
    empty: 'No discover notes yet'
  }
  return (locale.value === 'en-US' ? en : zh)[key] || key
}
</script>
