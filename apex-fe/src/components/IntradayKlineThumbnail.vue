<script setup>
import { computed } from 'vue'

const props = defineProps({
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
const chartBars = computed(() => props.bars
  .map((bar) => ({
    open: Number(bar?.openPrice ?? bar?.open),
    close: Number(bar?.closePrice ?? bar?.close),
    high: Number(bar?.highPrice ?? bar?.high),
    low: Number(bar?.lowPrice ?? bar?.low),
  }))
  .filter((bar) => [bar.open, bar.close, bar.high, bar.low].every(Number.isFinite)))
const bounds = computed(() => {
  const values = chartBars.value.flatMap((bar) => [bar.high, bar.low])
  const previousClose = Number(props.previousClose)
  if (Number.isFinite(previousClose)) values.push(previousClose)
  if (!values.length) return { min: 0, max: 1 }
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || Math.max(Math.abs(max) * 0.01, 0.01)
  return { min: min - range * 0.06, max: max + range * 0.06 }
})
const plotWidth = computed(() => Math.max(1, props.width - padding * 2))
const plotHeight = computed(() => Math.max(1, props.height - padding * 2))
const candleStep = computed(() => plotWidth.value / Math.max(1, chartBars.value.length))
const candleWidth = computed(() => Math.max(1, Math.min(4, candleStep.value * 0.58)))

function yFor(value) {
  const range = bounds.value.max - bounds.value.min || 1
  return padding + ((bounds.value.max - value) / range) * plotHeight.value
}

const candles = computed(() => chartBars.value.map((bar, index) => {
  const centerX = padding + candleStep.value * (index + 0.5)
  const openY = yFor(bar.open)
  const closeY = yFor(bar.close)
  return {
    centerX,
    highY: yFor(bar.high),
    lowY: yFor(bar.low),
    bodyX: centerX - candleWidth.value / 2,
    bodyY: Math.min(openY, closeY),
    bodyHeight: Math.max(1, Math.abs(openY - closeY)),
    color: bar.close >= bar.open ? '#d6495f' : '#16866a',
  }
}))
const baselineY = computed(() => {
  const previousClose = Number(props.previousClose)
  return Number.isFinite(previousClose) ? yFor(previousClose) : null
})
const ariaLabel = computed(() => {
  const latestClose = chartBars.value.at(-1)?.close
  const latestText = Number.isFinite(latestClose) ? latestClose.toFixed(2) : '暂无'
  return `${props.label}，${chartBars.value.length} 根，最新 ${latestText}`
})
</script>

<template>
  <svg
    v-if="candles.length"
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
    <g v-for="(candle, index) in candles" :key="index">
      <line
        class="intraday-kline-wick"
        :x1="candle.centerX"
        :x2="candle.centerX"
        :y1="candle.highY"
        :y2="candle.lowY"
        :stroke="candle.color"
      />
      <rect
        class="intraday-kline-body"
        :x="candle.bodyX"
        :y="candle.bodyY"
        :width="candleWidth"
        :height="candle.bodyHeight"
        :fill="candle.color"
      />
    </g>
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
  stroke-width: 1;
  stroke-dasharray: 3 3;
  vector-effect: non-scaling-stroke;
}

.intraday-kline-wick {
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}

.intraday-kline-body {
  shape-rendering: crispEdges;
}
</style>
