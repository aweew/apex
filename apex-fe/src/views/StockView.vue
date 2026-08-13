<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  fetchCompanyProfile,
  fetchStockDetail,
  fetchStockFundamental,
  fetchStockIntraday,
  refreshCompanyProfile,
  syncStockBasic,
} from '../api/stock'
import { syncBars } from '../api/bars'
import { saveObserve } from '../api/observe'
import { aggregateBars, defaultVisibleStart, tdSequential } from '../utils/kline'
import { analyzePriceStructure, buildPriceLevelMarkLines } from '../utils/priceStructure'
import { bindLongPress, resolveMobileTooltipPosition } from '../utils/chartLongPress'
import StockAnalysisPanel from '../components/StockAnalysisPanel.vue'
import ChipDistributionPanel from '../components/ChipDistributionPanel.vue'
import ValuationView from './ValuationView.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const syncingBars = ref(false)
const fundLoading = ref(false)
const profileLoading = ref(false)
const profileRefreshing = ref(false)
const profileExpand = ref(false)
const mainBusinessExpand = ref(false)
const conceptExpand = ref(false)
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
const activeTab = ref(route.query.tab === 'valuation' ? 'valuation' : 'chart')
const fund = ref(null)
const profile = ref(null)
const macdTip = ref('')
const tdTip = ref('')
const intraday = ref(null)
const intradayLoading = ref(false)
const intradayAsOf = ref('')
/** day | week | month | intraday */
const klinePeriod = ref('day')
/** 默认仅显示 MA5 / MA20 */
const selectedMas = ref(['MA5', 'MA20'])
const showTd9 = ref(true)
/** key=仅显示 8/9，all=显示 1–9 */
const tdShowMode = ref('key')
const isMobileChart = ref(window.matchMedia('(max-width: 820px)').matches)
const chartParamsExpanded = ref(!isMobileChart.value)
const BAR_LIMIT = 500
const CHART_PREF_KEY = 'apex.stock.chartPrefs'
const META_EXPAND_KEY = 'apex.stock.metaExpanded'
const metaExpanded = ref(localStorage.getItem(META_EXPAND_KEY) === '1')

function toggleMetaExpanded() {
  metaExpanded.value = !metaExpanded.value
  localStorage.setItem(META_EXPAND_KEY, metaExpanded.value ? '1' : '0')
}
const MA_META = [
  { name: 'MA5', color: '#1d1d1f' },
  { name: 'MA10', color: '#f59e0b' },
  { name: 'MA20', color: '#7c3aed' },
  { name: 'MA60', color: '#5c6bc0' },
]
const MA_NAMES = MA_META.map((item) => item.name)
let chart
let syncingFromLegend = false
let resetZoomNext = true
let savedZoom = { start: 0, end: 100 }
let intradayPollTimer = null
/** 当前图数据，供可视区高低点随缩放更新 */
let chartPayload = null
let compactPriceLabelsMode = null
let chartPressCleanup = null

const isIntraday = computed(() => klinePeriod.value === 'intraday')
const intradayPoints = computed(() => intraday.value?.points || [])
const showChartShell = computed(() => bars.value.length > 0 || isIntraday.value)
const chartBars = computed(() =>
  isIntraday.value ? [] : aggregateBars(bars.value, klinePeriod.value),
)
const priceStructure = computed(() =>
  analyzePriceStructure(bars.value, basic.value?.latestPrice),
)
const periodLabel = computed(() => {
  if (klinePeriod.value === 'intraday') return '分时'
  if (klinePeriod.value === 'week') return '周K'
  if (klinePeriod.value === 'month') return '月K'
  return '日K'
})
/** 随 K 线周期：天 / 周 / 月 */
const periodUnit = computed(() =>
  klinePeriod.value === 'week' ? '周' : klinePeriod.value === 'month' ? '月' : '天',
)
const periodMeta = computed(() => {
  if (isIntraday.value) {
    return intraday.value?.note || (intradayLoading.value ? '分时加载中…' : '')
  }
  const n = chartBars.value.length
  if (!n) return ''
  if (klinePeriod.value === 'day') return `${periodLabel.value} · ${n} 根`
  return `${periodLabel.value} · ${n} 根（由 ${bars.value.length} 根日线聚合）`
})
function maDisplayName(name) {
  const n = String(name).replace(/^MA/i, '')
  return `MA${n}${periodUnit.value}`
}

function toggleChartParams() {
  chartParamsExpanded.value = !chartParamsExpanded.value
}

function unbindChartPress() {
  if (chartPressCleanup) {
    chartPressCleanup()
    chartPressCleanup = null
  }
}

function bindMobileChartPress() {
  unbindChartPress()
  if (!chart || !isMobileChart.value) return

  const chartDom = chart.getDom()
  const showTip = (event) => {
    const rect = chartDom.getBoundingClientRect()
    chart.dispatchAction({
      type: 'showTip',
      x: event.clientX - rect.left,
      y: event.clientY - rect.top,
    })
  }
  chartPressCleanup = bindLongPress({
    element: chartDom,
    onActivate: showTip,
    onUpdate: showTip,
    onDeactivate: () => chart?.dispatchAction({ type: 'hideTip' }),
  })
}

function mobileTooltipPosition(point, params, tooltipDom, rect, size) {
  const chartRect = chartRef.value?.getBoundingClientRect()
  return resolveMobileTooltipPosition({
    point,
    contentSize: size.contentSize,
    viewSize: size.viewSize,
    chartTop: chartRect?.top || 0,
    viewportHeight: window.innerHeight,
  })
}

function loadChartPrefs() {
  try {
    const raw = localStorage.getItem(CHART_PREF_KEY)
    if (!raw) return
    const prefs = JSON.parse(raw)
    if (['day', 'week', 'month', 'intraday'].includes(prefs.klinePeriod)) {
      klinePeriod.value = prefs.klinePeriod
    }
    if (Array.isArray(prefs.selectedMas)) {
      const next = prefs.selectedMas.filter((name) => MA_NAMES.includes(name))
      if (next.length) selectedMas.value = next
    }
    if (typeof prefs.showTd9 === 'boolean') showTd9.value = prefs.showTd9
    if (prefs.tdShowMode === 'all' || prefs.tdShowMode === 'key') tdShowMode.value = prefs.tdShowMode
  } catch {
    /* ignore broken prefs */
  }
}

function saveChartPrefs() {
  try {
    localStorage.setItem(
      CHART_PREF_KEY,
      JSON.stringify({
        klinePeriod: klinePeriod.value,
        selectedMas: selectedMas.value,
        showTd9: showTd9.value,
        tdShowMode: tdShowMode.value,
      }),
    )
  } catch {
    /* ignore quota */
  }
}

function captureZoom() {
  if (!chart) return
  const dz = chart.getOption()?.dataZoom
  if (!Array.isArray(dz) || !dz.length) return
  const start = Number(dz[0].start)
  const end = Number(dz[0].end)
  if (!Number.isNaN(start) && !Number.isNaN(end)) savedZoom = { start, end }
}

/** dataZoom 百分比 → 可视下标区间（含端点） */
function visibleIndexRange(len, startPct, endPct) {
  if (len <= 0) return [0, -1]
  let startIdx = Math.floor((len * startPct) / 100)
  let endIdx = Math.ceil((len * endPct) / 100) - 1
  if (startIdx < 0) startIdx = 0
  if (endIdx >= len) endIdx = len - 1
  if (endIdx < startIdx) endIdx = startIdx
  return [startIdx, endIdx]
}

/**
 * 仅按可视区 K 线高低定 Y 轴（忽略均线），避免底部大块空白
 * scale:true 时 boundaryGap 无效，必须显式 min/max
 */
function calcVisiblePriceExtent(highs, lows, startPct, endPct) {
  const [startIdx, endIdx] = visibleIndexRange(highs.length, startPct, endPct)
  if (endIdx < startIdx) return { min: null, max: null }
  let min = Infinity
  let max = -Infinity
  for (let i = startIdx; i <= endIdx; i++) {
    if (Number.isFinite(lows[i]) && lows[i] < min) min = lows[i]
    if (Number.isFinite(highs[i]) && highs[i] > max) max = highs[i]
  }
  if (!Number.isFinite(min) || !Number.isFinite(max)) return { min: null, max: null }
  const span = max - min
  const pad = span > 0 ? span * 0.04 : Math.max(Math.abs(max) * 0.01, 0.01)
  return { min: +(min - pad).toFixed(4), max: +(max + pad).toFixed(4) }
}

/**
 * 可视范围阶段高低点（东财风格）
 * 锚点在当日影线最高/最低尖端；「价格→」水平对准尖端
 */
function buildVisibleExtremeMarkPoint(dates, highs, lows, startPct, endPct) {
  const [startIdx, endIdx] = visibleIndexRange(dates.length, startPct, endPct)
  if (endIdx < startIdx) {
    return { silent: true, animation: false, data: [] }
  }
  let highIdx = startIdx
  let lowIdx = startIdx
  let highVal = highs[startIdx]
  let lowVal = lows[startIdx]
  for (let i = startIdx + 1; i <= endIdx; i++) {
    if (highs[i] > highVal) {
      highVal = highs[i]
      highIdx = i
    }
    if (lows[i] < lowVal) {
      lowVal = lows[i]
      lowIdx = i
    }
  }
  const span = Math.max(1, endIdx - startIdx)
  const makePoint = (idx, val) => {
    const onLeft = (idx - startIdx) / span > 0.18
    const price = fmtNum(val)
    return {
      // 类目下标 + 当日最高/最低，钉在影线尖端（非实体）
      coord: [idx, val],
      symbol: 'circle',
      symbolSize: 0,
      itemStyle: { color: 'transparent' },
      label: {
        show: true,
        formatter: onLeft ? `${price} →` : `← ${price}`,
        position: onLeft ? 'left' : 'right',
        distance: 4,
        color: '#1d1d1f',
        fontSize: 11,
        fontWeight: 400,
        verticalAlign: 'middle',
        backgroundColor: 'rgba(255,255,255,0.7)',
        padding: [1, 4],
        borderRadius: 2,
      },
    }
  }
  const data = []
  if (Number.isFinite(highVal)) data.push(makePoint(highIdx, highVal))
  if (Number.isFinite(lowVal)) data.push(makePoint(lowIdx, lowVal))
  return {
    silent: true,
    animation: false,
    data,
  }
}

/** 缩放后同步：阶段高低点 + 按 K 线重算 Y 轴 */
function updateVisibleWindow() {
  if (!chart || !chartPayload) return
  captureZoom()
  const { dates, highs, lows } = chartPayload
  const markPoint = buildVisibleExtremeMarkPoint(dates, highs, lows, savedZoom.start, savedZoom.end)
  const extent = calcVisiblePriceExtent(highs, lows, savedZoom.start, savedZoom.end)
  chart.setOption({
    yAxis: [
      {
        min: extent.min,
        max: extent.max,
      },
    ],
    series: [
      {
        id: 'kline-main',
        markPoint,
      },
    ],
  })
}

async function loadIntraday(silent = false) {
  if (!code.value) return
  if (!silent) intradayLoading.value = true
  try {
    const res = await fetchStockIntraday(code.value.trim())
    intraday.value = res.data || null
    const pts = intraday.value?.points || []
    if (pts.length) {
      const last = pts[pts.length - 1]
      intradayAsOf.value = last.datetime
        || (intraday.value?.tradeDate ? `${intraday.value.tradeDate} ${last.time}` : last.time)
        || ''
    } else {
      intradayAsOf.value = ''
    }
    if (activeTab.value === 'chart' && isIntraday.value) {
      await renderIntradayChart()
    }
  } catch (e) {
    intraday.value = null
    intradayAsOf.value = ''
    if (isIntraday.value && !silent) {
      disposeChart()
      ElMessage.error(e.message || '分时加载失败')
    }
  } finally {
    if (!silent) intradayLoading.value = false
  }
}

function startIntradayPoll() {
  stopIntradayPoll()
  if (!isIntraday.value) return
  intradayPollTimer = setInterval(() => {
    if (isIntraday.value && activeTab.value === 'chart') loadIntraday(true)
  }, 60000)
}

function stopIntradayPoll() {
  if (intradayPollTimer) {
    clearInterval(intradayPollTimer)
    intradayPollTimer = null
  }
}

/**
 * 确保 ECharts 挂在当前 chartRef 上。
 * 分时/K 线用 v-if 切换时 DOM 会重建，旧实例仍指向已销毁节点会导致白屏。
 */
async function ensureChartInstance() {
  await nextTick()
  await new Promise((r) => requestAnimationFrame(() => r()))
  if (!chartRef.value) return false
  if (chartRef.value.clientWidth < 80 || chartRef.value.clientHeight < 80) {
    await new Promise((r) => setTimeout(r, 50))
    await new Promise((r) => requestAnimationFrame(() => r()))
  }
  if (!chartRef.value) return false
  if (chart && chart.getDom() !== chartRef.value) {
    disposeChart()
  }
  if (!chart) {
    chart = echarts.init(chartRef.value)
  } else {
    chart.off('datazoom')
    chart.off('legendselectchanged')
    chart.clear()
  }
  return true
}

async function renderIntradayChart() {
  const points = intradayPoints.value
  macdTip.value = ''
  tdTip.value = ''
  chartPayload = null
  if (!points.length) {
    disposeChart()
    return
  }
  if (!(await ensureChartInstance())) return
  const times = points.map((p) => p.time)
  const prices = points.map((p) => Number(p.price))
  const avgs = points.map((p) => Number(p.avgPrice))
  const vols = points.map((p) => Number(p.volume) || 0)
  const pre = Number(intraday.value?.preClose || prices[0] || 0)
  let maxAbs = 0
  for (const p of prices) {
    if (!Number.isFinite(p)) continue
    maxAbs = Math.max(maxAbs, Math.abs(p - pre))
  }
  maxAbs = Math.max(maxAbs, pre * 0.01 || 0.01)
  const ymin = pre - maxAbs * 1.08
  const ymax = pre + maxAbs * 1.08
  const lineColor = prices[prices.length - 1] >= pre ? '#ef5350' : '#26a69a'
  const volData = vols.map((v, i) => {
    const up = i === 0 ? prices[0] >= pre : prices[i] >= prices[i - 1]
    return {
      value: v,
      itemStyle: { color: up ? 'rgba(239,83,80,0.75)' : 'rgba(38,166,154,0.75)' },
    }
  })
  chart.setOption(
    {
      backgroundColor: 'transparent',
      animation: false,
      legend: {
        show: !isMobileChart.value,
        top: 0,
        left: 'center',
        itemWidth: 14,
        itemHeight: 8,
        data: ['分时', '均价', '成交量'],
      },
      tooltip: {
        trigger: 'axis',
        triggerOn: isMobileChart.value ? 'none' : 'mousemove|click',
        axisPointer: { type: 'cross' },
        confine: true,
        appendToBody: !isMobileChart.value,
        position: isMobileChart.value ? mobileTooltipPosition : undefined,
        transitionDuration: 0,
        formatter(params) {
          const rows = Array.isArray(params) ? params : [params]
          const idx = rows[0]?.dataIndex ?? 0
          const price = prices[idx]
          const pct = pre ? (((price - pre) / pre) * 100).toFixed(2) : '-'
          const lines = [`${times[idx] || ''}（昨收 ${pre.toFixed(2)}）`]
          for (const row of rows) {
            if (row.seriesName === '成交量') {
              lines.push(`${row.marker}${row.seriesName} ${row.value}`)
            } else {
              const val = Number(row.value)
              lines.push(`${row.marker}${row.seriesName} ${Number.isFinite(val) ? val.toFixed(2) : '-'}`)
            }
          }
          lines.push(`涨跌幅 ${pct}%`)
          return lines.join('<br/>')
        },
      },
      axisPointer: { link: [{ xAxisIndex: 'all' }] },
      grid: [
        { left: 56, right: 56, top: isMobileChart.value ? 16 : 36, height: '58%' },
        { left: 56, right: 56, top: '76%', height: '16%' },
      ],
      xAxis: [
        {
          type: 'category',
          data: times,
          boundaryGap: false,
          axisLabel: { interval: 29 },
          axisTick: { show: false },
        },
        {
          type: 'category',
          gridIndex: 1,
          data: times,
          boundaryGap: false,
          axisLabel: { show: false },
          axisTick: { show: false },
        },
      ],
      yAxis: [
        {
          scale: true,
          min: ymin,
          max: ymax,
          axisLabel: {
            formatter: (v) => Number(v).toFixed(2),
            color: (v) => (Number(v) >= pre ? '#ef5350' : '#26a69a'),
          },
          splitLine: { lineStyle: { type: 'dashed', opacity: 0.35 } },
        },
        {
          scale: true,
          min: ymin,
          max: ymax,
          position: 'right',
          axisLabel: {
            formatter: (v) => `${(((Number(v) - pre) / pre) * 100).toFixed(2)}%`,
            color: (v) => (Number(v) >= pre ? '#ef5350' : '#26a69a'),
          },
          splitLine: { show: false },
        },
        {
          scale: true,
          gridIndex: 1,
          splitNumber: 2,
          axisLabel: { show: false },
          splitLine: { show: false },
        },
      ],
      series: [
        {
          name: '分时',
          type: 'line',
          data: prices,
          showSymbol: false,
          lineStyle: { width: 1.6, color: lineColor },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: lineColor === '#ef5350' ? 'rgba(239,83,80,0.18)' : 'rgba(38,166,154,0.18)' },
              { offset: 1, color: 'rgba(255,255,255,0)' },
            ]),
          },
          markLine: {
            silent: true,
            symbol: 'none',
            data: [
              {
                yAxis: pre,
                label: { formatter: '昨收', position: 'insideEndTop', color: '#888', fontSize: 11 },
                lineStyle: { color: '#999', type: 'dashed', width: 1 },
              },
            ],
          },
        },
        {
          name: '均价',
          type: 'line',
          data: avgs,
          showSymbol: false,
          lineStyle: { width: 1, color: '#f59e0b' },
        },
        {
          name: '成交量',
          type: 'bar',
          xAxisIndex: 1,
          yAxisIndex: 2,
          data: volData,
          barWidth: '55%',
        },
      ],
    },
    true,
  )
  chart.resize()
  bindMobileChartPress()
}

function refreshChart() {
  if (isIntraday.value) {
    if (intradayPoints.value.length) renderIntradayChart()
    else loadIntraday()
    return
  }
  if (!bars.value.length) {
    macdTip.value = ''
    tdTip.value = ''
    disposeChart()
    return
  }
  renderChart(chartBars.value)
}

function resetChartView() {
  if (isIntraday.value) {
    if (activeTab.value === 'chart') refreshChart()
    return
  }
  resetZoomNext = true
  savedZoom = { start: defaultVisibleStart(chartBars.value.length), end: 100 }
  if (bars.value.length && activeTab.value === 'chart') refreshChart()
}

function bindChartEvents() {
  if (!chart) return
  chart.off('datazoom')
  chart.off('legendselectchanged')
  chart.on('datazoom', () => {
    updateVisibleWindow()
  })
  chart.on('legendselectchanged', (evt) => {
    const selected = evt?.selected || {}
    const unit = periodUnit.value
    const nextMas = MA_NAMES.filter((name) => selected[`${name}${unit}`] !== false)
    const masChanged =
      nextMas.length !== selectedMas.value.length ||
      nextMas.some((name) => !selectedMas.value.includes(name))
    if (!masChanged) return
    syncingFromLegend = true
    selectedMas.value = nextMas
    nextTick(() => {
      syncingFromLegend = false
      saveChartPrefs()
    })
  })
}

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

/** DIF 上穿 DEA=金叉，下穿=死叉 */
function macdCrosses(dif, dea) {
  const signals = new Array(dif.length).fill(null)
  for (let i = 1; i < dif.length; i++) {
    if (dif[i] == null || dea[i] == null || dif[i - 1] == null || dea[i - 1] == null) continue
    const prev = dif[i - 1] - dea[i - 1]
    const cur = dif[i] - dea[i]
    if (prev <= 0 && cur > 0) signals[i] = 'golden'
    else if (prev >= 0 && cur < 0) signals[i] = 'death'
  }
  return signals
}

/** KDJ(9,3,3) */
function kdj(highs, lows, closes, n = 9, m1 = 3, m2 = 3) {
  const rsv = closes.map((c, i) => {
    if (i + 1 < n) return null
    let hh = -Infinity
    let ll = Infinity
    for (let j = i - n + 1; j <= i; j++) {
      hh = Math.max(hh, highs[j])
      ll = Math.min(ll, lows[j])
    }
    if (!Number.isFinite(hh) || !Number.isFinite(ll) || hh === ll) return 50
    return ((c - ll) / (hh - ll)) * 100
  })
  const k = []
  const d = []
  let prevK = 50
  let prevD = 50
  for (let i = 0; i < rsv.length; i++) {
    if (rsv[i] == null) {
      k.push(null)
      d.push(null)
      continue
    }
    prevK = (rsv[i] + (m1 - 1) * prevK) / m1
    prevD = (prevK + (m2 - 1) * prevD) / m2
    k.push(+prevK.toFixed(2))
    d.push(+prevD.toFixed(2))
  }
  const j = k.map((kv, i) => (kv == null || d[i] == null ? null : +(3 * kv - 2 * d[i]).toFixed(2)))
  return { k, d, j }
}

function fmtVol(v) {
  if (v == null || Number.isNaN(Number(v))) return '-'
  const n = Number(v)
  const abs = Math.abs(n)
  if (abs >= 1e8) return (n / 1e8).toFixed(2) + '亿'
  if (abs >= 1e4) return (n / 1e4).toFixed(2) + '万'
  return n.toFixed(0)
}

function disposeChart() {
  unbindChartPress()
  if (chart) {
    chart.off('datazoom')
    chart.off('legendselectchanged')
    chart.dispose()
    chart = null
  }
  chartPayload = null
}

function dayPctOf(list, closes, idx) {
  const row = list[idx]
  if (row?.pctChg != null && row.pctChg !== '') {
    const n = Number(row.pctChg)
    if (!Number.isNaN(n)) return n
  }
  if (idx <= 0) return null
  const prev = closes[idx - 1]
  const cur = closes[idx]
  if (!prev || Number.isNaN(prev) || Number.isNaN(cur)) return null
  return ((cur - prev) / prev) * 100
}

function sincePctOf(closes, idx) {
  if (idx == null || idx < 0 || idx >= closes.length) return null
  const base = closes[idx]
  const latest = closes[closes.length - 1]
  if (!base || Number.isNaN(base) || latest == null || Number.isNaN(latest)) return null
  return ((latest - base) / base) * 100
}

function pctColor(v) {
  if (v == null || Number.isNaN(v)) return '#666'
  if (v > 0) return '#ef5350'
  if (v < 0) return '#26a69a'
  return '#666'
}

function fmtSignedPct(v) {
  if (v == null || Number.isNaN(v)) return '-'
  const sign = v > 0 ? '+' : ''
  return `${sign}${v.toFixed(2)}%`
}

async function renderChart(list) {
  if (!list.length) {
    macdTip.value = ''
    tdTip.value = ''
    disposeChart()
    return
  }
  // 等容器从空态切出并完成布局，避免 width=0 / 旧实例挂死 DOM
  if (chart) captureZoom()
  if (!(await ensureChartInstance())) return
  bindChartEvents()
  const dates = list.map((b) => b.tradeDate)
  const ohlc = list.map((b) => [+b.openPrice, +b.closePrice, +b.lowPrice, +b.highPrice])
  const volumes = list.map((b) => +b.volume)
  const amounts = list.map((b) => (b.amount != null ? +b.amount : null))
  const turnovers = list.map((b) => (b.turnoverRate != null && b.turnoverRate !== '' ? +b.turnoverRate : null))
  const closes = list.map((b) => +b.closePrice)
  const highs = list.map((b) => +b.highPrice)
  const lows = list.map((b) => +b.lowPrice)
  chartPayload = { dates, highs, lows }
  const ma5 = ma(closes, 5)
  const ma10 = ma(closes, 10)
  const ma20 = ma(closes, 20)
  const ma60 = ma(closes, 60)
  const maSelected = {
    MA5: selectedMas.value.includes('MA5'),
    MA10: selectedMas.value.includes('MA10'),
    MA20: selectedMas.value.includes('MA20'),
    MA60: selectedMas.value.includes('MA60'),
  }
  const periodPctLabel =
    klinePeriod.value === 'week' ? '本周' : klinePeriod.value === 'month' ? '本月' : '当日'
  const unit = periodUnit.value
  const maColors = { MA5: '#1d1d1f', MA10: '#f59e0b', MA20: '#7c3aed', MA60: '#5c6bc0' }
  const maLegend = {
    MA5: `MA5${unit}`,
    MA10: `MA10${unit}`,
    MA20: `MA20${unit}`,
    MA60: `MA60${unit}`,
  }
  const maLegendItem = (key) => ({
    name: maLegend[key],
    itemStyle: { color: maColors[key] },
    lineStyle: { color: maColors[key] },
  })
  const { dif, dea, hist } = macd(closes)
  const crosses = macdCrosses(dif, dea)
  const goldenPoints = []
  const deathPoints = []
  for (let i = 0; i < crosses.length; i++) {
    if (crosses[i] === 'golden') goldenPoints.push([dates[i], dif[i]])
    if (crosses[i] === 'death') deathPoints.push([dates[i], dif[i]])
  }
  let lastCrossIdx = -1
  for (let i = crosses.length - 1; i >= 0; i--) {
    if (crosses[i]) {
      lastCrossIdx = i
      break
    }
  }
  if (lastCrossIdx >= 0) {
    const kind = crosses[lastCrossIdx] === 'golden' ? '金叉' : '死叉'
    macdTip.value = `最近 MACD${kind}：${dates[lastCrossIdx]}（DIF ${fmtNum(dif[lastCrossIdx], 3)} / DEA ${fmtNum(dea[lastCrossIdx], 3)}）`
  } else {
    macdTip.value = ''
  }
  const { buy: tdBuy, sell: tdSell } = tdSequential(closes)
  const tdBuyPoints = []
  const tdSellPoints = []
  const tdMinShow = tdShowMode.value === 'all' ? 1 : 8
  if (showTd9.value) {
    for (let i = 0; i < closes.length; i++) {
      if (tdBuy[i] >= tdMinShow) {
        const isNine = tdBuy[i] === 9
        tdBuyPoints.push({
          value: [dates[i], lows[i]],
          count: tdBuy[i],
          label: {
            show: true,
            formatter: String(tdBuy[i]),
            position: 'bottom',
            distance: 4,
            color: isNine ? '#26a69a' : 'rgba(38,166,154,0.82)',
            fontSize: isNine ? 11 : 10,
            fontWeight: isNine ? 700 : 600,
          },
          itemStyle: {
            color: isNine ? 'rgba(38,166,154,0.22)' : 'transparent',
            borderColor: isNine ? '#26a69a' : 'transparent',
            borderWidth: isNine ? 1 : 0,
          },
          symbolSize: isNine ? 12 : 1,
        })
      }
      if (tdSell[i] >= tdMinShow) {
        const isNine = tdSell[i] === 9
        tdSellPoints.push({
          value: [dates[i], highs[i]],
          count: tdSell[i],
          label: {
            show: true,
            formatter: String(tdSell[i]),
            position: 'top',
            distance: 4,
            color: isNine ? '#ef5350' : 'rgba(239,83,80,0.82)',
            fontSize: isNine ? 11 : 10,
            fontWeight: isNine ? 700 : 600,
          },
          itemStyle: {
            color: isNine ? 'rgba(239,83,80,0.22)' : 'transparent',
            borderColor: isNine ? '#ef5350' : 'transparent',
            borderWidth: isNine ? 1 : 0,
          },
          symbolSize: isNine ? 12 : 1,
        })
      }
    }
  }
  tdTip.value = ''
  for (let i = closes.length - 1; i >= 0; i--) {
    if (tdSell[i] === 9) {
      tdTip.value = `最近上涨九转(卖)：${dates[i]}`
      break
    }
    if (tdBuy[i] === 9) {
      tdTip.value = `最近下跌九转(买)：${dates[i]}`
      break
    }
  }
  if (!tdTip.value) {
    for (let i = closes.length - 1; i >= 0; i--) {
      if (tdSell[i] > 0) {
        tdTip.value = `上涨九转进行中：九转卖${tdSell[i]}（${dates[i]}）`
        break
      }
      if (tdBuy[i] > 0) {
        tdTip.value = `下跌九转进行中：九转买${tdBuy[i]}（${dates[i]}）`
        break
      }
    }
  }
  const { k: kLine, d: dLine, j: jLine } = kdj(highs, lows, closes)
  // J 常超出 0–100，轴略外扩避免上下被裁切
  let kdjMin = 0
  let kdjMax = 100
  for (const series of [kLine, dLine, jLine]) {
    for (const val of series) {
      if (val == null || Number.isNaN(val)) continue
      if (val < kdjMin) kdjMin = val
      if (val > kdjMax) kdjMax = val
    }
  }
  const kdjPad = Math.max((kdjMax - kdjMin) * 0.08, 4)
  // 保证 20/50/80 虚线标尺落在可视范围内
  kdjMin = Math.min(Math.floor(kdjMin - kdjPad), 15)
  kdjMax = Math.max(Math.ceil(kdjMax + kdjPad), 85)
  let zoomStart = defaultVisibleStart(list.length)
  let zoomEnd = 100
  if (!resetZoomNext && list.length > 0) {
    zoomStart = savedZoom.start
    zoomEnd = savedZoom.end
  }
  resetZoomNext = false
  const extremeMarkPoint = buildVisibleExtremeMarkPoint(dates, highs, lows, zoomStart, zoomEnd)
  const priceExtent = calcVisiblePriceExtent(highs, lows, zoomStart, zoomEnd)
  const compactPriceLabels = isMobileChart.value || chartRef.value?.clientWidth < 680
  compactPriceLabelsMode = compactPriceLabels
  const chartGridRight = isMobileChart.value ? 18 : compactPriceLabels ? 72 : 96
  const structureMarkLines = buildPriceLevelMarkLines(priceStructure.value, compactPriceLabels)

  chart.setOption({
    backgroundColor: 'transparent',
    animation: false,
    legend: {
      show: !isMobileChart.value,
      top: 0,
      left: 'center',
      itemWidth: 14,
      itemHeight: 8,
      itemGap: 12,
      icon: 'roundRect',
      data: [
        'K线',
        maLegendItem('MA5'),
        maLegendItem('MA10'),
        maLegendItem('MA20'),
        maLegendItem('MA60'),
        '成交量',
        'DIF',
        'DEA',
        'MACD',
        'K',
        'D',
        'J',
      ],
      selected: {
        [maLegend.MA5]: maSelected.MA5,
        [maLegend.MA10]: maSelected.MA10,
        [maLegend.MA20]: maSelected.MA20,
        [maLegend.MA60]: maSelected.MA60,
      },
      textStyle: { fontSize: 11, color: '#6b7280', fontWeight: 400 },
      inactiveColor: '#c5c5c7',
    },
    // 副图左侧名称：成交量 / MACD / KDJ
    title: [
      {
        text: '成交量',
        left: 8,
        top: '47%',
        textStyle: { fontSize: 11, color: 'rgba(107,114,128,0.85)', fontWeight: 600 },
      },
      {
        text: 'MACD',
        left: 8,
        top: '60%',
        textStyle: { fontSize: 11, color: 'rgba(107,114,128,0.85)', fontWeight: 600 },
      },
      {
        text: 'KDJ',
        left: 8,
        top: '75%',
        textStyle: { fontSize: 11, color: 'rgba(107,114,128,0.85)', fontWeight: 600 },
      },
    ],
    tooltip: {
      trigger: 'axis',
      triggerOn: isMobileChart.value ? 'none' : 'mousemove|click',
      axisPointer: {
        type: 'cross',
        snap: true,
        animation: false,
        lineStyle: { color: 'rgba(29,29,31,0.28)', width: 1, type: 'dashed' },
        crossStyle: { color: 'rgba(29,29,31,0.22)', width: 1 },
        label: {
          backgroundColor: 'rgba(255,255,255,0.72)',
          borderColor: 'rgba(0,0,0,0.06)',
          borderWidth: 1,
          color: '#3a3a3c',
          fontSize: 11,
          padding: [3, 6],
        },
      },
      confine: true,
      enterable: false,
      appendToBody: !isMobileChart.value,
      position: isMobileChart.value ? mobileTooltipPosition : undefined,
      transitionDuration: 0,
      className: 'kline-tip',
      borderWidth: 0,
      backgroundColor: 'transparent',
      padding: 0,
      extraCssText:
        'box-shadow:none;background:transparent;border:none;padding:0;backdrop-filter:none;',
      formatter(params) {
        const items = Array.isArray(params) ? params : [params]
        const idx = items[0]?.dataIndex
        if (idx == null || idx < 0) return ''
        const date = dates[idx]
        const candle = ohlc[idx] || []
        const open = candle[0]
        const close = candle[1]
        const low = candle[2]
        const high = candle[3]
        const dayPct = dayPctOf(list, closes, idx)
        const sincePct = sincePctOf(closes, idx)
        const isLatest = idx === closes.length - 1
        const sinceText = isLatest ? '0.00%' : fmtSignedPct(sincePct)
        const sinceLabel =
          sincePct == null || Number.isNaN(sincePct) || sincePct >= 0 ? '至今涨幅' : '至今跌幅'
        const sincePeriods = Math.max(1, closes.length - idx)
        const badges = []
        if (crosses[idx] === 'golden') {
          badges.push('<span class="kline-tip__badge kline-tip__badge--up">MACD 金叉</span>')
        } else if (crosses[idx] === 'death') {
          badges.push('<span class="kline-tip__badge kline-tip__badge--down">MACD 死叉</span>')
        }
        if (tdSell[idx] === 9) {
          badges.push('<span class="kline-tip__badge kline-tip__badge--up">上涨九转</span>')
        } else if (tdBuy[idx] === 9) {
          badges.push('<span class="kline-tip__badge kline-tip__badge--down">下跌九转</span>')
        }
        const crossBadge = badges.join('')

        const maVals = { MA5: ma5[idx], MA10: ma10[idx], MA20: ma20[idx], MA60: ma60[idx] }
        const maChips = ['MA5', 'MA10', 'MA20', 'MA60']
          .filter((name) => maSelected[name])
          .map((name) => {
            const v = maVals[name]
            if (v == null) return ''
            return `<span class="kline-tip__chip"><i style="color:${maColors[name]}">${maLegend[name]}</i><b>${fmtNum(v)}</b></span>`
          })
          .join('')
        const chipRow = (items) =>
          items
            .filter(([, v]) => v != null && v !== '-')
            .map(([name, v, color, valueColor]) => {
              const num = fmtNum(v, 2)
              const valStyle = valueColor ? ` style="color:${valueColor}"` : ''
              return `<span class="kline-tip__chip"><i style="color:${color}">${name}</i><b${valStyle}>${num}</b></span>`
            })
            .join('')
        const macdVal = hist[idx]
        const macdChips = chipRow([
          ['DIF', dif[idx], '#1f6f5b'],
          ['DEA', dea[idx], '#c79100'],
          [
            'MACD',
            macdVal,
            '#86868b',
            macdVal == null || Number.isNaN(Number(macdVal))
              ? null
              : Number(macdVal) >= 0
                ? '#ef5350'
                : '#26a69a',
          ],
        ])
        const kdjChips = chipRow([
          ['K', kLine[idx], '#c79100'],
          ['D', dLine[idx], '#5c6bc0'],
          ['J', jLine[idx], '#6a4c93'],
        ])
        const amount = amounts[idx]
        const amountHtml =
          amount == null || Number.isNaN(Number(amount))
            ? ''
            : `<span><em>额</em><b>${fmtVol(amount)}</b></span>`
        const turnover = turnovers[idx]
        const turnoverHtml =
          turnover == null || Number.isNaN(Number(turnover))
            ? ''
            : `<span><em>换</em><b>${Number(turnover).toFixed(2)}%</b></span>`

        return `
<div class="kline-tip__card">
  <div class="kline-tip__head">
    <span class="kline-tip__date">${date}</span>
    ${crossBadge}
  </div>
  <div class="kline-tip__price-row">
    <span class="kline-tip__price" style="color:${pctColor(dayPct)}">${fmtNum(close)}</span>
    <span class="kline-tip__day">
      <em>${periodPctLabel}</em>
      <b style="color:${pctColor(dayPct)}">${fmtSignedPct(dayPct)}</b>
    </span>
  </div>
  <div class="kline-tip__metrics">
    <span class="kline-tip__metric">
      <em>${sinceLabel}</em>
      <b style="color:${pctColor(sincePct)}">${sinceText}</b>
    </span>
    <span class="kline-tip__periods">${sincePeriods}${unit}</span>
  </div>
  <div class="kline-tip__ohlc">
    <span><em>开</em><b>${fmtNum(open)}</b></span>
    <span><em>高</em><b style="color:#ef5350">${fmtNum(high)}</b></span>
    <span><em>低</em><b style="color:#26a69a">${fmtNum(low)}</b></span>
    <span><em>收</em><b>${fmtNum(close)}</b></span>
  </div>
  <div class="kline-tip__vol">
    <span><em>量</em><b>${fmtVol(volumes[idx])}</b></span>
    ${amountHtml}
    ${turnoverHtml}
  </div>
  ${maChips ? `<div class="kline-tip__row">${maChips}</div>` : ''}
  ${macdChips ? `<div class="kline-tip__row">${macdChips}</div>` : ''}
  ${kdjChips ? `<div class="kline-tip__row">${kdjChips}</div>` : ''}
</div>`
      },
    },
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    grid: [
      // 四个子图保持同宽；移动端隐藏图内价格牌，释放右侧绘图区。
      { left: 56, right: chartGridRight, top: isMobileChart.value ? 12 : 36, height: '38%' },
      { left: 56, right: chartGridRight, top: '47%', height: '9%' },
      { left: 56, right: chartGridRight, top: '60%', height: '11%' },
      { left: 56, right: chartGridRight, top: '75%', height: '10%' },
    ],
    xAxis: [
      {
        type: 'category',
        data: dates,
        boundaryGap: true,
        axisLine: { onZero: false, lineStyle: { color: 'rgba(0,0,0,0.22)' } },
        axisTick: { show: false },
        axisLabel: {
          show: true,
          color: '#86868b',
          fontSize: 11,
          hideOverlap: true,
          margin: 6,
        },
        min: 'dataMin',
        max: 'dataMax',
      },
      {
        type: 'category',
        gridIndex: 1,
        data: dates,
        boundaryGap: true,
        axisLabel: { show: false },
        axisTick: { show: false },
        axisLine: { show: false },
        min: 'dataMin',
        max: 'dataMax',
      },
      {
        type: 'category',
        gridIndex: 2,
        data: dates,
        boundaryGap: true,
        axisLabel: { show: false },
        axisTick: { show: false },
        axisLine: { show: false },
        min: 'dataMin',
        max: 'dataMax',
      },
      {
        type: 'category',
        gridIndex: 3,
        data: dates,
        boundaryGap: true,
        axisLabel: { show: false },
        axisTick: { show: false },
        axisLine: { show: false },
        min: 'dataMin',
        max: 'dataMax',
      },
    ],
    yAxis: [
      {
        scale: true,
        min: priceExtent.min,
        max: priceExtent.max,
        splitNumber: 4,
        splitArea: { show: true, areaStyle: { color: ['rgba(255,255,255,0.08)', 'rgba(0,0,0,0.015)'] } },
        splitLine: { lineStyle: { color: 'rgba(0,0,0,0.05)' } },
        axisLabel: { color: '#86868b', fontSize: 11 },
      },
      { scale: true, gridIndex: 1, splitNumber: 2, axisLabel: { show: false }, axisLine: { show: false }, axisTick: { show: false }, splitLine: { show: false } },
      { scale: true, gridIndex: 2, splitNumber: 2, axisLabel: { show: false }, axisLine: { show: false }, axisTick: { show: false }, splitLine: { show: false } },
      {
        min: kdjMin,
        max: kdjMax,
        gridIndex: 3,
        splitNumber: 2,
        axisLabel: { show: false },
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
      },
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1, 2, 3], start: zoomStart, end: zoomEnd },
      { show: true, xAxisIndex: [0, 1, 2, 3], type: 'slider', top: '94%', start: zoomStart, end: zoomEnd },
    ],
    series: [
      {
        id: 'kline-main',
        name: 'K线',
        type: 'candlestick',
        data: ohlc,
        itemStyle: { color: '#ef5350', color0: '#26a69a', borderColor: '#ef5350', borderColor0: '#26a69a' },
        // 阶段高低钉在当日影线最高/最低
        markPoint: extremeMarkPoint,
        markLine: {
          silent: true,
          symbol: 'none',
          data: structureMarkLines,
        },
      },
      {
        name: maLegend.MA5,
        type: 'line',
        data: ma5,
        smooth: true,
        showSymbol: false,
        color: maColors.MA5,
        itemStyle: { color: maColors.MA5 },
        lineStyle: { width: 1.2, color: maColors.MA5 },
      },
      {
        name: maLegend.MA10,
        type: 'line',
        data: ma10,
        smooth: true,
        showSymbol: false,
        color: maColors.MA10,
        itemStyle: { color: maColors.MA10 },
        lineStyle: { width: 1.2, color: maColors.MA10 },
      },
      {
        name: maLegend.MA20,
        type: 'line',
        data: ma20,
        smooth: true,
        showSymbol: false,
        color: maColors.MA20,
        itemStyle: { color: maColors.MA20 },
        lineStyle: { width: 1.5, color: maColors.MA20 },
      },
      {
        name: maLegend.MA60,
        type: 'line',
        data: ma60,
        smooth: true,
        showSymbol: false,
        color: maColors.MA60,
        itemStyle: { color: maColors.MA60 },
        lineStyle: { width: 1.5, color: maColors.MA60 },
      },
      {
        name: '九转卖',
        type: 'scatter',
        data: tdSellPoints,
        symbol: 'circle',
        z: 12,
        legendHoverLink: false,
        tooltip: { show: false },
      },
      {
        name: '九转买',
        type: 'scatter',
        data: tdBuyPoints,
        symbol: 'circle',
        z: 12,
        legendHoverLink: false,
        tooltip: { show: false },
      },
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
      {
        name: '金叉',
        type: 'scatter',
        xAxisIndex: 2,
        yAxisIndex: 2,
        data: goldenPoints,
        symbol: 'triangle',
        symbolSize: 11,
        legendHoverLink: false,
        itemStyle: { color: '#ef5350' },
        label: {
          show: true,
          formatter: '金',
          position: 'top',
          color: '#ef5350',
          fontSize: 10,
          fontWeight: 700,
        },
        z: 10,
      },
      {
        name: '死叉',
        type: 'scatter',
        xAxisIndex: 2,
        yAxisIndex: 2,
        data: deathPoints,
        symbol: 'triangle',
        symbolRotate: 180,
        symbolSize: 11,
        legendHoverLink: false,
        itemStyle: { color: '#26a69a' },
        label: {
          show: true,
          formatter: '死',
          position: 'bottom',
          color: '#26a69a',
          fontSize: 10,
          fontWeight: 700,
        },
        z: 10,
      },
      {
        name: 'K',
        type: 'line',
        xAxisIndex: 3,
        yAxisIndex: 3,
        data: kLine,
        showSymbol: false,
        clip: false,
        lineStyle: { width: 1.2, color: '#c79100' },
      },
      {
        name: 'D',
        type: 'line',
        xAxisIndex: 3,
        yAxisIndex: 3,
        data: dLine,
        showSymbol: false,
        clip: false,
        lineStyle: { width: 1.2, color: '#5c6bc0' },
      },
      {
        name: 'J',
        type: 'line',
        xAxisIndex: 3,
        yAxisIndex: 3,
        data: jLine,
        showSymbol: false,
        clip: false,
        lineStyle: { width: 1.2, color: '#6a4c93' },
      },
      {
        // 不进图例：东财风格 20 / 50 / 80 虚线标尺
        name: 'KDJ标尺',
        type: 'line',
        xAxisIndex: 3,
        yAxisIndex: 3,
        data: [],
        silent: true,
        legendHoverLink: false,
        showSymbol: false,
        tooltip: { show: false },
        lineStyle: { opacity: 0, width: 0 },
        markLine: {
          silent: true,
          symbol: 'none',
          animation: false,
          label: {
            show: true,
            position: 'end',
            distance: 2,
            color: '#a1a1a6',
            fontSize: 10,
            formatter: '{c}',
          },
          lineStyle: {
            color: 'rgba(0, 0, 0, 0.28)',
            type: 'dashed',
            width: 1,
          },
          data: [{ yAxis: 20 }, { yAxis: 50 }, { yAxis: 80 }],
        },
      },
    ],
  }, true)
  chart.resize()
  bindMobileChartPress()
}

async function load(refreshQuote = false) {
  if (!code.value) return
  loading.value = true
  try {
    const res = await fetchStockDetail(code.value.trim(), BAR_LIMIT, false)
    applyDetail(res.data)
    await loadFundamental()
    // 静默拉概况：回填东财二级行业到 meta，并同步 stock_basic.industry
    loadProfile(false).then(() => {
      if (profile.value?.industryL2 && basic.value) {
        basic.value = { ...basic.value, industry: profile.value.industryL2 }
      }
    })
    if (refreshQuote) {
      await syncStockBasic(code.value.trim())
      const again = await fetchStockDetail(code.value.trim(), BAR_LIMIT, false)
      applyDetail(again.data)
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadFundamental() {
  if (!code.value) return
  fundLoading.value = true
  try {
    const res = await fetchStockFundamental(code.value.trim(), 40, 12)
    fund.value = res.data || null
  } catch (e) {
    fund.value = null
    console.warn('加载基本面失败', e)
  } finally {
    fundLoading.value = false
  }
}

async function loadProfile(force = false) {
  if (!code.value) return
  profileLoading.value = true
  try {
    const res = force
      ? await refreshCompanyProfile(code.value.trim())
      : await fetchCompanyProfile(code.value.trim(), false)
    profile.value = res.data || null
    profileExpand.value = false
    mainBusinessExpand.value = false
    conceptExpand.value = false
  } catch (e) {
    profile.value = null
    if (force) ElMessage.error(e.message || '刷新公司概况失败')
    else console.warn('加载公司概况失败', e)
  } finally {
    profileLoading.value = false
  }
}

async function onRefreshProfile() {
  profileRefreshing.value = true
  try {
    await loadProfile(true)
    if (profile.value?.orgName) ElMessage.success('公司概况已刷新')
  } finally {
    profileRefreshing.value = false
  }
}

const visibleConcepts = computed(() => {
  const list = profile.value?.conceptList || []
  if (conceptExpand.value || list.length <= 12) return list
  return list.slice(0, 12)
})

const profileText = computed(() => {
  const text = profile.value?.orgProfile || ''
  if (profileExpand.value || text.length <= 220) return text
  return `${text.slice(0, 220)}…`
})

const mainBusinessText = computed(() => {
  const text = profile.value?.mainBusiness || ''
  if (mainBusinessExpand.value || text.length <= 90) return text
  return `${text.slice(0, 90)}…`
})

function fmtRatio(v) {
  if (v == null || v === '') return ''
  const n = Number(v)
  if (!Number.isFinite(n)) return ''
  return `${n.toFixed(2)}%`
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
  refreshChart()
}

async function syncStockData() {
  if (!code.value) return
  syncingBars.value = true
  try {
    const pure = code.value.trim()
    const res = await syncBars({ codes: [pure] })
    const data = res.data || {}
    try {
      await syncStockBasic(pure)
    } catch (quoteErr) {
      console.warn('同步日后刷新行情失败', quoteErr)
    }
    ElMessage.success(`已同步：日线 ${data.barCount ?? 0} 根，现价已更新`)
    const detail = await fetchStockDetail(pure, BAR_LIMIT, false)
    applyDetail(detail.data)
  } catch (e) {
    ElMessage.error(e.message || '同步失败')
  } finally {
    syncingBars.value = false
  }
}

const observeSaving = ref(false)

/** 一键加入观察池（默认 WATCHING） */
async function quickAddObserve() {
  const stockCode = code.value.trim()
  if (!stockCode) return
  observeSaving.value = true
  try {
    await saveObserve({
      code: stockCode,
      name: basic.value?.name || '',
      status: 'WATCHING',
      reason: '个股页快捷加入',
    })
    ElMessage.success('已加入观察池')
  } catch (e) {
    ElMessage.error(e.message || '加入失败')
  } finally {
    observeSaving.value = false
  }
}

function onResize() {
  chart?.resize()
  const nextMobileChart = window.matchMedia('(max-width: 820px)').matches
  if (nextMobileChart !== isMobileChart.value) {
    isMobileChart.value = nextMobileChart
    chartParamsExpanded.value = !nextMobileChart
    if (activeTab.value === 'chart') refreshChart()
    return
  }
  if (isIntraday.value || activeTab.value !== 'chart' || !bars.value.length || !chartRef.value) return
  const nextCompactMode = isMobileChart.value || chartRef.value.clientWidth < 680
  if (nextCompactMode !== compactPriceLabelsMode) refreshChart()
}

watch(
  () => route.params.code,
  (v) => {
    if (v) {
      code.value = String(v)
      resetZoomNext = true
      profile.value = null
      intraday.value = null
      intradayAsOf.value = ''
      load(false)
      if (activeTab.value === 'profile') loadProfile(false)
      if (activeTab.value === 'chart' && isIntraday.value) {
        loadIntraday()
        startIntradayPoll()
      } else {
        stopIntradayPoll()
      }
    }
  },
)

watch(
  () => route.query.tab,
  (tab) => {
    if (tab === 'valuation') activeTab.value = 'valuation'
  },
)

watch(klinePeriod, () => {
  resetZoomNext = true
  saveChartPrefs()
  if (activeTab.value !== 'chart') return
  // 周期切换会触发图表容器 v-if 重建，先释放旧实例避免白屏
  disposeChart()
  if (isIntraday.value) {
    loadIntraday()
    startIntradayPoll()
    return
  }
  stopIntradayPoll()
  if (bars.value.length) refreshChart()
})

watch(
  [selectedMas, showTd9, tdShowMode],
  () => {
    if (syncingFromLegend) {
      saveChartPrefs()
      return
    }
    saveChartPrefs()
    if (!isIntraday.value && bars.value.length && activeTab.value === 'chart') refreshChart()
  },
  { deep: true },
)

function rememberRecentStock(stockCode, stockName) {
  const c = String(stockCode || '').trim()
  if (!c) return
  try {
    const key = 'apex.search.recent'
    const prev = JSON.parse(localStorage.getItem(key) || '[]')
    const list = Array.isArray(prev) ? prev : []
    const next = [{ code: c, name: stockName || '' }, ...list.filter((r) => r.code !== c)].slice(0, 8)
    localStorage.setItem(key, JSON.stringify(next))
  } catch {
    /* ignore */
  }
}

onMounted(async () => {
  loadChartPrefs()
  await load(false)
  rememberRecentStock(code.value, basic.value?.name)
  if (isIntraday.value) {
    await loadIntraday()
    startIntradayPoll()
  }
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  stopIntradayPoll()
  disposeChart()
})

function fmtMv(v) {
  if (v == null) return '-'
  const n = Number(v)
  if (n >= 1e12) return (n / 1e12).toFixed(2) + '万亿'
  if (n >= 1e8) return (n / 1e8).toFixed(2) + '亿'
  return n.toFixed(2)
}

function fmtMoney(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  const abs = Math.abs(n)
  const sign = n < 0 ? '-' : ''
  if (abs >= 1e12) return sign + (abs / 1e12).toFixed(2) + '万亿'
  if (abs >= 1e8) return sign + (abs / 1e8).toFixed(2) + '亿'
  if (abs >= 1e4) return sign + (abs / 1e4).toFixed(2) + '万'
  return sign + abs.toFixed(2)
}

function fmtNum(v, digits = 2) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toFixed(digits)
}

function fmtPct(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toFixed(2) + '%'
}

function sheetCell(row, idx) {
  const text = row?.texts?.[idx]
  if (text) return text
  return fmtMoney(row?.values?.[idx])
}

function onTabChange(name) {
  if (name === 'chart') {
    nextTick(() => {
      refreshChart()
      if (isIntraday.value) startIntradayPoll()
      else stopIntradayPoll()
    })
  } else {
    stopIntradayPoll()
  }
  if (name === 'profile' && !profile.value) {
    loadProfile(false)
  }
  // 综合研判 Tab 由 StockAnalysisPanel 自行按 code 加载
}

function dash(v) {
  return v == null || v === '' ? '-' : v
}
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="header">
      <div class="stock-heading">
        <p class="eyebrow">灵枢 · Stock</p>
        <h1>
          <span class="stock-name">{{ basic?.name || '股票详情' }}</span>
          <SecurityMarketBadge :security="basic || { code }" />
          <span class="code">{{ basic?.code || code }}</span>
        </h1>
        <p class="stock-note">{{ note || 'K线 · 综合研判 · 估值 · 回测 · 观察池' }}</p>
      </div>
      <div class="actions">
        <el-button type="primary" :loading="syncingBars" @click="syncStockData">同步数据</el-button>
        <el-button type="warning" plain @click="activeTab = 'analysis'">综合研判</el-button>
        <el-button plain @click="router.push('/decision')">决策</el-button>
        <el-button plain @click="activeTab = 'valuation'">估值</el-button>
        <el-button plain @click="router.push({ path: '/backtest', query: { code: code.trim() } })">回测</el-button>
        <el-button plain @click="router.push({ path: '/paper', query: { code: code.trim(), side: 'BUY' } })">模拟买</el-button>
        <el-button type="warning" plain :loading="observeSaving" @click="quickAddObserve">加入观察池</el-button>
        <el-button text @click="router.push({ path: '/observe', query: { code: code.trim() } })">看观察池</el-button>
      </div>
    </header>

    <section class="meta-panel" v-if="basic">
      <div class="meta-bar">
        <div class="meta-bar-main">
          <span class="meta-price" :class="basic.pctChg >= 0 ? 'up' : 'down'">{{ basic.latestPrice ?? '-' }}</span>
          <span class="meta-pct" :class="basic.pctChg >= 0 ? 'up' : 'down'">
            {{ basic.pctChg != null ? (Number(basic.pctChg) > 0 ? '+' : '') + Number(basic.pctChg).toFixed(2) + '%' : '-' }}
          </span>
          <span class="meta-chip">PE TTM {{ basic.peTtm ?? '-' }}</span>
          <span class="meta-chip">PB {{ basic.pb ?? '-' }}</span>
          <span class="meta-chip">总市值 {{ fmtMv(basic.totalMv) }}</span>
          <span class="meta-chip">{{ profile?.industryL2 || basic.industry || basic.market || '-' }}</span>
          <span class="meta-chip">量比 {{ volumeRatio ?? '-' }}</span>
        </div>
        <button type="button" class="meta-toggle" @click="toggleMetaExpanded">
          {{ metaExpanded ? '收起指标' : '展开指标' }}
        </button>
      </div>
      <div v-show="metaExpanded" class="meta">
        <div><label>最新价</label><b :class="basic.pctChg >= 0 ? 'up' : 'down'">{{ basic.latestPrice ?? '-' }}</b></div>
        <div><label><TermTip term="pct_chg">涨跌幅</TermTip></label><b :class="basic.pctChg >= 0 ? 'up' : 'down'">{{ basic.pctChg != null ? basic.pctChg + '%' : '-' }}</b></div>
        <div><label><TermTip term="pe_dynamic">市盈率（动）</TermTip></label><span>{{ basic.peDynamic ?? '-' }}</span></div>
        <div><label><TermTip term="pe_static">市盈率（静）</TermTip></label><span>{{ basic.peStatic ?? '-' }}</span></div>
        <div><label><TermTip term="pe_ttm">市盈率（TTM）</TermTip></label><span>{{ basic.peTtm ?? '-' }}</span></div>
        <div><label><TermTip term="pb">市净率</TermTip></label><span>{{ basic.pb ?? '-' }}</span></div>
        <div><label><TermTip term="total_mv">总市值</TermTip></label><span>{{ fmtMv(basic.totalMv) }}</span></div>
        <div><label><TermTip term="circ_mv">流通市值</TermTip></label><span>{{ fmtMv(basic.circMv) }}</span></div>
        <div><label>市场</label><span>{{ basic.market || '-' }}</span></div>
        <div><label>行业</label><span>{{ profile?.industryL2 || basic.industry || '-' }}</span></div>
        <div><label>上市</label><span>{{ basic.listDate || '-' }}</span></div>
        <div><label>来源</label><span>{{ basic.source || '-' }}</span></div>
        <div><label>本地日线</label><span>{{ barCount }}</span></div>
        <div>
          <label><TermTip term="rs20">RS20 vs沪深300</TermTip></label>
          <b :class="Number(rs20) >= 0 ? 'up' : 'down'">{{ rs20 != null ? rs20 + 'pp' : '-' }}</b>
        </div>
        <div>
          <label><TermTip term="rs60">RS60 vs沪深300</TermTip></label>
          <b :class="Number(rs60) >= 0 ? 'up' : 'down'">{{ rs60 != null ? rs60 + 'pp' : '-' }}</b>
        </div>
        <div><label><TermTip term="volume_ratio">量比</TermTip></label><b :class="Number(volumeRatio) >= 1.5 ? 'up' : ''">{{ volumeRatio ?? '-' }}</b></div>
      </div>
    </section>

    <el-alert
      v-if="!loading && bars.length && needSyncBars"
      class="hint"
      type="warning"
      :closable="false"
      show-icon
      :title="`本地仅 ${barCount} 根日线，建议同步补齐后再做指标/回测`"
    />

    <el-tabs v-model="activeTab" class="tabs" @tab-change="onTabChange">
      <el-tab-pane label="综合研判" name="analysis" lazy>
        <StockAnalysisPanel v-if="basic?.code || code" :code="String(basic?.code || code).trim()" />
      </el-tab-pane>
      <el-tab-pane label="估值" name="valuation" lazy>
        <ValuationView
          embedded
          :stock-code="String(basic?.code || code).trim()"
        />
      </el-tab-pane>
      <el-tab-pane label="行情图表" name="chart">
        <el-empty
          v-if="!loading && !bars.length && !isIntraday"
          class="empty-bars"
          description="本地暂无日线，请先同步数据落库；也可先看分时"
        >
          <el-button type="primary" :loading="syncingBars" @click="syncStockData">同步数据</el-button>
          <el-button @click="klinePeriod = 'intraday'">看分时</el-button>
        </el-empty>
        <div v-if="showChartShell" class="chart-toolbar" v-loading="intradayLoading && isIntraday">
          <div class="chart-primary-controls">
            <el-radio-group v-model="klinePeriod" size="small" class="period-mode">
              <el-radio-button value="intraday">分时</el-radio-button>
              <el-radio-button value="day">日K</el-radio-button>
              <el-radio-button value="week">周K</el-radio-button>
              <el-radio-button value="month">月K</el-radio-button>
            </el-radio-group>
            <div class="chart-primary-actions">
              <el-button
                v-if="isIntraday"
                size="small"
                text
                type="primary"
                :loading="intradayLoading"
                @click="loadIntraday"
              >
                刷新分时
              </el-button>
              <button
                v-if="isMobileChart && !isIntraday"
                type="button"
                class="chart-params-toggle"
                :aria-expanded="chartParamsExpanded"
                @click="toggleChartParams"
              >
                <span>图表参数</span>
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="m6 9 6 6 6-6" />
                </svg>
              </button>
              <el-button size="small" text type="primary" class="reset-view" @click="resetChartView">
                重置视野
              </el-button>
            </div>
            <span v-if="periodMeta" class="period-meta">{{ periodMeta }}</span>
            <span v-if="isIntraday && intradayAsOf" class="intraday-asof">数据截至 {{ intradayAsOf }}</span>
          </div>
          <div v-if="!isIntraday" v-show="!isMobileChart || chartParamsExpanded" class="chart-advanced-controls">
            <el-checkbox-group v-model="selectedMas" size="small" class="ma-checks">
              <el-checkbox
                v-for="item in MA_META"
                :key="item.name"
                :value="item.name"
                :style="{ '--ma-color': item.color }"
              >
                {{ maDisplayName(item.name) }}
              </el-checkbox>
            </el-checkbox-group>
            <label class="ctrl-label">
              <TermTip term="td9">神奇九转</TermTip>
              <el-switch v-model="showTd9" size="small" />
            </label>
            <el-radio-group
              v-if="showTd9"
              v-model="tdShowMode"
              size="small"
              class="td-mode"
            >
              <el-radio-button value="key">仅8/9</el-radio-button>
              <el-radio-button value="all">全部</el-radio-button>
            </el-radio-group>
          </div>
          <el-alert
            v-if="!isIntraday && klinePeriod !== 'day' && bars.length < 120"
            class="chart-alert"
            type="info"
            :closable="false"
            show-icon
            :title="`日线仅 ${bars.length} 根，周/月K样本偏少，建议同步更多日线`"
          />
          <p class="chart-hint">
            <template v-if="isIntraday">
              东财分时 · 价格 / 均价 / 成交量 · 右侧为相对昨收涨跌幅
            </template>
            <template v-else>
              设置会记住 · 切换周期重置视野 · 改均线/九转保持缩放 · 副图：量 /
              <TermTip term="macd">MACD</TermTip>
              /
              <TermTip term="kdj">KDJ</TermTip>
              · 常见还有
              <TermTip term="rsi">RSI</TermTip>
              /
              <TermTip term="atr">ATR</TermTip>
              /
              <TermTip term="boll">布林带</TermTip>
            </template>
          </p>
          <p v-if="!isIntraday && tdTip && showTd9" class="macd-tip">{{ tdTip }}</p>
          <p v-if="!isIntraday && macdTip" class="macd-tip macd-tip--sub">{{ macdTip }}</p>
          <div v-if="isMobileChart && !isIntraday && priceStructure.ready" class="chart-price-levels">
            <span v-if="priceStructure.support" class="support">
              支撑 {{ fmtNum(priceStructure.support.price) }}
            </span>
            <span v-if="priceStructure.resistance" class="resistance">
              压力 {{ fmtNum(priceStructure.resistance.price) }}
            </span>
            <small>长按图表查看详情</small>
          </div>
        </div>
        <el-empty
          v-if="isIntraday && !intradayLoading && !intradayPoints.length"
          description="暂无分时数据"
        >
          <el-button type="primary" :loading="intradayLoading" @click="loadIntraday">重新拉取</el-button>
        </el-empty>
        <div
          v-if="(bars.length && !isIntraday) || (isIntraday && intradayPoints.length)"
          ref="chartRef"
          class="chart"
        />
        <ChipDistributionPanel
          v-if="!isIntraday && priceStructure.ready"
          :analysis="priceStructure"
        />
        <el-alert
          v-else-if="!isIntraday && bars.length"
          class="structure-insufficient"
          type="info"
          :closable="false"
          show-icon
          :title="`支撑压力与筹码分布至少需要 ${priceStructure.minimumSampleSize || 20} 根有效日线，当前 ${priceStructure.sampleSize || 0} 根`"
        />
      </el-tab-pane>

      <el-tab-pane label="公司概况" name="profile" lazy>
        <div v-loading="profileLoading" class="profile-wrap">
          <div class="profile-toolbar">
            <p class="fund-note">{{ profile?.note || '东财 F10 公司基本资料' }}</p>
            <el-button size="small" type="primary" :loading="profileRefreshing" @click="onRefreshProfile">
              刷新概况
            </el-button>
          </div>

          <el-empty v-if="!profileLoading && !profile?.orgName" description="暂无公司概况，点击刷新从东财拉取">
            <el-button type="primary" :loading="profileRefreshing" @click="onRefreshProfile">拉取公司概况</el-button>
          </el-empty>

          <template v-else-if="profile?.orgName">
            <section class="profile-card">
              <h3 class="profile-section-title">基本资料</h3>
              <div class="profile-name-block">
                <div class="profile-org">{{ profile.orgName }}</div>
                <div v-if="profile.businessTags?.length" class="profile-tags">
                  <span v-for="tag in profile.businessTags" :key="tag" class="profile-tag">{{ tag }}</span>
                </div>
                <p v-if="profile.orgHighlight" class="profile-highlight">
                  <span class="star">★</span> 公司亮点 · {{ profile.orgHighlight }}
                </p>
              </div>

              <div class="profile-kv">
                <div class="kv full">
                  <label>曾用名</label>
                  <span>{{ dash(profile.formerName) }}</span>
                </div>
                <div class="kv">
                  <label>A股代码</label>
                  <span>{{ dash(profile.aCode) }}</span>
                </div>
                <div class="kv">
                  <label>A股简称</label>
                  <span>{{ dash(profile.aName) }}</span>
                </div>
                <div class="kv">
                  <label>所属地区</label>
                  <span>{{ dash(profile.areaBoard || profile.region) }}</span>
                </div>
                <div class="kv">
                  <label>交易市场</label>
                  <span>{{ dash(profile.tradeMarket) }}</span>
                </div>
                <div class="kv">
                  <label>东财二级行业</label>
                  <span>{{ dash(profile.industryL2 || profile.industryEm) }}</span>
                </div>
                <div class="kv full">
                  <label>行业路径</label>
                  <span>{{ dash(profile.boardPath) }}</span>
                </div>
                <div class="kv full">
                  <label>证监会行业</label>
                  <span>{{ dash(profile.industryCsrc) }}</span>
                </div>
                <div class="kv full">
                  <label>所属概念</label>
                  <div class="concept-box">
                    <button
                      v-for="c in visibleConcepts"
                      :key="c"
                      type="button"
                      class="concept-chip"
                    >{{ c }}</button>
                    <button
                      v-if="(profile.conceptList || []).length > 12"
                      type="button"
                      class="concept-more"
                      @click="conceptExpand = !conceptExpand"
                    >{{ conceptExpand ? '收起' : '展开' }}</button>
                    <span v-if="!(profile.conceptList || []).length">-</span>
                  </div>
                </div>
                <div class="kv">
                  <label>董事长</label>
                  <span>{{ dash(profile.chairman) }}</span>
                </div>
                <div class="kv">
                  <label>法人代表</label>
                  <span>{{ dash(profile.legalPerson) }}</span>
                </div>
                <div class="kv">
                  <label>总经理</label>
                  <span>{{ dash(profile.president) }}</span>
                </div>
                <div class="kv">
                  <label>董秘</label>
                  <span>{{ dash(profile.secretary) }}</span>
                </div>
                <div class="kv full">
                  <label>控股股东</label>
                  <span>
                    {{ dash(profile.controlHolder) }}
                    <em v-if="profile.controlRatio">（直接持有 {{ profile.controlRatio }}）</em>
                  </span>
                </div>
                <div class="kv full">
                  <label>实际控制人</label>
                  <span>
                    {{ dash(profile.realController) }}
                    <em v-if="profile.realControllerRatio">（{{ profile.realControllerRatio }}）</em>
                  </span>
                </div>
                <div class="kv">
                  <label>经营性质</label>
                  <span>{{ dash(profile.orgForm) }}</span>
                </div>
                <div class="kv">
                  <label>成立日期</label>
                  <span>{{ dash(profile.foundDate) }}</span>
                </div>
                <div class="kv">
                  <label>注册资本</label>
                  <span>{{ dash(profile.regCapitalText) }}</span>
                </div>
                <div class="kv">
                  <label>上市日期</label>
                  <span>{{ dash(profile.listDate) }}</span>
                </div>
                <div class="kv">
                  <label>发行价格</label>
                  <span>{{ profile.issuePrice != null ? profile.issuePrice + ' 元' : '-' }}</span>
                </div>
                <div class="kv">
                  <label>员工人数</label>
                  <span>{{ dash(profile.employeeNum) }}</span>
                </div>
                <div class="kv">
                  <label>管理层人数</label>
                  <span>{{ dash(profile.managerNum) }}</span>
                </div>
                <div class="kv full">
                  <label>公司网站</label>
                  <span>
                    <a v-if="profile.website" :href="profile.website.startsWith('http') ? profile.website : 'https://' + profile.website" target="_blank" rel="noreferrer">{{ profile.website }}</a>
                    <template v-else>-</template>
                  </span>
                </div>
                <div class="kv">
                  <label>电子邮箱</label>
                  <span>{{ dash(profile.email) }}</span>
                </div>
                <div class="kv">
                  <label>联系电话</label>
                  <span>{{ dash(profile.phone) }}</span>
                </div>
                <div class="kv full">
                  <label>办公地址</label>
                  <span>{{ dash(profile.officeAddress) }}</span>
                </div>
                <div class="kv full">
                  <label>注册地址</label>
                  <span>{{ dash(profile.regAddress) }}</span>
                </div>
                <div class="kv full">
                  <label>统一社会信用代码</label>
                  <span>{{ dash(profile.regNum) }}</span>
                </div>
              </div>
            </section>

            <section class="profile-card">
              <h3 class="profile-section-title">经营业务</h3>
              <div class="biz-rows">
                <div class="biz-row">
                  <label>主营业务</label>
                  <div class="biz-content">
                    <span>{{ dash(mainBusinessText) }}</span>
                    <button
                      v-if="(profile.mainBusiness || '').length > 90"
                      type="button"
                      class="profile-more-btn"
                      @click="mainBusinessExpand = !mainBusinessExpand"
                    >{{ mainBusinessExpand ? '收起' : '展开' }}</button>
                  </div>
                </div>
                <div class="biz-row">
                  <label>最赚钱业务</label>
                  <div class="biz-content">
                    <template v-if="profile.topProfitBusiness">
                      <span>{{ profile.topProfitBusiness }}</span>
                      <em v-if="profile.topProfitRatio != null">（利润比例{{ fmtRatio(profile.topProfitRatio) }}）</em>
                    </template>
                    <span v-else>-</span>
                  </div>
                </div>
                <div class="biz-row">
                  <label>收入构成</label>
                  <div class="biz-content">
                    <template v-if="(profile.revenueItems || []).length">
                      <div
                        v-for="item in profile.revenueItems"
                        :key="item.name"
                        class="revenue-item"
                      >
                        <span>{{ item.name }}</span>
                        <em v-if="item.revenueRatio != null">（营收占比{{ fmtRatio(item.revenueRatio) }}）</em>
                      </div>
                      <small v-if="profile.revenueReportDate" class="revenue-date">
                        报告期 {{ profile.revenueReportDate }}
                      </small>
                    </template>
                    <span v-else>-</span>
                  </div>
                </div>
              </div>
            </section>

            <section class="profile-card">
              <h3 class="profile-section-title">公司介绍</h3>
              <p class="profile-intro">{{ profileText || '-' }}</p>
              <button
                v-if="(profile.orgProfile || '').length > 220"
                type="button"
                class="profile-more-btn"
                @click="profileExpand = !profileExpand"
              >{{ profileExpand ? '收起' : '展开全部' }}</button>
            </section>

            <section v-if="profile.businessScope" class="profile-card">
              <h3 class="profile-section-title">经营范围</h3>
              <p class="profile-intro">{{ profile.businessScope }}</p>
            </section>
          </template>
        </div>
      </el-tab-pane>

      <el-tab-pane label="财务摘要" name="abstract" lazy>
        <div v-loading="fundLoading">
          <p class="fund-note">{{ fund?.note || '加载中…' }}</p>
          <div class="meta fund-kpi" v-if="fund?.latestAbstract">
            <div><label>报告期</label><span>{{ fund.latestAbstract.reportDate || '-' }}</span></div>
            <div><label>净利润</label><b>{{ fmtMoney(fund.latestAbstract.netProfit) }}</b></div>
            <div><label>净利润同比</label><b :class="Number(fund.latestAbstract.netProfitYoy) >= 0 ? 'up' : 'down'">{{ fmtPct(fund.latestAbstract.netProfitYoy) }}</b></div>
            <div><label>扣非净利润</label><b>{{ fmtMoney(fund.latestAbstract.netProfitExcl) }}</b></div>
            <div><label>营业总收入</label><b>{{ fmtMoney(fund.latestAbstract.revenue) }}</b></div>
            <div><label>营收同比</label><b :class="Number(fund.latestAbstract.revenueYoy) >= 0 ? 'up' : 'down'">{{ fmtPct(fund.latestAbstract.revenueYoy) }}</b></div>
            <div><label>EPS</label><span>{{ fmtNum(fund.latestAbstract.epsBasic, 4) }}</span></div>
            <div><label>BPS</label><span>{{ fmtNum(fund.latestAbstract.bps, 4) }}</span></div>
            <div><label>ROE</label><span>{{ fmtPct(fund.latestAbstract.roe) }}</span></div>
            <div><label>净利率</label><span>{{ fmtPct(fund.latestAbstract.netMargin) }}</span></div>
            <div><label>资产负债率</label><span>{{ fmtPct(fund.latestAbstract.debtRatio) }}</span></div>
            <div><label>流动/速动</label><span>{{ fmtNum(fund.latestAbstract.currentRatio) }} / {{ fmtNum(fund.latestAbstract.quickRatio) }}</span></div>
          </div>
          <el-table v-if="fund?.abstracts?.length" :data="fund.abstracts" size="small" stripe height="480" class="fund-table">
            <el-table-column prop="reportDate" label="报告期" width="110" fixed />
            <el-table-column label="净利润" min-width="110"><template #default="{ row }">{{ fmtMoney(row.netProfit) }}</template></el-table-column>
            <el-table-column label="净利同比" width="100"><template #default="{ row }">{{ fmtPct(row.netProfitYoy) }}</template></el-table-column>
            <el-table-column label="营收" min-width="110"><template #default="{ row }">{{ fmtMoney(row.revenue) }}</template></el-table-column>
            <el-table-column label="营收同比" width="100"><template #default="{ row }">{{ fmtPct(row.revenueYoy) }}</template></el-table-column>
            <el-table-column label="EPS" width="90"><template #default="{ row }">{{ fmtNum(row.epsBasic, 4) }}</template></el-table-column>
            <el-table-column label="ROE" width="90"><template #default="{ row }">{{ fmtPct(row.roe) }}</template></el-table-column>
            <el-table-column label="净利率" width="90"><template #default="{ row }">{{ fmtPct(row.netMargin) }}</template></el-table-column>
            <el-table-column label="负债率" width="90"><template #default="{ row }">{{ fmtPct(row.debtRatio) }}</template></el-table-column>
          </el-table>
          <el-empty v-else description="暂无财务摘要，请先同步基本面" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="分析指标" name="indicator" lazy>
        <div v-loading="fundLoading">
          <div class="meta fund-kpi" v-if="fund?.latestIndicator">
            <div><label>报告期</label><span>{{ fund.latestIndicator.reportDate || '-' }}</span></div>
            <div><label>EPS</label><span>{{ fmtNum(fund.latestIndicator.eps, 4) }}</span></div>
            <div><label>扣非EPS</label><span>{{ fmtNum(fund.latestIndicator.epsExcl, 4) }}</span></div>
            <div><label>BPS</label><span>{{ fmtNum(fund.latestIndicator.bps, 4) }}</span></div>
            <div><label>OCFPS</label><span>{{ fmtNum(fund.latestIndicator.ocfps, 4) }}</span></div>
            <div><label>ROE</label><span>{{ fmtPct(fund.latestIndicator.roe) }}</span></div>
            <div><label>ROA</label><span>{{ fmtPct(fund.latestIndicator.roa) }}</span></div>
            <div><label>毛利率</label><span>{{ fmtPct(fund.latestIndicator.grossMargin) }}</span></div>
            <div><label>净利率</label><span>{{ fmtPct(fund.latestIndicator.netMargin) }}</span></div>
            <div><label>营业利润率</label><span>{{ fmtPct(fund.latestIndicator.operateMargin) }}</span></div>
            <div><label>资产负债率</label><span>{{ fmtPct(fund.latestIndicator.debtRatio) }}</span></div>
            <div><label>流动/速动</label><span>{{ fmtNum(fund.latestIndicator.currentRatio) }} / {{ fmtNum(fund.latestIndicator.quickRatio) }}</span></div>
          </div>
          <el-table v-if="fund?.indicators?.length" :data="fund.indicators" size="small" stripe height="480" class="fund-table">
            <el-table-column prop="reportDate" label="报告期" width="110" fixed />
            <el-table-column label="EPS" width="90"><template #default="{ row }">{{ fmtNum(row.eps, 4) }}</template></el-table-column>
            <el-table-column label="加权EPS" width="90"><template #default="{ row }">{{ fmtNum(row.epsWeighted, 4) }}</template></el-table-column>
            <el-table-column label="扣非EPS" width="90"><template #default="{ row }">{{ fmtNum(row.epsExcl, 4) }}</template></el-table-column>
            <el-table-column label="BPS" width="90"><template #default="{ row }">{{ fmtNum(row.bps, 4) }}</template></el-table-column>
            <el-table-column label="OCFPS" width="90"><template #default="{ row }">{{ fmtNum(row.ocfps, 4) }}</template></el-table-column>
            <el-table-column label="ROE" width="90"><template #default="{ row }">{{ fmtPct(row.roe) }}</template></el-table-column>
            <el-table-column label="ROA" width="90"><template #default="{ row }">{{ fmtPct(row.roa) }}</template></el-table-column>
            <el-table-column label="毛利率" width="90"><template #default="{ row }">{{ fmtPct(row.grossMargin) }}</template></el-table-column>
            <el-table-column label="净利率" width="90"><template #default="{ row }">{{ fmtPct(row.netMargin) }}</template></el-table-column>
            <el-table-column label="负债率" width="90"><template #default="{ row }">{{ fmtPct(row.debtRatio) }}</template></el-table-column>
            <el-table-column label="流动比率" width="90"><template #default="{ row }">{{ fmtNum(row.currentRatio) }}</template></el-table-column>
            <el-table-column label="速动比率" width="90"><template #default="{ row }">{{ fmtNum(row.quickRatio) }}</template></el-table-column>
          </el-table>
          <el-empty v-else description="暂无分析指标，请先同步基本面" />
        </div>
      </el-tab-pane>

      <el-tab-pane
        v-for="sheetKey in ['profitSheet', 'balanceSheet', 'cashflowSheet']"
        :key="sheetKey"
        :label="fund?.[sheetKey]?.statementName || ({ profitSheet: '利润表', balanceSheet: '资产负债表', cashflowSheet: '现金流量表' }[sheetKey])"
        :name="sheetKey"
        lazy
      >
        <div v-loading="fundLoading" class="sheet-wrap">
          <el-table
            v-if="fund?.[sheetKey]?.rows?.length"
            :data="fund[sheetKey].rows"
            size="small"
            stripe
            height="560"
            class="fund-table"
          >
            <el-table-column prop="itemName" label="科目" min-width="180" fixed />
            <el-table-column
              v-for="(p, idx) in fund[sheetKey].periods"
              :key="p + '-' + idx"
              :label="String(p)"
              min-width="120"
              align="right"
            >
              <template #default="{ row }">{{ sheetCell(row, idx) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无该报表数据，请先同步基本面" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.profile-wrap {
  padding-bottom: 24px;
}

.profile-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.profile-card {
  background: var(--glass);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  padding: 16px 18px 8px;
  margin-bottom: 12px;
  box-shadow: var(--shadow-soft);
}

.profile-section-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.profile-name-block {
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.profile-org {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin-bottom: 8px;
}

.profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.profile-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(0, 113, 227, 0.1);
  color: var(--accent);
  font-size: 12px;
}

.profile-highlight {
  margin: 0;
  font-size: 12px;
  color: #c27803;
  line-height: 1.5;
}

.profile-highlight .star {
  margin-right: 2px;
}

.profile-kv {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;
}

.profile-kv .kv {
  display: grid;
  grid-template-columns: 108px 1fr;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
  align-items: start;
}

.profile-kv .kv.full {
  grid-column: 1 / -1;
}

.profile-kv label {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
}

.profile-kv span {
  color: var(--ink);
  line-height: 1.55;
  word-break: break-word;
}

.profile-kv em {
  font-style: normal;
  color: var(--slate);
  margin-left: 4px;
}

.biz-rows {
  display: flex;
  flex-direction: column;
}

.biz-row {
  display: grid;
  grid-template-columns: 108px 1fr;
  gap: 8px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
  align-items: start;
}

.biz-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.biz-row:first-child {
  padding-top: 0;
}

.biz-row label {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
}

.biz-content {
  color: var(--ink);
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}

.biz-content em {
  font-style: normal;
  color: var(--slate);
  margin-left: 2px;
}

.revenue-item {
  margin-bottom: 4px;
}

.revenue-date {
  display: block;
  margin-top: 8px;
  font-size: 11px;
  color: var(--muted);
}

.concept-box {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.concept-chip {
  border: 0;
  background: rgba(0, 113, 227, 0.08);
  color: var(--accent);
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 6px;
  cursor: default;
}

.concept-more,
.profile-more-btn {
  border: 0;
  background: none;
  color: var(--accent);
  font-size: 12px;
  cursor: pointer;
  padding: 0 4px;
}

.profile-intro {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-soft);
  white-space: pre-wrap;
}

.profile-more-btn {
  margin-bottom: 10px;
}

@media (max-width: 720px) {
  .profile-kv {
    grid-template-columns: 1fr;
  }
}

.header .code {
  color: var(--muted);
  font-size: 18px;
  font-weight: 500;
  margin-left: 8px;
  letter-spacing: -0.02em;
}

.meta-panel {
  margin-bottom: 12px;
}

.meta-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass);
  box-shadow: var(--shadow-soft);
}

.meta-bar-main {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 14px;
  min-width: 0;
  font-variant-numeric: tabular-nums;
}

.meta-price {
  font-size: 22px;
  font-weight: 750;
  line-height: 1.1;
}

.meta-pct {
  font-size: 14px;
  font-weight: 700;
}

.meta-chip {
  font-size: 12px;
  color: var(--ink-soft);
  white-space: nowrap;
}

.meta-toggle {
  flex: 0 0 auto;
  border: 1px solid var(--glass-border);
  background: rgba(255, 255, 255, 0.65);
  color: var(--ink-soft);
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}

.meta-toggle:hover {
  color: var(--ink);
  border-color: rgba(0, 0, 0, 0.16);
}

.meta {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-top: 10px;
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

@media (max-width: 720px) {
  .meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .meta-bar {
    align-items: flex-start;
    flex-direction: column;
  }
}

.hint {
  margin-bottom: 12px;
}

.empty-bars {
  margin: 24px 0;
}

.chart-toolbar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0 0 10px;
}

.chart-primary-controls,
.chart-advanced-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 16px;
}

.chart-primary-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.chart-params-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 0;
  background: transparent;
  color: var(--accent);
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.chart-params-toggle svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
  transition: transform 160ms ease;
}

.chart-params-toggle[aria-expanded='true'] svg {
  transform: rotate(180deg);
}

.ma-checks {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 2px 10px;
}

.ma-checks :deep(.el-checkbox__label) {
  color: var(--ma-color, #3a3a3c);
  font-weight: 600;
  font-size: 12px;
  padding-left: 6px;
}

.ctrl-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: #3a3a3c;
  cursor: pointer;
}

.td-mode {
  margin-left: -4px;
}

.reset-view {
  margin-left: 0;
  font-weight: 600;
}

.period-meta {
  margin-left: auto;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  font-variant-numeric: tabular-nums;
}

.intraday-asof {
  font-size: 11px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}

.chart-price-levels {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.chart-price-levels span {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 9px;
  border: 1px solid currentColor;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.chart-price-levels .support {
  color: #1f8f48;
  background: rgba(52, 199, 89, 0.07);
}

.chart-price-levels .resistance {
  color: #d92d20;
  background: rgba(255, 59, 48, 0.07);
}

.chart-price-levels small {
  margin-left: auto;
  color: var(--muted);
  font-size: 11px;
}

.chart-alert {
  margin: 0;
}

.chart-alert :deep(.el-alert__title) {
  font-size: 12px;
  line-height: 1.4;
}

.chart-hint {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.01em;
}

.macd-tip {
  margin: 0;
  color: #1d1d1f;
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.macd-tip--sub {
  font-weight: 500;
  color: #4b5563;
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

.structure-insufficient {
  margin-top: 14px;
}

.tabs {
  margin-top: 4px;
}

.fund-note {
  color: var(--muted);
  font-size: 13px;
  margin: 0 0 12px;
}

.fund-kpi {
  margin-bottom: 14px;
}

.fund-table {
  width: 100%;
  background: transparent;
}

.sheet-wrap {
  min-height: 200px;
}

@media (max-width: 900px) {
  .meta {
    grid-template-columns: 1fr 1fr;
  }

  .chart {
    height: 560px;
  }
}

@media (max-width: 820px) {
  .header {
    gap: 8px;
    margin-bottom: 12px;
  }

  .stock-heading {
    width: 100%;
    min-width: 0;
  }

  .header .eyebrow {
    margin-bottom: 2px;
    font-size: 11px;
  }

  .header h1,
  :global(.shell.dense) .header h1 {
    display: flex;
    align-items: baseline;
    gap: 6px;
    font-size: 24px;
    line-height: 1.2;
  }

  .stock-name {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .header .code {
    flex: 0 0 auto;
    margin-left: 0;
    font-size: 14px;
  }

  .header .stock-note,
  :global(.shell.dense) .header .stock-note {
    display: block;
    max-width: 100%;
    margin-top: 4px;
    overflow: hidden;
    color: var(--muted);
    font-size: 12px;
    line-height: 1.5;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .header .actions {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 8px;
    width: 100%;
  }

  .header .actions :deep(.el-button) {
    width: 100%;
    min-width: 0;
    min-height: 44px;
    margin: 0;
    padding: 0 4px;
    border-radius: 8px;
    font-size: 13px;
  }

  .header .actions :deep(.el-button:not(.el-button--primary)) {
    border-color: var(--line);
    background: rgba(255, 255, 255, 0.62);
    color: var(--ink-soft);
  }

  .header .actions :deep(.el-button:not(.el-button--primary):active) {
    background: rgba(0, 113, 227, 0.08);
    color: var(--accent);
  }

  .chart-toolbar {
    gap: 6px;
    margin-bottom: 8px;
  }

  .chart-primary-controls {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: 6px;
  }

  .period-mode {
    display: flex;
    width: 100%;
  }

  .period-mode :deep(.el-radio-button) {
    min-width: 0;
    min-height: 44px;
    display: flex;
    align-items: center;
    flex: 1;
  }

  .period-mode :deep(.el-radio-button__inner),
  .td-mode :deep(.el-radio-button__inner) {
    height: 36px;
    min-height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    padding: 0 12px;
    font-size: 13px;
  }

  .chart-primary-actions {
    justify-content: flex-end;
  }

  .chart-primary-actions :deep(.el-button),
  .chart-params-toggle {
    min-height: 44px;
    margin: 0;
    padding: 0 10px;
    font-size: 13px;
  }

  .chart-advanced-controls {
    gap: 4px 12px;
    padding: 8px 10px;
    border: 1px solid var(--line);
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.58);
  }

  .ma-checks {
    width: 100%;
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 0;
  }

  .ma-checks :deep(.el-checkbox) {
    min-width: 0;
    min-height: 44px;
    margin-right: 0;
  }

  .ma-checks :deep(.el-checkbox__label) {
    padding-left: 4px;
    font-size: 11px;
  }

  .ctrl-label {
    min-height: 44px;
  }

  .td-mode {
    display: flex;
    min-width: 152px;
    margin-left: auto;
  }

  .td-mode :deep(.el-radio-button) {
    min-height: 44px;
    display: flex;
    align-items: center;
    flex: 1;
  }

  .period-meta {
    margin-left: 0;
    font-size: 11px;
  }

  .chart-hint {
    display: none;
  }

  .macd-tip {
    font-size: 11px;
    line-height: 1.4;
  }

  .chart {
    height: 520px;
    border-radius: 8px;
    touch-action: manipulation;
    user-select: none;
    -webkit-user-select: none;
  }
}

@media (max-width: 820px) and (orientation: landscape) {
  .header .actions {
    grid-template-columns: repeat(8, minmax(0, 1fr));
  }

  .header .actions :deep(.el-button) {
    font-size: 12px;
  }
}
</style>

<!-- tooltip 挂到 body，需非 scoped -->
<style>
.kline-tip {
  pointer-events: none !important;
  z-index: 40 !important;
}

.kline-tip__card {
  width: 280px;
  box-sizing: border-box;
  padding: 12px 14px 10px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.32);
  border: 1px solid rgba(255, 255, 255, 0.48);
  box-shadow:
    0 12px 32px rgba(15, 23, 42, 0.07),
    inset 0 1px 0 rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(20px) saturate(1.4);
  -webkit-backdrop-filter: blur(20px) saturate(1.4);
  color: #1d1d1f;
  font-family: inherit;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.01em;
  overflow: visible;
}

.kline-tip__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px 8px;
  margin-bottom: 8px;
}

.kline-tip__date {
  font-size: 12px;
  color: #86868b;
  font-weight: 500;
}

.kline-tip__badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.kline-tip__badge--up {
  color: #c62828;
  background: rgba(239, 83, 80, 0.14);
}

.kline-tip__badge--down {
  color: #0f766e;
  background: rgba(38, 166, 154, 0.16);
}

.kline-tip__badge--soft {
  color: #6b7280;
  background: rgba(0, 0, 0, 0.05);
}

.kline-tip__price-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin: 0 0 8px;
  min-width: 0;
}

.kline-tip__price {
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.03em;
}

.kline-tip__day {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  flex: 0 0 auto;
  font-size: 13px;
  line-height: 1.2;
  white-space: nowrap;
}

.kline-tip__day em {
  font-style: normal;
  font-weight: 500;
  color: #86868b;
}

.kline-tip__day b {
  font-weight: 700;
}

.kline-tip__metrics {
  display: flex;
  flex-wrap: nowrap;
  align-items: baseline;
  gap: 8px;
  margin: 0 0 10px;
  min-width: 0;
  white-space: nowrap;
}

.kline-tip__metric {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.25;
  color: #3a3a3c;
}

.kline-tip__metric em {
  font-style: normal;
  color: #86868b;
  font-weight: 500;
  margin-right: 4px;
}

.kline-tip__metric b {
  font-weight: 700;
}

.kline-tip__periods {
  font-size: 12px;
  font-weight: 500;
  color: #86868b;
}

.kline-tip__ohlc {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 16px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #3a3a3c;
}

.kline-tip__ohlc > span,
.kline-tip__vol > span {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
  white-space: nowrap;
}

.kline-tip__ohlc b,
.kline-tip__vol b {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.kline-tip__vol {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 16px;
  padding-top: 8px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  color: #6b7280;
  margin-bottom: 8px;
  font-size: 12px;
}

.kline-tip__ohlc em,
.kline-tip__vol em {
  font-style: normal;
  color: #a1a1a6;
  font-size: 11px;
  flex: 0 0 auto;
}

.kline-tip__row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.kline-tip__chip {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 3px 8px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.35);
  font-size: 11px;
  color: #3a3a3c;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.kline-tip__chip i {
  font-style: normal;
  font-size: 10px;
  font-weight: 700;
  flex: 0 0 auto;
}

.kline-tip__chip b {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 820px) {
  .kline-tip__card {
    width: min(280px, calc(100vw - 32px));
    max-height: min(420px, calc(100dvh - 32px));
    overflow-y: auto;
    padding: 10px 12px 9px;
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.96);
    border-color: rgba(0, 0, 0, 0.08);
    box-shadow: 0 10px 28px rgba(15, 23, 42, 0.16);
    backdrop-filter: blur(12px) saturate(1.2);
    -webkit-backdrop-filter: blur(12px) saturate(1.2);
  }
}
</style>
