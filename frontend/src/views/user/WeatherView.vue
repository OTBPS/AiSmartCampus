<template>
  <AppShell>
    <div class="page-head weather-head">
      <div>
        <h1>{{ $t('weather.title') }}</h1>
        <p>{{ $t('weather.subtitle') }}</p>
      </div>
      <div class="weather-head-actions">
        <el-select
          v-model="selectedPoiId"
          filterable
          remote
          clearable
          :remote-method="searchPois"
          :loading="poiLoading"
          :placeholder="localText('poiPlaceholder')"
          :aria-label="localText('poiPlaceholder')"
          style="width: 280px"
          @change="loadWeather"
          @clear="loadWeather"
        >
          <el-option v-for="poi in poiOptions" :key="poi.id" :label="poi.name" :value="poi.id" />
        </el-select>
        <el-button :loading="loading" @click="loadWeather">{{ $t('common.refresh') }}</el-button>
      </div>
    </div>

    <el-alert v-if="error" class="weather-alert" type="error" :title="error" :closable="false" show-icon role="alert" />

    <div v-if="weather" class="weather-dashboard">
      <section class="weather-current-card">
        <div class="weather-current-main">
          <span class="weather-kicker">{{ weather.location.name }}</span>
          <div class="weather-temp-row">
            <div class="weather-orb" :class="weatherTone">
              <component :is="weatherIcon" />
            </div>
            <div>
              <strong>{{ valueOrDash(weather.now.temp) }}°</strong>
              <span>{{ weather.now.text || localText('unknown') }}</span>
            </div>
          </div>
          <p>{{ localText('feelsLike') }} {{ valueOrDash(weather.now.feelsLike) }}° · {{ localText('updated') }} {{ formatDateTime(weather.updateTime) }}</p>
        </div>
        <dl class="weather-metrics">
          <div>
            <dt>{{ localText('humidity') }}</dt>
            <dd>{{ valueOrDash(weather.now.humidity) }}%</dd>
          </div>
          <div>
            <dt>{{ localText('wind') }}</dt>
            <dd>{{ weather.now.windDir || '-' }} {{ weather.now.windScale || '-' }}</dd>
          </div>
          <div>
            <dt>{{ localText('precip') }}</dt>
            <dd>{{ valueOrDash(weather.now.precip) }} mm</dd>
          </div>
          <div>
            <dt>{{ localText('visibility') }}</dt>
            <dd>{{ valueOrDash(weather.now.vis) }} km</dd>
          </div>
        </dl>
      </section>

      <section class="weather-hourly-panel">
        <div class="panel-title-row">
          <div>
            <h2>{{ localText('hourly') }}</h2>
            <p>{{ localText('hourlyHint') }}</p>
          </div>
        </div>
        <div class="weather-hourly-strip" role="list">
          <article v-for="item in hourlyItems" :key="item.fxTime" class="weather-hour" role="listitem">
            <span>{{ formatHour(item.fxTime) }}</span>
            <div class="weather-hour-icon" :class="weatherIconTone(item)" :aria-label="item.text || localText('unknown')" role="img">
              <component :is="weatherIconFor(item)" />
            </div>
            <strong>{{ valueOrDash(item.temp) }}°</strong>
            <small>{{ item.pop ?? 0 }}%</small>
          </article>
        </div>
      </section>

      <section class="weather-advice-panel">
        <div class="panel-title-row">
          <div>
            <h2>{{ localText('advice') }}</h2>
            <p>{{ localText('adviceHint') }}</p>
          </div>
        </div>
        <div class="weather-advice-list">
          <article
            v-for="item in weather.recommendations"
            :key="`${item.type}-${item.title}-${item.poiId || 'campus'}`"
            class="weather-advice-card"
            :class="item.tone"
          >
            <el-tag size="small" effect="plain">{{ adviceType(item.type) }}</el-tag>
            <h3>{{ item.title }}</h3>
            <p>{{ item.detail }}</p>
            <button
              v-if="item.poiId"
              class="weather-map-link"
              type="button"
              :aria-label="localText('openMap')"
              @click="openMap(item.poiId)"
            >
              <el-icon><Location /></el-icon>
              {{ item.poiName || localText('openMap') }}
            </button>
          </article>
        </div>
      </section>

      <section class="weather-daily-panel">
        <div class="panel-title-row">
          <div>
            <h2>{{ localText('daily') }}</h2>
            <p>{{ localText('dailyHint') }}</p>
          </div>
        </div>
        <div class="weather-daily-grid">
          <article v-for="day in weather.daily" :key="day.fxDate" class="weather-day">
            <span>{{ formatDay(day.fxDate) }}</span>
            <strong>{{ valueOrDash(day.tempMin) }}° / {{ valueOrDash(day.tempMax) }}°</strong>
            <p>{{ day.textDay }} · {{ day.windDirDay }} {{ day.windScaleDay }}</p>
            <small>UV {{ valueOrDash(day.uvIndex) }} · {{ valueOrDash(day.precip) }} mm</small>
          </article>
        </div>
      </section>
    </div>

    <el-empty v-else-if="!loading" :description="localText('empty')" />
  </AppShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppShell from '../../components/AppShell.vue'
import { poiApi, weatherApi } from '../../api/modules'

const { locale } = useI18n()
const router = useRouter()
const loading = ref(false)
const poiLoading = ref(false)
const selectedPoiId = ref(null)
const poiOptions = ref([])
const weather = ref(null)
const error = ref('')

const hourlyItems = computed(() => (weather.value?.hourly || []).slice(0, 12))
const weatherTone = computed(() => {
  const text = weather.value?.now?.text || ''
  if (/雨|雪|rain|snow|shower|thunder/i.test(text)) return 'rain'
  if (/云|阴|cloud|overcast/i.test(text)) return 'cloud'
  return 'sun'
})
const weatherIcon = computed(() => {
  if (weatherTone.value === 'rain') return 'Umbrella'
  if (weatherTone.value === 'cloud') return 'MostlyCloudy'
  return 'Sunny'
})

onMounted(async () => {
  await Promise.all([searchPois(''), loadWeather()])
})

async function loadWeather() {
  loading.value = true
  error.value = ''
  try {
    weather.value = await weatherApi.campus({ poiId: selectedPoiId.value || undefined })
  } catch (err) {
    error.value = err.message || localText('loadFailed')
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

async function searchPois(keyword) {
  poiLoading.value = true
  try {
    poiOptions.value = await poiApi.list({ keyword, enabledOnly: true, limit: 20 })
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    poiLoading.value = false
  }
}

function openMap(poiId) {
  router.push({ path: '/map-chat', query: { poiId } })
}

function weatherIconFor(item) {
  const text = `${item?.text || ''} ${item?.icon || ''}`.toLowerCase()
  const code = String(item?.icon || '')
  if (/雷|thunder/.test(text) || ['302', '303', '304'].includes(code)) return 'Lightning'
  if (/雨|rain|shower|drizzle/.test(text) || /^3/.test(code)) return 'Pouring'
  if (/雪|snow|sleet/.test(text) || /^4/.test(code)) return 'MostlyCloudy'
  if (/雾|霾|沙|尘|fog|haze|dust|sand/.test(text) || /^5/.test(code)) return 'Cloudy'
  if (/阴|overcast/.test(text) || code === '104') return 'Cloudy'
  if (/云|cloud/.test(text) || ['101', '102', '103'].includes(code)) return 'MostlyCloudy'
  return 'Sunny'
}

function weatherIconTone(item) {
  const icon = weatherIconFor(item)
  if (icon === 'Lightning') return 'storm'
  if (icon === 'Pouring') return 'rain'
  if (icon === 'Cloudy' || icon === 'MostlyCloudy') return 'cloud'
  return 'sun'
}

function valueOrDash(value) {
  return value === null || value === undefined || value === '' ? '-' : value
}

function formatHour(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value || '-'
  return date.toLocaleTimeString(locale.value === 'en-US' ? 'en-US' : 'zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function formatDay(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value || '-'
  return date.toLocaleDateString(locale.value === 'en-US' ? 'en-US' : 'zh-CN', { weekday: 'short', month: 'numeric', day: 'numeric' })
}

function formatDateTime(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value || '-'
  return date.toLocaleString(locale.value === 'en-US' ? 'en-US' : 'zh-CN')
}

function adviceType(type) {
  const labels = localText('adviceTypes')
  return labels[type] || type
}

function localText(key) {
  const zh = {
    poiPlaceholder: '选择校园地点',
    unknown: '未知',
    feelsLike: '体感',
    updated: '更新',
    humidity: '湿度',
    wind: '风力',
    precip: '降水',
    visibility: '能见度',
    hourly: '24 小时趋势',
    hourlyHint: '温度点位与降水概率用于判断出行窗口',
    advice: '智能校园建议',
    adviceHint: '基于天气、POI 标签和校园路径偏好生成',
    daily: '7 天预报',
    dailyHint: '用于安排学习、运动和长距离步行',
    openMap: '回到 AI 地图查看地点',
    empty: '暂无天气数据',
    loadFailed: '天气加载失败',
    adviceTypes: {
      travel: '出行',
      study: '学习',
      sport: '运动',
      place: '地点',
      campus: '校园'
    }
  }
  const en = {
    poiPlaceholder: 'Select campus place',
    unknown: 'Unknown',
    feelsLike: 'Feels like',
    updated: 'Updated',
    humidity: 'Humidity',
    wind: 'Wind',
    precip: 'Precip',
    visibility: 'Visibility',
    hourly: '24-hour trend',
    hourlyHint: 'Temperature points and rain chance help pick travel windows',
    advice: 'Smart campus guidance',
    adviceHint: 'Generated from weather, POI tags, and campus movement patterns',
    daily: '7-day forecast',
    dailyHint: 'For study, exercise, and longer walking plans',
    openMap: 'View place on AI map',
    empty: 'No weather data',
    loadFailed: 'Weather failed to load',
    adviceTypes: {
      travel: 'Travel',
      study: 'Study',
      sport: 'Sport',
      place: 'Place',
      campus: 'Campus'
    }
  }
  return (locale.value === 'en-US' ? en : zh)[key] || key
}
</script>
