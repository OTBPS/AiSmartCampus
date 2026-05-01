<template>
  <section class="map-shell" :aria-label="$t('nav.map')">
    <div ref="amapContainer" class="map-placeholder" :class="{ active: amapReady }"></div>
    <div v-if="amapFallbackReason" class="map-fallback-badge">{{ amapFallbackReason }}</div>

    <template v-if="!amapReady">
      <div class="map-road main"></div>
      <div class="map-road cross"></div>
      <svg v-if="routeLine" class="route-overlay" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
        <line
          :x1="routeLine.from.x"
          :y1="routeLine.from.y"
          :x2="routeLine.to.x"
          :y2="routeLine.to.y"
        />
      </svg>
      <span
        v-if="routeLine"
        class="route-endpoint start"
        :style="{ left: `${routeLine.from.x}%`, top: `${routeLine.from.y}%` }"
      >
        {{ $t('map.start') }}
      </span>
      <span
        v-if="routeLine"
        class="route-endpoint end"
        :style="{ left: `${routeLine.to.x}%`, top: `${routeLine.to.y}%` }"
      >
        {{ $t('map.end') }}
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

const emit = defineEmits(['select'])
const { locale } = useI18n()
const amapContainer = ref(null)
const amapReady = ref(false)
const amapFallbackReason = ref('')
let map
let AMapRef
let walking
let fallbackRouteLine
let startMarker
let endMarker
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

const routeLine = computed(() => {
  const routePois = routePoisByAction()
  if (routePois.length < 2) return null
  return { from: routePois[0], to: routePois[routePois.length - 1] }
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
    marker.on('click', () => emit('select', poi))
    marker.setMap(map)
    return marker
  })
}

function renderRoute() {
  clearRoute()
  const routePois = routePoisByAction()
  if (!map || !AMapRef || routePois.length < 2) return
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
}

function clearRoute() {
  if (walking?.clear) walking.clear()
  if (fallbackRouteLine) fallbackRouteLine.setMap(null)
  if (startMarker) startMarker.setMap(null)
  if (endMarker) endMarker.setMap(null)
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
  return ids.map((id) => props.pois.find((poi) => poi.id === id)).filter((poi) => poi && isValidCoordinate(poi))
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
