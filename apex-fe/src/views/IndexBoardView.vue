<script setup>
/**
 * 行情中心（大盘）— 参考百度财经沪深行情结构：
 * 指数条 → 市场脉搏 → 走势图+板块热力 → 涨跌榜
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { Download, Histogram, Refresh } from '@element-plus/icons-vue'
import { fetchIndexBars, fetchIndexBoard, refreshIndexBoard } from '../api/indexBoard'
import { fetchMarketBriefing, getMarketBoard } from '../api/market'
import { fetchMarketHeatmap } from '../api/heatmap'
import { fetchSectorBoard } from '../api/sector'
import {
  buildMarketShareSheet,
  mountMarketShareSheet,
  renderMarketShareHeatmap,
} from '../utils/marketShareSheet.js'
import {
  captureElementBlob,
  copyImageBlob,
  downloadBlob,
  shareFilename,
} from '../utils/shareCapture.js'
import HeatmapView from './HeatmapView.vue'
import CapitalFlowView from './CapitalFlowView.vue'
import SectorBoardView from './SectorBoardView.vue'
import { isConceptBoard, normalizeHotThemes } from '../utils/hotTheme.js'
import { formatVolumeChangeText } from '../utils/marketVolume.js'
import { snapshotStamp } from '../utils/snapshotDate.js'
import { resolveActiveMarket, resolveMarketTab } from '../utils/marketTradingSession.js'
import { staleDataTime } from '../utils/dataFreshness.js'
import FloatingShareButton from '../components/FloatingShareButton.vue'
import IntradayKlineThumbnail from '../components/IntradayKlineThumbnail.vue'
import { useSessionViewState } from '../utils/viewState.js'
import worldMarketMapUrl from '../assets/world-market-map.svg'
import {
  buildGlobalMarketHubs,
  derivePointChange,
  summarizeGlobalMarkets,
} from '../utils/globalMarketOverview.js'

const marketTabs = [
  { key: 'global', label: '全球' },
  { key: 'cn', label: '沪深A股' },
  { key: 'hk', label: '港股' },
  { key: 'asia', label: '日韩' },
  { key: 'us', label: '美股' },
]
const indexMarketKeys = marketTabs.filter((item) => item.key !== 'global').map((item) => item.key)

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const quoteRefreshing = ref(false)
const indexSyncing = ref(false)
const activeMarketKey = ref(resolveActiveMarket())
const marketTab = ref(resolveMarketTab(activeMarketKey.value) || 'global')
const indexData = ref(null)
const briefing = ref(null)
const marketBoard = ref(null)
const industryRows = ref([])
const conceptRows = ref([])
const industryTradeDate = ref('')
const conceptTradeDate = ref('')
const lastLog = ref('')
const activeCode = ref('')
const detailBars = ref([])
const chartRef = ref(null)
let chart
let marketSessionTimer
const tabRefs = new Map()
const tabIndicatorStyles = ref({ index: {}, analysis: {} })
let tabResizeObserver

useSessionViewState('market', { marketTab })
if (!['global', ...indexMarketKeys, 'capital-flow', 'sector'].includes(marketTab.value)) {
  marketTab.value = resolveMarketTab(activeMarketKey.value) || 'global'
}
if (['global', 'capital-flow', 'sector'].includes(route.query.tab)) marketTab.value = route.query.tab

const sharing = ref(false)
const shareOpen = ref(false)
const sharePreviewUrl = ref('')
const copying = ref(false)
const downloading = ref(false)
let sharePreviewObjectUrl = ''

const cnIndexes = computed(() => indexData.value?.cn || [])
const regionalMarketIndexes = computed(() => ({
  cn: cnIndexes.value,
  hk: indexData.value?.hk || [],
  jp: (indexData.value?.asia || []).filter((item) => item.code === 'JP_N225'),
  kr: (indexData.value?.asia || []).filter((item) => item.code === 'KR_KOSPI'),
  us: indexData.value?.us || [],
}))
const marketIndexes = computed(() => ({
  cn: regionalMarketIndexes.value.cn,
  hk: regionalMarketIndexes.value.hk,
  asia: regionalMarketIndexes.value.jp.concat(regionalMarketIndexes.value.kr),
  us: regionalMarketIndexes.value.us,
}))
const globalMarketHubs = computed(() => buildGlobalMarketHubs(regionalMarketIndexes.value))
const globalMarketSummary = computed(() => summarizeGlobalMarkets(regionalMarketIndexes.value))
const globalMarketTime = computed(() => staleDataTime({
  tradeDate: globalMarketSummary.value.latestTradeDate,
}))
const isIndexMarketTab = computed(() => indexMarketKeys.includes(marketTab.value))
const isQuoteMarketTab = computed(() => marketTab.value === 'global' || isIndexMarketTab.value)
const activeMarketItems = computed(() => marketIndexes.value[marketTab.value] || [])
const activeMarketTitle = computed(() => marketTabs.find((item) => item.key === marketTab.value)?.label || '')
const activeMarketTabKey = computed(() => resolveMarketTab(activeMarketKey.value))
const allIndexItems = computed(() => Object.values(regionalMarketIndexes.value).flat())
const marketPageSubtitle = computed(() => {
  if (marketTab.value === 'global') {
    return '全球市场 · 指数分布与区域走势'
  }
  if (isIndexMarketTab.value && marketTab.value !== 'cn') {
    return `${activeMarketTitle.value}市场 · 指数行情与走势`
  }
  return briefing.value?.message || '沪深市场总览 · 指数 · 涨跌分布 · 板块热力'
})

function setTabsRef(group, element) {
  if (element) tabRefs.set(group, element)
  else tabRefs.delete(group)
}

function updateTabIndicator(group, activeKey, items) {
  const tabs = tabRefs.get(group)
  if (!tabs) return
  const activeIndex = items.findIndex((item) => item.key === activeKey)
  const activeTab = tabs.querySelector(`[data-tab-key="${activeKey}"]`)
  if (activeIndex < 0 || !activeTab) {
    tabIndicatorStyles.value = {
      ...tabIndicatorStyles.value,
      [group]: { opacity: '0' },
    }
    return
  }
  const tabsRect = tabs.getBoundingClientRect()
  const tabRect = activeTab.getBoundingClientRect()
  tabIndicatorStyles.value = {
    ...tabIndicatorStyles.value,
    [group]: {
      left: `${tabRect.left - tabsRect.left}px`,
      width: `${tabRect.width}px`,
      opacity: '1',
    },
  }
}

function updateTabIndicators() {
  updateTabIndicator('index', marketTab.value, marketTabs)
  updateTabIndicator('analysis', marketTab.value, [
    { key: 'sector' },
    { key: 'capital-flow' },
  ])
}

const heroIndexes = computed(() => {
  // 优先用简报实时指数，并用本地指数补 spark / code
  const live = briefing.value?.indexes || []
  if (live.length) {
    return live.map((row) => {
      const local = cnIndexes.value.find((r) => r.name === row.name)
      return {
        name: row.name,
        closePrice: row.close,
        pctChg: row.pctChg,
        code: local?.code || matchCnCode(row.name),
        tradeDate: briefing.value?.asOf,
        sparkCloses: local?.sparkCloses || [],
        live: true,
      }
    })
  }
  return cnIndexes.value.slice(0, 4).map((row) => ({ ...row, live: false }))
})

const effect = computed(() => briefing.value?.effect || null)
const hotThemes = computed(() => normalizeHotThemes(briefing.value))
const volumeChangeText = computed(() => formatVolumeChangeText(briefing.value))

/** 赚钱效应六指标（展示用） */
const effectMetrics = computed(() => {
  const e = effect.value
  if (!e) return []
  return [
    { key: 'avg', label: '平均股价', tip: '800005', value: e.avgPctChg },
    { key: 'median', label: '中位数', tip: '880009口径', value: e.medianPctChg },
    { key: 'eq', label: '全A等权', tip: '800010 / 全A截面算术平均', value: e.equalWeightPctChg },
    { key: 'micro', label: '微盘股', tip: '800007≈880823', value: e.microPctChg ?? e.csi2000PctChg },
    { key: 'csi1000', label: '中证1000', tip: '000852', value: e.csi1000PctChg },
    { key: 'hs300', label: '沪深300', tip: '000300', value: e.hs300PctChg },
  ]
})

const breadth = computed(() => {
  const up = Number(briefing.value?.breadthUp)
  const down = Number(briefing.value?.breadthDown)
  if (Number.isNaN(up) || Number.isNaN(down) || (up <= 0 && down <= 0)) return null
  const flatRaw = briefing.value?.breadthFlat
  const flat = flatRaw == null || Number.isNaN(Number(flatRaw)) ? 0 : Number(flatRaw)
  const total = up + down + flat
  return {
    up,
    down,
    flat,
    hasFlat: flatRaw != null,
    upPct: total ? (up / total) * 100 : 0,
    flatPct: total ? (flat / total) * 100 : 0,
    downPct: total ? (down / total) * 100 : 0,
  }
})

/** 涨停/跌停配对 + 比例条 */
const limitPair = computed(() => {
  const upRaw = briefing.value?.limitUpCount
  const downRaw = briefing.value?.limitDownCount
  if (upRaw == null && downRaw == null) return null
  const up = upRaw == null || Number.isNaN(Number(upRaw)) ? 0 : Number(upRaw)
  const down = downRaw == null || Number.isNaN(Number(downRaw)) ? 0 : Number(downRaw)
  const total = up + down
  return {
    up: upRaw == null ? null : up,
    down: downRaw == null ? null : down,
    upPct: total ? (up / total) * 100 : 50,
    downPct: total ? (down / total) * 100 : 50,
  }
})

const cnStaleHint = computed(() => {
  const rows = cnIndexes.value
  const dates = rows.map((r) => r.tradeDate).filter(Boolean).sort()
  if (!dates.length) return ''
  return staleDataTime({ tradeDate: dates[dates.length - 1] })
})

function indexItemDataTime(item, regionItems) {
  if (!item?.tradeDate) return ''
  if (String(item.code || '').startsWith('CN_')) {
    return staleDataTime({ tradeDate: item.tradeDate })
  }
  const latestRegionDate = (regionItems || [])
    .map((row) => String(row.tradeDate || '').slice(0, 10))
    .filter(Boolean)
    .sort()
    .at(-1)
  return staleDataTime({
    tradeDate: item.tradeDate,
    latest: String(item.tradeDate).slice(0, 10) === latestRegionDate,
  })
}

function matchCnCode(name) {
  const map = {
    上证指数: 'CN_SH',
    深证成指: 'CN_SZ',
    创业板指: 'CN_CYB',
    科创50: 'CN_KC50',
  }
  if (map[name]) return map[name]
  const hit = cnIndexes.value.find((r) => r.name === name)
  return hit?.code || ''
}

function fmtNum(v, digits = 2) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toLocaleString('zh-CN', {
    maximumFractionDigits: digits,
    minimumFractionDigits: digits,
  })
}

function fmtPct(v) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  const sign = n > 0 ? '+' : ''
  return `${sign}${n.toFixed(2)}%`
}

function fmtPointChange(item) {
  const pointChange = derivePointChange(item)
  if (pointChange == null) return '--'
  const sign = pointChange > 0 ? '+' : ''
  return `约 ${sign}${fmtNum(pointChange)}`
}

function fmtVol(v) {
  if (v == null || v === '' || Number(v) === 0) return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  if (Math.abs(n) >= 1e8) return `${(n / 1e8).toFixed(2)}亿`
  if (Math.abs(n) >= 1e4) return `${(n / 1e4).toFixed(1)}万`
  return n.toFixed(0)
}

function pctClass(v) {
  const n = Number(v)
  if (Number.isNaN(n) || n === 0) return 'flat'
  return n > 0 ? 'up' : 'down'
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

function openSectorConstituents(row, type) {
  if (!row?.code) return
  router.push({
    path: '/market',
    query: { tab: 'sector', type, code: row.code },
  })
}

function setMarketTab(tab) {
  const nextTab = resolveMarketTab(tab)
  marketTab.value = nextTab
  const query = { ...route.query }
  if (nextTab === 'global') {
    query.tab = 'global'
    delete query.type
    delete query.code
    delete query.q
  } else if (nextTab === 'capital-flow') {
    query.tab = 'capital-flow'
    delete query.type
    delete query.code
    delete query.q
  } else if (nextTab === 'sector') {
    query.tab = 'sector'
  } else {
    delete query.tab
    delete query.type
    delete query.code
    delete query.q
  }
  router.replace({ query })
  if (nextTab === 'global') {
    activateGlobalMarket()
  } else if (indexMarketKeys.includes(nextTab)) {
    activateIndexMarket(nextTab, tab)
  }
}

async function activateGlobalMarket() {
  const activeHub = globalMarketHubs.value.find((hub) => hub.key === activeMarketKey.value && hub.primary)
  const firstHub = globalMarketHubs.value.find((hub) => hub.primary)
  const primaryIndex = activeHub?.primary || firstHub?.primary
  if (primaryIndex?.code && primaryIndex.code !== activeCode.value) {
    await selectIndex(primaryIndex.code)
  }
}

function selectGlobalHub(hub) {
  if (hub?.primary?.code) selectIndex(hub.primary.code)
}

async function activateIndexMarket(market, preferredMarket = activeMarketKey.value) {
  const preferredIndex = resolveMarketTab(preferredMarket) === market
    ? regionalMarketIndexes.value[preferredMarket]?.find((item) => item.code)
    : null
  const firstIndex = preferredIndex || marketIndexes.value[market]?.find((item) => item.code)
  if (firstIndex?.code && firstIndex.code !== activeCode.value) {
    await selectIndex(firstIndex.code)
  }
}

async function syncActiveMarket() {
  const nextMarket = resolveActiveMarket()
  if (!nextMarket) {
    activeMarketKey.value = null
    return
  }
  const nextMarketTab = resolveMarketTab(nextMarket)
  const marketChanged = nextMarket !== activeMarketKey.value
  activeMarketKey.value = nextMarket
  if (isIndexMarketTab.value && (marketChanged || marketTab.value !== nextMarketTab)) {
    marketTab.value = nextMarketTab
    await activateIndexMarket(nextMarketTab, nextMarket)
  }
}

async function load(forceBriefing = false, showLoading = true) {
  if (showLoading) loading.value = true
  try {
    const [idx, brief, board, industry, concept] = await Promise.all([
      fetchIndexBoard(30),
      fetchMarketBriefing(forceBriefing),
      getMarketBoard('我的自选', 8).catch(() => ({ data: null })),
      fetchSectorBoard({ type: 'INDUSTRY', sortBy: 'pctChg', order: 'desc', limit: 10 }).catch(() => ({ data: null })),
      fetchSectorBoard({ type: 'CONCEPT', sortBy: 'pctChg', order: 'desc', limit: 10 }).catch(() => ({ data: null })),
    ])
    indexData.value = idx.data
    briefing.value = brief.data
    marketBoard.value = board.data
    industryRows.value = Array.isArray(industry.data?.items) ? industry.data.items : []
    conceptRows.value = Array.isArray(concept.data?.items)
      ? concept.data.items.filter((row) => isConceptBoard(row?.name, row?.boardType || 'CONCEPT'))
      : []
    industryTradeDate.value = snapshotStamp(industry.data)
    conceptTradeDate.value = snapshotStamp(concept.data)

    const currentMarket = resolveActiveMarket()
    if (currentMarket) activeMarketKey.value = currentMarket
    if (isIndexMarketTab.value && currentMarket) marketTab.value = resolveMarketTab(currentMarket)
    if (!activeCode.value) {
      if (marketTab.value === 'global') await activateGlobalMarket()
      else await activateIndexMarket(marketTab.value)
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    if (showLoading) loading.value = false
  }
}

async function onRefreshQuotes() {
  quoteRefreshing.value = true
  try {
    await load(true, false)
    ElMessage.success('行情已刷新')
  } catch (e) {
    ElMessage.error(e.message || '刷新失败')
  } finally {
    quoteRefreshing.value = false
  }
}

async function onSyncIndex(start = '20240101') {
  indexSyncing.value = true
  try {
    const res = await refreshIndexBoard(start)
    indexData.value = res.data?.board || indexData.value
    lastLog.value = res.data?.log || ''
    ElMessage.success(res.data?.message || '指数已同步')
    if (activeCode.value) await selectIndex(activeCode.value)
  } catch (e) {
    ElMessage.error(e.message || '同步失败')
  } finally {
    indexSyncing.value = false
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

function activeIndexMeta() {
  return (
    heroIndexes.value.find((i) => i.code === activeCode.value)
    || allIndexItems.value.find((i) => i.code === activeCode.value)
    || null
  )
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const bars = detailBars.value
  const dates = bars.map((b) => b.tradeDate)
  const closes = bars.map((b) => +b.closePrice)
  const volumes = bars.map((b) => (b.volume != null ? +b.volume : 0))
  const active = activeIndexMeta()
  const up = Number(active?.pctChg) >= 0
  const lineColor = up ? '#e11d48' : '#059669'
  chart.setOption({
    animation: false,
    backgroundColor: 'transparent',
    title: {
      text: active ? `${active.name} · 近 ${bars.length} 日` : activeCode.value,
      left: 0,
      top: 0,
      textStyle: { fontSize: 14, fontWeight: 700, color: '#0f172a' },
    },
    tooltip: { trigger: 'axis' },
    grid: [
      { left: 52, right: 16, top: 36, height: '54%' },
      { left: 52, right: 16, top: '74%', height: '16%' },
    ],
    xAxis: [
      { type: 'category', data: dates, gridIndex: 0, axisLabel: { show: false }, axisTick: { show: false } },
      { type: 'category', data: dates, gridIndex: 1, axisLabel: { fontSize: 10, color: '#94a3b8' } },
    ],
    yAxis: [
      { type: 'value', scale: true, gridIndex: 0, splitNumber: 3, splitLine: { lineStyle: { color: '#f1f5f9' } } },
      { type: 'value', gridIndex: 1, splitNumber: 2, axisLabel: { show: false }, splitLine: { show: false } },
    ],
    series: [
      {
        name: '收盘',
        type: 'line',
        data: closes,
        showSymbol: false,
        lineStyle: { width: 2, color: lineColor },
        areaStyle: { color: up ? 'rgba(225,29,72,0.08)' : 'rgba(5,150,105,0.08)' },
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
            if (p.dataIndex === 0) return '#cbd5e1'
            const prev = volumes[p.dataIndex - 1] || 0
            const cur = volumes[p.dataIndex] || 0
            if (!prev || !cur) return '#cbd5e1'
            return cur >= prev ? '#e11d48' : '#059669'
          },
        },
      },
    ],
  })
}

function onResize() {
  chart?.resize()
}

async function scrollToMarketSection() {
  if (!['#heatmap', '#dragon-tiger'].includes(route.hash)) return
  await nextTick()
  document.getElementById(route.hash.slice(1))?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

watch(detailBars, async () => {
  await nextTick()
  if (!chartRef.value) {
    chart?.dispose()
    chart = null
    return
  }
  renderChart()
})

watch(
  () => route.query.tab,
  (tab) => {
    if (tab === 'global' || tab === 'capital-flow' || tab === 'sector') {
      marketTab.value = tab
      if (tab === 'global') activateGlobalMarket()
    } else if (marketTab.value === 'global' || marketTab.value === 'capital-flow' || marketTab.value === 'sector') {
      marketTab.value = resolveMarketTab(activeMarketKey.value) || 'cn'
      activateIndexMarket(marketTab.value)
    }
  },
)

watch(marketTab, async () => {
  await nextTick()
  updateTabIndicators()
  if (activeCode.value && detailBars.value.length) {
    if (chart) {
      chart.dispose()
      chart = null
    }
    renderChart()
  }
})

watch(() => route.hash, scrollToMarketSection)

function revokeSharePreview() {
  if (sharePreviewObjectUrl) {
    URL.revokeObjectURL(sharePreviewObjectUrl)
    sharePreviewObjectUrl = ''
  }
  sharePreviewUrl.value = ''
}

async function captureMarketShare() {
  const titleDate = snapshotStamp(briefing.value, 'asOf')
  if (!titleDate) throw new Error('市场快照日期缺失，请刷新后再分享')
  const shareIndustryRows = industryTradeDate.value === titleDate ? industryRows.value : []
  const shareConceptRows = conceptTradeDate.value === titleDate ? conceptRows.value : []
  let heatmap = null
  try {
    const response = await fetchMarketHeatmap({
      type: 'INDUSTRY',
      colorBy: 'pctChg',
      sizeBy: 'circMv',
      limit: 80,
    })
    if (snapshotStamp(response.data) === titleDate) heatmap = response.data
  } catch (e) {
    console.warn('分享行情截图时未获取到行业云图', e)
  }
  const sheet = buildMarketShareSheet({
    titleDate,
    volumeText: briefing.value?.indexVolumeText || '--',
    volumeLabel: volumeChangeText.value,
    breadth: breadth.value
      ? {
          up: breadth.value.up,
          flat: breadth.value.hasFlat ? breadth.value.flat : '--',
          down: breadth.value.down,
        }
      : null,
    limitPair: limitPair.value,
    indexes: heroIndexes.value,
    effectMetrics: effectMetrics.value,
    hint: effect.value?.hint || '',
    industries: shareIndustryRows.map((row) => ({
      name: row.name,
      pctChg: row.pctChg ?? row.avgPctChg,
    })),
    concepts: shareConceptRows.map((row) => ({
      name: row.name,
      pctChg: row.pctChg ?? row.avgPctChg,
    })),
    heatmap,
  })
  const mounted = mountMarketShareSheet(sheet)
  const heatmapChart = renderMarketShareHeatmap(sheet, heatmap?.nodes)
  try {
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))
    const width = 960
    const height = Math.max(sheet.scrollHeight, sheet.offsetHeight, 1)
    sheet.style.width = `${width}px`
    sheet.style.height = `${height}px`
    const dpr = Math.max(window.devicePixelRatio || 1, 2)
    return await captureElementBlob(sheet, {
      scale: Math.min(dpr, 2.5),
      width,
      height,
      backgroundColor: '#edf2f7',
      style: {
        width: `${width}px`,
        height: `${height}px`,
        overflow: 'visible',
        transform: 'none',
        margin: '0',
        opacity: '1',
      },
    })
  } finally {
    heatmapChart?.dispose()
    mounted.dispose()
  }
}

async function openShare() {
  sharing.value = true
  try {
    const blob = await captureMarketShare()
    revokeSharePreview()
    sharePreviewObjectUrl = URL.createObjectURL(blob)
    sharePreviewUrl.value = sharePreviewObjectUrl
    shareOpen.value = true
  } catch (e) {
    console.error('生成市场指数分享图失败', e)
    ElMessage.error(e.message || '截图失败')
  } finally {
    sharing.value = false
  }
}

async function onCopyShare() {
  copying.value = true
  try {
    await copyImageBlob(captureMarketShare())
    ElMessage.success('已复制到剪贴板，可直接粘贴到微信/文档')
  } catch (e) {
    console.error('复制市场指数分享图失败', e)
    ElMessage.error(e.message || '复制失败，请改用下载')
  } finally {
    copying.value = false
  }
}

async function onDownloadShare() {
  downloading.value = true
  try {
    const blob = await captureMarketShare()
    const date = snapshotStamp(briefing.value, 'asOf') || 'date-unknown'
    downloadBlob(blob, shareFilename('apex_market', date))
    ElMessage.success('已下载分享图')
  } catch (e) {
    console.error('下载市场指数分享图失败', e)
    ElMessage.error(e.message || '下载失败')
  } finally {
    downloading.value = false
  }
}

function closeShare() {
  shareOpen.value = false
  revokeSharePreview()
  copying.value = false
  downloading.value = false
}

onMounted(async () => {
  // 首次进入复用服务端简报快照，避免与首页同时触发全市场截面重建。
  await load()
  marketSessionTimer = window.setInterval(syncActiveMarket, 30 * 1000)
  window.addEventListener('resize', onResize)
  await nextTick()
  updateTabIndicators()
  if (typeof ResizeObserver !== 'undefined') {
    tabResizeObserver = new ResizeObserver(updateTabIndicators)
    tabRefs.forEach((element) => tabResizeObserver.observe(element))
  }
  await scrollToMarketSection()
})

onBeforeUnmount(() => {
  window.clearInterval(marketSessionTimer)
  window.removeEventListener('resize', onResize)
  tabResizeObserver?.disconnect()
  revokeSharePreview()
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="page mc-page" v-loading="loading">
    <header class="header">
      <div>
        <p class="eyebrow">Quotation</p>
        <h1>行情中心</h1>
        <p>{{ marketPageSubtitle }}</p>
      </div>
      <div class="actions">
        <nav class="market-nav" aria-label="行情中心视图">
          <div class="market-nav-group index-market-nav">
            <span class="market-nav-label">指数行情</span>
            <div
              class="tabs"
              role="tablist"
              aria-label="指数行情"
              :ref="(element) => setTabsRef('index', element)"
            >
              <span class="tab-indicator" aria-hidden="true" :style="tabIndicatorStyles.index" />
              <button
                v-for="item in marketTabs"
                :key="item.key"
                type="button"
                class="tab"
                :data-tab-key="item.key"
                :class="{ on: marketTab === item.key }"
                @click="setMarketTab(item.key)"
              >{{ item.label }}</button>
            </div>
          </div>
        </nav>
        <div class="market-secondary-row">
          <div class="market-nav-group analysis-market-nav">
            <span class="market-nav-label">A股分析</span>
            <div
              class="tabs"
              role="tablist"
              aria-label="A股分析"
              :ref="(element) => setTabsRef('analysis', element)"
            >
              <span class="tab-indicator" aria-hidden="true" :style="tabIndicatorStyles.analysis" />
              <button
                type="button"
                class="tab"
                data-tab-key="sector"
                :class="{ on: marketTab === 'sector' }"
                @click="setMarketTab('sector')"
              >板块</button>
              <button
                type="button"
                class="tab"
                data-tab-key="capital-flow"
                :class="{ on: marketTab === 'capital-flow' }"
                @click="setMarketTab('capital-flow')"
              >资金流</button>
            </div>
          </div>
          <div v-if="isQuoteMarketTab" class="market-action-group">
            <el-button class="market-action" :loading="quoteRefreshing" :disabled="indexSyncing" aria-label="刷新行情" @click="onRefreshQuotes">
              <el-icon v-if="!quoteRefreshing"><Refresh /></el-icon>
              <span class="desktop-action-label">刷新行情</span>
              <span class="mobile-action-label">刷新</span>
            </el-button>
            <el-button class="market-action" :loading="indexSyncing" :disabled="quoteRefreshing" aria-label="同步指数" @click="onSyncIndex('20240101')">
              <el-icon v-if="!indexSyncing"><Download /></el-icon>
              <span class="desktop-action-label">同步指数</span>
              <span class="mobile-action-label">同步</span>
            </el-button>
            <el-button class="market-action mobile-icon-action" plain aria-label="打开连板天梯" title="连板天梯" @click="router.push('/limit-up')">
              <el-icon><Histogram /></el-icon>
              <span class="desktop-action-label">连板天梯</span>
            </el-button>
          </div>
        </div>
      </div>
    </header>

    <FloatingShareButton
      v-if="marketTab === 'cn' && !shareOpen"
      :loading="sharing"
      label="分享行情截图"
      @click="openShare"
    />

    <el-alert
      v-if="marketTab === 'cn' && cnStaleHint"
      class="stale-alert"
      type="info"
      show-icon
      :closable="false"
      :title="cnStaleHint"
    />

    <template v-if="marketTab === 'global'">
      <section class="global-overview" aria-label="全球主要指数分布">
        <div class="global-overview-head">
          <div>
            <p class="section-kicker">World markets</p>
            <h2>全球主要指数分布</h2>
          </div>
          <div class="global-summary" aria-label="全球指数概览">
            <span v-if="globalMarketTime" class="snapshot">{{ globalMarketTime }}</span>
            <span>覆盖指数 <b>{{ globalMarketSummary.total }}</b></span>
            <span class="up">上涨 <b>{{ globalMarketSummary.up }}</b></span>
            <span class="down">下跌 <b>{{ globalMarketSummary.down }}</b></span>
            <span>平盘 <b>{{ globalMarketSummary.flat }}</b></span>
          </div>
        </div>

        <div class="world-market-map">
          <img :src="worldMarketMapUrl" alt="" aria-hidden="true" />
          <button
            v-for="hub in globalMarketHubs"
            :key="hub.key"
            type="button"
            class="global-node"
            :class="[
              pctClass(hub.primary?.pctChg),
              { on: activeCode === hub.primary?.code, empty: !hub.primary },
            ]"
            :style="{
              '--hub-x': `${hub.position.x}%`,
              '--hub-y': `${hub.position.y}%`,
              '--hub-mobile-x': `${hub.position.mobileX}%`,
              '--hub-mobile-y': `${hub.position.mobileY}%`,
            }"
            :disabled="!hub.primary"
            @click="selectGlobalHub(hub)"
          >
            <span class="global-node-market">
              {{ hub.label }}
              <i v-if="hub.key === activeMarketKey" title="按常规交易时段判断">当前时段</i>
            </span>
            <strong class="global-node-name">{{ hub.primary?.name || '暂无指数' }}</strong>
            <span class="global-node-price">{{ fmtNum(hub.primary?.closePrice) }}</span>
            <span class="global-node-change" title="涨跌点数由收盘点位与涨跌幅反推">
              <span>{{ fmtPointChange(hub.primary) }}</span>
              <b>{{ fmtPct(hub.primary?.pctChg) }}</b>
            </span>
            <i class="global-node-marker" aria-hidden="true" />
          </button>
        </div>

        <div class="global-region-grid">
          <section v-for="hub in globalMarketHubs" :key="`region-${hub.key}`" class="global-region">
            <div class="global-region-head">
              <div>
                <strong>{{ hub.label }}</strong>
                <span>{{ hub.items.length }} 个指数</span>
              </div>
              <button type="button" class="link" @click="setMarketTab(hub.key)">区域详情</button>
            </div>
            <div v-if="hub.items.length" class="global-quote-list">
              <button
                v-for="item in hub.items"
                :key="item.code"
                type="button"
                class="global-quote-row"
                :class="{ on: activeCode === item.code }"
                @click="selectIndex(item.code)"
              >
                <span>
                  <strong>{{ item.name }}</strong>
                  <small v-if="indexItemDataTime(item, hub.items)">{{ indexItemDataTime(item, hub.items) }}</small>
                </span>
                <b :class="pctClass(item.pctChg)">{{ fmtPct(item.pctChg) }}</b>
              </button>
            </div>
            <p v-else class="side-empty">暂无指数数据</p>
          </section>
        </div>
      </section>

      <section v-if="activeCode" class="chart-panel global-chart">
        <div ref="chartRef" class="chart" />
      </section>
    </template>

    <template v-else-if="marketTab === 'cn'">
      <!-- 指数条 -->
      <section class="hero-indexes" aria-label="主要指数">
        <button
          v-for="item in heroIndexes"
          :key="item.name + (item.code || '')"
          type="button"
          class="hero-card"
          :class="[pctClass(item.pctChg), { on: item.code && activeCode === item.code }]"
          @click="item.code && selectIndex(item.code)"
        >
          <div class="hero-name">
            <strong>{{ item.name }}</strong>
            <span v-if="item.live" class="live">实时</span>
          </div>
          <div class="hero-price">
            <b>{{ fmtNum(item.closePrice ?? item.close) }}</b>
            <em>{{ fmtPct(item.pctChg) }}</em>
          </div>
          <IntradayKlineThumbnail
            v-if="item.sparkCloses?.length"
            class="spark"
            :points="item.sparkCloses"
            :previous-close="item.sparkCloses[0]"
            :width="88"
            :height="28"
            :label="`${item.name}近20日走势`"
          />
        </button>
        <div v-if="!heroIndexes.length" class="hero-empty">
          暂无指数，请先「同步指数」或「刷新行情」
        </div>
      </section>

      <!-- 市场脉搏：宽度 + 赚钱效应 -->
      <section class="pulse" aria-label="市场脉搏">
        <div class="pulse-width">
          <div class="pulse-tile vol">
            <span class="k"><TermTip term="amount">三市成交</TermTip></span>
            <strong class="v">{{ briefing?.indexVolumeText || '--' }}</strong>
            <span v-if="volumeChangeText" class="sub">{{ volumeChangeText }}</span>
          </div>
          <div class="pulse-tile breadth">
            <span class="k"><TermTip term="market_breadth">涨跌家数</TermTip></span>
            <template v-if="breadth">
              <div class="pair">
                <strong class="up">{{ breadth.up }}</strong>
                <span class="sep">/</span>
                <strong class="flat">{{ breadth.hasFlat ? breadth.flat : '--' }}</strong>
                <span class="sep">/</span>
                <strong class="down">{{ breadth.down }}</strong>
              </div>
              <div class="bar" aria-hidden="true">
                <i class="up-seg" :style="{ width: breadth.upPct + '%' }" />
                <i class="flat-seg" :style="{ width: breadth.flatPct + '%' }" />
                <i class="down-seg" :style="{ width: breadth.downPct + '%' }" />
              </div>
            </template>
            <strong v-else class="v miss">--</strong>
          </div>
          <div class="pulse-tile limit">
            <span class="k"><TermTip term="price_limit_system">涨跌停</TermTip></span>
            <template v-if="limitPair">
              <div class="pair">
                <strong class="up">{{ limitPair.up ?? '--' }}</strong>
                <span class="sep">/</span>
                <strong class="down">{{ limitPair.down ?? '--' }}</strong>
              </div>
              <div class="bar thin" aria-hidden="true">
                <i class="up-seg" :style="{ width: limitPair.upPct + '%' }" />
                <i class="down-seg" :style="{ width: limitPair.downPct + '%' }" />
              </div>
            </template>
            <strong v-else class="v miss">--</strong>
          </div>
        </div>

        <div v-if="effectMetrics.length" class="pulse-effect">
          <div class="pulse-effect-head">
            <span class="pulse-effect-title"><TermTip term="money_effect">赚钱效应</TermTip></span>
            <span v-if="effect?.hint" class="pulse-effect-hint">{{ effect.hint }}</span>
          </div>
          <div class="pulse-effect-grid">
            <div
              v-for="m in effectMetrics"
              :key="m.key"
              class="metric"
              :class="pctClass(m.value)"
              :title="m.tip"
            >
              <span class="k">{{ m.label }}</span>
              <strong class="v">{{ fmtPct(m.value) }}</strong>
            </div>
          </div>
        </div>
      </section>

      <!-- 主区：走势 + 板块 -->
      <div class="main-grid">
        <section class="chart-panel">
          <div class="panel-head">
            <h2><TermTip term="market_index">指数走势</TermTip></h2>
            <div class="mini-tabs">
              <button
                v-for="item in cnIndexes.slice(0, 6)"
                :key="item.code"
                type="button"
                class="mini-tab"
                :class="{ on: activeCode === item.code }"
                @click="selectIndex(item.code)"
              >{{ item.name }}</button>
            </div>
          </div>
          <div v-if="activeCode && detailBars.length" ref="chartRef" class="chart" />
          <div v-else class="chart-empty">
            {{ activeCode ? '暂无可用指数走势，请刷新或同步指数后重试' : '选择上方指数查看走势' }}
          </div>
          <p class="hint">成交量柱：红=较前日放量，绿=较前日缩量</p>
        </section>

        <aside class="market-rankings" aria-label="板块涨幅">
          <section class="side-card ranking-panel">
            <div class="panel-head">
              <h2><TermTip term="sector">行业涨幅</TermTip> <small v-if="industryTradeDate">{{ industryTradeDate }}</small></h2>
              <button type="button" class="link" @click="setMarketTab('sector')">更多</button>
            </div>
            <ul v-if="industryRows.length" class="rank-list">
              <li v-for="(row, idx) in industryRows.slice(0, 8)" :key="row.code || row.name || idx" class="detail-row">
                <button type="button" class="rank-row" @click="openSectorConstituents(row, 'INDUSTRY')">
                  <span class="rank" :class="{ top: idx < 3 }">{{ idx + 1 }}</span>
                  <span class="n">{{ row.name || row.industry || '--' }}</span>
                  <b :class="pctClass(row.pctChg ?? row.avgPctChg)">{{ fmtPct(row.pctChg ?? row.avgPctChg) }}</b>
                </button>
              </li>
            </ul>
            <p v-else class="side-empty">暂无行业数据</p>
          </section>

          <section class="side-card ranking-panel">
            <div class="panel-head">
              <h2><TermTip term="concept_board">概念涨幅</TermTip> <small v-if="conceptTradeDate">{{ conceptTradeDate }}</small></h2>
              <button type="button" class="link" @click="router.push({ path: '/market', query: { tab: 'sector', type: 'CONCEPT' } })">更多</button>
            </div>
            <ul v-if="conceptRows.length" class="rank-list">
              <li v-for="(row, idx) in conceptRows.slice(0, 8)" :key="row.code || row.name || idx" class="detail-row">
                <button type="button" class="rank-row" @click="openSectorConstituents(row, 'CONCEPT')">
                  <span class="rank" :class="{ top: idx < 3 }">{{ idx + 1 }}</span>
                  <span class="n">{{ row.name || '--' }}</span>
                  <b :class="pctClass(row.pctChg)">{{ fmtPct(row.pctChg) }}</b>
                </button>
              </li>
            </ul>
            <p v-else class="side-empty">暂无概念数据</p>
          </section>
        </aside>
      </div>

      <!-- 涨跌榜 + 快捷 -->
      <div class="bottom-grid">
        <section class="side-card">
          <div class="panel-head">
            <h2>自选涨幅</h2>
            <button type="button" class="link" @click="router.push('/watchlist')">自选</button>
          </div>
          <ul v-if="marketBoard?.gainers?.length" class="rank-list">
            <li
              v-for="(row, idx) in marketBoard.gainers"
              :key="'g' + row.code"
              class="clickable"
              @click="router.push(`/stock/${row.code}`)"
            >
              <span class="rank" :class="{ top: idx < 3 }">{{ idx + 1 }}</span>
              <span class="n">{{ row.name || row.code }}</span>
              <b :class="pctClass(row.pctChg)">{{ fmtPct(row.pctChg) }}</b>
            </li>
          </ul>
          <p v-else class="side-empty">暂无自选涨幅</p>
        </section>

        <section class="side-card">
          <div class="panel-head">
            <h2>自选跌幅</h2>
            <button type="button" class="link" @click="router.push('/watchlist')">自选</button>
          </div>
          <ul v-if="marketBoard?.losers?.length" class="rank-list">
            <li
              v-for="(row, idx) in marketBoard.losers"
              :key="'l' + row.code"
              class="clickable"
              @click="router.push(`/stock/${row.code}`)"
            >
              <span class="rank">{{ idx + 1 }}</span>
              <span class="n">{{ row.name || row.code }}</span>
              <b :class="pctClass(row.pctChg)">{{ fmtPct(row.pctChg) }}</b>
            </li>
          </ul>
          <p v-else class="side-empty">暂无自选跌幅</p>
        </section>

        <section class="side-card shortcuts">
          <div class="panel-head"><h2>快捷入口</h2></div>
          <div class="shortcut-grid">
            <button type="button" @click="router.push('/limit-up')">连板天梯</button>
            <button type="button" @click="router.push('/hot')">市场热点</button>
            <button type="button" @click="router.push('/news')">财经资讯</button>
            <button type="button" @click="router.push('/decision')">智能决策</button>
            <button type="button" @click="router.push('/observe')">观察池</button>
            <button type="button" @click="router.push('/dashboard')">决策看板</button>
          </div>
          <section v-if="hotThemes.length" class="themes" aria-label="市场主线">
            <div class="themes-head">
              <span>市场主线</span>
              <small>{{ hotThemes.length }} 个方向</small>
            </div>
            <div class="theme-list">
              <div
                v-for="t in hotThemes.slice(0, 5)"
                :key="t.key"
                class="theme-item"
              >
                <span class="theme-name">{{ t.name }}</span>
                <span v-if="t.pctText" class="theme-pct" :class="t.pctDir">{{ t.pctText }}</span>
              </div>
            </div>
          </section>
        </section>
      </div>

      <HeatmapView embedded />
    </template>

    <template v-else-if="isIndexMarketTab">
      <section class="market-sec">
        <div class="market-sec-head">
          <h2>{{ activeMarketTitle }}</h2>
          <span v-if="marketTab === activeMarketTabKey" class="active-market-tag">当前激活</span>
        </div>
        <div v-if="activeMarketItems.length" class="cards">
          <button
            v-for="item in activeMarketItems"
            :key="item.code"
            type="button"
            class="idx-card"
            :class="{ on: activeCode === item.code }"
            @click="selectIndex(item.code)"
          >
            <div class="idx-top">
              <strong>{{ item.name }}</strong>
              <span v-if="indexItemDataTime(item, activeMarketItems)" class="date">
                {{ indexItemDataTime(item, activeMarketItems) }}
              </span>
            </div>
            <div class="idx-price" :class="pctClass(item.pctChg)">
              <b>{{ fmtNum(item.closePrice) }}</b>
              <em>{{ fmtPct(item.pctChg) }}</em>
            </div>
            <IntradayKlineThumbnail
              v-if="item.sparkCloses?.length"
              class="spark"
              :points="item.sparkCloses"
              :previous-close="item.sparkCloses[0]"
              :width="88"
              :height="28"
              :label="`${item.name}近20日走势`"
            />
          </button>
        </div>
        <p v-else class="side-empty">暂无{{ activeMarketTitle }}指数数据</p>
      </section>
      <section v-if="activeCode" class="chart-panel global-chart">
        <div ref="chartRef" class="chart" />
      </section>
    </template>

    <CapitalFlowView v-else-if="marketTab === 'capital-flow'" embedded />

    <SectorBoardView v-else embedded />

    <el-collapse v-if="isQuoteMarketTab && lastLog" class="log-box">
      <el-collapse-item title="最近指数同步日志" name="log">
        <pre class="log">{{ lastLog }}</pre>
      </el-collapse-item>
    </el-collapse>

    <el-dialog
      v-model="shareOpen"
      title="分享行情截图"
      width="960px"
      append-to-body
      destroy-on-close
      align-center
      class="market-share-dialog"
      @closed="revokeSharePreview"
    >
      <p class="share-tip">含灵极 Apex 品牌与赚钱效应；可复制或下载 PNG 后发微信/社群。</p>
      <div class="share-stage">
        <img v-if="sharePreviewUrl" :src="sharePreviewUrl" alt="行情分享预览" />
      </div>
      <template #footer>
        <el-button @click="closeShare">关闭</el-button>
        <el-button type="primary" plain :loading="copying" @click="onCopyShare">复制图片</el-button>
        <el-button type="primary" :loading="downloading" @click="onDownloadShare">下载 PNG</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mc-page {
  --mc-up: #e11d48;
  --mc-down: #059669;
  --mc-ink: #0f172a;
  --mc-muted: #64748b;
  --mc-line: #e2e8f0;
  --mc-bg: #f8fafc;
}

.stale-alert {
  margin-bottom: 12px;
}

.mc-page > .header > .actions {
  align-items: flex-end;
}

.market-nav {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  min-width: 0;
}

.market-secondary-row,
.market-action-group {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  min-width: 0;
}

.market-action-group {
  align-items: center;
}

.market-nav-group {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.market-nav-label {
  padding-left: 4px;
  color: var(--mc-muted);
  font-size: 10px;
  font-weight: 650;
  line-height: 1;
}

.analysis-market-nav {
  padding-left: 12px;
  border-left: 1px solid var(--mc-line);
}

.tabs {
  position: relative;
  isolation: isolate;
  display: inline-flex;
  padding: 3px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 12px;
  background: rgba(210, 225, 243, 0.46);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.92),
    inset 0 -1px 0 rgba(73, 101, 137, 0.08),
    0 5px 16px rgba(42, 65, 94, 0.09);
  backdrop-filter: blur(20px) saturate(165%);
  -webkit-backdrop-filter: blur(20px) saturate(165%);
  gap: 2px;
}

.tab-indicator {
  position: absolute;
  z-index: 0;
  top: 3px;
  bottom: 3px;
  left: 0;
  width: 0;
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 9px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0.56)),
    rgba(255, 255, 255, 0.55);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.98),
    inset 0 -1px 0 rgba(111, 137, 169, 0.08),
    0 4px 12px rgba(45, 73, 108, 0.16);
  backdrop-filter: blur(14px) saturate(180%);
  -webkit-backdrop-filter: blur(14px) saturate(180%);
  pointer-events: none;
  transition: left 300ms cubic-bezier(0.22, 0.61, 0.36, 1), width 220ms ease;
}

.tab {
  position: relative;
  z-index: 1;
  border: 0;
  background: transparent !important;
  padding: 6px 12px;
  border: 1px solid transparent;
  border-radius: 9px;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  color: var(--mc-muted);
  cursor: pointer;
  transition: color 180ms ease, transform 160ms ease;
}

.tab:hover {
  color: var(--mc-ink);
}

.tab:active {
  transform: scale(0.97);
}

.tab.on {
  color: var(--accent);
  font-weight: 650;
}

.tab:focus-visible {
  outline: 2px solid rgba(22, 105, 201, 0.55);
  outline-offset: -2px;
}

.mc-page :deep(.market-action.el-button) {
  border-color: rgba(255, 255, 255, 0.92) !important;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.78), rgba(255, 255, 255, 0.52)),
    rgba(231, 239, 249, 0.4) !important;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.95),
    inset 0 -1px 0 rgba(93, 119, 153, 0.12),
    0 4px 12px rgba(45, 73, 108, 0.1) !important;
  color: var(--mc-ink) !important;
  backdrop-filter: blur(14px) saturate(160%);
  -webkit-backdrop-filter: blur(14px) saturate(160%);
  transition: transform 160ms ease, box-shadow 180ms ease, background 180ms ease;
}

.mc-page :deep(.market-action.el-button:hover) {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0.64)),
    rgba(231, 239, 249, 0.5) !important;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.98),
    0 6px 16px rgba(45, 73, 108, 0.15) !important;
}

.mc-page :deep(.market-action.el-button:active) {
  transform: scale(0.96);
}

.mobile-action-label {
  display: none;
}

.global-overview {
  margin-bottom: 16px;
  border: 1px solid var(--mc-line);
  background: #fff;
  overflow: hidden;
}

.global-overview-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--mc-line);
}

.section-kicker {
  margin: 0 0 4px;
  color: #0f766e;
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0;
  text-transform: uppercase;
}

.global-overview-head h2 {
  margin: 0;
  color: var(--mc-ink);
  font-size: 20px;
  font-weight: 750;
  letter-spacing: 0;
}

.global-summary {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  min-width: 0;
  color: var(--mc-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.global-summary span {
  white-space: nowrap;
}

.global-summary b {
  color: var(--mc-ink);
  font-size: 13px;
}

.global-summary .up b { color: var(--mc-up); }
.global-summary .down b { color: var(--mc-down); }

.global-summary .snapshot {
  padding-right: 16px;
  border-right: 1px solid var(--mc-line);
}

.world-market-map {
  position: relative;
  width: 100%;
  height: clamp(460px, 42vw, 560px);
  min-height: 460px;
  overflow: hidden;
  background: #f6f9f9;
}

.world-market-map > img {
  position: absolute;
  inset: 5% 2%;
  width: 96%;
  height: 90%;
  object-fit: contain;
  opacity: 0.92;
  pointer-events: none;
}

.global-node {
  position: absolute;
  z-index: 1;
  left: var(--hub-x);
  top: var(--hub-y);
  display: flex;
  width: 156px;
  min-height: 116px;
  transform: translate(-50%, -50%);
  flex-direction: column;
  align-items: flex-start;
  padding: 9px 10px 10px;
  border: 1px solid var(--mc-line);
  border-top: 3px solid #94a3b8;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  color: var(--mc-ink);
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.08);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}

.global-node.up { border-top-color: var(--mc-up); }
.global-node.down { border-top-color: var(--mc-down); }

.global-node:hover,
.global-node.on {
  border-color: #0f766e;
  box-shadow: 0 10px 26px rgba(15, 118, 110, 0.16);
  transform: translate(-50%, calc(-50% - 2px));
}

.global-node:focus-visible,
.global-quote-row:focus-visible {
  outline: 3px solid rgba(15, 118, 110, 0.2);
  outline-offset: 2px;
}

.global-node.empty {
  opacity: 0.65;
  cursor: default;
}

.global-node-market {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  margin-bottom: 5px;
  color: var(--mc-muted);
  font-size: 10px;
  font-weight: 650;
}

.global-node-market i {
  flex: 0 0 auto;
  padding: 1px 4px;
  border-radius: 4px;
  background: #ccfbf1;
  color: #0f766e;
  font-size: 9px;
  font-style: normal;
}

.global-node-name {
  width: 100%;
  overflow: hidden;
  margin-bottom: 2px;
  font-size: 13px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.global-node-price {
  font-size: 17px;
  font-weight: 750;
  font-variant-numeric: tabular-nums;
  line-height: 1.35;
}

.global-node-change {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  color: var(--mc-muted);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.global-node-change b {
  flex: 0 0 auto;
  padding: 2px 5px;
  border-radius: 4px;
  background: #f1f5f9;
  color: var(--mc-muted);
  font-size: 12px;
}

.global-node-change > span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.global-node.up .global-node-price,
.global-node.up .global-node-change,
.global-node.up .global-node-change b { color: var(--mc-up); }
.global-node.up .global-node-change b { background: rgba(225, 29, 72, 0.09); }
.global-node.down .global-node-price,
.global-node.down .global-node-change,
.global-node.down .global-node-change b { color: var(--mc-down); }
.global-node.down .global-node-change b { background: rgba(5, 150, 105, 0.09); }

.global-node-marker {
  position: absolute;
  top: 100%;
  left: 50%;
  width: 1px;
  height: 28px;
  background: #94a3b8;
  pointer-events: none;
}

.global-node-marker::after {
  position: absolute;
  bottom: -4px;
  left: -4px;
  width: 9px;
  height: 9px;
  border: 2px solid #f6f9f9;
  border-radius: 50%;
  background: #64748b;
  content: '';
}

.global-region-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  border-top: 1px solid var(--mc-line);
}

.global-region {
  min-width: 0;
  padding: 14px;
}

.global-region + .global-region {
  border-left: 1px solid var(--mc-line);
}

.global-region-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.global-region-head > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.global-region-head strong {
  overflow: hidden;
  color: var(--mc-ink);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.global-region-head span {
  color: var(--mc-muted);
  font-size: 10px;
}

.global-quote-list {
  border-top: 1px solid #f1f5f9;
}

.global-quote-row {
  display: grid;
  width: 100%;
  min-height: 48px;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 7px 0;
  border: 0;
  border-bottom: 1px solid #f1f5f9;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.global-quote-row:last-child { border-bottom: 0; }
.global-quote-row:hover strong,
.global-quote-row.on strong { color: #0f766e; }

.global-quote-row > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.global-quote-row strong {
  overflow: hidden;
  color: var(--mc-ink);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.global-quote-row small {
  color: var(--mc-muted);
  font-size: 9px;
  font-variant-numeric: tabular-nums;
}

.global-quote-row > b {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.global-quote-row > b.up { color: var(--mc-up); }
.global-quote-row > b.down { color: var(--mc-down); }

.hero-indexes {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.hero-card {
  text-align: left;
  border: 1px solid var(--mc-line);
  background: #fff;
  border-radius: 14px;
  padding: 14px 14px 10px;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.hero-card:hover,
.hero-card.on {
  border-color: rgba(15, 23, 42, 0.22);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.hero-name {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.hero-name .live {
  margin-left: auto;
}

.hero-name strong {
  font-size: 13px;
  color: var(--mc-ink);
}

.live {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(225, 29, 72, 0.1);
  color: var(--mc-up);
}

.hero-price {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 6px;
}

.hero-price b {
  font-size: 24px;
  font-weight: 750;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.03em;
  color: var(--mc-ink);
}

.hero-price em {
  font-style: normal;
  font-size: 14px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.hero-card.up .hero-price b,
.hero-card.up .hero-price em { color: var(--mc-up); }
.hero-card.down .hero-price b,
.hero-card.down .hero-price em { color: var(--mc-down); }

.hero-empty {
  grid-column: 1 / -1;
  padding: 28px;
  text-align: center;
  color: var(--mc-muted);
  border: 1px dashed var(--mc-line);
  border-radius: 14px;
  background: #fff;
}

.pulse {
  display: grid;
  grid-template-columns: minmax(280px, 0.9fr) minmax(0, 1.4fr);
  gap: 10px;
  margin-bottom: 12px;
}

.pulse-width,
.pulse-effect {
  border: 1px solid var(--mc-line);
  border-radius: 14px;
  background: #fff;
  min-width: 0;
}

.pulse-width {
  display: grid;
  grid-template-columns: 1.1fr 1.4fr 0.9fr;
  gap: 0;
  padding: 4px;
}

.pulse-tile {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  min-width: 0;
  padding: 12px 14px;
  border-radius: 10px;
}

.pulse-tile + .pulse-tile {
  position: relative;
}

.pulse-tile + .pulse-tile::before {
  content: '';
  position: absolute;
  left: 0;
  top: 14px;
  bottom: 14px;
  width: 1px;
  background: var(--mc-line);
}

.pulse-tile .k {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--mc-muted);
}

.pulse-tile .v {
  font-size: 18px;
  font-weight: 750;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
  color: var(--mc-ink);
  line-height: 1.15;
}

.pulse-tile .sub {
  font-size: 11px;
  color: var(--mc-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pulse-tile .pair {
  display: flex;
  align-items: baseline;
  gap: 4px;
  min-width: 0;
}

.pulse-tile .pair strong {
  font-size: 17px;
  font-weight: 750;
  font-variant-numeric: tabular-nums;
  line-height: 1.15;
}

.pulse-tile .sep {
  color: #cbd5e1;
  font-size: 13px;
  font-weight: 500;
}

.pulse-tile .up { color: var(--mc-up); }
.pulse-tile .down { color: var(--mc-down); }
.pulse-tile .flat { color: var(--mc-muted); }
.pulse-tile .miss { color: var(--mc-muted); }

.pulse-tile .bar {
  display: flex;
  width: 100%;
  height: 5px;
  border-radius: 999px;
  overflow: hidden;
  background: #f1f5f9;
}

.pulse-tile .bar.thin { height: 4px; }

.pulse-tile .bar .up-seg,
.pulse-tile .bar .flat-seg,
.pulse-tile .bar .down-seg {
  display: block;
  height: 100%;
  min-width: 0;
}

.pulse-tile .bar .up-seg { background: #e11d48; }
.pulse-tile .bar .flat-seg { background: #cbd5e1; }
.pulse-tile .bar .down-seg { background: #059669; }

.pulse-effect {
  padding: 10px 12px 12px;
}

.pulse-effect-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 8px;
  min-width: 0;
}

.pulse-effect-title {
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 700;
  color: var(--mc-ink);
  letter-spacing: 0.02em;
}

.pulse-effect-hint {
  min-width: 0;
  font-size: 11px;
  color: var(--mc-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pulse-effect-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 6px;
}

.metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 10px 8px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid transparent;
}

.metric .k {
  font-size: 11px;
  font-weight: 600;
  color: var(--mc-muted);
  line-height: 1.2;
}

.metric .v {
  font-size: 16px;
  font-weight: 750;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
  color: var(--mc-ink);
  line-height: 1.15;
}

.metric.up {
  background: rgba(225, 29, 72, 0.06);
  border-color: rgba(225, 29, 72, 0.1);
}

.metric.up .v { color: var(--mc-up); }

.metric.down {
  background: rgba(5, 150, 105, 0.06);
  border-color: rgba(5, 150, 105, 0.1);
}

.metric.down .v { color: var(--mc-down); }

.metric.flat .v { color: var(--mc-muted); }

.rank-list b.up { color: var(--mc-up); }
.rank-list b.down { color: var(--mc-down); }

.main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(520px, 1.6fr);
  align-items: start;
  gap: 12px;
  margin-bottom: 12px;
}

.market-rankings {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 12px;
  min-width: 0;
}

.chart-panel,
.side-card {
  border: 1px solid var(--mc-line);
  border-radius: 14px;
  background: #fff;
  padding: 12px 14px 14px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.panel-head h2 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--mc-ink);
}

.panel-head h2 small {
  margin-left: 5px;
  color: var(--mc-muted);
  font-size: 10px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.link {
  border: 0;
  background: transparent;
  color: #2563eb;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}

.mini-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
}

.mini-tab {
  border: 1px solid var(--mc-line);
  background: #fff;
  border-radius: 999px;
  padding: 2px 8px;
  font: inherit;
  font-size: 11px;
  color: var(--mc-muted);
  cursor: pointer;
}

.mini-tab.on {
  border-color: rgba(15, 23, 42, 0.2);
  color: var(--mc-ink);
  font-weight: 650;
  background: #f8fafc;
}

.chart {
  width: 100%;
  height: 360px;
}

.chart-empty {
  height: 280px;
  display: grid;
  place-items: center;
  color: var(--mc-muted);
  font-size: 13px;
}

.hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--mc-muted);
}

.rank-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.rank-list li {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  padding: 7px 0;
  border-bottom: 1px solid #f1f5f9;
  font-size: 13px;
}

.rank-list li:last-child {
  border-bottom: 0;
}

.rank-list li.detail-row {
  display: block;
  padding: 0;
}

.rank-row {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) auto;
  align-items: center;
  width: 100%;
  min-height: 44px;
  gap: 8px;
  padding: 7px 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.rank-row:hover .n {
  color: #2563eb;
}

.rank-row:focus-visible {
  border-radius: 4px;
  outline: 3px solid rgba(37, 99, 235, 0.18);
  outline-offset: 1px;
}

.rank-list li.clickable {
  cursor: pointer;
}

.rank-list li.clickable:hover .n {
  color: #2563eb;
}

.rank {
  font-size: 12px;
  font-weight: 700;
  color: var(--mc-muted);
  font-variant-numeric: tabular-nums;
}

.rank.top {
  color: var(--mc-up);
}

.rank-list .n {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--mc-ink);
}

.rank-list b {
  font-variant-numeric: tabular-nums;
  font-size: 13px;
}

.side-empty {
  margin: 18px 0;
  text-align: center;
  color: var(--mc-muted);
  font-size: 12px;
}

.bottom-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.shortcut-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.shortcut-grid button {
  border: 1px solid var(--mc-line);
  background: #f8fafc;
  border-radius: 10px;
  padding: 12px 10px;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  color: var(--mc-ink);
  cursor: pointer;
  text-align: left;
}

.shortcut-grid button:hover {
  border-color: rgba(15, 23, 42, 0.2);
  background: #fff;
}

.themes {
  margin: 14px 0 0;
  padding-top: 12px;
  border-top: 1px solid var(--mc-line);
}

.themes-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 7px;
  font-size: 12px;
  font-weight: 700;
  color: var(--mc-ink);
}

.themes-head small {
  font-size: 11px;
  font-weight: 500;
  color: var(--mc-muted);
}

.theme-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.theme-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding: 6px 8px;
  border-radius: 7px;
  background: #f1f5f9;
}

.theme-name {
  overflow: hidden;
  color: var(--mc-ink);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.theme-pct {
  font-size: 12px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  letter-spacing: 0;
  white-space: nowrap;
}

.themes .theme-pct.up {
  color: var(--up, #c45656);
}

.themes .theme-pct.down {
  color: var(--down, #1f7a4d);
}

.market-sec {
  margin-bottom: 16px;
}

.market-sec h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}

.market-sec-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.active-market-tag {
  padding: 2px 7px;
  border-radius: 8px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 12px;
  font-weight: 600;
}

.cards {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.idx-card {
  text-align: left;
  border: 1px solid var(--mc-line);
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  cursor: pointer;
}

.idx-card.on,
.idx-card:hover {
  border-color: rgba(15, 23, 42, 0.22);
}

.idx-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.idx-top strong { font-size: 13px; }
.idx-top .date { font-size: 11px; color: var(--mc-muted); }

.idx-price {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 6px;
}

.idx-price b {
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}

.idx-price em {
  font-style: normal;
  font-size: 13px;
  font-weight: 650;
}

.idx-price.up b,
.idx-price.up em { color: var(--mc-up); }
.idx-price.down b,
.idx-price.down em { color: var(--mc-down); }

.spark {
  width: 100%;
  height: 28px;
  display: block;
}

.global-chart {
  margin-top: 8px;
}

.log-box {
  margin-top: 12px;
}

.log {
  margin: 0;
  white-space: pre-wrap;
  font-size: 12px;
  color: var(--mc-muted);
}

@media (max-width: 1100px) {
  .hero-indexes {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .main-grid,
  .bottom-grid {
    grid-template-columns: 1fr;
  }
  .cards {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .global-region-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .global-region:nth-child(4) {
    border-left: 0;
  }
  .global-region:nth-child(n + 4) {
    border-top: 1px solid var(--mc-line);
  }
}

@media (max-width: 980px) {
  .pulse {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .global-overview-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
    padding: 16px;
  }

  .global-summary {
    display: grid;
    width: 100%;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    justify-content: stretch;
    gap: 8px;
  }

  .global-summary .snapshot {
    grid-column: 1 / -1;
    padding: 0 0 8px;
    border-right: 0;
    border-bottom: 1px solid var(--mc-line);
  }

  .world-market-map {
    height: auto;
    min-height: 640px;
  }

  .world-market-map > img {
    inset: 8% -24%;
    width: 148%;
    height: 84%;
    opacity: 0.72;
  }

  .global-node {
    left: var(--hub-mobile-x);
    top: var(--hub-mobile-y);
    width: 132px;
    min-height: 112px;
    padding: 8px 9px;
  }

  .global-node-name {
    overflow-wrap: anywhere;
  }

  .global-node-price {
    font-size: 15px;
  }

  .global-region-grid {
    grid-template-columns: 1fr;
  }

  .global-region + .global-region,
  .global-region:nth-child(4) {
    border-left: 0;
  }

  .global-region + .global-region {
    border-top: 1px solid var(--mc-line);
  }

  .global-region-head strong {
    font-size: 14px;
  }

  .global-quote-row strong,
  .global-quote-row > b {
    font-size: 13px;
  }

  .header .actions .market-nav {
    flex: 1 0 100%;
    align-items: stretch;
    flex-direction: column;
    gap: 8px;
    max-width: 100%;
  }

  .market-nav-group {
    width: 100%;
  }

  .header .actions .index-market-nav .tabs {
    width: 100%;
    overflow: hidden;
  }

  .header .actions .index-market-nav .tabs > .tab {
    flex: 1 1 0;
    min-width: 0;
    padding: 6px 4px;
    font-size: 12px;
    text-align: center;
  }

  .market-secondary-row {
    display: flex;
    align-items: flex-end;
    width: 100%;
    gap: 8px;
    padding-top: 8px;
    border-top: 1px solid var(--mc-line);
  }

  .analysis-market-nav {
    flex: 0 1 auto;
    width: auto;
    padding: 0;
    border-top: 0;
    border-left: 0;
  }

  .market-action-group {
    flex: 0 0 auto;
    gap: 6px;
    margin-left: auto;
  }

  .header .actions .tabs {
    width: max-content;
    max-width: 100%;
    overflow-x: auto;
  }

  .header .actions .tabs > .tab {
    flex: 0 0 auto;
    padding: 6px 10px;
    white-space: nowrap;
  }

  .header .actions :deep(.market-action) {
    flex: 0 0 auto;
    width: auto;
    min-height: 44px;
    margin: 0;
    padding: 0 12px;
  }

  .header .actions :deep(.mobile-icon-action) {
    width: 44px;
    padding: 0;
  }

  .desktop-action-label {
    display: none;
  }

  .mobile-action-label {
    display: inline;
  }

  .hero-price {
    min-width: 0;
    gap: 6px;
  }

  .hero-price b {
    min-width: 0;
    font-size: 20px;
  }

  .hero-price em {
    flex: 0 0 auto;
    white-space: nowrap;
  }

  .cards {
    grid-template-columns: 1fr 1fr;
  }
  .market-rankings {
    grid-template-columns: 1fr;
  }
  .pulse-width {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.45fr) minmax(0, 0.95fr);
    padding: 3px;
  }

  .pulse-tile {
    gap: 5px;
    padding: 10px 8px;
  }

  .pulse-tile .v,
  .pulse-tile .pair strong {
    font-size: 15px;
  }

  .pulse-tile .sub {
    font-size: 10px;
  }

  .pulse-tile .pair {
    gap: 2px;
  }

  .pulse-tile .sep {
    font-size: 12px;
  }

  .pulse-tile + .pulse-tile::before {
    display: block;
  }

}

@media (max-width: 560px) {
  .themes {
    margin-top: 12px;
    padding-top: 10px;
  }

  .themes-head {
    margin-bottom: 6px;
  }

  .theme-list {
    gap: 5px;
  }

  .theme-item {
    gap: 4px;
    padding: 6px 7px;
  }

  .theme-name,
  .theme-pct {
    font-size: 11px;
  }

  .pulse-effect {
    padding: 10px;
  }

  .pulse-effect-head {
    margin-bottom: 8px;
  }

  .pulse-effect-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 0;
    overflow: hidden;
    border: 1px solid var(--mc-line);
    border-radius: 10px;
    background: #f8fafc;
  }

  .pulse-effect-grid .metric {
    position: relative;
    align-items: center;
    justify-content: center;
    gap: 5px;
    min-height: 66px;
    padding: 9px 3px;
    border: 0;
    border-radius: 0;
    background: transparent;
    text-align: center;
  }

  .pulse-effect-grid .metric + .metric::before {
    position: absolute;
    top: 10px;
    bottom: 10px;
    left: 0;
    width: 1px;
    background: var(--mc-line);
    content: '';
  }

  .pulse-effect-grid .metric:nth-child(3n + 1)::before {
    display: none;
  }

  .pulse-effect-grid .metric:nth-child(n + 4)::after {
    position: absolute;
    top: 0;
    right: 10px;
    left: 10px;
    height: 1px;
    background: var(--mc-line);
    content: '';
  }

  .pulse-effect-grid .metric .k {
    width: 100%;
    overflow: hidden;
    font-size: 10px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .pulse-effect-grid .metric .v {
    font-size: 14px;
    white-space: nowrap;
  }
}

@media (min-width: 360px) and (max-width: 560px) {
  .pulse-effect-grid .metric .v {
    font-size: 15px;
  }
}

.share-tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: #86868b;
}

.share-stage {
  /* 勿用 flex + 默认 stretch，会把预览图纵向压扁 */
  display: block;
  width: 100%;
  max-height: min(68vh, 760px);
  overflow: auto;
  padding: 10px;
  background: #ececec;
  border-radius: 12px;
  text-align: center;
  box-sizing: border-box;
}

.share-stage img {
  width: min(100%, 960px);
  max-width: none;
  height: auto;
  display: inline-block;
  vertical-align: top;
  object-fit: contain;
  box-shadow: 0 10px 32px rgba(0, 0, 0, 0.12);
  border-radius: 4px;
}
</style>
