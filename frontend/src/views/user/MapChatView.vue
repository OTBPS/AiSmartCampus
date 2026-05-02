<template>
  <AppShell>
    <div class="workbench map-chat-workbench">
      <section class="panel ai-panel">
        <div class="panel-pad">
          <el-input
            ref="questionInputRef"
            v-model="question"
            type="textarea"
            :rows="3"
            :placeholder="$t('map.placeholder')"
          />
          <div v-if="hasRouteDraft" class="route-draft">
            <div class="route-draft-main">
              <span class="route-draft-label">{{ localText('routeDraft') }}</span>
              <span class="route-chip origin">{{ localText('origin') }}: {{ routeDraft.origin?.name || localText('unset') }}</span>
              <span v-for="poi in routeDraft.waypoints" :key="poi.id" class="route-chip waypoint">{{ localText('via') }}: {{ poi.name }}</span>
              <span class="route-chip destination">{{ localText('destination') }}: {{ routeDraft.destination?.name || localText('unset') }}</span>
            </div>
            <div class="route-draft-actions">
              <el-button size="small" @click="writeRouteDraftToQuestion">{{ localText('writeToChat') }}</el-button>
              <el-button size="small" type="primary" :disabled="!routeDraftReady" @click="generateRouteFromDraft">{{ localText('generateRoute') }}</el-button>
              <el-button size="small" text @click="clearRouteDraft">{{ localText('clear') }}</el-button>
            </div>
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
              @click="selectPoi(poi, !routeAction)"
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
          :route-action="effectiveRouteAction"
          @select="selectPoi"
          @route-context="handleRouteContextAction"
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
import { useRoute } from 'vue-router'
import AppShell from '../../components/AppShell.vue'
import CampusMap from '../../components/CampusMap.vue'
import { aiApi, feedbackApi, poiApi } from '../../api/modules'

const { t, locale } = useI18n()
const route = useRoute()
const question = ref(t('map.quickStudy'))
const questionInputRef = ref(null)
const loading = ref(false)
const pois = ref([])
const selectedPoi = ref(null)
const isolateSelectedPoi = ref(false)
const lastResponse = ref(null)
const highlightedIds = ref([])
const routeDraft = reactive({ origin: null, destination: null, waypoints: [] })
const messagesRef = ref(null)
const feedbackVisible = ref(false)
const feedback = reactive({ type: 'INFO_ERROR', content: '' })
const messages = ref([
  { id: 1, role: 'assistant', content: t('map.intro') }
])

const resultPois = computed(() => lastResponse.value?.pois || [])
const routeDraftReady = computed(() => Boolean(routeDraft.origin?.id && routeDraft.destination?.id))
const hasRouteDraft = computed(() => Boolean(routeDraft.origin?.id || routeDraft.destination?.id || routeDraft.waypoints.length))
const displayedPois = computed(() => {
  if (isolateSelectedPoi.value && selectedPoi.value?.id) {
    return [selectedPoi.value]
  }
  if (routeAction.value?.poiIds?.length) {
    const routePois = routeAction.value.poiIds.map((id) => findPoiById(id)).filter(Boolean)
    if (routePois.length) return routePois
  }
  const merged = new Map()
  pois.value.forEach((poi) => merged.set(poi.id, poi))
  resultPois.value.forEach((poi) => merged.set(poi.id, poi))
  if (selectedPoi.value?.id) merged.set(selectedPoi.value.id, selectedPoi.value)
  if (routeDraft.origin?.id) merged.set(routeDraft.origin.id, routeDraft.origin)
  routeDraft.waypoints.forEach((poi) => merged.set(poi.id, poi))
  if (routeDraft.destination?.id) merged.set(routeDraft.destination.id, routeDraft.destination)
  return Array.from(merged.values())
})
const routeAction = computed(() => lastResponse.value?.mapActions?.find((item) => item.type === 'draw_route') || null)
const effectiveRouteAction = computed(() => (isolateSelectedPoi.value ? null : routeAction.value))
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
  const summary = t('map.routeSummary', { from, to, reason })
  const via = Array.isArray(payload.via) && payload.via.length ? `${localText('via')}: ${payload.via.join(' -> ')}` : ''
  return via ? `${summary} · ${via}` : summary
})
const aiContextNote = computed(() => {
  if (!lastResponse.value) return ''
  if (routeSummary.value) return routeSummary.value
  if (lastResponse.value.intent === 'recommend_place') return t('map.recommendationReason', { reply: lastResponse.value.reply })
  return t('map.aiAction', { reply: lastResponse.value.reply })
})

onMounted(async () => {
  await loadPois()
  await applyPoiFromRoute()
  applyDraftFromRoute()
})

watch(
  () => [messages.value.length, lastResponse.value?.intent, resultPois.value.length, mapActions.value.length],
  scrollMessagesToBottom,
  { flush: 'post' }
)

watch(locale, () => {
  if (!lastResponse.value && messages.value.length === 1 && !route.query.draft) {
    messages.value = [{ id: 1, role: 'assistant', content: t('map.intro') }]
    question.value = t('map.quickStudy')
    scrollMessagesToBottom()
  }
})

watch(
  () => route.query.draft,
  (value) => applyDraftFromRoute(value)
)

watch(
  () => route.query.poiId,
  (value) => applyPoiFromRoute(value)
)

async function loadPois() {
  pois.value = await poiApi.list({ enabledOnly: true, mapOnly: true, limit: 20 })
  selectedPoi.value = pois.value[0] || null
}

async function applyPoiFromRoute(value = route.query.poiId) {
  const raw = Array.isArray(value) ? value[0] : value
  const id = Number(raw)
  if (!Number.isFinite(id) || id <= 0) return
  try {
    let poi = findPoiById(id)
    if (!poi) {
      poi = await poiApi.get(id)
      if (poi?.id && !pois.value.some((item) => item.id === poi.id)) {
        pois.value = [...pois.value, poi]
      }
    }
    if (poi?.id) {
      selectedPoi.value = poi
      highlightedIds.value = [poi.id]
      isolateSelectedPoi.value = true
    }
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function applyDraftFromRoute(value = route.query.draft) {
  const draft = Array.isArray(value) ? value[0] : value
  if (!draft || typeof draft !== 'string') return
  question.value = draft.trim()
  nextTick(() => questionInputRef.value?.focus?.())
}

async function ask(text, routeContextOverride) {
  if (!text) return
  question.value = text
  messages.value.push({ id: Date.now(), role: 'user', content: text })
  scrollMessagesToBottom()
  loading.value = true
  try {
    isolateSelectedPoi.value = false
    const routeContext = routeContextOverride === undefined ? routeContextForMessage(text) : routeContextOverride
    const response = await aiApi.chat(text, locale.value, routeContext)
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
  isolateSelectedPoi.value = false
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

function selectPoi(poi, isolate = true) {
  selectedPoi.value = poi
  isolateSelectedPoi.value = isolate
  if (!highlightedIds.value.includes(poi.id)) {
    highlightedIds.value = [poi.id]
  }
}

function handleRouteContextAction({ action, poi }) {
  if (action === 'clear') {
    clearRouteDraft()
    return
  }
  if (!poi?.id) return
  if (action === 'origin') {
    routeDraft.origin = poi
    routeDraft.waypoints = routeDraft.waypoints.filter((item) => item.id !== poi.id)
    if (routeDraft.destination?.id === poi.id) routeDraft.destination = null
  }
  if (action === 'destination') {
    routeDraft.destination = poi
    routeDraft.waypoints = routeDraft.waypoints.filter((item) => item.id !== poi.id)
    if (routeDraft.origin?.id === poi.id) routeDraft.origin = null
  }
  if (action === 'waypoint' && routeDraft.origin?.id !== poi.id && routeDraft.destination?.id !== poi.id) {
    if (!routeDraft.waypoints.some((item) => item.id === poi.id)) {
      routeDraft.waypoints.push(poi)
    }
  }
  selectPoi(poi, false)
}

function clearRouteDraft() {
  routeDraft.origin = null
  routeDraft.destination = null
  routeDraft.waypoints = []
}

function writeRouteDraftToQuestion() {
  question.value = routeDraftText()
}

function generateRouteFromDraft() {
  if (!routeDraftReady.value) {
    ElMessage.warning(localText('routeDraftIncomplete'))
    return
  }
  const text = routeDraftText()
  question.value = text
  ask(text, buildRouteContext())
}

function routeContextForMessage(text) {
  return hasRouteDraft.value && looksLikeRouteText(text) ? buildRouteContext() : null
}

function buildRouteContext() {
  if (!hasRouteDraft.value) return null
  return {
    originPoiId: routeDraft.origin?.id || null,
    destinationPoiId: routeDraft.destination?.id || null,
    waypointPoiIds: routeDraft.waypoints.map((poi) => poi.id)
  }
}

function routeDraftText() {
  const origin = routeDraft.origin?.name || localText('unset')
  const destination = routeDraft.destination?.name || localText('unset')
  const via = routeDraft.waypoints.map((poi) => poi.name)
  if (locale.value === 'en-US') {
    return via.length
      ? `Go from ${origin} to ${destination} via ${via.join(', ')}`
      : `Go from ${origin} to ${destination}`
  }
  return via.length
    ? `\u4ece ${origin} \u51fa\u53d1\uff0c\u5230 ${destination}\uff0c\u9014\u7ecf ${via.join('\u3001')}`
    : `\u4ece ${origin} \u51fa\u53d1\uff0c\u5230 ${destination}`
}

function looksLikeRouteText(text) {
  const normalized = `${text || ''}`.toLowerCase().replace(/\s+/g, '')
  return ['from', 'to', 'go', 'route', 'directions', 'via', 'passby', '\u4ece', '\u5230', '\u53bb', '\u8def\u7ebf', '\u9014\u7ecf'].some((item) => normalized.includes(item))
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
  return findPoiById(id)?.name
}

function findPoiById(id) {
  if (!id) return null
  const pools = [resultPois.value, pois.value, routeDraft.waypoints, [routeDraft.origin, routeDraft.destination, selectedPoi.value]]
  return pools.flat().find((poi) => poi?.id === id) || null
}

function localText(key) {
  const zh = {
    routeDraft: '\u8def\u7ebf\u8349\u7a3f',
    origin: '\u8d77\u70b9',
    destination: '\u7ec8\u70b9',
    via: '\u9014\u7ecf',
    unset: '\u672a\u8bbe\u7f6e',
    writeToChat: '\u5199\u5165\u5bf9\u8bdd\u6846',
    generateRoute: '\u751f\u6210\u8def\u7ebf',
    clear: '\u6e05\u7a7a',
    routeDraftIncomplete: '\u8bf7\u5148\u8bbe\u7f6e\u8d77\u70b9\u548c\u7ec8\u70b9'
  }
  const en = {
    routeDraft: 'Route Draft',
    origin: 'Start',
    destination: 'End',
    via: 'Via',
    unset: 'Unset',
    writeToChat: 'Write to Chat',
    generateRoute: 'Generate Route',
    clear: 'Clear',
    routeDraftIncomplete: 'Set both start and end first'
  }
  return (locale.value === 'en-US' ? en : zh)[key] || key
}

function scrollMessagesToBottom() {
  nextTick(() => {
    const el = messagesRef.value
    if (!el) return
    el.scrollTop = el.scrollHeight
  })
}
</script>
