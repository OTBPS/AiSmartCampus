<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>{{ $t('profile.title') }}</h1>
      </div>
    </div>

    <div class="card-grid profile-grid">
      <section class="table-panel ai-history-panel">
        <div class="profile-table-head">
          <div class="profile-head-copy">
            <h3>{{ $t('profile.aiHistory') }}</h3>
            <span>{{ $t('profile.aiHistoryClickTip') }}</span>
          </div>
          <el-button size="small" type="danger" plain :disabled="!aiLogs.length" @click="confirmClearAiLogs">
            {{ $t('profile.clearAiHistory') }}
          </el-button>
        </div>
        <div v-if="aiLogs.length" class="ai-history-list">
          <button
            v-for="item in aiLogs"
            :key="item.id"
            class="ai-history-item"
            type="button"
            @click="reuseAiQuestion(item)"
          >
            <div>
              <strong>{{ item.question }}</strong>
              <span>{{ formatTime(item.createdAt) }}</span>
            </div>
            <el-tag size="small" effect="plain">{{ intentLabel(item.intent) }}</el-tag>
          </button>
        </div>
        <el-empty v-else :description="$t('profile.noAiHistory')" :image-size="92" />
      </section>
      <section class="table-panel profile-feedback-panel">
        <div class="profile-table-head">
          <h3>{{ $t('profile.myFeedback') }}</h3>
          <span>{{ $t('profile.feedbackClickTip') }}</span>
        </div>
        <el-table
          :data="feedback"
          height="100%"
          row-class-name="clickable-feedback-row"
          @row-click="openFeedbackDetail"
        >
          <el-table-column prop="type" :label="$t('common.type')" width="140" />
          <el-table-column prop="content" :label="$t('common.content')" />
          <el-table-column :label="$t('common.status')" width="120">
            <template #default="{ row }">
              {{ feedbackStatusLabel(row.status) }}
            </template>
          </el-table-column>
        </el-table>
      </section>
      <section class="table-panel profile-notes-panel">
        <div class="profile-table-head">
          <div class="profile-head-copy">
            <h3>{{ localText('discoverNotes') }}</h3>
            <span>{{ localText('notesTip') }}</span>
          </div>
        </div>
        <el-radio-group v-model="noteTab" class="profile-note-tabs">
          <el-radio-button value="mine">{{ localText('myPosts') }}</el-radio-button>
          <el-radio-button value="favorites">{{ localText('myFavorites') }}</el-radio-button>
        </el-radio-group>
        <div v-if="profileNotes.length" class="profile-note-list">
          <button
            v-for="item in profileNotes"
            :key="item.id"
            class="profile-note-item"
            type="button"
            @click="router.push(`/discover/${item.id}`)"
          >
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.poiName }} · {{ formatTime(item.createdAt) }}</span>
            </div>
            <div class="profile-note-metrics">
              <span><el-icon><Star /></el-icon>{{ item.likeCount }}</span>
              <span><el-icon><CollectionTag /></el-icon>{{ item.favoriteCount }}</span>
            </div>
          </button>
        </div>
        <el-empty v-else :description="localText('noNotes')" :image-size="92" />
      </section>
    </div>

    <el-dialog v-model="detailVisible" :title="$t('profile.feedbackDetail')" width="520px">
      <div v-if="selectedFeedback" class="feedback-detail">
        <div>
          <span>{{ $t('common.type') }}</span>
          <strong>{{ selectedFeedback.type }}</strong>
        </div>
        <div>
          <span>{{ $t('common.status') }}</span>
          <strong>{{ feedbackStatusLabel(selectedFeedback.status) }}</strong>
        </div>
        <div>
          <span>{{ $t('profile.submittedAt') }}</span>
          <strong>{{ formatTime(selectedFeedback.createdAt) }}</strong>
        </div>
        <div>
          <span>{{ $t('profile.reviewedAt') }}</span>
          <strong>{{ selectedFeedback.reviewedAt ? formatTime(selectedFeedback.reviewedAt) : $t('profile.notReviewed') }}</strong>
        </div>
        <section>
          <span>{{ $t('common.content') }}</span>
          <p>{{ selectedFeedback.content || $t('common.noRemark') }}</p>
        </section>
        <section>
          <span>{{ $t('profile.adminReviewNote') }}</span>
          <p>{{ selectedFeedback.reviewNote || $t('profile.noReviewNote') }}</p>
        </section>
      </div>
      <template #footer>
        <el-button type="primary" @click="detailVisible = false">{{ $t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppShell from '../../components/AppShell.vue'
import { aiApi, discoverApi, feedbackApi } from '../../api/modules'

const { t, locale } = useI18n()
const router = useRouter()
const feedback = ref([])
const aiLogs = ref([])
const myNotes = ref([])
const favoriteNotes = ref([])
const noteTab = ref('mine')
const detailVisible = ref(false)
const selectedFeedback = ref(null)
const profileNotes = computed(() => (noteTab.value === 'favorites' ? favoriteNotes.value : myNotes.value))
onMounted(loadProfileData)

async function loadProfileData() {
  const [feedbackItems, aiLogItems, mineItems, favoriteItems] = await Promise.all([
    feedbackApi.mine(),
    aiApi.mineLogs(),
    discoverApi.mine(),
    discoverApi.favorites()
  ])
  feedback.value = feedbackItems
  aiLogs.value = aiLogItems
  myNotes.value = mineItems
  favoriteNotes.value = favoriteItems
}

function openFeedbackDetail(row) {
  selectedFeedback.value = row
  detailVisible.value = true
}

function feedbackStatusLabel(status) {
  return {
    PENDING: t('common.pending'),
    APPROVED: t('common.approved'),
    REJECTED: t('common.rejected')
  }[status] || status
}

function intentLabel(intent) {
  return t(`labels.intent.${intent}`, t('labels.intent.unknown'))
}

function reuseAiQuestion(row) {
  const draft = row?.question?.trim()
  if (!draft) return
  router.push({ path: '/map-chat', query: { draft } })
}

async function confirmClearAiLogs() {
  if (!aiLogs.value.length) return
  try {
    await ElMessageBox.confirm(t('profile.clearAiHistoryMessage'), t('profile.clearAiHistoryTitle'), {
      confirmButtonText: t('common.yes'),
      cancelButtonText: t('common.no'),
      type: 'warning'
    })
    await aiApi.clearMineLogs()
    aiLogs.value = []
    ElMessage.success(t('profile.clearAiHistorySuccess'))
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || t('common.unknown'))
    }
  }
}

function formatTime(value) {
  if (!value) return t('common.unknown')
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString(locale.value === 'en-US' ? 'en-US' : 'zh-CN')
}

function localText(key) {
  const zh = {
    discoverNotes: '我的发现',
    notesTip: '查看自己发布或收藏的 note',
    myPosts: '我的发布',
    myFavorites: '我的收藏',
    noNotes: '暂无发现 note'
  }
  const en = {
    discoverNotes: 'My Discover',
    notesTip: 'Published and saved notes',
    myPosts: 'My posts',
    myFavorites: 'Saved',
    noNotes: 'No discover notes yet'
  }
  return (locale.value === 'en-US' ? en : zh)[key] || key
}
</script>
