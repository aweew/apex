<script setup>
import { computed } from 'vue'

const props = defineProps({
  score: { type: [Number, String], default: null },
  max: { type: Number, default: 100 },
})

const num = computed(() => {
  const n = Number(props.score)
  return Number.isNaN(n) ? null : n
})

const pct = computed(() => {
  if (num.value == null) return 0
  return Math.max(0, Math.min(100, (num.value / props.max) * 100))
})

const tone = computed(() => {
  if (num.value == null) return ''
  if (num.value >= 88) return 'hot'
  if (num.value >= 75) return 'warm'
  return ''
})

const label = computed(() => (num.value == null ? '-' : num.value.toFixed(1)))
</script>

<template>
  <span class="score-bar" :title="label">
    <span class="score-bar-track">
      <span class="score-bar-fill" :class="tone" :style="{ width: pct + '%' }" />
    </span>
    <span class="score-bar-num">{{ label }}</span>
  </span>
</template>
