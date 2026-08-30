<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DataAnalysis, MagicStick, Refresh, TrendCharts } from '@element-plus/icons-vue'
import { fetchStockAnalysis, fetchStockDetail } from '../api/stock'
import {
  captureElementBlob,
  copyImageBlob,
  downloadBlob,
  prepareLongCapture,
  resetScrollForCapture,
  shareFilename,
} from '../utils/shareCapture'
import BrandShareLockup from './share/BrandShareLockup.vue'
import BrandShareFoot from './share/BrandShareFoot.vue'
import { BRAND } from '../brand/identity.js'
import FloatingShareButton from './FloatingShareButton.vue'

const props = defineProps({
  code: { type: String, required: true },
})

const router = useRouter()
const loading = ref(false)
const aiLoading = ref(false)
const aiRequested = ref(false)
const side = ref('BUY')
const data = ref(null)
const error = ref('')
const shareCardRef = ref(null)

const sharing = ref(false)
const capturingShare = ref(false)
const shareOpen = ref(false)
const sharePreviewUrl = ref('')
const copying = ref(false)
const downloading = ref(false)
let sharePreviewObjectUrl = ''
let rulesRequestSeq = 0
let aiRequestSeq = 0

const ANALYSIS_SHARE_WIDTH = 720

const stanceClass = computed(() => {
  const s = data.value?.stance || ''
  if (s.includes('看多')) return 'good'
  if (s.includes('看空')) return 'bad'
  return 'mid'
})

const aiStanceClass = computed(() => {
  const s = data.value?.ai?.stance || ''
  if (s.includes('看多')) return 'good'
  if (s.includes('看空')) return 'bad'
  return 'mid'
})

const STRATEGY_META = {
  S1: {
    name: '均线趋势',
    tip: '快线上穿慢线且放量 → 偏买；跌破快线 → 偏卖',
  },
  S2: {
    name: 'RSI回调',
    tip: '价在均线上方、RSI 从超卖回升 → 偏买；RSI 超买或跌破均线 → 偏卖',
  },
  S3: {
    name: '突破放量',
    tip: '收盘创近高且量比放大 → 偏买；跌破突破日低点 → 偏卖',
  },
}

const shareTitle = computed(() => {
  const d = data.value
  if (!d) return props.code
  return `${d.name || ''} ${d.code || props.code}`.trim()
})

const scorePct = computed(() => {
  const n = Number(data.value?.compositeScore)
  if (Number.isNaN(n)) return 0
  return Math.max(0, Math.min(100, n))
})

const bullTop = computed(() => (data.value?.bullPoints || []).slice(0, 3).map(annotatePoint))

const bearTop = computed(() => {
  const bears = data.value?.bearPoints || []
  const risks = data.value?.riskFlags || []
  return [...bears, ...risks].slice(0, 4).map(annotatePoint)
})

const signalCards = computed(() => {
  const list = data.value?.signals || []
  return list.slice(0, 6).map((row) => {
    const id = String(row.strategyId || '').toUpperCase()
    const meta = STRATEGY_META[id] || { name: id || '策略', tip: '' }
    const sell = /SELL|卖/i.test(String(row.side || ''))
    return {
      ...row,
      sid: id,
      title: meta.name,
      tip: meta.tip,
      sell,
      sideLabel: sell ? '卖出' : /BUY|买/i.test(String(row.side || '')) ? '买入' : row.side,
      reasonText: parseReason(row.reasonJson),
    }
  })
})

const techHitRate = computed(() => {
  const hit = Number(data.value?.tech?.hitCount) || 0
  const total = Number(data.value?.tech?.total) || 0
  if (!total) return 0
  return Math.round((hit / total) * 100)
})

const radarDirectionLabel = computed(() => (side.value === 'SELL' ? '空头/风险' : '多头/机会'))

const radarResultText = computed(() => {
  const loadedSide = String(data.value?.tech?.side || '').toUpperCase()
  if (loading.value && loadedSide !== side.value) {
    return `切换中 · ${radarDirectionLabel.value}`
  }
  const hit = Number(data.value?.tech?.hitCount) || 0
  const total = Number(data.value?.tech?.total) || 0
  return total > 0
    ? `${radarDirectionLabel.value} · 命中 ${hit}/${total}`
    : `${radarDirectionLabel.value} · 暂无信号`
})

const todayRadarItems = computed(() => {
  const analysis = data.value
  if (!analysis) return []

  const techHitCount = Number(analysis.tech?.hitCount) || 0
  const techTotal = Number(analysis.tech?.total) || 0
  const newsCount = analysis.recentNews?.length || 0
  const valuationLevel = analysis.valuation?.level
  const valuationUnknown = !analysis.valuation || valuationLevel === 'UNKNOWN'
  const valuationCheap = ['UNDERVALUED', 'SLIGHTLY_CHEAP'].includes(valuationLevel)
  const valuationFacts = []
  if (analysis.valuation?.levelLabel) valuationFacts.push(analysis.valuation.levelLabel)
  if (analysis.valuation?.pePercentile != null) {
    valuationFacts.push(`PE 行业分位 ${fmtNum(analysis.valuation.pePercentile, 0)}%`)
  }
  if (analysis.valuation?.marginOfSafety != null) {
    valuationFacts.push(`安全边际 ${fmtPct(analysis.valuation.marginOfSafety)}`)
  }

  const sectorActive = Number(analysis.capital?.sectorPctChg) > 0
    || Number(analysis.capital?.sectorMainNetInflow) > 0
  let marketValue = '未命中热点'
  let marketTone = sectorActive ? 'watch' : 'quiet'
  if (analysis.capital?.hotHit) {
    marketValue = `多源共振 ×${analysis.capital.hotSourceCount || 1}`
    marketTone = 'hot'
  } else if (sectorActive) {
    marketValue = '板块有热度'
  }

  return [
    {
      label: '技术信号',
      value: techTotal ? `${techHitCount}/${techTotal} 命中` : '数据不足',
      detail: analysis.tech?.summary || '暂无可用技术结构',
      tone: techTotal && techHitCount ? 'active' : 'quiet',
    },
    {
      label: '个股消息',
      value: newsCount ? `${newsCount} 条直接相关` : '暂未收录',
      detail: analysis.newsSummary || '近7日未收录直接相关消息',
      tone: newsCount ? 'active' : 'quiet',
    },
    {
      label: '估值',
      value: valuationUnknown ? '数据不足' : valuationCheap ? '存在洼地' : '未处洼地',
      detail: valuationFacts.join(' · ') || '缺少可核验估值数据',
      tone: valuationCheap ? 'good' : valuationUnknown ? 'quiet' : 'neutral',
    },
    {
      label: '盘面热点',
      value: marketValue,
      detail: analysis.capital?.summary || '暂无板块资金与热点证据',
      tone: marketTone,
    },
  ]
})

function strategyMeta(id) {
  return STRATEGY_META[String(id || '').toUpperCase()] || null
}

function annotatePoint(text) {
  const raw = String(text || '')
  const m = raw.match(/\b(S[123])\b/i)
  if (!m) return { text: raw, tip: '' }
  const meta = strategyMeta(m[1])
  if (!meta) return { text: raw, tip: '' }
  return {
    text: raw.replace(new RegExp(`策略\\s*${m[1]}`, 'i'), `策略 ${m[1].toUpperCase()}（${meta.name}）`),
    tip: meta.tip,
  }
}

function parseReason(raw) {
  if (raw == null || raw === '') return ''
  const text = String(raw).trim()
  if (!text) return ''
  if (!(text.startsWith('{') || text.startsWith('['))) {
    return text.length > 140 ? `${text.slice(0, 140)}…` : text
  }
  try {
    const obj = JSON.parse(text)
    if (typeof obj === 'string') return obj
    if (obj?.summary) return String(obj.summary)
    if (obj?.reason) return String(obj.reason)
    if (obj?.message) return String(obj.message)
    if (obj?.desc) return String(obj.desc)
    const parts = []
    for (const [key, value] of Object.entries(obj)) {
      if (value == null || value === '') continue
      if (typeof value === 'object') continue
      parts.push(`${key} ${value}`)
      if (parts.length >= 4) break
    }
    const joined = parts.join(' · ')
    return joined.length > 140 ? `${joined.slice(0, 140)}…` : joined
  } catch {
    return text.length > 140 ? `${text.slice(0, 140)}…` : text
  }
}

function dirClass(v) {
  const n = Number(v)
  if (Number.isNaN(n) || n === 0) return ''
  return n > 0 ? 'up' : 'down'
}

function scoreBar(v) {
  const n = Number(v)
  if (Number.isNaN(n)) return 0
  return Math.max(0, Math.min(100, n))
}

function revokeSharePreview() {
  if (sharePreviewObjectUrl) {
    URL.revokeObjectURL(sharePreviewObjectUrl)
    sharePreviewObjectUrl = ''
  }
  sharePreviewUrl.value = ''
}

function fmtPct(v, digits = 2) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return '-'
  const sign = n > 0 ? '+' : ''
  return `${sign}${n.toFixed(digits)}%`
}

function fmtNum(v, digits = 2) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return '-'
  return n.toFixed(digits)
}

function fmtMoneyYi(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return '-'
  if (Math.abs(n) >= 1e8) return `${(n / 1e8).toFixed(2)}亿`
  if (Math.abs(n) >= 1e4) return `${(n / 1e4).toFixed(0)}万`
  return n.toFixed(0)
}

function fmtNewsTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

function newsSourceLabel(source) {
  return {
    eastmoney: '东财',
    cls: '财联社',
    ths: '同花顺',
    sina: '新浪',
    cctv: '央视',
  }[source] || source || '资讯'
}

function periodRet(closes, lookback) {
  if (!Array.isArray(closes) || closes.length <= lookback) return null
  const end = Number(closes[closes.length - 1])
  const start = Number(closes[closes.length - 1 - lookback])
  if (!Number.isFinite(end) || !Number.isFinite(start) || start === 0) return null
  return Number((((end - start) / start) * 100).toFixed(2))
}

function hasPeriod(v) {
  return v != null && v !== '' && !Number.isNaN(Number(v))
}

/** 后端缺近 N 日时，用日线本地补齐 */
async function enrichQuotePeriods(payload) {
  if (!payload) return payload
  if (hasPeriod(payload.pctChg3) && hasPeriod(payload.pctChg5) && hasPeriod(payload.pctChg20)) {
    return payload
  }
  try {
    const res = await fetchStockDetail(props.code, 80, false)
    const bars = res.data?.bars || []
    const closes = bars
      .map((b) => Number(b.closePrice))
      .filter((n) => Number.isFinite(n))
    const basic = res.data?.basic || {}
    return {
      ...payload,
      latestPrice: payload.latestPrice ?? basic.latestPrice,
      pctChg: payload.pctChg ?? basic.pctChg,
      industry: payload.industry || basic.industry,
      pctChg3: hasPeriod(payload.pctChg3) ? payload.pctChg3 : periodRet(closes, 3),
      pctChg5: hasPeriod(payload.pctChg5) ? payload.pctChg5 : periodRet(closes, 5),
      pctChg20: hasPeriod(payload.pctChg20) ? payload.pctChg20 : periodRet(closes, 20),
    }
  } catch {
    return payload
  }
}

async function loadRules() {
  if (!props.code) return
  const requestSeq = ++rulesRequestSeq
  const requestedSide = side.value
  aiRequestSeq += 1
  loading.value = true
  aiLoading.value = false
  aiRequested.value = false
  error.value = ''
  try {
    const res = await fetchStockAnalysis(props.code, requestedSide, 120, false, false)
    const next = await enrichQuotePeriods(res.data)
    if (requestSeq !== rulesRequestSeq || requestedSide !== side.value) return
    data.value = next
  } catch (e) {
    if (requestSeq !== rulesRequestSeq || requestedSide !== side.value) return
    data.value = null
    error.value = e.message || '加载失败'
    ElMessage.error(error.value)
  } finally {
    if (requestSeq === rulesRequestSeq) loading.value = false
  }
}

async function loadAi(forceAi = false, requestedSide = side.value) {
  if (!props.code) return
  const requestSeq = ++aiRequestSeq
  aiRequested.value = true
  aiLoading.value = true
  try {
    const res = await fetchStockAnalysis(props.code, requestedSide, 120, true, forceAi)
    const next = await enrichQuotePeriods(res.data)
    if (requestSeq !== aiRequestSeq || requestedSide !== side.value) return
    if (!data.value) {
      data.value = next
    } else {
      // 保留已展示的规则区，合并 AI；近 N 日涨跌优先保留已有有效值
      data.value = {
        ...data.value,
        ...next,
        ai: next?.ai,
        freshness: next?.freshness || data.value.freshness,
        pctChg3: hasPeriod(next?.pctChg3) ? next.pctChg3 : data.value.pctChg3,
        pctChg5: hasPeriod(next?.pctChg5) ? next.pctChg5 : data.value.pctChg5,
        pctChg20: hasPeriod(next?.pctChg20) ? next.pctChg20 : data.value.pctChg20,
        latestPrice: next?.latestPrice ?? data.value.latestPrice,
        pctChg: next?.pctChg ?? data.value.pctChg,
      }
    }
    if (res.data?.ai?.configured === false) {
      ElMessage.warning(res.data.ai.disclaimer || 'AI 未配置')
    } else if (forceAi) {
      ElMessage.success(res.data?.ai?.fromCache ? '仍为缓存解读' : 'AI 解读已更新')
    }
  } catch (e) {
    if (requestSeq !== aiRequestSeq || requestedSide !== side.value) return
    if (!data.value) {
      error.value = e.message || '加载失败'
      ElMessage.error(error.value)
    } else {
      ElMessage.error(e.message || 'AI 解读失败')
    }
  } finally {
    if (requestSeq === aiRequestSeq) aiLoading.value = false
  }
}

function load(forceAi = false) {
  if (forceAi) return loadAi(true)
  return loadRules()
}

async function refreshAi() {
  return loadAi(true)
}

async function captureAnalysisShare() {
  const el = shareCardRef.value
  if (!el) throw new Error('研判内容未就绪')
  capturingShare.value = true
  await nextTick()
  try {
    const restoreScroll = resetScrollForCapture(el)
    const restoreLong = prepareLongCapture(el)
    const prev = {
      padding: el.style.padding,
      background: el.style.background,
      boxSizing: el.style.boxSizing,
      width: el.style.width,
      minWidth: el.style.minWidth,
      maxWidth: el.style.maxWidth,
    }
    el.style.boxSizing = 'border-box'
    el.style.width = `${ANALYSIS_SHARE_WIDTH}px`
    el.style.minWidth = `${ANALYSIS_SHARE_WIDTH}px`
    el.style.maxWidth = 'none'
    el.style.padding = '28px 30px 22px'
    el.style.background = '#ffffff'
    try {
      return await captureElementBlob(el, {
        scale: 2,
        backgroundColor: '#ffffff',
        style: {
          width: `${ANALYSIS_SHARE_WIDTH}px`,
          minWidth: `${ANALYSIS_SHARE_WIDTH}px`,
          maxWidth: 'none',
          padding: '28px 30px 22px',
          backgroundColor: '#ffffff',
          boxSizing: 'border-box',
        },
      })
    } finally {
      el.style.padding = prev.padding
      el.style.background = prev.background
      el.style.boxSizing = prev.boxSizing
      el.style.width = prev.width
      el.style.minWidth = prev.minWidth
      el.style.maxWidth = prev.maxWidth
      restoreLong()
      restoreScroll()
    }
  } finally {
    capturingShare.value = false
    await nextTick()
  }
}

async function openShare() {
  if (!data.value) {
    ElMessage.warning('暂无研判可分享')
    return
  }
  if (aiLoading.value) {
    ElMessage.info('AI 仍在生成，将分享当前已出内容')
  }
  sharing.value = true
  try {
    const blob = await captureAnalysisShare()
    revokeSharePreview()
    sharePreviewObjectUrl = URL.createObjectURL(blob)
    sharePreviewUrl.value = sharePreviewObjectUrl
    shareOpen.value = true
  } catch (e) {
    console.error('生成个股分析分享图失败', e)
    ElMessage.error(e.message || '截图失败')
  } finally {
    sharing.value = false
  }
}

async function onCopyShare() {
  copying.value = true
  try {
    await copyImageBlob(captureAnalysisShare())
    ElMessage.success('已复制到剪贴板，可直接粘贴到微信/文档')
  } catch (e) {
    console.error('复制个股分析分享图失败', e)
    ElMessage.error(e.message || '复制失败，请改用下载')
  } finally {
    copying.value = false
  }
}

async function onDownloadShare() {
  downloading.value = true
  try {
    const blob = await captureAnalysisShare()
    downloadBlob(blob, shareFilename('apex_analysis', shareTitle.value))
    ElMessage.success('已下载分享图')
  } catch (e) {
    console.error('下载个股分析分享图失败', e)
    ElMessage.error(e.message || '下载失败')
  } finally {
    downloading.value = false
  }
}

function closeShare() {
  shareOpen.value = false
  revokeSharePreview()
}

watch(
  () => [props.code, side.value],
  () => loadRules(),
  { immediate: true },
)

onBeforeUnmount(() => {
  revokeSharePreview()
})

defineExpose({ reload: () => loadRules() })
</script>

<template>
  <div class="analysis" v-loading="loading && !data">
    <div class="analysis-toolbar no-capture">
      <div class="analysis-mode">
        <div class="analysis-mode-head">
          <span class="analysis-mode-title">技术信号</span>
          <span class="analysis-mode-result" aria-live="polite">{{ radarResultText }}</span>
        </div>
        <el-radio-group v-model="side" class="analysis-direction" aria-label="技术信号方向">
          <el-radio-button value="BUY">多头/机会</el-radio-button>
          <el-radio-button value="SELL">空头/风险</el-radio-button>
        </el-radio-group>
      </div>

      <div class="analysis-actions">
        <el-tooltip content="刷新当前方向研判" placement="bottom">
          <el-button class="analysis-refresh" :loading="loading" aria-label="刷新当前方向研判" @click="loadRules">
            <el-icon v-if="!loading"><Refresh /></el-icon>
          </el-button>
        </el-tooltip>
        <el-button class="analysis-ai" type="primary" plain :loading="aiLoading" :disabled="loading" @click="refreshAi">
          <el-icon v-if="!aiLoading"><MagicStick /></el-icon>
          <span>AI 解读</span>
        </el-button>
        <el-button
          class="analysis-link"
          plain
          @click="router.push({ path: `/stock/${code}`, query: { tab: 'valuation' } })"
        >
          <el-icon><DataAnalysis /></el-icon>
          <span>完整估值</span>
        </el-button>
        <el-button class="analysis-link" plain @click="router.push({ path: '/backtest', query: { code } })">
          <el-icon><TrendCharts /></el-icon>
          <span>历史回测</span>
        </el-button>
      </div>
    </div>

    <FloatingShareButton
      v-if="!shareOpen"
      :loading="sharing"
      :disabled="!data"
      label="分享个股研判"
      @click="openShare"
    />

    <el-empty v-if="!loading && error" :description="error">
      <el-button type="primary" @click="loadRules">重试</el-button>
    </el-empty>

    <div v-if="data" ref="shareCardRef" class="share-card" :class="{ 'is-share-capture': capturingShare }">
      <header class="share-head">
        <div class="share-head-top">
          <div class="share-head-main">
            <BrandShareLockup v-if="capturingShare" subtitle="个股研判" :size="44" />
            <div class="quote-id">
              <StockIdentity :security="data" prominent />
              <span v-if="data.industry" class="quote-ind">{{ data.industry }}</span>
            </div>
          </div>
          <span class="stance-pill" :class="stanceClass">{{ data.stance }}</span>
        </div>
        <div class="quote-core">
          <div class="qc-item price">
            <label>现价</label>
            <b :class="dirClass(data.pctChg)">{{ fmtNum(data.latestPrice, 2) }}</b>
          </div>
          <div class="qc-item">
            <label>今日</label>
            <b :class="dirClass(data.pctChg)">{{ fmtPct(data.pctChg) }}</b>
          </div>
          <div class="qc-item">
            <label>近3日</label>
            <b :class="dirClass(data.pctChg3)">{{ fmtPct(data.pctChg3) }}</b>
          </div>
          <div class="qc-item">
            <label>近5日</label>
            <b :class="dirClass(data.pctChg5)">{{ fmtPct(data.pctChg5) }}</b>
          </div>
          <div class="qc-item">
            <label>近20日</label>
            <b :class="dirClass(data.pctChg20)">{{ fmtPct(data.pctChg20) }}</b>
          </div>
        </div>
      </header>

      <p v-if="data.freshness?.note" class="fresh-note" :class="{ stale: data.freshness.barsStale }">
        {{ data.freshness.barsStale ? '日线滞后' : '日线新鲜' }}
        <span v-if="data.freshness.lastBarDate"> · {{ data.freshness.lastBarDate }}</span>
        <span v-if="data.freshness.barCount != null"> · {{ data.freshness.barCount }} 根</span>
      </p>

      <section class="today-radar" aria-labelledby="today-radar-title">
        <header class="today-radar-head">
          <div>
            <h2 id="today-radar-title">今日雷达</h2>
            <span>本地规则快照</span>
          </div>
          <small>{{ radarResultText }}</small>
        </header>
        <div class="today-radar-grid">
          <article
            v-for="item in todayRadarItems"
            :key="item.label"
            class="today-radar-item"
            :data-tone="item.tone"
          >
            <span>{{ item.label }}</span>
            <b>{{ item.value }}</b>
            <p :title="item.detail">{{ item.detail }}</p>
          </article>
        </div>
      </section>

      <!-- 结论总览 -->
      <section class="hero" :class="stanceClass">
        <div class="hero-score">
          <div class="score-ring" :style="{ '--p': scorePct + '%' }">
            <em>{{ fmtNum(data.compositeScore, 1) }}</em>
          </div>
          <span>综合分</span>
        </div>
        <div class="hero-main">
          <div class="hero-stance">{{ data.stance }}</div>
          <p class="hero-action">{{ data.actionHint }}</p>
          <p v-if="data.summary" class="hero-summary" :title="data.summary">{{ data.summary }}</p>
          <div v-if="data.scoreExplain?.length" class="explain">
            <span v-for="(x, i) in data.scoreExplain.slice(0, 4)" :key="i" class="explain-chip">{{ x }}</span>
          </div>
        </div>
      </section>

      <section v-if="aiRequested" class="ai-card" :class="aiStanceClass" v-loading="aiLoading">
        <header class="ai-head">
          <h3>AI 实时解读</h3>
          <div class="ai-meta">
            <el-tag v-if="data.ai?.stance" size="small" effect="dark" round>{{ data.ai.stance }}</el-tag>
            <span v-if="data.ai?.model" class="meta-chip">{{ data.ai.model }}</span>
            <span v-if="aiLoading" class="meta-chip pulse">生成中</span>
          </div>
        </header>
        <p v-if="data.ai?.brief" class="ai-brief">{{ data.ai.brief }}</p>
        <p v-else-if="data.ai?.configured === false" class="muted">{{ data.ai.disclaimer }}</p>
        <p v-else-if="aiLoading" class="muted">规则已出，AI 生成中…</p>
        <p v-else class="muted">暂无 AI 正文</p>
        <div v-if="data.ai?.watchPoints?.length" class="watch-row">
          <span v-for="(p, i) in data.ai.watchPoints.slice(0, 4)" :key="'w' + i" class="watch-chip">{{ p }}</span>
        </div>
        <p v-if="data.ai?.riskNote" class="ai-risk">{{ data.ai.riskNote }}</p>
      </section>

      <section class="card stock-news-card">
        <header class="card-head stock-news-head">
          <h3>个股消息面</h3>
          <span class="pill quiet">近7日</span>
        </header>
        <p class="stock-news-summary">{{ data.newsSummary || '近7日未收录直接相关消息' }}</p>
        <div v-if="data.recentNews?.length" class="stock-news-list">
          <a
            v-for="news in data.recentNews.slice(0, 5)"
            :key="`${news.publishedAt}-${news.title}`"
            class="stock-news-item"
            :class="{ 'is-static': !news.url }"
            :href="news.url || undefined"
            :target="news.url ? '_blank' : undefined"
            :rel="news.url ? 'noopener' : undefined"
          >
            <span>{{ newsSourceLabel(news.source) }} {{ fmtNewsTime(news.publishedAt) }}</span>
            <b>{{ news.title }}</b>
            <em>{{ news.matchType }}</em>
          </a>
        </div>
      </section>

      <div class="grid-2 tone-grid">
        <section class="card tone-bull">
          <header class="card-head">
            <h3>多头/机会</h3>
            <span class="count-badge bull">{{ bullTop.length }}</span>
          </header>
          <ul v-if="bullTop.length" class="point-list">
            <li v-for="(p, i) in bullTop" :key="'b' + i">
              <div class="point-main">{{ p.text }}</div>
              <div v-if="p.tip" class="point-tip">{{ p.tip }}</div>
            </li>
          </ul>
          <p v-else class="muted">暂无</p>
        </section>
        <section class="card tone-bear">
          <header class="card-head">
            <h3>空头/风险</h3>
            <span class="count-badge bear">{{ bearTop.length }}</span>
          </header>
          <ul v-if="bearTop.length" class="point-list">
            <li v-for="(p, i) in bearTop" :key="'r' + i">
              <div class="point-main">{{ p.text }}</div>
              <div v-if="p.tip" class="point-tip">{{ p.tip }}</div>
            </li>
          </ul>
          <p v-else class="muted">暂无</p>
        </section>
      </div>

      <div class="grid-2 dim-grid">
        <section class="card dim-tech">
          <header class="card-head stacked">
            <div class="title-row">
              <h3><TermTip term="ma">技术面</TermTip></h3>
              <span class="pill tech">{{ data.tech?.regimeLabel || '结构' }}</span>
            </div>
            <div class="mini-bar" :title="`雷达 ${data.tech?.hitCount ?? 0}/${data.tech?.total ?? 0}`">
              <i :style="{ width: techHitRate + '%' }" />
              <em>{{ data.tech?.hitCount ?? 0 }}/{{ data.tech?.total ?? 0 }}</em>
            </div>
          </header>
          <div class="kpi-row">
            <div class="kpi"><label><TermTip term="rsi">RSI</TermTip></label><b>{{ fmtNum(data.tech?.rsi14, 1) }}</b></div>
            <div class="kpi"><label><TermTip term="atr_pct">ATR%</TermTip></label><b>{{ fmtNum(data.tech?.atrPct, 2) }}</b></div>
            <div class="kpi"><label><TermTip term="volume_ratio">量比</TermTip></label><b>{{ fmtNum(data.tech?.volumeRatio, 2) }}</b></div>
            <div class="kpi"><label><TermTip term="rs20">RS20</TermTip></label><b :class="dirClass(data.tech?.rs20VsHs300)">{{ fmtNum(data.tech?.rs20VsHs300, 2) }}</b></div>
            <div class="kpi"><label><TermTip term="rs60">RS60</TermTip></label><b :class="dirClass(data.tech?.rs60VsHs300)">{{ fmtNum(data.tech?.rs60VsHs300, 2) }}</b></div>
            <div class="kpi"><label>MA</label><b>{{ fmtNum(data.tech?.ma5, 1) }}/{{ fmtNum(data.tech?.ma20, 1) }}</b></div>
          </div>
          <div class="radar">
            <button
              v-for="s in data.tech?.signals || []"
              :key="s.key"
              type="button"
              class="radar-chip"
              :class="{ on: s.hit }"
              :title="s.detail || s.label"
            >
              {{ s.label }}
            </button>
          </div>
        </section>

        <section class="card dim-val">
          <header class="card-head stacked">
            <div class="title-row">
              <h3><TermTip term="pe_ttm">估值</TermTip></h3>
              <span class="pill val">{{ data.valuation?.levelLabel || '-' }}</span>
            </div>
            <div class="mini-bar val" :title="`估值分 ${fmtNum(data.valuation?.score, 1)}`">
              <i :style="{ width: scoreBar(data.valuation?.score) + '%' }" />
              <em>{{ fmtNum(data.valuation?.score, 0) }}</em>
            </div>
          </header>
          <div class="kpi-row">
            <div class="kpi"><label>PE</label><b>{{ fmtNum(data.valuation?.peTtm, 1) }}</b></div>
            <div class="kpi"><label>PB</label><b>{{ fmtNum(data.valuation?.pb, 2) }}</b></div>
            <div class="kpi"><label>PEG</label><b>{{ fmtNum(data.valuation?.peg, 2) }}</b></div>
            <div class="kpi"><label>安全边际</label><b :class="dirClass(data.valuation?.marginOfSafety)">{{ fmtPct(data.valuation?.marginOfSafety) }}</b></div>
            <div class="kpi"><label>行业PE</label><b>{{ fmtNum(data.valuation?.industryPeMedian, 1) }}</b></div>
            <div class="kpi"><label>PE分位</label><b>{{ fmtNum(data.valuation?.pePercentile, 0) }}</b></div>
          </div>
          <div v-if="data.valuation?.dimensions?.length" class="dims">
            <div v-for="d in data.valuation.dimensions.slice(0, 4)" :key="d.key" class="dim-row">
              <span class="dim-name">{{ d.name }}</span>
              <em>{{ fmtNum(d.score, 0) }}</em>
              <div class="dim-track"><i :style="{ width: scoreBar(d.score) + '%' }" /></div>
              <div v-if="d.verdict" class="dim-verdict">当前：{{ d.verdict }}</div>
              <div v-if="d.reference" class="dim-reference">参考：{{ d.reference }}</div>
            </div>
          </div>
        </section>

        <section class="card dim-cap">
          <header class="card-head stacked">
            <div class="title-row">
              <h3>盘面热点</h3>
              <span class="pill" :class="data.capital?.hotHit ? 'hot' : 'quiet'">
                {{ data.capital?.hotHit ? `热点×${data.capital.hotSourceCount}` : '非热点' }}
              </span>
            </div>
          </header>
          <div class="kpi-row">
            <div class="kpi"><label>量比</label><b>{{ fmtNum(data.capital?.volumeRatio, 2) }}</b></div>
            <div class="kpi wide"><label>板块</label><b class="truncate" :title="data.capital?.sectorName">{{ data.capital?.sectorName || '-' }}</b></div>
            <div class="kpi"><label>涨跌</label><b :class="dirClass(data.capital?.sectorPctChg)">{{ fmtPct(data.capital?.sectorPctChg) }}</b></div>
            <div class="kpi"><label>净流入</label><b :class="dirClass(data.capital?.sectorNetInflow)">{{ fmtMoneyYi(data.capital?.sectorNetInflow) }}</b></div>
            <div class="kpi"><label>主力</label><b :class="dirClass(data.capital?.sectorMainNetInflow)">{{ fmtMoneyYi(data.capital?.sectorMainNetInflow) }}</b></div>
            <div class="kpi"><label>热点榜</label><b>{{ data.capital?.hotBestRank ?? '-' }}</b></div>
          </div>
          <div v-if="data.capital?.hotSources?.length" class="hot-sources">
            <span v-for="s in data.capital.hotSources.slice(0, 4)" :key="s" class="tag">{{ s }}</span>
          </div>
        </section>

        <section class="card dim-sig">
          <header class="card-head stacked">
            <div class="title-row">
              <h3><TermTip term="strategy_signal">策略决策</TermTip></h3>
              <span class="pill sig">{{ data.signals?.length || 0 }} 信号</span>
            </div>
            <div class="strat-legend">
              <span v-for="(meta, sid) in STRATEGY_META" :key="sid" class="legend-chip" :title="meta.tip">
                <b>{{ sid }}</b>{{ meta.name }}
              </span>
            </div>
          </header>
          <div v-if="data.decision" class="decision-box">
            <div class="decision-action">
              <span class="pill decision">{{ data.decision.action }}</span>
              <b v-if="data.decision.score != null">{{ fmtNum(data.decision.score, 1) }} 分</b>
            </div>
            <p v-if="data.decision.reason || data.decision.scoreExplain" class="decision-reason">
              {{ data.decision.reason || data.decision.scoreExplain }}
            </p>
          </div>
          <div v-if="signalCards.length" class="sig-list">
            <div v-for="(row, i) in signalCards" :key="i" class="sig-card" :class="{ sell: row.sell }">
              <div class="sig-top">
                <span class="sig-id">{{ row.sid }}</span>
                <span class="sig-title">{{ row.title }}</span>
                <span class="sig-side" :class="row.sell ? 'down' : 'up'">{{ row.sideLabel }}</span>
                <b class="sig-score">{{ fmtNum(row.score, 1) }}</b>
              </div>
              <p v-if="row.tip" class="sig-tip">{{ row.tip }}</p>
              <p v-if="row.reasonText" class="sig-reason">{{ row.reasonText }}</p>
              <div class="sig-foot">
                <em>{{ row.signalDate || '-' }}</em>
              </div>
            </div>
          </div>
          <p v-else-if="!data.decision" class="muted">当日无策略信号，可先跑「信号」或「智能决策」</p>
          <p v-else class="muted">今日有决策，但暂无单票策略信号明细</p>
        </section>
      </div>

      <BrandShareFoot
        v-if="capturingShare"
        :note="`${BRAND.nameZh} · 仅供研究参考 · 不构成投资建议`"
      />
    </div>

    <el-dialog v-model="shareOpen" title="分享个股研判" width="720px" destroy-on-close @closed="closeShare">
      <div v-if="sharePreviewUrl" class="share-preview">
        <img :src="sharePreviewUrl" alt="个股研判分享预览" />
      </div>
      <template #footer>
        <el-button :loading="copying" @click="onCopyShare">复制图片</el-button>
        <el-button type="primary" :loading="downloading" @click="onDownloadShare">下载 PNG</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.analysis {
  padding: 4px 0 16px;
}

.analysis-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
  padding: 8px 10px;
  border: 1px solid rgba(20, 38, 64, 0.1);
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.86);
}

.analysis-mode {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.analysis-mode-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  min-width: 0;
}

.analysis-mode-title {
  color: var(--ink);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.analysis-mode-result {
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.analysis-direction {
  display: flex;
  flex: 0 1 264px;
  width: 264px;
  padding: 2px;
  border: 1px solid rgba(20, 38, 64, 0.12);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.72);
  box-sizing: border-box;
}

.analysis-direction :deep(.el-radio-button) {
  flex: 1 1 0;
}

.analysis-direction :deep(.el-radio-button__inner) {
  display: inline-grid;
  width: 100%;
  min-height: 30px;
  place-items: center;
  padding: 0 10px;
  border: 0 !important;
  border-radius: 5px !important;
  background: transparent;
  box-shadow: none !important;
  color: var(--slate);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0;
}

.analysis-direction :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: #fff;
  box-shadow: 0 1px 3px rgba(20, 38, 64, 0.12) !important;
  color: var(--accent);
}

.analysis-direction :deep(.el-radio-button:first-child .el-radio-button__original-radio:checked + .el-radio-button__inner) {
  color: #c62828;
}

.analysis-direction :deep(.el-radio-button:last-child .el-radio-button__original-radio:checked + .el-radio-button__inner) {
  color: #1b7f37;
}

.analysis-actions {
  display: flex;
  gap: 8px;
  align-items: end;
  justify-content: flex-end;
  min-width: max-content;
  padding: 0;
}

.analysis-actions :deep(.el-button) {
  width: auto;
  min-width: 0;
  height: 32px;
  margin: 0;
  padding: 0 10px;
  border-radius: 5px;
  font-weight: 600;
  letter-spacing: 0;
  white-space: nowrap;
}

.analysis-actions :deep(.analysis-refresh) {
  width: 36px;
  min-width: 36px;
  padding: 0;
  font-size: 16px;
}

.analysis-actions :deep(.analysis-ai) {
  border-color: rgba(0, 113, 227, 0.28);
  background: rgba(0, 113, 227, 0.06);
}

.analysis-actions :deep(.analysis-link) {
  color: var(--ink-soft);
}

.share-card {
  border-radius: 16px;
  padding: 4px 2px 8px;
}

.share-head {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}

.share-head-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
}

.share-head-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.quote-id {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.quote-ind {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(0, 113, 227, 0.1);
  color: #0058b0;
  white-space: nowrap;
}

.quote-core {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  width: 100%;
  padding: 10px;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(0, 113, 227, 0.06), rgba(255, 255, 255, 0.9));
  border: 1px solid rgba(0, 113, 227, 0.1);
}

.qc-item {
  min-width: 0;
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(0, 0, 0, 0.04);
}

.qc-item.price {
  min-width: 0;
}

.qc-item label {
  display: block;
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 3px;
  white-space: nowrap;
}

.qc-item b {
  display: block;
  font-size: 15px;
  font-weight: 750;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.qc-item b.up {
  color: var(--up);
}

.qc-item b.down {
  color: var(--down);
}

.stance-pill {
  flex-shrink: 0;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
  background: #fff3cd;
  color: #9a6700;
}

.stance-pill.good {
  background: rgba(52, 199, 89, 0.16);
  color: #1b7f37;
}

.stance-pill.bad {
  background: rgba(255, 59, 48, 0.14);
  color: #c62828;
}

.stance-pill.mid {
  background: rgba(255, 159, 10, 0.18);
  color: #9a6700;
}

@media (max-width: 720px) {
  .quote-core {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.fresh-note {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--muted);
}

.fresh-note.stale {
  color: #c77700;
}

.today-radar {
  margin-bottom: 12px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #f8fafc;
}

.today-radar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 48px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--line);
  background: #fff;
}

.today-radar-head > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.today-radar-head h2 {
  margin: 0;
  color: var(--ink);
  font-size: 15px;
  line-height: 1.3;
  letter-spacing: 0;
}

.today-radar-head span,
.today-radar-head small {
  color: var(--muted);
  font-size: 11px;
  line-height: 1.4;
}

.today-radar-head small {
  flex: 0 0 auto;
  text-align: right;
}

.today-radar-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.today-radar-item {
  position: relative;
  min-width: 0;
  min-height: 104px;
  padding: 14px 12px 12px;
  border-right: 1px solid var(--line);
}

.today-radar-item:last-child {
  border-right: 0;
}

.today-radar-item::before {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 3px;
  background: #a1a1aa;
  content: '';
}

.today-radar-item[data-tone='active']::before {
  background: #0071e3;
}

.today-radar-item[data-tone='good']::before {
  background: #1d8a45;
}

.today-radar-item[data-tone='hot']::before {
  background: #d92d20;
}

.today-radar-item[data-tone='watch']::before {
  background: #b26a00;
}

.today-radar-item[data-tone='neutral']::before {
  background: #6b7280;
}

.today-radar-item > span {
  display: block;
  margin-bottom: 5px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.3;
}

.today-radar-item > b {
  display: block;
  overflow-wrap: anywhere;
  color: var(--ink);
  font-size: 15px;
  line-height: 1.35;
  letter-spacing: 0;
}

.today-radar-item > p {
  display: -webkit-box;
  margin: 5px 0 0;
  overflow: hidden;
  color: var(--slate);
  font-size: 11px;
  line-height: 1.45;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.hero {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 18px;
  padding: 18px 20px;
  border-radius: 18px;
  margin-bottom: 12px;
  border: 1px solid rgba(0, 0, 0, 0.04);
  box-shadow: var(--shadow-soft);
}

.hero.good {
  background:
    linear-gradient(135deg, rgba(52, 199, 89, 0.18), rgba(255, 255, 255, 0.85) 55%),
    #fff;
}

.hero.bad {
  background:
    linear-gradient(135deg, rgba(255, 59, 48, 0.14), rgba(255, 255, 255, 0.88) 55%),
    #fff;
}

.hero.mid {
  background:
    linear-gradient(135deg, rgba(255, 159, 10, 0.16), rgba(255, 255, 255, 0.88) 55%),
    #fff;
}

.hero-score {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.score-ring {
  --p: 0%;
  width: 88px;
  height: 88px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background:
    radial-gradient(circle at center, #fff 58%, transparent 59%),
    conic-gradient(#0071e3 var(--p), rgba(0, 0, 0, 0.06) 0);
}

.hero.good .score-ring {
  background:
    radial-gradient(circle at center, #fff 58%, transparent 59%),
    conic-gradient(#34c759 var(--p), rgba(0, 0, 0, 0.06) 0);
}

.hero.bad .score-ring {
  background:
    radial-gradient(circle at center, #fff 58%, transparent 59%),
    conic-gradient(#ff3b30 var(--p), rgba(0, 0, 0, 0.06) 0);
}

.hero.mid .score-ring {
  background:
    radial-gradient(circle at center, #fff 58%, transparent 59%),
    conic-gradient(#ff9f0a var(--p), rgba(0, 0, 0, 0.06) 0);
}

.score-ring em {
  font-style: normal;
  font-size: 26px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.03em;
}

.hero-score span {
  font-size: 12px;
  color: var(--slate);
}

.hero-stance {
  font-size: 22px;
  font-weight: 750;
  letter-spacing: -0.02em;
  margin-bottom: 6px;
}

.hero-action {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 650;
  color: var(--ink);
  line-height: 1.45;
}

.hero-summary {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--slate);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.explain {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.explain-chip {
  font-size: 11px;
  padding: 3px 9px;
  border-radius: 999px;
  background: rgba(0, 113, 227, 0.1);
  color: #0058b0;
  white-space: nowrap;
}

.ai-card {
  position: relative;
  padding: 14px 16px 14px 18px;
  border-radius: 16px;
  margin-bottom: 12px;
  border: 1px solid rgba(255, 159, 10, 0.22);
  background: linear-gradient(135deg, rgba(255, 159, 10, 0.12), rgba(255, 255, 255, 0.92));
  box-shadow: var(--shadow-soft);
  overflow: hidden;
}

.ai-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: #ff9f0a;
}

.ai-card.good {
  border-color: rgba(52, 199, 89, 0.25);
  background: linear-gradient(135deg, rgba(52, 199, 89, 0.12), rgba(255, 255, 255, 0.92));
}

.ai-card.good::before {
  background: #34c759;
}

.ai-card.bad {
  border-color: rgba(255, 59, 48, 0.22);
  background: linear-gradient(135deg, rgba(255, 59, 48, 0.1), rgba(255, 255, 255, 0.92));
}

.ai-card.bad::before {
  background: #ff3b30;
}

.ai-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.ai-head h3 {
  margin: 0;
  font-size: 15px;
  white-space: nowrap;
  word-break: keep-all;
}

.ai-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.meta-chip {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.05);
  color: var(--slate);
}

.meta-chip.pulse {
  background: rgba(255, 159, 10, 0.2);
  color: #9a6700;
}

.ai-brief {
  margin: 0 0 10px;
  line-height: 1.6;
  font-size: 14px;
  color: var(--ink);
}

.watch-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.watch-chip {
  max-width: 100%;
  font-size: 12px;
  padding: 5px 10px;
  border-radius: 10px;
  background: rgba(0, 113, 227, 0.08);
  color: #0058b0;
  line-height: 1.35;
}

.ai-risk {
  margin: 0;
  font-size: 12px;
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(255, 159, 10, 0.12);
  color: #9a6700;
}

.stock-news-card {
  margin-bottom: 12px;
  background: linear-gradient(165deg, rgba(0, 113, 227, 0.07), #fff 44%);
}

.stock-news-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.stock-news-summary {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.45;
}

.stock-news-list {
  display: grid;
  gap: 6px;
  margin-top: 10px;
}

.stock-news-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 6px;
  align-items: baseline;
  min-width: 0;
  padding: 8px 9px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.45;
  text-decoration: none;
}

.stock-news-item:not(.is-static):hover b {
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.stock-news-item span,
.stock-news-item em {
  color: var(--muted);
  font-size: 11px;
  font-style: normal;
  white-space: nowrap;
}

.stock-news-item b {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}

.card {
  border-radius: 16px;
  padding: 14px;
  min-width: 0;
  border: 1px solid rgba(0, 0, 0, 0.04);
  background: #fff;
  box-shadow: var(--shadow-soft);
}

.card h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
  word-break: keep-all;
  flex-shrink: 0;
}

.card-head {
  margin-bottom: 10px;
}

.tone-grid .card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-head.stacked {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.tone-bull {
  background: linear-gradient(160deg, rgba(255, 59, 48, 0.1), #fff 48%);
  border-color: rgba(255, 59, 48, 0.16);
}

.tone-bear {
  background: linear-gradient(160deg, rgba(52, 199, 89, 0.12), #fff 48%);
  border-color: rgba(52, 199, 89, 0.18);
}

.dim-tech {
  background: linear-gradient(165deg, rgba(0, 113, 227, 0.1), #fff 42%);
}

.dim-val {
  background: linear-gradient(165deg, rgba(48, 176, 199, 0.12), #fff 42%);
}

.dim-cap {
  background: linear-gradient(165deg, rgba(255, 149, 0, 0.1), #fff 42%);
}

.dim-sig {
  background: linear-gradient(165deg, rgba(50, 173, 230, 0.1), #fff 42%);
}

.count-badge {
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 999px;
  display: inline-grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
}

.count-badge.bull {
  background: rgba(255, 59, 48, 0.14);
  color: #c62828;
}

.count-badge.bear {
  background: rgba(52, 199, 89, 0.18);
  color: #1b7f37;
}

.point-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}

.point-list li {
  position: relative;
  padding: 8px 10px 8px 14px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
  overflow: hidden;
}

.point-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  border-radius: 0;
  background: transparent;
}

.tone-bull .point-list li::before {
  background: #ff3b30;
}

.tone-bear .point-list li::before {
  background: #34c759;
}

.point-main {
  font-size: 13px;
  line-height: 1.45;
  color: var(--ink-soft);
}

.point-tip {
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.4;
  color: var(--muted);
}

.pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 650;
  white-space: nowrap;
  background: rgba(0, 0, 0, 0.05);
  color: var(--slate);
}

.pill.tech {
  background: rgba(0, 113, 227, 0.12);
  color: #0058b0;
}

.pill.val {
  background: rgba(48, 176, 199, 0.16);
  color: #0f766e;
}

.pill.hot {
  background: rgba(255, 59, 48, 0.12);
  color: #c62828;
}

.pill.quiet {
  background: rgba(0, 0, 0, 0.05);
  color: var(--muted);
}

.pill.sig,
.pill.decision {
  background: rgba(50, 173, 230, 0.16);
  color: #0a7ea4;
}

.mini-bar {
  position: relative;
  height: 8px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.mini-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #5ac8fa, #0071e3);
}

.mini-bar.val i {
  background: linear-gradient(90deg, #5ac8fa, #30b0c7);
}

.mini-bar em {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1;
  padding: 0 3px;
  border-radius: 3px;
  font-style: normal;
  font-size: 11px;
  font-weight: 700;
  line-height: 14px;
  color: var(--slate);
  background: rgba(255, 255, 255, 0.88);
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 10px;
}

.kpi {
  padding: 8px 9px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(0, 0, 0, 0.04);
  min-width: 0;
}

.kpi.wide {
  grid-column: span 1;
}

.kpi label {
  display: block;
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 3px;
  white-space: nowrap;
}

.kpi b {
  display: block;
  font-size: 14px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

.kpi b.truncate {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kpi b.up {
  color: var(--up);
}

.kpi b.down {
  color: var(--down);
}

.radar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.radar-chip {
  border: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(255, 255, 255, 0.85);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 11px;
  color: #8e8e93;
  cursor: default;
  white-space: nowrap;
}

.radar-chip.on {
  border-color: rgba(52, 199, 89, 0.35);
  background: rgba(52, 199, 89, 0.16);
  color: #1b7f37;
  font-weight: 650;
}

.dims {
  display: grid;
  gap: 10px;
}

.dim-row {
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-areas:
    'name score'
    'track track'
    'verdict verdict'
    'reference reference';
  gap: 4px 10px;
  align-items: center;
  font-size: 12px;
}

.dim-name {
  grid-area: name;
  color: var(--slate);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

.dim-track {
  grid-area: track;
  height: 8px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.dim-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #64d2ff, #30b0c7);
}

.dim-verdict {
  grid-area: verdict;
  color: #0f766e;
  font-size: 11px;
  font-weight: 650;
  line-height: 1.4;
}

.dim-reference {
  grid-area: reference;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.dim-row em {
  grid-area: score;
  font-style: normal;
  font-weight: 700;
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--ink);
  min-width: 1.5em;
}

.hot-sources {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 8px;
  background: rgba(255, 59, 48, 0.1);
  color: #c62828;
  white-space: nowrap;
}

.decision-box {
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(50, 173, 230, 0.1);
}

.decision-action {
  display: flex;
  align-items: center;
  gap: 8px;
}

.decision-action b {
  font-size: 13px;
}

.decision-reason {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--slate);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.sig-list {
  display: grid;
  gap: 8px;
}

.sig-card {
  padding: 10px 11px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(50, 173, 230, 0.18);
}

.sig-card.sell {
  border-color: rgba(255, 59, 48, 0.2);
  background: rgba(255, 59, 48, 0.06);
}

.sig-top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
}

.sig-id {
  font-weight: 800;
  color: #0a7ea4;
  font-size: 12px;
}

.sig-card.sell .sig-id {
  color: #c62828;
}

.sig-title {
  font-size: 12px;
  font-weight: 650;
  color: var(--ink);
}

.sig-side.up {
  color: var(--up);
  font-weight: 650;
  font-size: 12px;
}

.sig-side.down {
  color: var(--down);
  font-weight: 650;
  font-size: 12px;
}

.sig-score {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
  font-size: 13px;
}

.sig-tip {
  margin: 6px 0 0;
  font-size: 11px;
  line-height: 1.4;
  color: var(--slate);
}

.sig-reason {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--ink-soft);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.sig-foot {
  margin-top: 6px;
  text-align: right;
}

.sig-foot em {
  font-style: normal;
  font-size: 11px;
  color: var(--muted);
}

.strat-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.legend-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(50, 173, 230, 0.1);
  color: #0a7ea4;
  font-size: 11px;
  white-space: nowrap;
  cursor: help;
}

.legend-chip b {
  font-weight: 800;
}

.muted {
  color: var(--muted);
  font-size: 13px;
}

.share-preview {
  max-height: 70vh;
  overflow: auto;
  border-radius: 8px;
  background: #f8fafc;
}

.share-preview img {
  display: block;
  width: 100%;
}

@media (max-width: 900px) {
  .analysis-toolbar {
    flex-wrap: wrap;
  }

  .analysis-mode {
    flex: 1 1 100%;
  }

  .analysis-actions {
    margin-left: auto;
  }

  .grid-2 {
    grid-template-columns: 1fr;
  }

  .hero {
    grid-template-columns: 1fr;
  }

  .kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .analysis-toolbar {
    display: grid;
    grid-template-columns: 1fr;
    gap: 10px;
    padding: 0 0 12px;
    border: 0;
    border-bottom: 1px solid var(--line);
    border-radius: 0;
    background: transparent;
  }

  .analysis-mode {
    display: grid;
    gap: 7px;
  }

  .analysis-mode-head {
    padding: 0 2px;
  }

  .analysis-direction :deep(.el-radio-button__inner) {
    min-height: 44px;
    font-size: 14px;
  }

  .analysis-direction {
    flex: none;
    width: 100%;
  }

  .analysis-actions {
    display: grid;
    grid-template-columns: 44px minmax(78px, 1.1fr) minmax(76px, 1fr) minmax(58px, 0.72fr);
    gap: 6px;
    justify-self: stretch;
    min-width: 0;
    padding: 0;
    border: 0;
    border-radius: 0;
    background: transparent;
  }

  .analysis-actions :deep(.el-button) {
    width: 100%;
    min-width: 0;
    min-height: 44px;
    padding: 0 8px;
    font-size: 13px;
  }

  .analysis-actions :deep(.analysis-refresh) {
    width: 100%;
    min-width: 0;
    padding: 0;
  }

  .today-radar-head {
    align-items: flex-start;
  }

  .today-radar-head > div {
    flex-direction: column;
    gap: 1px;
  }

  .today-radar-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .today-radar-item {
    min-height: 108px;
  }

  .today-radar-item:nth-child(2n) {
    border-right: 0;
  }

  .today-radar-item:nth-child(n + 3) {
    border-top: 1px solid var(--line);
  }
}

@media (max-width: 360px) {
  .analysis-actions :deep(.analysis-link .el-icon) {
    display: none;
  }
}

/* 分享图使用独立画布密度，不继承触发分享时的手机断点布局。 */
.share-card.is-share-capture {
  width: 720px;
  min-width: 720px;
  max-width: none;
  color: #172033;
}

.share-card.is-share-capture .share-head {
  gap: 12px;
  margin-bottom: 10px;
  padding-bottom: 14px;
  border-bottom: 1px solid #e6ebf1;
}

.share-card.is-share-capture .share-head-top {
  flex-wrap: nowrap;
  align-items: center;
}

.share-card.is-share-capture .share-head-main {
  flex-direction: row;
  align-items: center;
  gap: 18px;
}

.share-card.is-share-capture .quote-id {
  flex-wrap: nowrap;
  gap: 7px;
}

.share-card.is-share-capture .quote-core {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0;
  padding: 0;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f7f9fc;
  overflow: hidden;
}

.share-card.is-share-capture .qc-item {
  padding: 9px 12px;
  border: 0;
  border-right: 1px solid #e6ebf1;
  border-radius: 0;
  background: transparent;
}

.share-card.is-share-capture .qc-item:last-child {
  border-right: 0;
}

.share-card.is-share-capture .fresh-note {
  margin-bottom: 10px;
  font-size: 11px;
}

.share-card.is-share-capture .today-radar-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.share-card.is-share-capture .today-radar-item {
  min-height: 92px;
  padding: 12px 10px 10px;
  border-top: 0;
  border-right: 1px solid #e6ebf1;
}

.share-card.is-share-capture .today-radar-item:last-child {
  border-right: 0;
}

.share-card.is-share-capture .hero {
  grid-template-columns: 104px minmax(0, 1fr);
  gap: 16px;
  margin-bottom: 10px;
  padding: 15px 18px;
  border-radius: 8px;
  box-shadow: none;
}

.share-card.is-share-capture .score-ring {
  width: 76px;
  height: 76px;
}

.share-card.is-share-capture .score-ring em {
  font-size: 23px;
  letter-spacing: 0;
}

.share-card.is-share-capture .hero-stance {
  margin-bottom: 4px;
  font-size: 20px;
  letter-spacing: 0;
}

.share-card.is-share-capture .hero-action {
  margin-bottom: 5px;
}

.share-card.is-share-capture .hero-summary {
  margin-bottom: 7px;
  -webkit-line-clamp: 1;
}

.share-card.is-share-capture .ai-card {
  margin-bottom: 10px;
  padding: 12px 14px 12px 17px;
  border-radius: 8px;
  box-shadow: none;
}

.share-card.is-share-capture .ai-head {
  margin-bottom: 6px;
}

.share-card.is-share-capture .ai-brief {
  margin-bottom: 7px;
  font-size: 13px;
  line-height: 1.5;
}

.share-card.is-share-capture .watch-row {
  margin-bottom: 6px;
}

.share-card.is-share-capture .watch-chip {
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
}

.share-card.is-share-capture .ai-risk {
  padding: 6px 8px;
  border-radius: 6px;
  font-size: 11px;
}

.share-card.is-share-capture .grid-2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 10px;
  align-items: start;
}

.share-card.is-share-capture .card {
  padding: 12px;
  border-color: #e3e8ef;
  border-radius: 8px;
  box-shadow: none;
}

.share-card.is-share-capture .stock-news-card {
  margin-bottom: 10px;
}

.share-card.is-share-capture .stock-news-list {
  gap: 4px;
  margin-top: 7px;
}

.share-card.is-share-capture .stock-news-item {
  padding: 6px 8px;
  border-radius: 5px;
}

.share-card.is-share-capture .stock-news-item:nth-child(n + 4) {
  display: none;
}

.share-card.is-share-capture .card-head {
  margin-bottom: 8px;
}

.share-card.is-share-capture .card-head.stacked {
  gap: 6px;
}

.share-card.is-share-capture .point-list {
  gap: 5px;
}

.share-card.is-share-capture .point-list li {
  padding: 6px 8px 6px 11px;
  border-radius: 5px;
}

.share-card.is-share-capture .point-main {
  font-size: 12px;
  line-height: 1.4;
}

.share-card.is-share-capture .point-tip {
  margin-top: 2px;
  font-size: 10px;
  line-height: 1.35;
}

.share-card.is-share-capture .kpi-row {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  margin-bottom: 8px;
}

.share-card.is-share-capture .kpi {
  padding: 6px 7px;
  border-radius: 5px;
}

.share-card.is-share-capture .radar,
.share-card.is-share-capture .hot-sources,
.share-card.is-share-capture .strat-legend {
  gap: 4px;
}

.share-card.is-share-capture .radar-chip,
.share-card.is-share-capture .tag,
.share-card.is-share-capture .legend-chip {
  padding: 3px 7px;
  border-radius: 5px;
  font-size: 10px;
}

.share-card.is-share-capture .dims {
  gap: 6px;
}

.share-card.is-share-capture .dim-track,
.share-card.is-share-capture .mini-bar {
  height: 6px;
}

.share-card.is-share-capture .decision-box {
  margin-bottom: 7px;
  padding: 7px 9px;
  border-radius: 6px;
}

.share-card.is-share-capture .sig-list {
  gap: 5px;
}

.share-card.is-share-capture .sig-card {
  padding: 7px 8px;
  border-radius: 6px;
}

.share-card.is-share-capture .sig-tip {
  display: none;
}

.share-card.is-share-capture .sig-reason {
  margin-top: 3px;
  font-size: 11px;
  line-height: 1.35;
  -webkit-line-clamp: 2;
}

.share-card.is-share-capture .sig-foot {
  margin-top: 3px;
}
</style>
