<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowRight, MoreFilled } from '@element-plus/icons-vue'
import { saveObserve } from '../api/observe'
import { searchStock } from '../api/stock'
import {
  importPortfolioHoldings,
  listPortfolioDaily,
  listPortfolios,
  portfolioDetail,
  refreshPortfolioQuotes,
  refreshAllPortfolioQuotes,
  removePortfolio,
  removePortfolioHolding,
  savePortfolio,
  savePortfolioHolding,
  snapshotAllPortfolios,
  snapshotPortfolio,
} from '../api/portfolio'
import {
  HOLDING_SHARE_WIDTH,
  buildHoldingShareSheet,
  mountHoldingShareSheet,
} from '../utils/holdingShareSheet.js'
import {
  PORTFOLIO_TODAY_SHARE_WIDTH,
  buildPortfolioTodayShareSheet,
  mountPortfolioTodayShareSheet,
} from '../utils/portfolioTodayShareSheet.js'
import {
  captureElementBlob,
  copyImageBlob,
  downloadBlob,
  freezeCanvasesForCapture,
  prepareLongCapture,
  resetScrollForCapture,
  shareFilename,
} from '../utils/shareCapture.js'
import BrandShareLockup from '../components/share/BrandShareLockup.vue'
import BrandShareFoot from '../components/share/BrandShareFoot.vue'
import { securityMarketBadge } from '../utils/securityMarket.js'
import { availablePeMetrics } from '../utils/valuationMetrics.js'
import { resolveActionColumnVisible } from '../utils/responsiveTable.js'
import FloatingShareButton from '../components/FloatingShareButton.vue'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const detailLoading = ref(false)
const refreshing = ref(false)
const list = ref([])
const includeArchived = ref(false)
const activeId = ref(null)
const detail = ref(null)
const rows = ref([])
const dailyRows = ref([])
const chartRef = ref(null)
const industryPieRef = ref(null)
const themePieRef = ref(null)
const viewportWidth = ref(window.innerWidth)
const isMobileViewport = computed(() => viewportWidth.value <= 820)
const mobileDetailOpen = computed(() => {
  const portfolioId = Number(route.query.portfolio)
  return isMobileViewport.value && Number.isFinite(portfolioId) && portfolioId > 0
})
const showActionColumn = computed(() => resolveActionColumnVisible(viewportWidth.value))
let chart = null
let industryChart = null
let themeChart = null

const showIndustry = ref(false)
const pfDialog = ref(false)
const dialogVisible = ref(false)
const importDialog = ref(false)
const searchLoading = ref(false)
const searchOptions = ref([])
const saving = ref(false)
const snapshotting = ref(false)

const sharing = ref(false)
const shareOpen = ref(false)
const sharePreviewUrl = ref('')
/** 长图预览按截取逻辑宽度显示，避免 width:100% 把图拉大发糊 */
const sharePreviewLogicalWidth = ref(0)
const shareMode = ref('card')
const copying = ref(false)
const downloading = ref(false)
let sharePreviewObjectUrl = ''
/** 多选组合：今日战绩拼图 */
const selectedIds = ref([])

const selectedPortfolios = computed(() => {
  const set = new Set(selectedIds.value.map(Number))
  return (list.value || []).filter((x) => set.has(Number(x.id)))
})

function isSelected(id) {
  return selectedIds.value.some((x) => Number(x) === Number(id))
}

function toggleSelect(id, checked) {
  const nid = Number(id)
  if (checked) {
    if (!isSelected(nid)) selectedIds.value = [...selectedIds.value, nid]
  } else {
    selectedIds.value = selectedIds.value.filter((x) => Number(x) !== nid)
  }
}

function toggleSelectAll(checked) {
  if (checked) {
    selectedIds.value = (list.value || []).map((x) => Number(x.id))
  } else {
    selectedIds.value = []
  }
}

const SIDE_COLLAPSE_KEY = 'apex.portfolio.sideCollapsed'
const sideCollapsed = ref(localStorage.getItem(SIDE_COLLAPSE_KEY) === '1')
const detailCaptureRef = ref(null)
const holdingTableRef = ref(null)
/** 长图截取中：用真实列宽重排表格，避免 CSS 硬改 colgroup 导致标签被挤爆 */
const sharingCapture = ref(false)

/** 分享长图列宽合计约 1520，保证题材/技术/估值/建议可读 */
const SHARE_TABLE_MIN_WIDTH = 1520

const shareCol = computed(() =>
  sharingCapture.value
    ? {
        security: 128,
        today: 88,
        price: 76,
        stop: 76,
        theme: 112,
        tech: 340,
        val: 176,
        verdict: 80,
        advice: 280,
      }
    : {
        security: 128,
        today: 132,
        priceCost: 116,
        stop: 84,
        take: 84,
        mv: 96,
        pnl: 132,
        weight: 112,
        theme: 108,
        tech: 220,
        val: 176,
        verdict: 80,
        advice: 210,
        qty: 88,
        cost: 84,
        note: 100,
        ops: 168,
      },
)

function toggleSide() {
  sideCollapsed.value = !sideCollapsed.value
  localStorage.setItem(SIDE_COLLAPSE_KEY, sideCollapsed.value ? '1' : '0')
  nextTick(() => onResize())
}

function displayTechHits(row) {
  const hits = hitTech(row)
  if (!sharingCapture.value) return hits
  // 长图只留前 3 个命中，避免多标签把行撑乱
  return hits.slice(0, 3)
}

const CORE_THEME_META = {
  '光模块(CPO)': {
    short: '光模块',
    color: '#c43d4a',
    bg: 'rgba(255, 59, 48, 0.10)',
    border: 'rgba(255, 59, 48, 0.22)',
    chart: '#ff6b6b',
  },
  存储芯片: {
    short: '存储',
    color: '#1f8a4c',
    bg: 'rgba(52, 199, 89, 0.12)',
    border: 'rgba(52, 199, 89, 0.24)',
    chart: '#34c759',
  },
  '数据中心(IDC)': {
    short: 'IDC',
    color: '#0a66c2',
    bg: 'rgba(0, 113, 227, 0.10)',
    border: 'rgba(0, 113, 227, 0.22)',
    chart: '#0071e3',
  },
  算力: {
    short: '算力',
    color: '#b36b00',
    bg: 'rgba(255, 159, 10, 0.14)',
    border: 'rgba(255, 159, 10, 0.28)',
    chart: '#ff9f0a',
  },
  锂电: {
    short: '锂电',
    color: '#6b4fbb',
    bg: 'rgba(94, 92, 230, 0.12)',
    border: 'rgba(94, 92, 230, 0.24)',
    chart: '#5e5ce6',
  },
}

const pfForm = reactive({
  id: null,
  name: '',
  ownerLabel: '',
  note: '',
  status: 'ACTIVE',
})

const form = reactive({
  id: null,
  code: '',
  name: '',
  quantity: 100,
  costPrice: '',
  stopLoss: '',
  takeProfit: '',
  note: '',
})

const importText = ref('')

const activeSummary = computed(() => {
  if (!activeId.value) return null
  return list.value.find((x) => x.id === activeId.value) || detail.value
})

function themeMeta(name) {
  return (
    CORE_THEME_META[name] || {
      short: name,
      color: '#515154',
      bg: 'rgba(0, 0, 0, 0.04)',
      border: 'rgba(0, 0, 0, 0.08)',
      chart: '#8e8e93',
    }
  )
}

function isCoreTheme(name) {
  return Object.prototype.hasOwnProperty.call(CORE_THEME_META, name)
}

/** 非核心题材时优先用二级行业，避免概念里弱 AI 标签抢展示 */
const SOFT_THEME_SKIP =
  /融资融券|深股通|沪股通|HS300|中证|创业|基金重仓|股权激励|证金|富时|MSCI|中盘|预增|AH股|深成|上证|机构重仓|央视|专精特新|深圳特区|西部大开发|海南|股权转让|人工智能|AI应用|DeepSeek|ChatGPT|融资|回购/

function softTheme(row) {
  const industry = String(row?.industry || '').trim()
  // ETF 本身就是题材口径，勿跳过导致分享里变「未分类 / —」
  if (industry === 'ETF') return 'ETF'
  if (industry && industry !== '未分类') {
    return industry.replace(/[ⅡI]+$/u, '')
  }
  const concepts = String(row?.concepts || '').split(/[,，、;；|/]/)
  for (const raw of concepts) {
    const text = String(raw || '').trim()
    if (!text || SOFT_THEME_SKIP.test(text)) continue
    return text
  }
  const code = String(row?.code || '')
  if (/^(15|16|51|56|58)\d{4}$/.test(code) || /ETF|基金|LOF/i.test(String(row?.name || ''))) {
    return 'ETF'
  }
  return ''
}

function primaryTheme(row) {
  const tags = Array.isArray(row?.themeTags) ? row.themeTags.filter(isCoreTheme) : []
  return tags[0] || ''
}

function displayTheme(row) {
  // 有可信核心题材（如润泽→IDC）优先展示；否则用二级行业，避免概念板误挂
  return primaryTheme(row) || softTheme(row)
}

function isSoftOnlyTheme(row) {
  return !primaryTheme(row) && !!softTheme(row)
}

function hitTech(row) {
  return (Array.isArray(row?.techSignals) ? row.techSignals : []).filter((s) => s && s.hit)
}

function todayPnlTone(row) {
  const n = row.todayPnl != null ? Number(row.todayPnl) : Number(row.pctChg)
  if (!Number.isFinite(n)) return ''
  return n >= 0 ? 'up' : 'down'
}

function shortTechLabel(label) {
  const raw = String(label || '')
  return raw
    .replace(/^站上/, '')
    .replace(/^站稳/, '稳')
    .replace(/^跌破/, '')
    .replace(/金叉\/多/, '金叉')
    .replace(/死叉\/空/, '死叉')
    .replace(/红柱放大/, '红柱')
    .replace(/绿柱扩大/, '绿柱')
    .replace(/放量确认/, '放量')
    .replace(/RSI健康/, 'RSI')
    .replace(/RSI转弱/, 'RSI弱')
    .replace(/近20日高/, '新高')
    .replace(/破20日低/, '破低')
    .replace(/多头排列/, '多头')
    .replace(/空头排列/, '空头')
}

function valClass(level) {
  if (level === 'UNDERVALUED' || level === 'SLIGHTLY_CHEAP') return 'cheap'
  if (level === 'OVERVALUED' || level === 'SLIGHTLY_EXPENSIVE') return 'rich'
  return 'fair'
}

function verdictClass(verdict) {
  const v = String(verdict || '')
  if (v.includes('卖出') || v.includes('减仓')) return 'warn'
  if (v.includes('偏多') || v.includes('继续')) return 'ok'
  if (v.includes('谨慎') || v.includes('不足')) return 'soft'
  return ''
}

function fmtSignedPct(pct) {
  if (pct == null || !Number.isFinite(Number(pct))) return ''
  const n = Number(pct)
  return `${n > 0 ? '+' : ''}${n.toFixed(2)}%`
}

function fmtPct(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  if (Math.abs(n) <= 1) return `${(n * 100).toFixed(1)}%`
  return `${n.toFixed(1)}%`
}

function fmtMoney(v) {
  if (v == null || !Number.isFinite(Number(v))) return '-'
  return Math.round(Number(v)).toLocaleString('zh-CN')
}

function fmtSignedMoney(v) {
  if (v == null || !Number.isFinite(Number(v))) return '-'
  const n = Math.round(Number(v))
  const abs = Math.abs(n).toLocaleString('zh-CN')
  if (n > 0) return `+${abs}`
  if (n < 0) return `-${abs}`
  return '0'
}

function rowWeight(row) {
  const mv = Number(row.marketValue)
  const costMv = Number(row.costPrice) * Number(row.quantity || 0)
  if (Number.isFinite(mv) && mv > 0) return mv
  if (Number.isFinite(costMv) && costMv > 0) return costMv
  return 0
}

function fmtPrice(v) {
  if (v == null || !Number.isFinite(Number(v))) return '-'
  return Number(v).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 3,
  })
}

function positionWeightPct(row) {
  return totalMv.value > 0 ? (rowWeight(row) / totalMv.value) * 100 : 0
}

function positionWeightLabel(row) {
  const pct = positionWeightPct(row)
  if (pct >= 20) return '重仓'
  if (pct >= 10) return '核心'
  if (pct >= 5) return '配置'
  return '轻仓'
}

function positionWeightTone(row) {
  const pct = positionWeightPct(row)
  if (pct >= 20) return 'is-heavy'
  if (pct >= 10) return 'is-core'
  if (pct >= 5) return 'is-normal'
  return 'is-light'
}

function comparePositionWeight(a, b) {
  return rowWeight(a) - rowWeight(b)
}

function toDist(map) {
  const list = [...map.entries()]
    .map(([name, value]) => ({ name, value: Math.round(value * 100) / 100 }))
    .sort((a, b) => b.value - a.value)
  const sum = list.reduce((s, x) => s + x.value, 0)
  return list.map((x) => ({
    ...x,
    pct: sum > 0 ? x.value / sum : 0,
  }))
}

const totalPnl = computed(() => rows.value.reduce((sum, r) => sum + (Number(r.pnl) || 0), 0))
const totalTodayPnl = computed(() => rows.value.reduce((sum, r) => sum + (Number(r.todayPnl) || 0), 0))
const hasTodayPnl = computed(() =>
  rows.value.some((r) => r.todayPnl != null && Number.isFinite(Number(r.todayPnl))),
)
const totalMv = computed(() => rows.value.reduce((sum, r) => sum + (Number(r.marketValue) || 0), 0))
const totalTodayPct = computed(() => {
  if (!hasTodayPnl.value) return null
  const preMv = totalMv.value - totalTodayPnl.value
  if (!Number.isFinite(preMv) || Math.abs(preMv) < 1e-6) return null
  return (totalTodayPnl.value / preMv) * 100
})

const industryDist = computed(() => {
  const map = new Map()
  for (const row of rows.value) {
    const name = String(row.industry || '').trim() || '未分类'
    const value = rowWeight(row)
    if (value <= 0) continue
    map.set(name, (map.get(name) || 0) + value)
  }
  return toDist(map)
})

/** 题材分布：与表格「题材」列同一口径（行业优先），不再用被概念污染的 themeTags 饼图 */
const themeDist = computed(() => {
  const map = new Map()
  for (const row of rows.value) {
    const value = rowWeight(row)
    if (value <= 0) continue
    const name = displayTheme(row) || '未分类'
    map.set(name, (map.get(name) || 0) + value)
  }
  return toDist(map)
})

const themeTagBar = computed(() => [...themeDist.value].slice(0, 12))
const themeHitCount = computed(() => rows.value.filter((r) => !!displayTheme(r)).length)

const DIST_PALETTE = [
  '#0071e3',
  '#1f8a4c',
  '#c43d4a',
  '#b36b00',
  '#6b4fbb',
  '#0a66c2',
  '#d4537e',
  '#2a9d8f',
  '#e76f51',
  '#457b9d',
  '#8e8e93',
  '#5ac8fa',
]

function distColor(name, index) {
  const meta = CORE_THEME_META[name]
  if (meta?.chart) return meta.chart
  return DIST_PALETTE[index % DIST_PALETTE.length]
}

/** 饼图过多扇区时合并尾部，避免标签叠字 */
function compactDist(dist, maxSlices = 7) {
  if (!dist?.length || dist.length <= maxSlices) return dist || []
  const head = dist.slice(0, maxSlices - 1)
  const tail = dist.slice(maxSlices - 1)
  const otherValue = tail.reduce((s, x) => s + (Number(x.value) || 0), 0)
  const sum = dist.reduce((s, x) => s + (Number(x.value) || 0), 0)
  return [
    ...head,
    {
      name: '其他',
      value: Math.round(otherValue * 100) / 100,
      pct: sum > 0 ? otherValue / sum : 0,
    },
  ]
}

const brief = computed(() => detail.value?.brief || null)

function tipLevelClass(level) {
  const v = String(level || '')
  if (v === 'critical') return 'is-critical'
  if (v === 'warn') return 'is-warn'
  return 'is-info'
}

function tipLevelLabel(level) {
  const v = String(level || '')
  if (v === 'critical') return '紧急'
  if (v === 'warn') return '注意'
  return '提示'
}

async function loadList(silent = false) {
  loading.value = true
  try {
    const res = await listPortfolios(includeArchived.value)
    list.value = res?.data || []
    selectedIds.value = selectedIds.value.filter((id) =>
      list.value.some((x) => Number(x.id) === Number(id)),
    )
    if (!activeId.value && list.value.length) {
      const def = list.value.find((x) => x.isDefault) || list.value[0]
      activeId.value = def.id
    }
    if (activeId.value && !list.value.some((x) => x.id === activeId.value)) {
      activeId.value = list.value[0]?.id || null
    }
    if (activeId.value) await loadDetail(activeId.value, silent)
  } catch (e) {
    ElMessage.error(e.message || '加载组合失败')
  } finally {
    loading.value = false
  }
}

async function loadDetail(id, silent = false) {
  if (!id) {
    detail.value = null
    rows.value = []
    dailyRows.value = []
    renderPies()
    renderDailyChart()
    return
  }
  detailLoading.value = true
  try {
    const [dRes, dayRes] = await Promise.all([
      portfolioDetail(id),
      listPortfolioDaily(id, 60),
    ])
    detail.value = dRes?.data || null
    rows.value = detail.value?.holdings || []
    dailyRows.value = (dayRes?.data || []).slice().reverse()
    if (!silent) {
      // keep quiet on switch
    }
    await nextTick()
    renderPies()
    renderDailyChart()
  } catch (e) {
    ElMessage.error(e.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function selectPortfolio(row) {
  activeId.value = row.id
  if (isMobileViewport.value) {
    await router.push({
      path: route.path,
      query: { ...route.query, portfolio: String(row.id) },
    })
    await nextTick()
    window.scrollTo({ top: 0, behavior: 'auto' })
  }
}

async function closeMobileDetail() {
  const query = { ...route.query }
  delete query.portfolio
  await router.replace({ path: route.path, query })
}

function handleMobileListAction(command) {
  if (command === 'today-share') {
    selectedIds.value = (list.value || []).map((row) => Number(row.id))
    nextTick(() => openShare('today'))
    return
  }
  if (command === 'refresh-all') {
    onRefreshQuotesAll()
    return
  }
  if (command === 'snapshot-all') {
    onSnapshotAll()
    return
  }
  if (command === 'holding') {
    router.push('/holding')
    return
  }
  if (command === 'refresh-list') loadList(true)
}

function handleMobileDetailAction(command) {
  if (command === 'edit') {
    if (activeSummary.value) openEditPf(activeSummary.value)
    return
  }
  if (command === 'remove') {
    if (activeSummary.value) onRemovePf(activeSummary.value)
    return
  }
  if (command === 'refresh') {
    onRefreshQuotes()
    return
  }
  if (command === 'import') {
    openImport()
    return
  }
  if (command === 'snapshot') {
    onSnapshot()
    return
  }
  if (command === 'holding') router.push('/holding')
}

watch(activeId, (id) => {
  if (id) loadDetail(id)
})

watch(
  () => route.query.portfolio,
  (id) => {
    const portfolioId = Number(id)
    if (isMobileViewport.value && Number.isFinite(portfolioId) && portfolioId > 0) {
      activeId.value = portfolioId
    }
  },
  { immediate: true },
)

watch(includeArchived, () => loadList(true))

watch(showIndustry, async () => {
  await nextTick()
  renderPies()
})

watch([industryDist, themeDist], async () => {
  await nextTick()
  renderPies()
})

function renderPies() {
  const pieOpt = (dist, colors) => ({
    tooltip: {
      trigger: 'item',
      formatter: (p) => `${p.name}<br/>${fmtMoney(p.value)} · ${(p.percent || 0).toFixed(1)}%`,
    },
    series: [
      {
        type: 'pie',
        radius: ['46%', '72%'],
        center: ['50%', '50%'],
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        // 右侧已有题材条，饼图只作色块占比，去掉外标避免左上角叠字
        label: { show: false },
        labelLine: { show: false },
        data: dist.map((x, i) => ({
          name: isCoreTheme(x.name) ? themeMeta(x.name).short : x.name,
          value: x.value,
          itemStyle: { color: colors[i % colors.length] },
        })),
      },
    ],
  })

  if (showIndustry.value && industryPieRef.value && industryDist.value.length) {
    if (!industryChart) industryChart = echarts.init(industryPieRef.value)
    industryChart.setOption(
      pieOpt(
        industryDist.value,
        ['#5ac8fa', '#0071e3', '#34c759', '#ff9f0a', '#af52de', '#ff6b6b', '#8e8e93'],
      ),
      true,
    )
  } else {
    industryChart?.clear()
  }

  if (themePieRef.value && themeDist.value.length) {
    if (!themeChart) themeChart = echarts.init(themePieRef.value)
    const pieData = compactDist(themeDist.value)
    const colors = pieData.map((x, i) => distColor(x.name, i))
    themeChart.setOption(pieOpt(pieData, colors), true)
  } else {
    themeChart?.clear()
  }
}

function renderDailyChart() {
  if (!dailyRows.value.length) {
    chart?.clear()
    return
  }
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const dates = dailyRows.value.map((x) => x.tradeDate)
  const pcts = dailyRows.value.map((x) => (x.todayPct != null ? Number(x.todayPct) : null))
  const mvs = dailyRows.value.map((x) => (x.marketValue != null ? Number(x.marketValue) : null))
  chart.setOption(
    {
      color: ['#c23a3a', '#0071e3'],
      tooltip: { trigger: 'axis' },
      legend: { data: ['当日涨跌%', '市值'], top: 0 },
      grid: { left: 48, right: 56, top: 36, bottom: 28 },
      xAxis: { type: 'category', data: dates, axisLabel: { color: '#86868b' } },
      yAxis: [
        {
          type: 'value',
          name: '%',
          axisLabel: { color: '#86868b' },
          splitLine: { lineStyle: { color: '#eee' } },
        },
        { type: 'value', name: '市值', axisLabel: { color: '#86868b' }, splitLine: { show: false } },
      ],
      series: [
        { name: '当日涨跌%', type: 'bar', data: pcts, barMaxWidth: 18 },
        { name: '市值', type: 'line', yAxisIndex: 1, smooth: true, data: mvs, showSymbol: false },
      ],
    },
    true,
  )
}

function openCreatePf() {
  Object.assign(pfForm, { id: null, name: '', ownerLabel: '', note: '', status: 'ACTIVE' })
  pfDialog.value = true
}

function openEditPf(row) {
  Object.assign(pfForm, {
    id: row.id,
    name: row.name || '',
    ownerLabel: row.ownerLabel || '',
    note: row.note || '',
    status: row.status || 'ACTIVE',
  })
  pfDialog.value = true
}

async function submitPf() {
  if (!pfForm.name?.trim()) {
    ElMessage.warning('请填写组合名称')
    return
  }
  saving.value = true
  try {
    const res = await savePortfolio({ ...pfForm })
    ElMessage.success(pfForm.id ? '已更新' : '已创建')
    pfDialog.value = false
    await loadList(true)
    if (res?.data?.id) activeId.value = res.data.id
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onRemovePf(row) {
  if (row.isDefault) {
    ElMessage.warning('默认组合不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除组合「${row.name}」？持仓与快照一并清除。`, '删除组合', {
      type: 'warning',
    })
    await removePortfolio(row.id)
    ElMessage.success('已删除')
    if (activeId.value === row.id) activeId.value = null
    await loadList(true)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

function openCreate() {
  if (!activeId.value) return
  Object.assign(form, {
    id: null,
    code: '',
    name: '',
    quantity: 100,
    costPrice: '',
    stopLoss: '',
    takeProfit: '',
    note: '',
  })
  searchOptions.value = []
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    code: row.code,
    name: row.name || '',
    quantity: row.quantity,
    costPrice: row.costPrice ?? '',
    stopLoss: row.stopLoss ?? '',
    takeProfit: row.takeProfit ?? '',
    note: row.note || '',
  })
  searchOptions.value = row.code ? [{ code: row.code, name: row.name || row.code }] : []
  dialogVisible.value = true
}

async function onSearchStock(q) {
  const keyword = String(q || '').trim()
  if (!keyword) {
    searchOptions.value = []
    return
  }
  searchLoading.value = true
  try {
    const res = await searchStock(keyword)
    searchOptions.value = res?.data || []
  } catch {
    searchOptions.value = []
  } finally {
    searchLoading.value = false
  }
}

function onPickStock(code) {
  const hit = searchOptions.value.find((x) => x.code === code)
  if (hit) form.name = hit.name || form.name
}

async function onSave() {
  if (!activeId.value || !form.code) {
    ElMessage.warning('请填写代码')
    return
  }
  saving.value = true
  try {
    await savePortfolioHolding(activeId.value, {
      id: form.id,
      code: form.code,
      name: form.name,
      quantity: Number(form.quantity || 0),
      costPrice: form.costPrice === '' || form.costPrice == null ? null : Number(form.costPrice),
      stopLoss: form.stopLoss === '' || form.stopLoss == null ? null : Number(form.stopLoss),
      takeProfit: form.takeProfit === '' || form.takeProfit == null ? null : Number(form.takeProfit),
      note: form.note,
    })
    ElMessage.success('持仓已保存')
    dialogVisible.value = false
    await loadList(true)
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onRemove(row) {
  try {
    await ElMessageBox.confirm(`删除 ${row.code} ${row.name || ''}？`, '删除持仓', { type: 'warning' })
    await removePortfolioHolding(activeId.value, row.id)
    ElMessage.success('已删除')
    await loadList(true)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

async function addObserve(row) {
  try {
    await saveObserve({
      code: row.code,
      name: row.name,
      side: 'SELL',
      reason: '组合持仓观察',
      triggerType: 'MANUAL',
      priority: 3,
      tags: '组合,持仓',
    })
    ElMessage.success('已加入观察池')
  } catch (e) {
    ElMessage.error(e.message || '加入观察池失败')
  }
}

function openImport() {
  importText.value = ''
  importDialog.value = true
}

async function submitImport() {
  if (!activeId.value || !importText.value.trim()) {
    ElMessage.warning('请粘贴导入内容')
    return
  }
  saving.value = true
  try {
    const res = await importPortfolioHoldings(activeId.value, importText.value)
    const data = res?.data || {}
    ElMessage.success(`导入完成：成功 ${data.success || 0}，失败 ${data.fail || 0}`)
    if (data.errors?.length) ElMessage.warning(data.errors.slice(0, 3).join('；'))
    importDialog.value = false
    await loadList(true)
  } catch (e) {
    ElMessage.error(e.message || '导入失败')
  } finally {
    saving.value = false
  }
}

async function onRefreshQuotes() {
  if (!activeId.value || !rows.value.length) return
  refreshing.value = true
  try {
    const res = await refreshPortfolioQuotes(activeId.value, false)
    const next = res?.data?.detail
    if (next) {
      detail.value = next
      rows.value = next.holdings || []
      const dayRes = await listPortfolioDaily(activeId.value, 60)
      dailyRows.value = (dayRes?.data || []).slice().reverse()
      await nextTick()
      renderPies()
      renderDailyChart()
    } else {
      await loadDetail(activeId.value, true)
    }
    ElMessage.success(res?.data?.message || '行情已刷新')
  } catch (e) {
    ElMessage.error(e.message || '刷新失败')
  } finally {
    refreshing.value = false
  }
}

async function onRefreshQuotesAll() {
  if (!list.value.length) {
    ElMessage.warning('暂无组合')
    return
  }
  refreshing.value = true
  try {
    const res = await refreshAllPortfolioQuotes(false)
    ElMessage.success(res?.data?.message || '全部组合行情已刷新')
    await loadList(true)
    if (activeId.value) await loadDetail(activeId.value, true)
  } catch (e) {
    ElMessage.error(e.message || '全部刷新失败')
  } finally {
    refreshing.value = false
  }
}

async function onSnapshot() {
  if (!activeId.value) return
  snapshotting.value = true
  try {
    await snapshotPortfolio(activeId.value)
    ElMessage.success('已写入今日快照')
    await loadDetail(activeId.value, true)
  } catch (e) {
    ElMessage.error(e.message || '快照失败')
  } finally {
    snapshotting.value = false
  }
}

async function onSnapshotAll() {
  snapshotting.value = true
  try {
    const res = await snapshotAllPortfolios()
    ElMessage.success(res?.data?.message || '已全部快照')
    if (activeId.value) await loadDetail(activeId.value, true)
  } catch (e) {
    ElMessage.error(e.message || '快照失败')
  } finally {
    snapshotting.value = false
  }
}

function revokeSharePreview() {
  if (sharePreviewObjectUrl) {
    URL.revokeObjectURL(sharePreviewObjectUrl)
    sharePreviewObjectUrl = ''
  }
  sharePreviewUrl.value = ''
  sharePreviewLogicalWidth.value = 0
}

function shareRowsPayload() {
  const total = rows.value.reduce((sum, row) => sum + rowWeight(row), 0)
  const listRows = rows.value.map((row) => {
    const w = rowWeight(row)
    const weightPct = total > 0 ? (w / total) * 100 : 0
    const theme = displayTheme(row)
    const meta = theme && isCoreTheme(theme) ? themeMeta(theme) : null
    const techHits = hitTech(row).map((s) => shortTechLabel(s.label)).filter(Boolean)
    const tech = row.techSummary || techHits.slice(0, 3).join(' ') || ''
    return {
      code: row.code,
      name: row.name,
      quantity: row.quantity,
      weightPct,
      pctChg: row.pctChg,
      theme: meta?.short || theme || '',
      themeColor: meta?.color || '#515154',
      themeBg: meta?.bg || 'rgba(0,0,0,.04)',
      themeBorder: meta?.border || 'rgba(0,0,0,.08)',
      tech,
      techHits: techHits.slice(0, 4),
      valuation: row.valuationLabel || '',
      valuationLevel: row.valuationLevel || '',
      verdict: row.verdict || '',
      advice: row.advice || '',
    }
  })
  listRows.sort((a, b) => (b.weightPct || 0) - (a.weightPct || 0))
  return listRows
}

async function captureCardShareBlob() {
  const titleDate = new Date().toISOString().slice(0, 10)
  const sheet = buildHoldingShareSheet({
    titleDate,
    count: rows.value.length,
    todayPct: totalTodayPct.value,
    themeHitCount: themeHitCount.value,
    themes: themeDist.value.map((t, i) => {
      const meta = themeMeta(t.name)
      return {
        name: t.name,
        short: meta.short || t.name,
        pct: t.pct,
        color: distColor(t.name, i),
        bg: meta.bg,
      }
    }),
    otherPct: 0,
    rows: shareRowsPayload(),
  })
  const mounted = mountHoldingShareSheet(sheet)
  try {
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))
    const width = HOLDING_SHARE_WIDTH
    const height = Math.max(sheet.scrollHeight, sheet.offsetHeight, 1)
    sheet.style.width = `${width}px`
    sheet.style.height = `${height}px`
    const dpr = Math.max(window.devicePixelRatio || 1, 2)
    return await captureElementBlob(sheet, {
      scale: Math.min(dpr, 2),
      width,
      height,
      backgroundColor: '#f7f4ee',
      style: {
        width: `${width}px`,
        height: `${height}px`,
        overflow: 'visible',
        transform: 'none',
        margin: '0',
        opacity: '1',
        fontFamily: '"Microsoft YaHei","PingFang SC","Noto Sans SC",sans-serif',
        letterSpacing: '0',
      },
    })
  } finally {
    mounted.dispose()
  }
}

/**
 * 原样截右侧组合详情（研判+题材+持仓表+曲线），清晰长图
 */
async function capturePageShareBlob() {
  const el = detailCaptureRef.value
  if (!el) throw new Error('组合详情未就绪')
  const restoreScroll = resetScrollForCapture(el)
  sharingCapture.value = true
  el.classList.add('is-sharing-capture')
  let restoreLayout = () => {}
  let restoreCanvas = () => {}
  try {
    el.scrollIntoView({ block: 'start', behavior: 'auto' })
    // 先按分享列宽重排，再展开滚动裁剪，避免 colgroup 与 CSS 抢宽度
    await nextTick()
    await nextTick()
    holdingTableRef.value?.doLayout?.()
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))
    await new Promise((r) => setTimeout(r, 120))
    holdingTableRef.value?.doLayout?.()
    restoreLayout = prepareLongCapture(el, { minTableWidth: SHARE_TABLE_MIN_WIDTH })
    onResize()
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))
    await new Promise((r) => setTimeout(r, 160))
    holdingTableRef.value?.doLayout?.()
    await new Promise((r) => requestAnimationFrame(r))
    restoreCanvas = freezeCanvasesForCapture(el)
    await new Promise((r) => requestAnimationFrame(r))
    const width = Math.ceil(Math.max(el.scrollWidth, el.offsetWidth, SHARE_TABLE_MIN_WIDTH))
    const height = Math.ceil(Math.max(el.scrollHeight, el.offsetHeight, 1))
    sharePreviewLogicalWidth.value = width
    return await captureElementBlob(el, {
      scale: 2,
      width,
      height,
      backgroundColor: '#f5f5f7',
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
    restoreCanvas()
    restoreLayout()
    el.classList.remove('is-sharing-capture')
    sharingCapture.value = false
    restoreScroll()
    await nextTick()
    holdingTableRef.value?.doLayout?.()
    onResize()
  }
}

async function captureTodayBattleShareBlob() {
  const picks = selectedPortfolios.value
  if (!picks.length) throw new Error('请先勾选要分享的组合')
  const titleDate = new Date().toISOString().slice(0, 10)
  const sheet = buildPortfolioTodayShareSheet({
    titleDate,
    portfolios: picks.map((p) => ({
      id: p.id,
      name: p.name,
      positionCount: p.positionCount,
      todayPct: p.todayPct,
      todayPnl: p.todayPnl,
      topHoldings: (p.topHoldings || []).map((h) => ({
        code: h.code,
        name: h.name,
        weightPct: h.weightPct,
        pctChg: h.pctChg,
      })),
    })),
  })
  const mounted = mountPortfolioTodayShareSheet(sheet)
  try {
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))
    const width = PORTFOLIO_TODAY_SHARE_WIDTH
    const height = Math.max(sheet.scrollHeight, sheet.offsetHeight, 1)
    sheet.style.width = `${width}px`
    sheet.style.height = `${height}px`
    const dpr = Math.max(window.devicePixelRatio || 1, 2)
    sharePreviewLogicalWidth.value = width
    return await captureElementBlob(sheet, {
      scale: Math.min(dpr, 2),
      width,
      height,
      backgroundColor: '#f7f9fc',
      style: {
        width: `${width}px`,
        height: `${height}px`,
        overflow: 'visible',
        transform: 'none',
        margin: '0',
        opacity: '1',
        padding: '0',
        boxSizing: 'border-box',
        fontFamily: '"Microsoft YaHei","PingFang SC","Noto Sans SC",sans-serif',
      },
    })
  } finally {
    mounted.dispose()
  }
}

async function openShare(mode = 'card') {
  const nextMode = mode === 'page' ? 'page' : mode === 'today' ? 'today' : 'card'
  if (nextMode === 'today') {
    if (!selectedPortfolios.value.length) {
      ElMessage.warning('请先在左侧勾选要对比的组合')
      return
    }
  } else if (!rows.value.length) {
    return
  }
  shareMode.value = nextMode
  sharing.value = true
  try {
    const blob =
      nextMode === 'page'
        ? await capturePageShareBlob()
        : nextMode === 'today'
          ? await captureTodayBattleShareBlob()
          : await captureCardShareBlob()
    revokeSharePreview()
    sharePreviewObjectUrl = URL.createObjectURL(blob)
    sharePreviewUrl.value = sharePreviewObjectUrl
    shareOpen.value = true
    await nextTick()
    const stage = document.querySelector('.holding-share-dialog .share-stage')
    if (stage) stage.scrollTop = 0
  } catch (e) {
    ElMessage.error(e.message || '生成分享图失败')
  } finally {
    sharing.value = false
  }
}

function closeShare() {
  shareOpen.value = false
  revokeSharePreview()
}

async function onCopyShare() {
  if (!sharePreviewObjectUrl) return
  copying.value = true
  try {
    await copyImageBlob(fetch(sharePreviewObjectUrl).then((r) => r.blob()))
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.error(e.message || '复制失败')
  } finally {
    copying.value = false
  }
}

async function onDownloadShare() {
  if (!sharePreviewObjectUrl) return
  downloading.value = true
  try {
    const blob = await fetch(sharePreviewObjectUrl).then((r) => r.blob())
    const name = detail.value?.name || '组合'
    const prefix =
      shareMode.value === 'page'
        ? `apex-portfolio-page-${name}`
        : shareMode.value === 'today'
          ? `apex-portfolio-today-${selectedIds.value.length}组`
          : `apex-portfolio-${name}`
    downloadBlob(blob, shareFilename(prefix))
  } catch (e) {
    ElMessage.error(e.message || '下载失败')
  } finally {
    downloading.value = false
  }
}

function onResize() {
  viewportWidth.value = window.innerWidth
  chart?.resize()
  industryChart?.resize()
  themeChart?.resize()
}

onMounted(async () => {
  await loadList(true)
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  revokeSharePreview()
  chart?.dispose()
  industryChart?.dispose()
  themeChart?.dispose()
  chart = null
  industryChart = null
  themeChart = null
})
</script>

<template>
  <div
    class="page portfolio-page"
    :class="{ 'mobile-detail-open': mobileDetailOpen }"
    v-loading="loading || detailLoading || refreshing"
  >
    <header v-if="!mobileDetailOpen" class="header portfolio-header">
      <div>
        <p class="eyebrow">灵枢 · Portfolio</p>
        <h1>组合</h1>
        <p>跟踪自己的或别人的实盘；详情区与「真实持仓」同风格，可导入与每日浮盈快照。</p>
      </div>
      <div class="actions desktop-header-actions">
        <el-button type="primary" @click="openCreatePf">新建组合</el-button>
        <el-button plain :loading="refreshing" @click="onRefreshQuotesAll">刷新全部行情</el-button>
        <el-button plain :loading="snapshotting" @click="onSnapshotAll">全部打快照</el-button>
        <el-button plain @click="router.push('/holding')">真实持仓</el-button>
        <el-button text :loading="loading" @click="loadList(true)">刷新</el-button>
      </div>
      <div class="mobile-header-actions">
        <el-button type="primary" @click="openCreatePf">新建组合</el-button>
        <el-dropdown trigger="click" placement="bottom-end" @command="handleMobileListAction">
          <button type="button" class="portfolio-more-trigger" aria-label="组合更多操作" title="更多操作">
            <el-icon><MoreFilled /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="today-share" :disabled="!list.length">今日战绩拼图</el-dropdown-item>
              <el-dropdown-item command="refresh-all">刷新全部行情</el-dropdown-item>
              <el-dropdown-item command="snapshot-all">全部打快照</el-dropdown-item>
              <el-dropdown-item command="holding" divided>真实持仓</el-dropdown-item>
              <el-dropdown-item command="refresh-list">刷新组合列表</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div class="layout" :class="{ 'is-side-collapsed': sideCollapsed && !isMobileViewport }">
      <aside
        v-if="!isMobileViewport || !mobileDetailOpen"
        class="side"
        :class="{ collapsed: sideCollapsed && !isMobileViewport }"
      >
        <div class="side-head">
          <button v-if="!isMobileViewport" type="button" class="side-toggle" :title="sideCollapsed ? '展开列表' : '折叠列表'" @click="toggleSide">
            {{ sideCollapsed ? '»' : '«' }}
          </button>
          <template v-if="!sideCollapsed || isMobileViewport">
            <span class="side-title">{{ isMobileViewport ? '我的组合' : '组合列表' }}</span>
            <el-checkbox v-model="includeArchived" size="small">含归档</el-checkbox>
          </template>
        </div>
        <template v-if="!sideCollapsed || isMobileViewport">
          <div v-if="!isMobileViewport" class="side-select-bar">
            <el-checkbox
              :model-value="list.length > 0 && selectedIds.length === list.length"
              :indeterminate="selectedIds.length > 0 && selectedIds.length < list.length"
              size="small"
              @change="toggleSelectAll"
            >
              全选
            </el-checkbox>
            <span class="side-select-count">已选 {{ selectedIds.length }}</span>
            <el-button
              link
              type="primary"
              size="small"
              :disabled="!selectedIds.length || sharing"
              :loading="sharing && shareMode === 'today'"
              @click="openShare('today')"
            >
              今日战绩
            </el-button>
          </div>
          <div v-if="!list.length" class="side-empty">暂无组合</div>
          <button
            v-for="row in list"
            :key="row.id"
            type="button"
            class="pf-card"
            :class="{
              active: !isMobileViewport && row.id === activeId,
              archived: row.status === 'ARCHIVED',
              picked: !isMobileViewport && isSelected(row.id),
            }"
            @click="selectPortfolio(row)"
          >
            <div class="pf-top">
              <el-checkbox
                v-if="!isMobileViewport"
                :model-value="isSelected(row.id)"
                @click.stop
                @change="(v) => toggleSelect(row.id, v)"
              />
              <strong>{{ row.name }}</strong>
              <span v-if="row.isDefault" class="tag">默认</span>
              <el-icon v-if="isMobileViewport" class="pf-card-arrow"><ArrowRight /></el-icon>
            </div>
            <div v-if="!isMobileViewport" class="pf-meta">
              <span>{{ row.positionCount || 0 }} 只</span>
            </div>
            <div class="pf-pnl" :class="Number(row.todayPnl) >= 0 ? 'up' : 'down'">
              今日 {{ fmtSignedMoney(row.todayPnl) }}
              <small v-if="row.todayPct != null">{{ fmtSignedPct(row.todayPct) }}</small>
            </div>
            <div v-if="row.topHoldings?.length" class="pf-tops">
              <span v-for="h in row.topHoldings.slice(0, 3)" :key="h.code" class="pf-top-chip">
                {{ h.name || h.code }}
                <em :class="Number(h.pctChg) >= 0 ? 'up' : 'down'">{{ fmtSignedPct(h.pctChg) }}</em>
              </span>
            </div>
            <div v-if="!isMobileViewport" class="pf-ops" @click.stop>
              <el-button link type="primary" @click="openEditPf(row)">编辑</el-button>
              <el-button link type="danger" :disabled="row.isDefault" @click="onRemovePf(row)">删除</el-button>
            </div>
          </button>
        </template>
        <div v-else-if="!isMobileViewport" class="side-rail">
          <button
            v-for="row in list"
            :key="'rail-' + row.id"
            type="button"
            class="rail-item"
            :class="{ active: row.id === activeId }"
            :title="row.name"
            @click="selectPortfolio(row)"
          >
            {{ (row.name || '?').slice(0, 1) }}
          </button>
        </div>
      </aside>

      <main v-if="detail && (!isMobileViewport || mobileDetailOpen)" ref="detailCaptureRef" class="main">
        <div v-if="isMobileViewport && !sharingCapture" class="mobile-detail-nav">
          <button type="button" class="mobile-back-button" @click="closeMobileDetail">
            <el-icon><ArrowLeft /></el-icon>
            <span>组合列表</span>
          </button>
          <el-dropdown trigger="click" placement="bottom-end" @command="handleMobileDetailAction">
            <button type="button" class="portfolio-more-trigger" aria-label="组合详情更多操作" title="更多操作">
              <el-icon><MoreFilled /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">编辑组合</el-dropdown-item>
                <el-dropdown-item command="refresh" :disabled="!rows.length">刷新行情和日线</el-dropdown-item>
                <el-dropdown-item command="import">导入持仓</el-dropdown-item>
                <el-dropdown-item command="snapshot">打今日快照</el-dropdown-item>
                <el-dropdown-item v-if="detail.isDefault" command="holding" divided>打开持仓页</el-dropdown-item>
                <el-dropdown-item v-if="!detail.isDefault" command="remove" divided>删除组合</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div v-show="sharingCapture" class="share-brand-strip">
          <BrandShareLockup :subtitle="detail.isDefault ? '真实持仓' : '组合跟踪'" :size="44" />
        </div>
        <header class="detail-header">
          <div class="detail-title">
            <p class="eyebrow share-hide-meta">{{ detail.isDefault ? '灵枢 · Holding' : '灵枢 · Track' }}</p>
            <h2>{{ detail.name }}</h2>
            <p v-if="detail.ownerLabel || detail.note" class="detail-sub share-hide-meta">
              <template v-if="detail.ownerLabel">{{ detail.ownerLabel }}</template>
              <template v-if="detail.ownerLabel && detail.note"> · </template>
              <template v-if="detail.note">{{ detail.note }}</template>
            </p>
          </div>
          <div class="actions detail-actions">
            <el-button type="primary" @click="openCreate">添加持仓</el-button>
            <el-button plain :loading="refreshing" :disabled="!rows.length" @click="onRefreshQuotes">
              刷新当前行情+日线
            </el-button>
            <el-button plain @click="openImport">导入</el-button>
            <el-button plain :loading="snapshotting" @click="onSnapshot">打今日快照</el-button>
            <el-button v-if="detail.isDefault" plain @click="router.push('/holding')">打开持仓页</el-button>
          </div>
        </header>

        <el-dropdown
          v-if="!shareOpen"
          class="floating-share-dropdown"
          trigger="click"
          :disabled="sharing"
          @command="openShare"
        >
          <FloatingShareButton
            :loading="sharing"
            :disabled="!rows.length && !selectedIds.length"
            label="选择分享方式"
          />
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="today" :disabled="!selectedIds.length">
                今日战绩拼图{{ selectedIds.length ? `（${selectedIds.length}）` : '' }}
              </el-dropdown-item>
              <el-dropdown-item command="card" :disabled="!rows.length">卡片海报</el-dropdown-item>
              <el-dropdown-item command="page" :disabled="!rows.length">右侧原样长图</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <div v-if="rows.length" class="stat-cards" :class="{ 'stat-cards--share': sharingCapture }">
          <div class="stat-card">
            <label>持仓只数</label>
            <b>{{ rows.length }}</b>
          </div>
          <div v-if="!sharingCapture" class="stat-card">
            <label><TermTip term="total_mv">总市值</TermTip></label>
            <b>{{ fmtMoney(totalMv) }}</b>
          </div>
          <div class="stat-card">
            <label>{{ sharingCapture ? '今日涨跌' : '今日盈亏' }}</label>
            <b :class="(sharingCapture ? totalTodayPct : totalTodayPnl) >= 0 ? 'up' : 'down'">
              <template v-if="sharingCapture">
                {{ totalTodayPct != null ? fmtSignedPct(totalTodayPct) : '-' }}
              </template>
              <template v-else-if="hasTodayPnl">
                {{ fmtSignedMoney(totalTodayPnl) }}
                <span v-if="totalTodayPct != null" class="pct-aside">{{ fmtSignedPct(totalTodayPct) }}</span>
              </template>
              <template v-else>-</template>
            </b>
          </div>
          <div v-if="!sharingCapture" class="stat-card">
            <label><TermTip term="unrealized_pnl">持仓盈亏</TermTip></label>
            <b :class="totalPnl >= 0 ? 'up' : 'down'">{{ fmtSignedMoney(totalPnl) }}</b>
          </div>
        </div>

        <section v-if="rows.length && brief" class="brief-panel">
          <div class="brief-head">
            <div class="brief-title-row">
              <h3>组合研判</h3>
              <span class="stance-pill" :class="'stance-' + (brief.stance || '')">{{ brief.stance || '均衡' }}</span>
            </div>
            <p class="brief-summary">{{ brief.summary }}</p>
          </div>
          <div class="brief-thesis">
            <div class="brief-label">思路</div>
            <p>{{ brief.thesis }}</p>
          </div>
          <div class="brief-grid">
            <div class="brief-block">
              <div class="brief-label">操作建议</div>
              <ul class="brief-list">
                <li v-for="(item, idx) in brief.actions || []" :key="'a-' + idx" :class="tipLevelClass(item.level)">
                  <span class="tip-tag">{{ tipLevelLabel(item.level) }}</span>
                  <span class="tip-text">{{ item.text }}</span>
                </li>
              </ul>
            </div>
            <div class="brief-block">
              <div class="brief-label">风险预警</div>
              <ul class="brief-list">
                <li v-for="(item, idx) in brief.risks || []" :key="'r-' + idx" :class="tipLevelClass(item.level)">
                  <span class="tip-tag">{{ tipLevelLabel(item.level) }}</span>
                  <span class="tip-text">{{ item.text }}</span>
                </li>
                <li v-if="!(brief.risks || []).length" class="is-info">
                  <span class="tip-tag">提示</span>
                  <span class="tip-text">暂无显著结构性风险，仍需执行止损纪律。</span>
                </li>
              </ul>
            </div>
          </div>
          <div class="brief-watch">
            <div class="brief-label">关注点</div>
            <ul class="watch-list">
              <li v-for="(point, idx) in brief.watchPoints || []" :key="'w-' + idx">{{ point }}</li>
            </ul>
          </div>
          <p class="brief-foot">基于本地仓位、止损止盈、技术命中与估值标签聚合，不构成投资建议。</p>
        </section>

        <div v-if="!loading && !refreshing && !rows.length" class="page-empty">
          <h3>该组合还没有持仓</h3>
          <p>可手动添加，或粘贴导入代码/数量/成本</p>
          <el-button type="primary" @click="openCreate">添加持仓</el-button>
          <el-button plain @click="openImport">导入</el-button>
        </div>

        <section v-if="rows.length" class="theme-panel">
          <div class="theme-panel-head">
            <div class="theme-panel-title">
              <h3><TermTip term="hot_theme">题材分布</TermTip></h3>
              <span class="muted">覆盖 {{ themeHitCount }} 只</span>
            </div>
            <label class="industry-toggle">
              <span>二级行业</span>
              <el-switch v-model="showIndustry" size="small" />
            </label>
          </div>
          <div class="theme-panel-body" :class="{ 'with-industry': showIndustry }">
            <div v-if="showIndustry" class="pie-wrap">
              <div class="pie-caption">二级行业</div>
              <div v-if="industryDist.length" ref="industryPieRef" class="pie-chart" />
              <div v-else class="pie-empty">暂无市值</div>
            </div>
            <div class="pie-wrap">
              <div class="pie-caption">题材分布</div>
              <div v-if="themeDist.length" ref="themePieRef" class="pie-chart" />
              <div v-else class="pie-empty">暂无题材数据</div>
            </div>
            <div v-if="themeTagBar.length" class="theme-bars">
              <div v-for="(item, idx) in themeTagBar" :key="item.name" class="theme-bar-row">
                <span
                  class="theme-chip"
                  :title="item.name"
                  :style="{
                    color: isCoreTheme(item.name) ? themeMeta(item.name).color : '#515154',
                    background: isCoreTheme(item.name) ? themeMeta(item.name).bg : 'rgba(0,0,0,.04)',
                    borderColor: isCoreTheme(item.name) ? themeMeta(item.name).border : 'rgba(0,0,0,.08)',
                  }"
                >
                  <span class="theme-chip-label">{{ themeMeta(item.name).short }}</span>
                </span>
                <div class="theme-bar-track">
                  <i
                    class="theme-bar-fill"
                    :style="{
                      width: `${Math.max(item.pct * 100, 2)}%`,
                      background: distColor(item.name, idx),
                    }"
                  />
                </div>
                <span class="theme-bar-pct">{{ (item.pct * 100).toFixed(1) }}%</span>
                <span v-if="!sharingCapture" class="theme-bar-mv muted">{{ fmtMoney(item.value) }}</span>
              </div>
            </div>
          </div>
        </section>

        <section v-if="rows.length" class="holding-layout">
          <el-table
            ref="holdingTableRef"
            class="holding-table"
            :data="rows"
            :default-sort="{ prop: 'positionWeight', order: 'descending' }"
            size="small"
            stripe
          >
            <el-table-column
              prop="name"
              label="个股"
              :width="shareCol.security"
              align="center"
              :fixed="sharingCapture ? false : 'left'"
              :class-name="sharingCapture ? '' : 'security-column'"
              :label-class-name="sharingCapture ? '' : 'security-column'"
              :sortable="!sharingCapture"
            >
              <template #default="{ row }">
                <button type="button" class="security-link" @click="router.push(`/stock/${row.code}`)">
                  <span class="security-name">
                    <span class="security-name-text">{{ row.name || '-' }}</span>
                    <span
                      v-if="securityMarketBadge(row)"
                      class="market-badge"
                      :class="`is-${securityMarketBadge(row).tone}`"
                      :title="securityMarketBadge(row).title"
                    >{{ securityMarketBadge(row).label }}</span>
                  </span>
                  <span class="security-code">{{ row.code }}</span>
                </button>
              </template>
            </el-table-column>
            <el-table-column
              v-if="!sharingCapture"
              prop="pnl"
              label="持仓盈亏"
              :width="shareCol.pnl"
              align="center"
              sortable
            >
              <template #default="{ row }">
                <div
                  v-if="row.pnl != null"
                  class="hold-pnl"
                  :class="Number(row.pnl) >= 0 ? 'up' : 'down'"
                >
                  <b>{{ fmtSignedMoney(row.pnl) }}</b>
                  <small v-if="row.pnlPct != null">{{ fmtPct(row.pnlPct) }}</small>
                </div>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column
              prop="todayPnl"
              :label="sharingCapture ? '今日涨跌' : '当日盈亏'"
              :width="shareCol.today"
              align="center"
              :sortable="!sharingCapture"
            >
              <template #default="{ row }">
                <div
                  v-if="row.todayPnl != null || row.pctChg != null"
                  class="today-pnl"
                  :class="todayPnlTone(row)"
                >
                  <template v-if="sharingCapture">
                    <b>{{ row.pctChg != null ? fmtSignedPct(row.pctChg) : '-' }}</b>
                  </template>
                  <template v-else>
                    <b>{{ row.todayPnl != null ? fmtSignedMoney(row.todayPnl) : '-' }}</b>
                    <small v-if="row.pctChg != null">{{ fmtSignedPct(row.pctChg) }}</small>
                  </template>
                </div>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column
              prop="marketPrice"
              :label="sharingCapture ? '现价' : '现价/成本'"
              :width="sharingCapture ? shareCol.price : shareCol.priceCost"
              :sortable="!sharingCapture"
            >
              <template #default="{ row }">
                <template v-if="sharingCapture">{{ fmtPrice(row.marketPrice) }}</template>
                <div v-else class="price-cost">
                  <b>{{ fmtPrice(row.marketPrice) }}</b>
                  <small>/ {{ fmtPrice(row.costPrice) }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="!sharingCapture"
              prop="quantity"
              label="持仓数量"
              :width="shareCol.qty"
              align="center"
              sortable
            />
            <el-table-column
              prop="positionWeight"
              v-if="!sharingCapture"
              label="个股仓位"
              :width="shareCol.weight"
              align="center"
              :sort-method="comparePositionWeight"
              sortable
            >
              <template #default="{ row }">
                <span class="weight-chip" :class="positionWeightTone(row)">
                  <span>{{ positionWeightLabel(row) }}</span>
                  <b>{{ positionWeightPct(row).toFixed(1) }}%</b>
                </span>
              </template>
            </el-table-column>
            <el-table-column label="题材" :width="shareCol.theme" align="center">
              <template #default="{ row }">
                <span
                  v-if="displayTheme(row)"
                  class="theme-chip theme-chip--sm"
                  :class="{ 'theme-chip--soft': isSoftOnlyTheme(row) }"
                  :style="{
                    color: themeMeta(displayTheme(row)).color,
                    background: themeMeta(displayTheme(row)).bg,
                    borderColor: themeMeta(displayTheme(row)).border,
                  }"
                >
                  {{ isCoreTheme(displayTheme(row)) ? themeMeta(displayTheme(row)).short : displayTheme(row) }}
                </span>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="技术" :width="sharingCapture ? shareCol.tech : undefined" :min-width="shareCol.tech">
              <template #default="{ row }">
                <div class="tech-cell">
                  <div class="tech-sum">{{ row.techSummary || '-' }}</div>
                  <div v-if="displayTechHits(row).length" class="tech-chips">
                    <span
                      v-for="sig in displayTechHits(row)"
                      :key="sig.key"
                      class="tech-chip on"
                      :title="sig.detail || sig.label"
                    >{{ shortTechLabel(sig.label) }}</span>
                  </div>
                  <span v-else-if="row.techSignals?.length" class="muted">暂无命中</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="估值" :width="shareCol.val" align="center">
              <template #default="{ row }">
                <div class="valuation-cell">
                  <span
                    v-if="row.valuationLabel"
                    class="val-chip"
                    :class="valClass(row.valuationLevel)"
                    :title="row.valuationSummary || ''"
                  >{{ row.valuationLabel }}</span>
                  <span v-else class="muted">-</span>
                  <div
                    v-if="availablePeMetrics(row).length"
                    class="pe-variants"
                    title="市盈率（动）/ 市盈率（静）/ 市盈率（TTM）；仅显示有效口径"
                  >
                    <span v-for="metric in availablePeMetrics(row)" :key="metric.key" class="pe-metric">
                      <i>{{ metric.label }}</i><b>{{ metric.value }}</b>
                    </span>
                  </div>
                  <span
                    v-else
                    class="valuation-missing"
                    title="当前行情源未返回有效的动态、静态或 TTM 市盈率"
                  >PE 暂缺</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="verdict" label="评价" :width="shareCol.verdict" align="center">
              <template #default="{ row }">
                <span v-if="row.verdict" class="verdict" :class="verdictClass(row.verdict)">{{ row.verdict }}</span>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column
              prop="advice"
              label="建议"
              :width="sharingCapture ? shareCol.advice : undefined"
              :min-width="shareCol.advice"
              :show-overflow-tooltip="!sharingCapture"
            >
              <template #default="{ row }">
                <span class="advice">{{ row.advice || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="stopLoss" label="止损" :width="shareCol.stop" align="center" :sortable="!sharingCapture">
              <template #default="{ row }">
                {{ row.stopLoss != null ? Number(row.stopLoss).toFixed(2) : '-' }}
              </template>
            </el-table-column>
            <el-table-column
              v-if="!sharingCapture"
              prop="takeProfit"
              label="止盈"
              :width="shareCol.take"
              align="center"
              sortable
            >
              <template #default="{ row }">
                {{ row.takeProfit != null ? Number(row.takeProfit).toFixed(2) : '-' }}
              </template>
            </el-table-column>
            <el-table-column
              v-if="!sharingCapture"
              prop="marketValue"
              label="市值"
              :width="shareCol.mv"
              sortable
            >
              <template #default="{ row }">
                {{ row.marketValue != null ? fmtMoney(row.marketValue) : '-' }}
              </template>
            </el-table-column>
            <el-table-column
              v-if="showIndustry && !sharingCapture"
              prop="industry"
              label="二级行业"
              width="100"
              show-overflow-tooltip
              sortable
            />
            <el-table-column
              v-if="!sharingCapture"
              prop="note"
              label="备注"
              :min-width="shareCol.note"
              show-overflow-tooltip
              sortable
            />
            <el-table-column
              v-if="!sharingCapture && showActionColumn"
              label="操作"
              :width="shareCol.ops"
              fixed="right"
              align="center"
              class-name="ops-column"
              label-class-name="ops-column"
            >
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="warning" @click="addObserve(row)">观察</el-button>
                <el-button link type="danger" @click="onRemove(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section v-show="dailyRows.length" class="daily-panel">
          <div class="theme-panel-head">
            <div class="theme-panel-title">
              <h3>收益快照</h3>
              <span class="muted">记录每日涨跌与组合市值</span>
            </div>
          </div>
          <div ref="chartRef" class="daily-chart" />
        </section>
        <BrandShareFoot
          v-show="sharingCapture"
          class="share-brand-foot-strip"
          :note="`${new Date().toISOString().slice(0, 10)} · 仅供研究参考 · 不构成投资建议`"
        />
      </main>

      <main v-else-if="!isMobileViewport" class="main empty-main">
        <h3>选择或新建一个组合</h3>
        <p class="muted">默认「我的持仓」会从现有真实持仓自动迁移</p>
      </main>
    </div>

    <el-dialog
      v-model="pfDialog"
      :title="pfForm.id ? '编辑组合' : '新建组合'"
      width="480px"
      destroy-on-close
      append-to-body
      align-center
    >
      <el-form label-width="88px">
        <el-form-item label="名称" required>
          <el-input v-model="pfForm.name" maxlength="32" placeholder="如：某某实盘" />
        </el-form-item>
        <el-form-item label="归属人">
          <el-input v-model="pfForm.ownerLabel" maxlength="32" placeholder="可选" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="pfForm.note" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
        <el-form-item v-if="pfForm.id && activeSummary && !activeSummary.isDefault" label="状态">
          <el-select v-model="pfForm.status" style="width: 100%">
            <el-option label="活跃" value="ACTIVE" />
            <el-option label="归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pfDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPf">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑持仓' : '添加持仓'"
      width="480px"
      append-to-body
      align-center
    >
      <el-form label-width="80px">
        <el-form-item label="代码" required>
          <el-select
            v-if="!form.id"
            v-model="form.code"
            filterable
            remote
            clearable
            :remote-method="onSearchStock"
            :loading="searchLoading"
            placeholder="代码或名称"
            style="width: 100%"
            @change="onPickStock"
          >
            <el-option
              v-for="opt in searchOptions"
              :key="opt.code"
              :label="`${opt.code} ${opt.name || ''}`"
              :value="opt.code"
            />
          </el-select>
          <el-input v-else v-model="form.code" disabled />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="可空，自动补全" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="form.quantity" :min="0" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="成本价">
          <el-input v-model="form.costPrice" placeholder="可选" />
        </el-form-item>
        <el-form-item label="止损">
          <el-input v-model="form.stopLoss" placeholder="可选" />
        </el-form-item>
        <el-form-item label="止盈">
          <el-input v-model="form.takeProfit" placeholder="可选" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.note" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="importDialog"
      title="导入持仓"
      width="560px"
      destroy-on-close
      append-to-body
      align-center
    >
      <p class="muted import-tip">每行一条：代码,数量,成本 —— 也可用空格/Tab；名称可代替代码</p>
      <el-input
        v-model="importText"
        type="textarea"
        :rows="10"
        placeholder="000001,1000,12.5&#10;600519 100 1800"
      />
      <template #footer>
        <el-button @click="importDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitImport">导入</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="shareOpen"
      :title="
        shareMode === 'page'
          ? '分享组合长图'
          : shareMode === 'today'
            ? '组合今日战绩拼图'
            : '分享组合截图'
      "
      :width="shareMode === 'page' ? '92vw' : shareMode === 'today' ? '1180px' : '1580px'"
      append-to-body
      destroy-on-close
      align-center
      class="holding-share-dialog"
      @closed="revokeSharePreview"
    >
      <div class="share-mode-row">
        <el-radio-group v-model="shareMode" size="small" @change="(v) => openShare(v)">
          <el-radio-button value="today" :disabled="!selectedIds.length">今日战绩拼图</el-radio-button>
          <el-radio-button value="card" :disabled="!rows.length">卡片海报</el-radio-button>
          <el-radio-button value="page" :disabled="!rows.length">右侧原样长图</el-radio-button>
        </el-radio-group>
      </div>
      <div class="share-stage" :class="{ 'is-long': shareMode === 'page' || shareMode === 'today' }">
        <img
          v-if="sharePreviewUrl"
          :src="sharePreviewUrl"
          alt="组合分享预览"
          :style="
            (shareMode === 'page' || shareMode === 'today') && sharePreviewLogicalWidth
              ? { width: `${sharePreviewLogicalWidth}px`, maxWidth: '100%' }
              : undefined
          "
        />
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
.floating-share-dropdown {
  position: fixed;
  right: max(18px, calc((100vw - 1240px) / 2));
  bottom: max(22px, env(safe-area-inset-bottom));
  z-index: 850;
}

.floating-share-dropdown :deep(.floating-share-button) {
  position: static;
}

.mobile-header-actions,
.mobile-detail-nav {
  display: none;
}

@media (max-width: 820px) {
  .floating-share-dropdown {
    right: 16px;
    bottom: calc(16px + env(safe-area-inset-bottom));
  }

  .portfolio-page.mobile-detail-open .floating-share-dropdown {
    bottom: calc(12px + env(safe-area-inset-bottom));
  }

  .portfolio-header {
    gap: 12px;
  }

  .portfolio-header > div:first-child p:last-child {
    display: none;
  }

  .desktop-header-actions {
    display: none !important;
  }

  .mobile-header-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    width: 100%;
  }

  .mobile-header-actions > .el-button {
    min-width: 112px;
    margin: 0;
  }

  .portfolio-more-trigger {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    padding: 0;
    border: 1px solid var(--line);
    border-radius: 6px;
    background: rgba(255, 255, 255, 0.72);
    color: var(--ink-soft);
    cursor: pointer;
  }

  .portfolio-more-trigger .el-icon {
    font-size: 20px;
  }

  .mobile-detail-nav {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 14px;
    padding-bottom: 10px;
    border-bottom: 1px solid var(--line);
  }

  .mobile-back-button {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    min-height: 44px;
    padding: 0 8px 0 2px;
    border: 0;
    background: transparent;
    color: var(--accent);
    font-size: 14px;
    font-weight: 650;
    cursor: pointer;
  }

  .mobile-back-button .el-icon {
    font-size: 18px;
  }

  .portfolio-page.mobile-detail-open .detail-actions {
    display: none;
  }

  .portfolio-page.mobile-detail-open .detail-header {
    margin-bottom: 12px;
  }
}

.eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.04em;
  color: var(--accent);
  text-transform: uppercase;
  line-height: 1.3;
}
.header p {
  max-width: 46em;
  margin: 0;
  color: var(--muted, #6e6e73);
  font-size: 13px;
}
.detail-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.detail-title .eyebrow {
  margin: 0;
  position: relative;
  z-index: 1;
}
.detail-title h2 {
  margin: 0;
  font-size: 22px;
  line-height: 1.25;
  position: relative;
  z-index: 0;
}
.detail-sub {
  margin: 0;
  color: var(--muted, #6e6e73);
  font-size: 13px;
  line-height: 1.4;
  max-width: 46em;
}
.header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}
.header h1 {
  margin: 4px 0 6px;
  font-size: 26px;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
  transition: grid-template-columns 0.2s ease;
}
.layout.is-side-collapsed {
  grid-template-columns: 52px minmax(0, 1fr);
}
.side {
  background: var(--glass, #faf8f4);
  border: 1px solid var(--glass-border, rgba(0, 0, 0, 0.06));
  border-radius: var(--radius, 12px);
  padding: 12px;
  min-height: 420px;
  position: sticky;
  top: 68px;
  transition: padding 0.2s ease;
}
.side.collapsed {
  padding: 10px 6px;
  min-height: 240px;
}
.side-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
}
.side.collapsed .side-head {
  justify-content: center;
  margin-bottom: 8px;
}
.side-title {
  flex: 1;
  min-width: 0;
}
.side-toggle {
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 8px;
  background: #fff;
  color: #515154;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  padding: 0;
}
.side-toggle:hover {
  border-color: rgba(0, 113, 227, 0.35);
  color: #0071e3;
}
.side-rail {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
}
.rail-item {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: #fff;
  color: #1d1d1f;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  padding: 0;
}
.rail-item:hover {
  border-color: rgba(0, 0, 0, 0.1);
}
.rail-item.active {
  border-color: rgba(0, 113, 227, 0.4);
  background: rgba(0, 113, 227, 0.08);
  color: #0071e3;
}
.side-empty {
  color: var(--muted);
  font-size: 13px;
  padding: 24px 8px;
}
.side-select-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding: 0 2px;
}
.side-select-count {
  flex: 1;
  font-size: 12px;
  color: #86868b;
}
.pf-card {
  display: block;
  width: 100%;
  text-align: left;
  border: 1px solid transparent;
  background: #fff;
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
  position: relative;
}
.pf-card:hover {
  border-color: rgba(0, 0, 0, 0.08);
}
.pf-card.active {
  border-color: rgba(0, 113, 227, 0.35);
  box-shadow: 0 0 0 1px rgba(0, 113, 227, 0.12);
}
.pf-card.picked {
  background: rgba(0, 113, 227, 0.04);
}
.pf-card.archived {
  opacity: 0.65;
}
.pf-top {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-right: 72px;
}
.pf-top strong {
  font-size: 14px;
}
.tag {
  font-size: 11px;
  color: #0071e3;
  background: rgba(0, 113, 227, 0.1);
  padding: 1px 6px;
  border-radius: 999px;
}
.pf-meta {
  display: flex;
  justify-content: flex-start;
  margin-top: 4px;
  font-size: 12px;
  color: #86868b;
}
.pf-pnl {
  margin-top: 6px;
  font-size: 13px;
  font-weight: 600;
}
.pf-pnl small {
  margin-left: 6px;
  font-weight: 500;
}
.pf-tops {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  margin-top: 6px;
}
.pf-top-chip {
  font-size: 11px;
  color: #6e6e73;
  background: rgba(0, 0, 0, 0.04);
  padding: 2px 6px;
  border-radius: 4px;
  line-height: 1.3;
}
.pf-top-chip em {
  font-style: normal;
  margin-left: 3px;
  font-weight: 600;
}
.pf-top-chip em.up {
  color: #c23a3a;
}
.pf-top-chip em.down {
  color: #1f7a4d;
}
.pf-ops {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  align-items: center;
  gap: 2px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease;
}
.pf-card:hover .pf-ops,
.pf-card:focus-within .pf-ops {
  opacity: 1;
  pointer-events: auto;
}
.main {
  min-width: 0;
  background: transparent;
  padding: 0;
}
/* 长图分享：藏操作区/副标题；放开单元格换行，避免标签叠压截断 */
.main.is-sharing-capture {
  min-width: 1520px;
  box-sizing: border-box;
}
.share-brand-strip {
  margin-bottom: 12px;
}
.main.is-sharing-capture :deep(.detail-header .actions),
.main.is-sharing-capture :deep(.share-hide-meta),
.main.is-sharing-capture :deep(.brief-foot),
.main.is-sharing-capture :deep(.industry-toggle),
.main.is-sharing-capture :deep(.caret-wrapper),
.main.is-sharing-capture :deep(.el-table__column-filter-trigger) {
  display: none !important;
}
.main.is-sharing-capture :deep(.detail-header) {
  align-items: flex-start;
}
.main.is-sharing-capture :deep(.el-table__header-wrapper),
.main.is-sharing-capture :deep(.el-table__body-wrapper) {
  overflow: visible !important;
}
.main.is-sharing-capture :deep(.el-table .cell) {
  white-space: normal !important;
  overflow: visible !important;
  text-overflow: clip !important;
  line-height: 1.35;
}
.main.is-sharing-capture :deep(.el-table__body td.el-table__cell) {
  vertical-align: top;
}
.main.is-sharing-capture :deep(.tech-chips) {
  flex-wrap: wrap;
  gap: 4px;
}
.main.is-sharing-capture :deep(.tech-chip),
.main.is-sharing-capture :deep(.val-chip),
.main.is-sharing-capture :deep(.theme-chip) {
  white-space: nowrap;
  flex-shrink: 0;
}
.main.is-sharing-capture :deep(.advice) {
  white-space: normal;
  word-break: break-word;
  line-height: 1.35;
}
.detail-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 14px;
}
.empty-main {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  min-height: 360px;
  color: #6e6e73;
  background: var(--glass, #faf8f4);
  border-radius: var(--radius, 12px);
  border: 1px solid var(--glass-border, rgba(0, 0, 0, 0.06));
}
.page-empty {
  text-align: center;
  padding: 40px 16px;
  color: var(--muted);
}
.page-empty h3 {
  margin: 0 0 8px;
  color: var(--ink, #1d1d1f);
}
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}
.stat-cards--share {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  max-width: 420px;
}
.brief-panel {
  margin-bottom: 16px;
  padding: 16px 18px 14px;
  border-radius: 14px;
  background: linear-gradient(165deg, #faf7f1 0%, #f3efe8 55%, #eef2f7 100%);
  border: 1px solid rgba(0, 0, 0, 0.06);
}
.brief-head {
  margin-bottom: 12px;
}
.brief-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.brief-title-row h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #1d1d1f;
}
.stance-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  background: rgba(0, 0, 0, 0.05);
  color: #515154;
}
.stance-pill.stance-防守 {
  background: rgba(196, 61, 74, 0.12);
  color: #c43d4a;
}
.stance-pill.stance-均衡偏谨慎,
.stance-pill.stance-均衡 {
  background: rgba(179, 107, 0, 0.12);
  color: #b36b00;
}
.stance-pill.stance-偏进攻 {
  background: rgba(31, 138, 76, 0.12);
  color: #1f8a4c;
}
.brief-summary {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: #3a3a3c;
  font-weight: 600;
}
.brief-thesis {
  margin-bottom: 14px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(0, 0, 0, 0.05);
}
.brief-thesis p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #3a3a3c;
}
.brief-label {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #86868b;
  text-transform: uppercase;
}
.brief-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}
.brief-block {
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(0, 0, 0, 0.05);
  min-width: 0;
}
.brief-list,
.watch-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.brief-list li {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 8px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
  font-size: 13px;
  line-height: 1.5;
  color: #3a3a3c;
}
.brief-list li:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}
.brief-list li:first-child {
  padding-top: 0;
}
.tip-tag {
  flex: 0 0 auto;
  margin-top: 1px;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  background: rgba(0, 0, 0, 0.05);
  color: #6e6e73;
}
.brief-list li.is-critical .tip-tag {
  background: rgba(196, 61, 74, 0.12);
  color: #c43d4a;
}
.brief-list li.is-warn .tip-tag {
  background: rgba(179, 107, 0, 0.12);
  color: #b36b00;
}
.brief-list li.is-info .tip-tag {
  background: rgba(0, 113, 227, 0.1);
  color: #0071e3;
}
.tip-text {
  min-width: 0;
  flex: 1;
}
.brief-watch {
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(0, 0, 0, 0.05);
}
.watch-list li {
  position: relative;
  padding: 5px 0 5px 14px;
  font-size: 13px;
  line-height: 1.5;
  color: #515154;
}
.watch-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.7em;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #0071e3;
}
.brief-foot {
  margin: 10px 0 0;
  font-size: 11px;
  color: #8e8e93;
}
@media (max-width: 960px) {
  .brief-grid {
    grid-template-columns: 1fr;
  }
}
.stat-card {
  background: var(--glass, #f7f4ee);
  border: 1px solid var(--glass-border, rgba(0, 0, 0, 0.05));
  border-radius: 10px;
  padding: 12px 14px;
}
.stat-card label {
  display: block;
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 4px;
}
.stat-card b {
  font-size: 18px;
}
.up {
  color: var(--up);
}
.down {
  color: var(--down);
}
.pct-aside {
  margin-left: 6px;
  font-size: 0.82em;
  font-weight: 600;
  opacity: 0.85;
}
.security-name {
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
}
.security-name-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
}
.security-link {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  width: 100%;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  font: inherit;
  line-height: 1.15;
  text-align: center;
  cursor: pointer;
}
.security-link:hover .security-name-text,
.security-link:focus-visible .security-name-text {
  color: var(--el-color-primary);
}
.security-link:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 2px;
}
.security-code {
  color: var(--muted);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}
.market-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 18px;
  width: 18px;
  height: 18px;
  border: 1px solid transparent;
  border-radius: 4px;
  box-sizing: border-box;
  font-size: 10px;
  font-weight: 750;
  line-height: 1;
}
.market-badge.is-star {
  color: #0a66c2;
  background: rgba(0, 113, 227, 0.09);
  border-color: rgba(0, 113, 227, 0.18);
}
.market-badge.is-chinext {
  color: #16775d;
  background: rgba(42, 157, 143, 0.1);
  border-color: rgba(42, 157, 143, 0.2);
}
.market-badge.is-bj {
  color: #a86400;
  background: rgba(255, 159, 10, 0.11);
  border-color: rgba(255, 159, 10, 0.22);
}
.market-badge.is-hk {
  color: #6b4fbb;
  background: rgba(107, 79, 187, 0.1);
  border-color: rgba(107, 79, 187, 0.2);
}
.market-badge.is-us {
  color: #515154;
  background: rgba(0, 0, 0, 0.05);
  border-color: rgba(0, 0, 0, 0.1);
}
.holding-layout {
  margin-top: 14px;
  min-width: 0;
  max-width: 100%;
}
.today-pnl,
.hold-pnl {
  display: inline-flex;
  align-items: baseline;
  justify-content: center;
  gap: 6px;
  width: 100%;
  line-height: 1.2;
  white-space: nowrap;
}
.today-pnl b,
.hold-pnl b {
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.today-pnl small,
.hold-pnl small {
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  opacity: 0.72;
}
.price-cost {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  line-height: 1.2;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.price-cost b {
  font-size: 13px;
  font-weight: 650;
  color: var(--ink-soft);
}
.price-cost small {
  font-size: 11px;
  color: var(--muted);
}
.weight-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 24px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: 7px;
  box-sizing: border-box;
  font-size: 11px;
  line-height: 1;
  white-space: nowrap;
}
.weight-chip b {
  font-size: 11px;
  font-weight: 750;
  font-variant-numeric: tabular-nums;
}
.weight-chip.is-heavy {
  color: #c43d4a;
  background: rgba(255, 59, 48, 0.1);
  border-color: rgba(255, 59, 48, 0.2);
}
.weight-chip.is-core {
  color: #a86400;
  background: rgba(255, 159, 10, 0.11);
  border-color: rgba(255, 159, 10, 0.22);
}
.weight-chip.is-normal {
  color: #0a66c2;
  background: rgba(0, 113, 227, 0.08);
  border-color: rgba(0, 113, 227, 0.16);
}
.weight-chip.is-light {
  color: #5f6368;
  background: rgba(0, 0, 0, 0.04);
  border-color: rgba(0, 0, 0, 0.08);
}
.theme-panel,
.daily-panel {
  margin: 2px 0 12px;
  padding: 12px 14px 14px;
  background: var(--glass);
  backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  -webkit-backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-soft);
}
.daily-panel {
  margin-top: 14px;
}
.theme-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.theme-panel-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}
.theme-panel-title h3 {
  margin: 0;
  font-size: 15px;
  white-space: nowrap;
}
.industry-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--muted);
  cursor: pointer;
  user-select: none;
}
.theme-panel-body {
  display: grid;
  grid-template-columns: minmax(200px, 280px) minmax(0, 1fr);
  gap: 12px 20px;
  align-items: center;
}
.theme-panel-body.with-industry {
  grid-template-columns: minmax(180px, 1fr) minmax(180px, 1fr) minmax(0, 1.1fr);
}
.pie-wrap {
  min-width: 0;
}
.pie-caption {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 2px;
}
.pie-chart {
  width: 100%;
  height: 220px;
}
.pie-empty,
.chart-empty {
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--muted);
  font-size: 13px;
  background: rgba(0, 0, 0, 0.02);
  border-radius: 10px;
}
.theme-bars {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding: 4px 0;
}
.theme-bar-row {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr) 48px 72px;
  gap: 8px;
  align-items: center;
}
.theme-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 24px;
  padding: 0 9px;
  border-radius: 7px;
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.01em;
  line-height: 1;
  white-space: nowrap;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}
.theme-chip-label {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.theme-chip--sm {
  height: 22px;
  padding: 0 7px;
  font-size: 11px;
  border-radius: 6px;
}
.theme-chip--soft {
  font-weight: 500;
  opacity: 0.92;
}
.tech-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 2px 0;
}
.tech-sum {
  font-size: 11px;
  color: var(--muted);
  line-height: 1.2;
}
.tech-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
}
.tech-chip {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 7px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.01em;
  border: 1px solid transparent;
  white-space: nowrap;
  flex-shrink: 0;
}
.tech-chip.on {
  color: #0a66c2;
  background: rgba(0, 113, 227, 0.1);
  border-color: rgba(0, 113, 227, 0.18);
}
.val-chip {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 7px;
  font-size: 11px;
  font-weight: 650;
  border: 1px solid transparent;
  white-space: nowrap;
}
.val-chip.cheap {
  color: #1f8a4c;
  background: rgba(52, 199, 89, 0.12);
  border-color: rgba(52, 199, 89, 0.22);
}
.val-chip.rich {
  color: #c43d4a;
  background: rgba(255, 59, 48, 0.1);
  border-color: rgba(255, 59, 48, 0.2);
}
.val-chip.fair {
  color: #0a66c2;
  background: rgba(0, 113, 227, 0.08);
  border-color: rgba(0, 113, 227, 0.16);
}
.valuation-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
}
.pe-variants {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 4px 7px;
  font-size: 10px;
  line-height: 1.2;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.pe-metric {
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
}
.pe-metric i {
  color: var(--muted);
  font-style: normal;
}
.pe-variants b {
  color: var(--ink-soft);
  font-size: 11px;
  font-weight: 650;
}
.valuation-missing {
  color: var(--muted);
  font-size: 10px;
  line-height: 1.2;
}
.verdict {
  font-size: 12px;
  font-weight: 650;
  color: var(--ink-soft);
}
.verdict.ok {
  color: #1f8a4c;
}
.verdict.warn {
  color: #c43d4a;
}
.verdict.soft {
  color: #b36b00;
}
.advice {
  font-size: 12px;
  color: var(--ink-soft);
  line-height: 1.35;
}
.theme-bar-track {
  height: 8px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.04);
  overflow: hidden;
}
.theme-bar-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  opacity: 0.92;
}
.theme-bar-pct {
  font-size: 12px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  text-align: right;
  color: var(--ink-soft);
}
.theme-bar-mv {
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  text-align: right;
}
.muted {
  font-size: 11px;
  color: var(--muted);
}
.holding-table {
  width: 100%;
  min-width: 0;
}
.holding-table :deep(.ops-column) {
  background: rgba(255, 255, 255, 0.97) !important;
  box-shadow: -10px 0 18px -18px rgba(29, 29, 31, 0.65);
}
.holding-table :deep(.security-column) {
  background: rgba(255, 255, 255, 0.97) !important;
  box-shadow: 10px 0 18px -18px rgba(29, 29, 31, 0.65);
}
.holding-table :deep(.ops-column .cell) {
  white-space: nowrap;
}
.holding-table :deep(.el-table__body tr:hover > .ops-column),
.holding-table :deep(.el-table__body tr:hover > .security-column) {
  background: #f1f7fd !important;
}
.daily-chart {
  height: 260px;
  width: 100%;
}
.import-tip {
  margin: 0 0 10px;
}
.share-tip {
  margin: 0 0 10px;
  font-size: 13px;
  color: #86868b;
}
.share-mode-row {
  margin-bottom: 12px;
}
.share-stage {
  /* 勿用 flex + 默认 stretch，会把预览图纵向压扁 */
  display: block;
  width: 100%;
  max-height: min(72vh, 860px);
  overflow: auto;
  padding: 12px;
  background: #ececec;
  border-radius: 12px;
  text-align: center;
  box-sizing: border-box;
}
.share-stage.is-long {
  max-height: min(78vh, 920px);
  text-align: left;
}
.share-stage img {
  width: min(100%, 1480px);
  max-width: none;
  height: auto;
  display: inline-block;
  vertical-align: top;
  object-fit: contain;
  box-shadow: 0 10px 32px rgba(0, 0, 0, 0.12);
  border-radius: 4px;
}
.share-stage.is-long img {
  /* 逻辑宽度由 inline style 控制，避免 width:100% 放大发糊 */
  width: auto;
  max-width: 100%;
  height: auto;
}
@media (max-width: 960px) {
  .layout,
  .layout.is-side-collapsed {
    grid-template-columns: 1fr;
  }
  .side,
  .side.collapsed {
    position: static;
    min-height: 0;
  }
  .side.collapsed .side-rail {
    flex-direction: row;
    flex-wrap: wrap;
    justify-content: flex-start;
  }
  .stat-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .theme-panel-body,
  .theme-panel-body.with-industry {
    grid-template-columns: 1fr;
  }
  .theme-bar-row {
    grid-template-columns: minmax(88px, 112px) minmax(0, 1fr) 48px;
  }
  .theme-bar-mv {
    display: none;
  }
}

@media (max-width: 820px) {
  .layout,
  .layout.is-side-collapsed {
    display: block;
  }

  .side,
  .side.collapsed {
    padding: 0;
    border: 0;
    border-radius: 0;
    background: transparent;
  }

  .side-head {
    margin-bottom: 8px;
    padding: 0 2px 8px;
    border-bottom: 1px solid var(--line);
  }

  .side-title {
    font-size: 16px;
  }

  .pf-card {
    margin: 0;
    padding: 15px 2px 14px;
    border: 0;
    border-bottom: 1px solid var(--line);
    border-radius: 0;
    background: transparent;
  }

  .pf-card:last-child {
    border-bottom: 0;
  }

  .pf-card:active {
    background: var(--fill);
  }

  .pf-card:focus-visible,
  .portfolio-more-trigger:focus-visible,
  .mobile-back-button:focus-visible {
    outline: 2px solid var(--el-color-primary-light-5);
    outline-offset: 2px;
  }

  .pf-card.archived {
    opacity: 0.58;
  }

  .pf-top {
    gap: 6px;
    padding-right: 0;
  }

  .pf-top strong {
    overflow: hidden;
    font-size: 17px;
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .pf-card-arrow {
    flex: 0 0 auto;
    margin-left: auto;
    color: var(--muted);
    font-size: 18px;
  }

  .pf-pnl {
    display: flex;
    align-items: baseline;
    gap: 7px;
    margin-top: 8px;
    font-size: 16px;
    font-variant-numeric: tabular-nums;
  }

  .pf-pnl small {
    margin-left: 0;
    font-size: 13px;
  }

  .pf-tops {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 6px;
    margin-top: 10px;
  }

  .pf-top-chip {
    display: flex;
    min-width: 0;
    align-items: baseline;
    justify-content: space-between;
    gap: 4px;
    overflow: hidden;
    padding: 6px 7px;
    border-radius: 4px;
    font-size: 11px;
    white-space: nowrap;
  }

  .pf-top-chip em {
    flex: 0 0 auto;
  }

  .mobile-detail-open .main {
    width: 100%;
    animation: portfolio-mobile-view-in 0.18s ease-out;
  }

  .mobile-detail-open .detail-title h2 {
    font-size: 24px;
  }
}

@keyframes portfolio-mobile-view-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .mobile-detail-open .main {
    animation: none;
  }
}
</style>
