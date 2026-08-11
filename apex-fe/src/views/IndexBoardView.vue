<script setup>
/**
 * 行情中心（大盘）— 参考百度财经沪深行情结构：
 * 指数条 → 市场脉搏 → 走势图+板块热力 → 涨跌榜
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { Download, Grid, Histogram, Refresh } from '@element-plus/icons-vue'
import { fetchIndexBars, fetchIndexBoard, refreshIndexBoard } from '../api/indexBoard'
import { fetchMarketBriefing, getMarketBoard } from '../api/market'
import { fetchSectorBoard } from '../api/sector'
import {
  buildMarketShareSheet,
  mountMarketShareSheet,
} from '../utils/marketShareSheet.js'
import {
  captureElementBlob,
  copyImageBlob,
  downloadBlob,
  shareFilename,
} from '../utils/shareCapture.js'
import HeatmapView from './HeatmapView.vue'
import { normalizeHotThemes } from '../utils/hotTheme.js'
import { formatVolumeChangeText } from '../utils/marketVolume.js'
import { snapshotStamp } from '../utils/snapshotDate.js'
import FloatingShareButton from '../components/FloatingShareButton.vue'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const refreshing = ref(false)
const marketTab = ref('ab') // ab | global
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

const sharing = ref(false)
const shareOpen = ref(false)
const sharePreviewUrl = ref('')
const copying = ref(false)
const downloading = ref(false)
let sharePreviewObjectUrl = ''

const cnIndexes = computed(() => indexData.value?.cn || [])
const globalSections = computed(() => [
  { key: 'hk', title: '港股', items: indexData.value?.hk || [] },
  { key: 'asia', title: '日韩', items: indexData.value?.asia || [] },
  { key: 'us', title: '美国', items: indexData.value?.us || [] },
])

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

/** 赚钱效应五指标（展示用） */
const effectMetrics = computed(() => {
  const e = effect.value
  if (!e) return []
  return [
    { key: 'avg', label: '平均股价', tip: '800005', value: e.avgPctChg },
    { key: 'median', label: '中位数', tip: '880009口径', value: e.medianPctChg },
    { key: 'eq', label: '全A等权', tip: '800010 / 全A截面算术平均', value: e.equalWeightPctChg },
    { key: 'micro', label: '微盘股', tip: '800007≈880823', value: e.microPctChg ?? e.csi2000PctChg },
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
  const latest = dates[dates.length - 1]
  const today = new Date()
  const todayStr = [
    today.getFullYear(),
    String(today.getMonth() + 1).padStart(2, '0'),
    String(today.getDate()).padStart(2, '0'),
  ].join('-')
  if (latest >= todayStr) return ''
  const wd = today.getDay()
  if (wd === 0 || wd === 6) return `指数本地截至 ${latest}（周末休市）`
  return `指数本地截至 ${latest} · 顶部条优先用实时简报`
})

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

async function load(forceBriefing = false) {
  loading.value = true
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
    conceptRows.value = Array.isArray(concept.data?.items) ? concept.data.items : []
    industryTradeDate.value = snapshotStamp(industry.data)
    conceptTradeDate.value = snapshotStamp(concept.data)

    if (!activeCode.value) {
      const first = heroIndexes.value.find((i) => i.code) || cnIndexes.value[0]
      if (first?.code) await selectIndex(first.code)
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onRefreshQuotes() {
  refreshing.value = true
  try {
    await load(true)
    ElMessage.success('行情已刷新')
  } catch (e) {
    ElMessage.error(e.message || '刷新失败')
  } finally {
    refreshing.value = false
  }
}

async function onSyncIndex(start = '20240101') {
  refreshing.value = true
  try {
    const res = await refreshIndexBoard(start)
    indexData.value = res.data?.board || indexData.value
    lastLog.value = res.data?.log || ''
    ElMessage.success(res.data?.message || '指数已同步')
    if (activeCode.value) await selectIndex(activeCode.value)
  } catch (e) {
    ElMessage.error(e.message || '同步失败')
  } finally {
    refreshing.value = false
  }
}

async function selectIndex(code) {
  if (!code) return
  activeCode.value = code
  marketTab.value = 'ab'
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
    || cnIndexes.value.find((i) => i.code === activeCode.value)
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

watch(detailBars, () => nextTick(renderChart))

watch(marketTab, async () => {
  await nextTick()
  if (activeCode.value && detailBars.value.length) {
    if (chart) {
      chart.dispose()
      chart = null
    }
    renderChart()
  }
})

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
  const sheet = buildMarketShareSheet({
    titleDate,
    message: briefing.value?.message || '沪深市场总览 · 赚钱效应 · 板块热力',
    stance: briefing.value?.stance || '',
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
  })
  const mounted = mountMarketShareSheet(sheet)
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
      backgroundColor: '#0f1419',
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
    console.error(e)
    ElMessage.error(e.message || '截图失败')
  } finally {
    sharing.value = false
  }
}

async function onCopyShare() {
  copying.value = true
  try {
    const blob = await captureMarketShare()
    await copyImageBlob(blob)
    ElMessage.success('已复制到剪贴板，可直接粘贴到微信/文档')
  } catch (e) {
    console.error(e)
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
    console.error(e)
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
  await load(true)
  window.addEventListener('resize', onResize)
  if (route.hash === '#heatmap') {
    await nextTick()
    document.getElementById('heatmap')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  revokeSharePreview()
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="page mc-page" v-loading="loading || refreshing">
    <header class="header">
      <div>
        <p class="eyebrow">灵枢 · Quotation</p>
        <h1>行情中心</h1>
        <p>{{ briefing?.message || '沪深市场总览 · 指数 · 涨跌分布 · 板块热力' }}</p>
      </div>
      <div class="actions">
        <div class="tabs" role="tablist">
          <button
            type="button"
            class="tab"
            :class="{ on: marketTab === 'ab' }"
            @click="marketTab = 'ab'"
          >沪深A股</button>
          <button
            type="button"
            class="tab"
            :class="{ on: marketTab === 'global' }"
            @click="marketTab = 'global'"
          >全球指数</button>
        </div>
        <el-button class="market-action" type="primary" :loading="refreshing" aria-label="刷新行情" @click="onRefreshQuotes">
          <el-icon v-if="!refreshing"><Refresh /></el-icon>
          <span class="desktop-action-label">刷新行情</span>
          <span class="mobile-action-label">刷新</span>
        </el-button>
        <el-button class="market-action" :loading="refreshing" aria-label="同步指数" @click="onSyncIndex('20240101')">
          <el-icon v-if="!refreshing"><Download /></el-icon>
          <span class="desktop-action-label">同步指数</span>
          <span class="mobile-action-label">同步</span>
        </el-button>
        <el-button class="market-action mobile-icon-action" plain aria-label="打开板块行情" title="板块" @click="router.push('/sector')">
          <el-icon><Grid /></el-icon>
          <span class="desktop-action-label">板块</span>
        </el-button>
        <el-button class="market-action mobile-icon-action" plain aria-label="打开连板天梯" title="连板天梯" @click="router.push('/limit-up')">
          <el-icon><Histogram /></el-icon>
          <span class="desktop-action-label">涨停</span>
        </el-button>
      </div>
    </header>

    <FloatingShareButton
      v-if="!shareOpen"
      :loading="sharing"
      label="分享行情截图"
      @click="openShare"
    />

    <el-alert
      v-if="cnStaleHint"
      class="stale-alert"
      type="info"
      show-icon
      :closable="false"
      :title="cnStaleHint"
    />

    <template v-if="marketTab === 'ab'">
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
          <svg
            v-if="item.sparkCloses?.length"
            class="spark"
            viewBox="0 0 88 28"
            preserveAspectRatio="none"
          >
            <path
              :d="sparkPath(item.sparkCloses)"
              fill="none"
              :stroke="Number(item.pctChg) >= 0 ? '#e11d48' : '#059669'"
              stroke-width="1.6"
            />
          </svg>
        </button>
        <div v-if="!heroIndexes.length" class="hero-empty">
          暂无指数，请先「同步指数」或「刷新行情」
        </div>
      </section>

      <!-- 市场脉搏：宽度 + 赚钱效应 -->
      <section class="pulse" aria-label="市场脉搏">
        <div class="pulse-width">
          <div class="pulse-tile vol">
            <span class="k">三市成交</span>
            <strong class="v">{{ briefing?.indexVolumeText || '--' }}</strong>
            <span v-if="volumeChangeText" class="sub">{{ volumeChangeText }}</span>
          </div>
          <div class="pulse-tile breadth">
            <span class="k">涨跌家数</span>
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
            <span class="k">涨跌停</span>
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
            <span class="pulse-effect-title">赚钱效应</span>
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
            <h2>指数走势</h2>
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
          <div v-if="activeCode" ref="chartRef" class="chart" />
          <div v-else class="chart-empty">选择上方指数查看走势</div>
          <p class="hint">成交量柱：红=较前日放量，绿=较前日缩量</p>
        </section>

        <aside class="side-panels">
          <section class="side-card">
            <div class="panel-head">
              <h2>行业涨幅 <small v-if="industryTradeDate">{{ industryTradeDate }}</small></h2>
              <button type="button" class="link" @click="router.push('/sector')">更多</button>
            </div>
            <ul v-if="industryRows.length" class="rank-list">
              <li v-for="(row, idx) in industryRows.slice(0, 8)" :key="row.code || row.name || idx">
                <span class="rank" :class="{ top: idx < 3 }">{{ idx + 1 }}</span>
                <span class="n">{{ row.name || row.industry || '--' }}</span>
                <b :class="pctClass(row.pctChg ?? row.avgPctChg)">{{ fmtPct(row.pctChg ?? row.avgPctChg) }}</b>
              </li>
            </ul>
            <p v-else class="side-empty">暂无行业数据</p>
          </section>

          <section class="side-card">
            <div class="panel-head">
              <h2>概念涨幅 <small v-if="conceptTradeDate">{{ conceptTradeDate }}</small></h2>
              <button type="button" class="link" @click="router.push({ path: '/sector', query: { type: 'CONCEPT' } })">更多</button>
            </div>
            <ul v-if="conceptRows.length" class="rank-list">
              <li v-for="(row, idx) in conceptRows.slice(0, 8)" :key="row.code || row.name || idx">
                <span class="rank" :class="{ top: idx < 3 }">{{ idx + 1 }}</span>
                <span class="n">{{ row.name || '--' }}</span>
                <b :class="pctClass(row.pctChg)">{{ fmtPct(row.pctChg) }}</b>
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
          <p v-if="hotThemes.length" class="themes">
            主线
            <span
              v-for="t in hotThemes.slice(0, 5)"
              :key="t.key"
            >
              <span class="theme-name">{{ t.name }}</span>
              <span v-if="t.abs" class="theme-pct" :class="t.pctDir">
                <span v-if="t.sign" class="theme-sign">{{ t.sign }}</span>{{ t.abs }}%
              </span>
            </span>
          </p>
        </section>
      </div>

      <HeatmapView embedded />
    </template>

    <template v-else>
      <section
        v-for="sec in globalSections"
        :key="sec.key"
        class="market-sec"
        v-show="sec.items.length"
      >
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
            <svg class="spark" viewBox="0 0 88 28" preserveAspectRatio="none">
              <path
                :d="sparkPath(item.sparkCloses)"
                fill="none"
                :stroke="Number(item.pctChg) >= 0 ? '#e11d48' : '#059669'"
                stroke-width="1.6"
              />
            </svg>
          </button>
        </div>
      </section>
      <section v-if="activeCode" class="chart-panel global-chart">
        <div ref="chartRef" class="chart" />
      </section>
    </template>

    <el-collapse v-if="lastLog" class="log-box">
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
      <p class="share-tip">含灵枢 Apex 品牌与赚钱效应；可复制或下载 PNG 后发微信/社群。</p>
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

.tabs {
  display: inline-flex;
  padding: 3px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.05);
  gap: 2px;
}

.tab {
  border: 0;
  background: transparent;
  padding: 6px 12px;
  border-radius: 8px;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  color: var(--mc-muted);
  cursor: pointer;
}

.tab.on {
  background: #fff;
  color: var(--mc-ink);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}

.mobile-action-label {
  display: none;
}

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
  grid-template-columns: repeat(5, minmax(0, 1fr));
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
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 0.9fr);
  gap: 12px;
  margin-bottom: 12px;
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

.side-panels {
  display: flex;
  flex-direction: column;
  gap: 12px;
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
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--mc-muted);
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.themes span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #f1f5f9;
  color: var(--mc-ink);
}

.themes .theme-name {
  color: var(--mc-ink);
}

.themes .theme-pct {
  display: inline-flex;
  align-items: center;
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'tnum' 1;
  letter-spacing: 0;
  line-height: 1;
  white-space: nowrap;
}

.themes .theme-sign {
  display: inline-block;
  transform: translateY(-0.14em);
  line-height: 1;
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
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 700;
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
}

@media (max-width: 980px) {
  .pulse {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
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

  .cards {
    grid-template-columns: 1fr 1fr;
  }
  .pulse-width {
    grid-template-columns: 1fr;
  }
  .pulse-tile + .pulse-tile::before {
    display: none;
  }
  .pulse-effect-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
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
