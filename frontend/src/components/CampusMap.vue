<template>
  <section class="map-shell" :aria-label="$t('nav.map')" @click="closeContextMenu">
    <div ref="amapContainer" class="map-placeholder" :class="{ active: amapReady }"></div>
    <div v-if="amapFallbackReason" class="map-fallback-badge">{{ amapFallbackReason }}</div>
    <button
      class="map-location-status"
      :class="locationStatusClass"
      type="button"
      @click.stop="$emit('locate-current')"
    >
      <span>{{ locationStatusText }}</span>
      <small v-if="locationCanRetry">{{ locale === 'en-US' ? 'Retry' : '\u91cd\u8bd5' }}</small>
    </button>

    <template v-if="!amapReady">
      <div class="map-road main"></div>
      <div class="map-road cross"></div>
      <svg v-if="routeLines.length" class="route-overlay" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
        <line
          v-for="line in routeLines"
          :key="line.key"
          :x1="line.from.x"
          :y1="line.from.y"
          :x2="line.to.x"
          :y2="line.to.y"
        />
      </svg>
      <span
        v-for="point in routePoints"
        :key="`route-point-${point.id}`"
        class="route-endpoint"
        :class="[point.type, { shelter: point.shelter }]"
        :style="{ left: `${point.x}%`, top: `${point.y}%` }"
      >
        {{ point.label }}
      </span>
      <button
        v-for="poi in normalizedPois"
        :key="poi.id"
        class="pin"
        :class="{ active: isActivePoi(poi.id), 'shelter-candidate': isShelterCandidate(poi.id) }"
        :style="{ left: `${poi.x}%`, top: `${poi.y}%` }"
        type="button"
        :aria-label="poi.name"
        @click="$emit('select', poi)"
        @contextmenu.prevent.stop="openContextMenu(poi, $event)"
      >
        <span>{{ categoryInitial(poi.category) }}</span>
      </button>
      <span
        v-if="normalizedCurrentLocation"
        class="current-location-pin"
        :style="{ left: `${normalizedCurrentLocation.x}%`, top: `${normalizedCurrentLocation.y}%` }"
      ></span>
      <span
        v-if="normalizedCurrentLocation"
        class="map-label current-location-label"
        :style="{ left: `${normalizedCurrentLocation.x}%`, top: `${normalizedCurrentLocation.y}%` }"
      >
        {{ currentLocationLabel }}
      </span>
      <span
        v-for="poi in normalizedPois"
        :key="`label-${poi.id}`"
        class="map-label"
        :style="{ left: `${poi.x}%`, top: `${poi.y}%` }"
      >
        {{ poi.name }}
      </span>
    </template>
    <div
      v-if="contextMenu"
      class="map-context-menu"
      :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
      @click.stop
      @contextmenu.prevent
    >
      <strong>{{ contextMenu.poi.name }}</strong>
      <button type="button" @click="emitRouteAction('origin')">{{ contextLabel('origin') }}</button>
      <button type="button" @click="emitRouteAction('destination')">{{ contextLabel('destination') }}</button>
      <button type="button" @click="emitRouteAction('waypoint')">{{ contextLabel('waypoint') }}</button>
      <button type="button" @click="emitRouteAction('clear')">{{ contextLabel('clear') }}</button>
    </div>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadAmap } from '../utils/amapLoader'

const props = defineProps({
  pois: { type: Array, default: () => [] },
  highlightedIds: { type: Array, default: () => [] },
  selectedPoiId: { type: Number, default: null },
  routeAction: { type: Object, default: null },
  routeMode: { type: String, default: 'walking' },
  currentLocation: { type: Object, default: null },
  locationStatus: { type: String, default: 'idle' },
  locationMessage: { type: String, default: '' }
})

const emit = defineEmits(['select', 'route-context', 'locate-current'])
const { locale } = useI18n()
const amapContainer = ref(null)
const amapReady = ref(false)
const amapFallbackReason = ref('')
const contextMenu = ref(null)
let map
let AMapRef
let routePlanner
let fallbackRouteLine
let startMarker
let endMarker
let routePlanners = []
let fallbackRouteLines = []
let routePointMarkers = []
let markers = []
let currentLocationMarker

const normalizationBounds = computed(() => {
  const points = [
    ...props.pois.filter((poi) => isValidCoordinate(poi)),
    props.currentLocation,
    rawRouteStartPoint()
  ].filter((point) => isValidCoordinate(point))
  if (!points.length) return null
  const lngs = points.map((point) => Number(point.longitude))
  const lats = points.map((point) => Number(point.latitude))
  return {
    minLng: Math.min(...lngs),
    maxLng: Math.max(...lngs),
    minLat: Math.min(...lats),
    maxLat: Math.max(...lats)
  }
})

const normalizedPois = computed(() => props.pois
  .filter((poi) => isValidCoordinate(poi))
  .map((poi) => normalizeFallbackPoint(poi))
  .filter(Boolean))

const normalizedCurrentLocation = computed(() => {
  if (!isValidCoordinate(props.currentLocation)) return null
  return normalizeFallbackPoint(currentLocationPoint(props.currentLocation))
})

const currentLocationLabel = computed(() => props.currentLocation?.label || (locale.value === 'en-US' ? 'Current location' : '\u5f53\u524d\u4f4d\u7f6e'))
const locationCanRetry = computed(() => ['denied', 'error', 'unsupported', 'idle'].includes(props.locationStatus))
const locationStatusClass = computed(() => ({
  success: props.locationStatus === 'success',
  locating: props.locationStatus === 'locating',
  error: ['denied', 'error', 'unsupported'].includes(props.locationStatus)
}))
const locationStatusText = computed(() => {
  if (props.locationMessage) return props.locationMessage
  if (props.locationStatus === 'success') return locale.value === 'en-US' ? 'Current location ready' : '\u5f53\u524d\u4f4d\u7f6e\u5df2\u5c31\u7eea'
  if (props.locationStatus === 'locating') return locale.value === 'en-US' ? 'Locating...' : '\u6b63\u5728\u5b9a\u4f4d...'
  return locale.value === 'en-US' ? 'Use current location' : '\u4f7f\u7528\u5f53\u524d\u4f4d\u7f6e'
})

const routePoints = computed(() => routePoisByAction().map((poi, index, list) => ({
  ...poi,
  type: index === 0 ? 'start' : index === list.length - 1 ? 'end' : 'waypoint',
  shelter: isShelterWaypoint(poi.id),
  label: routePointLabel(index, list.length, poi)
})))

const routeLines = computed(() => {
  const routePois = routePoisByAction()
  if (routePois.length < 2) return []
  return routePois.slice(0, -1).map((poi, index) => ({
    key: `${poi.id}-${routePois[index + 1].id}`,
    from: poi,
    to: routePois[index + 1]
  }))
})

onMounted(async () => {
  try {
    AMapRef = await loadAmap()
    initAmap()
  } catch (error) {
    amapFallbackReason.value = fallbackMessage(error)
  }
})

onBeforeUnmount(() => {
  clearRoute()
  markers.forEach((marker) => marker.setMap(null))
  if (currentLocationMarker) currentLocationMarker.setMap(null)
  if (map?.destroy) map.destroy()
})

watch(
  () => [props.pois, props.highlightedIds, props.selectedPoiId, props.currentLocation],
  () => {
    if (amapReady.value) {
      renderMarkers()
      focusVisiblePois()
    }
  },
  { deep: true }
)

watch(
  () => [props.routeAction, props.routeMode],
  () => {
    if (amapReady.value) renderRoute()
  },
  { deep: true }
)

watch(locale, () => {
  if (amapReady.value) {
    renderMarkers()
    renderRoute()
  } else if (amapFallbackReason.value) {
    amapFallbackReason.value = fallbackMessage({ message: 'amap_load_failed' })
  }
})

function initAmap() {
  if (!AMapRef || !amapContainer.value) return
  amapReady.value = true
  amapFallbackReason.value = ''
  const center = props.pois.find((poi) => isValidCoordinate(poi))
  map = new AMapRef.Map(amapContainer.value, {
    zoom: 17,
    center: center ? poiLngLat(center) : [113.9345, 22.5331],
    viewMode: '2D',
    resizeEnable: true,
    mapStyle: 'amap://styles/normal'
  })
  map.on('click', closeContextMenu)
  if (AMapRef.Scale) map.addControl(new AMapRef.Scale())
  if (AMapRef.ToolBar) map.addControl(new AMapRef.ToolBar({ position: 'RT' }))
  renderMarkers()
  renderRoute()
  focusVisiblePois()
}

function renderMarkers() {
  if (!map || !AMapRef) return
  markers.forEach((marker) => marker.setMap(null))
  if (currentLocationMarker) {
    currentLocationMarker.setMap(null)
    currentLocationMarker = null
  }
  markers = props.pois.filter((poi) => isValidCoordinate(poi)).map((poi) => {
    const marker = new AMapRef.Marker({
      position: poiLngLat(poi),
      title: poi.name,
      anchor: 'bottom-center',
      content: markerContent(poi)
    })
    marker.on('click', () => {
      closeContextMenu()
      emit('select', poi)
    })
    marker.on('rightclick', (event) => openContextMenu(poi, event))
    marker.setMap(map)
    return marker
  })
  renderCurrentLocationMarker()
}

function renderCurrentLocationMarker() {
  if (!map || !AMapRef || !isValidCoordinate(props.currentLocation)) return
  currentLocationMarker = new AMapRef.Marker({
    position: poiLngLat(props.currentLocation),
    title: currentLocationLabel.value,
    anchor: 'center',
    zIndex: 120,
    content: currentLocationContent()
  })
  currentLocationMarker.setMap(map)
}

function renderRoute() {
  clearRoute()
  const routePois = routePoisByAction()
  if (!map || !AMapRef || routePois.length < 2) return
  renderSegmentedRoute(routePois)
  return
  const from = poiLngLat(routePois[0])
  const to = poiLngLat(routePois[routePois.length - 1])

  startMarker = new AMapRef.Marker({
    position: from,
    anchor: 'bottom-center',
    content: endpointContent(locale.value === 'en-US' ? 'Start' : '起点', 'start')
  })
  endMarker = new AMapRef.Marker({
    position: to,
    anchor: 'bottom-center',
    content: endpointContent(locale.value === 'en-US' ? 'End' : '终点', 'end')
  })
  startMarker.setMap(map)
  endMarker.setMap(map)

  const RoutePlanner = routePlannerCtor()
  if (RoutePlanner) {
    routePlanner = new RoutePlanner(routePlannerOptions())
    routePlanner.search(from, to, (status) => {
      if (status !== 'complete') drawFallbackRoute(from, to)
      focusRoute()
    })
    return
  }

  drawFallbackRoute(from, to)
  focusRoute()
}

function renderSegmentedRoute(routePois) {
  const RoutePlanner = routePlannerCtor()
  routePois.forEach((poi, index) => {
    const marker = new AMapRef.Marker({
      position: poiLngLat(poi),
      anchor: 'bottom-center',
      content: endpointContent(
        routePointLabel(index, routePois.length, poi),
        index === 0 ? 'start' : index === routePois.length - 1 ? 'end' : 'waypoint',
        isShelterWaypoint(poi.id)
      )
    })
    marker.setMap(map)
    routePointMarkers.push(marker)
  })

  routePois.slice(0, -1).forEach((poi, index) => {
    const from = poiLngLat(poi)
    const to = poiLngLat(routePois[index + 1])
    if (RoutePlanner) {
      const segment = new RoutePlanner(routePlannerOptions())
      routePlanners.push(segment)
      segment.search(from, to, (status) => {
        if (status !== 'complete') drawFallbackRoute(from, to)
        focusRoute()
      })
      return
    }
    drawFallbackRoute(from, to)
  })
  focusRoute()
}

function routePlannerCtor() {
  if (!AMapRef) return null
  if (props.routeMode === 'cycling') return AMapRef.Riding || AMapRef.Walking || null
  return AMapRef.Walking || null
}

function routePlannerOptions() {
  return {
    map,
    hideMarkers: true,
    autoFitView: false
  }
}

function drawFallbackRoute(from, to) {
  if (!AMapRef?.Polyline) return
  fallbackRouteLine = new AMapRef.Polyline({
    path: [from, to],
    strokeColor: props.routeMode === 'cycling' ? '#2563eb' : '#0f766e',
    strokeOpacity: 0.9,
    strokeWeight: 7,
    strokeStyle: 'dashed',
    lineJoin: 'round',
    zIndex: 80
  })
  fallbackRouteLine.setMap(map)
  fallbackRouteLines.push(fallbackRouteLine)
}

function clearRoute() {
  routePlanners.forEach((segment) => {
    if (segment?.clear) segment.clear()
  })
  fallbackRouteLines.forEach((line) => line.setMap(null))
  routePointMarkers.forEach((marker) => marker.setMap(null))
  if (routePlanner?.clear) routePlanner.clear()
  if (fallbackRouteLine) fallbackRouteLine.setMap(null)
  if (startMarker) startMarker.setMap(null)
  if (endMarker) endMarker.setMap(null)
  routePlanners = []
  fallbackRouteLines = []
  routePointMarkers = []
  routePlanner = null
  fallbackRouteLine = null
  startMarker = null
  endMarker = null
}

function focusVisiblePois() {
  const activeIds = new Set([...props.highlightedIds, props.selectedPoiId].filter(Boolean))
  const targetPois = props.pois.filter((poi) => activeIds.has(poi.id) && isValidCoordinate(poi))
  if (targetPois.length) {
    const points = targetPois.map(poiLngLat)
    if (isValidCoordinate(props.currentLocation)) {
      points.push(poiLngLat(props.currentLocation))
    }
    focusLngLats(points)
    return
  }
  const points = props.pois.filter(isValidCoordinate).map(poiLngLat)
  if (isValidCoordinate(props.currentLocation)) {
    points.push(poiLngLat(props.currentLocation))
  }
  if (points.length) focusLngLats(points)
}

function focusRoute() {
  const routePois = routePoisByAction()
  if (routePois.length >= 2) focusLngLats(routePois.map(poiLngLat))
}

function focusLngLats(points) {
  if (!map || !points.length) return
  if (points.length === 1) {
    map.setZoomAndCenter(17, points[0])
    return
  }
  const overlays = points.map((point) => new AMapRef.Marker({ position: point }))
  map.setFitView(overlays, false, [60, 60, 60, 60], 18)
}

function routePoisByAction() {
  const ids = props.routeAction?.poiIds || []
  const routePois = ids
    .map((id) => props.pois.find((poi) => poi.id === id))
    .filter((poi) => poi && isValidCoordinate(poi))
    .map((poi) => normalizedPois.value.find((item) => item.id === poi.id) || poi)
  const start = routeStartPoint()
  const points = start ? [start, ...routePois] : routePois
  return points.length >= 2 ? points : []
}

function routeStartPoint() {
  const point = rawRouteStartPoint()
  if (!point) return null
  return normalizeFallbackPoint(point) || point
}

function rawRouteStartPoint() {
  const point = props.routeAction?.payload?.startPoint
  if (!isValidCoordinate(point)) return null
  return currentLocationPoint(point, true)
}

function currentLocationPoint(source, routeStart = false) {
  return {
    id: routeStart ? '__current-route-start' : '__current-location',
    name: source?.label || (locale.value === 'en-US' ? 'Current location' : '\u5f53\u524d\u4f4d\u7f6e'),
    longitude: Number(source.longitude),
    latitude: Number(source.latitude),
    isCurrentLocation: true
  }
}

function normalizeFallbackPoint(point) {
  const bounds = normalizationBounds.value
  if (!bounds || !isValidCoordinate(point)) return null
  const lngRange = bounds.maxLng - bounds.minLng || 1
  const latRange = bounds.maxLat - bounds.minLat || 1
  return {
    ...point,
    x: 12 + ((Number(point.longitude) - bounds.minLng) / lngRange) * 76,
    y: 82 - ((Number(point.latitude) - bounds.minLat) / latRange) * 64
  }
}

function openContextMenu(poi, event) {
  if (!poi?.id || !amapContainer.value) return
  const source = event?.originEvent || event
  source?.preventDefault?.()
  const rect = amapContainer.value.getBoundingClientRect()
  const pixel = event?.pixel
  const rawX = Number.isFinite(source?.clientX) ? source.clientX - rect.left : pixel?.x ?? pixel?.getX?.() ?? 20
  const rawY = Number.isFinite(source?.clientY) ? source.clientY - rect.top : pixel?.y ?? pixel?.getY?.() ?? 20
  contextMenu.value = {
    poi,
    x: Math.min(Math.max(rawX, 12), Math.max(rect.width - 190, 12)),
    y: Math.min(Math.max(rawY, 12), Math.max(rect.height - 190, 12))
  }
}

function closeContextMenu() {
  contextMenu.value = null
}

function emitRouteAction(action) {
  if (!contextMenu.value) return
  emit('route-context', { action, poi: contextMenu.value.poi })
  closeContextMenu()
}

function isActivePoi(id) {
  return props.highlightedIds.includes(id) || props.selectedPoiId === id
}

function isValidCoordinate(poi) {
  const lng = Number(poi?.longitude)
  const lat = Number(poi?.latitude)
  return Number.isFinite(lng) && Number.isFinite(lat)
}

function poiLngLat(poi) {
  return [Number(poi.longitude), Number(poi.latitude)]
}

function markerContent(poi) {
  const active = isActivePoi(poi.id) ? ' active' : ''
  const shelterCandidate = isShelterCandidate(poi.id) ? ' shelter-candidate' : ''
  const initial = categoryInitial(poi.category)
  const candidateLabel = isShelterCandidate(poi.id)
    ? `<em>${escapeHtml(locale.value === 'en-US' ? 'Shelter candidate' : '\u5019\u9009\u906e\u853d\u70b9')}</em>`
    : ''
  return `<div class="amap-poi-marker${active}${shelterCandidate}"><span>${escapeHtml(initial)}</span><b>${escapeHtml(poi.name || '')}</b>${candidateLabel}</div>`
}

function currentLocationContent() {
  return `<div class="amap-current-marker"><span></span><b>${escapeHtml(currentLocationLabel.value)}</b></div>`
}

function endpointContent(label, type, shelter = false) {
  return `<div class="amap-route-endpoint ${type}${shelter ? ' shelter' : ''}">${escapeHtml(label)}</div>`
}

function routePointLabel(index, total, poi = null) {
  if (poi?.isCurrentLocation) return locale.value === 'en-US' ? 'Me' : '\u6211'
  if (index === 0) return locale.value === 'en-US' ? 'Start' : '\u8d77\u70b9'
  if (index === total - 1) return locale.value === 'en-US' ? 'End' : '\u7ec8\u70b9'
  if (isShelterWaypoint(poi?.id)) return locale.value === 'en-US' ? 'Shelter' : '\u906e\u853d\u70b9'
  return locale.value === 'en-US' ? `Via ${index}` : `\u9014\u7ecf${index}`
}

function isShelterWaypoint(id) {
  const ids = props.routeAction?.payload?.shelterWaypointIds || []
  return ids.includes(id)
}

function isShelterCandidate(id) {
  if (props.routeAction?.payload?.weatherAdjusted) return false
  const ids = props.routeAction?.payload?.shelterCandidateIds || []
  const routeIds = props.routeAction?.poiIds || []
  return ids.includes(id) && !routeIds.includes(id) && !isShelterWaypoint(id)
}

function contextLabel(key) {
  const zh = {
    origin: '\u8bbe\u4e3a\u8d77\u70b9',
    destination: '\u8bbe\u4e3a\u7ec8\u70b9',
    waypoint: '\u6dfb\u52a0\u9014\u7ecf\u70b9',
    clear: '\u6e05\u7a7a\u8def\u7ebf\u8349\u7a3f'
  }
  const en = {
    origin: 'Set as Start',
    destination: 'Set as End',
    waypoint: 'Add Waypoint',
    clear: 'Clear Route Draft'
  }
  return (locale.value === 'en-US' ? en : zh)[key] || key
}

function categoryInitial(category) {
  const map = {
    STUDY: 'S',
    DINING: 'D',
    TEACHING: 'T',
    DORM: 'R',
    SERVICE: 'V',
    SPORTS: 'P',
    TRANSPORT: 'B',
    LANDMARK: 'L'
  }
  return map[category] || 'P'
}

function fallbackMessage(error) {
  if (error?.message === 'missing_amap_key') {
    return locale.value === 'en-US' ? 'AMap key is not configured, using demo map.' : '未配置高德 Key，使用模拟地图兜底。'
  }
  return locale.value === 'en-US' ? 'AMap failed to load, using demo map.' : '高德地图加载失败，使用模拟地图兜底。'
}

function escapeHtml(value) {
  return `${value}`.replace(/[&<>"']/g, (char) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[char]))
}
</script>
