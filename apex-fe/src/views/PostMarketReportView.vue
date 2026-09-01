<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { DocumentCopy, Refresh } from '@element-plus/icons-vue'
import { fetchPostMarketReport, refreshPostMarketReport } from '../api/postMarketReport'
import StockIdentity from '../components/StockIdentity.vue'
import {
  formatCapitalAmount,
  formatCapitalPercent,
  formatCapitalPrice,
  resolveCapitalClass,
} from '../utils/capitalFlow.js'
import { tradingCalendar } from '../utils/dataFreshness.js'
import { isPostMarketReportVisible } from '../utils/postMarketReportVisibility.js'

const router = useRouter()
const loading = ref(false)
const refreshing = ref(false)
const report = ref(null)
const reportClock = ref(new Date())
let reportClockTimer

const reportWindowOpen = computed(() => isPostMarketReportVisible(reportClock.value, tradingCalendar.value))
const marketSnapshot = computed(() => report.value?.marketSnapshot || {})
const indexes = computed(() => marketSnapshot.value.indexes || [])
const industryBoards = computed(() => report.value?.industryBoards || [])
const conceptBoards = computed(() => report.value?.conceptBoards || [])
const mainlines = computed(() => report.value?.mainlines || [])
const starStocks = computed(() => report.value?.starStocks || [])
const dragonTigerItems = computed(() => report.value?.dragonTigerItems || [])
const activeSeats = computed(() => report.value?.activeSeats || [])
const reportDate = computed(() => report.value?.tradeDate || report.value?.reportDate || '')
const generatedAt = computed(() => formatDateTime(report.value?.generatedAt))
const dataAsOf = computed(() => formatDateTime(report.value?.dataAsOf || marketSnapshot.value.marketDataUpdatedAt))
const sourceLabel = computed(() => report.value?.reportSource === 'AI' ? '智能研判' : '规则研判')
const hasReportData = computed(() => Boolean(
  indexes.value.length || industryBoards.value.length || conceptBoards.value.length
  || mainlines.value.length || starStocks.value.length || dragonTigerItems.value.length
  || activeSeats.value.length || report.value?.content,
))

function formatDateTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

function formatInteger(value) {
  if (value == null || value === '') return '-'
  const number = Number(value)
  return Number.isFinite(number) ? number.toLocaleString('zh-CN') : '-'
}

function dataLevelLabel(level) {
  if (level === 'GREEN') return '数据覆盖完整'
  if (level === 'YELLOW') return '数据覆盖一般'
  return '数据覆盖有限'
}

function dataLevelType(level) {
  if (level === 'GREEN') return 'success'
  if (level === 'YELLOW') return 'warning'
  return 'danger'
}

function boardReason(row) {
  return row.moveReason || row.reason || '暂无异动说明'
}

function stockReasons(row) {
  return Array.isArray(row.reasons) && row.reasons.length ? row.reasons.join('；') : '暂无入选说明'
}

function seatName(row) {
  return row.actorName || row.subjectName || '未标注席位'
}

function seatStock(row) {
  return row.relatedName || row.relatedCode || row.topic || '-'
}

function confidenceLabel(value) {
  const labels = {
    HIGH: '高',
    MEDIUM: '中',
    LOW: '低',
    SEAT_LABEL: '席位标签',
  }
  return labels[String(value || '').toUpperCase()] || value || ''
}

function openStock(stock) {
  const code = stock?.code || stock?.relatedCode
  if (code) router.push(`/stock/${code}`)
}

async function loadReport() {
  if (!reportWindowOpen.value) return
  loading.value = true
  try {
    const response = await fetchPostMarketReport()
    report.value = response.data || null
  } catch (error) {
    ElMessage.error(error.message || '盘后总结加载失败')
  } finally {
    loading.value = false
  }
}

async function refreshReport() {
  refreshing.value = true
  try {
    const response = await refreshPostMarketReport()
    report.value = response.data || null
    ElMessage.success('盘后总结已重新生成')
  } catch (error) {
    ElMessage.error(error.message || '盘后总结生成失败')
  } finally {
    refreshing.value = false
  }
}

async function copyReport() {
  if (!report.value?.content) {
    ElMessage.warning('暂无可复制的盘后总结')
    return
  }
  try {
    await navigator.clipboard.writeText(report.value.content)
    ElMessage.success('盘后总结已复制')
  } catch {
    ElMessage.error('复制失败，请检查浏览器剪贴板权限')
  }
}

function syncReportWindow() {
  const wasOpen = reportWindowOpen.value
  reportClock.value = new Date()
  if (!reportWindowOpen.value) {
    report.value = null
    if (wasOpen) void router.replace('/dashboard')
  } else if (!wasOpen) {
    void loadReport()
  }
}

watch(tradingCalendar, (calendar) => {
  if (!calendar) return
  if (reportWindowOpen.value && !report.value) void loadReport()
  else if (!reportWindowOpen.value) void router.replace('/dashboard')
})

onMounted(() => {
  if (tradingCalendar.value) {
    if (reportWindowOpen.value) void loadReport()
    else void router.replace('/dashboard')
  }
  reportClockTimer = window.setInterval(syncReportWindow, 30000)
})

onBeforeUnmount(() => {
  if (reportClockTimer) window.clearInterval(reportClockTimer)
})
</script>

<template>
  <div class="page post-market-page" v-loading="loading">
    <header v-if="reportWindowOpen" class="post-market-toolbar">
      <div>
        <span>盘后总结</span>
        <strong v-if="reportDate">{{ reportDate }}</strong>
      </div>
      <div class="actions">
        <el-tooltip content="复制完整总结" placement="bottom">
          <el-button :icon="DocumentCopy" :disabled="!report?.content" @click="copyReport">复制</el-button>
        </el-tooltip>
        <el-tooltip content="使用最新收盘数据重新生成" placement="bottom">
          <el-button type="primary" :icon="Refresh" :loading="refreshing" @click="refreshReport">更新</el-button>
        </el-tooltip>
      </div>
    </header>

    <article v-if="reportWindowOpen && report" class="post-market-report" aria-label="最新交易日盘后总结">
      <header class="post-market-lead">
        <div>
          <p class="post-market-kicker">APEX POST-MARKET REVIEW</p>
          <h1>收盘后的市场全景</h1>
          <p class="post-market-subtitle">从大盘强弱、板块资金与核心标的中，复盘今天真正发生了什么</p>
          <div class="post-market-meta" aria-label="盘后总结数据时效">
            <span v-if="dataAsOf">数据截至 {{ dataAsOf }}</span>
            <span v-if="generatedAt">生成于 {{ generatedAt }}</span>
            <el-tag size="small" effect="plain" :type="dataLevelType(report.dataLevel)">{{ dataLevelLabel(report.dataLevel) }}</el-tag>
            <span>{{ sourceLabel }}</span>
            <el-tag v-if="report.contentLevel" size="small" effect="plain" :type="report.contentLevel === 'FULL' ? 'success' : 'warning'">
              {{ report.contentLevel === 'FULL' ? '正文已核验' : '证据版正文' }}
            </el-tag>
          </div>
          <p v-if="report.qualityWarnings?.length" class="post-market-warning">{{ report.qualityWarnings.join('；') }}</p>
        </div>
        <aside class="post-market-status">
          <span>收盘定性</span>
          <strong>{{ report.marketStatus || marketSnapshot.stance || '待确认' }}</strong>
          <small>{{ marketSnapshot.stanceReason || '18:30 自动生成' }}</small>
        </aside>
      </header>

      <section class="post-market-thesis" aria-label="收盘结论">
        <span>收盘结论</span>
        <p>{{ report.coreView || '当前数据不足，暂不形成确定性结论。' }}</p>
        <small v-if="report.maxRisk">最大风险：{{ report.maxRisk }}</small>
      </section>

      <section class="post-market-section" aria-labelledby="post-market-index-title">
        <div class="post-market-section-head"><div><span>01</span><h2 id="post-market-index-title">大盘</h2></div><p>{{ marketSnapshot.volumeLabel || marketSnapshot.message || '' }}</p></div>
        <div class="market-index-grid">
          <article v-for="item in indexes" :key="item.name" class="market-index-item">
            <span>{{ item.name }}</span><strong>{{ formatCapitalPrice(item.close) }}</strong><em :class="resolveCapitalClass(item.pctChg)">{{ formatCapitalPercent(item.pctChg) }}</em>
          </article>
          <article class="market-index-item"><span>成交额</span><strong>{{ formatCapitalAmount(marketSnapshot.indexVolume) }}</strong><em>{{ marketSnapshot.volumeTrend || '-' }}</em></article>
          <article class="market-index-item"><span>上涨 / 下跌</span><strong>{{ formatInteger(marketSnapshot.breadthUp) }} / {{ formatInteger(marketSnapshot.breadthDown) }}</strong><em>平盘 {{ formatInteger(marketSnapshot.breadthFlat) }}</em></article>
          <article class="market-index-item"><span>涨停 / 跌停</span><strong>{{ formatInteger(marketSnapshot.limitUpCount) }} / {{ formatInteger(marketSnapshot.limitDownCount) }}</strong><em>市场广度</em></article>
        </div>
      </section>

      <section class="post-market-section" aria-labelledby="post-market-sector-title">
        <div class="post-market-section-head"><div><span>02</span><h2 id="post-market-sector-title">板块</h2></div><p>行业与概念分开观察</p></div>
        <div class="board-columns">
          <div v-for="group in [{ title: '行业', rows: industryBoards }, { title: '概念', rows: conceptBoards }]" :key="group.title" class="board-column">
            <h3>{{ group.title }}</h3>
            <el-table v-if="group.rows.length" :data="group.rows" stripe class="post-market-table">
              <el-table-column prop="name" label="板块" min-width="120" />
              <el-table-column label="涨跌幅" width="88" align="right"><template #default="{ row }"><span :class="resolveCapitalClass(row.pctChg)">{{ formatCapitalPercent(row.pctChg) }}</span></template></el-table-column>
              <el-table-column label="主力净流入" min-width="112" align="right"><template #default="{ row }">{{ formatCapitalAmount(row.mainNetInflow ?? row.netInflow) }}</template></el-table-column>
              <el-table-column prop="leadStockName" label="领涨股" min-width="100" />
              <el-table-column label="异动原因" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ boardReason(row) }}</template></el-table-column>
            </el-table>
            <div v-if="group.rows.length" class="post-market-mobile-list">
              <article v-for="row in group.rows" :key="row.code || row.name" class="post-market-card">
                <div><strong>{{ row.name }}</strong><span :class="resolveCapitalClass(row.pctChg)">{{ formatCapitalPercent(row.pctChg) }}</span></div>
                <p>{{ boardReason(row) }}</p><small>主力 {{ formatCapitalAmount(row.mainNetInflow ?? row.netInflow) }} · 领涨 {{ row.leadStockName || '-' }}</small>
              </article>
            </div>
            <el-empty v-if="!group.rows.length" :image-size="52" :description="`暂无${group.title}板块数据`" />
          </div>
        </div>
      </section>

      <section class="post-market-section" aria-labelledby="post-market-mainline-title">
        <div class="post-market-section-head"><div><span>03</span><h2 id="post-market-mainline-title">主线</h2></div><p>以持续性与资金共振识别</p></div>
        <div v-if="mainlines.length" class="mainline-grid">
          <article v-for="(row, index) in mainlines" :key="row.code || row.name" class="mainline-item">
            <span>{{ String(index + 1).padStart(2, '0') }}</span><div><h3>{{ row.name }}</h3><p>{{ boardReason(row) }}</p></div>
            <dl><dt>主线分</dt><dd>{{ row.mainlineScore ?? '-' }}</dd><dt>近 3 日</dt><dd :class="resolveCapitalClass(row.pctChg3d)">{{ formatCapitalPercent(row.pctChg3d) }}</dd></dl>
          </article>
        </div>
        <el-empty v-else :image-size="56" description="今日尚未形成可确认主线" />
      </section>

      <section class="post-market-section" aria-labelledby="post-market-star-title">
        <div class="post-market-section-head"><div><span>04</span><h2 id="post-market-star-title">明星个股</h2></div><p>{{ starStocks.length }} 只</p></div>
        <el-table v-if="starStocks.length" :data="starStocks" stripe class="post-market-table">
          <el-table-column label="股票" min-width="150"><template #default="{ row }"><StockIdentity :security="row" :interactive="true" @select="openStock" /></template></el-table-column>
          <el-table-column label="涨跌幅" width="90" align="right"><template #default="{ row }"><span :class="resolveCapitalClass(row.pctChg)">{{ formatCapitalPercent(row.pctChg) }}</span></template></el-table-column>
          <el-table-column prop="lianban" label="连板" width="72" align="right" />
          <el-table-column label="封板资金" min-width="106" align="right"><template #default="{ row }">{{ formatCapitalAmount(row.sealAmount) }}</template></el-table-column>
          <el-table-column label="主力净流入" min-width="112" align="right"><template #default="{ row }"><span :class="resolveCapitalClass(row.mainNetInflow)">{{ formatCapitalAmount(row.mainNetInflow) }}</span></template></el-table-column>
          <el-table-column label="入选依据" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ stockReasons(row) }}</template></el-table-column>
        </el-table>
        <div v-if="starStocks.length" class="post-market-mobile-list">
          <article v-for="row in starStocks" :key="row.code" class="post-market-card stock-card" @click="openStock(row)">
            <div><StockIdentity :security="row" /><span :class="resolveCapitalClass(row.pctChg)">{{ formatCapitalPercent(row.pctChg) }}</span></div>
            <p>{{ stockReasons(row) }}</p><small>{{ row.lianban ? `${row.lianban} 连板 · ` : '' }}主力 {{ formatCapitalAmount(row.mainNetInflow) }}</small>
          </article>
        </div>
        <el-empty v-if="!starStocks.length" :image-size="56" description="暂无明星个股" />
      </section>

      <section class="post-market-section" aria-labelledby="post-market-lhb-title">
        <div class="post-market-section-head"><div><span>05</span><h2 id="post-market-lhb-title">龙虎榜</h2></div><p>{{ dragonTigerItems.length }} 只</p></div>
        <el-table v-if="dragonTigerItems.length" :data="dragonTigerItems" stripe class="post-market-table">
          <el-table-column label="股票" min-width="150"><template #default="{ row }"><StockIdentity :security="row" :interactive="true" @select="openStock" /></template></el-table-column>
          <el-table-column label="收盘价" width="84" align="right"><template #default="{ row }">{{ formatCapitalPrice(row.closePrice) }}</template></el-table-column>
          <el-table-column label="涨跌幅" width="90" align="right"><template #default="{ row }"><span :class="resolveCapitalClass(row.pctChg)">{{ formatCapitalPercent(row.pctChg) }}</span></template></el-table-column>
          <el-table-column label="换手率" width="88" align="right"><template #default="{ row }">{{ formatCapitalPercent(row.turnoverRate) }}</template></el-table-column>
          <el-table-column label="净买额" min-width="106" align="right"><template #default="{ row }"><span :class="resolveCapitalClass(row.netBuyAmount)">{{ formatCapitalAmount(row.netBuyAmount) }}</span></template></el-table-column>
          <el-table-column label="成交额" min-width="104" align="right"><template #default="{ row }">{{ formatCapitalAmount(row.amount) }}</template></el-table-column>
          <el-table-column prop="reason" label="上榜原因" min-width="220" show-overflow-tooltip />
        </el-table>
        <div v-if="dragonTigerItems.length" class="post-market-mobile-list">
          <article v-for="row in dragonTigerItems" :key="`${row.code}-${row.reason}`" class="post-market-card stock-card" @click="openStock(row)">
            <div><StockIdentity :security="row" /><span :class="resolveCapitalClass(row.pctChg)">{{ formatCapitalPercent(row.pctChg) }}</span></div>
            <p>{{ row.reason || '暂无上榜原因' }}</p><small>净买额 {{ formatCapitalAmount(row.netBuyAmount) }} · 换手 {{ formatCapitalPercent(row.turnoverRate) }}</small>
          </article>
        </div>
        <el-empty v-if="!dragonTigerItems.length" :image-size="56" description="暂无龙虎榜数据" />
      </section>

      <section class="post-market-section" aria-labelledby="post-market-seat-title">
        <div class="post-market-section-head"><div><span>06</span><h2 id="post-market-seat-title">知名游资与活跃席位</h2></div><p>{{ activeSeats.length }} 条已同步记录</p></div>
        <el-table v-if="activeSeats.length" :data="activeSeats" stripe class="post-market-table">
          <el-table-column label="主体 / 席位" min-width="150"><template #default="{ row }"><strong>{{ seatName(row) }}</strong><small v-if="row.actorConfidence">置信度 {{ confidenceLabel(row.actorConfidence) }}</small></template></el-table-column>
          <el-table-column label="相关标的" min-width="110"><template #default="{ row }"><button v-if="row.relatedCode" class="stock-link" type="button" @click="openStock(row)">{{ seatStock(row) }}</button><span v-else>{{ seatStock(row) }}</span></template></el-table-column>
          <el-table-column prop="direction" label="方向" width="84" />
          <el-table-column label="净额" min-width="104" align="right"><template #default="{ row }">{{ formatCapitalAmount(row.netAmount) }}</template></el-table-column>
          <el-table-column label="证据" min-width="220"><template #default="{ row }"><a v-if="row.actorEvidenceUrl || row.url" :href="row.actorEvidenceUrl || row.url" target="_blank" rel="noreferrer">{{ row.title || row.summary || row.source || '查看来源' }}</a><span v-else>{{ row.title || row.summary || row.source || '暂无外部链接' }}</span></template></el-table-column>
        </el-table>
        <div v-if="activeSeats.length" class="post-market-mobile-list">
          <article v-for="(row, index) in activeSeats" :key="`${seatName(row)}-${row.relatedCode}-${index}`" class="post-market-card">
            <div><strong>{{ seatName(row) }}</strong><span>{{ row.direction || '动向' }}</span></div>
            <p>{{ seatStock(row) }} · {{ row.summary || row.title || row.topic || '暂无摘要' }}</p>
            <a v-if="row.actorEvidenceUrl || row.url" :href="row.actorEvidenceUrl || row.url" target="_blank" rel="noreferrer">查看证据</a>
          </article>
        </div>
        <el-empty v-if="!activeSeats.length" :image-size="56" description="暂无可核验的游资与席位动向" />
      </section>

      <section v-if="report.content" class="post-market-content" aria-labelledby="post-market-content-title">
        <h2 id="post-market-content-title">完整复盘</h2><p>{{ report.content }}</p>
      </section>
      <section v-if="report.missingData?.length" class="post-market-missing" aria-label="数据缺口">
        <strong>数据缺口</strong><span v-for="item in report.missingData" :key="item">{{ item }}</span>
      </section>
    </article>

    <section v-if="reportWindowOpen && !loading && !report" class="post-market-empty" aria-label="盘后总结尚未生成">
      <el-empty :image-size="72" description="盘后总结尚未生成">
        <el-button type="primary" :icon="Refresh" :loading="refreshing" @click="refreshReport">立即生成</el-button>
      </el-empty>
    </section>
    <el-empty v-if="reportWindowOpen && !loading && report && !hasReportData" :image-size="72" description="最新交易日盘后数据尚未就绪" />
  </div>
</template>

<style scoped>
.post-market-page { padding-bottom: 72px; }
.post-market-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 26px; padding-bottom: 14px; border-bottom: 1px solid var(--line); }
.post-market-toolbar > div:first-child { display: flex; align-items: baseline; gap: 10px; }
.post-market-toolbar span { color: var(--muted); font-size: 12px; }
.post-market-toolbar strong { color: var(--ink); font-size: 15px; }
.post-market-report { width: min(100%, 1320px); margin: 0 auto; color: var(--ink); }
.post-market-lead { display: grid; grid-template-columns: minmax(0, 1fr) 250px; gap: 40px; padding: 14px 0 30px; border-bottom: 1px solid var(--line-strong); }
.post-market-kicker { margin: 0 0 9px; color: var(--primary); font-size: 11px; font-weight: 700; }
.post-market-lead h1 { margin: 0; font-size: 32px; line-height: 1.2; letter-spacing: 0; }
.post-market-subtitle { margin: 12px 0 0; color: var(--muted); font-size: 14px; }
.post-market-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 14px; margin-top: 18px; color: var(--muted); font-size: 11px; }
.post-market-warning { margin: 10px 0 0; color: #9a6a16; font-size: 12px; overflow-wrap: anywhere; }
.post-market-status { display: flex; flex-direction: column; justify-content: center; min-width: 0; padding-left: 24px; border-left: 1px solid var(--line); }
.post-market-status span, .post-market-status small { color: var(--muted); font-size: 11px; }
.post-market-status strong { margin: 9px 0; font-size: 24px; overflow-wrap: anywhere; }
.post-market-thesis { margin: 24px 0 4px; padding: 18px 20px; border-left: 3px solid var(--primary); background: rgba(22, 105, 201, 0.045); }
.post-market-thesis > span { color: var(--primary); font-size: 11px; font-weight: 700; }
.post-market-thesis p { margin: 8px 0; font-size: 17px; line-height: 1.75; overflow-wrap: anywhere; }
.post-market-thesis small { color: var(--muted); }
.post-market-section { padding: 30px 0; border-bottom: 1px solid var(--line); }
.post-market-section-head { display: flex; align-items: baseline; justify-content: space-between; gap: 18px; margin-bottom: 16px; }
.post-market-section-head > div { display: flex; align-items: baseline; gap: 10px; }
.post-market-section-head span { color: var(--primary); font-size: 11px; font-weight: 700; }
.post-market-section-head h2 { margin: 0; font-size: 20px; letter-spacing: 0; }
.post-market-section-head p { margin: 0; color: var(--muted); font-size: 12px; }
.market-index-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); overflow: hidden; border: 1px solid var(--line); border-radius: var(--radius-sm); }
.market-index-item { min-width: 0; padding: 14px 16px; border-left: 1px solid var(--line); }
.market-index-item:first-child { border-left: 0; }
.market-index-item span, .market-index-item em { display: block; color: var(--muted); font-size: 11px; font-style: normal; }
.market-index-item strong { display: block; margin: 7px 0 4px; font-size: 19px; overflow-wrap: anywhere; }
.board-columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 24px; }
.board-column { min-width: 0; }
.board-column h3 { margin: 0 0 10px; font-size: 14px; }
.post-market-mobile-list { display: none; }
.mainline-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.mainline-item { display: grid; grid-template-columns: 28px minmax(0, 1fr) auto; gap: 12px; padding: 16px; border: 1px solid var(--line); border-radius: var(--radius-sm); }
.mainline-item > span { color: var(--primary); font-weight: 700; }
.mainline-item h3 { margin: 0; font-size: 15px; }
.mainline-item p { margin: 5px 0 0; color: var(--muted); font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.mainline-item dl { display: grid; grid-template-columns: auto auto; align-content: start; gap: 4px 9px; margin: 0; font-size: 11px; }
.mainline-item dt { color: var(--muted); }
.mainline-item dd { margin: 0; text-align: right; }
.post-market-table :deep(.cell) { line-height: 1.45; overflow-wrap: anywhere; }
.post-market-table small { display: block; color: var(--muted); font-size: 10px; }
.post-market-table a, .post-market-card a, .stock-link { color: var(--primary); text-decoration: none; }
.stock-link { padding: 0; border: 0; background: none; cursor: pointer; }
.post-market-content { padding: 32px 0; }
.post-market-content h2 { margin: 0 0 16px; font-size: 20px; }
.post-market-content p { margin: 0; color: #34414d; font-size: 14px; line-height: 1.9; white-space: pre-wrap; overflow-wrap: anywhere; }
.post-market-missing { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; padding: 14px 0; color: var(--muted); font-size: 11px; }
.post-market-missing span { padding: 4px 7px; border: 1px solid var(--line); border-radius: 4px; }
.post-market-empty { padding: 72px 0; }
.up { color: var(--up); }.down { color: var(--down); }.flat { color: var(--slate); }

@media (max-width: 1100px) {
  .market-index-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .market-index-item:nth-child(4) { border-left: 0; border-top: 1px solid var(--line); }
  .market-index-item:nth-child(n + 5) { border-top: 1px solid var(--line); }
}

@media (max-width: 760px) {
  .post-market-page { padding-bottom: 96px; }
  .post-market-toolbar { margin-bottom: 16px; }
  .post-market-toolbar .actions :deep(.el-button) { width: 44px; height: 44px; padding: 0; font-size: 0; }
  .post-market-toolbar .actions :deep(.el-icon) { margin: 0; font-size: 17px; }
  .post-market-lead { grid-template-columns: minmax(0, 1fr); gap: 20px; padding-top: 8px; }
  .post-market-lead h1 { font-size: 28px; }
  .post-market-status { padding: 14px 0 0; border-top: 1px solid var(--line); border-left: 0; }
  .post-market-status strong { font-size: 20px; }
  .post-market-thesis { padding: 15px 16px; }
  .post-market-thesis p { font-size: 15px; }
  .post-market-section { padding: 24px 0; }
  .post-market-section-head { align-items: flex-start; }
  .market-index-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .market-index-item { padding: 12px; border-top: 1px solid var(--line); }
  .market-index-item:nth-child(odd) { border-left: 0; }
  .market-index-item:nth-child(-n + 2) { border-top: 0; }
  .market-index-item:nth-child(4) { border-left: 1px solid var(--line); }
  .market-index-item strong { font-size: 16px; }
  .board-columns, .mainline-grid { grid-template-columns: minmax(0, 1fr); }
  .board-columns { gap: 24px; }
  .post-market-table { display: none; }
  .post-market-mobile-list { display: grid; grid-template-columns: minmax(0, 1fr); gap: 8px; }
  .post-market-card { min-width: 0; padding: 13px; border: 1px solid var(--line); border-radius: var(--radius-sm); background: #fff; }
  .post-market-card > div { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 12px; }
  .post-market-card p { margin: 9px 0 6px; color: #46535f; font-size: 12px; line-height: 1.55; overflow-wrap: anywhere; }
  .post-market-card small { color: var(--muted); font-size: 11px; overflow-wrap: anywhere; }
  .stock-card { cursor: pointer; }
  .mainline-item { grid-template-columns: 24px minmax(0, 1fr); }
  .mainline-item dl { grid-column: 2; grid-template-columns: auto auto auto auto; }
}
</style>
