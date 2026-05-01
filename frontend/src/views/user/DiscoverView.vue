<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>{{ $t('discover.title') }}</h1>
        <p>{{ $t('discover.subtitle') }}</p>
      </div>
      <el-button type="primary" @click="load">{{ $t('common.refresh') }}</el-button>
    </div>

    <div class="card-grid">
      <article v-for="post in posts" :key="post.id" class="discover-card">
        <el-tag size="small">{{ post.category }}</el-tag>
        <h3>{{ post.title }}</h3>
        <p>{{ post.summary }}</p>
        <el-button v-if="post.poiId" link type="primary" @click="$router.push('/map-chat')">{{ $t('discover.backToMap') }}</el-button>
      </article>
    </div>
  </AppShell>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AppShell from '../../components/AppShell.vue'
import { discoverApi } from '../../api/modules'

const posts = ref([])
onMounted(load)

async function load() {
  posts.value = await discoverApi.posts()
}
</script>
