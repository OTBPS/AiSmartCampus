<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>{{ $t('profile.title') }}</h1>
        <p>{{ $t('profile.subtitle') }}</p>
      </div>
    </div>

    <div class="card-grid" style="grid-template-columns: 1fr 2fr">
      <section class="metric-card">
        <strong>{{ auth.user?.displayName }}</strong>
        <span>{{ auth.user?.username }} · {{ auth.user?.role }}</span>
      </section>
      <section class="table-panel">
        <h3>{{ $t('profile.myFeedback') }}</h3>
        <el-table :data="feedback" height="420">
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
  </AppShell>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AppShell from '../../components/AppShell.vue'
import { feedbackApi } from '../../api/modules'
import { useAuthStore } from '../../stores/auth'

const { t } = useI18n()
const auth = useAuthStore()
const feedback = ref([])
onMounted(async () => {
  feedback.value = await feedbackApi.mine()
})

function feedbackStatusLabel(status) {
  return {
    PENDING: t('common.pending'),
    APPROVED: t('common.approved'),
    REJECTED: t('common.rejected')
  }[status] || status
}
</script>
