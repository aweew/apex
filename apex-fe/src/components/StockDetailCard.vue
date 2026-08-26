<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { aggregateBars } from '../utils/kline'

const props = defineProps({
  basic: {
    type: Object,
    default: () => ({}),
  },
  bars: {
    type: Array,
    default: () => [],
  },
  intraday: {
    type: Object,
    default: null,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  initialPeriod: {
    type: String,
    default: 'day',
    validator: (value) => ['intraday', 'day', 'week', 'month'].includes(value),
  },
})

const emit = defineEmits(['period-change'])
const chartRef = ref(null)
const activePeriod = ref(props.initialPeriod)
const periods = [
  { value: 'intraday', label: '分时' },
  { value: 'day', label: '日K' },
  { value: 'week', label: '周K' },
  { value: 'month', label: '月K' },
]
const maColors = {
  MA5: '#d88b18',
  MA10: '#2786c7',
  MA20: '#b83aae',
}
let chart
let resizeObserver

const isIntraday = computed(() => activePeriod.value === 'intraday')
const chartBars = computed(() => aggregateBars(props.bars, activePeriod.value))
const intradayPoints = computed(() => props.intraday?.points || [])
const hasChartData = computed(() => (
  isIntraday.value ? intradayPoints.value.length > 0 : chartBars.value.length > 0
))
const directionClass = computed(() => {
  const percentage = Number(props.basic?.pctChg)
  if (!Number.isFinite(percentage) || percentage === 0) return 'is-flat'
  return percentage > 0 ? 'is-up' : 'is-down'
})
const latestPrice = computed(() => formatPrice(props.basic?.latestPrice))
const changePercentage = computed(() => formatSigned(props.basic?.pctChg, 2, '%'))
const changeAmount = computed(() => {
  if (props.basic?.latestPrice == null || props.basic?.pctChg == null) return '-'
  const price = Number(props.basic?.latestPrice)
  const percentage = Number(props.basic?.pctChg)
  if (!Number.isFinite(price) || !Number.isFinite(percentage) || percentage === -100) return '-'
  return formatSigned((price * percentage) / (100 + percentage), 2)
})
const currentBar = computed(() => chartBars.value.at(-1))
const volumeText = computed(() => {
  if (isIntraday.value && !intradayPoints.value.length) return '-'
  return formatVolume(isIntraday.value
    ? intradayPoints.value.reduce((total, point) => total + (Number(point.volume) || 0), 0)
    : currentBar.value?.volume)
})
const maValues = computed(() => {
  if (isIntraday.value || !chartBars.value.length) return []
  const closes = chartBars.value.map((bar) => Number(bar.closePrice))
  return [5, 10, 20].map((period) => ({
    name: `MA${period}`,
    value: movingAverage(closes, period).at(-1),
    color: maColors[`MA${period}`],
  }))
})
const chartLabel = computed(() => {
  const stockName = props.basic?.name || '个股'
  const periodName = periods.find((item) => item.value === activePeriod.value)?.label || ''
  return `${stockName}${periodName}行情图`
})

function formatPrice(value) {
  if (value == null || value === '') return '-'
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(2) : '-'
}

function formatSigned(value, digits = 2, suffix = '') {
  if (value == null || value === '') return '-'
  const number = Number(value)
  if (!Number.isFinite(number)) return '-'
  const sign = number > 0 ? '+' : ''
  return `${sign}${number.toFixed(digits)}${suffix}`
}

function formatVolume(value) {
  if (value == null || value === '') return '-'
  const number = Number(value)
  if (!Number.isFinite(number)) return '-'
  if (Math.abs(number) >= 100000000) return `${(number / 100000000).toFixed(2)}亿`
  if (Math.abs(number) >= 10000) return `${(number / 10000).toFixed(2)}万`
  return number.toFixed(0)
}

function movingAverage(values, period) {
  const result = new Array(values.length).fill(null)
  let sum = 0
  for (let index = 0; index < values.length; index += 1) {
    sum += values[index]
    if (index >= period) sum -= values[index - period]
    if (index >= period - 1) result[index] = Number((sum / period).toFixed(4))
  }
  return result
}

function selectPeriod(period) {
  if (activePeriod.value === period) {
    if (period === 'intraday' && !intradayPoints.value.length) emit('period-change', period)
    return
  }
  activePeriod.value = period
  emit('period-change', period)
}

function ensureChart() {
  if (!chartRef.value || chartRef.value.clientWidth < 40) return false
  if (!chart) chart = echarts.init(chartRef.value, null, { renderer: 'canvas' })
  return true
}

function baseChartOption() {
  return {
    animation: false,
    backgroundColor: 'transparent',
    textStyle: {
      color: '#64748b',
      fontFamily: 'var(--font-ui)',
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: 'rgba(255, 255, 255, 0.96)',
      borderColor: '#dce3ed',
      textStyle: { color: '#18212f', fontSize: 12 },
      axisPointer: { type: 'cross', lineStyle: { color: '#94a3b8' } },
    },
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    grid: [
      { left: 48, right: 12, top: 16, height: '62%' },
      { left: 48, right: 12, top: '75%', height: '16%' },
    ],
  }
}

function renderIntraday() {
  const points = intradayPoints.value
  const times = points.map((point) => point.time)
  const prices = points.map((point) => Number(point.price))
  const averagePrices = points.map((point) => Number(point.avgPrice))
  const previousClose = Number(props.intraday?.preClose || prices[0] || 0)
  const volumeData = points.map((point, index) => ({
    value: Number(point.volume) || 0,
    itemStyle: {
      color: prices[index] >= (index === 0 ? previousClose : prices[index - 1]) ? '#e5484d' : '#23855a',
    },
  }))
  chart.setOption({
    ...baseChartOption(),
    xAxis: [
      {
        type: 'category',
        data: times,
        boundaryGap: false,
        axisTick: { show: false },
        axisLine: { lineStyle: { color: '#dce3ed' } },
        axisLabel: { color: '#64748b', hideOverlap: true },
      },
      {
        type: 'category',
        gridIndex: 1,
        data: times,
        boundaryGap: true,
        axisTick: { show: false },
        axisLine: { show: false },
        axisLabel: { show: false },
      },
    ],
    yAxis: [
      {
        scale: true,
        axisTick: { show: false },
        axisLine: { show: false },
        axisLabel: { color: '#8793a5', formatter: (value) => Number(value).toFixed(2) },
        splitLine: { lineStyle: { color: '#e5eaf1' } },
      },
      {
        scale: true,
        gridIndex: 1,
        axisLabel: { show: false },
        axisTick: { show: false },
        axisLine: { show: false },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '价格',
        type: 'line',
        data: prices,
        symbol: 'none',
        lineStyle: { width: 1.6, color: '#1669c9' },
      },
      {
        name: '均价',
        type: 'line',
        data: averagePrices,
        symbol: 'none',
        lineStyle: { width: 1.2, color: maColors.MA5 },
      },
      {
        name: '成交量',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: volumeData,
        barMaxWidth: 8,
      },
    ],
  }, true)
}

function renderKline() {
  const currentBars = chartBars.value
  const dates = currentBars.map((bar) => bar.tradeDate)
  const closes = currentBars.map((bar) => Number(bar.closePrice))
  const candleData = currentBars.map((bar) => [
    Number(bar.openPrice),
    Number(bar.closePrice),
    Number(bar.lowPrice),
    Number(bar.highPrice),
  ])
  const volumeData = currentBars.map((bar) => ({
    value: Number(bar.volume) || 0,
    itemStyle: { color: Number(bar.closePrice) >= Number(bar.openPrice) ? '#e5484d' : '#23855a' },
  }))
  const visibleCount = activePeriod.value === 'day' ? 60 : 48
  const zoomStart = currentBars.length > visibleCount
    ? Number((((currentBars.length - visibleCount) / currentBars.length) * 100).toFixed(2))
    : 0

  chart.setOption({
    ...baseChartOption(),
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1], start: zoomStart, end: 100, zoomOnMouseWheel: false },
    ],
    xAxis: [
      {
        type: 'category',
        data: dates,
        boundaryGap: true,
        axisTick: { show: false },
        axisLine: { lineStyle: { color: '#dce3ed' } },
        axisLabel: { color: '#64748b', hideOverlap: true },
      },
      {
        type: 'category',
        gridIndex: 1,
        data: dates,
        boundaryGap: true,
        axisTick: { show: false },
        axisLine: { show: false },
        axisLabel: { show: false },
      },
    ],
    yAxis: [
      {
        scale: true,
        axisTick: { show: false },
        axisLine: { show: false },
        axisLabel: { color: '#8793a5', formatter: (value) => Number(value).toFixed(2) },
        splitLine: { lineStyle: { color: '#e5eaf1' } },
      },
      {
        scale: true,
        gridIndex: 1,
        axisLabel: { show: false },
        axisTick: { show: false },
        axisLine: { show: false },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: 'K线',
        type: 'candlestick',
        data: candleData,
        itemStyle: {
          color: '#ffffff',
          color0: '#23855a',
          borderColor: '#e5484d',
          borderColor0: '#23855a',
        },
      },
      ...[5, 10, 20].map((period) => ({
        name: `MA${period}`,
        type: 'line',
        data: movingAverage(closes, period),
        symbol: 'none',
        connectNulls: true,
        lineStyle: { width: 1.2, color: maColors[`MA${period}`] },
      })),
      {
        name: '成交量',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: volumeData,
        barMaxWidth: 8,
      },
    ],
  }, true)
}

async function renderChart() {
  await nextTick()
  if (!hasChartData.value || !ensureChart()) {
    chart?.clear()
    return
  }
  if (isIntraday.value) renderIntraday()
  else renderKline()
  chart.resize()
}

watch(
  () => [activePeriod.value, props.bars, props.intraday],
  renderChart,
  { deep: true },
)

onMounted(() => {
  resizeObserver = new ResizeObserver(() => renderChart())
  if (chartRef.value) resizeObserver.observe(chartRef.value)
  renderChart()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = undefined
})
</script>

<template>
  <article class="stock-detail-card" :class="directionClass" v-loading="loading">
    <header class="stock-detail-card__header">
      <div class="stock-detail-card__identity">
        <h2>{{ basic?.name || '股票详情' }}</h2>
        <p>{{ basic?.code || '-' }}</p>
      </div>
      <div class="stock-detail-card__quote" aria-live="polite">
        <strong>{{ latestPrice }}</strong>
        <div>
          <span>{{ changeAmount }}</span>
          <span>{{ changePercentage }}</span>
        </div>
      </div>
    </header>

    <nav class="stock-detail-card__periods" aria-label="个股行情周期">
      <button
        v-for="item in periods"
        :key="item.value"
        type="button"
        :data-period="item.value"
        :class="{ 'is-active': activePeriod === item.value }"
        :aria-pressed="activePeriod === item.value"
        @click="selectPeriod(item.value)"
      >
        {{ item.label }}
      </button>
    </nav>

    <div class="stock-detail-card__meta">
      <div v-if="!isIntraday" class="stock-detail-card__ma" aria-label="移动平均线">
        <span v-for="item in maValues" :key="item.name" :style="{ color: item.color }">
          {{ item.name }}: {{ formatPrice(item.value) }}
        </span>
      </div>
      <span v-else class="stock-detail-card__intraday-label">价格 / 均价</span>
      <span v-if="!isIntraday" class="stock-detail-card__adjustment">前复权</span>
    </div>

    <div class="stock-detail-card__chart-wrap">
      <div
        ref="chartRef"
        class="stock-detail-card__chart"
        role="img"
        :aria-label="chartLabel"
      />
      <div v-if="!hasChartData && !loading" class="stock-detail-card__empty">
        {{ isIntraday ? '暂无分时数据' : '暂无K线数据' }}
      </div>
    </div>

    <div class="stock-detail-card__volume">
      <span>成交量</span>
      <strong>{{ volumeText }}</strong>
    </div>
  </article>
</template>

<style scoped>
.stock-detail-card {
  --quote-color: var(--ink);

  width: 100%;
  min-width: 0;
  padding: 24px;
  overflow: hidden;
  border: 1px solid var(--glass-border);
  border-radius: 8px;
  background: var(--glass-strong);
  box-shadow: var(--shadow-soft);
}

.stock-detail-card.is-up {
  --quote-color: var(--up);
}

.stock-detail-card.is-down {
  --quote-color: var(--down);
}

.stock-detail-card__header {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.stock-detail-card__identity {
  min-width: 0;
}

.stock-detail-card__identity h2 {
  margin: 0;
  overflow: hidden;
  color: var(--ink);
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stock-detail-card__identity p {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 17px;
  font-variant-numeric: tabular-nums;
  line-height: 1.3;
}

.stock-detail-card__quote {
  flex: 0 0 auto;
  color: var(--quote-color);
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.stock-detail-card__quote strong {
  display: block;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.1;
}

.stock-detail-card__quote div {
  display: flex;
  justify-content: flex-end;
  gap: 20px;
  margin-top: 6px;
  font-size: 16px;
  font-weight: 650;
}

.stock-detail-card__periods {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px;
  margin-top: 24px;
  padding: 4px;
  border-radius: 8px;
  background: var(--glass-tint);
}

.stock-detail-card__periods button {
  min-width: 0;
  min-height: 40px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--slate);
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0;
  cursor: pointer;
}

.stock-detail-card__periods button:hover {
  color: var(--ink);
}

.stock-detail-card__periods button.is-active {
  border-color: var(--line);
  background: #ffffff;
  color: var(--ink);
  box-shadow: var(--shadow-soft);
}

.stock-detail-card__periods button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.stock-detail-card__meta {
  display: flex;
  min-height: 32px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
}

.stock-detail-card__ma {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 6px 14px;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  font-weight: 650;
}

.stock-detail-card__adjustment,
.stock-detail-card__intraday-label {
  flex: 0 0 auto;
  color: var(--slate);
  font-size: 13px;
  font-weight: 600;
}

.stock-detail-card__chart-wrap {
  position: relative;
  min-width: 0;
}

.stock-detail-card__chart {
  width: 100%;
  height: clamp(380px, 48vw, 520px);
  min-width: 0;
  touch-action: pan-y;
  overscroll-behavior-x: contain;
}

.stock-detail-card__empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: var(--muted);
  font-size: 14px;
}

.stock-detail-card__volume {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: -8px 0 0 48px;
  color: var(--slate);
  font-size: 13px;
}

.stock-detail-card__volume strong {
  color: var(--ink-soft);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 640px) {
  .stock-detail-card {
    padding: 16px 12px 14px;
  }

  .stock-detail-card__header {
    gap: 12px;
  }

  .stock-detail-card__identity h2 {
    font-size: 21px;
  }

  .stock-detail-card__identity p {
    font-size: 13px;
  }

  .stock-detail-card__quote strong {
    font-size: 25px;
  }

  .stock-detail-card__quote div {
    gap: 10px;
    margin-top: 4px;
    font-size: 13px;
  }

  .stock-detail-card__periods {
    margin-top: 18px;
  }

  .stock-detail-card__periods button {
    min-height: 44px;
    font-size: 13px;
  }

  .stock-detail-card__meta {
    align-items: flex-start;
  }

  .stock-detail-card__ma {
    gap: 4px 10px;
    font-size: 11px;
  }

  .stock-detail-card__adjustment,
  .stock-detail-card__intraday-label {
    font-size: 11px;
  }

  .stock-detail-card__chart {
    height: 360px;
  }

  .stock-detail-card__volume {
    margin-left: 44px;
    font-size: 12px;
  }
}
</style>
