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
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppShell from '../../components/AppShell.vue'
import { aiApi, feedbackApi } from '../../api/modules'

const { t } = useI18n()
const router = useRouter()
const feedback = ref([])
const aiLogs = ref([])
const detailVisible = ref(false)
const selectedFeedback = ref(null)
onMounted(loadProfileData)

async function loadProfileData() {
  const [feedbackItems, aiLogItems] = await Promise.all([feedbackApi.mine(), aiApi.mineLogs()])
  feedback.value = feedbackItems
  aiLogs.value = aiLogItems
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
  return date.toLocaleString()
}
</script>
