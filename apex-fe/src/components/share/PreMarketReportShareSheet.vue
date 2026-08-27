<script setup>
import { computed, ref } from 'vue'
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
const detailSections = computed(() => props.document.sections.filter((section) => section.number !== '01'))

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
      <div v-if="report.marketStatus">
        <span>市场状态</span>
        <strong>{{ report.marketStatus }}</strong>
      </div>
      <div v-if="report.sentimentScore != null">
        <span>情绪指数</span>
        <strong>{{ report.sentimentScore }} / 100</strong>
      </div>
      <div v-if="report.portfolioCount || report.holdingCount">
        <span>组合覆盖</span>
        <strong>{{ report.portfolioCount || 0 }} 组合 · {{ report.holdingCount || 0 }} 持仓</strong>
      </div>
      <div v-if="sourceLabel || dataLevelLabel">
        <span>研判来源</span>
        <strong>{{ sourceLabel }}<template v-if="sourceLabel && dataLevelLabel"> · </template>{{ dataLevelLabel }}</strong>
      </div>
    </div>

    <div class="detail-sections">
      <section
        v-for="section in detailSections"
        :key="section.number"
        class="detail-section"
        :data-section="section.number"
      >
        <header>
          <span>{{ section.number }}</span>
          <h2>{{ section.title }}</h2>
        </header>
        <div class="detail-lines">
          <p v-for="line in section.lines" :key="line">{{ line }}</p>
        </div>
      </section>
    </div>

    <BrandShareFoot :note="`${tradeDate}${tradeDate ? ' · ' : ''}仅供研究参考 · 不构成投资建议`" />
    <span v-if="generatedTime" class="generated-time">生成于 {{ generatedTime }}</span>
  </article>
</template>

<style scoped>
.pre-market-share-sheet {
  width: 760px;
  box-sizing: border-box;
  padding: 34px 38px 26px;
  color: #1d2939;
  background: #f7f9fb;
  font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans SC", sans-serif;
  letter-spacing: 0;
  overflow: visible;
}

.pre-market-share-sheet :deep(*) {
  box-sizing: border-box;
  letter-spacing: 0 !important;
}

.sheet-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.sheet-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  padding-top: 3px;
  color: #667085;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.sheet-meta strong {
  color: #344054;
  font-size: 13px;
}

.header-rule {
  height: 1px;
  margin: 22px 0 24px;
  background: #d6dee7;
}

.judgement-band {
  padding: 0 0 22px 18px;
  border-left: 4px solid #315c86;
}

.judgement-band span,
.decision-cell span,
.market-strip span {
  display: block;
  color: #667085;
  font-size: 11px;
  font-weight: 700;
}

.judgement-band h1 {
  margin: 7px 0 0;
  color: #172b3f;
  font-size: 25px;
  font-weight: 760;
  line-height: 1.48;
  overflow-wrap: anywhere;
}

.decision-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(0, 1fr));
  margin-bottom: 18px;
  border-block: 1px solid #d9e1e8;
  background: #ffffff;
}

.decision-cell {
  min-width: 0;
  padding: 16px 18px 17px;
  border-right: 1px solid #e2e7ec;
}

.decision-cell:last-child {
  border-right: 0;
}

.decision-cell strong {
  display: block;
  margin-top: 7px;
  color: #243443;
  font-size: 15px;
  line-height: 1.62;
  overflow-wrap: anywhere;
}

.priority-cell {
  border-top: 3px solid #28785b;
}

.risk-cell {
  border-top: 3px solid #b5473c;
}

.market-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  margin-bottom: 24px;
  border-block: 1px solid #d9e1e8;
  background: #edf2f6;
}

.market-strip > div {
  min-width: 0;
  padding: 12px 14px;
  border-right: 1px solid #d9e1e8;
}

.market-strip > div:last-child {
  border-right: 0;
}

.market-strip strong {
  display: block;
  margin-top: 5px;
  color: #263746;
  font-size: 13px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.detail-section {
  display: grid;
  grid-template-columns: 138px minmax(0, 1fr);
  gap: 24px;
  padding: 20px 2px;
  border-bottom: 1px solid #dfe5eb;
  break-inside: avoid;
}

.detail-section:first-child {
  border-top: 1px solid #dfe5eb;
}

.detail-section > header {
  display: flex;
  align-items: baseline;
  gap: 9px;
}

.detail-section > header span {
  color: #98a2b3;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.detail-section h2 {
  margin: 0;
  color: #243443;
  font-size: 16px;
  line-height: 1.45;
}

.detail-lines p {
  margin: 0 0 9px;
  color: #344054;
  font-size: 14px;
  line-height: 1.72;
  overflow-wrap: anywhere;
}

.detail-lines p:last-child {
  margin-bottom: 0;
}

.detail-section[data-section='03'],
.detail-section[data-section='04'],
.detail-section[data-section='05'],
.detail-section[data-section='07'] {
  background: #f2f6f9;
}

.generated-time {
  display: block;
  margin-top: 8px;
  color: #98a2b3;
  font-size: 10px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
