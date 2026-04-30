<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>AI 地图工作台</h1>
        <p>自然语言查找地点，系统返回结构化意图、地图动作和 POI 推荐。</p>
      </div>
      <el-tag type="success" effect="light">Mock AI JSON</el-tag>
    </div>

    <div class="workbench">
      <section class="panel ai-panel">
        <div class="panel-pad">
          <el-input
            v-model="question"
            type="textarea"
            :rows="3"
            placeholder="例如：找一个安静有插座的自习点"
          />
          <div class="quick-grid">
            <el-button v-for="item in quickPrompts" :key="item" @click="ask(item)">{{ item }}</el-button>
          </div>
        </div>

        <div class="messages">
          <article v-for="message in messages" :key="message.id" class="message" :class="message.role">
            <div class="message-meta">{{ message.role === 'user' ? '你' : 'AI 地图助手' }}</div>
            <div class="bubble">{{ message.content }}</div>
          </article>

          <div v-if="lastResponse" class="result-list">
            <el-alert :title="`识别意图：${lastResponse.intent}`" type="success" :closable="false" />
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
          <el-button type="primary" :loading="loading" style="width: 100%" @click="ask(question)">发送并执行地图动作</el-button>
        </div>
      </section>

      <section class="map-column">
        <CampusMap
          :pois="pois"
          :highlighted-ids="highlightedIds"
          :selected-poi-id="selectedPoi?.id"
          @select="selectPoi"
        />
        <section v-if="selectedPoi" class="panel detail-panel">
          <div>
            <h3>{{ selectedPoi.name }}</h3>
            <p>{{ selectedPoi.locationText }} · {{ selectedPoi.openStatus }} · {{ selectedPoi.tags }}</p>
            <p>{{ selectedPoi.remark }}</p>
          </div>
          <div>
            <el-button type="primary" @click="feedbackVisible = true">提交地点反馈</el-button>
            <el-button @click="simulateRoute">普通路线兜底</el-button>
          </div>
        </section>
      </section>
    </div>

    <el-dialog v-model="feedbackVisible" title="提交地点反馈" width="480px">
      <el-form label-position="top">
        <el-form-item label="反馈类型">
          <el-select v-model="feedback.type" style="width: 100%">
            <el-option label="地点信息错误" value="INFO_ERROR" />
            <el-option label="地点临时关闭" value="TEMP_CLOSED" />
            <el-option label="坐标位置不准" value="COORDINATE_ERROR" />
          </el-select>
        </el-form-item>
        <el-form-item label="反馈内容">
          <el-input v-model="feedback.content" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFeedback">提交</el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppShell from '../../components/AppShell.vue'
import CampusMap from '../../components/CampusMap.vue'
import { aiApi, feedbackApi, poiApi } from '../../api/modules'

const question = ref('找一个安静有插座的自习点')
const loading = ref(false)
const pois = ref([])
const selectedPoi = ref(null)
const lastResponse = ref(null)
const highlightedIds = ref([])
const feedbackVisible = ref(false)
const feedback = reactive({ type: 'INFO_ERROR', content: '' })
const messages = ref([
  { id: 1, role: 'assistant', content: '你可以问我找地点、推荐自习点或从一个地点去另一个地点。' }
])

const quickPrompts = ['找图书馆', '找打印店', '找一个安静有插座的自习点', '从宿舍 A 区去图书馆三楼自习区']
const resultPois = computed(() => lastResponse.value?.pois || [])

onMounted(loadPois)

async function loadPois() {
  pois.value = await poiApi.list({ enabledOnly: true })
  selectedPoi.value = pois.value[0] || null
}

async function ask(text) {
  if (!text) return
  question.value = text
  messages.value.push({ id: Date.now(), role: 'user', content: text })
  loading.value = true
  try {
    const response = await aiApi.chat(text)
    lastResponse.value = response
    messages.value.push({ id: Date.now() + 1, role: 'assistant', content: response.reply })
    applyMapActions(response)
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
  if (open?.poiId) {
    selectedPoi.value = pois.value.find((item) => item.id === open.poiId) || response.pois?.[0] || selectedPoi.value
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
    content: `已选择 ${selectedPoi.value.name} 作为路线目标，第一版将触发高德普通路线兜底。`
  })
}

async function submitFeedback() {
  if (!selectedPoi.value || !feedback.content) {
    ElMessage.warning('请填写反馈内容')
    return
  }
  await feedbackApi.submit({ poiId: selectedPoi.value.id, type: feedback.type, content: feedback.content })
  ElMessage.success('反馈已提交，等待管理员审核')
  feedback.content = ''
  feedbackVisible.value = false
}
</script>

