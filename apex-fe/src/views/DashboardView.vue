<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { dashboardHome } from '../api/dashboard'
import { normalizeHotThemes } from '../utils/hotTheme.js'
import { buildVolumeChangeParts } from '../utils/marketVolume.js'
import { publishDataFreshness, staleDataTime } from '../utils/dataFreshness.js'
const router = useRouter()
const HOME_CACHE_KEY = 'apex.dashboard.home.v19'
const loading = ref(false)
const refreshing = ref(false)
const home = ref(null)
const loadError = ref('')
const marketDetailOpen = ref(false)
const morningMarketExpanded = ref(false)
const morningNewsExpanded = ref(false)
const opinionPreviewOpen = ref(false)
const opinionPreview = ref(null)

function readHomeCache() {
  try {
    const raw = sessionStorage.getItem(HOME_CACHE_KEY)
    if (!raw) return null
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function writeHomeCache(data) {
  try {
    if (data) sessionStorage.setItem(HOME_CACHE_KEY, JSON.stringify(data))
  } catch {
    // ignore quota
  }
}

const market = computed(() => home.value?.market || null)
const command = computed(() => home.value?.command || null)
const isIntradayCommand = computed(() => command.value?.phase === 'IN_SESSION' && command.value?.marketDataUpdatedAt)
const dashboardMarketTime = computed(() => staleDataTime({
  tradeDate: isIntradayCommand.value ? command.value?.marketDataUpdatedAt : market.value?.asOf,
  updatedAt: command.value?.marketDataUpdatedAt,
  intraday: Boolean(isIntradayCommand.value),
  latest: ['STALE', 'BLOCKED'].includes(command.value?.status) ? false : undefined,
}))
const commandOperationItems = computed(() => (command.value?.operationGuide?.items || []).slice(0, 3))
const hasExecutableNewPosition = computed(() => commandOperationItems.value.some(
  (item) => item.code === 'BUY_CONDITIONALLY' && item.status === 'READY',
))
const morningBriefing = computed(() => home.value?.morningBriefing || null)
const morningBriefingTime = computed(() => staleDataTime({
  tradeDate: morningBriefing.value?.tradeDate,
  updatedAt: morningBriefing.value?.generatedAt,
  latest: Boolean(
    morningBriefing.value?.tradeDate
    && morningBriefing.value.tradeDate === command.value?.tradeDate
    && !['STALE', 'BLOCKED'].includes(command.value?.status),
  ) || undefined,
}))
const breadthForecast = computed(() => home.value?.breadthForecast || null)
const newsPulse = computed(() => morningBriefing.value?.newsPulse || null)
const preMarketEventImpacts = computed(() => newsPulse.value?.eventImpacts || [])
const visiblePreMarketEventImpacts = computed(() => (
  morningNewsExpanded.value ? preMarketEventImpacts.value : preMarketEventImpacts.value.slice(0, 3)
))
const marketOpinion = computed(() => morningBriefing.value?.marketOpinion || null)
const institutionViews = computed(() => marketOpinion.value?.institutionViews || [])
const traderSeatViews = computed(() => marketOpinion.value?.traderSeatViews || [])
const activeSeats = computed(() => marketOpinion.value?.activeSeats || [])
const kolViews = computed(() => marketOpinion.value?.kolViews || [])
const kolSources = computed(() => marketOpinion.value?.kolSources || [])
const legacyIndexSymbols = new Set(['usIXIC', 'usDJI', 'usINX'])
const overnightIndexes = computed(() => {
  const indexQuotes = morningBriefing.value?.indexQuotes
  if (Array.isArray(indexQuotes)) return indexQuotes
  const marketQuotes = morningBriefing.value?.marketQuotes || []
  return marketQuotes.filter((quote) => legacyIndexSymbols.has(quote.symbol))
})
const overnightThemes = computed(() => morningBriefing.value?.marketThemes || [])
const asiaIndexes = computed(() => morningBriefing.value?.asiaQuotes || [])
const openingAuction = computed(() => home.value?.openingAuction || null)
const hasOpeningAuction = computed(() => Boolean(
  openingAuction.value?.available && openingAuction.value?.indexes?.length
))
const externalMarketItems = computed(() => morningBriefing.value?.externalMarketItems || [])
const externalMarketAvailableCount = computed(
  () => externalMarketItems.value.filter((item) => item.available).length,
)
const ftseA50Future = computed(() => morningBriefing.value?.ftseA50Future || null)
const overnightStars = computed(() => {
  const starQuotes = morningBriefing.value?.starQuotes
  if (Array.isArray(starQuotes)) return starQuotes
  const marketQuotes = morningBriefing.value?.marketQuotes || []
  return marketQuotes.filter((quote) => !legacyIndexSymbols.has(quote.symbol))
})
const morningNewsCards = computed(() => {
  const cards = newsPulse.value?.cards || []
  if (cards.length) return cards.slice(0, 3)
  return (morningBriefing.value?.newsTitles || []).slice(0, 3).map((title, index) => ({
    id: `briefing-title-${index}`,
    title,
  }))
})
const hasMoreMorningNews = computed(() => (
  preMarketEventImpacts.value.length > 3
  || morningNewsCards.value.length > 0
  || Boolean(marketOpinion.value)
))
const decision = computed(() => home.value?.decision || null)
const observeAlerts = computed(() => home.value?.observeAlerts || [])
const dataHealth = computed(() => home.value?.dataHealth || null)
const themes = computed(() => normalizeHotThemes(market.value))
const tips = computed(() => market.value?.tips || [])
const effect = computed(() => market.value?.effect || null)
const topBuys = computed(() => decision.value?.topBuys || [])
const topSells = computed(() => decision.value?.topSells || [])
const valuationDistTotal = computed(() => {
  const d = decision.value
  if (!d) return 0
  return (Number(d.valuationCheapCount) || 0)
    + (Number(d.valuationFairCount) || 0)
    + (Number(d.valuationRichCount) || 0)
})
const indexCards = computed(() => {
  const rows = market.value?.indexes
  if (rows?.length) return rows
  // 兼容旧接口：从文案行解析
  return (market.value?.indexLines || []).map((line) => parseIndexLine(line)).filter(Boolean)
})

function parseIndexLine(line) {
  const text = String(line || '').trim()
  if (!text) return null
  const m = text.match(/^(.+?)\s+([+-]?\d+(?:\.\d+)?)%\s*·\s*([\d.]+)$/)
  if (!m) {
    return { name: text, pctChg: null, close: null, direction: 'flat' }
  }
  const pct = Number(m[2])
  return {
    name: m[1],
    pctChg: pct,
    close: Number(m[3]),
    direction: pct > 0 ? 'up' : pct < 0 ? 'down' : 'flat',
  }
}

function fmtIndexPct(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return '-'
  const sign = n > 0 ? '+' : n < 0 ? '−' : ''
  return `${sign}${Math.abs(n).toFixed(2)}%`
}

function fmtQuotePrice(v) {
  if (v == null || v === '') return ''
  const n = Number(v)
  if (Number.isNaN(n)) return ''
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtBriefingTime(v) {
  if (!v) return '待生成'
  return String(v).replace('T', ' ').slice(5, 16)
}

function fmtForecastRatio(value) {
  const ratio = Number(value)
  if (Number.isNaN(ratio)) return '--'
  return `${Math.max(0, Math.min(100, ratio)).toFixed(0)}%`
}

function forecastRatio(value) {
  const ratio = Number(value)
  if (Number.isNaN(ratio)) return 50
  return Math.max(0, Math.min(100, ratio))
}

function newsSourceLabel(source) {
  const labels = {
    eastmoney: '东财',
    cls: '财联社',
    ths: '同花顺',
    sina: '新浪',
    cctv: '央视',
  }
  return labels[source] || source || ''
}

function opinionTime(value) {
  if (!value) return '时间未披露'
  return String(value).replace('T', ' ').slice(5, 16)
}

function opinionTone(direction) {
  const text = String(direction || '')
  if (/买入|增持|推荐|看多/.test(text)) return 'bull'
  if (/卖出|减持|回避|看空/.test(text)) return 'bear'
  return ''
}

function formatOpinionAmount(value) {
  const amount = Number(value)
  if (Number.isNaN(amount)) return ''
  const sign = amount > 0 ? '+' : ''
  return `${sign}${(amount / 100000000).toFixed(2)} 亿`
}

function opinionSourceStatusLabel(status) {
  return status === 'READY' ? '已核验' : '待核验'
}

function openOpinionPreview(item) {
  if (!item?.url) return
  opinionPreview.value = item
  opinionPreviewOpen.value = true
}

function openOpinionInNewTab() {
  if (!opinionPreview.value?.url) return
  window.open(opinionPreview.value.url, '_blank', 'noopener,noreferrer')
}

function pctDir(v) {
  const n = Number(v)
  if (Number.isNaN(n) || n === 0) return 'flat'
  return n > 0 ? 'up' : 'down'
}

function themeUpPct(theme) {
  const upCount = Number(theme?.upCount)
  const quoteCount = Number(theme?.quoteCount)
  if (Number.isNaN(upCount) || Number.isNaN(quoteCount) || quoteCount <= 0) return 0
  return Math.min(100, Math.max(0, (upCount / quoteCount) * 100))
}

/** 量能涨跌幅比例红绿：正比为红，负比为绿 */
function volumeDir(trend, vsMa5Pct) {
  if (trend === '放量') return 'up'
  if (trend === '缩量') return 'down'
  const n = Number(vsMa5Pct)
  if (!Number.isNaN(n) && n > 0) return 'up'
  if (!Number.isNaN(n) && n < 0) return 'down'
  return ''
}

/** 缩量/放量 + 较前日成交额增减值和百分比 */
const volumeChangeParts = computed(() => {
  return buildVolumeChangeParts(market.value)
})

const volumePercentageParts = computed(() => {
  const percentageText = volumeChangeParts.value?.percentageText || ''
  const matched = percentageText.match(/^([+\-−]?)(.*)$/)
  return {
    sign: matched?.[1] || '',
    number: matched?.[2] || percentageText,
  }
})

const breadth = computed(() => {
  const up = Number(market.value?.breadthUp)
  const down = Number(market.value?.breadthDown)
  if (Number.isNaN(up) || Number.isNaN(down) || (up <= 0 && down <= 0)) {
    return null
  }
  // 缺字段时不要当成 0，避免「平盘家数为什么是 0」的误导
  const flatRaw = market.value?.breadthFlat
  const hasFlat = flatRaw != null && flatRaw !== '' && !Number.isNaN(Number(flatRaw))
  const flatN = hasFlat ? Math.max(0, Number(flatRaw)) : null
  const total = up + down + (flatN || 0)
  const upPct = total > 0 ? (up / total) * 100 : 50
  const flatPct = total > 0 && flatN != null ? (flatN / total) * 100 : 0
  return {
    up,
    down,
    flat: flatN,
    hasFlat,
    upPct,
    flatPct,
    downPct: Math.max(0, 100 - upPct - flatPct),
    upShare: total > 0 ? Math.round((up / total) * 100) : null,
    ratio: down > 0 ? (up / down).toFixed(2) : up > 0 ? '∞' : '-',
  }
})

/** 指数方向与涨跌家数分化时的说明（权重拖累等，不是数据坏了） */
const breadthHint = computed(() => {
  const b = breadth.value
  const rows = indexCards.value || []
  if (!b || !rows.length) return ''
  const pcts = rows.map((r) => Number(r.pctChg)).filter((n) => !Number.isNaN(n))
  if (!pcts.length) return ''
  const avg = pcts.reduce((a, c) => a + c, 0) / pcts.length
  if (avg < -0.2 && b.up > b.down * 1.2) {
    return `上涨占比 ${b.upShare}% · 指数跌、个股涨（权重拖累）`
  }
  if (avg > 0.2 && b.down > b.up * 1.2) {
    return `上涨占比 ${b.upShare}% · 指数涨、个股跌（赚钱效应弱）`
  }
  if (b.upShare != null) return `上涨占比 ${b.upShare}%`
  return ''
})
const scorePct = computed(() => {
  const s = Number(market.value?.stanceScore)
  if (Number.isNaN(s)) return 0
  return Math.max(0, Math.min(100, s))
})

function stanceClass(s) {
  if (s === '进攻') return 'stance-attack'
  if (s === '防守') return 'stance-defend'
  return 'stance-balance'
}

function dataLevelType(level) {
  if (level === 'GREEN') return 'success'
  if (level === 'YELLOW') return 'warning'
  if (level === 'RED') return 'error'
  return 'info'
}

function dataLevelLabel(level) {
  if (level === 'GREEN') return '正常'
  if (level === 'YELLOW') return '预警'
  if (level === 'RED') return '异常'
  return level || '-'
}

function commandStatusLabel(status) {
  const labels = {
    READY: '已就绪',
    PARTIAL: '部分可用',
    STALE: '数据过期',
    BLOCKED: '已阻断',
    GENERATING: '生成中',
  }
  return labels[status] || '状态未知'
}

function publishDashboardDataFreshness(homeData) {
  const commandData = homeData?.command
  if (!commandData) return
  const level = homeData?.dataHealth?.level
    || (commandData.status === 'READY' ? 'GREEN' : commandData.status === 'STALE' || commandData.status === 'BLOCKED' ? 'RED' : 'YELLOW')
  const staleStatus = ['STALE', 'BLOCKED'].includes(commandData.status)
  const marketDataTime = staleDataTime({
    tradeDate: commandData.phase === 'IN_SESSION' && commandData.marketDataUpdatedAt
      ? commandData.marketDataUpdatedAt : commandData.marketDataAsOf,
    updatedAt: commandData.marketDataUpdatedAt,
    intraday: commandData.phase === 'IN_SESSION',
    latest: staleStatus ? false : undefined,
  })
  const decisionDataTime = staleDataTime({
    tradeDate: commandData.decisionDataAsOf,
    latest: staleStatus ? false : undefined,
  })
  publishDataFreshness({
    level,
    label: `看板数据${dataLevelLabel(level)}`,
    detail: [marketDataTime, decisionDataTime, commandStatusLabel(commandData.status)].filter(Boolean).join(' · '),
    route: '/dashboard',
  })
}

function operationStatusLabel(status) {
  const labels = {
    REQUIRED: '必做',
    READY: '可执行',
    WAIT: '等待',
    BLOCKED: '已阻断',
    DONE: '已完成',
  }
  return labels[status] || '待确认'
}

const commandDataTimeText = computed(() => {
  const tradeDate = command.value?.tradeDate || '-'
  const marketDataUpdatedAt = command.value?.marketDataUpdatedAt
  if (marketDataUpdatedAt) {
    const updatedTime = String(marketDataUpdatedAt).replace('T', ' ').slice(0, 16)
    const compactUpdatedTime = updatedTime.startsWith(tradeDate) ? updatedTime.slice(11) : updatedTime
    return `${tradeDate} · 行情 ${compactUpdatedTime} 更新`
  }
  if (command.value?.marketDataAsOf) {
    return `${tradeDate} · 行情截至 ${command.value.marketDataAsOf}`
  }
  return tradeDate
})

function openCommandAction(code) {
  if (code === 'VIEW_CONTEXT') {
    document.getElementById('pre-market-context')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }
  const routeByCode = {
    RISK_FIRST: '/portfolio',
    BUY_CONDITIONALLY: '/decision',
    WATCH_ALERTS: '/observe',
    REFRESH_DATA: '/sync',
  }
  const route = routeByCode[code]
  if (route) router.push(route)
}

function fmtPct(v, digits = 2) {
  if (v == null || v === '') return '-'
  return (Number(v) * 100).toFixed(digits) + '%'
}

function fmtFactor(value) {
  if (value == null || value === '') return '-'
  const factor = Number(value)
  if (Number.isNaN(factor)) return '-'
  return `${factor.toFixed(2)} 倍`
}

function fmtScore(v) {
  if (v == null || v === '') return '-'
  return Number(v).toFixed(0)
}

function fmtWeight(v) {
  if (v == null || v === '') return '-'
  return (Number(v) * 100).toFixed(1) + '%'
}

async function load(opts = {}) {
  const silent = !!opts.silent
  const hasCache = !!home.value
  if (!silent && !hasCache) loading.value = true
  refreshing.value = true
  loadError.value = ''
  try {
    const res = await dashboardHome(undefined, '我的自选', false)
    home.value = res.data
    publishDashboardDataFreshness(res.data)
    writeHomeCache(res.data)
  } catch (e) {
    if (!hasCache) {
      home.value = null
      const msg = e.message || '加载失败'
      loadError.value = msg.includes('404') || msg.includes('Not Found')
        ? '看板接口未就绪：请重启后端后重新进入看板（/api/dashboard/home）'
        : msg
      ElMessage.error(loadError.value)
    } else {
      ElMessage.warning('数据更新失败，仍展示本地缓存（可能过期）')
    }
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

onMounted(() => {
  const cached = readHomeCache()
  if (cached) {
    home.value = cached
    publishDashboardDataFreshness(cached)
    load({ silent: true })
  } else {
    load()
  }
})
</script>

<template>
  <div class="page dash" v-loading="loading">
    <header class="header dash-header">
      <div>
        <p class="eyebrow">Command</p>
        <h1>看板</h1>
        <p class="sub">
          {{
            home?.message
              || (loading || refreshing ? '正在加载市场与决策…' : '先看市场立场，再处理买卖行动')
          }}
        </p>
      </div>
    </header>

    <el-alert
      v-if="loadError"
      class="load-alert"
      type="error"
      show-icon
      :closable="false"
      :title="loadError"
    >
      <template #default>
        看板数据依赖 <code>/api/dashboard/home</code>。重启后端后重新进入看板；数据任务统一在同步中心执行。
      </template>
    </el-alert>

    <!-- ① 市场立场（始终占位，避免整块消失） -->
    <section
      class="stance-panel enter"
      :class="market ? stanceClass(market.stance) : 'stance-empty'"
    >
      <div class="stance-glow" aria-hidden="true" />
      <div class="stance-main">
        <div class="kicker">
          <span>
            市场立场
            <template v-if="dashboardMarketTime">· {{ dashboardMarketTime }}</template>
          </span>
          <el-tag
            v-if="market"
            size="small"
            effect="plain"
            :type="dataLevelType(market.dataLevel)"
            round
          >
            数据{{ dataLevelLabel(market.dataLevel) }}
          </el-tag>
          <el-tag v-else size="small" type="info" effect="plain" round>
            {{ loading || refreshing ? '加载中' : '未加载' }}
          </el-tag>
        </div>
        <div class="stance-title-row">
          <div
            class="score-ring"
            :style="{ '--pct': market ? scorePct : 0 }"
            :aria-label="`立场分 ${market?.stanceScore ?? '-'}`"
          >
            <div class="score-ring-inner">
              <strong>{{ market?.stanceScore ?? '-' }}</strong>
              <small>/100</small>
            </div>
          </div>
          <div class="stance-copy">
            <h2>
              <span class="pill">{{ market?.stance || '暂无' }}</span>
            </h2>
            <p class="reason">
              {{
                market?.stanceReason
                  || (loadError
                    ? '首页接口未返回市场简报，请先重启后端并重新进入看板'
                    : (loading || refreshing
                      ? '正在拉取市场简报…'
                      : '若长期空白，请先同步指数/板块/涨停'))
              }}
            </p>
            <p class="advice">
              {{
                command?.preMarketSummary?.headline
                  || market?.positionAdvice
                  || (loading || refreshing
                    ? '仓位建议加载中'
                    : '同步行情后，这里会给出进攻 / 均衡 / 防守与仓位建议')
              }}
            </p>
          </div>
        </div>
      </div>
      <div class="stance-side">
        <div class="market-block">
          <div class="market-top">
            <span class="market-title">市场概览</span>
            <div class="market-links">
              <button
                type="button"
                class="market-toggle"
                :aria-expanded="marketDetailOpen"
                aria-controls="dashboard-index-detail"
                @click="marketDetailOpen = !marketDetailOpen"
              >
                <span>{{ marketDetailOpen ? '收起指数' : '指数明细' }}</span>
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M7 10l5 5 5-5" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </button>
              <button type="button" class="text-link" @click="router.push('/market')">
                <span class="desktop-link-label">详情</span>
                <span class="mobile-link-label">行情页</span>
              </button>
            </div>
          </div>

          <div
            id="dashboard-index-detail"
            class="market-detail"
            :class="{ open: marketDetailOpen }"
          >
            <div class="index-lines">
              <template v-if="indexCards.length">
                <div
                  v-for="idx in indexCards.slice(0, 4)"
                  :key="idx.name"
                  class="index-line"
                >
                  <span class="n">{{ idx.name }}</span>
                  <span class="c" :class="idx.direction">{{ idx.close != null ? Number(idx.close).toFixed(2) : '-' }}</span>
                  <span class="p" :class="idx.direction">{{ fmtIndexPct(idx.pctChg) }}</span>
                </div>
              </template>
              <template v-else>
                <div v-for="name in ['上证指数', '深证成指', '创业板指', '科创50']" :key="name" class="index-line muted">
                  <span class="n">{{ name }}</span>
                  <span class="c">--</span>
                  <span class="p">--</span>
                </div>
              </template>
            </div>
          </div>

          <div class="stat-line">
            <span
              class="stat volume-stat"
              :title="market?.volumeVsMa5Pct != null ? `三市成交较上一交易日 ${fmtIndexPct(market.volumeVsMa5Pct)}` : '三市成交额（上证+深成+北证）'"
            >
              <em>三市</em>
              <b>{{ market?.indexVolumeText || '--' }}</b>
              <i
                v-if="volumeChangeParts?.detailText || volumeChangeParts?.percentageText"
                class="vol-change"
              >
                <span
                  v-if="volumeChangeParts.trendText"
                  class="vol-trend"
                  :class="{ 'is-contraction': volumeChangeParts.trendText === '缩量' }"
                >{{ volumeChangeParts.trendText }}</span>
                <span v-if="volumeChangeParts.amountText" class="vol-amount">{{ volumeChangeParts.amountText }}</span>
                <span
                  v-if="volumeChangeParts.percentageText"
                  class="vol-percentage"
                  :class="volumeDir(market?.volumeTrend, market?.volumeVsMa5Pct)"
                >
                  <span v-if="volumePercentageParts.sign" class="vol-sign">{{ volumePercentageParts.sign }}</span>
                  <span class="vol-number">{{ volumePercentageParts.number }}</span>
                </span>
              </i>
              <i v-else class="miss-hint">暂无今日额</i>
            </span>
            <span class="dot" aria-hidden="true" />
            <span
              class="stat"
              :title="breadth
                ? `涨跌比 ${breadth.ratio}${breadth.hasFlat ? ` · 平 ${breadth.flat}` : ''}`
                : '暂无全市场涨跌家数'"
            >
              <em>涨跌</em>
              <span class="stat-value">
                <template v-if="breadth">
                  <b class="up">{{ breadth.up }}</b>
                  <span class="slash">/</span>
                  <b class="flat">{{ breadth.hasFlat ? breadth.flat : '--' }}</b>
                  <span class="slash">/</span>
                  <b class="down">{{ breadth.down }}</b>
                </template>
                <b v-else class="miss">--</b>
              </span>
            </span>
            <span class="dot" aria-hidden="true" />
            <span
              class="stat"
              :title="market?.limitUpCount != null || market?.limitDownCount != null
                ? `涨停 ${market?.limitUpCount ?? '--'} / 跌停 ${market?.limitDownCount ?? '--'}`
                : '暂无涨跌停家数'"
            >
              <em>涨跌停</em>
              <span class="stat-value">
                <b class="up">{{ market?.limitUpCount ?? '--' }}</b>
                <span class="slash">/</span>
                <b class="down">{{ market?.limitDownCount ?? '--' }}</b>
              </span>
            </span>
          </div>
          <div v-if="breadth" class="breadth-track" aria-hidden="true">
            <i class="up-seg" :style="{ width: breadth.upPct + '%' }" />
            <i class="flat-seg" :style="{ width: breadth.flatPct + '%' }" />
            <i class="down-seg" :style="{ width: breadth.downPct + '%' }" />
          </div>
          <p v-if="breadthHint" class="breadth-hint">{{ breadthHint }}</p>
        </div>
      </div>
    </section>

    <section
      v-if="breadthForecast"
      class="breadth-forecast enter delay-1"
      :class="{ 'is-empty': !breadthForecast.available }"
      aria-label="盘前涨跌比预测"
    >
      <div v-if="breadthForecast.available" class="breadth-forecast-main">
        <div class="breadth-forecast-head">
          <div>
            <h3>盘前涨跌比预测</h3>
            <p>平盘剔除 · {{ fmtBriefingTime(breadthForecast.generatedAt) }} 固化</p>
          </div>
          <span class="breadth-forecast-confidence">{{ breadthForecast.confidence || '低' }}置信度</span>
        </div>
        <div class="breadth-forecast-tug" :aria-label="`预测上涨 ${fmtForecastRatio(breadthForecast.predictedUpRatio)}，预测下跌 ${fmtForecastRatio(breadthForecast.predictedDownRatio)}`">
          <div class="breadth-forecast-side up">
            <span>预测上涨</span>
            <strong>{{ fmtForecastRatio(breadthForecast.predictedUpRatio) }}</strong>
          </div>
          <div class="breadth-forecast-track" aria-hidden="true">
            <i class="breadth-forecast-up" :style="{ width: `${forecastRatio(breadthForecast.predictedUpRatio)}%` }" />
            <i class="breadth-forecast-down" :style="{ width: `${forecastRatio(breadthForecast.predictedDownRatio)}%` }" />
          </div>
          <div class="breadth-forecast-side down">
            <span>预测下跌</span>
            <strong>{{ fmtForecastRatio(breadthForecast.predictedDownRatio) }}</strong>
          </div>
        </div>
        <div class="breadth-forecast-evidence">
          <span v-for="reason in breadthForecast.reasons || []" :key="reason">{{ reason }}</span>
        </div>
      </div>
      <div v-else class="breadth-forecast-empty">
        <h3>盘前涨跌比预测</h3>
        <p>{{ breadthForecast.message || '盘前数据尚未齐全' }}</p>
      </div>
      <div v-if="breadthForecast.available" class="breadth-forecast-backtest">
        <template v-if="breadthForecast.settled">
          <span class="breadth-forecast-backtest-title">收盘回测</span>
          <span>实际上涨 {{ fmtForecastRatio(breadthForecast.actualUpRatio) }}</span>
          <span>实际下跌 {{ fmtForecastRatio(breadthForecast.actualDownRatio) }}</span>
          <span :class="breadthForecast.directionHit ? 'is-hit' : 'is-miss'">
            {{ breadthForecast.directionHit ? '方向命中' : '方向未命中' }}
          </span>
          <span>偏差 {{ Number(breadthForecast.absoluteError || 0).toFixed(2) }} 个百分点</span>
        </template>
        <span v-else class="breadth-forecast-pending">{{ breadthForecast.message }}</span>
        <p v-if="breadthForecast.analysisSummary">{{ breadthForecast.analysisSummary }}</p>
        <p class="breadth-forecast-rolling">{{ breadthForecast.rollingBacktestSummary }}</p>
      </div>
    </section>

    <section v-if="effect" class="effect-strip enter delay-1" aria-label="赚钱效应">
      <div class="effect-head">
        <span class="effect-title"><TermTip term="money_effect">赚钱效应</TermTip></span>
        <span v-if="effect.hint" class="effect-hint">{{ effect.hint }}</span>
        <span v-else class="effect-hint muted">平均股价 · 中位数 · 全A等权 · 微盘股 · 中证1000 · 沪深300</span>
      </div>
      <div class="effect-grid">
        <div class="effect-cell" :class="pctDir(effect.avgPctChg)" title="800005 平均股价指数涨跌幅">
          <em>平均股价</em>
          <b>{{ fmtIndexPct(effect.avgPctChg) }}</b>
        </div>
        <div class="effect-cell" :class="pctDir(effect.medianPctChg)" title="880009 口径：全A涨幅中位数">
          <em>中位数</em>
          <b>{{ fmtIndexPct(effect.medianPctChg) }}</b>
        </div>
        <div class="effect-cell" :class="pctDir(effect.equalWeightPctChg)" title="800010 优先；缺失时使用全A截面算术平均">
          <em>全A等权</em>
          <b>{{ fmtIndexPct(effect.equalWeightPctChg) }}</b>
        </div>
        <div class="effect-cell" :class="pctDir(effect.microPctChg ?? effect.csi2000PctChg)" title="800007 Choice微盘，对齐 880823">
          <em>微盘股</em>
          <b>{{ fmtIndexPct(effect.microPctChg ?? effect.csi2000PctChg) }}</b>
        </div>
        <div class="effect-cell" :class="pctDir(effect.csi1000PctChg)" title="000852 中证1000">
          <em>中证1000</em>
          <b>{{ fmtIndexPct(effect.csi1000PctChg) }}</b>
        </div>
        <div class="effect-cell" :class="pctDir(effect.hs300PctChg)" title="000300 沪深300">
          <em>沪深300</em>
          <b>{{ fmtIndexPct(effect.hs300PctChg) }}</b>
        </div>
      </div>
    </section>

    <section v-if="command" class="command-band enter delay-1" aria-label="开盘准备">
      <div class="command-head">
        <div>
          <h3>开盘准备</h3>
          <p>{{ commandDataTimeText }}</p>
        </div>
        <span class="command-status" :class="`status-${String(command.status || '').toLowerCase()}`">
          {{ commandStatusLabel(command.status) }}
        </span>
      </div>

      <div class="command-grid">
        <div class="command-column command-summary">
          <div class="command-column-head">
            <h4>今日重点</h4>
          </div>
          <p
            v-if="command.status !== 'GENERATING' && decision?.hasToday && command.preMarketSummary?.headline"
            class="command-headline"
          >
            {{ command.preMarketSummary.headline }}
          </p>

          <div v-if="command.preMarketSummary?.forecast?.marketOutlook" class="command-forecast">
            <span class="command-forecast-label">{{ isIntradayCommand ? '盘中判断' : '今日预测' }}</span>
            <p>{{ command.preMarketSummary.forecast.marketOutlook }}</p>

            <div
              v-if="command.preMarketSummary.forecast.focusItems?.length || command.preMarketSummary.forecast.riskItems?.length"
              class="command-forecast-grid"
            >
              <div v-if="command.preMarketSummary.forecast.focusItems?.length" class="command-forecast-direction focus">
                <strong>关注方向</strong>
                <span
                  v-for="item in command.preMarketSummary.forecast.focusItems"
                  :key="`${item.name}-${item.reason}`"
                >
                  <b>{{ item.name }}</b>{{ item.reason ? `：${item.reason}` : '' }}
                  <small v-if="item.watchStocks?.length">候选 {{ item.watchStocks.join('、') }}</small>
                </span>
              </div>
              <div v-if="command.preMarketSummary.forecast.riskItems?.length" class="command-forecast-direction risk">
                <strong>回避方向</strong>
                <span
                  v-for="item in command.preMarketSummary.forecast.riskItems"
                  :key="`${item.name}-${item.reason}`"
                >
                  <b>{{ item.name }}</b>{{ item.reason ? `：${item.reason}` : '' }}
                </span>
              </div>
            </div>

            <div v-if="command.preMarketSummary.forecast.watchConditions?.length" class="command-forecast-watch">
              <span>盘中确认</span>
              <p
                v-for="item in command.preMarketSummary.forecast.watchConditions"
                :key="`${item.title}-${item.condition}`"
              >
                <b>{{ item.title }}</b>{{ item.condition ? `：${item.condition}` : '' }}
              </p>
            </div>
          </div>

          <div
            v-if="command.status !== 'GENERATING' && decision?.hasToday && command.preMarketSummary?.watchConditions?.length"
            class="command-watch"
          >
            <span>{{ command.status === 'READY' ? '取消条件' : '恢复条件' }}</span>
            <p v-for="item in command.preMarketSummary.watchConditions" :key="`${item.title}-${item.condition}`">
              <b>{{ item.title }}</b>{{ item.condition ? `：${item.condition}` : '' }}
            </p>
          </div>
        </div>

        <div class="command-column command-guide">
          <div class="command-column-head">
            <h4>执行清单</h4>
          </div>
          <p v-if="!commandOperationItems.length" class="command-guide-summary">
            {{ command.operationGuide?.summary || command.operationGuide?.blockedReason || '今日操作指引待生成' }}
          </p>
          <div
            v-if="command.operationGuide && command.status === 'READY' && hasExecutableNewPosition"
            class="command-position"
          >
            <span>
              目标仓位
              <b>{{ fmtPct(command.operationGuide.targetPositionMin, 0) }} - {{ fmtPct(command.operationGuide.targetPositionMax, 0) }}</b>
            </span>
            <span>新仓系数 <b>{{ fmtFactor(command.operationGuide.newPositionFactor) }}</b></span>
          </div>
          <p
            v-if="command.operationGuide?.blockedReason && !commandOperationItems.length"
            class="command-blocked-reason"
          >
            {{ command.operationGuide.blockedReason }}
          </p>
          <div v-if="commandOperationItems.length" class="command-actions">
            <button
              v-for="item in commandOperationItems"
              :key="`${item.priority}-${item.code}`"
              type="button"
              class="command-action"
              @click="openCommandAction(item.code)"
            >
              <span class="command-action-order">{{ item.priority }}</span>
              <span class="command-action-copy">
                <span class="command-action-title">
                  <b>{{ item.title }}</b>
                  <em>{{ operationStatusLabel(item.status) }}</em>
                  <span v-if="Number(item.targetCount) > 0" class="command-target-count">
                    {{ item.targetCount }} 项
                  </span>
                </span>
                <span>{{ item.actionText }}</span>
                <small v-if="item.conditionText">{{ item.conditionText }}</small>
              </span>
              <span class="command-action-arrow" aria-hidden="true">→</span>
            </button>
          </div>
        </div>
      </div>
    </section>

    <section id="pre-market-context" class="morning-context enter delay-1" aria-label="盘前依据">
      <div class="morning-context-head">
        <div class="morning-context-title">
          <h3>盘前依据</h3>
          <p>美股收盘表现与今日重要资讯</p>
        </div>
        <div class="morning-context-meta">
          <div class="morning-context-status">
            <el-tag
              v-if="morningBriefing"
              size="small"
              effect="plain"
              :type="dataLevelType(morningBriefing.dataLevel)"
            >
              {{ dataLevelLabel(morningBriefing.dataLevel) }}
            </el-tag>
            <span v-if="morningBriefingTime" class="morning-context-time">
              <time>{{ morningBriefingTime }}</time>
            </span>
            <span v-if="morningBriefing?.refreshing" class="morning-context-refreshing">
              正在刷新，显示上一份晨报
            </span>
          </div>
          <el-button class="morning-context-link" link type="primary" @click="router.push('/news')">
            消息面
            <span aria-hidden="true">→</span>
          </el-button>
        </div>
      </div>

      <div
        class="morning-context-grid"
        :class="{ 'is-market-collapsed': !morningMarketExpanded }"
      >
        <div class="morning-context-block overnight-block">
          <div class="morning-block-head">
            <h4>开盘影响</h4>
            <span>{{ hasOpeningAuction ? '竞价确认与外盘锚' : '外盘锚' }}</span>
          </div>

          <div v-if="hasOpeningAuction" class="overnight-layer">
            <div class="overnight-layer-head">
              <h5>集合竞价确认</h5>
              <span>{{ openingAuction?.stateDesc || '等待竞价状态' }}</span>
            </div>
            <div class="opening-auction-grid">
              <div v-for="item in openingAuction.indexes" :key="item.code" class="overnight-quote">
                <div class="overnight-quote-name">
                  <strong>{{ item.name || item.code }}</strong>
                  <small v-if="fmtQuotePrice(item.latestPrice)">{{ fmtQuotePrice(item.latestPrice) }}</small>
                </div>
                <b :class="pctDir(item.pctChg)">{{ fmtIndexPct(item.pctChg) }}</b>
              </div>
            </div>
          </div>

          <div class="overnight-layer">
            <div class="overnight-layer-head">
              <h5>三大指数</h5>
              <span>收盘表现</span>
            </div>
            <div v-if="overnightIndexes.length" class="overnight-index-grid">
              <div v-for="quote in overnightIndexes" :key="quote.symbol" class="overnight-quote">
                <div class="overnight-quote-name">
                  <strong>{{ quote.name || quote.symbol }}</strong>
                  <small v-if="fmtQuotePrice(quote.latestPrice)">{{ fmtQuotePrice(quote.latestPrice) }}</small>
                </div>
                <b :class="pctDir(quote.pctChg)">{{ fmtIndexPct(quote.pctChg) }}</b>
              </div>
            </div>
            <p v-else class="morning-context-empty">
              {{ loading || refreshing ? '正在读取隔夜行情…' : '隔夜行情暂未获取' }}
            </p>
          </div>

          <button
            type="button"
            class="morning-disclosure"
            :aria-expanded="morningMarketExpanded"
            aria-controls="morning-market-more"
            @click="morningMarketExpanded = !morningMarketExpanded"
          >
            <span>{{ morningMarketExpanded ? '收起次要行情' : '展开更多行情' }}</span>
            <span class="morning-disclosure-arrow" aria-hidden="true">{{ morningMarketExpanded ? '↑' : '↓' }}</span>
          </button>

          <div id="morning-market-more" class="morning-context-more" v-show="morningMarketExpanded">
            <div class="overnight-layer">
              <div class="overnight-layer-head">
                <h5>A股盘前</h5>
                <span>夜盘开盘参考</span>
              </div>
              <div v-if="ftseA50Future" class="overnight-index-grid a50-future-grid">
                <div class="overnight-quote">
                  <div class="overnight-quote-name">
                    <strong>富时 A50 期指连续</strong>
                    <small v-if="fmtQuotePrice(ftseA50Future.latestPrice)">{{ fmtQuotePrice(ftseA50Future.latestPrice) }}</small>
                  </div>
                  <b :class="pctDir(ftseA50Future.pctChg)">{{ fmtIndexPct(ftseA50Future.pctChg) }}</b>
                </div>
              </div>
              <p v-else class="morning-context-empty">富时 A50 期指连续暂未获取</p>
            </div>

            <div class="overnight-layer">
            <div class="overnight-layer-head">
              <h5>亚太情绪</h5>
              <span>盘前可用的亚太指数</span>
            </div>
            <div v-if="asiaIndexes.length" class="asia-index-grid">
              <div v-for="quote in asiaIndexes" :key="quote.symbol" class="overnight-quote">
                <div class="overnight-quote-name">
                  <strong>{{ quote.name || quote.symbol }}</strong>
                  <small v-if="fmtQuotePrice(quote.latestPrice)">{{ fmtQuotePrice(quote.latestPrice) }}</small>
                </div>
                <b :class="pctDir(quote.pctChg)">{{ fmtIndexPct(quote.pctChg) }}</b>
              </div>
            </div>
            <p v-else class="morning-context-empty">亚太指数暂未获取</p>
          </div>

          <div class="overnight-layer">
            <div class="overnight-layer-head">
              <h5>外围环境</h5>
              <span>外盘锚与行业映射</span>
            </div>
            <div v-if="externalMarketItems.length" class="external-market-grid">
              <article v-for="item in externalMarketItems" :key="item.code" class="external-market-card">
                <div class="external-market-quote">
                  <strong>{{ item.name }}</strong>
                  <b v-if="item.available" :class="pctDir(item.pctChg)">{{ fmtIndexPct(item.pctChg) }}</b>
                  <span v-else>暂未获取</span>
                </div>
                <span v-if="item.available && fmtQuotePrice(item.latestPrice)" class="external-market-price">
                  {{ fmtQuotePrice(item.latestPrice) }}
                </span>
                <p>
                  <span>对 A 股：</span>{{ item.aShareImpact || item.ashareImpact || '影响说明暂未获取' }}
                </p>
              </article>
            </div>
            <p v-else class="morning-context-empty">外围环境指标暂未获取</p>
            <p class="external-market-note">
              已获取 {{ externalMarketAvailableCount }}/5 项。它们是影响 A 股开盘情绪的外部线索，并非单独买卖信号。
            </p>
          </div>

          <div class="overnight-layer">
            <div class="overnight-layer-head">
              <h5>主题情绪</h5>
              <span>按涨跌幅中位数排序</span>
            </div>
            <div v-if="overnightThemes.length" class="overnight-theme-grid">
              <div
                v-for="(theme, index) in overnightThemes"
                :key="theme.code || theme.name"
                class="overnight-theme"
              >
                <span class="overnight-theme-rank">{{ index + 1 }}</span>
                <div class="overnight-theme-copy">
                  <strong>{{ theme.name }}</strong>
                  <small v-if="theme.leaderQuote">
                    领涨 {{ theme.leaderQuote.name || theme.leaderQuote.symbol }}
                    {{ fmtIndexPct(theme.leaderQuote.pctChg) }}
                  </small>
                </div>
                <div class="overnight-theme-stats">
                  <b :class="pctDir(theme.medianPctChg)">{{ fmtIndexPct(theme.medianPctChg) }}</b>
                  <span class="overnight-theme-breadth">
                    <span class="overnight-theme-breadth-track" aria-hidden="true">
                      <i :style="{ width: `${themeUpPct(theme)}%` }" />
                    </span>
                    <span>{{ theme.upCount ?? 0 }}/{{ theme.quoteCount ?? 0 }} 上涨</span>
                  </span>
                </div>
              </div>
            </div>
            <p v-else class="morning-context-empty">主题情绪暂未生成</p>
          </div>

          <div class="overnight-layer">
            <div class="overnight-layer-head">
              <h5>明星异动</h5>
              <span>按绝对涨跌幅排序</span>
            </div>
            <div v-if="overnightStars.length" class="overnight-star-grid">
              <div v-for="quote in overnightStars" :key="quote.symbol" class="overnight-quote">
                <div class="overnight-quote-name">
                  <strong>{{ quote.name || quote.symbol }}</strong>
                  <small v-if="fmtQuotePrice(quote.latestPrice)">{{ fmtQuotePrice(quote.latestPrice) }}</small>
                </div>
                <b :class="pctDir(quote.pctChg)">{{ fmtIndexPct(quote.pctChg) }}</b>
              </div>
            </div>
            <p v-else class="morning-context-empty">明星异动暂未生成</p>
            </div>
          </div>
        </div>

        <div class="morning-context-block morning-news-block">
          <div class="morning-block-head">
            <h4>今日消息面</h4>
            <div v-if="newsPulse" class="news-counts">
              <span class="bull">利好 <b>{{ newsPulse.bullCount ?? 0 }}</b></span>
              <span class="bear">利空 <b>{{ newsPulse.bearCount ?? 0 }}</b></span>
              <span class="news-bias">{{ newsPulse.biasLabel || '中性' }}</span>
            </div>
          </div>
          <div class="morning-news-lead">
            <span class="morning-news-summary-label">核心结论</span>
            <p class="morning-news-summary">
              {{
                newsPulse?.executiveSummary
                  || morningBriefing?.summary
                  || (loading || refreshing ? '正在生成消息面摘要…' : '今日消息面暂未生成')
              }}
            </p>
          </div>
          <section v-if="preMarketEventImpacts.length" class="pre-market-event-impact" aria-label="盘前事件影响">
            <div class="pre-market-event-head">
              <h5>盘前事件影响</h5>
              <span>按重要度排序</span>
            </div>
            <article v-for="item in visiblePreMarketEventImpacts" :key="`${item.eventType}-${item.title}`" class="pre-market-event-item">
              <div class="pre-market-event-meta">
                <span class="pre-market-event-type">{{ item.eventTypeName }}</span>
                <span>{{ item.impactScopeName || item.impactScope }}</span>
                <span :class="item.direction === '利好' ? 'bull' : item.direction === '利空' ? 'bear' : ''">
                  {{ item.direction }}
                </span>
                <span>{{ item.verificationStatus }}</span>
              </div>
              <a v-if="item.url" :href="item.url" target="_blank" rel="noopener noreferrer">{{ item.title }}</a>
              <strong v-else>{{ item.title }}</strong>
              <p v-if="morningNewsExpanded">{{ item.impactExplanation }}</p>
              <div
                v-if="morningNewsExpanded && (item.relatedCodes?.length || item.themes?.length)"
                class="pre-market-event-targets"
              >
                <span v-for="code in item.relatedCodes || []" :key="code">{{ code }}</span>
                <span v-for="theme in item.themes || []" :key="theme">{{ theme }}</span>
              </div>
            </article>
          </section>
          <button
            v-if="hasMoreMorningNews"
            type="button"
            class="morning-disclosure"
            :aria-expanded="morningNewsExpanded"
            aria-controls="morning-news-more"
            @click="morningNewsExpanded = !morningNewsExpanded"
          >
            <span>{{ morningNewsExpanded ? '收起次要信息' : '展开更多信息' }}</span>
            <span class="morning-disclosure-arrow" aria-hidden="true">{{ morningNewsExpanded ? '↑' : '↓' }}</span>
          </button>

          <div id="morning-news-more" class="morning-context-more" v-show="morningNewsExpanded">
            <div v-if="morningNewsCards.length" class="morning-news-list">
              <article v-for="item in morningNewsCards" :key="item.id || item.title" class="morning-news-item">
                <span
                  class="news-sentiment"
                  :class="item.sentiment === '利好' ? 'bull' : item.sentiment === '利空' ? 'bear' : ''"
                >
                  {{ item.sentiment || '要闻' }}
                </span>
                <a v-if="item.url" :href="item.url" target="_blank" rel="noopener noreferrer">{{ item.title }}</a>
                <span v-else class="morning-news-title">{{ item.title }}</span>
                <small v-if="item.source">{{ newsSourceLabel(item.source) }}</small>
              </article>
            </div>

            <section v-if="marketOpinion" class="opinion-radar" aria-label="市场观点雷达">
              <div class="opinion-radar-head">
                <h5>观点雷达</h5>
                <time>{{ fmtBriefingTime(marketOpinion.snapshotTime) }}</time>
              </div>
              <div class="opinion-summary-grid">
                <p><span>共识</span>{{ marketOpinion.consensus || '暂未形成有效共识' }}</p>
                <p><span>分歧</span>{{ marketOpinion.divergence || '暂未发现明确分歧' }}</p>
              </div>
              <div v-if="traderSeatViews.length" class="opinion-group">
                <div class="opinion-group-head"><h6>已核验游资席位</h6><span>营业部标签映射</span></div>
                <article v-for="item in traderSeatViews" :key="[item.url, item.subjectName, item.publishedAt].join('|')" class="opinion-item">
                  <span class="opinion-direction seat">席位</span>
                  <a v-if="item.actorEvidenceUrl" :href="item.actorEvidenceUrl" target="_blank" rel="noopener noreferrer">{{ item.actorName }}</a>
                  <span v-else class="opinion-item-title">{{ item.actorName }}</span>
                  <small>{{ item.subjectName }} · {{ item.summary || '涉及股票未披露' }} · {{ formatOpinionAmount(item.netAmount) || '金额未披露' }}</small>
                </article>
              </div>
              <div v-if="activeSeats.length" class="opinion-group">
                <div class="opinion-group-head"><h6>龙虎榜活跃营业部</h6><span>仅表示公开席位行为，不代表具体自然人观点</span></div>
                <article v-for="item in activeSeats" :key="[item.url, item.subjectName, item.publishedAt].join('|')" class="opinion-item">
                  <span class="opinion-direction seat">营业部</span>
                  <a v-if="item.url" :href="item.url" target="_blank" rel="noopener noreferrer">{{ item.subjectName }}</a>
                  <span v-else class="opinion-item-title">{{ item.subjectName }}</span>
                  <small>{{ item.summary || '涉及股票未披露' }} · {{ formatOpinionAmount(item.netAmount) || '金额未披露' }}</small>
                </article>
              </div>
              <div v-if="kolViews.length || kolSources.length" class="opinion-group">
                <div class="opinion-group-head"><h6>公开账号观点</h6><span>原帖可追溯</span></div>
                <article v-for="item in kolViews" :key="item.url || item.title" class="opinion-item">
                  <span class="opinion-direction seat">原帖</span>
                  <a v-if="item.url" :href="item.url" target="_blank" rel="noopener noreferrer">{{ item.title }}</a>
                  <span v-else class="opinion-item-title">{{ item.title }}</span>
                  <small>{{ item.actorName || item.subjectName }} · {{ opinionTime(item.publishedAt) }}</small>
                </article>
                <div v-if="kolSources.length" class="opinion-source-list">
                  <span v-for="source in kolSources" :key="source.actorName" :title="source.sourceNote">
                    <a v-if="source.accountUrl" :href="source.accountUrl" target="_blank" rel="noopener noreferrer">{{ source.actorName }}</a>
                    <b v-else>{{ source.actorName }}</b>
                    <i>{{ source.platform || '公开源' }} · {{ opinionSourceStatusLabel(source.sourceStatus) }}</i>
                  </span>
                </div>
              </div>
              <div v-if="institutionViews.length" class="opinion-group">
                <div class="opinion-group-head"><h6>机构观点</h6><span>公开研报</span></div>
                <article v-for="item in institutionViews" :key="item.url || item.title" class="opinion-item">
                  <span class="opinion-direction" :class="opinionTone(item.direction)">{{ item.direction || '未评级' }}</span>
                  <button v-if="item.url" type="button" class="opinion-item-link" @click="openOpinionPreview(item)">{{ item.title }}</button>
                  <span v-else class="opinion-item-title">{{ item.title }}</span>
                  <small>{{ item.subjectName }} · {{ item.relatedName || item.topic || opinionTime(item.publishedAt) }}</small>
                </article>
              </div>
              <p class="opinion-kol-status">{{ marketOpinion.kolSourceStatus }}</p>
            </section>
          </div>
        </div>
      </div>
    </section>

    <el-dialog v-model="opinionPreviewOpen" title="研报预览" class="opinion-preview-dialog" width="960px" destroy-on-close>
      <div v-if="opinionPreview" class="opinion-preview-body">
        <p>{{ opinionPreview.subjectName }} · {{ opinionPreview.title }}</p>
        <iframe :src="opinionPreview.url" :title="opinionPreview.title" class="opinion-preview-frame" />
      </div>
      <template #footer>
        <el-button @click="opinionPreviewOpen = false">关闭</el-button>
        <el-button type="primary" @click="openOpinionInNewTab">在新标签打开</el-button>
      </template>
    </el-dialog>

    <section v-if="observeAlerts.length" class="panel observe-strip enter delay-1">
      <div class="panel-head">
        <div>
          <h3>观察池提醒</h3>
          <p class="panel-desc">接近触发 / 已触发，优先处理</p>
        </div>
        <el-button link type="primary" @click="router.push('/observe')">打开观察池 →</el-button>
      </div>
      <div class="observe-chips">
        <button
          v-for="item in observeAlerts"
          :key="item.id"
          type="button"
          class="observe-chip"
          :class="item.status === 'TRIGGERED' ? 'trig' : 'near'"
          @click="router.push(`/stock/${item.code}`)"
        >
          <StockIdentity class="observe-chip__identity" :security="item" compact />
          <em>
            <span class="observe-chip__status-dot" aria-hidden="true"></span>
            {{ item.status === 'TRIGGERED' ? '已触发' : '接近' }}
          </em>
        </button>
      </div>
    </section>

    <div class="two-col">
      <!-- ② 今日决策 -->
      <section class="panel action-panel enter delay-1">
        <div class="panel-head">
          <div>
            <h3>今日决策</h3>
            <p class="panel-desc">买入机会 Top3</p>
          </div>
          <el-button link type="primary" @click="router.push('/decision')">全部</el-button>
        </div>

        <div class="panel-meta">
          <div class="meta-line">
            <span class="meta-date">{{ decision?.actionDate || '-' }}</span>
            <span v-if="decision?.hasToday" class="meta-counts">
              买 {{ decision.buyCount ?? 0 }}
              · 卖 {{ decision.sellCount ?? 0 }}
              · 可执行 {{ decision.executableCount ?? 0 }}
            </span>
            <span v-else class="meta-counts">{{ loading || refreshing ? '加载中…' : '尚无清单' }}</span>
          </div>
          <p class="meta-note">
            {{
              decision?.riskNote
                || (loading || refreshing ? '正在读取今日决策…' : '生成决策后显示买入 Top3 与仓位建议')
            }}
          </p>
          <div
            v-if="decision?.hasToday && valuationDistTotal > 0"
            class="val-dist"
            :title="`低估 ${decision.valuationCheapCount ?? 0} · 合理 ${decision.valuationFairCount ?? 0} · 高估 ${decision.valuationRichCount ?? 0}`"
          >
            <i class="cheap" :style="{ flex: decision.valuationCheapCount || 0 }" />
            <i class="fair" :style="{ flex: decision.valuationFairCount || 0 }" />
            <i class="rich" :style="{ flex: decision.valuationRichCount || 0 }" />
          </div>
          <div v-else class="val-dist val-dist-placeholder" aria-hidden="true" />
        </div>

        <div class="panel-body">
          <el-empty
            v-if="!decision?.hasToday"
            class="dash-empty"
            :description="loading || refreshing ? '决策加载中…' : '生成后将在此显示买入 Top3'"
            :image-size="56"
          >
            <el-button
              v-if="!loading && !refreshing"
              type="primary"
              size="small"
              @click="router.push('/sync')"
            >
              去同步中心
            </el-button>
          </el-empty>
          <template v-else>
            <el-table
              :data="topBuys"
              size="small"
              class="dash-table desktop-action-table"
              empty-text="暂无买入建议"
              stripe
            >
              <el-table-column prop="name" label="股票" width="120">
                <template #default="{ row }">
                  <StockIdentity :security="row" interactive compact @select="router.push(`/stock/${row.code}`)" />
                </template>
              </el-table-column>
              <el-table-column prop="strategyId" label="策略" width="52" />
              <el-table-column label="评分" width="96">
                <template #default="{ row }">
                  <ScoreBar :score="row.score" />
                </template>
              </el-table-column>
              <el-table-column label="估值" width="72">
                <template #default="{ row }">
                  <span class="muted">{{ row.valuationLabel || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="仓位" width="60">
                <template #default="{ row }">
                  <span class="num">{{ fmtWeight(row.suggestedWeight) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="联动" min-width="124" class-name="action-cues-col">
                <template #default="{ row }">
                  <div class="action-cues">
                    <el-tag
                      v-if="row.executableHint"
                      size="small"
                      type="success"
                      effect="plain"
                    >可执行</el-tag>
                    <el-tag
                      v-if="row.linkHint"
                      class="link-hint-tag"
                      size="small"
                      effect="plain"
                      :type="String(row.linkHint).includes('降权') ? 'danger' : 'success'"
                    >{{ row.linkHint }}</el-tag>
                    <el-tag
                      v-if="row.mainlineMatch"
                      class="mainline-hint-tag"
                      size="small"
                      type="warning"
                      effect="light"
                    >{{ row.mainlineName || '匹配' }}</el-tag>
                    <span
                      v-if="!row.executableHint && !row.linkHint && !row.mainlineMatch"
                      class="muted"
                    >-</span>
                  </div>
                </template>
              </el-table-column>
            </el-table>

            <div class="mobile-action-list" aria-label="今日买入机会">
              <p v-if="!topBuys.length" class="mobile-action-empty">暂无买入建议</p>
              <button
                v-for="row in topBuys"
                :key="row.code"
                type="button"
                class="mobile-action-item"
                @click="router.push(`/stock/${row.code}`)"
              >
                <span class="mobile-action-primary">
                  <span class="mobile-stock">
                    <StockIdentity :security="row" compact />
                  </span>
                  <span class="mobile-score">
                    <em>评分</em>
                    <ScoreBar :score="row.score" />
                  </span>
                </span>
                <span class="mobile-action-details">
                  <span><em>策略</em><b>{{ row.strategyId || '-' }}</b></span>
                  <span><em>估值</em><b>{{ row.valuationLabel || '-' }}</b></span>
                  <span><em>仓位</em><b class="num">{{ fmtWeight(row.suggestedWeight) }}</b></span>
                </span>
                <span
                  v-if="row.executableHint || row.linkHint || row.mainlineMatch"
                  class="mobile-action-tags"
                >
                  <span v-if="row.executableHint" class="mobile-action-tag executable">可执行</span>
                  <span
                    v-if="row.linkHint"
                    class="mobile-action-tag"
                    :class="String(row.linkHint).includes('降权') ? 'negative' : 'positive'"
                  >{{ row.linkHint }}</span>
                  <span v-if="row.mainlineMatch" class="mobile-action-tag mainline">
                    主线 · {{ row.mainlineName || '匹配' }}
                  </span>
                </span>
              </button>
            </div>
          </template>
        </div>
      </section>

      <!-- ③ 持仓行动 -->
      <section class="panel action-panel enter delay-2">
        <div class="panel-head">
          <div>
            <h3>持仓行动</h3>
            <p class="panel-desc">止损止盈与策略卖出优先</p>
          </div>
          <el-button link type="primary" @click="router.push('/portfolio')">我的组合</el-button>
        </div>

        <div class="panel-meta">
          <div class="meta-line">
            <span class="meta-date">{{ decision?.actionDate || '-' }}</span>
            <span class="meta-counts">
              卖点 {{ topSells.length }}
              · 优先风控 / 策略卖出
            </span>
          </div>
          <p class="meta-note">对照今日决策清单执行，卖出优先处理</p>
          <div class="val-dist val-dist-placeholder" aria-hidden="true" />
        </div>

        <div class="panel-body">
          <el-empty
            v-if="!topSells.length"
            class="dash-empty"
            :description="loading || refreshing ? '卖点加载中…' : '持仓暂无卖点'"
            :image-size="56"
          />
          <template v-else>
            <el-table
              :data="topSells"
              size="small"
              class="dash-table desktop-action-table"
              stripe
            >
              <el-table-column prop="name" label="股票" width="120">
                <template #default="{ row }">
                  <StockIdentity :security="row" interactive compact @select="router.push(`/stock/${row.code}`)" />
                </template>
              </el-table-column>
              <el-table-column label="策略" width="72">
                <template #default="{ row }">
                  <span :class="row.strategyId === 'RISK' ? 'risk-tag' : ''">
                    {{ row.strategyId === 'RISK' ? '风控' : row.strategyId || '-' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="评分" width="100">
                <template #default="{ row }">
                  <ScoreBar :score="row.score" />
                </template>
              </el-table-column>
              <el-table-column prop="exitRule" label="触发" min-width="160" show-overflow-tooltip />
            </el-table>

            <div class="mobile-action-list" aria-label="持仓卖出行动">
              <button
                v-for="row in topSells"
                :key="row.code"
                type="button"
                class="mobile-action-item"
                @click="router.push(`/stock/${row.code}`)"
              >
                <span class="mobile-action-primary">
                  <span class="mobile-stock-with-strategy">
                    <StockIdentity :security="row" compact />
                    <span
                      class="mobile-strategy-badge"
                      :class="{ 'is-risk': row.strategyId === 'RISK' }"
                    >
                      {{ row.strategyId === 'RISK' ? '风控' : row.strategyId || '-' }}
                    </span>
                  </span>
                  <span class="mobile-score">
                    <em>评分</em>
                    <ScoreBar :score="row.score" />
                  </span>
                </span>
                <span class="mobile-exit-rule">
                  <em>触发</em>
                  <span>{{ row.exitRule || row.reason || '-' }}</span>
                </span>
              </button>
            </div>
          </template>
        </div>
      </section>
    </div>

    <!-- ④ 主线与情绪 -->
    <section class="panel enter delay-3">
      <div class="panel-head">
        <div>
          <h3>主线与情绪</h3>
          <p class="panel-desc">今日关注题材与操作提示</p>
        </div>
        <div class="panel-links">
          <el-button link type="primary" @click="router.push({ path: '/market', query: { tab: 'sector' } })">板块</el-button>
          <el-button link type="primary" @click="router.push('/limit-up')">连板天梯</el-button>
        </div>
      </div>
      <div v-if="themes.length" class="theme-row">
        <span
          v-for="(t, i) in themes"
          :key="t.key"
          class="theme-chip"
          :style="{ '--i': i }"
        >
          <span class="theme-name">{{ t.name }}</span>
          <span v-if="t.pctText" class="theme-pct" :class="t.pctDir">{{ t.pctText }}</span>
        </span>
      </div>
      <div v-else class="empty-guide">
        <p>{{ loading || refreshing ? '主线加载中…' : '暂无主线题材' }}</p>
        <span v-if="!loading && !refreshing">先在同步中心刷新「板块行情」，看板才会显示今日主线芯片与操作提示。</span>
        <el-button v-if="!loading && !refreshing" size="small" round @click="router.push('/sync')">去同步板块</el-button>
      </div>
      <div v-if="tips.length" class="tip-list">
        <div v-for="(t, i) in tips" :key="i" class="tip-item">
          <span class="tip-dot" />
          <span>{{ t }}</span>
        </div>
      </div>
    </section>

    <!-- ⑤ 数据可信度 -->
    <section
      class="panel health enter delay-5"
      :class="'lvl-' + (dataHealth?.level || 'yellow').toLowerCase()"
    >
      <div class="health-bar" aria-hidden="true" />
      <div class="health-body">
        <div class="panel-head">
          <div>
            <h3>数据可信度</h3>
            <p class="panel-desc">
              {{
                dataHealth?.suggestion
                  || '尚未拿到数据健康状态；重启后端并刷新后会显示自选/K线覆盖情况'
              }}
            </p>
          </div>
          <el-tag
            size="small"
            effect="dark"
            :type="dataLevelType(dataHealth?.level || 'YELLOW')"
            round
          >
            {{ dataLevelLabel(dataHealth?.level || 'YELLOW') }}
          </el-tag>
        </div>
        <div class="health-stats">
          <span>自选 <b>{{ dataHealth?.watchlistCount ?? '-' }}</b></span>
          <span>过期 <b>{{ dataHealth?.barsStaleCount ?? '-' }}</b></span>
          <span>空K <b>{{ dataHealth?.barsEmptyCount ?? '-' }}</b></span>
        </div>
        <el-button size="small" round @click="router.push('/sync')">去同步中心</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.dash {
  max-width: 1280px;
  margin: 0 auto;
}

.load-alert {
  margin-bottom: 14px;
}

.load-alert code {
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.05);
}

.dash-header .eyebrow {
  margin: 0 0 4px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--accent);
}

.dash-header .sub {
  margin-top: 6px;
}

/* —— enter motion —— */
.enter {
  animation: dashIn 0.45s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.delay-1 { animation-delay: 0.04s; }
.delay-2 { animation-delay: 0.08s; }
.delay-3 { animation-delay: 0.12s; }
.delay-4 { animation-delay: 0.16s; }
.delay-5 { animation-delay: 0.2s; }

@keyframes dashIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.two-col {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
  align-items: stretch;
}

.action-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.action-panel .panel-head {
  flex: 0 0 auto;
  margin-bottom: 8px;
}

.panel-meta {
  flex: 0 0 auto;
  min-height: 72px;
  margin-bottom: 10px;
}

.meta-line {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 12px;
  margin-bottom: 4px;
}

.meta-date {
  font-size: 12px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  color: var(--ink-soft);
}

.meta-counts {
  font-size: 12px;
  color: var(--slate);
  font-variant-numeric: tabular-nums;
}

.meta-note {
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.9em;
}

.panel-body {
  flex: 1 1 auto;
  min-height: 0;
}

.observe-strip {
  margin-bottom: 14px;
}

.observe-chips {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 260px));
  gap: 8px;
}

.observe-chip {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  min-width: 200px;
  max-width: 260px;
  min-height: 54px;
  align-items: center;
  gap: 8px;
  box-sizing: border-box;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 7px 8px 7px 10px;
  background: rgba(255, 255, 255, 0.76);
  color: var(--ink);
  font: inherit;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.16s ease, background-color 0.16s ease, box-shadow 0.16s ease;
}

.observe-chip:hover {
  border-color: rgba(35, 99, 235, 0.26);
  background: #fff;
  box-shadow: 0 3px 10px rgba(15, 23, 42, 0.06);
}

.observe-chip:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.observe-chip :deep(.observe-chip__identity) {
  width: auto;
  max-width: 100%;
  gap: 3px;
}

.observe-chip :deep(.stock-identity__name) {
  font-size: 13px;
  font-weight: 650;
}

.observe-chip :deep(.stock-identity__meta-line) {
  gap: 3px;
}

.observe-chip :deep(.stock-identity__code) {
  font-size: 11px;
}

.observe-chip em {
  display: inline-flex;
  min-width: 48px;
  min-height: 30px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  box-sizing: border-box;
  border-left: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 4px;
  padding: 4px 6px 4px 8px;
  font-style: normal;
  font-size: 12px;
  font-weight: 650;
  line-height: 1;
  white-space: nowrap;
}

.observe-chip__status-dot {
  width: 5px;
  height: 5px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: currentColor;
}

.observe-chip.near em {
  background: rgba(234, 88, 12, 0.07);
  color: #c2410c;
}

.observe-chip.trig em {
  background: rgba(220, 38, 38, 0.07);
  color: #c62828;
}

/* —— stance hero —— */
.stance-panel {
  position: relative;
  overflow: hidden;
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 20px;
  margin-bottom: 14px;
  padding: 20px 22px;
  border-radius: var(--radius);
  border: 1px solid var(--glass-border);
  background: var(--glass-strong);
  box-shadow: var(--shadow);
}

.stance-glow {
  display: none;
}

.stance-attack .stance-glow {
  background: radial-gradient(circle at 30% 40%, rgba(255, 59, 48, 0.16), transparent 65%);
}

.stance-defend .stance-glow {
  background: radial-gradient(circle at 30% 40%, rgba(0, 113, 227, 0.2), transparent 65%);
}

.stance-attack {
  border-color: rgba(255, 59, 48, 0.22);
}

.stance-defend {
  border-color: rgba(0, 113, 227, 0.28);
}

.stance-balance {
  border-color: rgba(0, 113, 227, 0.16);
}

.stance-empty {
  border-style: dashed;
  border-color: rgba(0, 0, 0, 0.1);
}

.stance-empty .pill {
  background: rgba(0, 0, 0, 0.05);
  color: var(--slate);
}


.stance-main,
.stance-side {
  position: relative;
  z-index: 1;
}

.kicker {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--slate);
  margin-bottom: 14px;
}

.stance-title-row {
  display: flex;
  align-items: flex-start;
  gap: 18px;
}

.score-ring {
  --pct: 50;
  flex: 0 0 88px;
  width: 88px;
  height: 88px;
  border-radius: 50%;
  background: conic-gradient(
    var(--accent) calc(var(--pct) * 1%),
    rgba(0, 0, 0, 0.06) 0
  );
  display: grid;
  place-items: center;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.5);
}

.stance-attack .score-ring {
  background: conic-gradient(
    #ff3b30 calc(var(--pct) * 1%),
    rgba(0, 0, 0, 0.06) 0
  );
}

.stance-defend .score-ring {
  background: conic-gradient(
    #0071e3 calc(var(--pct) * 1%),
    rgba(0, 0, 0, 0.06) 0
  );
}

.score-ring-inner {
  width: 70px;
  height: 70px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.92);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  line-height: 1.05;
}

.score-ring-inner strong {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}

.score-ring-inner small {
  font-size: 11px;
  color: var(--muted);
}

.stance-copy h2 {
  margin: 0 0 10px;
}

.pill {
  display: inline-block;
  padding: 4px 14px;
  border-radius: 999px;
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.02em;
  background: rgba(0, 113, 227, 0.1);
  color: var(--accent);
}

.stance-attack .pill {
  background: rgba(255, 59, 48, 0.1);
  color: var(--up);
}

.stance-defend .pill {
  background: rgba(0, 113, 227, 0.12);
  color: var(--accent);
}

.reason {
  margin: 0 0 6px;
  font-size: 14px;
  line-height: 1.5;
  color: var(--ink-soft);
  max-width: 36em;
}

.advice {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--slate);
}

.market-block {
  user-select: none;
}

.market-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.market-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--slate);
}

.market-links {
  display: flex;
  align-items: center;
  gap: 10px;
}

.market-toggle,
.mobile-link-label {
  display: none;
}

.market-toggle svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  transition: transform 0.18s ease;
}

.market-toggle[aria-expanded="true"] svg {
  transform: rotate(180deg);
}

.text-link {
  border: 0;
  background: transparent;
  padding: 0;
  font: inherit;
  font-size: 12px;
  color: var(--accent);
  cursor: pointer;
}

.text-link:hover {
  opacity: 0.8;
}

.index-lines {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  margin-bottom: 10px;
}

.index-line {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: 8px 9px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.45);
  border: 1px solid var(--line);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  cursor: default;
}

.index-line.muted {
  border-style: dashed;
  opacity: 0.7;
}

.index-line .n {
  font-size: 11px;
  line-height: 1.35;
  color: var(--muted);
}

.index-line .c {
  font-size: 11px;
  line-height: 1.35;
  color: var(--ink-soft);
  order: 1;
}

.index-line .p {
  font-size: 13px;
  font-weight: 650;
  letter-spacing: -0.02em;
  font-family: var(--font-display);
  color: var(--ink-soft);
}

.index-line .c.up,
.index-line .p.up { color: var(--up); }
.index-line .c.down,
.index-line .p.down { color: var(--down); }
.index-line.muted .c,
.index-line.muted .p { color: var(--muted); font-weight: 400; }

.stat-line {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 10px;
  padding-top: 8px;
  border-top: 1px solid var(--line);
  font-size: 12px;
}

.stat {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  color: var(--ink-soft);
}

.stat-value {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
}

.stat em {
  font-style: normal;
  color: var(--muted);
}

.stat b {
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  color: var(--ink);
}

.stat i {
  font-style: normal;
  color: var(--slate);
  font-size: 11px;
}

.stat i.vol-change {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  color: var(--ink);
  font-size: 12px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  letter-spacing: 0.01em;
}

.stat i.vol-change > span {
  line-height: 1;
}

.stat .vol-percentage {
  display: inline-flex;
  align-items: center;
  gap: 0;
}

.vol-percentage .vol-sign,
.vol-percentage .vol-number {
  display: inline-flex;
  align-items: center;
  line-height: 1;
}

.vol-sign {
  transform: translateY(-1px);
}

.volume-stat > b,
.volume-stat .vol-change > span:not(.vol-percentage) {
  font-weight: 500;
}

.volume-stat .vol-change > .vol-trend.is-contraction {
  font-weight: 400;
}

.stat .slash,
.stat .miss {
  color: var(--muted);
  font-weight: 500;
}

.stat b.up,
.stat i.up,
.stat .up { color: var(--up); }
.stat b.down,
.stat i.down,
.stat .down { color: var(--down); }
.stat b.flat,
.stat .flat { color: var(--slate); }

.stat-line .dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.15);
  flex: 0 0 auto;
}

.breadth-track {
  display: flex;
  height: 3px;
  margin-top: 8px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(0, 0, 0, 0.06);
}

.breadth-track .up-seg {
  display: block;
  height: 100%;
  background: rgba(255, 59, 48, 0.55);
}

.breadth-track .flat-seg {
  display: block;
  height: 100%;
  background: rgba(0, 0, 0, 0.12);
}

.breadth-track .down-seg {
  display: block;
  height: 100%;
  background: rgba(52, 199, 89, 0.55);
}

.breadth-hint {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--muted, #8a8f98);
  letter-spacing: -0.01em;
}

.breadth-forecast {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 0.72fr);
  gap: 18px;
  align-items: stretch;
  margin: 0 0 14px;
  padding: 14px 16px;
  border: 1px solid var(--glass-border, var(--line));
  border-radius: var(--radius, 10px);
  background: var(--glass, rgba(255, 255, 255, 0.9));
  box-shadow: var(--shadow-soft, none);
}

.breadth-forecast.is-empty {
  display: block;
  padding: 9px 14px;
  background: rgba(255, 255, 255, 0.68);
  box-shadow: none;
}

.breadth-forecast.is-empty .breadth-forecast-empty {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.breadth-forecast.is-empty .breadth-forecast-empty h3 {
  flex: 0 0 auto;
}

.breadth-forecast.is-empty .breadth-forecast-empty p {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}

.breadth-forecast-main,
.breadth-forecast-empty {
  min-width: 0;
}

.breadth-forecast-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.breadth-forecast-head h3,
.breadth-forecast-empty h3 {
  margin: 0;
  color: var(--ink);
  font-size: 15px;
  font-weight: 650;
}

.breadth-forecast-head p,
.breadth-forecast-empty p {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
}

.breadth-forecast-confidence {
  flex: 0 0 auto;
  padding: 3px 6px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 4px;
  color: var(--slate);
  background: rgba(15, 23, 42, 0.04);
  font-size: 11px;
  line-height: 1.3;
}

.breadth-forecast-tug {
  display: grid;
  grid-template-columns: minmax(70px, auto) minmax(110px, 1fr) minmax(70px, auto);
  align-items: center;
  gap: 10px;
  margin-top: 13px;
}

.breadth-forecast-side {
  display: grid;
  gap: 2px;
  font-variant-numeric: tabular-nums;
}

.breadth-forecast-side span {
  color: var(--muted);
  font-size: 11px;
}

.breadth-forecast-side strong {
  font-size: 20px;
  line-height: 1;
}

.breadth-forecast-side.up {
  text-align: left;
}

.breadth-forecast-side.down {
  text-align: right;
}

.breadth-forecast-side.up strong,
.breadth-forecast-backtest .is-miss {
  color: var(--up);
}

.breadth-forecast-side.down strong,
.breadth-forecast-backtest .is-hit {
  color: var(--down);
}

.breadth-forecast-track {
  display: flex;
  height: 18px;
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 4px;
  background: rgba(15, 23, 42, 0.06);
}

.breadth-forecast-track i {
  display: block;
  min-width: 0;
  height: 100%;
}

.breadth-forecast-up {
  background: rgba(255, 59, 48, 0.8);
}

.breadth-forecast-down {
  background: rgba(52, 199, 89, 0.8);
}

.breadth-forecast-evidence {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 12px;
}

.breadth-forecast-evidence span {
  max-width: 100%;
  padding: 3px 5px;
  border: 1px solid var(--line);
  border-radius: 4px;
  color: var(--slate);
  background: rgba(255, 255, 255, 0.56);
  font-size: 10px;
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.breadth-forecast-backtest {
  display: flex;
  align-content: flex-start;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px 10px;
  min-width: 0;
  padding: 2px 0 2px 18px;
  border-left: 1px solid var(--line);
  color: var(--slate);
  font-size: 11px;
  line-height: 1.45;
  font-variant-numeric: tabular-nums;
}

.breadth-forecast-backtest-title {
  color: var(--ink-soft);
  font-weight: 650;
}

.breadth-forecast-pending {
  color: var(--muted);
}

.breadth-forecast-backtest p {
  width: 100%;
  margin: 5px 0 0;
  color: var(--muted);
  overflow-wrap: anywhere;
}

.breadth-forecast-backtest .breadth-forecast-rolling {
  color: var(--ink-soft);
}

.effect-strip {
  margin: 0 0 14px;
  padding: 14px 16px 12px;
  border-radius: var(--radius, 14px);
  border: 1px solid var(--glass-border, var(--line));
  background: var(--glass, rgba(255, 255, 255, 0.92));
  backdrop-filter: blur(var(--blur, 8px));
  box-shadow: var(--shadow-soft, none);
}

.effect-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.effect-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--ink);
  letter-spacing: 0.02em;
}

.effect-hint {
  font-size: 12px;
  color: var(--ink-soft, var(--muted));
  line-height: 1.4;
}

.effect-hint.muted {
  color: var(--muted, #8a8f98);
}

.effect-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
}

.effect-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  padding: 12px 11px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.03);
  border: 1px solid transparent;
}

.effect-cell em {
  font-style: normal;
  font-size: 12px;
  font-weight: 600;
  color: var(--muted, #8a8f98);
}

.effect-cell b {
  font-size: 16px;
  font-weight: 750;
  font-variant-numeric: tabular-nums;
  color: var(--ink);
  line-height: 1.2;
  letter-spacing: -0.02em;
}

.effect-cell.up,
.effect-cell.down {
  background: rgba(15, 23, 42, 0.03);
  border-color: rgba(15, 23, 42, 0.06);
}

.effect-cell.up b { color: var(--up); }
.effect-cell.down b { color: var(--down); }
.effect-cell.flat b { color: var(--slate, #64748b); }

.command-band {
  min-width: 0;
  margin: 0 0 14px;
  padding: 18px 2px 20px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  letter-spacing: 0;
}

.command-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.command-head h3,
.command-column-head h4 {
  margin: 0;
  color: var(--ink);
  letter-spacing: 0;
}

.command-head h3 {
  font-size: 15px;
  line-height: 1.35;
}

.command-head p {
  margin: 4px 0 12px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
  font-variant-numeric: tabular-nums;
}

.command-status {
  flex: 0 0 auto;
  padding: 3px 7px;
  border: 1px solid rgba(52, 199, 89, 0.22);
  border-radius: 4px;
  background: rgba(52, 199, 89, 0.07);
  color: #248a3d;
  font-size: 11px;
  font-weight: 650;
}

.command-status.status-partial,
.command-status.status-generating {
  border-color: rgba(255, 159, 10, 0.25);
  background: rgba(255, 159, 10, 0.08);
  color: #9a5b00;
}

.command-status.status-stale,
.command-status.status-blocked {
  border-color: rgba(255, 59, 48, 0.23);
  background: rgba(255, 59, 48, 0.07);
  color: var(--up);
}

.command-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
}

.command-column {
  min-width: 0;
  padding: 2px 18px 0 0;
}

.command-guide {
  padding: 2px 0 0 18px;
  border-left: 1px solid var(--line);
}

.command-column-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 9px;
}

.command-column-head h4 {
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
}

.command-column-head span {
  color: var(--muted);
  font-size: 11px;
}

.command-headline,
.command-guide-summary {
  margin: 0;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.command-headline {
  font-weight: 620;
}

.command-forecast {
  margin-top: 10px;
  padding: 9px 10px;
  border-left: 2px solid rgba(0, 113, 227, 0.5);
  background: rgba(0, 113, 227, 0.04);
}

.command-forecast-label,
.command-forecast-watch > span {
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
}

.command-forecast > p {
  margin: 3px 0 0;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.command-forecast-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
  margin-top: 8px;
}

.command-forecast-direction {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.command-forecast-direction > strong {
  font-size: 11px;
}

.command-forecast-direction > span {
  color: var(--slate);
  font-size: 12px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.command-forecast-direction > span b,
.command-forecast-watch b {
  color: var(--ink-soft);
  font-weight: 650;
}

.command-forecast-direction > span small {
  display: block;
  margin-top: 2px;
  color: var(--accent);
  font-size: 11px;
}

.command-forecast-direction.focus > strong { color: #248a3d; }
.command-forecast-direction.risk > strong { color: var(--up); }

.command-forecast-watch {
  margin-top: 8px;
  padding-top: 7px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
}

.command-forecast-watch p {
  margin: 3px 0 0;
  color: var(--slate);
  font-size: 12px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.command-directions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 11px;
  padding-top: 9px;
  border-top: 1px solid rgba(15, 23, 42, 0.07);
}

.command-direction {
  display: grid;
  align-content: start;
  gap: 5px;
  min-width: 0;
}

.command-direction > strong {
  color: var(--muted);
  font-size: 11px;
}

.command-direction > span {
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.command-direction > span b {
  color: inherit;
  font-weight: 650;
}

.command-direction.opportunity > strong { color: #248a3d; }
.command-direction.risk > strong { color: var(--up); }

.command-watch {
  margin-top: 10px;
}

.asia-index-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 24px;
  min-width: 0;
  width: 100%;
  border-top: 1px solid rgba(15, 23, 42, 0.07);
}

.asia-index-grid .overnight-quote {
  justify-content: flex-start;
  gap: 12px;
}

.command-watch > span {
  color: var(--muted);
  font-size: 11px;
}

.command-watch p {
  margin: 3px 0 0;
  color: var(--slate);
  font-size: 12px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.command-watch b {
  color: var(--ink-soft);
  font-weight: 650;
}

.command-position {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
  margin-top: 9px;
  color: var(--muted);
  font-size: 12px;
}

.command-position b {
  margin-left: 3px;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
}

.command-blocked-reason {
  margin: 8px 0 0;
  padding-left: 8px;
  border-left: 2px solid rgba(255, 59, 48, 0.48);
  color: var(--up);
  font-size: 12px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.command-actions {
  margin-top: 8px;
  border-top: 1px solid rgba(15, 23, 42, 0.07);
}

.command-action {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
  min-height: 48px;
  margin: 0;
  padding: 9px 2px;
  border: 0;
  border-bottom: 1px solid rgba(15, 23, 42, 0.07);
  background: transparent;
  color: var(--ink);
  font: inherit;
  text-align: left;
  cursor: pointer;
  overflow-wrap: anywhere;
  touch-action: manipulation;
}

.command-action:last-child {
  border-bottom: 0;
}

.command-action:hover,
.command-action:focus-visible {
  background: rgba(0, 113, 227, 0.05);
}

.command-action:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.command-action-order {
  display: grid;
  place-items: center;
  width: 20px;
  aspect-ratio: 1;
  border-radius: 50%;
  background: rgba(0, 113, 227, 0.09);
  color: var(--accent);
  font-size: 10px;
  font-weight: 700;
}

.command-action-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.5;
}

.command-action-title {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px 7px;
}

.command-action-title b {
  color: var(--ink);
  font-size: 12px;
  font-weight: 650;
}

.command-action-title em {
  color: var(--accent);
  font-size: 11px;
  font-style: normal;
}

.command-target-count {
  color: var(--slate);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.command-action-copy small {
  color: var(--muted);
  font-size: 11px;
  overflow-wrap: anywhere;
}

.command-action-arrow {
  color: var(--accent);
  font-size: 13px;
}

.morning-context {
  margin: 0 0 14px;
  padding: 0 18px 20px 2px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  letter-spacing: 0;
}

.morning-context-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 24px;
  padding: 15px 0 14px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}

.morning-context-title {
  min-width: 0;
  padding-left: 12px;
  border-left: 3px solid var(--accent);
  text-align: left;
}

.morning-context-head h3 {
  margin: 0;
  color: var(--ink);
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: 0;
}

.morning-context-head p {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
  letter-spacing: 0;
}

.morning-context-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: var(--muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.morning-context-status {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.morning-context-status :deep(.el-tag) {
  flex: 0 0 auto;
}

.morning-context-time {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.morning-context-time-label {
  color: var(--muted);
  font-size: 11px;
}

.morning-context-time time {
  color: var(--slate);
}

.morning-context-refreshing {
  color: var(--warning);
  font-size: 11px;
  white-space: normal;
}

.morning-context-link {
  min-height: 44px;
  padding: 0 8px;
  touch-action: manipulation;
}

.morning-context-link :deep(span) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 650;
}

.morning-context-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  min-width: 0;
  width: 100%;
  gap: 0;
  margin-top: 20px;
}

@media (min-width: 1200px) {
  .morning-context-grid.is-market-collapsed {
    grid-template-columns: minmax(0, 3fr) minmax(0, 5fr);
  }
}

.morning-context-block {
  align-self: start;
  min-width: 0;
  padding: 0;
}

.morning-context-more {
  min-width: 0;
}

.morning-disclosure {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  width: 100%;
  min-height: 44px;
  margin: 7px 0 0;
  padding: 0;
  border: 0;
  border-top: 1px solid rgba(15, 23, 42, 0.07);
  background: transparent;
  color: var(--accent);
  font: inherit;
  font-size: 12px;
  font-weight: 650;
  text-align: left;
  cursor: pointer;
  touch-action: manipulation;
}

.morning-disclosure:hover {
  color: var(--accent-hover, #005bb5);
}

.morning-disclosure:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.morning-disclosure-arrow {
  flex: 0 0 auto;
  font-variant-numeric: tabular-nums;
}

.overnight-block {
  padding-right: 28px;
}

.morning-news-block {
  padding-left: 28px;
  border-left: 1px solid var(--line);
}

.morning-block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  min-height: 24px;
  margin-bottom: 12px;
}

.morning-block-head h4 {
  margin: 0;
  color: var(--ink);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: 0;
}

.morning-block-head > span {
  min-width: 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.5;
  letter-spacing: 0;
  text-align: right;
  overflow-wrap: anywhere;
}

.overnight-layer + .overnight-layer {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid rgba(15, 23, 42, 0.07);
}

.overnight-layer-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  min-height: 20px;
  margin-bottom: 8px;
}

.overnight-layer-head h5 {
  margin: 0;
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.5;
  letter-spacing: 0;
}

.overnight-layer-head span {
  min-width: 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.5;
  letter-spacing: 0;
  text-align: right;
  overflow-wrap: anywhere;
}

.overnight-index-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  min-width: 0;
  width: 100%;
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.42);
}

.a50-future-grid {
  grid-template-columns: minmax(0, 1fr);
}

.opening-auction-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  min-width: 0;
  width: 100%;
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.42);
}

.opening-auction-grid .overnight-quote {
  min-height: 44px;
  padding: 0 10px;
  border-right: 1px solid rgba(15, 23, 42, 0.07);
  border-bottom: 0;
}

.opening-auction-grid .overnight-quote:last-child {
  border-right: 0;
}

.external-market-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 24px;
  row-gap: 0;
  min-width: 0;
  border-top: 1px solid rgba(15, 23, 42, 0.07);
}

.external-market-card {
  min-width: 0;
  padding: 10px 0 11px;
  border: 0;
  border-bottom: 1px solid rgba(15, 23, 42, 0.07);
  border-radius: 0;
  background: transparent;
}

.external-market-quote {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: baseline;
  gap: 8px;
}

.external-market-quote strong {
  min-width: 0;
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.external-market-quote b {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.external-market-quote span {
  color: var(--muted);
  font-size: 11px;
  white-space: nowrap;
}

.external-market-price {
  display: block;
  margin-top: 2px;
  color: var(--muted);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.external-market-card p,
.external-market-note {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
}

.external-market-card p {
  overflow-wrap: anywhere;
}

.external-market-card p span {
  color: var(--ink-soft);
  font-weight: 650;
  white-space: nowrap;
}

.external-market-note {
  padding-left: 8px;
  border-left: 2px solid rgba(0, 113, 227, 0.45);
}

.overnight-theme-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  min-width: 0;
  width: 100%;
  column-gap: 18px;
}

.overnight-star-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  min-width: 0;
  width: 100%;
  column-gap: 18px;
}

.overnight-star-grid .overnight-quote {
  justify-content: flex-start;
  gap: 12px;
}

.overnight-quote {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
  min-height: 36px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.07);
  font-variant-numeric: tabular-nums;
}

.overnight-index-grid .overnight-quote {
  min-height: 44px;
  padding: 0 10px;
  border-right: 1px solid rgba(15, 23, 42, 0.07);
  border-bottom: 0;
}

.overnight-index-grid .overnight-quote:last-child {
  border-right: 0;
}

.overnight-index-grid .overnight-quote:nth-last-child(-n + 3),
.overnight-star-grid .overnight-quote:nth-last-child(-n + 2) {
  border-bottom-color: transparent;
}

.overnight-quote-name {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}

.overnight-quote-name strong {
  overflow: hidden;
  color: var(--ink);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overnight-quote-name small {
  min-width: 0;
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overnight-quote > b {
  flex: 0 0 auto;
  color: var(--slate);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
}

.overnight-quote > b.up { color: var(--up); }
.overnight-quote > b.down { color: var(--down); }

.overnight-theme {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) 104px;
  align-items: center;
  justify-content: stretch;
  gap: 8px;
  min-width: 0;
  min-height: 56px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.07);
  font-variant-numeric: tabular-nums;
}

.overnight-theme:nth-last-child(-n + 2) {
  border-bottom-color: transparent;
}

.overnight-theme-rank {
  color: #94a0b1;
  font-size: 11px;
  font-weight: 650;
  text-align: center;
}

.overnight-theme-copy,
.overnight-theme-stats {
  display: flex;
  min-width: 0;
}

.overnight-theme-copy {
  flex-direction: column;
  gap: 2px;
}

.overnight-theme-copy strong {
  overflow: hidden;
  color: var(--ink);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overnight-theme-copy small {
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overnight-theme-stats {
  align-items: stretch;
  flex-direction: column;
  gap: 1px;
  white-space: nowrap;
}

.overnight-theme-stats b {
  color: var(--slate);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  text-align: right;
}

.overnight-theme-stats b.up { color: var(--up); }
.overnight-theme-stats b.down { color: var(--down); }

.overnight-theme-stats span {
  color: var(--muted);
  font-size: 11px;
  letter-spacing: 0;
}

.overnight-theme-stats .overnight-theme-breadth {
  display: grid;
  grid-template-columns: 28px 58px;
  align-items: center;
  justify-content: end;
  gap: 5px;
}

.overnight-theme-breadth > span:last-child {
  text-align: right;
}

.overnight-theme-breadth-track {
  display: block;
  width: 28px;
  height: 3px;
  overflow: hidden;
  border-radius: 2px;
  background: rgba(100, 116, 139, 0.16);
}

.overnight-theme-breadth-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #527493;
}

.news-counts {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--muted);
  font-size: 11px;
  white-space: nowrap;
}

.news-counts b {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.news-counts .bull { color: var(--up); }
.news-counts .bear { color: var(--down); }

.news-bias {
  padding-left: 8px;
  border-left: 1px solid var(--line);
}

.morning-news-lead {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: 10px;
  margin-bottom: 10px;
  padding: 12px 14px;
  border-left: 2px solid var(--accent);
  background: rgba(22, 105, 201, 0.045);
}

.morning-news-summary-label {
  padding-top: 2px;
  color: var(--accent);
  font-size: 11px;
  font-weight: 650;
  line-height: 1.45;
  white-space: nowrap;
}

.morning-news-summary {
  max-width: 78ch;
  margin: 0;
  color: var(--ink-soft);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.6;
  letter-spacing: 0;
}

.morning-news-list {
  border-top: 1px solid rgba(15, 23, 42, 0.07);
}

.pre-market-event-impact {
  margin: 12px 0;
  border-top: 1px solid rgba(15, 23, 42, 0.07);
}

.pre-market-event-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 11px 0 8px;
}

.pre-market-event-head h5 {
  margin: 0;
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 700;
}

.pre-market-event-head > span,
.pre-market-event-meta {
  color: var(--muted);
  font-size: 11px;
}

.pre-market-event-item {
  padding: 10px 0;
  border-top: 1px solid rgba(15, 23, 42, 0.06);
}

.pre-market-event-meta,
.pre-market-event-targets {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 7px;
  align-items: center;
}

.pre-market-event-type {
  color: var(--accent);
  font-weight: 700;
}

.pre-market-event-item a,
.pre-market-event-item strong {
  display: block;
  margin-top: 4px;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.5;
}

.pre-market-event-item a:hover {
  color: var(--accent);
}

.pre-market-event-item p {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.55;
}

.pre-market-event-targets {
  margin-top: 5px;
}

.pre-market-event-targets span {
  padding: 1px 5px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  color: var(--slate);
  font-size: 11px;
}

.morning-news-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.07);
  font-size: 12px;
}

.morning-news-item:last-child {
  border-bottom-color: transparent;
}

.news-sentiment {
  min-width: 28px;
  color: var(--muted);
  font-size: 11px;
  font-weight: 600;
  text-align: left;
}

.news-sentiment.bull { color: var(--up); }
.news-sentiment.bear { color: var(--down); }

.morning-news-item a,
.morning-news-title {
  min-width: 0;
  overflow: hidden;
  color: var(--ink-soft);
  line-height: 1.5;
  letter-spacing: 0;
  text-decoration: none;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.morning-news-item a:hover {
  color: var(--accent);
}

.morning-news-item small {
  min-width: 26px;
  color: var(--muted);
  font-size: 11px;
  text-align: right;
}

.opinion-radar {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid rgba(15, 23, 42, 0.07);
}

.opinion-radar-head,
.opinion-group-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.opinion-radar-head h5,
.opinion-group-head h6 {
  margin: 0;
  color: var(--ink);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.5;
  letter-spacing: 0;
}

.opinion-radar-head time,
.opinion-group-head span,
.opinion-kol-status {
  color: var(--muted);
  font-size: 11px;
  line-height: 1.5;
  letter-spacing: 0;
}

.opinion-summary-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 7px;
  margin: 10px 0 12px;
}

.opinion-summary-grid p {
  margin: 0;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.6;
}

.opinion-summary-grid span {
  display: inline-block;
  min-width: 30px;
  margin-right: 8px;
  color: var(--accent);
  font-size: 11px;
  font-weight: 650;
}

.opinion-group + .opinion-group {
  margin-top: 12px;
}

.opinion-group-head {
  margin-bottom: 5px;
}

.opinion-group-head span {
  min-width: 0;
  text-align: right;
  overflow-wrap: anywhere;
}

.opinion-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  min-height: 36px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
  font-size: 12px;
}

.opinion-direction {
  min-width: 28px;
  color: var(--muted);
  font-size: 11px;
  font-weight: 650;
}

.opinion-direction.bull { color: var(--up); }
.opinion-direction.bear { color: var(--down); }
.opinion-direction.seat { color: var(--accent); }

.opinion-item a,
.opinion-item-link,
.opinion-item-title {
  min-width: 0;
  overflow: hidden;
  color: var(--ink-soft);
  line-height: 1.5;
  text-decoration: none;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.opinion-item-link {
  padding: 0;
  border: 0;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.opinion-item a:hover,
.opinion-item-link:hover { color: var(--accent); }

.opinion-item-link:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.opinion-item small {
  min-width: 0;
  max-width: 130px;
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:global(.opinion-preview-dialog) {
  width: min(960px, calc(100vw - 24px)) !important;
  overflow: hidden;
  border-radius: 8px;
}

:global(.opinion-preview-dialog .el-dialog__body) {
  padding-top: 4px;
  padding-bottom: 8px;
}

.opinion-preview-body {
  min-height: 0;
}

.opinion-preview-body p {
  margin: 0 0 10px;
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.opinion-preview-frame {
  display: block;
  width: 100%;
  height: min(70dvh, 720px);
  border: 1px solid var(--line);
  border-radius: 4px;
}

.opinion-kol-status {
  margin: 9px 0 0;
}

.opinion-source-list {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 10px;
  margin-top: 6px;
}

.opinion-source-list span {
  display: inline-flex;
  min-width: 0;
  align-items: baseline;
  gap: 3px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.5;
}

.opinion-source-list a,
.opinion-source-list b {
  max-width: 88px;
  overflow: hidden;
  color: var(--ink-soft);
  font-size: inherit;
  font-weight: 600;
  text-decoration: none;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.opinion-source-list a:hover {
  color: var(--accent);
}

.opinion-source-list i {
  color: var(--muted);
  font-style: normal;
  white-space: nowrap;
}

.morning-context-empty {
  max-width: 100%;
  margin: 12px 0;
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0;
  overflow-wrap: anywhere;
}

@media (max-width: 560px) {
  .observe-chips {
    grid-template-columns: 1fr;
  }

  .observe-chip {
    width: 100%;
    min-width: 0;
    max-width: none;
    min-height: 56px;
  }

  .effect-grid {
    grid-template-columns: 1fr 1fr;
  }

  .effect-cell {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    box-sizing: border-box;
    min-height: 48px;
    padding: 7px 10px;
  }

  .effect-cell em,
  .effect-cell b {
    display: inline-flex;
    align-items: center;
    min-height: 20px;
    line-height: 20px;
  }

  .effect-cell b {
    flex: 0 0 auto;
    justify-content: flex-end;
    text-align: right;
    white-space: nowrap;
  }

  .effect-cell:last-child:nth-child(odd) {
    grid-column: 1 / -1;
  }

  .command-directions {
    grid-template-columns: 1fr;
  }

  .command-forecast-grid {
    grid-template-columns: 1fr;
  }

  .morning-context .morning-context-head {
    grid-template-columns: 1fr;
    align-items: stretch;
    gap: 9px;
    padding: 14px 0 10px;
  }

  .morning-context-title {
    padding-left: 10px;
  }

  .morning-context-meta {
    justify-content: flex-start;
    width: 100%;
  }

  .morning-context-status {
    flex: 1 1 auto;
  }

  .morning-context-link {
    flex: 0 0 auto;
    margin-left: auto;
  }

  .morning-context-time-label,
  .morning-block-head > span {
    display: none;
  }

  .overnight-index-grid .overnight-quote {
    align-items: flex-start;
    flex-direction: column;
    justify-content: center;
    gap: 2px;
    padding: 6px 8px;
  }

  .overnight-star-grid {
    column-gap: 12px;
  }

  .external-market-grid {
    grid-template-columns: 1fr;
  }

  .overnight-theme-grid {
    grid-template-columns: 1fr;
  }

  .overnight-theme:nth-last-child(-n + 2) {
    border-bottom-color: rgba(15, 23, 42, 0.07);
  }

  .overnight-theme:last-child {
    border-bottom-color: transparent;
  }

  .overnight-quote-name small {
    display: none;
  }

  .morning-news-lead {
    grid-template-columns: 1fr;
    gap: 4px;
    padding: 9px 10px;
  }

  .morning-news-summary-label {
    padding-top: 0;
  }

  .morning-news-item {
    align-items: start;
    min-height: 44px;
    padding: 7px 0;
  }

  .morning-news-item a,
  .morning-news-title {
    display: -webkit-box;
    overflow: hidden;
    white-space: normal;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  .morning-news-item small {
    padding-top: 1px;
  }

  .opinion-item {
    align-items: start;
    min-height: 38px;
    padding: 5px 0;
  }

  .opinion-item a,
  .opinion-item-link,
  .opinion-item-title {
    display: -webkit-box;
    white-space: normal;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  .opinion-item small {
    max-width: 82px;
    padding-top: 1px;
  }
}

@media (max-width: 900px) {
  .command-band {
    padding-right: 0;
    padding-left: 0;
  }

  .command-grid {
    grid-template-columns: 1fr;
  }

  .command-column {
    padding: 0;
  }

  .command-guide {
    margin-top: 13px;
    padding-top: 12px;
    border-top: 1px solid var(--line);
    border-left: 0;
  }

  .morning-context {
    padding-right: 14px;
    padding-left: 0;
  }

  .morning-context-head {
    align-items: center;
  }

}

@media (max-width: 480px) {
  .morning-context-grid {
    grid-template-columns: 1fr;
  }

  .morning-context-block {
    width: 100%;
  }

  .overnight-block {
    padding-right: 0;
  }

  .morning-news-block {
    order: -1;
    margin-top: 0;
    margin-bottom: 12px;
    padding-top: 0;
    padding-bottom: 12px;
    padding-left: 0;
    border-top: 0;
    border-bottom: 1px solid var(--line);
    border-left: 0;
  }
}

.miss-hint {
  margin-left: 4px;
  font-style: normal;
  font-size: 11px;
  color: var(--muted, #8a8f98);
}

/* —— panels —— */
.panel {
  position: relative;
  margin-bottom: 14px;
  padding: 16px 18px 18px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass);
  box-shadow: var(--shadow-soft);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.panel:hover {
  border-color: #bdc9d9;
  box-shadow: 0 3px 9px rgba(15, 23, 42, 0.08);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-head h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--ink);
}

.panel-desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--muted);
}

.panel-links {
  display: flex;
  gap: 4px;
}

.val-dist {
  display: flex;
  height: 6px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(0, 0, 0, 0.06);
}

.val-dist-placeholder {
  visibility: hidden;
}

.val-dist i {
  display: block;
  min-width: 0;
}

.val-dist .cheap { background: #34c759; }
.val-dist .fair { background: #86868b; }
.val-dist .rich { background: #ff3b30; }

.dash-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(255, 255, 255, 0.45);
  --el-table-row-hover-bg-color: rgba(0, 113, 227, 0.05);
  --el-table-border-color: rgba(0, 0, 0, 0.05);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.dash-table :deep(.action-cues-col .cell) {
  overflow: visible;
  padding-left: 8px;
  padding-right: 8px;
}

.action-cues {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  min-width: 0;
}

.action-cues :deep(.el-tag) {
  max-width: 100%;
}

.action-cues :deep(.link-hint-tag) {
  white-space: nowrap;
}

.action-cues :deep(.mainline-hint-tag) {
  height: auto;
  white-space: normal;
}

.action-cues :deep(.mainline-hint-tag .el-tag__content) {
  overflow-wrap: anywhere;
}

.mobile-action-list {
  display: none;
}

.dash-empty {
  padding: 12px 0 4px;
}

.num {
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

.risk-tag {
  color: var(--warn);
  font-weight: 600;
}

.theme-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.theme-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  padding: 6px 12px;
  border-radius: 10px;
  color: var(--ink-soft);
  background: rgba(0, 113, 227, 0.08);
  border: 1px solid rgba(0, 113, 227, 0.12);
  animation: chipIn 0.35s ease both;
  animation-delay: calc(var(--i, 0) * 0.04s);
}

.theme-name {
  color: var(--ink-soft);
}

.theme-pct {
  display: inline-flex;
  align-items: center;
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'tnum' 1;
  letter-spacing: 0;
  line-height: 1;
  white-space: nowrap;
  color: var(--ink-soft);
}

.theme-pct.up {
  color: var(--up);
}

.theme-pct.down {
  color: var(--down);
}

@keyframes chipIn {
  from {
    opacity: 0;
    transform: scale(0.94);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.tip-list {
  display: grid;
  gap: 8px;
}

.tip-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.4);
  border: 1px solid var(--line);
  font-size: 12px;
  line-height: 1.45;
  color: var(--ink-soft);
}

.tip-dot {
  flex: 0 0 6px;
  width: 6px;
  height: 6px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.soft-msg {
  margin: 0 0 8px;
}

.empty-guide {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 14px 16px;
  border-radius: var(--radius-sm);
  border: 1px dashed var(--line-strong);
  background: rgba(255, 255, 255, 0.35);
}

.empty-guide p {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-soft);
}

.empty-guide span {
  font-size: 12px;
  color: var(--muted);
  line-height: 1.45;
  margin-bottom: 4px;
}

/* —— KPI —— */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.kpi {
  padding: 12px 12px 10px;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid var(--line);
  user-select: none;
  cursor: default;
  transition: transform 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.kpi:hover {
  transform: translateY(-2px);
  background: var(--glass-strong);
  box-shadow: var(--shadow-soft);
}

.kpi label {
  display: block;
  font-size: 11px;
  font-weight: 500;
  color: var(--muted);
  margin-bottom: 6px;
  letter-spacing: 0.02em;
}

.kpi b {
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 650;
  color: var(--ink);
}

.kpi.warn {
  background: rgba(255, 159, 10, 0.12);
  border-color: rgba(255, 159, 10, 0.28);
}

.kpi.warn b {
  color: #c77700;
}

.chart-shell {
  position: relative;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.45);
  border: 1px solid var(--line);
  padding: 8px 4px 4px;
  min-height: 168px;
}

.chart-shell.empty {
  border-style: dashed;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 16px;
}

.chart-label {
  position: absolute;
  top: 10px;
  left: 14px;
  z-index: 1;
  font-size: 11px;
  font-weight: 500;
  color: var(--muted);
}

.mini-chart {
  height: 168px;
  width: 100%;
}

.mini-chart.hidden {
  display: none;
}

.chart-empty {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.chart-empty p {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-soft);
}

.chart-empty span {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 4px;
}

/* —— health —— */
.health {
  display: grid;
  grid-template-columns: 4px 1fr;
  gap: 0;
  padding: 0;
  overflow: hidden;
}

.health-bar {
  background: var(--accent);
}

.health.lvl-green .health-bar { background: var(--down); }
.health.lvl-yellow .health-bar { background: var(--warn); }
.health.lvl-red .health-bar { background: var(--up); }

.health-body {
  padding: 14px 16px 16px;
}

.health-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--slate);
}

.health-stats b {
  color: var(--ink);
  font-variant-numeric: tabular-nums;
  margin-left: 4px;
}

.muted {
  color: var(--muted);
}

@media (max-width: 1100px) {
  .kpi-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .two-col {
    grid-template-columns: 1fr;
  }

  .stance-panel {
    grid-template-columns: 1fr;
  }

  .count-date {
    margin-left: 0;
    width: 100%;
  }
}

@media (max-width: 820px) {
  .desktop-action-table {
    display: none;
  }

  .mobile-action-list {
    display: block;
    width: 100%;
    min-width: 0;
    border-top: 1px solid var(--line);
  }

  .mobile-action-item {
    display: block;
    width: 100%;
    min-width: 0;
    min-height: 76px;
    margin: 0;
    padding: 14px 2px;
    border: 0;
    border-bottom: 1px solid var(--line);
    border-radius: 0;
    background: transparent;
    color: var(--ink);
    font: inherit;
    text-align: left;
    cursor: pointer;
    touch-action: manipulation;
    transition: background 0.15s ease;
  }

  .mobile-action-empty {
    margin: 0;
    padding: 28px 0;
    color: var(--muted);
    font-size: 12px;
    text-align: center;
  }

  .mobile-action-item:last-child {
    border-bottom: 0;
  }

  .mobile-action-item:active {
    background: rgba(0, 113, 227, 0.06);
  }

  .mobile-action-item:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: -2px;
  }

  .mobile-action-primary {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(96px, 112px);
    align-items: center;
    gap: 10px;
    min-width: 0;
  }

  .mobile-stock {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 3px 8px;
    min-width: 0;
  }

  .mobile-stock-with-strategy {
    display: flex;
    align-items: flex-end;
    gap: 6px;
    min-width: 0;
  }

  .mobile-strategy-badge {
    display: inline-flex;
    height: 20px;
    flex: 0 0 auto;
    align-items: center;
    padding: 0 5px;
    border: 1px solid var(--line-strong);
    border-radius: 4px;
    background: var(--paper-deep);
    color: var(--slate);
    font-size: 11px;
    font-weight: 650;
    line-height: 18px;
  }

  .mobile-strategy-badge.is-risk {
    border-color: rgba(255, 159, 10, 0.35);
    background: rgba(255, 159, 10, 0.08);
    color: var(--warn);
  }

  .mobile-stock strong {
    color: var(--accent);
    font-size: 14px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  .mobile-stock > span {
    min-width: 0;
    overflow-wrap: anywhere;
    color: var(--ink-soft);
    font-size: 13px;
    font-weight: 600;
  }

  .mobile-score {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
    align-items: center;
    gap: 6px;
    min-width: 0;
  }

  .mobile-score > em,
  .mobile-action-details em,
  .mobile-exit-rule > em {
    color: var(--muted);
    font-size: 11px;
    font-style: normal;
    font-weight: 500;
    white-space: nowrap;
  }

  .mobile-score :deep(.score-bar) {
    min-width: 0;
  }

  .mobile-score :deep(.score-bar-track) {
    min-width: 24px;
  }

  .mobile-action-details {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 6px 12px;
    margin-top: 11px;
  }

  .mobile-action-details > span {
    display: flex;
    align-items: baseline;
    gap: 6px;
    min-width: 0;
  }

  .mobile-action-details b {
    min-width: 0;
    overflow-wrap: anywhere;
    color: var(--ink-soft);
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0;
  }

  .mobile-action-details .risk-tag {
    color: var(--warn);
  }

  .mobile-action-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 10px;
  }

  .mobile-action-tag {
    max-width: 100%;
    padding: 3px 6px;
    border: 1px solid transparent;
    border-radius: 4px;
    font-size: 11px;
    line-height: 1.35;
    overflow-wrap: anywhere;
  }

  .mobile-action-tag.executable,
  .mobile-action-tag.positive {
    border-color: rgba(52, 199, 89, 0.2);
    background: rgba(52, 199, 89, 0.08);
    color: #248a3d;
  }

  .mobile-action-tag.negative {
    border-color: rgba(255, 59, 48, 0.2);
    background: rgba(255, 59, 48, 0.08);
    color: var(--up);
  }

  .mobile-action-tag.mainline {
    border-color: rgba(255, 159, 10, 0.22);
    background: rgba(255, 159, 10, 0.09);
    color: #9a5b00;
  }

  .mobile-exit-rule {
    display: grid;
    grid-template-columns: 24px minmax(0, 1fr);
    align-items: baseline;
    gap: 8px;
    min-width: 0;
    margin-top: 9px;
    padding: 8px 10px;
    border-left: 2px solid rgba(255, 159, 10, 0.5);
    background: rgba(255, 159, 10, 0.06);
    color: var(--ink-soft);
    font-size: 12px;
    line-height: 1.5;
  }

  .mobile-exit-rule > span {
    min-width: 0;
    overflow-wrap: anywhere;
  }
}

@media (max-width: 560px), (min-width: 561px) and (max-width: 900px) and (orientation: landscape) {
  .stance-panel {
    gap: 12px;
    margin-bottom: 10px;
    padding: 14px 12px 12px;
    border-radius: 12px;
  }

  .stance-glow {
    display: none;
  }

  .kicker {
    justify-content: space-between;
    gap: 6px;
    margin-bottom: 10px;
    font-size: 11px;
  }

  .stance-title-row {
    align-items: center;
    gap: 12px;
  }

  .score-ring {
    flex-basis: 72px;
    width: 72px;
    height: 72px;
  }

  .score-ring-inner {
    width: 58px;
    height: 58px;
  }

  .score-ring-inner strong {
    font-size: 20px;
    letter-spacing: 0;
  }

  .stance-copy {
    min-width: 0;
  }

  .stance-copy h2 {
    margin-bottom: 5px;
  }

  .pill {
    padding: 3px 10px;
    font-size: 16px;
    letter-spacing: 0;
  }

  .reason {
    margin-bottom: 3px;
    font-size: 13px;
    line-height: 1.4;
  }

  .advice {
    font-size: 12px;
    line-height: 1.5;
  }

  .stance-side {
    padding-top: 10px;
    border-top: 1px solid var(--line);
  }

  .market-top {
    min-height: 44px;
    margin: -8px 0 4px;
  }

  .market-title {
    font-size: 12px;
    color: var(--ink-soft);
  }

  .market-links {
    gap: 2px;
  }

  .market-toggle,
  .text-link {
    min-height: 44px;
    padding: 0 8px;
    border-radius: 8px;
    touch-action: manipulation;
  }

  .market-toggle {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    border: 0;
    background: transparent;
    color: var(--accent);
    font: inherit;
    font-size: 12px;
    cursor: pointer;
  }

  .market-toggle:active,
  .text-link:active {
    background: rgba(0, 113, 227, 0.08);
  }

  .desktop-link-label {
    display: none;
  }

  .mobile-link-label {
    display: inline;
  }

  .market-detail {
    display: none;
  }

  .market-detail.open {
    display: block;
  }

  .index-lines {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    margin: 0 0 8px;
  }

  .index-line {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    grid-template-rows: auto auto;
    align-items: baseline;
    column-gap: 8px;
    row-gap: 2px;
    padding: 7px 9px;
  }

  .index-line .n {
    grid-column: 1;
    grid-row: 1;
  }

  .index-line .c {
    grid-column: 1;
    grid-row: 2;
  }

  .index-line .p {
    grid-column: 2;
    grid-row: 1 / span 2;
    align-self: center;
    text-align: right;
  }

  .stat-line {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: stretch;
    gap: 8px;
    padding-top: 0;
    border-top: 0;
  }

  .stat-line > .dot {
    display: none;
  }

  .stat-line .stat {
    display: block;
    min-width: 0;
    min-height: 58px;
    box-sizing: border-box;
    padding: 9px 10px;
    border: 1px solid var(--line);
    border-radius: 6px;
    background: rgba(255, 255, 255, 0.72);
    line-height: 1.25;
  }

  .stat-line .stat:not(.volume-stat) {
    display: grid;
    grid-template-columns: max-content minmax(0, 1fr);
    align-items: center;
    min-height: 50px;
    padding-block: 7px;
    gap: 8px;
  }

  .stat-line .stat:not(.volume-stat) > em {
    margin-bottom: 0;
  }

  .stat-line .stat-value {
    justify-self: end;
    text-align: right;
    white-space: nowrap;
  }

  .stat-line .stat > em {
    display: block;
    margin-bottom: 6px;
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0;
  }

  .stat-line .volume-stat {
    grid-column: 1 / -1;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    min-height: 48px;
    padding-top: 8px;
    padding-bottom: 8px;
    gap: 4px 5px;
  }

  .stat-line .volume-stat > em {
    display: inline;
    margin: 0;
  }

  .stat i.vol-change {
    min-width: 0;
    margin-left: auto;
    gap: 4px;
    font-size: 11px;
  }

  .stat-line .stat > b,
  .stat-line .stat > .slash {
    font-size: 14px;
  }

  .stat-line .volume-stat > b {
    font-size: 15px;
    color: var(--ink);
  }

  .stat-line .stat > .slash {
    margin: 0 1px;
  }

  .breadth-track {
    height: 4px;
    margin-top: 8px;
  }

  .breadth-hint {
    margin-top: 5px;
    font-size: 12px;
  }

  .kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .breadth-forecast {
    grid-template-columns: 1fr;
    gap: 12px;
    padding: 13px 12px;
  }

  .breadth-forecast-main {
    display: grid;
    grid-template-columns: 1fr;
  }

  .breadth-forecast-tug {
    grid-template-columns: minmax(64px, auto) minmax(80px, 1fr) minmax(64px, auto);
    gap: 7px;
  }

  .breadth-forecast-side strong {
    font-size: 18px;
  }

  .breadth-forecast-backtest {
    padding: 10px 0 0;
    border-top: 1px solid var(--line);
    border-left: 0;
  }
}

@media (min-width: 561px) and (max-width: 900px) and (orientation: landscape) {
  .stance-panel {
    grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  }

  .kpi-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (prefers-reduced-motion: reduce) {
  .enter,
  .theme-chip {
    animation: none;
  }

  .kpi:hover,
  .panel:hover {
    transform: none;
  }
}
</style>
