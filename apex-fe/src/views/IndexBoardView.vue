<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { fetchIndexBars, fetchIndexBoard, refreshIndexBoard } from '../api/indexBoard'

const router = useRouter()
const loading = ref(false)
const refreshing = ref(false)
const data = ref(null)
const lastLog = ref('')
const activeCode = ref('')
const detailBars = ref([])
const chartRef = ref(null)
let chart

const sections = computed(() => [
  { key: 'cn', title: 'A股', items: data.value?.cn || [] },
  { key: 'hk', title: '港股', items: data.value?.hk || [] },
  { key: 'asia', title: '日韩', items: data.value?.asia || [] },
  { key: 'us', title: '美国', items: data.value?.us || [] },
])

function fmtNum(v, digits = 2) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toLocaleString('zh-CN', { maximumFractionDigits: digits, minimumFractionDigits: digits })
}

function fmtPct(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  const sign = n > 0 ? '+' : ''
  return `${sign}${n.toFixed(2)}%`
}

function fmtVol(v) {
  if (v == null || v === '' || Number(v) === 0) return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return '-'
  if (Math.abs(n) >= 1e8) return (n / 1e8).toFixed(2) + '亿'
  if (Math.abs(n) >= 1e4) return (n / 1e4).toFixed(1) + '万'
  return n.toFixed(0)
}

function pctClass(v) {
  const n = Number(v)
  if (Number.isNaN(n) || n === 0) return ''
  return n > 0 ? 'up' : 'down'
}

function trendClass(trend) {
  if (trend === '放量') return 'vol-up'
  if (trend === '缩量') return 'vol-down'
  return 'vol-flat'
}

function sparkPath(closes) {
  const vals = (closes || []).map(Number).filter((n) => !Number.isNaN(n))
  if (vals.length < 2) return ''
  const min = Math.min(...vals)
  const max = Math.max(...vals)
  const span = max - min || 1
  const w = 88
  const h = 28
  return vals
    .map((v, i) => {
      const x = (i / (vals.length - 1)) * w
      const y = h - ((v - min) / span) * h
      return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
}

async function load() {
  loading.value = true
  try {
    const res = await fetchIndexBoard(30)
    data.value = res.data
    if (!activeCode.value) {
      const first = [...(res.data?.cn || []), ...(res.data?.hk || []), ...(res.data?.us || [])][0]
      if (first) await selectIndex(first.code)
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onRefresh(start = '20180101') {
  refreshing.value = true
  try {
    const res = await refreshIndexBoard(start)
    data.value = res.data?.board || data.value
    lastLog.value = res.data?.log || ''
    ElMessage.success(res.data?.message || '指数已刷新')
    if (activeCode.value) await selectIndex(activeCode.value)
  } catch (e) {
    ElMessage.error(e.message || '刷新失败，可命令行运行 sync_index.py')
    await load()
  } finally {
    refreshing.value = false
  }
}

async function selectIndex(code) {
  if (!code) return
  activeCode.value = code
  try {
    const res = await fetchIndexBars(code, 180)
    detailBars.value = res.data || []
    await nextTick()
    renderChart()
  } catch (e) {
    detailBars.value = []
    ElMessage.error(e.message || '加载走势失败')
  }
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const bars = detailBars.value
  const dates = bars.map((b) => b.tradeDate)
  const closes = bars.map((b) => +b.closePrice)
  const volumes = bars.map((b) => (b.volume != null ? +b.volume : 0))
  const active = sections.value.flatMap((s) => s.items).find((i) => i.code === activeCode.value)
  chart.setOption({
    animation: false,
    title: {
      text: active ? `${active.name} · 近 ${bars.length} 日` : activeCode.value,
      left: 0,
      top: 0,
      textStyle: { fontSize: 14, fontWeight: 700, color: '#1d1d1f' },
    },
    tooltip: { trigger: 'axis' },
    grid: [
      { left: 48, right: 18, top: 36, height: '52%' },
      { left: 48, right: 18, top: '72%', height: '18%' },
    ],
    xAxis: [
      { type: 'category', data: dates, gridIndex: 0, axisLabel: { show: false }, axisTick: { show: false } },
      { type: 'category', data: dates, gridIndex: 1, axisLabel: { fontSize: 10 } },
    ],
    yAxis: [
      { type: 'value', scale: true, gridIndex: 0, splitNumber: 3 },
      { type: 'value', gridIndex: 1, splitNumber: 2, axisLabel: { show: false } },
    ],
    series: [
      {
        name: '收盘',
        type: 'line',
        data: closes,
        showSymbol: false,
        lineStyle: { width: 2, color: '#0071e3' },
        areaStyle: { color: 'rgba(0,113,227,0.08)' },
        xAxisIndex: 0,
        yAxisIndex: 0,
      },
      {
        name: '成交量',
        type: 'bar',
        data: volumes,
        xAxisIndex: 1,
        yAxisIndex: 1,
        itemStyle: {
          color: (p) => {
            if (p.dataIndex === 0) return '#c7c7cc'
            const prev = volumes[p.dataIndex - 1] || 0
            const cur = volumes[p.dataIndex] || 0
            if (!prev || !cur) return '#c7c7cc'
            return cur >= prev ? '#ff3b30' : '#34c759'
          },
        },
      },
    ],
  })
}

function onResize() {
  chart?.resize()
}

watch(detailBars, () => nextTick(renderChart))

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="page" v-loading="loading || refreshing">
    <header class="header">
      <div>
        <p class="eyebrow">Apex · Market</p>
        <h1>大盘看板</h1>
        <p>{{ data?.message || 'A股 / 港股 / 日韩 / 美股主流指数 · 含历史与成交量较昨日对比' }}</p>
      </div>
      <div class="actions">
        <el-button type="primary" :loading="refreshing" @click="onRefresh('20180101')">同步历史并刷新</el-button>
        <el-button :loading="refreshing" @click="onRefresh('20240101')">快刷(自2024)</el-button>
        <el-button plain @click="router.push('/sector')">板块</el-button>
        <el-button plain @click="router.push('/decision')">决策</el-button>
        <el-button plain @click="router.push('/news')">资讯</el-button>
        <el-button text @click="load">刷新</el-button>
      </div>
    </header>

    <div v-if="!loading && sections.every((s) => !s.items.length)" class="page-empty">
      <h3>暂无指数数据</h3>
      <p>同步历史后看 A股/港股/美股等主流指数与量能对比</p>
      <el-button type="primary" :loading="refreshing" @click="onRefresh('20180101')">拉取历史指数</el-button>
      <el-button plain @click="router.push('/decision')">智能决策</el-button>
    </div>

    <section v-for="sec in sections" :key="sec.key" class="market-sec" v-show="sec.items.length">
      <h2>{{ sec.title }}</h2>
      <div class="cards">
        <button
          v-for="item in sec.items"
          :key="item.code"
          type="button"
          class="idx-card"
          :class="{ on: activeCode === item.code }"
          @click="selectIndex(item.code)"
        >
          <div class="idx-top">
            <strong>{{ item.name }}</strong>
            <span class="date">{{ item.tradeDate || '-' }}</span>
          </div>
          <div class="idx-price" :class="pctClass(item.pctChg)">
            <b>{{ fmtNum(item.closePrice) }}</b>
            <em>{{ fmtPct(item.pctChg) }}</em>
          </div>
          <div class="idx-vol">
            <span>量 {{ fmtVol(item.volume) }}</span>
            <span class="trend" :class="trendClass(item.volumeTrend)">
              {{ item.volumeTrend }}
              <template v-if="item.volumeChgPct != null"> {{ fmtPct(item.volumeChgPct) }}</template>
            </span>
          </div>
          <svg class="spark" viewBox="0 0 88 28" preserveAspectRatio="none">
            <path
              :d="sparkPath(item.sparkCloses)"
              fill="none"
              :stroke="Number(item.pctChg) >= 0 ? '#ff3b30' : '#34c759'"
              stroke-width="1.6"
            />
          </svg>
        </button>
      </div>
    </section>

    <section v-if="activeCode" class="detail">
      <div ref="chartRef" class="chart" />
      <p class="hint">
        成交量柱：红=较前日放量，绿=较前日缩量。日韩部分指数源无成交量时显示「无数据」。
      </p>
    </section>

    <el-collapse v-if="lastLog" style="margin-top: 12px">
      <el-collapse-item title="最近同步日志" name="log">
        <pre class="log">{{ lastLog }}</pre>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.market-sec {
  margin-bottom: 18px;
}

.market-sec h2 {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.cards {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.idx-card {
  text-align: left;
  border: 1px solid var(--glass-border);
  background: var(--glass);
  border-radius: var(--radius);
  padding: 12px 12px 8px;
  cursor: pointer;
  box-shadow: var(--shadow-soft);
}

.idx-card.on,
.idx-card:hover {
  border-color: rgba(0, 113, 227, 0.45);
  box-shadow: 0 0 0 2px rgba(0, 113, 227, 0.08);
}

.idx-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.idx-top strong {
  font-size: 13px;
}

.idx-top .date {
  font-size: 11px;
  color: var(--muted);
}

.idx-price {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 6px;
}

.idx-price b {
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.03em;
}

.idx-price em {
  font-style: normal;
  font-size: 13px;
  font-weight: 600;
}

.idx-price.up b,
.idx-price.up em {
  color: var(--up);
}

.idx-price.down b,
.idx-price.down em {
  color: var(--down);
}

.idx-vol {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 11px;
  color: var(--slate);
  margin-bottom: 6px;
}

.trend.vol-up {
  color: var(--up);
  font-weight: 600;
}

.trend.vol-down {
  color: var(--down);
  font-weight: 600;
}

.trend.vol-flat {
  color: var(--muted);
}

.spark {
  width: 100%;
  height: 28px;
  display: block;
}

.detail {
  margin-top: 8px;
  background: var(--glass);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  padding: 12px;
}

.chart {
  width: 100%;
  height: 340px;
}

.hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--muted);
}

.log {
  margin: 0;
  white-space: pre-wrap;
  font-size: 12px;
  color: var(--slate);
}

@media (max-width: 1100px) {
  .cards {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .cards {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
