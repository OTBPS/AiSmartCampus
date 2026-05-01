<template>
  <div class="coordinate-preview">
    <div ref="previewRef" class="coordinate-map"></div>
    <div class="coordinate-preview-note">
      <strong>{{ title }}</strong>
      <span>{{ note }}</span>
      <span v-if="address">{{ address }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadAmap } from '../utils/amapLoader'

const props = defineProps({
  longitude: { type: [Number, String], default: 113.9345 },
  latitude: { type: [Number, String], default: 22.5331 }
})

const emit = defineEmits(['pick'])
const { locale } = useI18n()
const previewRef = ref(null)
const address = ref('')
const loadFailed = ref(false)
let AMapRef
let map
let marker
let geocoder

const title = computed(() => locale.value === 'en-US' ? 'AMap coordinate preview' : '高德坐标预览')
const note = computed(() => {
  if (loadFailed.value) {
    return locale.value === 'en-US' ? 'AMap is unavailable. Coordinates are still saved as numbers.' : '高德地图不可用，坐标仍会按数值保存。'
  }
  return locale.value === 'en-US' ? 'Click the map to update longitude and latitude.' : '点击地图可更新经纬度。'
})

onMounted(async () => {
  try {
    AMapRef = await loadAmap(['AMap.Scale', 'AMap.ToolBar', 'AMap.Geocoder'])
    initMap()
  } catch {
    loadFailed.value = true
  }
})

onBeforeUnmount(() => {
  if (map?.destroy) map.destroy()
})

watch(() => [props.longitude, props.latitude], () => updateMarker(), { deep: true })

function initMap() {
  if (!AMapRef || !previewRef.value) return
  map = new AMapRef.Map(previewRef.value, {
    zoom: 17,
    center: position(),
    viewMode: '2D',
    resizeEnable: true
  })
  if (AMapRef.Scale) map.addControl(new AMapRef.Scale())
  if (AMapRef.ToolBar) map.addControl(new AMapRef.ToolBar({ position: 'RT' }))
  if (AMapRef.Geocoder) geocoder = new AMapRef.Geocoder()
  map.on('click', (event) => {
    emit('pick', {
      longitude: Number(event.lnglat.lng.toFixed(6)),
      latitude: Number(event.lnglat.lat.toFixed(6))
    })
  })
  updateMarker()
}

function updateMarker() {
  if (!map || !AMapRef || !isValidPosition()) return
  const pos = position()
  if (!marker) {
    marker = new AMapRef.Marker({
      position: pos,
      anchor: 'bottom-center'
    })
    marker.setMap(map)
  } else {
    marker.setPosition(pos)
  }
  map.setCenter(pos)
  reverseGeocode(pos)
}

function reverseGeocode(pos) {
  if (!geocoder) return
  geocoder.getAddress(pos, (status, result) => {
    if (status === 'complete' && result?.regeocode?.formattedAddress) {
      address.value = result.regeocode.formattedAddress
    }
  })
}

function position() {
  return [Number(props.longitude), Number(props.latitude)]
}

function isValidPosition() {
  const lng = Number(props.longitude)
  const lat = Number(props.latitude)
  return Number.isFinite(lng) && Number.isFinite(lat)
}
</script>
