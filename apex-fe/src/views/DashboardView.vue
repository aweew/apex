<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { dashboardHome, dashboardOverview } from '../api/dashboard'
import { runDecision } from '../api/decision'
import { startSyncJob } from '../api/sync'
const router = useRouter()
const HOME_CACHE_KEY = 'apex.dashboard.home.v11'
const loading = ref(false)
const refreshing = ref(false)
const running = ref(false)
const home = ref(null)
const loadError = ref('')
const chartRef = ref(null)
let chart
let equityTimer = null

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
const decision = computed(() => home.value?.decision || null)
const observeAlerts = computed(() => home.value?.observeAlerts || [])
const account = computed(() => home.value?.account || null)
const dataHealth = computed(() => home.value?.dataHealth || null)
const themes = computed(() => market.value?.hotThemes || [])
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
const hasEquity = computed(() => (home.value?.equityCurve || []).length > 0)
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
  const sign = n > 0 ? '+' : ''
  return `${sign}${n.toFixed(2)}%`
}

function pctDir(v) {
  const n = Number(v)
  if (Number.isNaN(n) || n === 0) return 'flat'
  return n > 0 ? 'up' : 'down'
}

/** 量能红绿：放量/正比为红，缩量/负比为绿 */
function volumeDir(trend, vsMa5Pct) {
  if (trend === '放量') return 'up'
  if (trend === '缩量') return 'down'
  const n = Number(vsMa5Pct)
  if (!Number.isNaN(n) && n > 0) return 'up'
  if (!Number.isNaN(n) && n < 0) return 'down'
  return ''
}

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

function fmtPct(v, digits = 2) {
  if (v == null || v === '') return '-'
  return (Number(v) * 100).toFixed(digits) + '%'
}

function fmtScore(v) {
  if (v == null || v === '') return '-'
  return Number(v).toFixed(0)
}

function fmtWeight(v) {
  if (v == null || v === '') return '-'
  return (Number(v) * 100).toFixed(1) + '%'
}

function fmtAsset(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

function renderEquity(points) {
  if (!chartRef.value) return
  const rows = points || []
  if (!rows.length) {
    if (chart) {
      chart.clear()
    }
    return
  }
  if (!chart) chart = echarts.init(chartRef.value)
  const vals = rows.map((p) => Number(p.equity))
  chart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 48, right: 12, top: 20, bottom: 28 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.92)',
      borderColor: 'rgba(0,0,0,0.06)',
      textStyle: { color: '#1d1d1f', fontSize: 12 },
    },
    xAxis: {
      type: 'category',
      data: rows.map((p) => p.tradeDate),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { fontSize: 10, color: '#86868b' },
    },
    yAxis: {
      type: 'value',
      scale: true,
      splitNumber: 3,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: 'rgba(0,0,0,0.05)' } },
      axisLabel: { fontSize: 10, color: '#86868b' },
    },
    series: [
      {
        type: 'line',
        showSymbol: false,
        smooth: 0.25,
        data: vals,
        lineStyle: { color: '#0071e3', width: 2.2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(0,113,227,0.22)' },
            { offset: 1, color: 'rgba(0,113,227,0.02)' },
          ]),
        },
      },
    ],
  })
}

async function loadEquityLazy() {
  try {
    const res = await dashboardOverview()
    const curve = res.data?.equityCurve || []
    const metrics = res.data?.paperMetrics
    if (!home.value) return
    home.value = {
      ...home.value,
      equityCurve: curve,
      account: home.value.account
        ? {
            ...home.value.account,
            maxDrawdown: metrics?.maxDrawdown ?? home.value.account.maxDrawdown,
            winRate: metrics?.winRate ?? home.value.account.winRate,
          }
        : home.value.account,
    }
    writeHomeCache(home.value)
    await nextTick()
    renderEquity(curve)
  } catch {
    // 权益曲线延后失败不影响首屏
  }
}

function scheduleEquityLazy() {
  if (equityTimer) clearTimeout(equityTimer)
  // 错开首屏竞争，避免一进页就打沉重 overview
  equityTimer = setTimeout(() => {
    loadEquityLazy()
  }, 1200)
}

async function load(opts = {}) {
  const silent = !!opts.silent
  const forceRefresh = opts.forceRefresh !== false
  const hasCache = !!home.value
  if (!silent && !hasCache) loading.value = true
  refreshing.value = true
  loadError.value = ''
  try {
    if (forceRefresh) {
      try {
        sessionStorage.removeItem(HOME_CACHE_KEY)
      } catch {
        // ignore
      }
    }
    const res = await dashboardHome(undefined, '我的自选', forceRefresh)
    home.value = res.data
    writeHomeCache(res.data)
    if (forceRefresh && !silent) {
      const vol = res.data?.market?.indexVolumeText
      const stance = res.data?.market?.stance
      ElMessage.success(
        vol
          ? `行情已刷新 · ${stance || ''} · 沪深京 ${vol}`
          : `行情已刷新 · ${stance || '简报已重建'}`,
      )
    }
    await nextTick()
    renderEquity(home.value?.equityCurve)
    scheduleEquityLazy()
  } catch (e) {
    if (!hasCache) {
      home.value = null
      const msg = e.message || '加载失败'
      loadError.value = msg.includes('404') || msg.includes('Not Found')
        ? '看板接口未就绪：请重启后端后再刷新（/api/dashboard/home）'
        : msg
      ElMessage.error(loadError.value)
    } else {
      ElMessage.warning('刷新失败，仍展示本地缓存（可能过期）')
    }
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

const syncingClose = ref(false)

/** 收盘一键：跳转同步中心看进度 */
async function onCloseBundleSync() {
  syncingClose.value = true
  try {
    const res = await startSyncJob({
      taskType: 'CLOSE_BUNDLE',
      types: 'INDUSTRY,CONCEPT,THEME',
    })
    ElMessage.success(`已启动一键收盘同步 #${res.data?.id || ''}`)
    router.push('/sync')
  } catch (e) {
    ElMessage.error(e.message || '启动失败')
  } finally {
    syncingClose.value = false
  }
}

async function onRunDecision() {
  running.value = true
  try {
    const res = await runDecision({ groupName: '我的自选' })
    const obs = res.data?.observeUpserted
    ElMessage.success(
      obs != null
        ? `决策已生成，观察池写入 ${obs} 条剧本`
        : res.data?.message || '决策已生成',
    )
    router.push('/observe')
  } catch (e) {
    ElMessage.error(e.message || '生成失败')
  } finally {
    running.value = false
  }
}

function onResize() {
  chart?.resize()
}

onMounted(() => {
  const cached = readHomeCache()
  if (cached) {
    home.value = cached
    nextTick(() => renderEquity(cached.equityCurve))
    load({ silent: true, forceRefresh: true })
  } else {
    load({ forceRefresh: true })
  }
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (equityTimer) clearTimeout(equityTimer)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="page dash" v-loading="loading">
    <header class="header dash-header">
      <div>
        <p class="eyebrow">Apex · Command</p>
        <h1>看板</h1>
        <p class="sub">
          {{
            home?.message
              || (loading || refreshing ? '正在加载市场与决策…' : '先看市场立场，再处理买卖行动')
          }}
        </p>
      </div>
      <div class="actions">
        <el-button type="primary" class="cta" :loading="running" @click="onRunDecision">
          一键生成决策
        </el-button>
        <el-button type="success" plain :loading="syncingClose" @click="onCloseBundleSync">
          一键收盘同步
        </el-button>
        <el-button @click="router.push('/decision')">智能决策</el-button>
        <el-button plain @click="router.push('/sync')">同步中心</el-button>
        <el-button plain :loading="refreshing" @click="load({ forceRefresh: true })">刷新行情</el-button>
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
        看板数据依赖 <code>/api/dashboard/home</code>。重启后端后点「刷新」；指数/板块未同步时简报也会偏空。
      </template>
    </el-alert>

    <nav class="workflow-strip" aria-label="今日工作流">
      <button type="button" class="wf-step" @click="router.push('/sync')">
        <span class="wf-idx">1</span>
        <span class="wf-label">同步数据</span>
      </button>
      <span class="wf-sep" aria-hidden="true" />
      <button type="button" class="wf-step" @click="onRunDecision">
        <span class="wf-idx">2</span>
        <span class="wf-label">生成决策</span>
      </button>
      <span class="wf-sep" aria-hidden="true" />
      <button type="button" class="wf-step" @click="router.push('/decision')">
        <span class="wf-idx">3</span>
        <span class="wf-label">看买卖清单</span>
      </button>
      <span class="wf-sep" aria-hidden="true" />
      <button type="button" class="wf-step" @click="router.push('/observe')">
        <span class="wf-idx">4</span>
        <span class="wf-label">盯观察池</span>
      </button>
      <span class="wf-sep" aria-hidden="true" />
      <button type="button" class="wf-step" @click="router.push('/paper')">
        <span class="wf-idx">5</span>
        <span class="wf-label">模拟执行</span>
      </button>
    </nav>

    <!-- ① 市场立场（始终占位，避免整块消失） -->
    <section
      class="stance-panel enter"
      :class="market ? stanceClass(market.stance) : 'stance-empty'"
    >
      <div class="stance-glow" aria-hidden="true" />
      <div class="stance-main">
        <div class="kicker">
          <span>市场立场 · {{ market?.asOf || (loading || refreshing ? '加载中' : '待加载') }}</span>
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
                    ? '首页接口未返回市场简报，请先重启后端并刷新'
                    : (loading || refreshing
                      ? '正在拉取市场简报…'
                      : '若长期空白，请先同步指数/板块/涨停'))
              }}
            </p>
            <p class="advice">
              {{
                market?.positionAdvice
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
            <span class="market-title">大盘</span>
            <div class="market-links">
              <button type="button" class="text-link" @click="router.push('/market')">详情</button>
            </div>
          </div>

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

          <div class="stat-line">
            <span
              class="stat"
              :title="market?.volumeVsMa5Pct != null ? `较前日 ${fmtIndexPct(market.volumeVsMa5Pct)}` : '三市成交额（上证+深成+北证）'"
            >
              <em>三市</em>
              <b :class="volumeDir(market?.volumeTrend, market?.volumeVsMa5Pct)">{{ market?.indexVolumeText || '--' }}</b>
              <i
                v-if="market?.volumeLabel"
                :class="volumeDir(market?.volumeTrend, market?.volumeVsMa5Pct)"
              >{{ market.volumeLabel }}</i>
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
              <template v-if="breadth">
                <b class="up">{{ breadth.up }}</b>
                <span class="slash">/</span>
                <b class="flat">{{ breadth.hasFlat ? breadth.flat : '--' }}</b>
                <span class="slash">/</span>
                <b class="down">{{ breadth.down }}</b>
              </template>
              <b v-else class="miss">--</b>
            </span>
            <span class="dot" aria-hidden="true" />
            <span
              class="stat"
              :title="market?.limitUpCount != null || market?.limitDownCount != null
                ? `涨停 ${market?.limitUpCount ?? '--'} / 跌停 ${market?.limitDownCount ?? '--'}`
                : '暂无涨跌停家数'"
            >
              <em>涨跌停</em>
              <b class="up">{{ market?.limitUpCount ?? '--' }}</b>
              <span class="slash">/</span>
              <b class="down">{{ market?.limitDownCount ?? '--' }}</b>
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

    <section v-if="effect" class="effect-strip enter delay-1" aria-label="赚钱效应">
      <div class="effect-head">
        <span class="effect-title">赚钱效应</span>
        <span v-if="effect.hint" class="effect-hint">{{ effect.hint }}</span>
        <span v-else class="effect-hint muted">平均股价 · 中位数 · 全A等权 · 微盘股 · 沪深300</span>
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
        <div class="effect-cell" :class="pctDir(effect.equalWeightPctChg)" title="800010 全A(沪深京)等权，对齐 880008">
          <em>全A等权</em>
          <b>{{ fmtIndexPct(effect.equalWeightPctChg) }}</b>
        </div>
        <div class="effect-cell" :class="pctDir(effect.microPctChg ?? effect.csi2000PctChg)" title="800007 Choice微盘，对齐 880823">
          <em>微盘股</em>
          <b>{{ fmtIndexPct(effect.microPctChg ?? effect.csi2000PctChg) }}</b>
        </div>
        <div class="effect-cell" :class="pctDir(effect.hs300PctChg)" title="000300 沪深300">
          <em>沪深300</em>
          <b>{{ fmtIndexPct(effect.hs300PctChg) }}</b>
        </div>
      </div>
    </section>

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
          <b>{{ item.code }}</b>
          <span>{{ item.name || '' }}</span>
          <em>{{ item.status === 'TRIGGERED' ? '已触发' : '接近' }}</em>
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
              :loading="running"
              @click="onRunDecision"
            >
              一键生成决策
            </el-button>
          </el-empty>
          <el-table
            v-else
            :data="topBuys"
            size="small"
            class="dash-table"
            empty-text="暂无买入建议"
            stripe
          >
            <el-table-column prop="code" label="代码" width="100" class-name="code-col">
              <template #default="{ row }">
                <button type="button" class="code-link" @click="router.push(`/stock/${row.code}`)">
                  {{ row.code }}
                </button>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="名称" width="90" />
            <el-table-column prop="strategyId" label="策略" width="56" />
            <el-table-column label="评分" width="110">
              <template #default="{ row }">
                <ScoreBar :score="row.score" />
              </template>
            </el-table-column>
            <el-table-column label="估值" width="88">
              <template #default="{ row }">
                <span class="muted">{{ row.valuationLabel || '-' }}</span>
                <el-tag
                  v-if="row.executableHint"
                  size="small"
                  type="success"
                  effect="plain"
                  style="margin-left: 4px"
                >可执行</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="仓位" width="64">
              <template #default="{ row }">
                <span class="num">{{ fmtWeight(row.suggestedWeight) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="联动" min-width="88">
              <template #default="{ row }">
                <el-tag
                  v-if="row.linkHint"
                  size="small"
                  effect="plain"
                  :type="String(row.linkHint).includes('降权') ? 'danger' : 'success'"
                >{{ row.linkHint }}</el-tag>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="主线" min-width="72">
              <template #default="{ row }">
                <el-tag v-if="row.mainlineMatch" size="small" type="warning" effect="light" round>
                  {{ row.mainlineName || '匹配' }}
                </el-tag>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <!-- ③ 持仓行动 -->
      <section class="panel action-panel enter delay-2">
        <div class="panel-head">
          <div>
            <h3>持仓行动</h3>
            <p class="panel-desc">止损止盈与策略卖出优先</p>
          </div>
          <el-button link type="primary" @click="router.push('/holding')">我的持仓</el-button>
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
          <el-table v-else :data="topSells" size="small" class="dash-table" stripe>
            <el-table-column prop="code" label="代码" width="100" class-name="code-col">
              <template #default="{ row }">
                <button type="button" class="code-link" @click="router.push(`/stock/${row.code}`)">
                  {{ row.code }}
                </button>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="名称" width="90" />
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
            <el-table-column prop="exitRule" label="触发" min-width="120" show-overflow-tooltip />
          </el-table>
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
          <el-button link type="primary" @click="router.push('/sector')">板块</el-button>
          <el-button link type="primary" @click="router.push('/limit-up')">涨停复盘</el-button>
        </div>
      </div>
      <div v-if="themes.length" class="theme-row">
        <span v-for="(t, i) in themes" :key="t" class="theme-chip" :style="{ '--i': i }">
          {{ t }}
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

    <!-- ⑤ 账户快览 -->
    <section class="panel enter delay-4">
      <div class="panel-head">
        <div>
          <h3>账户快览</h3>
          <p class="panel-desc">核心指标 · 细节见模拟盘</p>
        </div>
        <el-button link type="primary" @click="router.push('/paper')">更多绩效 →</el-button>
      </div>
      <div class="kpi-row">
        <div class="kpi">
          <label>总资产</label>
          <b class="num">{{ account ? fmtAsset(account.totalAsset) : '-' }}</b>
        </div>
        <div class="kpi">
          <label>累计收益</label>
          <b
            class="num"
            :class="account && Number(account.totalReturn) >= 0 ? 'up' : account ? 'down' : ''"
          >
            {{ account ? fmtPct(account.totalReturn) : '-' }}
          </b>
        </div>
        <div class="kpi">
          <label>仓位</label>
          <b class="num">{{ account ? fmtPct(account.positionRatio, 1) : '-' }}</b>
        </div>
        <div class="kpi">
          <label>最大回撤</label>
          <b class="num">{{ account ? fmtPct(account.maxDrawdown) : '-' }}</b>
        </div>
        <div class="kpi">
          <label>胜率</label>
          <b class="num">{{ account ? fmtPct(account.winRate, 0) : '-' }}</b>
        </div>
        <div class="kpi" :class="{ warn: account && (account.criticalCount || 0) > 0 }">
          <label>告警</label>
          <b class="num">
            {{ account ? `C${account.criticalCount || 0} / W${account.warnCount || 0}` : '-' }}
          </b>
        </div>
      </div>
      <div class="chart-shell" :class="{ empty: !hasEquity }">
        <div class="chart-label">纸面权益</div>
        <div ref="chartRef" class="mini-chart" :class="{ hidden: !hasEquity }" />
        <div v-if="!hasEquity" class="chart-empty">
          <p>暂无权益曲线</p>
          <span>在模拟盘产生成交后，这里会画出纸面净值走势。</span>
          <el-button size="small" round @click="router.push('/paper')">去模拟盘</el-button>
        </div>
      </div>
    </section>

    <!-- ⑥ 数据可信度 -->
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

.cta {
  min-width: 132px;
  font-weight: 600;
}

.workflow-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin: 0 0 16px;
  padding: 10px 12px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  background: var(--glass);
}

.wf-step {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 0;
  background: transparent;
  padding: 6px 10px;
  border-radius: 10px;
  cursor: pointer;
  color: var(--ink-soft);
  font: inherit;
  transition: background 0.15s ease, color 0.15s ease;
}

.wf-step:hover {
  background: rgba(0, 113, 227, 0.08);
  color: var(--accent);
}

.wf-idx {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: var(--accent);
}

.wf-label {
  font-size: 13px;
  font-weight: 550;
}

.wf-sep {
  width: 18px;
  height: 1px;
  background: var(--line-strong);
  margin: 0 2px;
}

@media (max-width: 720px) {
  .wf-sep {
    display: none;
  }
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
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
  align-items: stretch;
}

.action-panel {
  display: flex;
  flex-direction: column;
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
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.observe-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.65);
  border-radius: 999px;
  padding: 6px 12px;
  font: inherit;
  cursor: pointer;
}

.observe-chip b {
  font-weight: 700;
}

.observe-chip span {
  font-size: 12px;
  color: var(--slate);
}

.observe-chip em {
  font-style: normal;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 999px;
}

.observe-chip.near em {
  background: rgba(255, 159, 10, 0.15);
  color: #c93400;
}

.observe-chip.trig em {
  background: rgba(255, 59, 48, 0.12);
  color: var(--up);
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
  border-radius: calc(var(--radius) + 4px);
  border: 1px solid var(--glass-border);
  background: var(--glass-strong);
  backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  -webkit-backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  box-shadow: var(--shadow);
}

.stance-glow {
  position: absolute;
  inset: -40% auto auto -10%;
  width: 55%;
  height: 140%;
  pointer-events: none;
  opacity: 0.55;
  filter: blur(40px);
  background: radial-gradient(circle at 30% 40%, rgba(0, 113, 227, 0.18), transparent 65%);
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
  font-size: 10px;
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
  gap: 10px;
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
  gap: 1px;
  min-width: 0;
  padding: 6px 8px;
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
  font-size: 10px;
  color: var(--muted);
}

.index-line .c {
  font-size: 10px;
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

.stat em {
  font-style: normal;
  color: var(--muted);
}

.stat b {
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  color: var(--ink);
}

.stat i {
  font-style: normal;
  color: var(--slate);
  font-size: 11px;
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
  font-size: 11px;
  line-height: 1.35;
  color: var(--muted, #8a8f98);
  letter-spacing: -0.01em;
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
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.effect-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 10px 10px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.03);
  border: 1px solid transparent;
}

.effect-cell em {
  font-style: normal;
  font-size: 11px;
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

.effect-cell.up {
  background: rgba(255, 59, 48, 0.06);
  border-color: rgba(255, 59, 48, 0.1);
}

.effect-cell.down {
  background: rgba(52, 199, 89, 0.06);
  border-color: rgba(52, 199, 89, 0.1);
}

.effect-cell.up b { color: var(--up); }
.effect-cell.down b { color: var(--down); }
.effect-cell.flat b { color: var(--slate, #64748b); }

@media (max-width: 560px) {
  .effect-grid {
    grid-template-columns: 1fr 1fr;
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
  backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  -webkit-backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  box-shadow: var(--shadow-soft);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.panel:hover {
  box-shadow: var(--shadow);
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

.dash-table :deep(.code-col .cell) {
  overflow: visible;
  padding-left: 8px;
  padding-right: 4px;
}

.code-link {
  border: 0;
  background: transparent;
  padding: 0;
  margin: 0;
  font: inherit;
  font-size: 13px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
  color: var(--accent);
  cursor: pointer;
  white-space: nowrap;
}

.code-link:hover {
  color: var(--accent-hover);
  text-decoration: underline;
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

@media (max-width: 560px) {
  .stance-title-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .index-lines {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
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
