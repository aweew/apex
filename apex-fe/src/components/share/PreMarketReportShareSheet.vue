<script setup>
import { computed, ref } from 'vue'
import PreMarketHoldingCard from '../PreMarketHoldingCard.vue'
import BrandShareFoot from './BrandShareFoot.vue'
import BrandShareLockup from './BrandShareLockup.vue'

const props = defineProps({
  report: { type: Object, required: true },
  document: { type: Object, required: true },
  generatedTime: { type: String, default: '' },
  sourceLabel: { type: String, default: '' },
  dataLevelLabel: { type: String, default: '' },
})

const rootRef = ref(null)
const tradeDate = computed(() => props.report.tradeDate || props.document.date || '')
const judgement = computed(() => props.document.judgement || props.report.marketJudgement || '')
const sentimentWidth = computed(() => Math.min(Math.max(props.report.sentimentScore || 0, 0), 100))

function section(number) {
  return props.document.sections.find((item) => item.number === number)
}

function cleanLine(line) {
  return String(line || '').replace(/^[-•]\s*/, '').trim()
}

function fieldLine(lines, label) {
  return lines.find((line) => line.startsWith(label)) || ''
}

function splitSentence(text) {
  const sentenceEnd = text.indexOf('。')
  if (sentenceEnd < 0) return { value: text, note: '' }
  return { value: text.slice(0, sentenceEnd), note: text.slice(sentenceEnd + 1) }
}

const capitalSection = computed(() => section('02'))
const volumeInsight = computed(() => splitSentence(fieldLine(capitalSection.value?.lines || [], '量能：').slice(3)))
const sentimentInsight = computed(() => fieldLine(capitalSection.value?.lines || [], '情绪：').slice(3))

const variableSection = computed(() => section('03'))
const variableItems = computed(() => (variableSection.value?.lines || []).map((line) => {
  const parts = cleanLine(line).split(/[｜|]/).map((part) => part.trim()).filter(Boolean)
  if (parts.length < 2 || !/^[SAB]\s*级$/.test(parts[0])) {
    return { level: '外盘', title: cleanLine(line), details: '' }
  }
  return { level: parts[0], title: parts[1], details: parts.slice(2).join(' · ') }
}))

const directionSection = computed(() => section('04'))
const directionItems = computed(() => (directionSection.value?.lines || []).map((line, index) => {
  const matched = cleanLine(line).match(/^(\d+)[.、]\s*([^：:]+)[：:]\s*(.+)$/)
  return matched
    ? { rank: matched[1], name: matched[2], detail: matched[3] }
    : { rank: String(index + 1), name: cleanLine(line), detail: '' }
}))

const holdingSection = computed(() => section('05'))
const holdings = computed(() => {
  const currentSection = holdingSection.value
  return currentSection?.holdings?.length === currentSection?.lines?.length ? currentSection.holdings : []
})

const fallbackSections = computed(() => props.document.sections.filter(
  (item) => !['01', '02', '03', '04', '05', '07', '08'].includes(item.number),
))

const riskSection = computed(() => section('07'))
const riskItems = computed(() => (riskSection.value?.lines || []).map((line) => {
  const text = cleanLine(line)
  const percentMatch = text.match(/(\d+(?:\.\d+)?)%/)
  const percent = percentMatch ? Number.parseFloat(percentMatch[1]) : 0
  return { text, percent, width: Math.min(Math.max(percent, 4), 100) }
}))

const scenarioSection = computed(() => section('08'))
const scenarios = computed(() => (scenarioSection.value?.lines || []).map((line) => {
  const matched = cleanLine(line).match(/^([^：:]+)[：:]\s*(.+)$/)
  return matched ? { label: matched[1], detail: matched[2] } : null
}).filter(Boolean))

function scenarioTone(label) {
  if (label === '偏多') return 'positive'
  if (label === '转谨慎') return 'danger'
  return 'neutral'
}

function getCaptureElement() {
  return rootRef.value
}

defineExpose({ getCaptureElement })
</script>

<template>
  <article ref="rootRef" class="pre-market-share-sheet">
    <header class="sheet-header">
      <BrandShareLockup subtitle="盘前观点" :size="48" />
      <div class="sheet-meta">
        <strong>A 股盘前策略</strong>
        <span v-if="tradeDate">{{ tradeDate }}</span>
      </div>
    </header>

    <div class="header-rule" />

    <section v-if="judgement" class="judgement-band">
      <span>今日判断</span>
      <h1>{{ judgement }}</h1>
    </section>

    <section v-if="document.priority || document.risk" class="decision-grid">
      <div v-if="document.priority" class="decision-cell priority-cell">
        <span>优先方向</span>
        <strong>{{ document.priority }}</strong>
      </div>
      <div v-if="document.risk" class="decision-cell risk-cell">
        <span>最大风险</span>
        <strong>{{ document.risk }}</strong>
      </div>
    </section>

    <div class="market-strip">
      <div v-if="report.marketStatus"><span>市场状态</span><strong>{{ report.marketStatus }}</strong></div>
      <div v-if="report.sentimentScore != null"><span>情绪指数</span><strong>{{ report.sentimentScore }} / 100</strong></div>
      <div v-if="report.portfolioCount || report.holdingCount"><span>组合覆盖</span><strong>{{ report.portfolioCount || 0 }} 组合 · {{ report.holdingCount || 0 }} 持仓</strong></div>
      <div v-if="sourceLabel || dataLevelLabel"><span>研判来源</span><strong>{{ sourceLabel }}<template v-if="sourceLabel && dataLevelLabel"> · </template>{{ dataLevelLabel }}</strong></div>
    </div>

    <section v-if="capitalSection" class="visual-section capital-section">
      <header class="section-title"><span>02</span><h2>资金与情绪</h2></header>
      <div class="capital-grid">
        <div v-if="volumeInsight.value" class="capital-card volume-card">
          <span>成交动能</span><strong>{{ volumeInsight.value }}</strong><p v-if="volumeInsight.note">{{ volumeInsight.note }}</p>
        </div>
        <div v-if="report.sentimentScore != null" class="capital-card sentiment-card">
          <div class="sentiment-head"><span>市场情绪</span><strong>{{ report.sentimentScore }}</strong></div>
          <div class="sentiment-meter" aria-hidden="true"><i :style="{ width: `${sentimentWidth}%` }" /></div>
          <p v-if="sentimentInsight">{{ sentimentInsight }}</p>
        </div>
      </div>
    </section>

    <section v-if="variableSection" class="visual-section">
      <header class="section-title"><span>03</span><h2>关键变量</h2><em>按事件优先级</em></header>
      <div class="variable-grid">
        <article v-for="item in variableItems" :key="item.title" class="variable-card" :class="`level-${item.level.charAt(0).toLowerCase()}`">
          <span>{{ item.level }}</span><strong>{{ item.title }}</strong><p v-if="item.details">{{ item.details }}</p>
        </article>
      </div>
    </section>

    <section v-if="directionSection" class="visual-section">
      <header class="section-title"><span>04</span><h2>今日方向</h2><em>只做开盘验证</em></header>
      <div class="direction-grid">
        <article v-for="item in directionItems" :key="item.rank" class="direction-card">
          <span class="direction-rank">{{ item.rank }}</span><strong>{{ item.name }}</strong><p>{{ item.detail }}</p>
        </article>
      </div>
    </section>

    <section v-if="holdingSection" class="visual-section holding-section">
      <header class="section-title"><span>05</span><h2>持仓提醒</h2><em>{{ holdings.length || holdingSection.lines.length }} 项需关注</em></header>
      <div v-if="holdings.length" class="holding-card-grid">
        <PreMarketHoldingCard v-for="holding in holdings" :key="holding.code" :holding="holding" />
      </div>
      <div v-else class="detail-lines"><p v-for="line in holdingSection.lines" :key="line">{{ line }}</p></div>
    </section>

    <section v-for="section in fallbackSections" :key="section.number" class="visual-section">
      <header class="section-title"><span>{{ section.number }}</span><h2>{{ section.title }}</h2></header>
      <div class="simple-list-grid"><p v-for="line in section.lines" :key="line">{{ cleanLine(line) }}</p></div>
    </section>

    <section v-if="riskSection" class="visual-section risk-section">
      <header class="section-title"><span>07</span><h2>组合风险</h2><em>集中度预警</em></header>
      <div class="risk-grid">
        <article v-for="item in riskItems" :key="item.text" class="risk-item">
          <div><p>{{ item.text }}</p><strong v-if="item.percent">{{ item.percent }}%</strong></div>
          <div class="risk-bar" aria-hidden="true"><i :style="{ width: `${item.width}%` }" /></div>
        </article>
      </div>
    </section>

    <section v-if="scenarioSection" class="visual-section scenario-section">
      <header class="section-title"><span>08</span><h2>开盘验证</h2><em>按盘面切换</em></header>
      <div class="scenario-grid">
        <article v-for="item in scenarios" :key="item.label" class="scenario-card" :class="`is-${scenarioTone(item.label)}`">
          <span>{{ item.label }}</span><p>{{ item.detail }}</p>
        </article>
      </div>
    </section>

    <BrandShareFoot :note="`${tradeDate}${tradeDate ? ' · ' : ''}仅供研究参考 · 不构成投资建议`" />
    <span v-if="generatedTime" class="generated-time">生成于 {{ generatedTime }}</span>
  </article>
</template>

<style scoped>
.pre-market-share-sheet {
  width: 760px;
  box-sizing: border-box;
  padding: 32px 36px 24px;
  color: #1d2939;
  background: #f4f7f9;
  font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans SC", sans-serif;
  letter-spacing: 0;
  overflow: visible;
}

.pre-market-share-sheet :deep(*) { box-sizing: border-box; letter-spacing: 0 !important; }
.sheet-header, .sentiment-head, .risk-item > div:first-child { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.sheet-meta { display: flex; flex-direction: column; align-items: flex-end; gap: 5px; padding-top: 3px; color: #667085; font-size: 12px; font-variant-numeric: tabular-nums; }
.sheet-meta strong { color: #344054; font-size: 13px; }
.header-rule { height: 1px; margin: 20px 0 22px; background: #d6dee7; }
.judgement-band { padding: 2px 0 20px 18px; border-left: 4px solid #315c86; }
.judgement-band span, .decision-cell span, .market-strip span, .capital-card > span { display: block; color: #667085; font-size: 11px; font-weight: 700; }
.judgement-band h1 { margin: 7px 0 0; color: #172b3f; font-size: 25px; font-weight: 760; line-height: 1.45; overflow-wrap: anywhere; }

.decision-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin-bottom: 16px; border: 1px solid #dbe2e8; border-radius: 6px; background: #fff; }
.decision-cell { min-width: 0; padding: 14px 16px 15px; border-right: 1px solid #e2e7ec; }
.decision-cell:last-child { border-right: 0; }
.decision-cell strong { display: block; margin-top: 6px; color: #243443; font-size: 14px; line-height: 1.55; overflow-wrap: anywhere; }
.priority-cell { border-top: 3px solid #28785b; }
.risk-cell { border-top: 3px solid #b5473c; }

.market-strip { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-bottom: 18px; border-block: 1px solid #d9e1e8; background: #e9eff3; }
.market-strip > div { min-width: 0; padding: 10px 12px; border-right: 1px solid #d5dee5; }
.market-strip > div:last-child { border-right: 0; }
.market-strip strong { display: block; margin-top: 4px; color: #263746; font-size: 12px; line-height: 1.4; overflow-wrap: anywhere; }

.visual-section { padding: 18px 0; border-top: 1px solid #dce3e8; break-inside: avoid; }
.section-title { display: flex; align-items: baseline; gap: 9px; margin-bottom: 12px; }
.section-title > span { color: #98a2b3; font-size: 10px; font-variant-numeric: tabular-nums; }
.section-title h2 { margin: 0; color: #243443; font-size: 16px; line-height: 1.4; }
.section-title em { margin-left: auto; color: #8995a1; font-size: 10px; font-style: normal; font-weight: 600; }

.capital-grid, .variable-grid, .holding-card-grid, .risk-grid, .simple-list-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.capital-card, .variable-card, .direction-card, .risk-item, .scenario-card, .simple-list-grid p { min-width: 0; border: 1px solid #dde4e9; border-radius: 6px; background: #fff; }
.capital-card { padding: 14px 15px; }
.capital-card > strong { display: block; margin-top: 5px; color: #1f3447; font-size: 18px; line-height: 1.35; }
.capital-card p { margin: 8px 0 0; color: #667480; font-size: 12px; line-height: 1.55; }
.volume-card { border-left: 3px solid #3b709e; }
.sentiment-head span { color: #667085; font-size: 11px; font-weight: 700; }
.sentiment-head strong { color: #b7791f; font-size: 22px; line-height: 1; font-variant-numeric: tabular-nums; }
.sentiment-meter { height: 7px; margin-top: 10px; overflow: hidden; border-radius: 4px; background: linear-gradient(90deg, #d8ebe2 0 40%, #efe7cf 40% 65%, #f2d8d5 65%); }
.sentiment-meter i { display: block; width: 0; height: 100%; border-right: 3px solid #25384a; }

.variable-card { position: relative; padding: 14px 14px 13px; overflow: hidden; }
.variable-card::before { content: ""; position: absolute; inset: 0 auto 0 0; width: 3px; background: #7a8996; }
.variable-card.level-s::before { background: #b33f39; }
.variable-card.level-a::before { background: #b7791f; }
.variable-card.level-b::before { background: #3d739c; }
.variable-card > span { display: inline-block; padding: 2px 6px; border-radius: 3px; color: #5f6d79; background: #eef1f3; font-size: 10px; font-weight: 750; }
.variable-card.level-s > span { color: #a33732; background: #f8e7e5; }
.variable-card strong { display: block; margin-top: 7px; color: #263746; font-size: 13px; line-height: 1.48; }
.variable-card p { margin: 7px 0 0; color: #697783; font-size: 11px; line-height: 1.55; }

.direction-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; }
.direction-card { position: relative; padding: 35px 13px 13px; overflow: hidden; }
.direction-rank { position: absolute; top: 10px; left: 12px; display: grid; place-items: center; width: 20px; height: 20px; border-radius: 50%; color: #fff; background: #315c86; font-size: 11px; font-weight: 750; font-variant-numeric: tabular-nums; }
.direction-card strong { color: #203548; font-size: 14px; }
.direction-card p { margin: 7px 0 0; color: #667480; font-size: 11px; line-height: 1.55; }

.holding-card-grid { align-items: stretch; }
.holding-card-grid :deep(.holding-card) { height: 100%; break-inside: avoid; }
.holding-card-grid :deep(.holding-head) { padding: 12px 13px 10px; }
.holding-card-grid :deep(.holding-identity strong) { font-size: 15px; }
.holding-card-grid :deep(.holding-metrics) { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.holding-card-grid :deep(.metric) { padding: 8px 9px; }
.holding-card-grid :deep(.radar-row) { padding: 10px 13px 0; }
.holding-card-grid :deep(.trend-text) { margin: 7px 13px 0; font-size: 10px; }
.holding-card-grid :deep(.holding-notes) { margin: 9px 13px 12px; }
.holding-card-grid :deep(.holding-notes > div) { padding-top: 7px; }
.holding-card-grid :deep(.holding-notes dd) { font-size: 11px; line-height: 1.5; }

.simple-list-grid p { margin: 0; padding: 11px 13px; color: #52616e; font-size: 12px; line-height: 1.55; }
.risk-item { padding: 12px 13px; }
.risk-item p { margin: 0; color: #465562; font-size: 11px; line-height: 1.45; }
.risk-item strong { flex: 0 0 auto; color: #a63d37; font-size: 14px; font-variant-numeric: tabular-nums; }
.risk-bar { height: 5px; margin-top: 9px; overflow: hidden; border-radius: 3px; background: #e9edef; }
.risk-bar i { display: block; height: 100%; border-radius: inherit; background: #c65b52; }

.scenario-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; }
.scenario-card { padding: 12px 13px; border-top: 3px solid #7f8b96; }
.scenario-card.is-positive { border-top-color: #28785b; }
.scenario-card.is-danger { border-top-color: #b5473c; }
.scenario-card span { color: #45525e; font-size: 12px; font-weight: 750; }
.scenario-card.is-positive span { color: #22694e; }
.scenario-card.is-danger span { color: #a43d37; }
.scenario-card p { margin: 6px 0 0; color: #687681; font-size: 11px; line-height: 1.55; }

.detail-lines p { margin: 0 0 8px; color: #344054; font-size: 13px; line-height: 1.65; overflow-wrap: anywhere; }
.generated-time { display: block; margin-top: 7px; color: #98a2b3; font-size: 10px; text-align: right; font-variant-numeric: tabular-nums; }
</style>
