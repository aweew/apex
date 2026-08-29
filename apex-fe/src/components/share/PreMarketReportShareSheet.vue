<script setup>
import { computed, ref } from 'vue'
import PreMarketReportSections from '../PreMarketReportSections.vue'
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
const reportTitle = computed(() => props.document.title.replace(/^今日投资机会[｜|]\s*/, '') || '今日投资机会')
const shareSections = computed(() => props.document.sections.filter((section) => section.number !== '02'))

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
      <div class="headline-main">
        <span>今日策略判断</span>
        <h1>{{ reportTitle }}</h1>
      </div>
      <div v-if="report.sentimentScore != null" class="sentiment-block">
        <span>市场温度</span>
        <strong>{{ report.sentimentScore }}</strong>
        <i aria-hidden="true"><b :style="{ width: `${report.sentimentScore}%` }" /></i>
        <em>{{ report.marketStatus }}</em>
      </div>
    </div>

    <div v-if="judgement" class="thesis-block">
      <span>核心观点</span>
      <p>{{ judgement }}</p>
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

    <PreMarketReportSections :sections="shareSections" compact />

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
  padding: 30px 36px 22px;
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
  display: grid;
  grid-template-columns: minmax(0, 1fr) 104px;
  gap: 28px;
  padding: 25px 0 20px;
}

.headline-main > span {
  display: block;
  margin-bottom: 8px;
  color: #3977a6;
  font-size: 9px;
  font-weight: 750;
}

.headline-block h1 {
  margin: 0;
  color: #172733;
  font-size: 27px;
  font-weight: 760;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.thesis-block {
  margin-bottom: 18px;
  padding: 11px 13px;
  border-left: 4px solid #b7791f;
  background: #f4f1ea;
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
  font-size: 13px;
  font-weight: 650;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.sentiment-block {
  padding-left: 15px;
  border-left: 1px solid #dbe2e6;
}

.sentiment-block span,
.sentiment-block strong,
.sentiment-block em {
  display: block;
}

.sentiment-block span {
  color: #7b8790;
  font-size: 9px;
  font-weight: 700;
}

.sentiment-block strong {
  margin-top: 7px;
  color: #203746;
  font-size: 34px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.sentiment-block i {
  display: block;
  height: 4px;
  margin-top: 9px;
  overflow: hidden;
  border-radius: 2px;
  background: #dfe6ea;
}

.sentiment-block b {
  display: block;
  height: 100%;
  background: #3977a6;
}

.sentiment-block em {
  margin-top: 6px;
  color: #3977a6;
  font-size: 9px;
  font-style: normal;
  font-weight: 720;
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

.sheet-meta-foot {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 0 2px;
  color: #8d979e;
  font-size: 9px;
  font-variant-numeric: tabular-nums;
}

.pre-market-share-sheet :deep(.holding-card) {
  height: 100%;
  break-inside: avoid;
}
</style>
