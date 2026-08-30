<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  Refresh,
  RefreshLeft,
  Star,
  TrendCharts,
  Wallet,
  ZoomIn,
  ZoomOut,
} from '@element-plus/icons-vue'
import {
  fetchCompanyProfile,
  fetchStockDetail,
  fetchStockFundamental,
  fetchStockIntraday,
  refreshCompanyProfile,
  syncStockQuote,
} from '../api/stock'
import { syncBarsFast } from '../api/bars'
import { saveObserve } from '../api/observe'
import { fetchTradeMarkers } from '../api/trade'
import {
  aggregateBars,
  defaultVisibleStart,
  nextKlineZoomRange,
  spaceChartSignals,
  tdSequential,
  visibleBarCount,
} from '../utils/kline'
import { buildTradeMarkerSeries } from '../utils/tradeMarkers'
import { analyzePriceStructure, buildPriceLevelMarkLines } from '../utils/priceStructure'
import { stockSyncSummary, synchronizeStockData } from '../utils/stockSync'
import { bindChartWheelScroll, bindLongPress, resolveMobileTooltipPosition } from '../utils/chartLongPress'
import { staleDataTime } from '../utils/dataFreshness.js'
import StockAnalysisPanel from '../components/StockAnalysisPanel.vue'
import ChipDistributionPanel from '../components/ChipDistributionPanel.vue'
import FactorCenterView from './FactorCenterView.vue'
import ValuationView from './ValuationView.vue'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const syncingBars = ref(false)
const syncProgress = ref({ bars: 'pending', quote: 'pending', detail: 'pending' })
const syncResult = ref(null)
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
const tradeRecords = ref([])
const needSyncBars = ref(false)
const barCount = ref(0)
const rs20 = ref(null)
const rs60 = ref(null)
const volumeRatio = ref(null)
const chartRef = ref(null)
const STOCK_TAB_NAMES = [
  'analysis',
  'valuation',
  'factors',
  'profile',
  'abstract',
  'indicator',
  'profitSheet',
  'balanceSheet',
  'cashflowSheet',
]
const LEGACY_MARKET_TABS = ['summary', 'chart']
const activeTab = ref(STOCK_TAB_NAMES.includes(route.query.tab) ? route.query.tab : 'analysis')
const fund = ref(null)
const profile = ref(null)
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
const BAR_LIMIT = 500
const MIN_VISIBLE_BARS = 12
const CHART_PREF_KEY = 'apex.stock.chartPrefs'
const metaExpanded = ref(false)

const syncButtonLabel = computed(() => {
  if (!syncingBars.value) return '同步行情'
  if (syncProgress.value.detail === 'running') return '刷新页面'
  const completed = ['bars', 'quote'].filter((key) =>
    ['success', 'error'].includes(syncProgress.value[key]),
  ).length
  return `同步中 ${completed}/2`
})

function syncStateLabel(state) {
  if (state === 'running') return '同步中'
  if (state === 'success') return '已完成'
  if (state === 'error') return '失败'
  return '等待中'
}

function toggleMetaExpanded() {
  metaExpanded.value = !metaExpanded.value
}
const MA_META = [
  { name: 'MA5', color: '#1d1d1f' },
  { name: 'MA10', color: '#f59e0b' },
  { name: 'MA20', color: '#7c3aed' },
  { name: 'MA60', color: '#5c6bc0' },
]
const DISPLAY_MA_NAMES = ['MA5', 'MA10', 'MA20']
let chart
let syncingFromLegend = false
let resetZoomNext = true
const savedZoom = ref({ start: 0, end: 100 })
let intradayPollTimer = null
/** 当前图数据，供可视区高低点随缩放更新 */
let chartPayload = null
let compactPriceLabelsMode = null
let chartPressCleanup = null
let chartWheelCleanup = null

const isIntraday = computed(() => klinePeriod.value === 'intraday')
const intradayDataTime = computed(() => {
  if (!isIntraday.value || !intraday.value) return ''
  return staleDataTime({
    tradeDate: intraday.value.tradeDate,
    updatedAt: intradayAsOf.value,
    intraday: true,
  })
})
const intradayPoints = computed(() => intraday.value?.points || [])
const showChartShell = computed(() => bars.value.length > 0 || isIntraday.value)
const chartBars = computed(() =>
  isIntraday.value ? [] : aggregateBars(bars.value, klinePeriod.value),
)
const visibleZoomBarCount = computed(() => visibleBarCount(
  chartBars.value.length,
  savedZoom.value.start,
  savedZoom.value.end,
))
const canZoomIn = computed(() => (
  visibleZoomBarCount.value > Math.min(chartBars.value.length, MIN_VISIBLE_BARS)
))
const canZoomOut = computed(() => visibleZoomBarCount.value < chartBars.value.length)
const priceStructure = computed(() =>
  analyzePriceStructure(bars.value, basic.value?.latestPrice),
)
const latestDailyBar = computed(() => bars.value.at(-1) || null)
const dailyDataTime = computed(() => {
  if (isIntraday.value) return ''
  return String(basic.value?.quoteTime || '').replace('T', ' ').slice(0, 19)
})
const quoteDirectionClass = computed(() => {
  if (basic.value?.pctChg == null) return ''
  const percentage = Number(basic.value.pctChg)
  if (!Number.isFinite(percentage) || percentage === 0) return ''
  return percentage > 0 ? 'up' : 'down'
})
const previousClose = computed(() => {
  if (basic.value?.latestPrice == null || basic.value?.pctChg == null) {
    if (bars.value.length > 1) return Number(bars.value.at(-2)?.closePrice)
    return null
  }
  const currentPrice = Number(basic.value?.latestPrice)
  const percentage = Number(basic.value?.pctChg)
  if (Number.isFinite(currentPrice) && Number.isFinite(percentage) && percentage !== -100) {
    return currentPrice / (1 + percentage / 100)
  }
  if (bars.value.length > 1) return Number(bars.value.at(-2)?.closePrice)
  return null
})
const priceChange = computed(() => {
  if (basic.value?.latestPrice == null || previousClose.value == null) return null
  const currentPrice = Number(basic.value?.latestPrice)
  const lastClose = Number(previousClose.value)
  if (!Number.isFinite(currentPrice) || !Number.isFinite(lastClose)) return null
  return currentPrice - lastClose
})
/** 随 K 线周期：天 / 周 / 月 */
const periodUnit = computed(() =>
  klinePeriod.value === 'week' ? '周' : klinePeriod.value === 'month' ? '月' : '天',
)
function maDisplayName(name) {
  const n = String(name).replace(/^MA/i, '')
  return `MA${n}${periodUnit.value}`
}

const chartLegendItems = computed(() => [
  ...MA_META.filter((item) => DISPLAY_MA_NAMES.includes(item.name)).map((item) => ({
    name: maDisplayName(item.name),
    label: `${String(item.name).replace(/^MA/i, '')}${klinePeriod.value === 'day' ? '日' : periodUnit.value}`,
    color: item.color,
    maName: item.name,
  })),
])

function isChartLegendSelected(item) {
  return selectedMas.value.includes(item.maName)
}

function toggleChartLegend(item) {
  if (selectedMas.value.includes(item.maName)) {
    selectedMas.value = selectedMas.value.filter((name) => name !== item.maName)
    return
  }
  selectedMas.value = DISPLAY_MA_NAMES.filter((name) => (
    name === item.maName || selectedMas.value.includes(name)
  ))
}

function unbindChartPress() {
  if (chartPressCleanup) {
    chartPressCleanup()
    chartPressCleanup = null
  }
}

function unbindChartWheel() {
  if (chartWheelCleanup) {
    chartWheelCleanup()
    chartWheelCleanup = null
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
      const next = prefs.selectedMas.filter((name) => DISPLAY_MA_NAMES.includes(name))
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
  if (!Number.isNaN(start) && !Number.isNaN(end)) savedZoom.value = { start, end }
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
  const pad = span > 0 ? span * 0.08 : Math.max(Math.abs(max) * 0.02, 0.01)
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
  const markPoint = buildVisibleExtremeMarkPoint(
    dates,
    highs,
    lows,
    savedZoom.value.start,
    savedZoom.value.end,
  )
  const extent = calcVisiblePriceExtent(
    highs,
    lows,
    savedZoom.value.start,
    savedZoom.value.end,
  )
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
    if (isIntraday.value) {
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
  if (!isIntraday.value || document.hidden) return
  intradayPollTimer = setInterval(() => {
    if (!document.hidden && isIntraday.value) loadIntraday(true)
  }, 60000)
}

function stopIntradayPoll() {
  if (intradayPollTimer) {
    clearInterval(intradayPollTimer)
    intradayPollTimer = null
  }
}

function onDocumentVisibilityChange() {
  if (document.hidden) {
    stopIntradayPoll()
    return
  }
  if (isIntraday.value) {
    loadIntraday(true)
    startIntradayPoll()
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
  unbindChartWheel()
  if (!isIntraday.value) chartWheelCleanup = bindChartWheelScroll(chart.getDom())
  return true
}

async function renderIntradayChart() {
  const points = intradayPoints.value
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
    disposeChart()
    return
  }
  renderChart(chartBars.value)
}

function resetChartView() {
  if (isIntraday.value) {
    refreshChart()
    return
  }
  resetZoomNext = true
  savedZoom.value = { start: defaultVisibleStart(chartBars.value.length), end: 100 }
  if (bars.value.length) refreshChart()
}

function zoomChart(direction) {
  if (!chart || isIntraday.value || !chartBars.value.length) return
  captureZoom()
  const nextZoom = nextKlineZoomRange(
    chartBars.value.length,
    savedZoom.value.start,
    savedZoom.value.end,
    direction,
    MIN_VISIBLE_BARS,
  )
  if (nextZoom.start === savedZoom.value.start && nextZoom.end === savedZoom.value.end) return
  savedZoom.value = nextZoom
  chart.dispatchAction({
    type: 'dataZoom',
    start: nextZoom.start,
    end: nextZoom.end,
  })
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
    const nextMas = DISPLAY_MA_NAMES.filter((name) => selected[`${name}${unit}`] !== false)
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
  unbindChartWheel()
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

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

function tradeMarkerTooltip(groups) {
  if (!groups.length) return ''
  const rows = []
  for (const group of groups) {
    for (const record of group.records || []) {
      const owner = record.ownerLabel || record.portfolioName || '交易记录'
      const portfolio = record.ownerLabel && record.portfolioName ? ` · ${record.portfolioName}` : ''
      const quantity = record.quantity == null ? '' : ` · ${Number(record.quantity).toLocaleString('zh-CN')}股`
      const price = record.price == null ? '价格待补' : `¥${fmtNum(record.price, 3)}`
      rows.push(`
        <div class="kline-tip__trade-row">
          <span class="kline-tip__trade-side kline-tip__trade-side--${record.side === 'BUY' ? 'buy' : 'sell'}">${record.side === 'BUY' ? 'B' : 'S'}</span>
          <span>${escapeHtml(owner)}${escapeHtml(portfolio)}</span>
          <b>${price}${quantity}${record.estimated ? ' · 估算' : ''}</b>
        </div>`)
    }
  }
  return `<div class="kline-tip__trades">${rows.join('')}</div>`
}

function buildTradeGuideSeries(side, markers) {
  const buy = side === 'BUY'
  const color = buy ? '#e5484d' : '#1677ff'
  const direction = buy ? 1 : -1
  return {
    id: `user-trade-${side.toLowerCase()}`,
    name: buy ? '买入记录' : '卖出记录',
    type: 'custom',
    coordinateSystem: 'cartesian2d',
    xAxisIndex: 0,
    yAxisIndex: 0,
    data: markers,
    encode: { x: 0, y: 1 },
    z: 30,
    clip: false,
    silent: true,
    legendHoverLink: false,
    tooltip: { show: false },
    renderItem(params, api) {
      const point = api.coord([api.value(0), api.value(1)])
      if (!Number.isFinite(point[0]) || !Number.isFinite(point[1])) return null
      const labelText = String(params.data?.labelText || (buy ? 'B' : 'S'))
      const labelWidth = Math.min(96, Math.max(30, Array.from(labelText).length * 11 + 16))
      const leaderEndY = point[1] + direction * 34
      const labelHeight = 20
      const labelTop = buy ? leaderEndY + 6 : leaderEndY - labelHeight - 6
      const labelLeft = point[0] - labelWidth / 2
      return {
        type: 'group',
        children: [
          {
            type: 'circle',
            shape: { cx: point[0], cy: point[1], r: 2.5 },
            style: { fill: color, stroke: '#fff', lineWidth: 1 },
          },
          {
            type: 'line',
            shape: { x1: point[0], y1: point[1], x2: point[0], y2: leaderEndY },
            style: { stroke: color, lineWidth: 1.25, lineDash: [4, 3] },
          },
          {
            type: 'rect',
            shape: { x: labelLeft, y: labelTop, width: labelWidth, height: labelHeight, r: 4 },
            style: { fill: 'rgba(255,255,255,0.96)', stroke: color, lineWidth: 1 },
          },
          {
            type: 'text',
            style: {
              x: labelLeft + 6,
              y: labelTop + labelHeight / 2,
              text: buy ? 'B' : 'S',
              fill: color,
              font: '700 11px sans-serif',
              textVerticalAlign: 'middle',
            },
          },
          {
            type: 'text',
            style: {
              x: labelLeft + 17,
              y: labelTop + labelHeight / 2,
              text: labelText.slice(1).trim(),
              fill: '#475569',
              font: '600 10px sans-serif',
              textVerticalAlign: 'middle',
            },
          },
        ],
      }
    },
  }
}

async function renderChart(list) {
  if (!list.length) {
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
  const userTradeMarkers = buildTradeMarkerSeries(tradeRecords.value, list)
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
  const markerCrosses = spaceChartSignals(crosses, 5)
  const goldenPoints = []
  const deathPoints = []
  for (let i = 0; i < markerCrosses.length; i++) {
    if (markerCrosses[i] === 'golden') goldenPoints.push([dates[i], dif[i]])
    if (markerCrosses[i] === 'death') deathPoints.push([dates[i], dif[i]])
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
    zoomStart = savedZoom.value.start
    zoomEnd = savedZoom.value.end
  }
  resetZoomNext = false
  savedZoom.value = { start: zoomStart, end: zoomEnd }
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
      show: false,
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
        const markerGroups = [...userTradeMarkers.buy, ...userTradeMarkers.sell]
          .filter((marker) => marker.value?.[0] === date)
        const tradeHtml = tradeMarkerTooltip(markerGroups)

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
  ${tradeHtml}
</div>`
      },
    },
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    grid: [
      // 四个子图保持同宽；移动端隐藏图内价格牌，释放右侧绘图区。
      { left: 56, right: chartGridRight, top: 12, height: '38%' },
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
      {
        type: 'inside',
        xAxisIndex: [0, 1, 2, 3],
        start: zoomStart,
        end: zoomEnd,
        minValueSpan: Math.min(MIN_VISIBLE_BARS, list.length),
        zoomOnMouseWheel: 'ctrl',
        moveOnMouseWheel: false,
      },
      {
        show: true,
        xAxisIndex: [0, 1, 2, 3],
        type: 'slider',
        top: '94%',
        start: zoomStart,
        end: zoomEnd,
        minValueSpan: Math.min(MIN_VISIBLE_BARS, list.length),
      },
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
      buildTradeGuideSeries('BUY', userTradeMarkers.buy),
      buildTradeGuideSeries('SELL', userTradeMarkers.sell),
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
        symbolOffset: [0, -5],
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
        labelLayout: { hideOverlap: true },
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
        symbolOffset: [0, 5],
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
        labelLayout: { hideOverlap: true },
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
    loadTradeRecords()
    await loadFundamental()
    // 静默拉概况：回填东财二级行业到 meta，并同步 stock_basic.industry
    loadProfile(false).then(() => {
      if (profile.value?.industryL2 && basic.value) {
        basic.value = { ...basic.value, industry: profile.value.industryL2 }
      }
    })
    if (refreshQuote) {
      await syncStockQuote(code.value.trim())
      const again = await fetchStockDetail(code.value.trim(), BAR_LIMIT, false)
      applyDetail(again.data)
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadTradeRecords() {
  const requestedCode = code.value.trim()
  if (!requestedCode) return
  try {
    const result = await fetchTradeMarkers(requestedCode)
    if (requestedCode !== code.value.trim()) return
    tradeRecords.value = result?.data || []
    if (!isIntraday.value && bars.value.length) refreshChart()
  } catch (error) {
    if (requestedCode !== code.value.trim()) return
    tradeRecords.value = []
    console.warn('加载交易标记失败', error)
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
  note.value = data.needSyncBars ? data.note || '' : ''
  bars.value = data.bars || []
  needSyncBars.value = !!data.needSyncBars
  barCount.value = data.barCount ?? bars.value.length
  rs20.value = data.rs20VsHs300
  rs60.value = data.rs60VsHs300
  volumeRatio.value = data.volumeRatio
  refreshChart()
}

async function syncStockData() {
  if (!code.value || syncingBars.value) return
  syncingBars.value = true
  syncResult.value = null
  try {
    const pure = code.value.trim()
    const result = await synchronizeStockData({
      code: pure,
      syncBars: syncBarsFast,
      syncQuote: syncStockQuote,
      fetchDetail: async (stockCode) => {
        const detail = await fetchStockDetail(stockCode, BAR_LIMIT, false)
        if (stockCode === code.value.trim()) {
          applyDetail(detail.data)
        }
        return detail
      },
      onProgress: (progress) => {
        syncProgress.value = progress
      },
    })
    const summary = stockSyncSummary(result)
    syncResult.value = summary
    ElMessage[summary.type](summary.text)
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
    refreshChart()
    return
  }
  if (isIntraday.value || !bars.value.length || !chartRef.value) return
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
      tradeRecords.value = []
      intraday.value = null
      intradayAsOf.value = ''
      load(false)
      if (activeTab.value === 'profile') loadProfile(false)
      if (isIntraday.value) {
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
    if (LEGACY_MARKET_TABS.includes(tab)) {
      activeTab.value = 'analysis'
      return
    }
    if (STOCK_TAB_NAMES.includes(tab)) activeTab.value = tab
  },
)

watch(klinePeriod, () => {
  resetZoomNext = true
  saveChartPrefs()
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
    if (!isIntraday.value && bars.value.length) refreshChart()
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
  document.addEventListener('visibilitychange', onDocumentVisibilityChange)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  document.removeEventListener('visibilitychange', onDocumentVisibilityChange)
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
  if (name === 'profile' && !profile.value) {
    loadProfile(false)
  }
  // 今日雷达 Tab 由 StockAnalysisPanel 自行按 code 加载
}

function dash(v) {
  return v == null || v === '' ? '-' : v
}
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="header">
      <div class="stock-heading">
        <p class="eyebrow">Stock</p>
        <h1>
          <StockIdentity
            :security="basic || { code }"
            :name="basic?.name || '股票详情'"
            :code="basic?.code || code"
            prominent
            :show-code="false"
          />
        </h1>
        <span class="stock-heading-code">{{ basic?.code || code }}</span>
        <p class="stock-note">{{ note || '今日雷达 · 个股消息 · 估值 · 行情' }}</p>
      </div>
      <div class="actions">
        <div class="stock-action-toolbar">
          <el-tooltip content="同步行情" placement="bottom">
            <el-button
              class="stock-icon-action sync-action"
              circle
              :loading="syncingBars"
              :disabled="syncingBars"
              :aria-label="syncButtonLabel"
              title="同步行情"
              @click="syncStockData"
            >
              <el-icon v-if="!syncingBars"><Refresh /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="加入观察池" placement="bottom">
            <el-button
              class="stock-icon-action observe-action"
              type="warning"
              plain
              circle
              :loading="observeSaving"
              aria-label="加入观察池"
              title="加入观察池"
              @click="quickAddObserve"
            >
              <el-icon v-if="!observeSaving"><Star /></el-icon>
            </el-button>
          </el-tooltip>
          <el-button class="stock-text-action" plain @click="router.push({ path: '/backtest', query: { code: code.trim() } })">
            <el-icon><TrendCharts /></el-icon><span>历史回测</span>
          </el-button>
          <el-button class="stock-text-action" plain @click="router.push({ path: '/paper', query: { code: code.trim(), side: 'BUY' } })">
            <el-icon><Wallet /></el-icon><span>模拟买</span>
          </el-button>
        </div>
        <div v-if="syncingBars" class="sync-progress" aria-live="polite">
          <span :data-state="syncProgress.bars">日线 {{ syncStateLabel(syncProgress.bars) }}</span>
          <span :data-state="syncProgress.quote">行情 {{ syncStateLabel(syncProgress.quote) }}</span>
        </div>
        <p v-else-if="syncResult" class="sync-result" :data-state="syncResult.type" aria-live="polite">{{ syncResult.text }}</p>
      </div>
    </header>

    <section id="market-overview" class="market-overview">
      <aside class="quote-snapshot" aria-label="个股行情快照">
        <div class="quote-primary">
          <strong :class="quoteDirectionClass">
            {{ fmtNum(basic?.latestPrice) }}
          </strong>
          <div :class="quoteDirectionClass">
            <span>{{ priceChange != null ? (priceChange > 0 ? '+' : '') + fmtNum(priceChange) : '-' }}</span>
            <span>{{ basic?.pctChg != null ? (Number(basic.pctChg) > 0 ? '+' : '') + Number(basic.pctChg).toFixed(2) + '%' : '-' }}</span>
          </div>
        </div>
        <p class="quote-industry">{{ profile?.industryL2 || basic?.industry || basic?.market || '行业待补充' }}</p>
        <div class="quote-metrics">
          <div><label>今开</label><b>{{ fmtNum(latestDailyBar?.openPrice) }}</b></div>
          <div><label>昨收</label><b>{{ fmtNum(previousClose) }}</b></div>
          <div><label>最高</label><b class="up">{{ fmtNum(latestDailyBar?.highPrice) }}</b></div>
          <div><label>最低</label><b class="down">{{ fmtNum(latestDailyBar?.lowPrice) }}</b></div>
          <div><label>成交量</label><b>{{ fmtVol(latestDailyBar?.volume) }}</b></div>
          <div><label>成交额</label><b>{{ fmtVol(latestDailyBar?.amount) }}</b></div>
          <div><label>换手率</label><b>{{ latestDailyBar?.turnoverRate != null ? fmtPct(latestDailyBar.turnoverRate) : '-' }}</b></div>
          <div><label>量比</label><b>{{ volumeRatio ?? '-' }}</b></div>
          <div><label>PE TTM</label><b>{{ basic?.peTtm ?? '-' }}</b></div>
          <div><label>PB</label><b>{{ basic?.pb ?? '-' }}</b></div>
          <div><label>总市值</label><b>{{ fmtMv(basic?.totalMv) }}</b></div>
          <div><label>流通值</label><b>{{ fmtMv(basic?.circMv) }}</b></div>
        </div>
        <button
          type="button"
          class="meta-toggle"
          :class="{ 'is-expanded': metaExpanded }"
          :aria-expanded="metaExpanded"
          aria-controls="stock-meta-details"
          @click="toggleMetaExpanded"
        >
          <span>{{ metaExpanded ? '收起全部指标' : '查看全部指标' }}</span>
          <el-icon aria-hidden="true"><ArrowDown /></el-icon>
        </button>
        <Transition name="quote-meta">
          <div
            id="stock-meta-details"
            v-if="basic && metaExpanded"
            class="quote-meta-details"
            aria-label="扩展行情指标"
          >
            <div><label><TermTip term="pe_dynamic">市盈率（动）</TermTip></label><span>{{ basic.peDynamic ?? '-' }}</span></div>
            <div><label><TermTip term="pe_static">市盈率（静）</TermTip></label><span>{{ basic.peStatic ?? '-' }}</span></div>
            <div><label><TermTip term="rs20">RS20 vs沪深300</TermTip></label><b :class="Number(rs20) >= 0 ? 'up' : 'down'">{{ rs20 != null ? rs20 + 'pp' : '-' }}</b></div>
            <div><label><TermTip term="rs60">RS60 vs沪深300</TermTip></label><b :class="Number(rs60) >= 0 ? 'up' : 'down'">{{ rs60 != null ? rs60 + 'pp' : '-' }}</b></div>
            <div><label>市场</label><span>{{ basic.market || '-' }}</span></div>
            <div><label>上市</label><span>{{ basic.listDate || '-' }}</span></div>
            <div><label>来源</label><span>{{ basic.source || '-' }}</span></div>
            <div><label>本地日线</label><span>{{ barCount }}</span></div>
          </div>
        </Transition>
      </aside>

      <div class="market-chart">
        <el-empty
          v-if="!loading && !bars.length && !isIntraday"
          class="empty-bars"
          description="本地暂无日线，请先同步数据落库；也可先看分时"
        >
          <el-button type="primary" :loading="syncingBars" @click="syncStockData">同步数据</el-button>
          <el-button @click="klinePeriod = 'intraday'">看分时</el-button>
        </el-empty>
        <div
          v-if="showChartShell && (isIntraday || (klinePeriod !== 'day' && bars.length < 120) || (isMobileChart && priceStructure.ready))"
          class="chart-toolbar"
          v-loading="intradayLoading && isIntraday"
        >
          <el-alert
            v-if="!isIntraday && klinePeriod !== 'day' && bars.length < 120"
            class="chart-alert"
            type="info"
            :closable="false"
            show-icon
            :title="`日线仅 ${bars.length} 根，周/月K样本偏少，建议同步更多日线`"
          />
          <div v-if="isMobileChart && !isIntraday && priceStructure.ready" class="chart-price-levels">
            <span v-if="priceStructure.support" class="support">
              支撑 {{ fmtNum(priceStructure.support.price) }}
            </span>
            <span v-if="priceStructure.resistance" class="resistance">
              压力 {{ fmtNum(priceStructure.resistance.price) }}
            </span>
            <small>长按图表查看详情</small>
          </div>
          <div v-if="isIntraday" class="chart-primary-controls">
            <el-radio-group v-model="klinePeriod" size="small" class="period-mode period-mode--toolbar">
              <el-radio-button value="intraday">分时</el-radio-button>
              <el-radio-button value="day">日K</el-radio-button>
              <el-radio-button value="week">周K</el-radio-button>
              <el-radio-button value="month">月K</el-radio-button>
            </el-radio-group>
            <div v-if="isIntraday" class="chart-primary-actions">
              <el-button
                size="small"
                text
                type="primary"
                :loading="intradayLoading"
                @click="loadIntraday"
              >
                刷新分时
              </el-button>
            </div>
            <div v-if="intradayDataTime" class="chart-data-status">
              <span v-if="isIntraday && intradayDataTime" class="intraday-asof">{{ intradayDataTime }}</span>
            </div>
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
          class="chart-stage"
          :class="{ 'has-chart-head': !isIntraday }"
        >
          <div v-if="!isIntraday" class="chart-canvas-head">
            <el-radio-group v-model="klinePeriod" size="small" class="period-mode period-mode--chart">
              <el-radio-button value="intraday">分时</el-radio-button>
              <el-radio-button value="day">日K</el-radio-button>
              <el-radio-button value="week">周K</el-radio-button>
              <el-radio-button value="month">月K</el-radio-button>
            </el-radio-group>
            <div class="chart-legend" aria-label="K线图例">
              <button
                v-for="item in chartLegendItems"
                :key="item.name"
                type="button"
                class="chart-legend-item"
                :class="{ 'is-inactive': !isChartLegendSelected(item) }"
                :aria-pressed="isChartLegendSelected(item)"
                @click="toggleChartLegend(item)"
              >
                <i :style="{ background: item.color }" aria-hidden="true"></i>
                <span>{{ item.label }}</span>
              </button>
              <span class="chart-legend-divider" aria-hidden="true"></span>
              <button
                type="button"
                class="chart-legend-item chart-td-toggle"
                :class="{ 'is-inactive': !showTd9 }"
                :aria-pressed="showTd9"
                @click="showTd9 = !showTd9"
              >
                <i aria-hidden="true">9</i>
                <span>神奇九转</span>
              </button>
              <el-checkbox
                v-if="showTd9"
                size="small"
                class="chart-td-filter"
                :model-value="tdShowMode === 'key'"
                aria-label="神奇九转显示范围"
                @change="tdShowMode = $event ? 'key' : 'all'"
              >仅看 8/9</el-checkbox>
            </div>
            <div v-if="dailyDataTime" class="chart-data-status">
              <span class="daily-data-time">{{ dailyDataTime }}</span>
            </div>
            <div class="chart-zoom-controls chart-zoom-controls--mobile" role="group" aria-label="K线缩放">
              <el-tooltip content="缩小 K 线，显示更多" placement="top">
                <el-button
                  size="small"
                  class="chart-zoom-button"
                  :icon="ZoomOut"
                  aria-label="缩小K线"
                  :disabled="!canZoomOut"
                  @click="zoomChart('out')"
                />
              </el-tooltip>
              <el-tooltip content="放大 K 线，显示更少" placement="top">
                <el-button
                  size="small"
                  class="chart-zoom-button"
                  :icon="ZoomIn"
                  aria-label="放大K线"
                  :disabled="!canZoomIn"
                  @click="zoomChart('in')"
                />
              </el-tooltip>
              <el-tooltip content="还原默认视野" placement="top">
                <el-button
                  size="small"
                  class="chart-zoom-button"
                  :icon="RefreshLeft"
                  aria-label="还原默认视野"
                  @click="resetChartView"
                />
              </el-tooltip>
            </div>
            <div class="chart-zoom-controls chart-zoom-controls--desktop" role="group" aria-label="K线缩放">
              <el-tooltip content="缩小 K 线，显示更多" placement="top">
                <el-button
                  size="small"
                  class="chart-zoom-button"
                  :icon="ZoomOut"
                  aria-label="缩小K线"
                  :disabled="!canZoomOut"
                  @click="zoomChart('out')"
                />
              </el-tooltip>
              <el-tooltip content="放大 K 线，显示更少" placement="top">
                <el-button
                  size="small"
                  class="chart-zoom-button"
                  :icon="ZoomIn"
                  aria-label="放大K线"
                  :disabled="!canZoomIn"
                  @click="zoomChart('in')"
                />
              </el-tooltip>
              <el-tooltip content="还原默认视野" placement="top">
                <el-button
                  size="small"
                  class="chart-zoom-button"
                  :icon="RefreshLeft"
                  aria-label="还原默认视野"
                  @click="resetChartView"
                />
              </el-tooltip>
            </div>
          </div>
          <div ref="chartRef" class="chart" />
        </div>
      </div>
    </section>

    <el-alert
      v-if="!loading && bars.length && needSyncBars"
      class="hint"
      type="warning"
      :closable="false"
      show-icon
      :title="note || `本地仅 ${barCount} 根日线，建议同步补齐后再做指标/回测`"
    />

    <ChipDistributionPanel
      v-if="!isIntraday && priceStructure.ready"
      class="structure-panel"
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

    <el-tabs v-model="activeTab" class="tabs" @tab-change="onTabChange">
      <el-tab-pane label="今日雷达" name="analysis" lazy>
        <StockAnalysisPanel v-if="basic?.code || code" :code="String(basic?.code || code).trim()" />
      </el-tab-pane>
      <el-tab-pane label="估值" name="valuation" lazy>
        <ValuationView
          embedded
          :stock-code="String(basic?.code || code).trim()"
        />
      </el-tab-pane>
      <el-tab-pane label="因子" name="factors" lazy>
        <FactorCenterView
          embedded
          :stock-code="String(basic?.code || code).trim()"
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
          <div class="meta fund-kpi" v-if="fund?.financialQuality">
            <div><label>现金质量期</label><span>{{ fund.financialQuality.reportDate || '-' }}</span></div>
            <div><label><TermTip term="operating_cash_flow">经营性现金流</TermTip></label><b>{{ fmtMoney(fund.financialQuality.operatingCashFlow) }}</b></div>
            <div><label><TermTip term="accounts_receivable">应收账款</TermTip></label><b>{{ fmtMoney(fund.financialQuality.accountsReceivable) }}</b></div>
            <div><label><TermTip term="cash_conversion_ratio">净利润现金含量</TermTip></label><b :class="Number(fund.financialQuality.cashConversionRatio) >= 1 ? 'up' : 'down'">{{ fmtNum(fund.financialQuality.cashConversionRatio) }}</b></div>
            <div><label>资本开支</label><b>{{ fmtMoney(fund.financialQuality.capitalExpenditure) }}</b></div>
            <div><label><TermTip term="free_cash_flow">自由现金流</TermTip></label><b :class="Number(fund.financialQuality.freeCashFlow) >= 0 ? 'up' : 'down'">{{ fmtMoney(fund.financialQuality.freeCashFlow) }}</b></div>
            <div><label><TermTip term="price_to_free_cash_flow">P/FCF</TermTip></label><b>{{ fmtNum(fund.financialQuality.priceToFreeCashFlow) }}</b></div>
          </div>
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
.header .stock-heading {
  display: grid;
  grid-template-columns: max-content max-content;
  grid-template-areas:
    'title module'
    'code code'
    'note note';
  align-items: center;
  column-gap: 8px;
  min-width: 0;
}

.header .stock-heading > h1 {
  grid-area: title;
  min-width: 0;
}

.stock-heading :deep(.stock-identity.is-prominent .stock-identity__name) {
  font-size: var(--page-title-size);
  font-weight: 650;
  line-height: 1.15;
}

.header .stock-heading > .eyebrow {
  grid-area: module;
  align-self: center;
  margin: 0 !important;
  color: var(--accent);
  font-size: 0.72em;
  line-height: 1;
}

.header .stock-heading > .stock-heading-code {
  grid-area: code;
}

.header .stock-heading > .stock-note {
  grid-area: note;
}

.stock-heading-code {
  display: block;
  margin-top: 3px;
  color: var(--accent);
  font-size: 13px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  line-height: 18px;
}

.header .actions {
  display: grid;
  justify-items: end;
  width: auto;
  max-width: 100%;
  gap: 4px;
}

.stock-action-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  min-height: 38px;
}

.stock-action-toolbar :deep(.el-button) {
  min-width: 0;
  min-height: 38px;
  margin: 0;
  border-radius: 7px;
  white-space: nowrap;
}

.stock-action-toolbar :deep(.stock-icon-action) {
  width: 38px;
  min-width: 38px;
  padding: 0;
  border-radius: 50%;
}

.stock-action-toolbar :deep(.sync-action) {
  color: var(--ink-soft);
  border-color: var(--line);
  background: #fff;
}

.stock-action-toolbar :deep(.sync-action:hover:not(:disabled)) {
  color: var(--accent);
  border-color: rgba(0, 113, 227, 0.35);
  background: rgba(0, 113, 227, 0.06);
}

.stock-action-toolbar :deep(.stock-text-action) {
  padding: 0 12px;
  color: var(--ink-soft);
}

.stock-action-toolbar :deep(.observe-action) {
  color: #a16600;
}

.sync-progress,
.sync-result {
  display: flex;
  align-items: flex-start;
  width: 100%;
  min-height: 15px;
  margin: 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 15px;
}

.sync-progress {
  justify-content: space-between;
  gap: 6px;
}

.sync-progress span {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  white-space: nowrap;
}

.sync-progress span::before {
  width: 6px;
  height: 6px;
  margin-right: 4px;
  border-radius: 50%;
  background: #8e99a8;
  content: '';
}

.sync-progress span[data-state='running']::before {
  background: var(--accent);
  box-shadow: 0 0 0 3px rgba(0, 113, 227, 0.1);
}

.sync-progress span[data-state='success']::before {
  background: #218052;
}

.sync-progress span[data-state='error']::before {
  background: #c2413b;
}

.sync-result {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sync-result[data-state='success'] {
  color: #218052;
}

.sync-result[data-state='warning'] {
  color: #a16600;
}

.sync-result[data-state='error'] {
  color: #c2413b;
}

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

.market-overview {
  display: grid;
  grid-template-columns: minmax(168px, 0.24fr) minmax(0, 1fr);
  gap: 0;
  margin-bottom: 14px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.58);
}

.quote-snapshot {
  min-width: 0;
  padding: 18px 18px 16px 4px;
  border-right: 1px solid var(--line);
  font-variant-numeric: tabular-nums;
}

.quote-primary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 12px;
  min-width: 0;
}

.quote-primary > strong {
  min-width: 0;
  font-size: 34px;
  font-weight: 760;
  line-height: 1.05;
}

.quote-primary > div {
  display: grid;
  align-content: start;
  justify-items: end;
  flex: 0 0 auto;
  min-width: 72px;
  padding-top: 3px;
  gap: 3px;
  text-align: right;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.2;
}

.quote-industry {
  margin: 8px 0 14px;
  overflow: hidden;
  color: var(--muted);
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quote-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid var(--line);
}

.quote-metrics > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  min-width: 0;
  min-height: 34px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
  font-size: 11px;
}

.quote-metrics > div:nth-child(odd) {
  padding-right: 9px;
}

.quote-metrics > div:nth-child(even) {
  padding-left: 9px;
  border-left: 1px solid rgba(15, 23, 42, 0.06);
}

.quote-metrics label {
  flex: 0 0 auto;
  color: var(--muted);
}

.quote-metrics b {
  min-width: 0;
  overflow: hidden;
  color: var(--ink);
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quote-snapshot .meta-toggle {
  width: 100%;
  min-height: 36px;
  margin-top: 12px;
  border-radius: 6px;
}

.market-chart {
  min-width: 0;
  padding: 12px 0 12px 18px;
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
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid var(--glass-border);
  background: rgba(255, 255, 255, 0.65);
  color: var(--ink-soft);
  border-radius: 6px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}

.meta-toggle .el-icon {
  transition: transform 160ms ease;
}

.meta-toggle:hover,
.meta-toggle:focus-visible {
  color: var(--ink);
  border-color: rgba(0, 0, 0, 0.16);
}

.meta-toggle:focus-visible {
  outline: 2px solid rgba(0, 113, 227, 0.24);
  outline-offset: 2px;
}

.meta-toggle.is-expanded {
  border-color: rgba(0, 113, 227, 0.24);
  background: rgba(0, 113, 227, 0.07);
  color: var(--accent);
}

.meta-toggle.is-expanded .el-icon {
  transform: rotate(180deg);
}

.quote-meta-details {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 8px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  font-variant-numeric: tabular-nums;
}

.quote-meta-details > div {
  display: grid;
  align-content: center;
  gap: 3px;
  min-width: 0;
  min-height: 48px;
  padding: 7px 8px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
  font-size: 11px;
}

.quote-meta-details > div:nth-child(even) {
  border-left: 1px solid rgba(15, 23, 42, 0.06);
}

.quote-meta-details > div:nth-last-child(-n + 2) {
  border-bottom: 0;
}

.quote-meta-details label {
  min-width: 0;
  color: var(--muted);
  font-size: 11px;
  font-weight: 500;
  line-height: 16px;
}

.quote-meta-details span,
.quote-meta-details b {
  min-width: 0;
  overflow: hidden;
  color: var(--ink);
  font-weight: 650;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quote-meta-enter-active,
.quote-meta-leave-active {
  overflow: hidden;
  transition:
    opacity 160ms ease,
    transform 160ms ease;
}

.quote-meta-enter-from,
.quote-meta-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 720px) {
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
  margin: 0 0 4px;
}

.chart-primary-controls {
  width: 100%;
  min-width: 0;
  min-height: 44px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 16px;
  padding: 6px 10px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: #f8fafc;
}

.chart-primary-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.chart-data-status {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
  margin-left: auto;
  padding-right: 8px;
  white-space: nowrap;
}

.period-mode :deep(.el-radio-button__inner) {
  height: 26px;
  min-height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 10px;
  font-size: 11px;
}

.chart-canvas-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 14px;
  min-height: 40px;
  padding: 4px 12px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.07);
  background: rgba(248, 250, 252, 0.72);
}

.chart-legend {
  display: flex;
  flex: 0 0 auto;
  min-width: 0;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
}

.chart-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  margin: 0;
  padding: 2px 0;
  border: 0;
  background: transparent;
  color: #697180;
  font: inherit;
  font-size: 11px;
  line-height: 20px;
  white-space: nowrap;
  cursor: pointer;
}

.chart-legend-item i {
  width: 14px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 2px;
}

.chart-legend-item:hover {
  color: var(--ink);
}

.chart-legend-item.is-inactive {
  opacity: 0.34;
}

.chart-legend-divider {
  width: 1px;
  height: 18px;
  flex: 0 0 auto;
  background: var(--line);
}

.chart-td-toggle i {
  display: inline-grid;
  width: 16px;
  height: 16px;
  place-items: center;
  border: 1px solid #f07b7b;
  border-radius: 50%;
  background: rgba(240, 123, 123, 0.08);
  color: #df6262;
  font-size: 10px;
  font-style: normal;
  font-weight: 700;
  line-height: 1;
}

.chart-td-filter {
  display: inline-flex;
  height: 26px;
  align-items: center;
  flex: 0 0 auto;
  margin-left: 0;
  --el-checkbox-checked-bg-color: #df6262;
  --el-checkbox-checked-input-border-color: #df6262;
  --el-checkbox-input-border-color-hover: #df6262;
}

.chart-td-filter :deep(.el-checkbox__label) {
  padding-left: 5px;
  color: #697180;
  font-size: 11px;
  line-height: 20px;
}

.chart-td-filter.is-checked :deep(.el-checkbox__label) {
  color: var(--ink);
}

.chart-zoom-controls {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0;
}

.chart-zoom-controls--desktop {
  justify-self: end;
}

.chart-zoom-controls--mobile {
  display: none;
}

.chart-zoom-button {
  width: 28px;
  min-width: 28px;
  height: 28px;
  min-height: 28px;
  margin: 0;
  padding: 0;
  border-radius: 4px;
  border: 0 !important;
  background: transparent;
  box-shadow: none !important;
  color: #737b87;
}

.chart-zoom-button:hover:not(:disabled),
.chart-zoom-button:focus-visible {
  background: rgba(233, 239, 246, 0.82);
  color: var(--ink);
}

.chart-zoom-controls :deep(.chart-zoom-button.el-button) {
  border: 0 !important;
  background: transparent;
  box-shadow: none;
}

.daily-data-time,
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

.chart-stage {
  position: relative;
  width: 100%;
}

.chart-stage.has-chart-head {
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.68);
}

.chart {
  height: 620px;
  width: 100%;
  touch-action: pan-y;
  overscroll-behavior-x: contain;
  background: rgba(255, 255, 255, 0.68);
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 4px;
  overflow: hidden;
}

.chart-stage.has-chart-head .chart {
  height: 580px;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.structure-insufficient {
  margin-top: 14px;
}

.structure-panel {
  margin-top: 14px;
}

.tabs {
  margin-top: 14px;
  padding-top: 0;
}

.tabs :deep(.el-tabs__header) {
  margin: 0 0 10px;
  border-bottom: 1px solid var(--line);
}

.tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: transparent;
}

.tabs :deep(.el-tabs__item) {
  height: 36px;
  padding: 0 13px;
  color: var(--muted);
  font-size: 14px;
  font-weight: 600;
  line-height: 36px;
}

.tabs :deep(.el-tabs__item.is-active) {
  color: var(--ink);
}

.tabs :deep(.el-tabs__active-bar) {
  height: 2px;
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
  .chart {
    height: 560px;
  }

  .chart-stage.has-chart-head .chart {
    height: 520px;
  }

  .chart-legend {
    gap: 7px;
  }

  .chart-legend-item {
    gap: 3px;
    font-size: 10px;
  }
}

@media (max-width: 820px) {
  .tabs :deep(.el-tabs__nav-wrap) {
    overflow-x: auto;
  }

  .tabs :deep(.el-tabs__item) {
    padding: 0 10px;
    font-size: 13px;
  }

  .market-overview {
    grid-template-columns: minmax(0, 1fr);
    margin-right: -2px;
    margin-left: -2px;
  }

  .quote-snapshot {
    padding: 12px 2px 10px;
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .quote-primary {
    justify-content: flex-start;
  }

  .quote-primary > strong {
    font-size: 30px;
  }

  .quote-primary > div {
    display: flex;
    gap: 8px;
    text-align: left;
  }

  .quote-industry {
    margin-bottom: 8px;
  }

  .quote-metrics {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .quote-metrics > div,
  .quote-metrics > div:nth-child(odd),
  .quote-metrics > div:nth-child(even) {
    display: grid;
    align-content: center;
    gap: 2px;
    min-height: 48px;
    padding: 4px 6px;
    border-left: 0;
  }

  .quote-metrics > div:not(:nth-child(4n + 1)) {
    border-left: 1px solid rgba(15, 23, 42, 0.06);
  }

  .quote-metrics > div:nth-child(n + 9) {
    display: none;
  }

  .quote-metrics label,
  .quote-metrics b {
    line-height: 16px;
  }

  .quote-snapshot .meta-toggle {
    min-height: 44px;
    margin-top: 8px;
  }

  .quote-meta-details {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .quote-meta-details > div:nth-child(even) {
    border-left: 0;
  }

  .quote-meta-details > div:not(:nth-child(4n + 1)) {
    border-left: 1px solid rgba(15, 23, 42, 0.06);
  }

  .quote-meta-details > div:nth-last-child(-n + 4) {
    border-bottom: 0;
  }

  .market-chart {
    min-width: 0;
    padding: 10px 0 12px;
  }

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
    justify-items: stretch;
    gap: 4px;
    width: 100%;
  }

  .stock-action-toolbar {
    display: grid;
    grid-template-columns: 44px 44px repeat(2, minmax(0, 1fr));
    gap: 6px;
    min-height: 44px;
  }

  .stock-action-toolbar :deep(.el-button) {
    width: 100%;
    min-width: 0;
    min-height: 44px;
    margin: 0;
    padding: 0 8px;
    border-radius: 8px;
    font-size: 13px;
    white-space: nowrap;
  }

  .stock-action-toolbar :deep(.stock-icon-action) {
    width: 44px;
    min-width: 44px;
    padding: 0;
  }

  .stock-action-toolbar :deep(.stock-text-action) {
    padding: 0 8px;
    font-size: 13px;
  }

  .chart-toolbar {
    gap: 6px;
    margin-bottom: 6px;
  }

  .chart-primary-controls {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 6px;
    padding: 0;
    border: 0;
    background: transparent;
  }

  .period-mode {
    order: 4;
    grid-column: 1 / -1;
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

  .period-mode :deep(.el-radio-button__inner) {
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
    order: 3;
    grid-column: 1 / -1;
    justify-content: flex-end;
  }

  .chart-primary-actions :deep(.el-button) {
    min-height: 44px;
    margin: 0;
    padding: 0 10px;
    font-size: 13px;
  }

  .chart-zoom-button {
    width: 44px;
    min-width: 44px;
    height: 44px;
    min-height: 44px;
    padding: 0;
  }

  .chart-zoom-controls {
    order: 2;
    justify-self: end;
    margin-left: 0;
    gap: 0;
    padding-left: 8px;
  }

  .chart-zoom-controls--desktop {
    display: none;
  }

  .chart-zoom-controls--mobile {
    display: flex;
    border-left: 1px solid var(--line);
  }

  .chart-data-status {
    order: 1;
    justify-content: flex-end;
    margin-left: 0;
  }

  .chart-stage.has-chart-head .chart {
    height: 500px;
  }

  .chart-canvas-head {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 6px;
    min-height: 40px;
    padding: 4px 10px;
  }

  .chart-canvas-head .period-mode--chart {
    grid-row: 2;
    grid-column: 1 / -1;
  }

  .chart-canvas-head .chart-data-status {
    grid-row: 1;
    grid-column: 1;
    justify-content: flex-start;
  }

  .chart-canvas-head .chart-zoom-controls--mobile {
    grid-row: 1;
    grid-column: 2;
  }

  .chart-legend {
    grid-row: 3;
    grid-column: 1 / -1;
    gap: 10px;
  }

  .chart-td-filter {
    display: none;
  }

  .daily-data-time,
  .intraday-asof {
    order: 2;
  }

  .chart {
    height: 500px;
    border-radius: 8px;
    user-select: none;
    -webkit-user-select: none;
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

.kline-tip__trades {
  display: grid;
  gap: 5px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
}

.kline-tip__trade-row {
  display: grid;
  grid-template-columns: 18px minmax(72px, 1fr) auto;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 11px;
}

.kline-tip__trade-row > span:nth-child(2) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #515154;
}

.kline-tip__trade-row b {
  color: #1d1d1f;
  font-weight: 650;
}

.kline-tip__trade-side {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 800;
}

.kline-tip__trade-side--buy {
  color: #9f2f3b;
  background: rgba(196, 61, 74, 0.1);
}

.kline-tip__trade-side--sell {
  color: #11634d;
  background: rgba(22, 119, 93, 0.1);
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
