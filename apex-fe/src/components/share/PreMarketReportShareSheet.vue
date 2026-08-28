<script setup>
import { computed, ref } from 'vue'
import { parseHoldingLine } from '../../utils/preMarketReport'
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

function cleanLine(line) {
  return String(line || '').replace(/^[-•]\s*/, '').trim()
}

function lineParts(line) {
  const text = cleanLine(line)
  const separatorIndex = text.search(/[：｜|]/)
  if (separatorIndex < 0) return { lead: '', detail: text }
  return {
    lead: text.slice(0, separatorIndex).trim(),
    detail: text.slice(separatorIndex + 1).trim(),
  }
}

function narrativeLines(section) {
  return section.lines.filter((line) => !parseHoldingLine(line))
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

    <div class="headline-block">
      <h1>{{ document.title }}</h1>
      <div v-if="judgement" class="thesis-block">
        <span>核心观点</span>
        <p>{{ judgement }}</p>
      </div>
    </div>

    <section v-if="document.priority || document.risk" class="decision-grid">
      <div v-if="document.priority">
        <span>优先方向</span>
        <strong>{{ document.priority }}</strong>
      </div>
      <div v-if="document.risk">
        <span>最大风险</span>
        <strong>{{ document.risk }}</strong>
      </div>
    </section>

    <section
      v-for="section in document.sections"
      :key="section.number"
      class="report-section"
      :class="`section-${section.number}`"
    >
      <header class="section-title">
        <span>{{ section.number }}</span>
        <h2>{{ section.title }}</h2>
      </header>

      <div v-if="section.holdings?.length" class="holding-card-grid">
        <PreMarketHoldingCard v-for="holding in section.holdings" :key="holding.code" :holding="holding" />
      </div>

      <div
        v-if="narrativeLines(section).length"
        class="section-lines"
        :class="{ 'scenario-grid': section.number === '05' }"
      >
        <p v-for="line in narrativeLines(section)" :key="line">
          <strong v-if="lineParts(line).lead">{{ lineParts(line).lead }}</strong>
          <span>{{ lineParts(line).detail }}</span>
        </p>
      </div>
    </section>

    <div class="sheet-meta-foot">
      <span>{{ sourceLabel }} · {{ dataLevelLabel }}</span>
      <span v-if="generatedTime">生成于 {{ generatedTime }}</span>
    </div>
    <BrandShareFoot :note="`${tradeDate}${tradeDate ? ' · ' : ''}仅供研究参考 · 不构成投资建议`" />
  </article>
</template>

<style scoped>
.pre-market-share-sheet {
  width: 760px;
  box-sizing: border-box;
  padding: 32px 38px 24px;
  overflow: visible;
  color: #24323c;
  background: #f8fafb;
  font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans SC", sans-serif;
  letter-spacing: 0;
}

.pre-market-share-sheet :deep(*) {
  box-sizing: border-box;
  letter-spacing: 0 !important;
}

.sheet-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid #d8dfe4;
}

.sheet-meta {
  display: flex;
  align-items: flex-end;
  flex-direction: column;
  gap: 5px;
  padding-top: 3px;
  color: #7b8790;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.sheet-meta strong {
  color: #344754;
  font-size: 12px;
}

.headline-block {
  padding: 28px 0 24px;
}

.headline-block h1 {
  margin: 0;
  color: #172733;
  font-size: 28px;
  font-weight: 760;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.thesis-block {
  margin-top: 20px;
  padding-left: 15px;
  border-left: 4px solid #b7791f;
}

.thesis-block span,
.decision-grid span {
  display: block;
  color: #7b8790;
  font-size: 10px;
  font-weight: 700;
}

.thesis-block p {
  margin: 6px 0 0;
  color: #2e3d47;
  font-size: 15px;
  font-weight: 650;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.decision-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-block: 1px solid #dbe1e5;
}

.decision-grid > div {
  min-width: 0;
  padding: 13px 16px 14px 0;
}

.decision-grid > div + div {
  padding-left: 18px;
  border-left: 1px solid #e0e5e8;
}

.decision-grid strong {
  display: block;
  margin-top: 5px;
  color: #2a3944;
  font-size: 12px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.report-section {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  gap: 22px;
  padding: 19px 0;
  border-bottom: 1px solid #dfe4e7;
  break-inside: avoid;
}

.section-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.section-title > span {
  color: #9aa3aa;
  font-size: 9px;
  font-variant-numeric: tabular-nums;
}

.section-title h2 {
  margin: 0;
  color: #273944;
  font-size: 14px;
  line-height: 1.4;
}

.section-lines {
  min-width: 0;
}

.section-lines p {
  display: grid;
  grid-template-columns: minmax(72px, auto) minmax(0, 1fr);
  gap: 10px;
  margin: 0;
  padding: 6px 0;
  color: #4d5b65;
  font-size: 11px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.section-lines p + p {
  border-top: 1px solid #edf0f2;
}

.section-lines strong {
  color: #263b49;
  font-weight: 720;
}

.section-03 .section-lines strong {
  color: #8b5d18;
}

.holding-card-grid {
  grid-column: 2;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
}

.holding-card-grid + .section-lines {
  grid-column: 2;
}

.holding-card-grid :deep(.holding-card) {
  height: 100%;
  break-inside: avoid;
}

.holding-card-grid :deep(.holding-head) {
  padding: 11px 12px 9px;
}

.holding-card-grid :deep(.holding-identity strong) {
  font-size: 14px;
}

.holding-card-grid :deep(.metric) {
  padding: 7px 8px;
}

.holding-card-grid :deep(.radar-row) {
  padding: 9px 12px 0;
}

.holding-card-grid :deep(.trend-text) {
  margin: 6px 12px 0;
  font-size: 9px;
}

.holding-card-grid :deep(.holding-notes) {
  margin: 8px 12px 11px;
}

.holding-card-grid :deep(.holding-notes dd) {
  font-size: 10px;
  line-height: 1.45;
}

.scenario-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.scenario-grid p {
  display: block;
  padding: 10px;
  border-top: 3px solid #87929a;
  background: #fff;
}

.scenario-grid p:first-child {
  border-top-color: #2d7a59;
}

.scenario-grid p:last-child {
  border-top-color: #b44e45;
}

.scenario-grid strong,
.scenario-grid span {
  display: block;
}

.scenario-grid span {
  margin-top: 5px;
}

.sheet-meta-foot {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 0 2px;
  color: #8d979e;
  font-size: 9px;
  font-variant-numeric: tabular-nums;
}
</style>
