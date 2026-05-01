<template>
  <div class="locale-switch" :class="{ compact }">
    <span v-if="!compact" class="locale-label">{{ $t('common.language') }}</span>
    <div class="locale-track" role="group" :aria-label="$t('common.language')">
      <button
        v-for="option in options"
        :key="option.value"
        class="locale-option"
        :class="{ active: currentLocale === option.value }"
        type="button"
        @click="currentLocale = option.value"
      >
        {{ option.label }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { setLocale } from '../i18n'

defineProps({
  compact: { type: Boolean, default: false }
})

const { locale, t } = useI18n()

const options = computed(() => [
  { label: t('common.zh'), value: 'zh-CN' },
  { label: t('common.en'), value: 'en-US' }
])

const currentLocale = computed({
  get: () => locale.value,
  set: (value) => setLocale(value)
})
</script>
