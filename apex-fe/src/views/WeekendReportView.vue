<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentCopy, Refresh } from '@element-plus/icons-vue'
import { fetchWeekendMarketReport, refreshWeekendMarketReport } from '../api/weekendReport'

const loading = ref(false)
const refreshing = ref(false)
const report = ref(null)

const reportDate = computed(() => report.value?.reportDate || report.value?.generatedAt?.slice?.(0, 10) || '')
const generatedAt = computed(() => formatDateTime(report.value?.generatedAt))
const periodText = computed(() => {
  const start = report.value?.weekStartDate || report.value?.weekStart
  const end = report.value?.weekEndDate || report.value?.weekEnd
  if (start && end) return `${start} 至 ${end}`
  return '最近完整交易周'
})
const dataAsOf = computed(() => report.value?.dataAsOf || report.value?.marketDataAsOf || report.value?.generatedAt)
const indexPerformances = computed(() => report.value?.indexPerformance || report.value?.indexPerformances || report.value?.indexes || [])
const fridaySnapshot = computed(() => report.value?.fridaySnapshot || report.value?.fridayClose || {})
const weekendNews = computed(() => report.value?.weekendNews || report.value?.news || [])
const marketOpinions = computed(() => report.value?.marketOpinions || report.value?.opinions || [])
const tradingThemes = computed(() => report.value?.tradingThemes || report.value?.themes || [])
const scenarios = computed(() => report.value?.marketScenarios || report.value?.scenarios || [])
const missingData = computed(() => report.value?.missingData || [])
const sourceLabel = computed(() => report.value?.reportSource === 'AI' ? '智能研判' : '规则研判')

function formatDateTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
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

function formatPercent(value) {
  if (value == null || value === '') return '--'
  const number = Number(value)
  if (Number.isNaN(number)) return '--'
  const sign = number > 0 ? '+' : number < 0 ? '−' : ''
  return `${sign}${Math.abs(number).toFixed(2)}%`
}

function directionClass(value) {
  const text = String(value || '').toUpperCase()
  if (text.includes('DOWN') || text.includes('SELL') || text.includes('BEAR') || text.includes('偏弱')) return 'is-down'
  if (text.includes('UP') || text.includes('BUY') || text.includes('BULL') || text.includes('偏强')) return 'is-up'
  return 'is-flat'
}

function scenarioTone(value) {
  const text = String(value || '')
  if (text.includes('强')) return 'is-up'
  if (text.includes('转弱') || text.includes('弱')) return 'is-down'
  return 'is-flat'
}

async function loadReport() {
  loading.value = true
  try {
    const response = await fetchWeekendMarketReport()
    report.value = response.data
  } catch (error) {
    ElMessage.error(error.message || '周末研报加载失败')
  } finally {
    loading.value = false
  }
}

async function refreshReport() {
  refreshing.value = true
  try {
    const response = await refreshWeekendMarketReport()
    report.value = response.data
    ElMessage.success('周末研报已重新生成')
  } catch (error) {
    ElMessage.error(error.message || '周末研报生成失败')
  } finally {
    refreshing.value = false
  }
}

async function copyReport() {
  if (!report.value?.content) {
    ElMessage.warning('暂无可复制的研报')
    return
  }
  try {
    await navigator.clipboard.writeText(report.value.content)
    ElMessage.success('研报已复制')
  } catch {
    ElMessage.error('复制失败，请检查浏览器剪贴板权限')
  }
}

onMounted(loadReport)
</script>

<template>
  <div class="page weekend-report-page" v-loading="loading">
    <header class="weekend-toolbar">
      <div class="weekend-toolbar-copy">
        <span>周末消息面</span>
        <strong v-if="reportDate">{{ reportDate }}</strong>
      </div>
      <div class="actions weekend-actions">
        <el-tooltip content="复制完整研报" placement="bottom">
          <el-button :icon="DocumentCopy" :disabled="!report?.content" @click="copyReport">复制</el-button>
        </el-tooltip>
        <el-tooltip content="使用最新数据重新生成" placement="bottom">
          <el-button type="primary" :icon="Refresh" :loading="refreshing" @click="refreshReport">更新</el-button>
        </el-tooltip>
      </div>
    </header>

    <article v-if="report" class="weekend-report-article" aria-label="周末消息面专题研报">
      <header class="weekend-report-lead">
        <div>
          <p class="weekend-kicker">APEX WEEKEND MARKET RESEARCH</p>
          <h1>周末消息面专题</h1>
          <p class="weekend-subtitle">上周走势与周末信息，整理成下周可验证的交易线索</p>
          <div class="weekend-meta-strip" aria-label="研报数据时效">
            <span>统计周期 {{ periodText }}</span>
            <span v-if="dataAsOf">数据截至 {{ formatDateTime(dataAsOf) }}</span>
            <span v-if="generatedAt">生成于 {{ generatedAt }}</span>
            <el-tag size="small" effect="plain" :type="dataLevelType(report.dataLevel)">
              {{ dataLevelLabel(report.dataLevel) }}
            </el-tag>
            <span>{{ sourceLabel }}</span>
          </div>
        </div>
        <aside class="weekend-lead-status" aria-label="报告状态">
          <span>本周研判</span>
          <strong>{{ report.marketStatus || '待确认' }}</strong>
          <small>周日 21:00 固化</small>
        </aside>
      </header>

      <section class="weekend-thesis" aria-label="核心观点">
        <span>核心观点</span>
        <p>{{ report.coreView || report.coreOpinion || report.judgement || '本期核心观点暂未生成。' }}</p>
      </section>

      <dl v-if="report.maxRisk || report.risk" class="weekend-decision-lines">
        <div>
          <dt>最大风险</dt>
          <dd>{{ report.maxRisk || report.risk }}</dd>
        </div>
        <div v-if="missingData.length">
          <dt>数据缺口</dt>
          <dd>{{ missingData.join('、') }}</dd>
        </div>
      </dl>

      <section class="weekend-section" aria-labelledby="weekend-trend-title">
        <div class="weekend-section-head">
          <div><span>01</span><h2 id="weekend-trend-title">上周走势</h2></div>
          <p>主要指数一周表现与周五收盘方向</p>
        </div>
        <div v-if="indexPerformances.length" class="index-performance-grid">
          <div v-for="item in indexPerformances" :key="item.code || item.name" class="index-performance-item">
            <span>{{ item.name || item.code }}</span>
            <strong :class="directionClass(item.weeklyReturn ?? item.weekPctChg ?? item.weekChange ?? item.pctChg)">
              {{ formatPercent(item.weeklyReturn ?? item.weekPctChg ?? item.weekChange ?? item.pctChg) }}
            </strong>
            <small>周五 {{ formatPercent(item.fridayPctChg ?? item.lastPctChg) }} · {{ item.weekEndClose ?? item.close ?? '--' }}</small>
          </div>
        </div>
        <p v-else class="weekend-empty">指数数据暂未获取</p>
      </section>

      <section class="weekend-section" aria-labelledby="weekend-friday-title">
        <div class="weekend-section-head">
          <div><span>02</span><h2 id="weekend-friday-title">周五收盘</h2></div>
          <p>{{ fridaySnapshot.asOf || fridaySnapshot.tradeDate || report.lastTradeDate || '最后交易日' }}</p>
        </div>
        <div class="friday-snapshot-grid">
          <div><span>成交额</span><strong>{{ fridaySnapshot.volumeText || fridaySnapshot.indexVolume || fridaySnapshot.volume || '--' }}</strong><small v-if="fridaySnapshot.volumeLabel">{{ fridaySnapshot.volumeLabel }}</small></div>
          <div><span>涨 / 平 / 跌</span><strong>{{ fridaySnapshot.breadthUp ?? fridaySnapshot.up ?? '--' }} / {{ fridaySnapshot.breadthFlat ?? fridaySnapshot.flat ?? '--' }} / {{ fridaySnapshot.breadthDown ?? fridaySnapshot.down ?? '--' }}</strong></div>
          <div><span>涨停 / 跌停</span><strong>{{ fridaySnapshot.limitUp ?? fridaySnapshot.limitUpCount ?? '--' }} / {{ fridaySnapshot.limitDown ?? fridaySnapshot.limitDownCount ?? '--' }}</strong></div>
          <div><span>市场状态</span><strong>{{ fridaySnapshot.stance || fridaySnapshot.marketStatus || fridaySnapshot.status || '待确认' }}</strong></div>
        </div>
        <p v-if="fridaySnapshot.hotThemes?.length" class="friday-hot-themes">
          <span>热点</span>
          <b v-for="theme in fridaySnapshot.hotThemes" :key="theme">{{ theme }}</b>
        </p>
      </section>

      <section class="weekend-section" aria-labelledby="weekend-news-title">
        <div class="weekend-section-head">
          <div><span>03</span><h2 id="weekend-news-title">周末消息</h2></div>
          <p>周五 15:00 至周日 21:00 · 已核验来源</p>
        </div>
        <div v-if="weekendNews.length" class="evidence-list news-list">
          <article v-for="item in weekendNews" :key="item.id || item.sourceId || item.title" class="evidence-row">
            <div class="evidence-row-main">
              <h3>{{ item.title }}</h3>
              <p v-if="item.summary || item.impact">{{ item.summary || item.impact }}</p>
              <div class="evidence-meta">
                <span>{{ item.source || '公开资讯' }}</span>
                <time>{{ formatDateTime(item.publishedAt || item.publishTime) }}</time>
                <span v-if="item.sentiment">{{ item.sentiment }}</span>
                <span v-if="item.topic || item.theme">{{ item.topic || item.theme }}</span>
                <span v-if="item.code || item.relatedCodes">{{ item.code || item.relatedCodes }}</span>
                <span v-if="!item.url && item.externalId">来源索引 {{ item.source }}#{{ item.externalId }}</span>
              </div>
            </div>
            <a v-if="item.url || item.link" :href="item.url || item.link" target="_blank" rel="noreferrer">原文 ↗</a>
          </article>
        </div>
        <p v-else class="weekend-empty">周末窗口暂无已核验消息</p>
      </section>

      <section class="weekend-section" aria-labelledby="weekend-opinions-title">
        <div class="weekend-section-head">
          <div><span>04</span><h2 id="weekend-opinions-title">机构与大 V 观点</h2></div>
          <p>机构、活跃席位与已核验公开观点</p>
        </div>
        <div v-if="marketOpinions.length" class="evidence-list opinion-list">
          <article v-for="item in marketOpinions" :key="item.id || `${item.subject}-${item.publishedAt}`" class="evidence-row">
            <div class="evidence-row-main">
              <div class="opinion-heading"><h3>{{ item.relatedName || item.topic || item.relatedCode || '市场观点' }}</h3><span :class="directionClass(item.direction)">{{ item.direction || '中性' }}</span></div>
              <p class="opinion-thesis"><strong>{{ item.subjectName || item.subject || item.name }}</strong><span>{{ item.title }}</span></p>
              <p v-if="item.summary || item.content">{{ item.summary || item.content }}</p>
              <div class="evidence-meta"><span>{{ item.opinionType || item.type || '公开观点' }}</span><span v-if="item.source">{{ item.source }}</span><time>{{ formatDateTime(item.publishedAt || item.publishTime) }}</time></div>
            </div>
            <a v-if="item.url || item.link" :href="item.url || item.link" target="_blank" rel="noreferrer">原文 ↗</a>
          </article>
        </div>
        <p v-else class="weekend-empty">本期暂无可引用的机构或大 V 观点</p>
      </section>

      <section class="weekend-section" aria-labelledby="weekend-themes-title">
        <div class="weekend-section-head">
          <div><span>05</span><h2 id="weekend-themes-title">下周交易主线</h2></div>
          <p>每条主线都必须能被确认，也必须有失效条件</p>
        </div>
        <div v-if="tradingThemes.length" class="theme-list">
          <article v-for="(item, index) in tradingThemes" :key="item.name || item.title || index" class="theme-row">
            <div class="theme-index">0{{ index + 1 }}</div>
            <div class="theme-copy"><h3>{{ item.theme || item.name || item.title }}</h3><p v-if="item.summary || item.reason || item.relatedCodes">{{ item.summary || item.reason || item.relatedCodes }}</p></div>
            <dl><div><dt>催化</dt><dd>{{ item.catalyst || item.catalysts || '--' }}</dd></div><div><dt>确认</dt><dd>{{ item.confirmation || item.confirm || '--' }}</dd></div><div><dt>失效</dt><dd>{{ item.invalidation || item.invalid || '--' }}</dd></div></dl>
          </article>
        </div>
        <p v-else class="weekend-empty">下周主线暂未生成</p>
      </section>

      <section class="weekend-section weekend-scenarios" aria-labelledby="weekend-scenarios-title">
        <div class="weekend-section-head">
          <div><span>06</span><h2 id="weekend-scenarios-title">市场剧本与风险</h2></div>
          <p>强势、震荡、转弱三种路径</p>
        </div>
        <div v-if="scenarios.length" class="scenario-grid">
          <article v-for="item in scenarios" :key="item.scenario || item.name || item.title" class="scenario-card" :class="scenarioTone(item.scenario || item.name || item.title)">
            <div class="scenario-card-head"><h3>{{ item.scenario || item.name || item.title }}</h3><span v-if="item.probability">{{ item.probability }}</span></div>
            <p>{{ item.description || item.summary || item.action || '--' }}</p>
            <small v-if="item.trigger || item.confirmation">确认：{{ item.trigger || item.confirmation }}</small>
          </article>
        </div>
        <p v-else class="weekend-empty">市场剧本暂未生成</p>
      </section>

      <footer class="weekend-footnote"><span>{{ sourceLabel }}</span><span>仅供研究，不构成投资建议</span></footer>
      <div v-if="report.content" class="sr-only" aria-label="可复制研报正文">{{ report.content }}</div>
    </article>

    <div v-else-if="!loading" class="page-empty">
      <h3>周末研报尚未生成</h3>
      <el-button type="primary" :icon="Refresh" :loading="refreshing" @click="refreshReport">生成研报</el-button>
    </div>
  </div>
</template>

<style scoped>
.weekend-report-page { padding-bottom: 72px; }
.weekend-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 20px; min-height: 48px; padding-bottom: 16px; border-bottom: 1px solid #e2e6e9; }
.weekend-toolbar-copy { display: flex; align-items: baseline; gap: 10px; color: #7b8790; font-size: 12px; }
.weekend-toolbar-copy span { color: #314558; font-size: 14px; font-weight: 700; }
.weekend-toolbar-copy strong { font-weight: 500; font-variant-numeric: tabular-nums; }
.weekend-actions { display: flex; gap: 8px; }
.weekend-report-article { width: min(100%, 1120px); margin: 0 auto; color: #26323c; }
.weekend-report-lead { display: grid; grid-template-columns: minmax(0, 1fr) 170px; gap: 36px; padding: 54px 0 28px; }
.weekend-kicker { margin: 0 0 12px; color: #3977a6; font-size: 11px; font-weight: 750; letter-spacing: .08em; }
.weekend-report-lead h1 { margin: 0; color: #142733; font-size: 40px; font-weight: 780; line-height: 1.2; letter-spacing: 0; overflow-wrap: anywhere; }
.weekend-subtitle { margin: 9px 0 0; color: #667782; font-size: 14px; line-height: 1.6; }
.weekend-meta-strip { display: flex; align-items: center; flex-wrap: wrap; gap: 7px 14px; margin-top: 16px; color: #647580; font-size: 12px; line-height: 1.5; }
.weekend-meta-strip span { overflow-wrap: anywhere; }
.weekend-lead-status { align-self: start; padding: 5px 0 0 20px; border-left: 1px solid #d9e1e5; }
.weekend-lead-status span, .weekend-lead-status strong, .weekend-lead-status small { display: block; }
.weekend-lead-status span { color: #7d8992; font-size: 11px; font-weight: 700; }
.weekend-lead-status strong { margin-top: 10px; color: #1d3341; font-size: 24px; line-height: 1.3; overflow-wrap: anywhere; }
.weekend-lead-status small { margin-top: 8px; color: #929ca3; font-size: 11px; }
.weekend-thesis { margin: 0 0 26px; padding: 15px 18px; border-left: 4px solid #b47b27; background: #f6f3ed; }
.weekend-thesis span { display: block; margin-bottom: 7px; color: #88601f; font-size: 12px; font-weight: 700; }
.weekend-thesis p { margin: 0; color: #273944; font-size: 17px; font-weight: 650; line-height: 1.65; overflow-wrap: anywhere; }
.weekend-decision-lines { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin: 0 0 12px; border-block: 1px solid #dbe2e6; }
.weekend-decision-lines > div { min-width: 0; padding: 14px 20px 15px 0; }
.weekend-decision-lines > div + div { padding-left: 20px; border-left: 1px solid #e3e7ea; }
.weekend-decision-lines dt { color: #a14d47; font-size: 10px; font-weight: 720; }
.weekend-decision-lines dd { margin: 6px 0 0; color: #273640; font-size: 14px; font-weight: 650; line-height: 1.5; overflow-wrap: anywhere; }
.weekend-section { padding: 30px 0 34px; border-top: 1px solid #e2e8ec; }
.weekend-section-head { display: flex; align-items: baseline; justify-content: space-between; gap: 20px; margin-bottom: 18px; }
.weekend-section-head > div { display: flex; align-items: baseline; gap: 10px; }
.weekend-section-head span { color: #3977a6; font-size: 11px; font-weight: 750; font-variant-numeric: tabular-nums; }
.weekend-section-head h2 { margin: 0; color: #1c303d; font-size: 22px; font-weight: 740; }
.weekend-section-head p { margin: 0; color: #87939b; font-size: 12px; text-align: right; }
.index-performance-grid, .friday-snapshot-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; }
.index-performance-item, .friday-snapshot-grid > div { min-width: 0; padding: 14px 12px; border: 1px solid #dfe6eb; background: #fbfcfd; }
.index-performance-item span, .friday-snapshot-grid span { display: block; color: #657680; font-size: 12px; overflow-wrap: anywhere; }
.index-performance-item strong { display: block; margin-top: 8px; font-size: 19px; font-variant-numeric: tabular-nums; }
.index-performance-item small { display: block; margin-top: 7px; color: #8a969e; font-size: 10px; line-height: 1.4; overflow-wrap: anywhere; }
.friday-snapshot-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
.friday-snapshot-grid strong { display: block; margin-top: 8px; color: #273944; font-size: 17px; line-height: 1.35; overflow-wrap: anywhere; }
.friday-snapshot-grid small { display: block; margin-top: 5px; color: #89959c; font-size: 10px; overflow-wrap: anywhere; }
.friday-hot-themes { display: flex; flex-wrap: wrap; gap: 8px; margin: 14px 0 0; color: #657680; font-size: 12px; }
.friday-hot-themes b { padding: 3px 8px; color: #34566d; font-weight: 650; border: 1px solid #d7e2e8; background: #f7fafb; }
.evidence-list { border-top: 1px solid #e2e8ec; }
.evidence-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; min-width: 0; padding: 15px 0; border-bottom: 1px solid #e8edf0; }
.evidence-row-main { min-width: 0; flex: 1; }
.evidence-row h3 { margin: 0; color: #253a47; font-size: 15px; font-weight: 680; line-height: 1.45; overflow-wrap: anywhere; }
.evidence-row p { margin: 6px 0 0; color: #556873; font-size: 13px; line-height: 1.55; overflow-wrap: anywhere; }
.evidence-row > a { flex: 0 0 auto; padding-top: 2px; color: #3977a6; font-size: 12px; text-decoration: none; white-space: nowrap; }
.evidence-meta { display: flex; flex-wrap: wrap; gap: 6px 14px; margin-top: 8px; color: #929da4; font-size: 11px; }
.opinion-heading { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; }
.opinion-heading > span { padding: 2px 6px; font-size: 11px; font-weight: 680; border: 1px solid currentColor; }
.opinion-thesis { display: flex; flex-wrap: wrap; gap: 4px 10px; }
.opinion-thesis strong { color: #314957; font-weight: 700; }
.theme-list { border-top: 1px solid #e2e8ec; }
.theme-row { display: grid; grid-template-columns: 42px minmax(160px, .9fr) minmax(0, 1.7fr); gap: 18px; padding: 18px 0; border-bottom: 1px solid #e8edf0; }
.theme-index { color: #3977a6; font-size: 12px; font-weight: 740; font-variant-numeric: tabular-nums; }
.theme-copy h3 { margin: 0; color: #253a47; font-size: 16px; overflow-wrap: anywhere; }
.theme-copy p { margin: 6px 0 0; color: #647680; font-size: 13px; line-height: 1.5; overflow-wrap: anywhere; }
.theme-row dl { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 0; }
.theme-row dt { color: #3977a6; font-size: 10px; font-weight: 720; }
.theme-row dd { margin: 5px 0 0; color: #4e626e; font-size: 12px; line-height: 1.5; overflow-wrap: anywhere; }
.scenario-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.scenario-card { min-width: 0; padding: 16px; border-top: 3px solid #8d9aa2; background: #f7f9fa; }
.scenario-card.is-up { border-top-color: #b05d3c; background: #fbf5f1; }
.scenario-card.is-down { border-top-color: #3a7a62; background: #f3f8f5; }
.scenario-card-head { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.scenario-card h3 { margin: 0; color: #253a47; font-size: 15px; overflow-wrap: anywhere; }
.scenario-card-head span { color: #7e8b92; font-size: 11px; white-space: nowrap; }
.scenario-card p { margin: 9px 0 0; color: #586c76; font-size: 13px; line-height: 1.55; overflow-wrap: anywhere; }
.scenario-card small { display: block; margin-top: 10px; color: #7e8b92; font-size: 11px; line-height: 1.45; overflow-wrap: anywhere; }
.is-up { color: #bb5149 !important; }
.is-down { color: #287959 !important; }
.is-flat { color: #6b7780 !important; }
.weekend-empty { margin: 0; padding: 14px 0; color: #929da4; font-size: 13px; }
.weekend-footnote { display: flex; flex-wrap: wrap; gap: 8px 18px; padding: 18px 0 0; color: #8a949c; font-size: 11px; }

@media (max-width: 760px) {
  .weekend-toolbar { align-items: stretch; flex-direction: column; gap: 12px; }
  .weekend-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .weekend-actions :deep(.el-button) { width: 100%; min-width: 0; margin: 0; }
  .weekend-report-lead { grid-template-columns: minmax(0, 1fr) 130px; gap: 24px; padding: 38px 0 24px; }
  .weekend-report-lead h1 { font-size: 32px; }
  .weekend-thesis p { font-size: 16px; }
  .index-performance-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .friday-snapshot-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .theme-row { grid-template-columns: 34px minmax(0, 1fr); gap: 10px 14px; }
  .theme-row dl { grid-column: 2; grid-template-columns: minmax(0, 1fr); gap: 8px; }
  .scenario-grid { grid-template-columns: minmax(0, 1fr); }
}

@media (max-width: 520px) {
  .weekend-report-lead { grid-template-columns: minmax(0, 1fr); gap: 18px; }
  .weekend-lead-status { padding: 12px 0 0; border-top: 1px solid #d9e1e5; border-left: 0; }
  .weekend-lead-status strong { margin-top: 5px; font-size: 21px; }
  .weekend-section-head { align-items: flex-start; flex-direction: column; gap: 6px; }
  .weekend-section-head p { text-align: left; }
  .index-performance-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .evidence-row { gap: 10px; }
  .evidence-row > a { font-size: 11px; }
  .weekend-meta-strip { gap: 5px 10px; }
}
</style>
