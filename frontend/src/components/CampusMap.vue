<template>
  <section class="map-shell" :aria-label="$t('nav.map')" @click="closeContextMenu">
    <div ref="amapContainer" class="map-placeholder" :class="{ active: amapReady }"></div>
    <div v-if="amapFallbackReason" class="map-fallback-badge">{{ amapFallbackReason }}</div>

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
        :class="point.type"
        :style="{ left: `${point.x}%`, top: `${point.y}%` }"
      >
        {{ point.label }}
      </span>
      <button
        v-for="poi in normalizedPois"
        :key="poi.id"
        class="pin"
        :class="{ active: isActivePoi(poi.id) }"
        :style="{ left: `${poi.x}%`, top: `${poi.y}%` }"
        type="button"
        :aria-label="poi.name"
        @click="$emit('select', poi)"
        @contextmenu.prevent.stop="openContextMenu(poi, $event)"
      >
        <span>{{ categoryInitial(poi.category) }}</span>
      </button>
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
  routeAction: { type: Object, default: null }
})

const emit = defineEmits(['select', 'route-context'])
const { locale } = useI18n()
const amapContainer = ref(null)
const amapReady = ref(false)
const amapFallbackReason = ref('')
const contextMenu = ref(null)
let map
let AMapRef
let walking
let fallbackRouteLine
let startMarker
let endMarker
let walkingRoutes = []
let fallbackRouteLines = []
let routePointMarkers = []
let markers = []

const normalizedPois = computed(() => {
  const validPois = props.pois.filter((poi) => isValidCoordinate(poi))
  if (!validPois.length) return []
  const lngs = validPois.map((poi) => Number(poi.longitude))
  const lats = validPois.map((poi) => Number(poi.latitude))
  const minLng = Math.min(...lngs)
  const maxLng = Math.max(...lngs)
  const minLat = Math.min(...lats)
  const maxLat = Math.max(...lats)
  return validPois.map((poi) => {
    const lngRange = maxLng - minLng || 1
    const latRange = maxLat - minLat || 1
    return {
      ...poi,
      x: 12 + ((Number(poi.longitude) - minLng) / lngRange) * 76,
      y: 82 - ((Number(poi.latitude) - minLat) / latRange) * 64
    }
  })
})

const routePoints = computed(() => routePoisByAction().map((poi, index, list) => ({
  ...poi,
  type: index === 0 ? 'start' : index === list.length - 1 ? 'end' : 'waypoint',
  label: routePointLabel(index, list.length)
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
  if (map?.destroy) map.destroy()
})

watch(
  () => [props.pois, props.highlightedIds, props.selectedPoiId],
  () => {
    if (amapReady.value) {
      renderMarkers()
      focusVisiblePois()
    }
  },
  { deep: true }
)

watch(
  () => props.routeAction,
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

  if (AMapRef.Walking) {
    walking = new AMapRef.Walking({
      map,
      hideMarkers: true,
      autoFitView: false
    })
    walking.search(from, to, (status) => {
      if (status !== 'complete') drawFallbackRoute(from, to)
      focusRoute()
    })
    return
  }

  drawFallbackRoute(from, to)
  focusRoute()
}

function renderSegmentedRoute(routePois) {
  routePois.forEach((poi, index) => {
    const marker = new AMapRef.Marker({
      position: poiLngLat(poi),
      anchor: 'bottom-center',
      content: endpointContent(routePointLabel(index, routePois.length), index === 0 ? 'start' : index === routePois.length - 1 ? 'end' : 'waypoint')
    })
    marker.setMap(map)
    routePointMarkers.push(marker)
  })

  routePois.slice(0, -1).forEach((poi, index) => {
    const from = poiLngLat(poi)
    const to = poiLngLat(routePois[index + 1])
    if (AMapRef.Walking) {
      const segment = new AMapRef.Walking({
        map,
        hideMarkers: true,
        autoFitView: false
      })
      walkingRoutes.push(segment)
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

function drawFallbackRoute(from, to) {
  if (!AMapRef?.Polyline) return
  fallbackRouteLine = new AMapRef.Polyline({
    path: [from, to],
    strokeColor: '#0f766e',
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
  walkingRoutes.forEach((segment) => {
    if (segment?.clear) segment.clear()
  })
  fallbackRouteLines.forEach((line) => line.setMap(null))
  routePointMarkers.forEach((marker) => marker.setMap(null))
  if (walking?.clear) walking.clear()
  if (fallbackRouteLine) fallbackRouteLine.setMap(null)
  if (startMarker) startMarker.setMap(null)
  if (endMarker) endMarker.setMap(null)
  walkingRoutes = []
  fallbackRouteLines = []
  routePointMarkers = []
  walking = null
  fallbackRouteLine = null
  startMarker = null
  endMarker = null
}

function focusVisiblePois() {
  const activeIds = new Set([...props.highlightedIds, props.selectedPoiId].filter(Boolean))
  const targetPois = props.pois.filter((poi) => activeIds.has(poi.id) && isValidCoordinate(poi))
  if (targetPois.length) {
    focusLngLats(targetPois.map(poiLngLat))
    return
  }
  if (props.pois.length) focusLngLats(props.pois.filter(isValidCoordinate).map(poiLngLat))
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
  if (ids.length < 2) return []
  return ids
    .map((id) => props.pois.find((poi) => poi.id === id))
    .filter((poi) => poi && isValidCoordinate(poi))
    .map((poi) => normalizedPois.value.find((item) => item.id === poi.id) || poi)
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
  const initial = categoryInitial(poi.category)
  return `<div class="amap-poi-marker${active}"><span>${escapeHtml(initial)}</span><b>${escapeHtml(poi.name || '')}</b></div>`
}

function endpointContent(label, type) {
  return `<div class="amap-route-endpoint ${type}">${escapeHtml(label)}</div>`
}

function routePointLabel(index, total) {
  if (index === 0) return locale.value === 'en-US' ? 'Start' : '\u8d77\u70b9'
  if (index === total - 1) return locale.value === 'en-US' ? 'End' : '\u7ec8\u70b9'
  return locale.value === 'en-US' ? `Via ${index}` : `\u9014\u7ecf${index}`
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
