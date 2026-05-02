<template>
  <AppShell>
    <div class="page-head weather-head">
      <div>
        <h1>{{ $t('weather.title') }}</h1>
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
              <span>{{ displayWeatherText(weather.now.text) }}</span>
            </div>
          </div>
        </div>
        <dl class="weather-metrics">
          <div>
            <dt>{{ localText('humidity') }}</dt>
            <dd>{{ valueOrDash(weather.now.humidity) }}%</dd>
          </div>
          <div>
            <dt>{{ localText('wind') }}</dt>
            <dd>{{ displayWindDir(weather.now.windDir) }} {{ weather.now.windScale || '-' }}</dd>
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
            <h3>{{ recommendationTitle(item) }}</h3>
            <p>{{ recommendationDetail(item) }}</p>
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
          </div>
        </div>
        <div class="weather-daily-grid">
          <article v-for="day in weather.daily" :key="day.fxDate" class="weather-day">
            <span>{{ formatDay(day.fxDate) }}</span>
            <strong>{{ valueOrDash(day.tempMin) }}° / {{ valueOrDash(day.tempMax) }}°</strong>
            <p>{{ displayDailySummary(day) }}</p>
            <small>UV {{ valueOrDash(day.uvIndex) }} · {{ valueOrDash(day.precip) }} mm</small>
          </article>
        </div>
      </section>
    </div>

    <el-empty v-else-if="!loading" :description="localText('empty')" />
  </AppShell>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
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

watch(locale, () => {
  if (weather.value) loadWeather()
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

function displayWeatherText(value) {
  if (!value) return localText('unknown')
  if (locale.value !== 'en-US') return value
  return weatherTextMap[value] || value
}

function displayWindDir(value) {
  if (!value) return '-'
  if (locale.value !== 'en-US') return value
  return windDirectionMap[value] || value
}

function displayDailySummary(day) {
  const text = displayWeatherText(day.textDay)
  const wind = displayWindDir(day.windDirDay)
  return `${text} · ${wind} ${day.windScaleDay || '-'}`
}

function recommendationTitle(item) {
  if (locale.value !== 'en-US' || !containsCjk(item.title)) return item.title
  return recommendationFallback(item).title
}

function recommendationDetail(item) {
  if (locale.value !== 'en-US' || !containsCjk(item.detail)) return item.detail
  return recommendationFallback(item).detail
}

function recommendationFallback(item) {
  const key = `${item.type}:${item.tone}`
  return recommendationFallbacks[key] || recommendationFallbacks[item.type] || {
    title: item.title,
    detail: item.detail
  }
}

function containsCjk(value) {
  return /[\u3400-\u9fff]/.test(`${value || ''}`)
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

const weatherTextMap = {
  晴: 'Sunny',
  多云: 'Cloudy',
  少云: 'Partly cloudy',
  晴间多云: 'Mostly sunny',
  阴: 'Overcast',
  小雨: 'Light rain',
  中雨: 'Moderate rain',
  大雨: 'Heavy rain',
  暴雨: 'Rainstorm',
  阵雨: 'Shower',
  雷阵雨: 'Thunder shower',
  雨: 'Rain',
  小雪: 'Light snow',
  中雪: 'Moderate snow',
  大雪: 'Heavy snow',
  雪: 'Snow',
  雾: 'Fog',
  霾: 'Haze'
}

const windDirectionMap = {
  N: 'North wind',
  NNE: 'North-northeast wind',
  NE: 'Northeast wind',
  ENE: 'East-northeast wind',
  E: 'East wind',
  ESE: 'East-southeast wind',
  SE: 'Southeast wind',
  SSE: 'South-southeast wind',
  S: 'South wind',
  SSW: 'South-southwest wind',
  SW: 'Southwest wind',
  WSW: 'West-southwest wind',
  W: 'West wind',
  WNW: 'West-northwest wind',
  NW: 'Northwest wind',
  NNW: 'North-northwest wind',
  北风: 'North wind',
  东北风: 'Northeast wind',
  东风: 'East wind',
  东南风: 'Southeast wind',
  南风: 'South wind',
  西南风: 'Southwest wind',
  西风: 'West wind',
  西北风: 'Northwest wind',
  无持续风向: 'Variable wind'
}

const recommendationFallbacks = {
  'travel:warn': {
    title: 'Rain-friendly route',
    detail: 'Prioritize sheltered corridors and leave extra walking time.'
  },
  'travel:cool': {
    title: 'Reduce exposed walking',
    detail: 'Wind or low temperature can make open paths uncomfortable; prefer direct routes.'
  },
  'study:hot': {
    title: 'Indoor study is safer',
    detail: 'High temperature makes long outdoor stays uncomfortable; choose cool study spaces.'
  },
  'sport:good': {
    title: 'Outdoor activity window',
    detail: 'The next few hours look stable for outdoor exercise or campus observation.'
  },
  'place:map': {
    title: 'View this place on the AI map',
    detail: 'Open the selected POI on the AI map to combine weather and navigation context.'
  },
  campus: {
    title: 'Campus weather is steady',
    detail: 'No strong weather constraint detected; plan routes by time and destination.'
  }
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
