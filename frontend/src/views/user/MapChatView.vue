<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>{{ $t('map.title') }}</h1>
      </div>
    </div>

    <div class="workbench">
      <section class="panel ai-panel">
        <div class="panel-pad">
          <el-input
            v-model="question"
            type="textarea"
            :rows="3"
            :placeholder="$t('map.placeholder')"
          />
          <div class="quick-grid">
            <el-button v-for="item in quickPrompts" :key="item" @click="ask(item)">{{ item }}</el-button>
          </div>
        </div>

        <div ref="messagesRef" class="messages">
          <article v-for="message in messages" :key="message.id" class="message" :class="message.role">
            <div class="message-meta">{{ message.role === 'user' ? $t('map.user') : $t('map.assistant') }}</div>
            <div class="bubble">{{ message.content }}</div>
          </article>

          <div v-if="lastResponse" class="result-list">
            <el-alert :title="$t('map.intent', { intent: intentLabel(lastResponse.intent) })" type="success" :closable="false" />
            <div class="ai-action-board">
              <div>
                <strong>{{ $t('map.mapActions') }}</strong>
                <div class="action-tags">
                  <el-tag v-for="action in mapActions" :key="action.key" size="small" effect="plain">
                    {{ action.label }}
                  </el-tag>
                </div>
              </div>
              <div>
                <strong>{{ $t('map.toolCalls') }}</strong>
                <div class="action-tags">
                  <el-tag v-for="tool in toolCalls" :key="tool.key" size="small" type="info" effect="plain">
                    {{ tool.label }}
                  </el-tag>
                </div>
              </div>
              <p v-if="routeSummary" class="route-summary">{{ routeSummary }}</p>
            </div>
            <button
              v-for="poi in resultPois"
              :key="poi.id"
              class="poi-card"
              :class="{ active: selectedPoi?.id === poi.id }"
              type="button"
              @click="selectPoi(poi)"
            >
              <div class="poi-card-title">
                <span>{{ poi.name }}</span>
                <el-tag size="small">{{ poi.category }}</el-tag>
              </div>
              <p>{{ poi.locationText }} · {{ poi.tags }}</p>
            </button>
          </div>
        </div>

        <div class="panel-pad">
          <el-button type="primary" :loading="loading" style="width: 100%" @click="ask(question)">{{ $t('map.send') }}</el-button>
        </div>
      </section>

      <section class="map-column">
        <CampusMap
          :pois="displayedPois"
          :highlighted-ids="highlightedIds"
          :selected-poi-id="selectedPoi?.id"
          :route-action="routeAction"
          @select="selectPoi"
        />
        <section v-if="selectedPoi" class="panel detail-panel">
          <div>
            <h3>{{ selectedPoi.name }}</h3>
            <p>{{ selectedPoi.locationText }} · {{ selectedPoi.openStatus }} · {{ selectedPoi.tags }}</p>
            <p>{{ selectedPoi.remark }}</p>
            <p v-if="aiContextNote" class="ai-context-note">{{ aiContextNote }}</p>
          </div>
          <div>
            <el-button type="primary" @click="feedbackVisible = true">{{ $t('map.submitFeedback') }}</el-button>
            <el-button @click="simulateRoute">{{ $t('map.routeFallback') }}</el-button>
          </div>
        </section>
      </section>
    </div>

    <el-dialog v-model="feedbackVisible" :title="$t('map.feedbackTitle')" width="480px">
      <el-form label-position="top">
        <el-form-item :label="$t('map.feedbackType')">
          <el-select v-model="feedback.type" style="width: 100%">
            <el-option :label="$t('map.feedbackInfoError')" value="INFO_ERROR" />
            <el-option :label="$t('map.feedbackTempClosed')" value="TEMP_CLOSED" />
            <el-option :label="$t('map.feedbackCoordinateError')" value="COORDINATE_ERROR" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('map.feedbackContent')">
          <el-input v-model="feedback.content" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitFeedback">{{ $t('common.submit') }}</el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import AppShell from '../../components/AppShell.vue'
import CampusMap from '../../components/CampusMap.vue'
import { aiApi, feedbackApi, poiApi } from '../../api/modules'

const { t, locale } = useI18n()
const question = ref(t('map.quickStudy'))
const loading = ref(false)
const pois = ref([])
const selectedPoi = ref(null)
const lastResponse = ref(null)
const highlightedIds = ref([])
const messagesRef = ref(null)
const feedbackVisible = ref(false)
const feedback = reactive({ type: 'INFO_ERROR', content: '' })
const messages = ref([
  { id: 1, role: 'assistant', content: t('map.intro') }
])

const quickPrompts = computed(() => [t('map.quickLibrary'), t('map.quickPrint'), t('map.quickStudy'), t('map.quickRoute')])
const resultPois = computed(() => lastResponse.value?.pois || [])
const displayedPois = computed(() => {
  const merged = new Map()
  pois.value.forEach((poi) => merged.set(poi.id, poi))
  resultPois.value.forEach((poi) => merged.set(poi.id, poi))
  if (selectedPoi.value?.id) merged.set(selectedPoi.value.id, selectedPoi.value)
  return Array.from(merged.values())
})
const routeAction = computed(() => lastResponse.value?.mapActions?.find((item) => item.type === 'draw_route') || null)
const mapActions = computed(() => (lastResponse.value?.mapActions || []).map((action, index) => ({
  key: `${action.type}-${index}`,
  label: mapActionLabel(action)
})))
const toolCalls = computed(() => (lastResponse.value?.toolCalls || []).map((tool, index) => ({
  key: `${tool.tool}-${index}`,
  label: toolCallLabel(tool)
})))
const routeSummary = computed(() => {
  if (!routeAction.value) return ''
  const payload = routeAction.value.payload || {}
  const from = payload.from || routePoiName(0) || t('map.start')
  const to = payload.to || routePoiName((routeAction.value.poiIds?.length || 1) - 1) || t('map.end')
  const reason = payload.reason || t('map.fallbackReason')
  return t('map.routeSummary', { from, to, reason })
})
const aiContextNote = computed(() => {
  if (!lastResponse.value) return ''
  if (routeSummary.value) return routeSummary.value
  if (lastResponse.value.intent === 'recommend_place') return t('map.recommendationReason', { reply: lastResponse.value.reply })
  return t('map.aiAction', { reply: lastResponse.value.reply })
})

onMounted(loadPois)

watch(
  () => [messages.value.length, lastResponse.value?.intent, resultPois.value.length, mapActions.value.length],
  scrollMessagesToBottom,
  { flush: 'post' }
)

watch(locale, () => {
  if (!lastResponse.value && messages.value.length === 1) {
    messages.value = [{ id: 1, role: 'assistant', content: t('map.intro') }]
    question.value = t('map.quickStudy')
    scrollMessagesToBottom()
  }
})

async function loadPois() {
  pois.value = await poiApi.list({ enabledOnly: true, mapOnly: true, limit: 20 })
  selectedPoi.value = pois.value[0] || null
}

async function ask(text) {
  if (!text) return
  question.value = text
  messages.value.push({ id: Date.now(), role: 'user', content: text })
  scrollMessagesToBottom()
  loading.value = true
  try {
    const response = await aiApi.chat(text, locale.value)
    lastResponse.value = response
    messages.value.push({ id: Date.now() + 1, role: 'assistant', content: response.reply })
    applyMapActions(response)
    scrollMessagesToBottom()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function applyMapActions(response) {
  const highlight = response.mapActions?.find((item) => item.type === 'highlight_pois')
  highlightedIds.value = highlight?.poiIds || []
  const open = response.mapActions?.find((item) => item.type === 'open_poi_detail')
  const route = response.mapActions?.find((item) => item.type === 'draw_route')
  if (open?.poiId) {
    selectedPoi.value = displayedPois.value.find((item) => item.id === open.poiId) || response.pois?.[0] || selectedPoi.value
  } else if (route?.poiIds?.length) {
    const destinationId = route.poiIds[route.poiIds.length - 1]
    selectedPoi.value = displayedPois.value.find((item) => item.id === destinationId) || response.pois?.[response.pois.length - 1] || selectedPoi.value
  } else if (response.pois?.length) {
    selectedPoi.value = response.pois[0]
  }
}

function selectPoi(poi) {
  selectedPoi.value = poi
  if (!highlightedIds.value.includes(poi.id)) {
    highlightedIds.value = [poi.id]
  }
}

function simulateRoute() {
  if (!selectedPoi.value) return
  messages.value.push({
    id: Date.now(),
    role: 'assistant',
    content: t('map.selectedRouteTarget', { name: selectedPoi.value.name })
  })
  scrollMessagesToBottom()
}

async function submitFeedback() {
  if (!selectedPoi.value || !feedback.content) {
    ElMessage.warning(t('map.feedbackRequired'))
    return
  }
  await feedbackApi.submit({ poiId: selectedPoi.value.id, type: feedback.type, content: feedback.content })
  ElMessage.success(t('map.feedbackSuccess'))
  feedback.content = ''
  feedbackVisible.value = false
}

function mapActionLabel(action) {
  if (action.type === 'highlight_pois') return t('map.highlightPois', { count: action.poiIds?.length || 0 })
  if (action.type === 'open_poi_detail') return t('map.openDetail', { id: action.poiId })
  if (action.type === 'draw_route') return t('map.drawRoute', { mode: action.routeMode || 'fallback' })
  return action.type
}

function toolCallLabel(tool) {
  if (tool.tool === 'searchPoi') return t('map.searchPoi', { keyword: tool.arguments?.keyword || '' })
  if (tool.tool === 'searchPoiByTags') return t('map.searchPoiByTags')
  if (tool.tool === 'planCampusRouteFallback') return t('map.planRouteFallback')
  return tool.tool
}

function intentLabel(intent) {
  return t(`labels.intent.${intent}`, intent)
}

function routePoiName(index) {
  const id = routeAction.value?.poiIds?.[index]
  return resultPois.value.find((poi) => poi.id === id)?.name || displayedPois.value.find((poi) => poi.id === id)?.name
}

function scrollMessagesToBottom() {
  nextTick(() => {
    const el = messagesRef.value
    if (!el) return
    el.scrollTop = el.scrollHeight
  })
}
</script>
