<script setup>
import { nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { fetchStockDetail, syncStockBasic } from '../api/stock'
import { syncBars } from '../api/bars'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const syncingBars = ref(false)
const refreshing = ref(false)
const code = ref(String(route.params.code || route.query.code || '600519'))
const basic = ref(null)
const note = ref('')
const bars = ref([])
const needSyncBars = ref(false)
const barCount = ref(0)
const rs20 = ref(null)
const rs60 = ref(null)
const volumeRatio = ref(null)
const chartRef = ref(null)
let chart

function ma(closes, period) {
  return closes.map((_, i) => {
    if (i + 1 < period) return null
    let sum = 0
    for (let j = i - period + 1; j <= i; j++) sum += closes[j]
    return +(sum / period).toFixed(2)
  })
}

function ema(closes, period) {
  const k = 2 / (period + 1)
  const out = []
  let prev = null
  for (let i = 0; i < closes.length; i++) {
    if (i + 1 < period) {
      out.push(null)
      continue
    }
    if (prev == null) {
      let sum = 0
      for (let j = i - period + 1; j <= i; j++) sum += closes[j]
      prev = sum / period
    } else {
      prev = closes[i] * k + prev * (1 - k)
    }
    out.push(+prev.toFixed(4))
  }
  return out
}

function macd(closes) {
  const ema12 = ema(closes, 12)
  const ema26 = ema(closes, 26)
  const dif = closes.map((_, i) =>
    ema12[i] == null || ema26[i] == null ? null : +(ema12[i] - ema26[i]).toFixed(4),
  )
  const dea = (() => {
    const vals = dif.map((v) => (v == null ? 0 : v))
    const e = ema(vals, 9)
    return e.map((v, i) => (dif[i] == null ? null : v))
  })()
  const hist = dif.map((v, i) => (v == null || dea[i] == null ? null : +((v - dea[i]) * 2).toFixed(4)))
  return { dif, dea, hist }
}

function rsi(closes, period = 14) {
  return closes.map((_, i) => {
    if (i < period) return null
    let gain = 0
    let loss = 0
    for (let j = i - period + 1; j <= i; j++) {
      const ch = closes[j] - closes[j - 1]
      if (ch >= 0) gain += ch
      else loss -= ch
    }
    if (loss === 0) return 100
    const rs = gain / loss
    return +((100 - 100 / (1 + rs)).toFixed(2))
  })
}

function disposeChart() {
  if (chart) {
    chart.dispose()
    chart = null
  }
}

async function renderChart(list) {
  if (!list.length) {
    disposeChart()
    return
  }
  // 等容器从空态切出并完成布局，避免 width=0 把图压成一条线
  await nextTick()
  await new Promise((r) => requestAnimationFrame(() => r()))
  if (!chartRef.value) return
  const width = chartRef.value.clientWidth
  if (width < 80) {
    await new Promise((r) => setTimeout(r, 50))
  }
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  } else {
    chart.resize()
  }
  const dates = list.map((b) => b.tradeDate)
  const ohlc = list.map((b) => [+b.openPrice, +b.closePrice, +b.lowPrice, +b.highPrice])
  const volumes = list.map((b) => +b.volume)
  const closes = list.map((b) => +b.closePrice)
  const ma20 = ma(closes, 20)
  const ma60 = ma(closes, 60)
  const { dif, dea, hist } = macd(closes)
  const rsi14 = rsi(closes, 14)

  chart.setOption({
    backgroundColor: 'transparent',
    animation: false,
    legend: { data: ['K线', 'MA20', 'MA60', '成交量', 'DIF', 'DEA', 'MACD', 'RSI'], top: 0 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    grid: [
      { left: 50, right: 20, top: 40, height: '38%' },
      { left: 50, right: 20, top: '52%', height: '10%' },
      { left: 50, right: 20, top: '66%', height: '12%' },
      { left: 50, right: 20, top: '82%', height: '10%' },
    ],
    xAxis: [
      { type: 'category', data: dates, boundaryGap: true, axisLine: { onZero: false }, min: 'dataMin', max: 'dataMax' },
      { type: 'category', gridIndex: 1, data: dates, boundaryGap: true, axisLabel: { show: false }, min: 'dataMin', max: 'dataMax' },
      { type: 'category', gridIndex: 2, data: dates, boundaryGap: true, axisLabel: { show: false }, min: 'dataMin', max: 'dataMax' },
      { type: 'category', gridIndex: 3, data: dates, boundaryGap: true, axisLabel: { show: false }, min: 'dataMin', max: 'dataMax' },
    ],
    yAxis: [
      { scale: true, splitArea: { show: true } },
      { scale: true, gridIndex: 1, splitNumber: 2, axisLabel: { show: false }, axisLine: { show: false }, axisTick: { show: false }, splitLine: { show: false } },
      { scale: true, gridIndex: 2, splitNumber: 2 },
      { min: 0, max: 100, gridIndex: 3, splitNumber: 2 },
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1, 2, 3], start: 45, end: 100 },
      { show: true, xAxisIndex: [0, 1, 2, 3], type: 'slider', top: '95%', start: 45, end: 100 },
    ],
    series: [
      {
        name: 'K线',
        type: 'candlestick',
        data: ohlc,
        itemStyle: { color: '#ef5350', color0: '#26a69a', borderColor: '#ef5350', borderColor0: '#26a69a' },
      },
      { name: 'MA20', type: 'line', data: ma20, smooth: true, showSymbol: false, lineStyle: { width: 1.5, color: '#c79100' } },
      { name: 'MA60', type: 'line', data: ma60, smooth: true, showSymbol: false, lineStyle: { width: 1.5, color: '#5c6bc0' } },
      {
        name: '成交量',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: volumes,
        itemStyle: {
          color: (p) => {
            const row = ohlc[p.dataIndex]
            return row && row[1] >= row[0] ? '#ef5350' : '#26a69a'
          },
        },
      },
      { name: 'DIF', type: 'line', xAxisIndex: 2, yAxisIndex: 2, data: dif, showSymbol: false, lineStyle: { width: 1, color: '#1f6f5b' } },
      { name: 'DEA', type: 'line', xAxisIndex: 2, yAxisIndex: 2, data: dea, showSymbol: false, lineStyle: { width: 1, color: '#c79100' } },
      {
        name: 'MACD',
        type: 'bar',
        xAxisIndex: 2,
        yAxisIndex: 2,
        data: hist,
        itemStyle: { color: (p) => (p.data >= 0 ? '#ef5350' : '#26a69a') },
      },
      { name: 'RSI', type: 'line', xAxisIndex: 3, yAxisIndex: 3, data: rsi14, showSymbol: false, lineStyle: { width: 1.2, color: '#6a4c93' } },
    ],
  }, true)
  chart.resize()
}

async function load(refreshQuote = false) {
  if (!code.value) return
  loading.value = true
  try {
    const res = await fetchStockDetail(code.value.trim(), 220, false)
    applyDetail(res.data)
    if (refreshQuote) {
      await refreshQuoteOnly()
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function applyDetail(data) {
  basic.value = data.basic
  note.value = data.note || ''
  bars.value = data.bars || []
  needSyncBars.value = !!data.needSyncBars
  barCount.value = data.barCount ?? bars.value.length
  rs20.value = data.rs20VsHs300
  rs60.value = data.rs60VsHs300
  volumeRatio.value = data.volumeRatio
  renderChart(bars.value)
}

async function refreshQuoteOnly() {
  refreshing.value = true
  try {
    await syncStockBasic(code.value.trim())
    const res = await fetchStockDetail(code.value.trim(), 220, false)
    applyDetail(res.data)
    ElMessage.success('行情已刷新并落库')
  } catch (e) {
    ElMessage.error(e.message || '刷新行情失败')
  } finally {
    refreshing.value = false
  }
}

async function syncDailyBars() {
  if (!code.value) return
  syncingBars.value = true
  try {
    const pure = code.value.trim()
    const res = await syncBars({ codes: [pure] })
    const data = res.data || {}
    // 日线同步只落 K 线，顺带刷一次行情快照，避免现价/估值仍是空
    try {
      await syncStockBasic(pure)
    } catch (quoteErr) {
      console.warn('同步日后刷新行情失败', quoteErr)
    }
    ElMessage.success(`日线同步完成：K线 ${data.barCount ?? 0} 根`)
    const detail = await fetchStockDetail(pure, 220, false)
    applyDetail(detail.data)
  } catch (e) {
    ElMessage.error(e.message || '同步日线失败')
  } finally {
    syncingBars.value = false
  }
}

function go() {
  router.replace(`/stock/${code.value.trim()}`)
  load(false)
}

function onResize() {
  chart?.resize()
}

watch(
  () => route.params.code,
  (v) => {
    if (v) {
      code.value = String(v)
      load(false)
    }
  },
)

onMounted(() => {
  load(false)
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  disposeChart()
})

function fmtMv(v) {
  if (v == null) return '-'
  const n = Number(v)
  if (n >= 1e12) return (n / 1e12).toFixed(2) + '万亿'
  if (n >= 1e8) return (n / 1e8).toFixed(2) + '亿'
  return n.toFixed(2)
}
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="header">
      <div>
        <h1>{{ basic?.name || '股票详情' }} <span class="code">{{ basic?.code || code }}</span></h1>
        <p>{{ note }}</p>
      </div>
      <div class="actions">
        <el-input v-model="code" style="width: 120px" placeholder="代码" @keyup.enter="go" />
        <el-button type="primary" @click="go">查询</el-button>
        <el-button :loading="refreshing" @click="refreshQuoteOnly">刷新行情</el-button>
        <el-button type="success" :loading="syncingBars" @click="syncDailyBars">同步日线</el-button>
        <el-button @click="router.push({ path: '/backtest', query: { code: code.trim() } })">回测</el-button>
        <el-button @click="router.push({ path: '/paper', query: { code: code.trim(), side: 'BUY' } })">模拟买</el-button>
      </div>
    </header>

    <div class="meta" v-if="basic">
      <div><label>最新价</label><b :class="basic.pctChg >= 0 ? 'up' : 'down'">{{ basic.latestPrice ?? '-' }}</b></div>
      <div><label>涨跌幅</label><b :class="basic.pctChg >= 0 ? 'up' : 'down'">{{ basic.pctChg != null ? basic.pctChg + '%' : '-' }}</b></div>
      <div><label>市盈率</label><span>{{ basic.peTtm ?? '-' }}</span></div>
      <div><label>市净率</label><span>{{ basic.pb ?? '-' }}</span></div>
      <div><label>总市值</label><span>{{ fmtMv(basic.totalMv) }}</span></div>
      <div><label>流通市值</label><span>{{ fmtMv(basic.circMv) }}</span></div>
      <div><label>市场</label><span>{{ basic.market || '-' }}</span></div>
      <div><label>行业</label><span>{{ basic.industry || '-' }}</span></div>
      <div><label>上市</label><span>{{ basic.listDate || '-' }}</span></div>
      <div><label>来源</label><span>{{ basic.source || '-' }}</span></div>
      <div><label>本地日线</label><span>{{ barCount }}</span></div>
      <div><label>RS20 vs沪深300</label><b :class="Number(rs20) >= 0 ? 'up' : 'down'">{{ rs20 != null ? rs20 + 'pp' : '-' }}</b></div>
      <div><label>RS60 vs沪深300</label><b :class="Number(rs60) >= 0 ? 'up' : 'down'">{{ rs60 != null ? rs60 + 'pp' : '-' }}</b></div>
      <div><label>量比</label><b :class="Number(volumeRatio) >= 1.5 ? 'up' : ''">{{ volumeRatio ?? '-' }}</b></div>
    </div>

    <el-alert
      v-if="!loading && bars.length && needSyncBars"
      class="hint"
      type="warning"
      :closable="false"
      show-icon
      :title="`本地仅 ${barCount} 根日线，建议同步补齐后再做指标/回测`"
    />

    <el-empty
      v-if="!loading && !bars.length"
      class="empty-bars"
      description="本地暂无日线，请先同步日线落库"
    >
      <el-button type="primary" :loading="syncingBars" @click="syncDailyBars">同步日线</el-button>
    </el-empty>

    <div v-if="bars.length" ref="chartRef" class="chart" />
  </div>
</template>

<style scoped>
.header .code {
  color: var(--muted);
  font-size: 18px;
  font-weight: 500;
  margin-left: 8px;
  letter-spacing: -0.02em;
}

.meta {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.meta > div {
  background: var(--glass);
  backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  -webkit-backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  padding: 12px 14px;
  box-shadow: var(--shadow-soft);
  font-variant-numeric: tabular-nums;
}

.meta label {
  display: block;
  color: var(--muted);
  font-size: 11px;
  font-weight: 500;
  margin-bottom: 6px;
}

.hint {
  margin-bottom: 12px;
}

.empty-bars {
  margin: 24px 0;
}

.chart {
  height: 720px;
  width: 100%;
  background: var(--glass-strong);
  backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  -webkit-backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-soft);
  overflow: hidden;
}

@media (max-width: 900px) {
  .meta {
    grid-template-columns: 1fr 1fr;
  }

  .chart {
    height: 560px;
  }
}
</style>
