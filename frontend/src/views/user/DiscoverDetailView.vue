<template>
  <AppShell>
    <template v-if="post">
      <div class="page-head note-detail-head">
        <div>
          <el-button link type="primary" @click="handleBack">{{ backLabel }}</el-button>
          <h1>{{ post.title }}</h1>
          <p>{{ post.authorName }} · {{ formatTime(post.createdAt) }}</p>
        </div>
        <div class="note-detail-actions">
          <button
            class="note-icon-action"
            :class="{ active: post.liked }"
            type="button"
            :aria-label="localText('like')"
            @click="toggleLike"
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
            @click="toggleFavorite"
          >
            <el-icon><CollectionTag /></el-icon>
            <span>{{ post.favoriteCount }}</span>
          </button>
          <el-button v-if="post.owner" :icon="EditPen" @click="editorVisible = true">{{ localText('edit') }}</el-button>
          <el-button v-if="post.owner" :icon="Delete" type="danger" plain @click="confirmDelete">{{ localText('delete') }}</el-button>
        </div>
      </div>

      <article class="note-detail-layout">
        <section class="note-detail-main">
          <header class="note-section-head">
            <el-tag effect="plain">{{ post.category }}</el-tag>
            <el-rate :model-value="post.rating || 0" disabled />
          </header>
          <div v-if="noteImages.length" class="note-detail-gallery">
            <el-image
              v-for="(image, index) in noteImages"
              :key="image.id"
              :src="image.imageUrl"
              :alt="localText('imageAlt')"
              fit="cover"
              :preview-src-list="previewImageUrls"
              :initial-index="index"
              :preview-teleported="true"
            />
          </div>
          <p class="note-body">{{ post.body }}</p>
        </section>

        <aside class="note-place-card">
          <div>
            <span>{{ localText('place') }}</span>
            <h3>{{ post.poiName }}</h3>
            <p>{{ post.poiLocationText }}</p>
          </div>
          <div class="note-place-meta">
            <el-tag size="small">{{ post.poiCategory }}</el-tag>
            <span>{{ post.poiOpenStatus }}</span>
          </div>
          <p class="note-place-tags">{{ post.poiTags }}</p>
          <el-button type="primary" :icon="Location" @click="backToMap">{{ localText('backToMap') }}</el-button>
        </aside>
      </article>

      <section class="table-panel note-comments-panel">
        <div class="profile-table-head">
          <h3>{{ localText('comments') }}</h3>
          <span>{{ post.commentCount }} {{ localText('items') }}</span>
        </div>
        <div class="comment-compose">
          <el-input v-model="commentText" type="textarea" :rows="3" :placeholder="localText('commentPlaceholder')" />
          <el-button type="primary" :loading="commenting" @click="addComment">{{ localText('commentSubmit') }}</el-button>
        </div>
        <div v-if="post.comments?.length" class="comment-list">
          <article v-for="comment in post.comments" :key="comment.id" class="comment-item">
            <div class="comment-main">
              <div class="comment-meta">
                <strong>{{ comment.authorName }}</strong>
                <span>{{ formatTime(comment.createdAt) }}</span>
              </div>
              <p>{{ comment.content }}</p>
            </div>
            <el-button
              v-if="comment.owner"
              class="comment-delete-button"
              type="danger"
              @click="deleteComment(comment)"
            >
              {{ localText('delete') }}
            </el-button>
          </article>
        </div>
        <el-empty v-else :description="localText('noComments')" :image-size="90" />
      </section>

      <DiscoverPostEditor v-model="editorVisible" :post="post" @saved="handleSaved" />
    </template>
    <el-empty v-else :description="localText('notFound')" />
  </AppShell>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Delete, EditPen, Location } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '../../components/AppShell.vue'
import DiscoverPostEditor from '../../components/DiscoverPostEditor.vue'
import { discoverApi } from '../../api/modules'

const { locale } = useI18n()
const route = useRoute()
const router = useRouter()
const post = ref(null)
const editorVisible = ref(false)
const commentText = ref('')
const commenting = ref(false)
const cameFromMapChat = computed(() => route.query.from === 'map-chat')
const backLabel = computed(() => cameFromMapChat.value
  ? (locale.value === 'en-US' ? 'Back to AI Map' : '\u8fd4\u56de AI \u5730\u56fe')
  : localText('back'))
const noteImages = computed(() => post.value?.images || [])
const previewImageUrls = computed(() => noteImages.value.map((image) => image.imageUrl))

onMounted(load)
watch(() => route.params.id, load)

async function load() {
  post.value = await discoverApi.post(route.params.id)
}

async function toggleLike() {
  try {
    const updated = post.value.liked ? await discoverApi.unlike(post.value.id) : await discoverApi.like(post.value.id)
    mergePost(updated)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function toggleFavorite() {
  try {
    const updated = post.value.favorited ? await discoverApi.unfavorite(post.value.id) : await discoverApi.favorite(post.value.id)
    mergePost(updated)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function addComment() {
  const content = commentText.value.trim()
  if (!content) {
    ElMessage.warning(localText('commentRequired'))
    return
  }
  commenting.value = true
  try {
    const comment = await discoverApi.addComment(post.value.id, { content })
    post.value.comments = [...(post.value.comments || []), comment]
    post.value.commentCount += 1
    commentText.value = ''
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    commenting.value = false
  }
}

async function deleteComment(comment) {
  await discoverApi.deleteComment(comment.id)
  post.value.comments = post.value.comments.filter((item) => item.id !== comment.id)
  post.value.commentCount = Math.max(0, post.value.commentCount - 1)
}

async function confirmDelete() {
  try {
    await ElMessageBox.confirm(localText('deleteMessage'), localText('deleteTitle'), {
      confirmButtonText: localText('delete'),
      cancelButtonText: localText('cancel'),
      type: 'warning'
    })
    await discoverApi.remove(post.value.id)
    ElMessage.success(localText('deleted'))
    router.push(backTarget())
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message)
  }
}

function handleSaved(saved) {
  post.value = saved
}

function mergePost(updated) {
  post.value = { ...post.value, ...updated, comments: post.value.comments }
}

function backToMap() {
  router.push({ path: '/map-chat', query: { poiId: post.value.poiId } })
}

function handleBack() {
  router.push(backTarget())
}

function backTarget() {
  return cameFromMapChat.value ? '/map-chat' : '/discover'
}

function formatTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString(locale.value === 'en-US' ? 'en-US' : 'zh-CN')
}

function localText(key) {
  const zh = {
    back: '返回发现',
    like: '喜欢',
    favorite: '收藏',
    edit: '编辑',
    delete: '删除',
    cancel: '取消',
    place: '关联地点',
    backToMap: '回到地图查看地点',
    comments: '评论',
    items: '条',
    commentPlaceholder: '写下你的评论',
    commentSubmit: '发表评论',
    commentRequired: '请填写评论内容',
    noComments: '暂无评论',
    notFound: '未找到 note',
    deleteTitle: '删除 note',
    deleteMessage: '确定删除这条 note 吗？删除后不可恢复。',
    deleted: 'note 已删除',
    imageAlt: 'note 图片'
  }
  const en = {
    back: 'Back to Discover',
    like: 'Like',
    favorite: 'Save',
    edit: 'Edit',
    delete: 'Delete',
    cancel: 'Cancel',
    place: 'Place',
    backToMap: 'View Place on Map',
    comments: 'Comments',
    items: 'items',
    commentPlaceholder: 'Write a comment',
    commentSubmit: 'Post comment',
    commentRequired: 'Enter a comment',
    noComments: 'No comments yet',
    notFound: 'Note not found',
    deleteTitle: 'Delete note',
    deleteMessage: 'Delete this note? This cannot be undone.',
    deleted: 'Note deleted',
    imageAlt: 'Note image'
  }
  return (locale.value === 'en-US' ? en : zh)[key] || key
}
</script>
