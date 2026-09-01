<script setup>
import { computed } from 'vue'

const props = defineProps({
  points: {
    type: Array,
    default: null,
  },
  bars: {
    type: Array,
    default: () => [],
  },
  previousClose: {
    type: [Number, String],
    default: null,
  },
  width: {
    type: Number,
    default: 180,
  },
  height: {
    type: Number,
    default: 48,
  },
  label: {
    type: String,
    default: '日内 K 线',
  },
})

const padding = 3
const sourcePoints = computed(() => props.points ?? props.bars)
const chartPoints = computed(() => sourcePoints.value
  .map((bar) => Number(typeof bar === 'object' ? (bar?.closePrice ?? bar?.close ?? bar?.value) : bar))
  .filter(Number.isFinite))
const latestClose = computed(() => chartPoints.value.at(-1))
const referenceClose = computed(() => {
  if (props.previousClose === null || props.previousClose === undefined || props.previousClose === '') {
    return chartPoints.value[0]
  }
  const previousClose = Number(props.previousClose)
  return Number.isFinite(previousClose) ? previousClose : chartPoints.value[0]
})
const lineColor = computed(() => {
  if (!Number.isFinite(latestClose.value) || !Number.isFinite(referenceClose.value)) return '#64748b'
  if (latestClose.value > referenceClose.value) return '#d6495f'
  if (latestClose.value < referenceClose.value) return '#16866a'
  return '#64748b'
})
const bounds = computed(() => {
  const values = [...chartPoints.value]
  if (Number.isFinite(referenceClose.value)) values.push(referenceClose.value)
  if (!values.length) return { min: 0, max: 1 }
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || Math.max(Math.abs(max) * 0.01, 0.01)
  return { min: min - range * 0.08, max: max + range * 0.08 }
})
const plotWidth = computed(() => Math.max(1, props.width - padding * 2))
const plotHeight = computed(() => Math.max(1, props.height - padding * 2))
const xStep = computed(() => chartPoints.value.length > 1
  ? plotWidth.value / (chartPoints.value.length - 1)
  : 0)

function yFor(value) {
  const range = bounds.value.max - bounds.value.min || 1
  return padding + ((bounds.value.max - value) / range) * plotHeight.value
}

const linePath = computed(() => chartPoints.value.map((value, index) => {
  const x = padding + index * xStep.value
  const y = yFor(value)
  return `${index ? 'L' : 'M'}${x.toFixed(2)},${y.toFixed(2)}`
}).join(' '))
const baselineY = computed(() => Number.isFinite(referenceClose.value) ? yFor(referenceClose.value) : null)
const ariaLabel = computed(() => {
  const latestText = Number.isFinite(latestClose.value) ? latestClose.value.toFixed(2) : '暂无'
  return `${props.label}，${chartPoints.value.length} 个点，最新 ${latestText}`
})
</script>

<template>
  <svg
    v-if="chartPoints.length"
    class="intraday-kline-thumbnail"
    :style="{ height: `${height}px` }"
    :viewBox="`0 0 ${width} ${height}`"
    preserveAspectRatio="none"
    role="img"
    :aria-label="ariaLabel"
  >
    <line
      v-if="baselineY !== null"
      class="intraday-kline-baseline"
      :x1="padding"
      :x2="width - padding"
      :y1="baselineY"
      :y2="baselineY"
    />
    <path
      class="intraday-kline-line"
      :d="linePath"
      fill="none"
      :stroke="lineColor"
      stroke-linecap="round"
      stroke-linejoin="round"
      stroke-width="1.2"
      vector-effect="non-scaling-stroke"
    />
  </svg>
</template>

<style scoped>
.intraday-kline-thumbnail {
  display: block;
  width: 100%;
  overflow: hidden;
}

.intraday-kline-baseline {
  stroke: #cbd5e1;
  stroke-width: 0.8;
  stroke-dasharray: 3 3;
  vector-effect: non-scaling-stroke;
}
</style>
