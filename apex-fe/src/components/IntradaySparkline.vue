<script setup>
import { computed } from 'vue'

const props = defineProps({
  points: {
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
    default: '日内走势',
  },
})

const padding = 3
const chartPoints = computed(() => props.points
  .map((point) => ({
    value: Number(typeof point === 'object' ? (point.price ?? point.value ?? point.closePrice) : point),
  }))
  .filter((point) => Number.isFinite(point.value)))
const firstValue = computed(() => chartPoints.value[0]?.value)
const lastValue = computed(() => chartPoints.value.at(-1)?.value)
const baselineValue = computed(() => {
  const value = Number(props.previousClose)
  return Number.isFinite(value) ? value : firstValue.value
})
const direction = computed(() => {
  if (!Number.isFinite(lastValue.value) || !Number.isFinite(baselineValue.value)) return 'flat'
  if (lastValue.value > baselineValue.value) return 'up'
  if (lastValue.value < baselineValue.value) return 'down'
  return 'flat'
})
const strokeColor = computed(() => ({
  up: '#d6495f',
  down: '#16866a',
  flat: '#64748b',
}[direction.value]))
const bounds = computed(() => {
  const values = chartPoints.value.map((point) => point.value)
  if (Number.isFinite(baselineValue.value)) values.push(baselineValue.value)
  if (!values.length) return { min: 0, max: 1 }
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || Math.max(Math.abs(max) * 0.01, 0.01)
  return { min: min - range * 0.08, max: max + range * 0.08 }
})
const plotHeight = computed(() => Math.max(1, props.height - padding * 2))
const xStep = computed(() => chartPoints.value.length > 1
  ? (props.width - padding * 2) / (chartPoints.value.length - 1)
  : 0)

function yFor(value) {
  const range = bounds.value.max - bounds.value.min || 1
  return padding + ((bounds.value.max - value) / range) * plotHeight.value
}

const linePath = computed(() => chartPoints.value.map((point, index) => {
  const x = padding + index * xStep.value
  const y = yFor(point.value)
  return `${index ? 'L' : 'M'}${x.toFixed(2)},${y.toFixed(2)}`
}).join(' '))
const areaPath = computed(() => {
  if (!linePath.value || !chartPoints.value.length) return ''
  const lastX = padding + (chartPoints.value.length - 1) * xStep.value
  return `${linePath.value} L${lastX.toFixed(2)},${(props.height - padding).toFixed(2)} L${padding},${(props.height - padding).toFixed(2)} Z`
})
const baselineY = computed(() => Number.isFinite(baselineValue.value) ? yFor(baselineValue.value) : null)
const latestPoint = computed(() => {
  if (!chartPoints.value.length) return null
  return {
    x: padding + (chartPoints.value.length - 1) * xStep.value,
    y: yFor(lastValue.value),
  }
})
const ariaLabel = computed(() => {
  const latest = Number.isFinite(lastValue.value) ? lastValue.value.toFixed(2) : '暂无'
  return `${props.label}，最新值 ${latest}`
})
</script>

<template>
  <svg
    v-if="chartPoints.length"
    class="intraday-sparkline"
    :style="{ height: `${height}px` }"
    :viewBox="`0 0 ${width} ${height}`"
    preserveAspectRatio="none"
    role="img"
    :aria-label="ariaLabel"
  >
    <line
      v-if="baselineY !== null"
      class="intraday-sparkline-baseline"
      :x1="padding"
      :x2="width - padding"
      :y1="baselineY"
      :y2="baselineY"
    />
    <path :d="areaPath" :fill="strokeColor" fill-opacity="0.08" />
    <path
      :d="linePath"
      fill="none"
      :stroke="strokeColor"
      stroke-linecap="round"
      stroke-linejoin="round"
      stroke-width="1.7"
      vector-effect="non-scaling-stroke"
    />
    <circle
      v-if="latestPoint"
      class="intraday-sparkline-latest"
      :cx="latestPoint.x"
      :cy="latestPoint.y"
      r="2.2"
      :fill="strokeColor"
    />
  </svg>
  <span v-else class="intraday-sparkline-empty">暂无日内走势</span>
</template>

<style scoped>
.intraday-sparkline {
  display: block;
  width: 100%;
  height: 48px;
  overflow: visible;
}

.intraday-sparkline-baseline {
  stroke: #cbd5e1;
  stroke-width: 1;
  stroke-dasharray: 3 3;
  vector-effect: non-scaling-stroke;
}

.intraday-sparkline-latest {
  stroke: #fff;
  stroke-width: 1.5;
  vector-effect: non-scaling-stroke;
}

.intraday-sparkline-empty {
  display: inline-flex;
  align-items: center;
  min-height: 48px;
  color: var(--muted);
  font-size: 11px;
  white-space: nowrap;
}
</style>
