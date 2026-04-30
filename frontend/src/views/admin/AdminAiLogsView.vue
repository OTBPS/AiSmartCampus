<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>AI 交互记录</h1>
        <p>用于展示用户问题、识别意图和地图动作，不扩展成复杂日志平台。</p>
      </div>
      <el-button type="primary" @click="load">刷新</el-button>
    </div>

    <section class="table-panel">
      <el-table :data="logs" height="620">
        <el-table-column prop="question" label="用户问题" min-width="240" />
        <el-table-column prop="intent" label="意图" width="150" />
        <el-table-column prop="reply" label="AI 回复" min-width="280" />
        <el-table-column prop="mapActionsJson" label="地图动作 JSON" min-width="280" />
      </el-table>
    </section>
  </AppShell>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AppShell from '../../components/AppShell.vue'
import { aiApi } from '../../api/modules'

const logs = ref([])
onMounted(load)

async function load() {
  logs.value = await aiApi.logs()
}
</script>

