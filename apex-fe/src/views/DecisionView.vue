<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import {
  fetchDecisionAttribution,
  fetchDecisionAdvice,
  fetchDecisionBuyAiSummary,
  fetchDecisionHistory,
  fetchDecisionPlaybook,
  fetchDecisionToday,
} from '../api/decision'
import { getAccount, orderFromDecision, placeOrder } from '../api/paper'
import {
  buildDecisionShareSheet,
  DECISION_SHARE_WIDTH,
  mountDecisionShareSheet,
} from '../utils/decisionShareSheet.js'
import {
  captureElementBlob,
  copyImageBlob,
  downloadBlob,
  shareFilename,
} from '../utils/shareCapture.js'
import { preloadBrandAssets } from '../brand/identity.js'
import { normalizeHotThemes } from '../utils/hotTheme.js'
import { snapshotStamp } from '../utils/snapshotDate.js'
import {
  buyActionState as getBuyActionState,
  canPaperBuy as isPaperBuyAllowed,
  chinaMarketDate,
  isCurrentLiveDecision,
  paperBuyBlockedReason as getPaperBuyBlockedReason,
} from '../utils/decisionActionability.js'
import FloatingShareButton from '../components/FloatingShareButton.vue'
import DecisionWorkspaceTabs from '../components/DecisionWorkspaceTabs.vue'
import { useSessionViewState } from '../utils/viewState.js'
import { publishDataFreshness } from '../utils/dataFreshness.js'

const router = useRouter()
const loading = ref(false)
const ordering = ref(false)
const DEFAULT_GROUP = '我的自选'
const data = ref(null)
const activeTab = ref('buys')
const history = ref([])
const playbook = ref(null)
const attribution = ref(null)
const decisionAdvice = ref(null)
const adviceLoading = ref(false)
const morePanels = ref([])
const FILTER_PREF_KEY = 'apex.decision.buyFilters'
const savedFilters = (() => {
  try {
    return JSON.parse(localStorage.getItem(FILTER_PREF_KEY) || '{}')
  } catch {
    return {}
  }
})()
const buyStrategyFilter = ref(savedFilters.strategy || '')
const buyMinScore = ref(savedFilters.minScore ?? '')
const buyMainlineOnly = ref(!!savedFilters.mainlineOnly)
const buyExecutableOnly = ref(!!savedFilters.executableOnly)
const buyCheapOnly = ref(!!savedFilters.cheapOnly)
/** 默认不含北交所（京市）；开启后在决策清单中纳入 */
const includeBj = ref(savedFilters.includeBj === true)

useSessionViewState('decision', { activeTab })

/** 表格长文案悬停：加宽、可换行，避免 show-overflow-tooltip 截断 */
const longTextTooltip = {
  effect: 'dark',
  placement: 'top',
  popperClass: 'decision-long-tooltip',
  showAfter: 200,
}

function persistBuyFilters() {
  try {
    localStorage.setItem(
      FILTER_PREF_KEY,
      JSON.stringify({
        strategy: buyStrategyFilter.value,
        minScore: buyMinScore.value,
        mainlineOnly: buyMainlineOnly.value,
        executableOnly: buyExecutableOnly.value,
        cheapOnly: buyCheapOnly.value,
        includeBj: includeBj.value,
      }),
    )
  } catch {
    /* ignore */
  }
}

/** 北交所代码：92/83/87/4 开头（与后端 MarketCodeUtils 一致） */
function isBjCode(code) {
  const digits = String(code || '')
    .replace(/\D/g, '')
    .slice(-6)
  if (digits.length < 6) return false
  return (
    digits.startsWith('92') ||
    digits.startsWith('83') ||
    digits.startsWith('87') ||
    digits.startsWith('4')
  )
}

const buys = computed(() => data.value?.buys || [])
const sells = computed(() => data.value?.sells || [])
const holds = computed(() => data.value?.holds || [])
const filteredBuys = computed(() => {
  const min = buyMinScore.value !== '' ? Number(buyMinScore.value) : null
  return buys.value.filter((row) => {
    if (!includeBj.value && isBjCode(row.code)) return false
    if (buyStrategyFilter.value && row.strategyId !== buyStrategyFilter.value) return false
    if (buyMainlineOnly.value && !row.mainlineMatch) return false
    if (buyExecutableOnly.value && row.executableHint !== true) return false
    if (
      buyCheapOnly.value &&
      row.valuationLevel !== 'UNDERVALUED' &&
      row.valuationLevel !== 'SLIGHTLY_CHEAP'
    ) {
      return false
    }
    if (min != null && !Number.isNaN(min) && Number(row.score || 0) < min) return false
    return true
  })
})
const briefing = computed(() => data.value?.marketBriefing || null)
const decisionDataLevel = computed(() => briefing.value?.dataLevel || 'RED')
const factors = computed(() => briefing.value?.factors || [])
const tips = computed(() => briefing.value?.tips || [])
const hotThemes = computed(() => normalizeHotThemes(briefing.value))
const strategies = computed(() => playbook.value?.strategies || [])
const buyAi = ref(null)
const buyAiLoading = ref(false)
const sharing = ref(false)
const shareOpen = ref(false)
const sharePreviewUrl = ref('')
const copying = ref(false)
const downloading = ref(false)
let sharePreviewObjectUrl = ''
const scorePct = computed(() => {
  const s = Number(briefing.value?.stanceScore)
  if (Number.isNaN(s)) return 0
  return Math.max(0, Math.min(100, s))
})

function priorityType(p) {
  if (p === '高') return 'danger'
  if (p === '中') return 'warning'
  if (p === '观望') return 'info'
  return ''
}

function stanceClass(s) {
  if (s === '进攻') return 'stance-attack'
  if (s === '防守') return 'stance-defend'
  return 'stance-balance'
}

function signalClass(s) {
  if (s === '偏多') return 'up'
  if (s === '偏空') return 'down'
  return ''
}

function tipType(level) {
  if (level === 'danger') return 'error'
  if (level === 'warn') return 'warning'
  return 'info'
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

function decisionDataStatus() {
  if (!isCurrentLiveDecision(decisionExecutionContext())) {
    return '历史回放或未发布结果仅供复盘，不能按该清单执行'
  }
  if (decisionDataLevel.value === 'GREEN') return '数据完整，可按行动计划执行'
  if (decisionDataLevel.value === 'RED') return '数据异常，买入建议仅供观察'
  return '数据存在预警，执行前请复核行情和风险边界'
}

function decisionDataTime() {
  const value = data.value?.asOfTime || data.value?.dataAsOf
  if (!value) return '截至时间未提供'
  return `数据截至 ${String(value).replace('T', ' ').slice(0, 16)}`
}

function publishDecisionDataFreshness() {
  const level = decisionDataLevel.value
  publishDataFreshness({
    level,
    label: `决策数据${dataLevelLabel(level)}`,
    detail: `${decisionDataTime()} · ${decisionDataStatus()}`,
    route: '/decision',
  })
}

function decisionExecutionContext() {
  return {
    dataLevel: decisionDataLevel.value,
    generated: data.value?.generated,
    runMode: data.value?.runMode,
    actionDate: data.value?.actionDate,
    currentDate: chinaMarketDate(),
  }
}

function canExecutePaperBuy(row) {
  return isPaperBuyAllowed(row, decisionExecutionContext())
}

function buyActionState(row) {
  return getBuyActionState(row, decisionExecutionContext())
}

function paperBuyBlockedReason(row) {
  return getPaperBuyBlockedReason(row, decisionExecutionContext())
}

function openTheme(theme) {
  if (!theme?.name) return
  const query = { type: theme.boardType || 'CONCEPT' }
  if (theme.code) query.code = theme.code
  else query.q = theme.name
  router.push({ path: '/sector', query })
}

async function loadHistory() {
  try {
    const res = await fetchDecisionHistory(12)
    history.value = res.data || []
  } catch {
    history.value = []
  }
}

async function loadPlaybook() {
  try {
    const res = await fetchDecisionPlaybook()
    playbook.value = res.data
  } catch {
    playbook.value = null
  }
}

async function loadAttribution() {
  try {
    const res = await fetchDecisionAttribution(20)
    attribution.value = res.data
  } catch {
    attribution.value = null
  }
}

async function loadDecisionAdvice(date) {
  adviceLoading.value = true
  try {
    const res = await fetchDecisionAdvice(date)
    decisionAdvice.value = res.data
  } catch {
    decisionAdvice.value = null
  } finally {
    adviceLoading.value = false
  }
}

function strategyName(id) {
  if (id === 'RISK') return 'RISK 止损止盈'
  const s = strategies.value.find((x) => x.strategyId === id)
  return s ? `${id} ${s.name}` : id || '-'
}

async function load() {
  loading.value = true
  try {
    const [res] = await Promise.all([
      fetchDecisionToday(undefined, DEFAULT_GROUP),
      loadPlaybook(),
    ])
    data.value = res.data
    publishDecisionDataFreshness()
    pickDefaultTab()
    await Promise.all([loadHistory(), loadAttribution()])
    loadDecisionAdvice(data.value?.actionDate)
    loadBuyAi(false)
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function openHistoryDay(row) {
  if (!row?.actionDate) return
  loading.value = true
  try {
    const res = await fetchDecisionToday(row.actionDate, DEFAULT_GROUP)
    data.value = res.data
    publishDecisionDataFreshness()
    pickDefaultTab()
    loadDecisionAdvice(data.value?.actionDate)
    loadBuyAi(false)
    ElMessage.success(`已切换到决策日 ${row.actionDate}`)
  } catch (e) {
    ElMessage.error(e.message || '加载历史决策失败')
  } finally {
    loading.value = false
  }
}

function pickDefaultTab() {
  if (buys.value.length) activeTab.value = 'buys'
  else if (sells.value.length) activeTab.value = 'sells'
  else activeTab.value = 'holds'
}

function fmtPct(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  if (Math.abs(n) <= 1) return (n * 100).toFixed(1) + '%'
  return n.toFixed(1) + '%'
}

function fmtScore(v) {
  if (v == null) return '-'
  return Number(v).toFixed(1)
}

function fmtMoney(v) {
  if (v == null || !Number.isFinite(Number(v))) return '-'
  return Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function fmtPrice(v) {
  if (v == null || !Number.isFinite(Number(v))) return '-'
  return Number(v).toFixed(2)
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

function adviceActionLabel(action) {
  return {
    BUY: '买入',
    ADD: '加仓',
    REDUCE: '减仓',
    SELL: '卖出',
    HOLD: '持有',
    WATCH: '观察',
  }[action] || action || '-'
}

function adviceActionType(action) {
  if (action === 'BUY' || action === 'ADD') return 'danger'
  if (action === 'SELL' || action === 'REDUCE') return 'success'
  if (action === 'WATCH') return 'warning'
  return 'info'
}

async function loadBuyAi(force = false) {
  if (!buys.value.length) {
    buyAi.value = null
    return
  }
  buyAiLoading.value = true
  try {
    const res = await fetchDecisionBuyAiSummary(
      data.value?.actionDate,
      DEFAULT_GROUP,
      force,
    )
    buyAi.value = res.data
  } catch (e) {
    buyAi.value = {
      configured: false,
      summary: e.message || 'AI 总结加载失败',
      watchPoints: [],
      stockNotes: [],
    }
  } finally {
    buyAiLoading.value = false
  }
}

function revokeSharePreview() {
  if (sharePreviewObjectUrl) {
    URL.revokeObjectURL(sharePreviewObjectUrl)
    sharePreviewObjectUrl = ''
  }
  sharePreviewUrl.value = ''
}

async function captureDecisionShare() {
  const titleDate = snapshotStamp(data.value, 'actionDate')
  if (!titleDate) throw new Error('决策日期缺失，请刷新后再分享')
  await preloadBrandAssets(['markShare'])
  const sheet = buildDecisionShareSheet({
    titleDate,
    groupName: data.value?.groupName || DEFAULT_GROUP,
    stance: briefing.value?.stance || '均衡',
    stanceScore: briefing.value?.stanceScore,
    stanceReason: briefing.value?.stanceReason || '',
    positionAdvice: briefing.value?.positionAdvice || data.value?.riskNote || '',
    hotThemes: hotThemes.value || [],
    buyCount: data.value?.buyCount ?? buys.value.length,
    sellCount: data.value?.sellCount ?? sells.value.length,
    holdCount: data.value?.holdCount ?? holds.value.length,
    executableCount: data.value?.executableCount ?? 0,
    aiSummary: buyAi.value?.summary || '',
    aiStance: buyAi.value?.stance || '',
    aiModel: buyAi.value?.model || '',
    buys: filteredBuys.value,
    sells: sells.value,
  })
  const mounted = mountDecisionShareSheet(sheet)
  try {
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))
    const width = DECISION_SHARE_WIDTH
    const height = Math.max(sheet.scrollHeight, sheet.offsetHeight, 1)
    sheet.style.width = `${width}px`
    sheet.style.height = `${height}px`
    return await captureElementBlob(sheet, {
      scale: 2,
      width,
      height,
      backgroundColor: '#f7f9fc',
    })
  } finally {
    mounted.dispose()
  }
}

async function openShare() {
  if (!buys.value.length && !sells.value.length && !holds.value.length) {
    ElMessage.warning('暂无决策清单，请先启动后台生成')
    return
  }
  sharing.value = true
  try {
    const blob = await captureDecisionShare()
    revokeSharePreview()
    sharePreviewObjectUrl = URL.createObjectURL(blob)
    sharePreviewUrl.value = sharePreviewObjectUrl
    shareOpen.value = true
  } catch (e) {
    console.error('生成智能决策分享图失败', e)
    ElMessage.error(e.message || '生成分享图失败')
  } finally {
    sharing.value = false
  }
}

async function onCopyShare() {
  copying.value = true
  try {
    await copyImageBlob(captureDecisionShare())
    ElMessage.success('已复制到剪贴板，可直接粘贴到微信/文档')
  } catch (e) {
    console.error('复制智能决策分享图失败', e)
    ElMessage.error(e.message || '复制失败，请改用下载')
  } finally {
    copying.value = false
  }
}

async function onDownloadShare() {
  downloading.value = true
  try {
    const blob = await captureDecisionShare()
    const stamp = snapshotStamp(data.value, 'actionDate') || 'date-unknown'
    downloadBlob(blob, shareFilename('apex_decision', stamp))
    ElMessage.success('已下载分享图')
  } catch (e) {
    console.error('下载智能决策分享图失败', e)
    ElMessage.error(e.message || '下载失败')
  } finally {
    downloading.value = false
  }
}

function closeShare() {
  shareOpen.value = false
  revokeSharePreview()
}

async function onPaperOrder(row) {
  if (!row || row.action === 'HOLD') return
  const buy = row.action === 'BUY'
  if (buy && !canExecutePaperBuy(row)) {
    ElMessage.warning(paperBuyBlockedReason(row))
    return
  }
  try {
    const { value } = await ElMessageBox.prompt(
      buy
        ? '目标仓位比例(如 0.1=10%)；留空则按决策建议仓位一键下单'
        : '卖出数量(股)；留空则按信号全平',
      `${row.code} ${row.action}`,
      {
        inputValue: buy ? String(row.suggestedWeight ?? 0.1) : '',
        confirmButtonText: '模拟下单',
      },
    )
    ordering.value = true
    const acc = await getAccount()
    const text = String(value ?? '').trim()
    if (buy) {
      const targetWeight = Number(text)
      if (text && !(targetWeight > 0 && targetWeight < 1)) {
        ElMessage.warning('目标仓位须大于 0 且小于 1')
        return
      }
      await orderFromDecision(
        row.id,
        acc.data.id,
        targetWeight > 0 && targetWeight < 1 ? targetWeight : undefined,
      )
      ElMessage.success('已按决策一键模拟成交')
      router.push('/paper')
      return
    }
    const num = Number(text)
    const payload = {
      accountId: acc.data.id,
      code: row.code,
      side: buy ? 'BUY' : 'SELL',
    }
    if (buy && num > 0 && num < 1) {
      payload.targetWeight = num
    } else if (!text && buy && row.suggestedWeight) {
      payload.targetWeight = Number(row.suggestedWeight)
    } else {
      payload.quantity = num
    }
    await placeOrder(payload)
    ElMessage.success('已模拟成交')
    router.push('/paper')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '下单失败')
  } finally {
    ordering.value = false
  }
}

onMounted(load)

onBeforeUnmount(() => {
  revokeSharePreview()
})
</script>

<template>
  <div class="page decision" v-loading="loading">
    <DecisionWorkspaceTabs />
    <header class="header dec-header">
      <div class="dec-heading">
        <p class="eyebrow">Decision</p>
        <h1>智能决策</h1>
        <p class="sub">
          {{ data?.message || '先看市场立场，再按评分出买卖单' }}
        </p>
      </div>
      <div class="dec-controls">
        <label class="market-scope" title="默认不含北交所；开启后在决策清单中纳入京市">
          <span>
            <em>股票范围</em>
            <b>京市</b>
          </span>
          <el-switch
            v-model="includeBj"
            size="small"
            aria-label="纳入京市"
            @change="persistBuyFilters"
          />
        </label>
        <el-button class="sync-link" link type="primary" @click="router.push('/sync')">
          同步中心
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
    </header>

    <FloatingShareButton
      v-if="!shareOpen"
      :loading="sharing"
      :disabled="!buys.length && !sells.length && !holds.length"
      @click="openShare"
    />

    <details class="decision-evidence-toggle">
      <summary>
        <span>市场依据与数据状态</span>
        <small>{{ briefing?.asOf ? `截至 ${briefing.asOf}` : '数据加载中' }}</small>
      </summary>

      <!-- ① 市场立场 -->
      <section
        v-if="briefing"
        class="stance-panel"
        :class="stanceClass(briefing.stance)"
      >
        <div class="stance-main">
          <div class="kicker">
            <span>市场简报 · {{ briefing.asOf || '-' }}</span>
            <span class="pill">{{ briefing.stance || '均衡' }}</span>
            <el-tag
              v-if="briefing.dataLevel"
              size="small"
              effect="plain"
              :type="dataLevelType(briefing.dataLevel)"
              round
            >
              数据{{ dataLevelLabel(briefing.dataLevel) }}
            </el-tag>
          </div>
          <div class="stance-title-row">
            <div class="score-ring" :style="{ '--pct': scorePct }">
              <div class="score-ring-inner">
                <strong>{{ briefing.stanceScore ?? '-' }}</strong>
                <small>/100</small>
              </div>
            </div>
            <div class="stance-copy">
              <p class="reason">{{ briefing.stanceReason }}</p>
              <p class="advice">{{ briefing.positionAdvice || data?.riskNote }}</p>
            </div>
          </div>
          <div v-if="hotThemes.length" class="theme-row theme-inline">
            <span class="side-title inline"><TermTip term="mainline">主线</TermTip></span>
            <div class="theme-chip-grid">
              <button
                v-for="t in hotThemes.slice(0, 6)"
                :key="t.key"
                type="button"
                class="theme-chip"
                :aria-label="`查看${t.name}成分股`"
                @click="openTheme(t)"
              >
                <span class="theme-name">{{ t.name }}</span>
                <span v-if="t.pctText" class="theme-pct" :class="t.pctDir">{{ t.pctText }}</span>
                <el-icon class="theme-link-icon"><ArrowRight /></el-icon>
              </button>
            </div>
          </div>
        </div>
      </section>

      <div v-if="factors.length" class="factor-strip">
        <div v-for="f in factors" :key="f.name" class="factor-cell">
          <label>{{ f.name }}</label>
          <div class="factor-val">
            <b :class="signalClass(f.signal)">{{ f.value }}</b>
            <em :class="signalClass(f.signal)">{{ f.signal }}</em>
          </div>
          <p>{{ f.note }}</p>
        </div>
      </div>

      <div v-if="tips.length" class="tips-row">
        <el-alert
          v-for="(tip, idx) in tips.slice(0, 3)"
          :key="idx"
          class="tip-item"
          :type="tipType(tip.level)"
          :closable="false"
          show-icon
          :title="tip.text"
        />
      </div>
    </details>

    <section v-if="decisionAdvice || adviceLoading" class="advice-panel" v-loading="adviceLoading">
      <div v-if="decisionAdvice" class="advice-head">
        <div>
          <div class="kicker">最终决策 · {{ decisionAdvice.actionDate }}</div>
          <h2>{{ decisionAdvice.executionDate }} {{ decisionAdvice.executionTiming }}</h2>
          <p>{{ decisionAdvice.summary }}</p>
        </div>
        <div class="advice-metrics">
          <span><label>当前仓位</label><b>{{ fmtPct(decisionAdvice.currentExposure) }}</b></span>
          <span><label>目标仓位</label><b>{{ fmtPct(decisionAdvice.targetExposure) }}</b></span>
          <span><label>回撤</label><b>{{ fmtPct(decisionAdvice.drawdown) }}</b></span>
          <span><label>现金</label><b>{{ fmtMoney(decisionAdvice.cash) }}</b></span>
        </div>
      </div>
      <div v-if="decisionAdvice?.actions?.length" class="advice-table-wrap">
        <el-table
          :key="`decision-advice-compact-v2-${decisionAdvice.actionDate}`"
          :data="decisionAdvice.actions"
          size="small"
          class="advice-table"
        >
          <el-table-column prop="priority" label="#" width="44" align="center" />
          <el-table-column label="标的" width="132">
            <template #default="{ row }">
              <StockIdentity :security="row" interactive compact @select="router.push(`/stock/${row.code}`)" />
            </template>
          </el-table-column>
          <el-table-column label="动作" width="76" align="center">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="adviceActionType(row.action)">
                {{ adviceActionLabel(row.action) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="当前 / 目标" min-width="118" align="center">
            <template #default="{ row }">{{ fmtPct(row.currentWeight) }} / {{ fmtPct(row.targetWeight) }}</template>
          </el-table-column>
          <el-table-column label="数量" width="88" align="right">
            <template #default="{ row }">{{ row.quantity ? `${row.quantity} 股` : '-' }}</template>
          </el-table-column>
          <el-table-column label="参考 / 止损 / 止盈" min-width="168" align="center">
            <template #default="{ row }">
              {{ fmtPrice(row.referencePrice) }} / {{ fmtPrice(row.stopLossPrice) }} /
              {{ fmtPrice(row.takeProfitPrice) }}
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="理由" min-width="260" show-overflow-tooltip />
        </el-table>
        <div class="advice-mobile-list" role="list">
          <article
            v-for="row in decisionAdvice.actions"
            :key="`${row.priority}-${row.code}-${row.action}`"
            class="advice-mobile-item"
            role="listitem"
          >
            <div class="advice-mobile-main">
              <span class="advice-priority">{{ row.priority }}</span>
              <div class="advice-mobile-stock">
                <StockIdentity :security="row" interactive compact @select="router.push(`/stock/${row.code}`)" />
              </div>
              <el-tag size="small" effect="plain" :type="adviceActionType(row.action)">
                {{ adviceActionLabel(row.action) }}
              </el-tag>
            </div>
            <div class="advice-mobile-meta">
              <span>仓位 {{ fmtPct(row.currentWeight) }} → {{ fmtPct(row.targetWeight) }}</span>
              <span v-if="row.quantity">{{ row.quantity }} 股</span>
              <span v-if="row.referencePrice">参考 {{ fmtPrice(row.referencePrice) }}</span>
            </div>
            <p v-if="row.reason" class="advice-mobile-reason">{{ row.reason }}</p>
          </article>
        </div>
      </div>
      <div v-if="decisionAdvice?.reviewSchedule?.length" class="review-row">
        <span v-for="item in decisionAdvice.reviewSchedule" :key="item">{{ item }}</span>
      </div>
    </section>

    <!-- ② 今日清单 -->
    <section class="action-panel">
      <div class="action-head">
        <div>
          <h2>今日清单</h2>
          <p class="muted">
            {{ data?.actionDate || '-' }}
            <template v-if="data?.universeCount != null"> · 池 {{ data.universeCount }}</template>
          </p>
        </div>
        <div class="metric-row">
          <div class="metric">
            <label>买入</label>
            <b class="up">{{ data?.buyCount ?? buys.length }}</b>
          </div>
          <div class="metric">
            <label>卖出</label>
            <b class="down">{{ data?.sellCount ?? sells.length }}</b>
          </div>
          <div class="metric">
            <label>持有</label>
            <b>{{ data?.holdCount ?? holds.length }}</b>
          </div>
          <div class="metric">
            <label>可执行</label>
            <b>{{ data?.executableCount ?? 0 }}</b>
          </div>
          <div class="metric">
            <label>低估</label>
            <b class="up">{{ data?.valuationCheapCount ?? 0 }}</b>
          </div>
          <div class="metric">
            <label>高估</label>
            <b class="down">{{ data?.valuationRichCount ?? 0 }}</b>
          </div>
          <div class="metric">
            <label>主线</label>
            <b>{{ data?.mainlineMatchCount ?? 0 }}</b>
          </div>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="tabs">
        <el-tab-pane :label="`建议买入 (${buys.length})`" name="buys">
          <div v-if="buys.length" class="buy-ai-panel" v-loading="buyAiLoading">
            <div class="buy-ai-head">
              <div>
                <h3>AI 详细总结</h3>
                <p class="muted">
                  <template v-if="buyAi?.stance">立场 {{ buyAi.stance }} · </template>
                  <template v-if="buyAi?.fromCache">缓存 · </template>
                  <template v-if="buyAi?.model">{{ buyAi.model }} · </template>
                  基于当日买入清单与市场立场
                </p>
              </div>
              <el-button size="small" type="warning" plain :loading="buyAiLoading" @click="loadBuyAi(true)">
                重新生成
              </el-button>
            </div>
            <p v-if="buyAi?.summary" class="buy-ai-summary">{{ buyAi.summary }}</p>
            <p v-else-if="buyAiLoading" class="muted">AI 总结生成中…</p>
            <p v-else class="muted">暂无 AI 正文</p>
            <div v-if="buyAi?.watchPoints?.length" class="buy-ai-watch">
              <span v-for="(p, i) in buyAi.watchPoints.slice(0, 5)" :key="'wp' + i" class="watch-chip">{{ p }}</span>
            </div>
            <div v-if="buyAi?.stockNotes?.length" class="buy-ai-notes">
              <div v-for="n in buyAi.stockNotes" :key="n.code" class="note-row">
                <StockIdentity :security="n" interactive compact @select="router.push(`/stock/${n.code}`)" />
                <el-tag
                  v-if="n.priority"
                  class="note-priority"
                  size="small"
                  effect="plain"
                  :type="priorityType(n.priority)"
                >
                  {{ n.priority }}
                </el-tag>
                <span class="note-text">{{ n.note || '-' }}</span>
              </div>
            </div>
            <p v-if="buyAi?.riskNote" class="buy-ai-risk">风险：{{ buyAi.riskNote }}</p>
            <p v-if="buyAi?.disclaimer" class="buy-ai-disc">{{ buyAi.disclaimer }}</p>
          </div>
          <div v-if="buys.length" class="toolbar-bar">
            <el-select
              v-model="buyStrategyFilter"
              clearable
              placeholder="策略"
              style="width: 110px"
              @change="persistBuyFilters"
            >
              <el-option label="S1" value="S1" />
              <el-option label="S2" value="S2" />
              <el-option label="S3" value="S3" />
            </el-select>
            <el-input
              v-model="buyMinScore"
              clearable
              placeholder="最低分"
              style="width: 100px"
              @change="persistBuyFilters"
            />
            <el-checkbox v-model="buyMainlineOnly" @change="persistBuyFilters">仅主线</el-checkbox>
            <el-checkbox v-model="buyExecutableOnly" @change="persistBuyFilters">仅可执行</el-checkbox>
            <el-checkbox v-model="buyCheapOnly" @change="persistBuyFilters">仅低估</el-checkbox>
            <el-checkbox v-model="includeBj" @change="persistBuyFilters">含京市</el-checkbox>
            <span class="muted">显示 {{ filteredBuys.length }} / {{ buys.length }}</span>
          </div>
          <div v-if="buys.length" class="decision-data-status" :class="`is-${decisionDataLevel.toLowerCase()}`">
            <span><b>市场数据 {{ dataLevelLabel(decisionDataLevel) }}</b> · {{ decisionDataStatus() }}</span>
            <span>{{ decisionDataTime() }}</span>
          </div>
          <div v-if="!buys.length" class="page-empty">
            <h3>暂无买入机会</h3>
            <p>系统会在后台扫描全 A + 热点并写入观察池</p>
            <el-button plain type="primary" @click="router.push('/sync')">
              去同步中心
              <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
          <el-table
            v-else
            class="decision-desktop-table"
            :data="filteredBuys"
            size="small"
            stripe
            empty-text="当前筛选下无买入标的"
          >
            <el-table-column prop="name" label="股票" width="136" fixed>
              <template #default="{ row }">
                <StockIdentity :security="row" interactive compact @select="router.push(`/stock/${row.code}`)" />
              </template>
            </el-table-column>
            <el-table-column label="策略" width="108">
              <template #default="{ row }">{{ strategyName(row.strategyId) }}</template>
            </el-table-column>
            <el-table-column label="评分" width="110">
              <template #default="{ row }"><ScoreBar :score="row.score" /></template>
            </el-table-column>
            <el-table-column label="行动计划" min-width="205">
              <template #default="{ row }">
                <div class="decision-action-plan">
                  <b :class="{ 'is-observe': !canExecutePaperBuy(row) }">
                    {{ buyActionState(row) }}
                  </b>
                  <span>仓位 {{ fmtPct(row.suggestedWeight) }} · 参考 {{ fmtPrice(row.referencePrice) }}</span>
                  <span v-if="row.stopLossPrice || row.takeProfitPrice">
                    止损 {{ fmtPrice(row.stopLossPrice) }} · 止盈 {{ fmtPrice(row.takeProfitPrice) }}
                  </span>
                  <span v-else-if="row.exitRule">失效 {{ row.exitRule }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column width="100">
              <template #header><TermTip term="confluence">共振</TermTip></template>
              <template #default="{ row }">
                <span v-if="row.strategies?.length">{{ row.strategies.join('+') }}</span>
                <span v-else>{{ row.confluenceCount || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="主线" width="96">
              <template #default="{ row }">
                <el-tag v-if="row.mainlineMatch" size="small" type="warning" effect="plain">
                  {{ row.mainlineName || '匹配' }}
                </el-tag>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="估值" width="88">
              <template #default="{ row }">
                <el-button
                  v-if="row.valuationLabel"
                  link
                  type="primary"
                  @click="router.push({ path: `/stock/${row.code}`, query: { tab: 'valuation' } })"
                >{{ row.valuationLabel }}</el-button>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="联动" width="110">
              <template #default="{ row }">
                <el-tag
                  v-if="row.linkHint"
                  size="small"
                  effect="plain"
                  :type="row.linkHint.includes('降权') ? 'danger' : 'success'"
                >{{ row.linkHint }}</el-tag>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column
              prop="scoreExplain"
              label="评分拆解"
              min-width="200"
              :show-overflow-tooltip="longTextTooltip"
            />
            <el-table-column label="风险" width="120">
              <template #default="{ row }">
                <template v-if="row.riskFlags?.length">
                  <el-tag
                    v-for="(rf, idx) in row.riskFlags.slice(0, 2)"
                    :key="idx"
                    size="small"
                    type="warning"
                    effect="plain"
                    class="risk-tag"
                  >{{ rf }}</el-tag>
                </template>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="亮点 / 消息面" min-width="310">
              <template #default="{ row }">
                <div class="stock-insight">
                  <div v-if="row.highlights?.length" class="stock-highlight-row">
                    <span v-for="highlight in row.highlights.slice(0, 3)" :key="highlight" class="stock-highlight-chip">
                      {{ highlight }}
                    </span>
                  </div>
                  <p v-if="row.newsSummary" class="stock-news-summary">{{ row.newsSummary }}</p>
                  <div v-if="row.recentNews?.length" class="stock-news-list">
                    <a
                      v-for="news in row.recentNews.slice(0, 2)"
                      :key="`${news.publishedAt}-${news.title}`"
                      class="stock-news-item"
                      :class="{ 'is-static': !news.url }"
                      :href="news.url || undefined"
                      :target="news.url ? '_blank' : undefined"
                      :rel="news.url ? 'noopener' : undefined"
                    >
                      <span>{{ newsSourceLabel(news.source) }} {{ fmtNewsTime(news.publishedAt) }}</span>
                      <b>{{ news.title }}</b>
                    </a>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              prop="reason"
              label="理由"
              min-width="200"
              :show-overflow-tooltip="longTextTooltip"
            />
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-tooltip
                  :content="canExecutePaperBuy(row) ? '按行动计划模拟买入' : paperBuyBlockedReason(row)"
                  placement="top"
                >
                  <span class="decision-order-trigger">
                    <el-button
                      type="primary"
                      link
                      :loading="ordering"
                      :disabled="!canExecutePaperBuy(row)"
                      @click="onPaperOrder(row)"
                    >{{ canExecutePaperBuy(row) ? '模拟买' : '仅观察' }}</el-button>
                  </span>
                </el-tooltip>
                <el-button
                  link
                  type="warning"
                  @click="router.push({ path: '/observe', query: { code: row.code, name: row.name || '' } })"
                >观察</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div
            v-if="buys.length && filteredBuys.length"
            class="decision-mobile-list decision-mobile-buy-list"
            role="list"
          >
            <article
              v-for="row in filteredBuys"
              :key="`mobile-buy-${row.code}-${row.strategyId}`"
              class="decision-mobile-item"
              role="listitem"
            >
              <div class="decision-mobile-head">
                <StockIdentity :security="row" interactive compact @select="router.push(`/stock/${row.code}`)" />
                <div class="decision-mobile-score">
                  <span>评分</span>
                  <ScoreBar :score="row.score" />
                </div>
              </div>
              <div class="decision-mobile-meta">
                <span>{{ strategyName(row.strategyId) }}</span>
                <span v-if="row.strategies?.length">共振 {{ row.strategies.join('+') }}</span>
                <el-tag
                  v-if="row.entryGatePassed !== null && row.entryGatePassed !== undefined"
                  size="small"
                  :type="row.entryGatePassed ? 'success' : 'warning'"
                  effect="plain"
                >{{ row.entryGatePassed ? '通过' : '观察' }}</el-tag>
              </div>
              <div class="decision-action-plan">
                <b :class="{ 'is-observe': !canExecutePaperBuy(row) }">
                  {{ buyActionState(row) }}
                </b>
                <span>建议仓位 {{ fmtPct(row.suggestedWeight) }} · 参考 {{ fmtPrice(row.referencePrice) }}</span>
                <span v-if="row.stopLossPrice || row.takeProfitPrice">
                  止损 {{ fmtPrice(row.stopLossPrice) }} · 止盈 {{ fmtPrice(row.takeProfitPrice) }}
                </span>
                <span v-else-if="row.exitRule">失效 {{ row.exitRule }}</span>
              </div>
              <div
                v-if="row.mainlineMatch || row.valuationLabel || row.linkHint || row.riskFlags?.length"
                class="decision-mobile-flags"
              >
                <el-tag v-if="row.mainlineMatch" size="small" type="warning" effect="plain">
                  {{ row.mainlineName || '主线匹配' }}
                </el-tag>
                <el-button
                  v-if="row.valuationLabel"
                  link
                  type="primary"
                  @click="router.push({ path: `/stock/${row.code}`, query: { tab: 'valuation' } })"
                >估值{{ row.valuationLabel }}</el-button>
                <el-tag
                  v-if="row.linkHint"
                  size="small"
                  effect="plain"
                  :type="row.linkHint.includes('降权') ? 'danger' : 'success'"
                >{{ row.linkHint }}</el-tag>
                <el-tag
                  v-for="(riskFlag, riskIndex) in (row.riskFlags || []).slice(0, 2)"
                  :key="riskIndex"
                  size="small"
                  type="warning"
                  effect="plain"
                  >{{ riskFlag }}</el-tag>
              </div>
              <div v-if="row.highlights?.length || row.newsSummary || row.recentNews?.length" class="decision-mobile-insight">
                <div v-if="row.highlights?.length" class="stock-highlight-row">
                  <span v-for="highlight in row.highlights.slice(0, 3)" :key="highlight" class="stock-highlight-chip">
                    {{ highlight }}
                  </span>
                </div>
                <p v-if="row.newsSummary" class="stock-news-summary">消息面 · {{ row.newsSummary }}</p>
                <div v-if="row.recentNews?.length" class="stock-news-list">
                  <a
                    v-for="news in row.recentNews.slice(0, 2)"
                    :key="`${news.publishedAt}-${news.title}`"
                    class="stock-news-item"
                    :class="{ 'is-static': !news.url }"
                    :href="news.url || undefined"
                    :target="news.url ? '_blank' : undefined"
                    :rel="news.url ? 'noopener' : undefined"
                  >
                    <span>{{ newsSourceLabel(news.source) }} {{ fmtNewsTime(news.publishedAt) }}</span>
                    <b>{{ news.title }}</b>
                  </a>
                </div>
              </div>
              <p class="decision-mobile-reason">{{ row.reason || '暂无决策理由' }}</p>
              <details v-if="row.reason || row.scoreExplain" class="decision-mobile-details">
                <summary>完整依据</summary>
                <p v-if="row.reason"><strong>理由</strong>{{ row.reason }}</p>
                <p v-if="row.scoreExplain"><strong>评分</strong>{{ row.scoreExplain }}</p>
              </details>
              <div class="decision-mobile-actions">
                <el-tooltip
                  :content="canExecutePaperBuy(row) ? '按行动计划模拟买入' : paperBuyBlockedReason(row)"
                  placement="top"
                >
                  <span class="decision-order-trigger">
                    <el-button
                      type="primary"
                      plain
                      :loading="ordering"
                      :disabled="!canExecutePaperBuy(row)"
                      @click="onPaperOrder(row)"
                    >{{ canExecutePaperBuy(row) ? '模拟买' : '仅观察' }}</el-button>
                  </span>
                </el-tooltip>
                <el-button
                  type="warning"
                  plain
                  @click="router.push({ path: '/observe', query: { code: row.code, name: row.name || '' } })"
                >观察</el-button>
              </div>
            </article>
          </div>
          <div v-else-if="buys.length" class="decision-mobile-empty">当前筛选下无买入标的</div>
        </el-tab-pane>

        <el-tab-pane :label="`建议卖出 (${sells.length})`" name="sells">
          <el-table class="decision-desktop-table" :data="sells" size="small" stripe empty-text="持仓暂无卖出建议">
            <el-table-column prop="name" label="股票" width="136" fixed>
              <template #default="{ row }">
                <StockIdentity :security="row" interactive compact @select="router.push(`/stock/${row.code}`)" />
              </template>
            </el-table-column>
            <el-table-column label="策略" width="120">
              <template #default="{ row }">{{ strategyName(row.strategyId) }}</template>
            </el-table-column>
          <el-table-column label="评分" width="110">
            <template #default="{ row }"><ScoreBar :score="row.score" /></template>
          </el-table-column>
          <el-table-column
            prop="exitRule"
            label="触发规则"
            min-width="150"
            :show-overflow-tooltip="longTextTooltip"
          />
          <el-table-column
            prop="scoreExplain"
            label="拆解"
            min-width="160"
            :show-overflow-tooltip="longTextTooltip"
          />
            <el-table-column
              prop="reason"
              label="理由"
              min-width="200"
              :show-overflow-tooltip="longTextTooltip"
            />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button type="danger" link :loading="ordering" @click="onPaperOrder(row)">模拟卖</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="sells.length" class="decision-mobile-list decision-mobile-sell-list" role="list">
            <article
              v-for="row in sells"
              :key="`mobile-sell-${row.code}-${row.strategyId}`"
              class="decision-mobile-item"
              role="listitem"
            >
              <div class="decision-mobile-head">
                <StockIdentity :security="row" interactive compact @select="router.push(`/stock/${row.code}`)" />
                <div class="decision-mobile-score">
                  <span>评分</span>
                  <ScoreBar :score="row.score" />
                </div>
              </div>
              <div class="decision-mobile-meta">
                <span>{{ strategyName(row.strategyId) }}</span>
                <span v-if="row.exitRule">触发 {{ row.exitRule }}</span>
              </div>
              <p class="decision-mobile-reason">{{ row.reason || '暂无卖出理由' }}</p>
              <details v-if="row.reason || row.scoreExplain" class="decision-mobile-details">
                <summary>完整依据</summary>
                <p v-if="row.reason"><strong>理由</strong>{{ row.reason }}</p>
                <p v-if="row.scoreExplain"><strong>评分</strong>{{ row.scoreExplain }}</p>
              </details>
              <div class="decision-mobile-actions decision-mobile-actions-single">
                <el-button type="danger" plain :loading="ordering" @click="onPaperOrder(row)">模拟卖</el-button>
              </div>
            </article>
          </div>
          <div v-else class="decision-mobile-empty">持仓暂无卖出建议</div>
        </el-tab-pane>

        <el-tab-pane :label="`继续持有 (${holds.length})`" name="holds">
          <el-table class="decision-desktop-table" :data="holds" size="small" stripe empty-text="持仓为空，或均已有买卖建议">
            <el-table-column prop="name" label="股票" width="142">
              <template #default="{ row }">
                <StockIdentity :security="row" interactive compact @select="router.push(`/stock/${row.code}`)" />
              </template>
            </el-table-column>
            <el-table-column
              prop="reason"
              label="理由"
              min-width="200"
              :show-overflow-tooltip="longTextTooltip"
            />
            <el-table-column
              prop="exitRule"
              label="止损/止盈"
              min-width="150"
              :show-overflow-tooltip="longTextTooltip"
            />
            <el-table-column
              prop="fundNote"
              label="基本面"
              min-width="160"
              :show-overflow-tooltip="longTextTooltip"
            />
          </el-table>
          <div v-if="holds.length" class="decision-mobile-list decision-mobile-hold-list" role="list">
            <article
              v-for="row in holds"
              :key="`mobile-hold-${row.code}`"
              class="decision-mobile-item"
              role="listitem"
            >
              <div class="decision-mobile-head decision-mobile-hold-head">
                <StockIdentity :security="row" interactive compact @select="router.push(`/stock/${row.code}`)" />
                <el-tag size="small" type="info" effect="plain">持有</el-tag>
              </div>
              <p class="decision-mobile-reason decision-mobile-reason-full">{{ row.reason || '暂无持有理由' }}</p>
              <dl class="decision-mobile-facts">
                <div v-if="row.exitRule">
                  <dt>止损/止盈</dt>
                  <dd>{{ row.exitRule }}</dd>
                </div>
                <div v-if="row.fundNote">
                  <dt>基本面</dt>
                  <dd>{{ row.fundNote }}</dd>
                </div>
              </dl>
            </article>
          </div>
          <div v-else class="decision-mobile-empty">持仓为空，或均已有买卖建议</div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <!-- ③ 次要信息折叠 -->
    <div v-if="(data?.executableCount || 0) > 0" class="exec-bar">
      <span>
        今日可执行提示 <b>{{ data.executableCount }}</b>
        · 低估 <b class="up">{{ data.valuationCheapCount ?? 0 }}</b>
      </span>
      <div class="exec-actions">
        <el-button size="small" type="warning" plain @click="router.push('/observe')">去观察池处理</el-button>
        <el-button size="small" type="primary" plain @click="buyExecutableOnly = true; persistBuyFilters(); activeTab = 'buys'">
          筛选可执行买入
        </el-button>
      </div>
    </div>

    <el-collapse v-model="morePanels" class="more-collapse">
      <el-collapse-item v-if="playbook" name="playbook">
        <template #title>
          <div class="collapse-heading">
            <span class="collapse-title">策略战法与规则</span>
            <span class="collapse-sub">S1 / S2 / S3 · 评分与仓位</span>
          </div>
        </template>
        <div class="strategy-grid">
          <article v-for="s in strategies" :key="s.strategyId" class="strategy-card">
            <header>
              <b>{{ s.strategyId }} · {{ s.name }}</b>
              <el-tag size="small" effect="plain">{{ s.style }}</el-tag>
            </header>
            <p><label>买入</label>{{ s.buyRule }}</p>
            <p><label>离场</label>{{ s.exitRule }}</p>
            <p class="fit"><label>市况</label>{{ s.marketFit }}</p>
          </article>
        </div>
        <div class="rules-grid">
          <div>
            <h3>流水线</h3>
            <ol>
              <li v-for="(step, i) in playbook.pipelineSteps || []" :key="i">
                {{ step.replace(/^\d+\.\s*/, '') }}
              </li>
            </ol>
          </div>
          <div>
            <h3>评分 / 仓位</h3>
            <ul>
              <li v-for="(r, i) in playbook.scoreRules || []" :key="'s'+i">{{ r }}</li>
              <li v-for="(r, i) in playbook.positionRules || []" :key="'p'+i">{{ r }}</li>
            </ul>
          </div>
          <div>
            <h3>门禁 / 卖出</h3>
            <ul>
              <li v-for="(r, i) in playbook.fundRules || []" :key="'f'+i">{{ r }}</li>
              <li v-for="(r, i) in playbook.sellRules || []" :key="'e'+i">{{ r }}</li>
            </ul>
          </div>
        </div>
      </el-collapse-item>

      <el-collapse-item v-if="attribution" name="attr">
        <template #title>
          <div class="collapse-heading">
            <span class="collapse-title">复盘归因</span>
            <span class="collapse-sub">{{ attribution.message }}</span>
          </div>
        </template>
        <div class="attr-explain">
          <strong>怎么看</strong>
          <span>样本是建议条数；次日均是有下一交易日行情样本的平均涨跌；胜率是其中次日上涨的占比。共振指同一标的同时命中至少 2 个策略，主线同向指建议与当日市场主线一致；“-”表示尚无有效行情，不参与均值和胜率计算。</span>
        </div>
        <div class="attr-grid">
          <div v-for="block in [
            { title: '按策略', rows: attribution.byStrategy },
            { title: '按共振', rows: attribution.byConfluence },
            { title: '按主线', rows: attribution.byMainline },
            { title: '按市场立场', rows: attribution.byStance },
          ]" :key="block.title">
            <h4>{{ block.title }}</h4>
            <el-table :data="block.rows || []" size="small" stripe flexible empty-text="暂无" class="attr-table">
              <el-table-column prop="label" label="桶" min-width="86" />
              <el-table-column prop="sampleCount" label="样本" min-width="56" />
              <el-table-column label="次日均%" min-width="86">
                <template #default="{ row }">
                  <span :class="Number(row.avgNextPct) > 0 ? 'up' : Number(row.avgNextPct) < 0 ? 'down' : ''">
                    {{ row.avgNextPct == null ? '-' : Number(row.avgNextPct).toFixed(2) + '%' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="胜率" min-width="64">
                <template #default="{ row }">{{ row.winRate == null ? '-' : row.winRate + '%' }}</template>
              </el-table-column>
            </el-table>
          </div>
          <div>
            <h4>成熟五日超额</h4>
            <el-table
              :data="attribution.matureStrategyPerformance || []"
              size="small"
              stripe
              flexible
              empty-text="样本积累中"
              class="attr-table"
            >
              <el-table-column prop="strategyId" label="策略" min-width="76" />
              <el-table-column prop="sampleCount" label="样本" min-width="56" />
              <el-table-column label="超额均%" min-width="86">
                <template #default="{ row }">
                  <span :class="Number(row.avgExcess5d) > 0 ? 'up' : Number(row.avgExcess5d) < 0 ? 'down' : ''">
                    {{ row.avgExcess5d == null ? '-' : (Number(row.avgExcess5d) * 100).toFixed(2) + '%' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="胜率" min-width="64">
                <template #default="{ row }">{{ row.winRate5d == null ? '-' : (Number(row.winRate5d) * 100).toFixed(0) + '%' }}</template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-collapse-item>

      <el-collapse-item v-if="history.length" name="history">
        <template #title>
          <div class="collapse-heading">
            <span class="collapse-title">决策历史</span>
            <span class="collapse-sub">点击行回看当日清单</span>
          </div>
        </template>
        <el-table
          :data="history"
          size="small"
          stripe
          highlight-current-row
          class="history-table"
          @row-click="openHistoryDay"
        >
          <el-table-column prop="actionDate" label="日期" width="120" />
          <el-table-column prop="stance" label="立场" width="70" />
          <el-table-column prop="buyCount" label="买" width="60" />
          <el-table-column prop="sellCount" label="卖" width="60" />
          <el-table-column prop="holdCount" label="持有" width="60" />
          <el-table-column prop="executableCount" label="可执行" width="70" />
          <el-table-column prop="valuationCheapCount" label="低估" width="60" />
          <el-table-column prop="mainlineMatchCount" label="主线" width="60" />
          <el-table-column label="买入次日均%" width="120">
            <template #default="{ row }">
              <span :class="Number(row.nextDayAvgPct) > 0 ? 'up' : Number(row.nextDayAvgPct) < 0 ? 'down' : ''">
                {{ row.nextDayAvgPct == null ? '-' : Number(row.nextDayAvgPct).toFixed(2) + '%' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="note" label="说明" min-width="200" show-overflow-tooltip />
        </el-table>
      </el-collapse-item>
    </el-collapse>

    <el-dialog
      v-model="shareOpen"
      title="分享智能决策截图"
      width="96vw"
      top="3vh"
      append-to-body
      destroy-on-close
      class="decision-share-dialog"
      @closed="revokeSharePreview"
    >
      <p class="share-tip">按当前决策清单生成分享图；可复制或下载 PNG 后发微信/社群。</p>
      <div class="share-stage">
        <img v-if="sharePreviewUrl" :src="sharePreviewUrl" alt="智能决策分享预览" />
        <el-empty v-else description="预览生成中…" />
      </div>
      <template #footer>
        <el-button @click="closeShare">关闭</el-button>
        <el-button :loading="copying" @click="onCopyShare">复制图片</el-button>
        <el-button type="primary" :loading="downloading" @click="onDownloadShare">下载 PNG</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.decision {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.decision .header {
  margin-bottom: 0;
}

.decision-evidence-toggle {
  order: 3;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass);
  box-shadow: var(--shadow-soft);
}

.decision-evidence-toggle > summary {
  display: flex;
  min-height: 46px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 16px;
  color: var(--ink-soft);
  cursor: pointer;
  font-size: 13px;
  font-weight: 650;
}

.decision-evidence-toggle > summary small {
  color: var(--muted);
  font-size: 11px;
  font-weight: 500;
}

.decision-evidence-toggle[open] > summary {
  border-bottom: 1px solid var(--line);
}

.decision-evidence-toggle .stance-panel {
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.decision-evidence-toggle .factor-strip,
.decision-evidence-toggle .tips-row {
  margin-right: 16px;
  margin-left: 16px;
}

.decision-evidence-toggle .tips-row {
  margin-bottom: 16px;
}

.advice-panel {
  order: 1;
}

.action-panel {
  order: 2;
}

.exec-bar,
.more-collapse {
  order: 4;
}

.dec-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 18px;
  padding: 2px 2px 12px;
  border-bottom: 1px solid var(--line);
}

.dec-heading {
  min-width: 0;
}

.dec-header .eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.04em;
  color: var(--accent);
  text-transform: uppercase;
}

.dec-header .sub {
  margin: 4px 0 0;
  max-width: 52ch;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.5;
}

.dec-controls {
  display: flex;
  align-items: center;
  gap: 6px;
}

.market-scope {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 40px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.46);
  cursor: pointer;
}

.market-scope > span {
  display: flex;
  align-items: baseline;
  gap: 5px;
  white-space: nowrap;
}

.market-scope em {
  color: var(--muted);
  font-size: 10px;
  font-style: normal;
}

.market-scope b {
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 650;
}

.sync-link {
  min-height: 40px;
  padding: 0 8px;
  font-weight: 600;
}

.sync-link :deep(.el-icon) {
  margin-left: 2px;
}

/* —— 市场立场 —— */
.stance-panel {
  position: relative;
  display: block;
  padding: 18px 20px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass-strong);
  box-shadow: var(--shadow-soft);
  overflow: hidden;
}

.stance-panel.stance-attack {
  border-color: rgba(255, 59, 48, 0.22);
  background:
    linear-gradient(135deg, rgba(255, 59, 48, 0.06), transparent 42%),
    var(--glass-strong);
}

.stance-panel.stance-defend {
  border-color: rgba(0, 113, 227, 0.22);
  background:
    linear-gradient(135deg, rgba(0, 113, 227, 0.07), transparent 42%),
    var(--glass-strong);
}

.kicker {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--muted);
}

.stance-title-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.score-ring {
  --pct: 0;
  width: 76px;
  height: 76px;
  border-radius: 50%;
  flex-shrink: 0;
  background: conic-gradient(
    var(--accent) calc(var(--pct) * 1%),
    rgba(0, 0, 0, 0.06) 0
  );
  display: grid;
  place-items: center;
}

.stance-attack .score-ring {
  background: conic-gradient(
    #ff3b30 calc(var(--pct) * 1%),
    rgba(0, 0, 0, 0.06) 0
  );
}

.score-ring-inner {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  line-height: 1.05;
}

.score-ring-inner strong {
  font-size: 20px;
  font-family: var(--font-display);
}

.score-ring-inner small {
  font-size: 10px;
  color: var(--muted);
}

.pill {
  display: inline-flex;
  align-items: center;
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 750;
  background: rgba(0, 0, 0, 0.05);
}

.stance-attack .pill {
  color: #c45656;
  background: rgba(255, 59, 48, 0.12);
}

.stance-defend .pill {
  color: #0058b0;
  background: rgba(0, 113, 227, 0.12);
}

.reason,
.advice {
  margin: 0 0 4px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--muted);
}

.advice {
  color: var(--ink-soft);
  font-weight: 600;
}

.side-title {
  font-size: 11px;
  font-weight: 650;
  color: var(--muted);
  letter-spacing: 0.04em;
}

.theme-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.theme-inline {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  align-items: start;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.side-title.inline {
  margin: 0;
  padding-top: 5px;
}

.theme-chip-grid {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.theme-chip {
  display: inline-flex;
  flex: 0 1 auto;
  align-items: center;
  gap: 6px;
  min-width: 0;
  max-width: min(100%, 240px);
  border: 0;
  font-size: 12px;
  font-family: inherit;
  line-height: inherit;
  text-align: left;
  padding: 3px 9px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.04);
  color: var(--ink-soft);
  cursor: pointer;
  transition: background 150ms ease, color 150ms ease;
}

.theme-chip:hover,
.theme-chip:focus-visible {
  background: var(--accent-soft);
  color: var(--accent);
}

.theme-chip:focus-visible {
  outline: 2px solid rgba(0, 113, 227, 0.28);
  outline-offset: 2px;
}

.theme-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.theme-link-icon {
  flex: 0 0 auto;
  width: 12px;
  height: 12px;
  font-size: 12px;
  opacity: 0.62;
}

.buy-ai-panel {
  margin-bottom: 12px;
  padding: 14px 16px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: linear-gradient(165deg, rgba(0, 113, 227, 0.05), transparent 55%), var(--glass);
}

.buy-ai-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 8px;
}

.buy-ai-head h3 {
  margin: 0;
  font-size: 15px;
}

.buy-ai-head .muted {
  margin: 4px 0 0;
  font-size: 12px;
}

.buy-ai-summary {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  color: var(--ink-soft);
  white-space: pre-wrap;
}

.buy-ai-watch {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.watch-chip {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 8px;
  background: rgba(0, 113, 227, 0.08);
  color: var(--ink-soft);
}

.buy-ai-notes {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.note-row {
  display: grid;
  grid-template-columns: 136px auto minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  font-size: 12px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.03);
}

.note-priority {
  justify-self: start;
  width: fit-content;
}

.note-name {
  color: var(--ink-soft);
  font-weight: 600;
}

.note-text {
  color: var(--muted);
  line-height: 1.45;
}

.buy-ai-risk {
  margin: 10px 0 0;
  font-size: 12px;
  color: #c45656;
  font-weight: 600;
}

.buy-ai-disc {
  margin: 6px 0 0;
  font-size: 11px;
  color: var(--muted);
}

/* —— 因子条 —— */
.factor-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.factor-cell:nth-child(7):last-child {
  grid-column: span 2;
}

.factor-cell {
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--glass);
}

.factor-cell label {
  display: block;
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 4px;
}

.factor-val {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 2px;
}

.factor-val b {
  font-size: 13px;
  font-weight: 700;
}

.factor-val em {
  font-style: normal;
  font-size: 11px;
  font-weight: 700;
}

.factor-cell p {
  margin: 0;
  font-size: 11px;
  color: var(--muted);
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.tips-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.advice-panel {
  margin: 12px 0;
  padding: 16px 18px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass-strong);
  box-shadow: var(--shadow-soft);
}

.advice-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 12px;
}

.advice-head h2 {
  margin: 0 0 6px;
  font-size: 17px;
}

.advice-head p {
  max-width: 760px;
  margin: 0;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.55;
}

.advice-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(78px, 1fr));
  gap: 8px;
  flex: 0 0 auto;
}

.advice-metrics span {
  padding: 8px 10px;
  border-left: 2px solid rgba(0, 113, 227, 0.24);
  background: rgba(0, 0, 0, 0.025);
}

.advice-metrics label,
.advice-metrics b {
  display: block;
  letter-spacing: 0;
}

.advice-metrics label {
  margin-bottom: 3px;
  color: var(--muted);
  font-size: 11px;
}

.advice-metrics b {
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.advice-table-wrap {
  width: 100%;
  overflow-x: auto;
}

.advice-table {
  min-width: 920px;
}

.advice-mobile-list {
  display: none;
}

.advice-code {
  display: block;
  color: var(--muted);
  font-size: 10px;
  text-align: center;
}

.review-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.review-row span {
  padding: 4px 8px;
  border: 1px solid var(--line);
  border-radius: 6px;
  color: var(--muted);
  font-size: 11px;
}

.tip-item {
  margin: 0;
}

/* —— 清单主面板 —— */
.action-panel {
  padding: 16px 18px 12px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass-strong);
  box-shadow: var(--shadow-soft);
}

.action-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.action-head h2 {
  margin: 0 0 4px;
  font-size: 17px;
  font-family: var(--font-display);
}

.metric-row {
  display: flex;
  gap: 18px;
}

.metric label {
  display: block;
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 2px;
}

.metric b {
  font-size: 22px;
  font-family: var(--font-display);
  font-variant-numeric: tabular-nums;
}

.tabs {
  margin-top: 4px;
}

.tabs :deep(.el-tabs__header) {
  margin-bottom: 10px;
}

.decision-mobile-list,
.decision-mobile-empty {
  display: none;
}

.risk-tag {
  margin-right: 4px;
}

.decision-data-status {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 6px 14px;
  margin: 0 0 10px;
  padding: 8px 10px;
  border-left: 3px solid #d29a1d;
  background: rgba(210, 154, 29, 0.08);
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.45;
}

.decision-data-status b {
  color: var(--ink);
}

.decision-data-status.is-green {
  border-left-color: var(--up);
  background: rgba(22, 142, 92, 0.08);
}

.decision-data-status.is-red {
  border-left-color: var(--down);
  background: rgba(214, 69, 69, 0.08);
}

.decision-action-plan {
  display: grid;
  gap: 3px;
  min-width: 170px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.4;
}

.decision-action-plan b {
  color: var(--up);
  font-size: 12px;
}

.decision-action-plan b.is-observe {
  color: #a87113;
}

.decision-order-trigger {
  display: inline-flex;
}

.stock-insight {
  display: grid;
  gap: 6px;
  min-width: 240px;
}

.stock-highlight-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.stock-highlight-chip {
  max-width: 100%;
  padding: 2px 6px;
  overflow: hidden;
  border-left: 2px solid rgba(0, 113, 227, 0.34);
  background: rgba(0, 113, 227, 0.05);
  color: var(--ink-soft);
  font-size: 11px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stock-news-summary {
  margin: 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.45;
}

.stock-news-list {
  display: grid;
  gap: 3px;
}

.stock-news-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 5px;
  align-items: baseline;
  min-width: 0;
  color: var(--ink-soft);
  font-size: 11px;
  line-height: 1.4;
  text-decoration: none;
}

.stock-news-item:not(.is-static):hover b {
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.stock-news-item span {
  color: var(--muted);
  font-size: 10px;
  white-space: nowrap;
}

.stock-news-item b {
  overflow: hidden;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.exec-bar {
  position: sticky;
  bottom: 12px;
  z-index: 20;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 14px 0 0;
  padding: 10px 14px;
  border: 1px solid rgba(0, 113, 227, 0.22);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(16px) saturate(160%);
  box-shadow: var(--shadow-soft);
  font-size: 13px;
  color: var(--ink-soft);
}

.exec-bar b {
  font-variant-numeric: tabular-nums;
}

.exec-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* —— 折叠次要区 —— */
.more-collapse {
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass);
  overflow: hidden;
}

.more-collapse :deep(.el-collapse-item__header) {
  padding: 0 16px;
  min-height: 48px;
  height: auto;
  align-items: center;
  background: transparent;
  border-bottom-color: var(--line);
  font-size: 14px;
}

.more-collapse :deep(.el-collapse-item__wrap) {
  border-bottom-color: var(--line);
  background: transparent;
}

.more-collapse :deep(.el-collapse-item__content) {
  padding: 14px 16px 18px;
}

.collapse-heading {
  display: flex;
  flex: 1;
  align-items: baseline;
  min-width: 0;
  gap: 10px;
  padding: 8px 0;
}

.collapse-title {
  font-weight: 700;
  flex: 0 0 auto;
}

.collapse-sub {
  min-width: 0;
  font-size: 12px;
  font-weight: 400;
  line-height: 1.4;
  color: var(--muted);
}

.strategy-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.strategy-card {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.5);
}

.strategy-card header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.strategy-card p {
  margin: 4px 0;
  font-size: 12px;
  line-height: 1.45;
}

.strategy-card label {
  color: var(--muted);
  margin-right: 6px;
}

.strategy-card .fit {
  color: var(--muted);
}

.rules-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.rules-grid h3,
.attr-grid h4 {
  margin: 0 0 8px;
  font-size: 13px;
}

.rules-grid ol,
.rules-grid ul {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--muted);
}

.attr-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.attr-grid > div {
  min-width: 0;
  max-width: 100%;
}

.attr-explain {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 6px 10px;
  margin: 0 0 14px;
  padding: 10px 12px;
  border-left: 3px solid var(--accent);
  background: rgba(0, 113, 227, 0.06);
  color: var(--muted);
  font-size: 12px;
  line-height: 1.55;
}

.attr-explain strong {
  color: var(--ink-soft);
  white-space: nowrap;
}

.attr-table {
  width: 100%;
}

.history-table :deep(.el-table__row) {
  cursor: pointer;
}

.muted {
  color: var(--muted);
  font-size: 12px;
}

.up {
  color: var(--up);
}

.down {
  color: var(--down);
}

@media (max-width: 1100px) {
  .factor-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .factor-cell:nth-child(7):last-child {
    grid-column: 1 / -1;
  }
}

@media (max-width: 900px) {
  .dec-header {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .note-row {
    grid-template-columns: 64px 1fr;
  }

  .note-row .note-text {
    grid-column: 1 / -1;
  }

  .factor-strip,
  .strategy-grid,
  .rules-grid,
  .attr-grid {
    grid-template-columns: 1fr;
  }

  .action-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .advice-head {
    flex-direction: column;
  }

  .advice-metrics {
    width: 100%;
  }
}

@media (max-width: 560px) {
  .more-collapse :deep(.el-collapse-item__header) {
    align-items: center;
  }

  .collapse-heading {
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
  }

  .attr-explain {
    grid-template-columns: 1fr;
    gap: 2px;
    padding: 9px 10px;
  }

  .dec-header {
    grid-template-columns: 1fr;
    align-items: stretch;
    gap: 10px;
    padding: 0 2px 10px;
  }

  .dec-header .eyebrow {
    margin-bottom: 2px;
    font-size: 10px;
  }

  .dec-header .sub {
    margin-top: 3px;
    font-size: 12px;
    line-height: 1.4;
  }

  .dec-controls {
    align-items: center;
    justify-content: space-between;
    width: 100%;
  }

  .market-scope {
    min-height: 44px;
    gap: 7px;
    padding: 0 2px;
    border: 0;
    border-radius: 0;
    background: transparent;
  }

  .market-scope > span {
    gap: 4px;
  }

  .market-scope em {
    font-size: 11px;
  }

  .market-scope b {
    color: var(--ink);
    font-size: 13px;
  }

  .market-scope :deep(.el-switch) {
    --el-switch-on-color: var(--accent);
    --el-switch-off-color: #d7dbe4;
  }

  .sync-link {
    min-height: 44px;
    padding: 0 2px;
  }

  .action-panel {
    padding: 14px 10px 10px;
  }

  .advice-panel {
    padding: 14px 10px;
  }

  .advice-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 4px;
  }

  .advice-metrics span {
    padding: 6px 8px;
  }

  .advice-metrics b {
    font-size: 13px;
  }

  .advice-table-wrap {
    overflow: visible;
  }

  .advice-table {
    display: none;
  }

  .advice-mobile-list {
    display: block;
    border-top: 1px solid var(--line);
  }

  .advice-mobile-item {
    padding: 9px 0 8px;
    border-bottom: 1px solid var(--line);
  }

  .advice-mobile-main {
    display: grid;
    grid-template-columns: 20px minmax(0, 1fr) auto;
    align-items: center;
    gap: 7px;
  }

  .advice-priority {
    color: var(--muted);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
    text-align: center;
  }

  .advice-mobile-stock {
    display: flex;
    align-items: center;
    gap: 5px;
    min-width: 0;
  }

  .advice-mobile-stock :deep(.el-button) {
    min-width: 0;
    min-height: 28px;
    padding: 0;
    overflow: hidden;
    font-size: 14px;
    font-weight: 650;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .advice-mobile-stock small {
    flex: 0 0 auto;
    color: var(--muted);
    font-size: 10px;
  }

  .advice-mobile-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 10px;
    margin: 1px 0 0 27px;
    color: var(--muted);
    font-size: 11px;
    font-variant-numeric: tabular-nums;
  }

  .advice-mobile-reason {
    margin: 3px 0 0 27px;
    overflow: hidden;
    color: var(--ink-soft);
    font-size: 11px;
    line-height: 1.35;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .decision-desktop-table {
    display: none;
  }

  .decision-mobile-list {
    display: block;
    border-top: 1px solid var(--line);
  }

  .decision-mobile-empty {
    display: block;
    padding: 28px 12px;
    color: var(--muted);
    font-size: 13px;
    text-align: center;
  }

  .decision-mobile-item {
    padding: 12px 2px;
    border-bottom: 1px solid var(--line);
  }

  .decision-mobile-item:last-child {
    border-bottom: 0;
  }

  .decision-mobile-head {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 94px;
    align-items: center;
    gap: 10px;
  }

  .decision-mobile-head :deep(.stock-identity) {
    --stock-identity-width: 100%;
  }

  .decision-mobile-hold-head {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .decision-mobile-score {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 3px;
  }

  .decision-mobile-score > span {
    color: var(--muted);
    font-size: 10px;
  }

  .decision-mobile-score :deep(.score-bar) {
    width: 94px;
  }

  .decision-mobile-meta,
  .decision-mobile-flags {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 5px 10px;
    margin-top: 8px;
  }

  .decision-mobile-meta {
    color: var(--muted);
    font-size: 11px;
    font-variant-numeric: tabular-nums;
  }

  .decision-mobile-item .decision-action-plan {
    margin-top: 8px;
    padding: 8px 0 0 9px;
    border-top: 1px solid var(--line);
    border-left: 2px solid rgba(0, 113, 227, 0.22);
  }

  .decision-mobile-flags {
    gap: 5px;
  }

  .decision-mobile-flags :deep(.el-button) {
    min-height: 24px;
    padding: 0 3px;
  }

  .decision-mobile-reason {
    display: -webkit-box;
    margin: 8px 0 0;
    overflow: hidden;
    color: var(--ink-soft);
    font-size: 12px;
    line-height: 1.5;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }

  .decision-mobile-insight {
    display: grid;
    gap: 6px;
    margin-top: 8px;
    padding: 8px 0 0 9px;
    border-top: 1px solid var(--line);
    border-left: 2px solid rgba(0, 113, 227, 0.18);
  }

  .decision-mobile-insight .stock-news-item {
    grid-template-columns: 1fr;
    gap: 1px;
  }

  .decision-mobile-insight .stock-news-item b {
    white-space: normal;
  }

  .decision-mobile-details {
    margin-top: 7px;
    color: var(--muted);
    font-size: 11px;
  }

  .decision-mobile-details summary {
    width: fit-content;
    color: var(--accent);
    cursor: pointer;
  }

  .decision-mobile-details p {
    margin: 6px 0 0;
    line-height: 1.5;
  }

  .decision-mobile-details strong {
    margin-right: 6px;
    color: var(--ink-soft);
  }

  .decision-mobile-reason-full {
    display: block;
    overflow: visible;
  }

  .decision-mobile-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    margin-top: 10px;
  }

  .decision-mobile-actions-single {
    grid-template-columns: 1fr;
  }

  .decision-mobile-actions :deep(.el-button) {
    width: 100%;
    min-height: 44px;
    margin: 0;
  }

  .decision-mobile-actions .decision-order-trigger {
    width: 100%;
  }

  .decision-mobile-actions .decision-order-trigger :deep(.el-button) {
    width: 100%;
  }

  .decision-mobile-facts {
    display: grid;
    gap: 7px;
    margin: 8px 0 0;
  }

  .decision-mobile-facts div {
    display: grid;
    grid-template-columns: 64px minmax(0, 1fr);
    gap: 8px;
  }

  .decision-mobile-facts dt {
    color: var(--muted);
    font-size: 11px;
  }

  .decision-mobile-facts dd {
    margin: 0;
    color: var(--ink-soft);
    font-size: 12px;
    line-height: 1.45;
  }

  .metric-row {
    width: 100%;
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 6px;
  }

  .metric-row .metric {
    min-width: 0;
    padding: 8px 4px;
    text-align: center;
  }

  .metric-row .metric b {
    font-size: 18px;
  }
}
</style>

<style>
.decision-share-dialog.el-dialog {
  max-width: 1100px;
}

.decision-share-dialog .el-dialog__body {
  padding-top: 8px;
}

.decision-share-dialog .share-tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.decision-share-dialog .share-stage {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  max-height: min(72vh, 820px);
  overflow: auto;
  background: #eef1f6;
  border-radius: 10px;
  padding: 12px;
}

.decision-share-dialog .share-stage img {
  display: block;
  max-width: 100%;
  height: auto;
  border-radius: 6px;
  box-shadow: 0 8px 28px rgba(15, 23, 42, 0.12);
}
</style>

<!-- tooltip 挂到 body，需非 scoped -->
<style>
.decision-long-tooltip {
  max-width: min(560px, 92vw) !important;
  line-height: 1.55 !important;
  white-space: pre-wrap !important;
  word-break: break-word !important;
  font-size: 13px !important;
  padding: 10px 12px !important;
}
</style>
