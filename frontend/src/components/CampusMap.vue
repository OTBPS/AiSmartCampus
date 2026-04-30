<template>
  <section class="map-shell" aria-label="校园地图">
    <div ref="amapContainer" class="map-placeholder"></div>
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
        起
      </span>
      <span
        v-if="routeLine"
        class="route-endpoint end"
        :style="{ left: `${routeLine.to.x}%`, top: `${routeLine.to.y}%` }"
      >
        终
      </span>
      <button
        v-for="poi in normalizedPois"
        :key="poi.id"
        class="pin"
        :class="{ active: highlightedIds.includes(poi.id) || selectedPoiId === poi.id }"
        :style="{ left: `${poi.x}%`, top: `${poi.y}%` }"
        type="button"
        :aria-label="`查看${poi.name}`"
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
import { computed, onMounted, ref, watch } from 'vue'

const props = defineProps({
  pois: { type: Array, default: () => [] },
  highlightedIds: { type: Array, default: () => [] },
  selectedPoiId: { type: Number, default: null },
  routeAction: { type: Object, default: null }
})

const emit = defineEmits(['select'])
const amapContainer = ref(null)
const amapReady = ref(false)
let map
let markers = []

const normalizedPois = computed(() => {
  if (!props.pois.length) return []
  const lngs = props.pois.map((poi) => Number(poi.longitude))
  const lats = props.pois.map((poi) => Number(poi.latitude))
  const minLng = Math.min(...lngs)
  const maxLng = Math.max(...lngs)
  const minLat = Math.min(...lats)
  const maxLat = Math.max(...lats)
  return props.pois.map((poi) => {
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
  const ids = props.routeAction?.poiIds || []
  if (ids.length < 2) return null
  const from = normalizedPois.value.find((poi) => poi.id === ids[0])
  const to = normalizedPois.value.find((poi) => poi.id === ids[ids.length - 1])
  if (!from || !to) return null
  return { from, to }
})

onMounted(() => {
  const key = import.meta.env.VITE_AMAP_KEY
  if (!key || window.AMap) {
    if (window.AMap && key) initAmap()
    return
  }
  const script = document.createElement('script')
  script.src = `https://webapi.amap.com/maps?v=2.0&key=${key}`
  script.onload = initAmap
  document.head.appendChild(script)
})

watch(() => [props.pois, props.highlightedIds, props.selectedPoiId], () => {
  if (amapReady.value) renderMarkers()
}, { deep: true })

function initAmap() {
  if (!window.AMap || !amapContainer.value) return
  amapReady.value = true
  map = new window.AMap.Map(amapContainer.value, {
    zoom: 17,
    center: props.pois[0] ? [Number(props.pois[0].longitude), Number(props.pois[0].latitude)] : [113.9345, 22.5331]
  })
  renderMarkers()
}

function renderMarkers() {
  if (!map || !window.AMap) return
  markers.forEach((marker) => map.remove(marker))
  markers = props.pois.map((poi) => {
    const marker = new window.AMap.Marker({
      position: [Number(poi.longitude), Number(poi.latitude)],
      title: poi.name
    })
    marker.on('click', () => emit('select', poi))
    marker.setMap(map)
    return marker
  })
}

function categoryInitial(category) {
  const map = {
    STUDY: '学',
    DINING: '食',
    TEACHING: '教',
    DORM: '宿',
    SERVICE: '服'
  }
  return map[category] || '点'
}
</script>
