<template>
  <AppShell>
    <div class="workbench map-chat-workbench">
      <section class="panel ai-panel">
        <div class="panel-pad">
          <div class="map-chat-input-head">
            <div class="map-chat-title-group">
              <el-button
                class="map-chat-back-button"
                circle
                plain
                :aria-label="localText('back')"
                :title="localText('back')"
                @click="handleBackNavigation"
              >
                <el-icon><ArrowLeft /></el-icon>
              </el-button>
              <span>{{ localText('chatPanel') }}</span>
            </div>
            <el-button size="small" plain @click="clearPageChat">{{ localText('clearChat') }}</el-button>
          </div>
          <div class="route-mode-switch">
            <span>{{ localText('routeMode') }}</span>
            <el-radio-group v-model="routeMode" size="small" :aria-label="localText('routeMode')">
              <el-radio-button value="walking">{{ localText('walkMode') }}</el-radio-button>
              <el-radio-button value="cycling">{{ localText('bikeMode') }}</el-radio-button>
            </el-radio-group>
          </div>
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

          <div v-if="lastResponse && !isSmallTalkResponse" class="result-list">
            <div v-if="routeSummary || showShelterCandidates" class="ai-action-board">
              <p v-if="routeSummary" class="route-summary">{{ routeSummary }}</p>
              <div v-if="showShelterCandidates" class="shelter-choice-panel">
                <div class="shelter-choice-head">
                  <strong>{{ localText('shelterCandidates') }}</strong>
                  <span>{{ selectedShelterCandidateIds.length }}/2</span>
                </div>
                <p>{{ localText('shelterCandidateHint') }}</p>
                <div class="shelter-choice-list">
                  <button
                    v-for="candidate in shelterCandidates"
                    :key="candidate.poiId"
                    class="shelter-choice-item"
                    :class="{ selected: selectedShelterCandidateIds.includes(candidate.poiId) }"
                    type="button"
                    :aria-pressed="selectedShelterCandidateIds.includes(candidate.poiId)"
                    @click="toggleShelterCandidate(candidate.poiId)"
                  >
                    <span>{{ candidate.name }}</span>
                    <small>
                      {{ localText('fromRoute') }} {{ Math.round(candidate.distanceFromRouteMeters || 0) }}m ·
                      {{ localText('fromStart') }} {{ Math.round(candidate.distanceFromStartMeters || 0) }}m
                    </small>
                  </button>
                </div>
                <div class="shelter-choice-actions">
                  <el-button size="small" @click="dismissShelterCandidates">{{ localText('skipShelters') }}</el-button>
                  <el-button
                    size="small"
                    type="primary"
                    :disabled="!selectedShelterCandidateIds.length"
                    @click="applyShelterCandidates"
                  >
                    {{ localText('addShelters') }}
                  </el-button>
                </div>
              </div>
            </div>
            <button
              v-for="note in noteResults"
              :key="note.id"
              class="ai-note-card"
              type="button"
              @click="openNoteFromChat(note)"
            >
              <span>{{ note.title }}</span>
              <small v-if="note.poiName">{{ note.poiName }}</small>
            </button>
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
          :route-mode="routeMode"
          :current-location="currentLocation"
          :location-status="locationStatus"
          :location-message="locationMessage"
          @select="selectPoi"
          @route-context="handleRouteContextAction"
          @locate-current="requestCurrentLocation"
          @reset="resetMapView"
        />
        <section v-if="selectedPoi" class="panel detail-panel">
          <div class="detail-copy">
            <div class="detail-head">
              <div>
                <span class="detail-kicker">{{ selectedPoi.category || localText('campusPlace') }}</span>
                <h3>{{ selectedPoi.name }}</h3>
              </div>
              <el-tag size="small" type="success" effect="plain">{{ selectedPoiDetail.status }}</el-tag>
            </div>
            <p class="detail-location">{{ selectedPoi.locationText }}</p>
            <div class="detail-info-grid">
              <section v-if="selectedPoiDetail.intro" class="detail-info-item">
                <span class="detail-info-label">{{ localText('introLabel') }}</span>
                <p>{{ selectedPoiDetail.intro }}</p>
              </section>
              <section v-if="selectedPoiDetail.hours" class="detail-info-item">
                <span class="detail-info-label">{{ localText('hoursLabel') }}</span>
                <p>{{ selectedPoiDetail.hours }}</p>
              </section>
            </div>
            <div v-if="selectedPoiDetail.tags.length" class="detail-tags" :aria-label="localText('tagsLabel')">
              <span v-for="tag in selectedPoiDetail.tags" :key="tag">{{ tag }}</span>
            </div>
            <p v-if="recommendationNote" class="ai-context-note">
              <strong>{{ aiContextLabel }}</strong>
              {{ recommendationNote }}
            </p>
          </div>
          <aside class="detail-media">
            <img
              :src="selectedPoiImage.url"
              :alt="selectedPoiImage.alt"
              :data-image-key="selectedPoiImage.key"
              loading="lazy"
              @error="handlePoiImageError"
            >
            <el-button type="primary" class="detail-feedback-button" @click="feedbackVisible = true">{{ $t('map.submitFeedback') }}</el-button>
          </aside>
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
import { useRoute, useRouter } from 'vue-router'
import AppShell from '../../components/AppShell.vue'
import CampusMap from '../../components/CampusMap.vue'
import { aiApi, feedbackApi, poiApi } from '../../api/modules'
import { loadAmap } from '../../utils/amapLoader'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const MAP_CHAT_STATE_KEY = 'smartcampus.mapChat.session.v1'
const question = ref(t('map.quickStudy'))
const questionInputRef = ref(null)
const loading = ref(false)
const pois = ref([])
const selectedPoi = ref(null)
const isolateSelectedPoi = ref(false)
const lastResponse = ref(null)
const highlightedIds = ref([])
const selectedShelterCandidateIds = ref([])
const shelterCandidatesDismissed = ref(false)
const chatStateReady = ref(false)
const routeDraft = reactive({ origin: null, destination: null, waypoints: [] })
const messagesRef = ref(null)
const feedbackVisible = ref(false)
const feedback = reactive({ type: 'INFO_ERROR', content: '' })
const messages = ref(defaultMessages())
const failedPoiImageKeys = ref([])
const routeMode = ref('walking')
const currentLocation = ref(null)
const locationStatus = ref('idle')
const locationMessage = ref('')

const CAMPUS_IMAGE = {
  key: 'campus',
  url: 'https://www.nuist.edu.cn/images/nav-pic.jpg',
  alt: 'NUIST campus'
}

const LIBRARY_IMAGE = {
  key: 'library',
  url: 'https://lib.nuist.edu.cn/img/list_banner.jpg',
  alt: 'NUIST Library'
}

const WEATHER_IMAGE = {
  key: 'weather',
  url: 'https://www.nuist.edu.cn/images/s7-tlbg.png',
  alt: 'NUIST weather campus view'
}

const resultPois = computed(() => lastResponse.value?.pois || [])
const chatResultPois = computed(() => uniquePois(resultPois.value))
const noteResults = computed(() => lastResponse.value?.notes || [])
const isSmallTalkResponse = computed(() => lastResponse.value?.intent === 'small_talk')
const isNoteResponse = computed(() => lastResponse.value?.intent === 'find_note')
const routeDraftReady = computed(() => Boolean(routeDraft.origin?.id && routeDraft.destination?.id))
const hasRouteDraft = computed(() => Boolean(routeDraft.origin?.id || routeDraft.destination?.id || routeDraft.waypoints.length))
const displayedPois = computed(() => {
  if (isolateSelectedPoi.value && selectedPoi.value?.id) {
    return [selectedPoi.value]
  }
  if (routeAction.value?.poiIds?.length) {
    const routePois = routeAction.value.poiIds.map((id) => findPoiById(id)).filter(Boolean)
    const candidates = shelterCandidatePois()
    if (routePois.length) return uniquePois([...routePois, ...candidates])
  }
  if (chatResultPois.value.length) {
    return chatResultPois.value
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
const shelterCandidates = computed(() => {
  const payload = routeAction.value?.payload || {}
  const candidates = Array.isArray(payload.shelterCandidates) ? payload.shelterCandidates : []
  return candidates
    .map((candidate) => {
      const poi = findPoiById(candidate.poiId)
      return {
        ...candidate,
        name: candidate.name || poi?.name || `POI ${candidate.poiId}`,
        poi
      }
    })
    .filter((candidate) => candidate.poiId && candidate.poi && !routeAction.value?.poiIds?.includes(candidate.poiId))
})
const showShelterCandidates = computed(() => {
  const payload = routeAction.value?.payload || {}
  return Boolean(
    payload.weatherShelterSuggested
    && !payload.weatherAdjusted
    && shelterCandidates.value.length
    && !shelterCandidatesDismissed.value
  )
})
const routeSummary = computed(() => {
  if (!routeAction.value) return ''
  const payload = routeAction.value.payload || {}
  const from = payload.from || routePoiName(0) || t('map.start')
  const to = payload.to || routePoiName((routeAction.value.poiIds?.length || 1) - 1) || t('map.end')
  const reason = payload.reason || t('map.fallbackReason')
  const summary = t('map.routeSummary', { from, to, reason })
  const via = Array.isArray(payload.via) && payload.via.length ? `${localText('via')}: ${payload.via.join(' -> ')}` : ''
  if (payload.weatherAdjusted) {
    const baseSummary = via ? `${summary} \u8def ${via}` : summary
    const weatherReason = payload.weatherReason ? `${payload.weatherReason} ` : ''
    return `${baseSummary} \u8def ${weatherReason}${localText('weatherShelterAdded')}`
  }
  if (payload.weatherShelterSuggested) {
    const baseSummary = via ? `${summary} \u8def ${via}` : summary
    const weatherReason = payload.weatherReason ? `${payload.weatherReason} ` : ''
    return `${baseSummary} \u8def ${weatherReason}${localText('weatherShelterSuggested')}`
  }
  return via ? `${summary} · ${via}` : summary
})
const aiContextNote = computed(() => {
  if (!lastResponse.value) return ''
  if (isSmallTalkResponse.value) return ''
  if (isNoteResponse.value) return ''
  if (routeSummary.value) return routeSummary.value
  if (lastResponse.value.intent === 'recommend_place') return t('map.recommendationReason', { reply: lastResponse.value.reply })
  return t('map.aiAction', { reply: lastResponse.value.reply })
})
const selectedPoiDetail = computed(() => buildPoiDetail(selectedPoi.value))
const selectedPoiImage = computed(() => {
  const image = choosePoiImage(selectedPoi.value)
  if (image.key !== CAMPUS_IMAGE.key && failedPoiImageKeys.value.includes(image.key)) {
    return CAMPUS_IMAGE
  }
  return image
})
const recommendationNote = computed(() => stripContextPrefix(aiContextNote.value))
const aiContextLabel = computed(() => (routeSummary.value ? localText('routeContext') : localText('recommendReason')))

onMounted(async () => {
  restoreChatState()
  await loadPois()
  await applyPoiFromRoute()
  applyDraftFromRoute()
  chatStateReady.value = true
  persistChatState()
  requestCurrentLocation()
})

watch(
  () => [messages.value.length, lastResponse.value?.intent, resultPois.value.length, noteResults.value.length, routeSummary.value],
  scrollMessagesToBottom,
  { flush: 'post' }
)

watch(
  () => ({
    messages: messages.value,
    lastResponse: lastResponse.value,
    highlightedIds: highlightedIds.value,
    selectedPoiId: selectedPoi.value?.id || null,
    isolateSelectedPoi: isolateSelectedPoi.value,
    shelterCandidatesDismissed: shelterCandidatesDismissed.value,
    question: question.value,
    routeMode: routeMode.value
  }),
  persistChatState,
  { deep: true }
)

watch(locale, () => {
  if (!lastResponse.value && messages.value.length === 1 && !route.query.draft) {
    messages.value = defaultMessages()
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
  if (!selectedPoi.value) {
    selectedPoi.value = pois.value[0] || null
  }
}

function requestCurrentLocation() {
  if (typeof navigator === 'undefined' || !navigator.geolocation) {
    locationStatus.value = 'unsupported'
    locationMessage.value = localText('locationUnsupported')
    return
  }
  locationStatus.value = 'locating'
  locationMessage.value = localText('locating')
  navigator.geolocation.getCurrentPosition(
    async (position) => {
      const raw = {
        longitude: position.coords.longitude,
        latitude: position.coords.latitude,
        accuracyMeters: position.coords.accuracy,
        label: localText('currentLocation'),
        coordinateSystem: 'WGS84'
      }
      currentLocation.value = await convertCurrentLocation(raw)
      locationStatus.value = 'success'
      locationMessage.value = currentLocation.value.accuracyMeters
        ? localText('locatedWithAccuracy', { accuracy: Math.round(currentLocation.value.accuracyMeters) })
        : localText('located')
    },
    (error) => {
      currentLocation.value = null
      locationStatus.value = error?.code === 1 ? 'denied' : 'error'
      locationMessage.value = error?.code === 1 ? localText('locationDenied') : localText('locationFailed')
    },
    { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 }
  )
}

async function convertCurrentLocation(raw) {
  try {
    const AMap = await loadAmap()
    if (!AMap?.convertFrom) return raw
    const converted = await new Promise((resolve) => {
      AMap.convertFrom([raw.longitude, raw.latitude], 'gps', (status, result) => {
        const location = result?.locations?.[0]
        if (status === 'complete' && location) {
          resolve({
            longitude: Number(location.lng ?? location.getLng?.()),
            latitude: Number(location.lat ?? location.getLat?.())
          })
          return
        }
        resolve(null)
      })
    })
    if (!converted || !Number.isFinite(converted.longitude) || !Number.isFinite(converted.latitude)) {
      return raw
    }
    return {
      ...raw,
      ...converted,
      coordinateSystem: 'GCJ02'
    }
  } catch {
    return raw
  }
}

function currentLocationPayload() {
  const location = currentLocation.value
  if (!location || !Number.isFinite(Number(location.longitude)) || !Number.isFinite(Number(location.latitude))) {
    return null
  }
  return {
    longitude: Number(location.longitude),
    latitude: Number(location.latitude),
    accuracyMeters: Number.isFinite(Number(location.accuracyMeters)) ? Number(location.accuracyMeters) : null,
    label: location.label || localText('currentLocation'),
    coordinateSystem: location.coordinateSystem || 'GCJ02'
  }
}

function defaultMessages() {
  return [{ id: 1, role: 'assistant', content: t('map.intro') }]
}

function restoreChatState() {
  try {
    const raw = sessionStorage.getItem(MAP_CHAT_STATE_KEY)
    if (!raw) return
    const state = JSON.parse(raw)
    if (Array.isArray(state.messages) && state.messages.length) {
      messages.value = state.messages
    }
    lastResponse.value = state.lastResponse || null
    highlightedIds.value = Array.isArray(state.highlightedIds) ? state.highlightedIds : []
    isolateSelectedPoi.value = Boolean(state.isolateSelectedPoi)
    shelterCandidatesDismissed.value = Boolean(state.shelterCandidatesDismissed)
    if (typeof state.question === 'string') {
      question.value = state.question
    }
    if (['walking', 'cycling'].includes(state.routeMode)) {
      routeMode.value = state.routeMode
    }
    const restoredPoi = findPoiById(state.selectedPoiId)
    if (restoredPoi) {
      selectedPoi.value = restoredPoi
    }
    scrollMessagesToBottom()
  } catch {
    sessionStorage.removeItem(MAP_CHAT_STATE_KEY)
  }
}

function persistChatState() {
  if (!chatStateReady.value) return
  try {
    sessionStorage.setItem(MAP_CHAT_STATE_KEY, JSON.stringify({
      messages: messages.value,
      lastResponse: lastResponse.value,
      highlightedIds: highlightedIds.value,
      selectedPoiId: selectedPoi.value?.id || null,
      isolateSelectedPoi: isolateSelectedPoi.value,
      shelterCandidatesDismissed: shelterCandidatesDismissed.value,
      question: question.value,
      routeMode: routeMode.value
    }))
  } catch {
    // Ignore storage failures; the chat still works for the current render.
  }
}

function clearPageChat() {
  messages.value = defaultMessages()
  lastResponse.value = null
  highlightedIds.value = []
  selectedShelterCandidateIds.value = []
  shelterCandidatesDismissed.value = false
  isolateSelectedPoi.value = false
  question.value = t('map.quickStudy')
  sessionStorage.removeItem(MAP_CHAT_STATE_KEY)
  scrollMessagesToBottom()
}

function resetMapView() {
  lastResponse.value = null
  highlightedIds.value = []
  selectedShelterCandidateIds.value = []
  shelterCandidatesDismissed.value = false
  isolateSelectedPoi.value = false
  clearRouteDraft()
  selectedPoi.value = pois.value[0] || null
  const nextQuery = { ...route.query }
  delete nextQuery.poiId
  delete nextQuery.draft
  if (Object.keys(nextQuery).length !== Object.keys(route.query).length) {
    router.replace({ path: route.path, query: nextQuery })
  }
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
    selectedShelterCandidateIds.value = []
    shelterCandidatesDismissed.value = false
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
  if (response.intent === 'small_talk') {
    highlightedIds.value = []
    return
  }
  if (response.intent === 'find_note') {
    highlightedIds.value = []
    return
  }
  isolateSelectedPoi.value = false
  selectedShelterCandidateIds.value = []
  shelterCandidatesDismissed.value = false
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

function shelterCandidatePois() {
  const payload = routeAction.value?.payload || {}
  if (payload.weatherAdjusted) return []
  const ids = payload.shelterCandidateIds || []
  const routeIds = routeAction.value?.poiIds || []
  return ids
    .filter((id) => !routeIds.includes(id))
    .map((id) => findPoiById(id))
    .filter(Boolean)
}

function uniquePois(items) {
  const map = new Map()
  items.filter(Boolean).forEach((poi) => map.set(poi.id, poi))
  return Array.from(map.values())
}

function toggleShelterCandidate(id) {
  if (selectedShelterCandidateIds.value.includes(id)) {
    selectedShelterCandidateIds.value = selectedShelterCandidateIds.value.filter((item) => item !== id)
    return
  }
  if (selectedShelterCandidateIds.value.length >= 2) {
    ElMessage.warning(localText('shelterLimit'))
    return
  }
  selectedShelterCandidateIds.value = [...selectedShelterCandidateIds.value, id]
}

function applyShelterCandidates() {
  if (!routeAction.value || !selectedShelterCandidateIds.value.length) return
  const payload = routeAction.value.payload || {}
  const originalIds = Array.isArray(payload.originalPoiIds) && payload.originalPoiIds.length
    ? payload.originalPoiIds
    : routeAction.value.poiIds || []
  const selected = shelterCandidates.value
    .filter((candidate) => selectedShelterCandidateIds.value.includes(candidate.poiId))
    .sort((a, b) => (a.segmentIndex - b.segmentIndex) || (a.distanceFromStartMeters - b.distanceFromStartMeters))
  const byInsertAfter = new Map()
  selected.forEach((candidate) => {
    const key = candidate.insertAfterPoiId
    if (!byInsertAfter.has(key)) byInsertAfter.set(key, [])
    byInsertAfter.get(key).push(candidate)
  })
  const nextIds = []
  originalIds.forEach((id) => {
    if (!nextIds.includes(id)) nextIds.push(id)
    const inserts = byInsertAfter.get(id) || []
    inserts.forEach((candidate) => {
      if (!nextIds.includes(candidate.poiId)) nextIds.push(candidate.poiId)
    })
  })
  routeAction.value.poiIds = nextIds
  payload.weatherAdjusted = true
  payload.weatherShelterSuggested = false
  payload.shelterWaypointIds = selected.map((candidate) => candidate.poiId)
  payload.via = nextIds
    .slice(1, -1)
    .map((id) => findPoiById(id)?.name)
    .filter(Boolean)
  routeAction.value.payload = payload
  const highlight = lastResponse.value?.mapActions?.find((item) => item.type === 'highlight_pois')
  if (highlight) highlight.poiIds = nextIds
  highlightedIds.value = nextIds
  selectedShelterCandidateIds.value = []
  shelterCandidatesDismissed.value = true
  ElMessage.success(localText('shelterAddedToast'))
}

function dismissShelterCandidates() {
  selectedShelterCandidateIds.value = []
  shelterCandidatesDismissed.value = true
}

function openNoteFromChat(note) {
  if (!note?.id) return
  router.push({ path: `/discover/${note.id}`, query: { from: 'map-chat' } })
}

function handleBackNavigation() {
  if (window.history.state?.back) {
    router.back()
    return
  }
  router.push('/')
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
  const includeRouteDraft = hasRouteDraft.value && looksLikeRouteText(text)
  return buildRouteContext(includeRouteDraft)
}

function buildRouteContext(includeRouteDraft = hasRouteDraft.value) {
  const context = {}
  if (includeRouteDraft && hasRouteDraft.value) {
    context.originPoiId = routeDraft.origin?.id || null
    context.destinationPoiId = routeDraft.destination?.id || null
    context.waypointPoiIds = routeDraft.waypoints.map((poi) => poi.id)
  }
  const location = currentLocationPayload()
  if (location) {
    context.currentLocation = location
  }
  return Object.keys(context).length ? context : null
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

function routePoiName(index) {
  const id = routeAction.value?.poiIds?.[index]
  return findPoiById(id)?.name
}

function findPoiById(id) {
  if (!id) return null
  const pools = [resultPois.value, pois.value, routeDraft.waypoints, [routeDraft.origin, routeDraft.destination, selectedPoi.value]]
  return pools.flat().find((poi) => poi?.id === id) || null
}

function buildPoiDetail(poi) {
  const parsed = parsePoiRemark(poi?.remark)
  return {
    intro: parsed.intro || poi?.remark || '',
    hours: parsed.hours,
    tags: splitPoiTags(poi?.tags),
    status: openStatusText(poi?.openStatus)
  }
}

function parsePoiRemark(remark) {
  const raw = `${remark || ''}`.trim()
  if (!raw) return { intro: '', hours: '' }

  const normalized = raw.replace(/\s+/g, ' ')
  const labeled = normalized.match(/^Intro:\s*(.*?)(?:\s+Hours:\s*(.*))?$/i)
  if (labeled) {
    return {
      intro: labeled[1]?.trim() || '',
      hours: labeled[2]?.trim() || ''
    }
  }

  const zhLabeled = normalized.match(/^简介[:：]\s*(.*?)(?:\s+开放时间[:：]\s*(.*))?$/)
  if (zhLabeled) {
    return {
      intro: zhLabeled[1]?.trim() || '',
      hours: zhLabeled[2]?.trim() || ''
    }
  }

  const hours = normalized.match(/^(.*?)(?:\s+Hours?:\s*|\s+Opening hours?:\s*)(.*)$/i)
  if (hours) {
    return {
      intro: hours[1]?.trim() || '',
      hours: hours[2]?.trim() || ''
    }
  }

  return { intro: raw, hours: '' }
}

function splitPoiTags(tags) {
  return `${tags || ''}`
    .split(/[,，、;\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 7)
}

function openStatusText(status) {
  const value = `${status || ''}`.toUpperCase()
  if (locale.value === 'en-US') {
    if (value === 'OPEN') return 'Open'
    if (value === 'CLOSED') return 'Closed'
    return status || 'Unknown'
  }
  if (value === 'OPEN') return '开放中'
  if (value === 'CLOSED') return '已关闭'
  return status || '状态未知'
}

function choosePoiImage(poi) {
  const imageUrl = normalizePoiImageUrl(poi?.imageUrl)
  if (imageUrl) {
    return {
      key: `poi-${poi?.id || 'custom'}-${imageUrl}`,
      url: imageUrl,
      alt: poi?.name || 'POI image'
    }
  }
  const text = `${poi?.name || ''} ${poi?.category || ''} ${poi?.tags || ''}`.toLowerCase()
  if (/(library|reading|study|图书|阅览|自习)/.test(text)) return LIBRARY_IMAGE
  if (/(weather|meteorology|radar|气象|雷达|观测)/.test(text)) return WEATHER_IMAGE
  return CAMPUS_IMAGE
}

function normalizePoiImageUrl(value) {
  const url = `${value || ''}`.trim()
  return url.toLowerCase() === 'null' ? '' : url
}

function handlePoiImageError(event) {
  const key = event?.target?.dataset?.imageKey
  if (!key || failedPoiImageKeys.value.includes(key)) return
  failedPoiImageKeys.value = [...failedPoiImageKeys.value, key]
}

function stripContextPrefix(text) {
  return `${text || ''}`
    .replace(/^Recommendation reason:\s*/i, '')
    .replace(/^推荐理由[:：]\s*/, '')
    .replace(/^AI action:\s*/i, '')
    .replace(/^AI 动作[:：]\s*/, '')
    .trim()
}

function localText(key, params = {}) {
  const zh = {
    routeDraft: '\u8def\u7ebf\u8349\u7a3f',
    origin: '\u8d77\u70b9',
    destination: '\u7ec8\u70b9',
    via: '\u9014\u7ecf',
    unset: '\u672a\u8bbe\u7f6e',
    writeToChat: '\u5199\u5165\u5bf9\u8bdd\u6846',
    generateRoute: '\u751f\u6210\u8def\u7ebf',
    clear: '\u6e05\u7a7a',
    routeDraftIncomplete: '\u8bf7\u5148\u8bbe\u7f6e\u8d77\u70b9\u548c\u7ec8\u70b9',
    weatherShelterSuggested: '\u68c0\u6d4b\u5230\u6076\u52a3\u5929\u6c14\uff0c\u5df2\u63a8\u8350\u9644\u8fd1\u53ef\u906e\u853d\u70b9\uff0c\u53ef\u9009\u62e9\u6700\u591a 2 \u4e2a\u52a0\u5165\u8def\u7ebf',
    weatherShelterAdded: '\u5df2\u52a0\u5165\u53ef\u906e\u853d\u9014\u7ecf\u70b9',
    shelterCandidates: '\u5019\u9009\u906e\u853d\u70b9',
    shelterCandidateHint: '\u5148\u67e5\u770b\u5019\u9009\u70b9\uff0c\u518d\u9009\u62e9\u6700\u591a 2 \u4e2a\u63d2\u5165\u5f53\u524d\u8def\u7ebf\u3002',
    addShelters: '\u52a0\u5165\u8def\u7ebf',
    skipShelters: '\u6682\u4e0d\u52a0\u5165',
    shelterLimit: '\u6700\u591a\u9009\u62e9 2 \u4e2a\u906e\u853d\u70b9',
    shelterAddedToast: '\u5df2\u5c06\u906e\u853d\u70b9\u52a0\u5165\u8def\u7ebf',
    fromRoute: '\u8ddd\u8def\u7ebf',
    fromStart: '\u8ddd\u8d77\u70b9',
    back: '\u8fd4\u56de',
    chatPanel: 'AI \u804a\u5929',
    clearChat: '\u6e05\u9664\u804a\u5929',
    routeMode: '\u51fa\u884c\u65b9\u5f0f',
    walkMode: '\u6b65\u884c',
    bikeMode: '\u9a91\u884c',
    campusPlace: '\u6821\u56ed\u5730\u70b9',
    introLabel: '\u7b80\u4ecb',
    hoursLabel: '\u5f00\u653e\u65f6\u95f4',
    tagsLabel: '\u6807\u7b7e',
    recommendReason: '\u63a8\u8350\u7406\u7531',
    routeContext: '\u8def\u7ebf\u8bf4\u660e',
    currentLocation: '\u5f53\u524d\u4f4d\u7f6e',
    locating: '\u6b63\u5728\u83b7\u53d6\u5f53\u524d\u4f4d\u7f6e...',
    located: '\u5df2\u5b9a\u4f4d\u5230\u5f53\u524d\u4f4d\u7f6e',
    locatedWithAccuracy: '\u5df2\u5b9a\u4f4d\uff0c\u7cbe\u5ea6\u7ea6 {accuracy}m',
    locationDenied: '\u5b9a\u4f4d\u6743\u9650\u5df2\u62d2\u7edd\uff0c\u53ef\u70b9\u51fb\u91cd\u8bd5',
    locationFailed: '\u5b9a\u4f4d\u5931\u8d25\uff0c\u53ef\u70b9\u51fb\u91cd\u8bd5',
    locationUnsupported: '\u5f53\u524d\u6d4f\u89c8\u5668\u4e0d\u652f\u6301\u5b9a\u4f4d'
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
    routeDraftIncomplete: 'Set both start and end first',
    weatherShelterSuggested: 'Severe weather detected. Nearby sheltered candidates are ready, and you can add up to 2 to the route',
    weatherShelterAdded: 'Sheltered waypoints were added',
    shelterCandidates: 'Shelter Candidates',
    shelterCandidateHint: 'Review the candidates, then choose up to 2 to insert into the current route.',
    addShelters: 'Add to Route',
    skipShelters: 'Skip',
    shelterLimit: 'Choose at most 2 sheltered points',
    shelterAddedToast: 'Sheltered points were added to the route',
    fromRoute: 'from route',
    fromStart: 'from start',
    back: 'Back',
    chatPanel: 'AI Chat',
    clearChat: 'Clear Chat',
    routeMode: 'Travel Mode',
    walkMode: 'Walk',
    bikeMode: 'Bike',
    campusPlace: 'Campus Place',
    introLabel: 'Intro',
    hoursLabel: 'Opening Hours',
    tagsLabel: 'Tags',
    recommendReason: 'Recommendation reason',
    routeContext: 'Route context',
    currentLocation: 'Current location',
    locating: 'Locating current position...',
    located: 'Current position located',
    locatedWithAccuracy: 'Located, accuracy about {accuracy}m',
    locationDenied: 'Location permission was denied. Click retry',
    locationFailed: 'Location failed. Click retry',
    locationUnsupported: 'This browser does not support location'
  }
  let value = (locale.value === 'en-US' ? en : zh)[key] || key
  Object.entries(params).forEach(([name, replacement]) => {
    value = value.replace(`{${name}}`, replacement)
  })
  return value
}

function scrollMessagesToBottom() {
  nextTick(() => {
    const el = messagesRef.value
    if (!el) return
    el.scrollTop = el.scrollHeight
  })
}
</script>
