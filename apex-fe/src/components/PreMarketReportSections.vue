<script setup>
import PreMarketHoldingCard from './PreMarketHoldingCard.vue'

const props = defineProps({
  sections: { type: Array, default: () => [] },
  compact: { type: Boolean, default: false },
})

function holdingPriority(holding) {
  if (holding.status === '高风险') return 0
  if (holding.status === '风险观察') return 1
  if (holding.status === '中性观察') return 2
  return 3
}

function sortedHoldings(section) {
  return [...(section.holdings || [])].sort((left, right) => {
    const statusDifference = holdingPriority(left) - holdingPriority(right)
    if (statusDifference !== 0) return statusDifference
    return (right.weight || 0) - (left.weight || 0)
  })
}

function scenarioTone(name) {
  if (name === '偏强') return 'positive'
  if (name === '转弱') return 'negative'
  return 'neutral'
}
</script>

<template>
  <div class="report-sections" :class="{ 'is-compact': props.compact }">
    <section
      v-for="section in sections"
      :key="section.number"
      class="report-section"
      :class="`section-${section.number}`"
    >
      <header class="section-heading">
        <span>{{ section.number }}</span>
        <h2>{{ section.title }}</h2>
      </header>

      <div class="section-content">
        <div v-if="section.opportunities?.length" class="opportunity-grid">
          <article v-for="opportunity in section.opportunities" :key="opportunity.rank" class="opportunity-card">
            <header>
              <span>{{ String(opportunity.rank).padStart(2, '0') }}</span>
              <h3>{{ opportunity.direction }}</h3>
            </header>
            <dl>
              <div v-if="opportunity.catalyst" class="catalyst-row">
                <dt>催化</dt>
                <dd>{{ opportunity.catalyst }}</dd>
              </div>
              <div v-if="opportunity.confirmation" class="confirm-row">
                <dt>确认</dt>
                <dd>{{ opportunity.confirmation }}</dd>
              </div>
              <div v-if="opportunity.invalidation" class="invalidate-row">
                <dt>失效</dt>
                <dd>{{ opportunity.invalidation }}</dd>
              </div>
            </dl>
          </article>
        </div>

        <template v-else-if="section.number === '04'">
          <div v-if="section.holdings?.length && props.compact" class="holding-action-grid">
            <article
              v-for="(holding, index) in sortedHoldings(section)"
              :key="`${holding.code}-${index}`"
              class="holding-action-row"
              :class="{ 'is-danger': holding.status === '高风险' }"
            >
              <header>
                <div>
                  <strong>{{ holding.name }}</strong>
                  <span>{{ holding.code }}</span>
                </div>
                <em>{{ holding.status }}</em>
              </header>
              <div class="holding-action-metrics">
                <span v-if="holding.weightText">仓位 <strong>{{ holding.weightText }}</strong></span>
                <span v-if="holding.pnlText">盈亏 <strong>{{ holding.pnl > 0 ? '+' : '' }}{{ holding.pnlText }}</strong></span>
                <span v-if="holding.radarTotal">雷达 <strong>{{ holding.radarHit }}/{{ holding.radarTotal }}</strong></span>
              </div>
              <p v-if="holding.reason"><span>入选</span>{{ holding.reason }}</p>
              <p v-if="holding.advice" class="holding-action"><span>处理</span>{{ holding.advice }}</p>
            </article>
          </div>
          <div v-else-if="section.holdings?.length" class="holding-grid">
            <PreMarketHoldingCard
              v-for="(holding, index) in sortedHoldings(section)"
              :key="`${holding.code}-${index}`"
              :holding="holding"
              :compact="props.compact"
            />
          </div>
          <div v-if="section.portfolioRisks?.length" class="portfolio-risk-block">
            <header>
              <span>PORTFOLIO RISK</span>
              <strong>组合暴露</strong>
            </header>
            <ul>
              <li v-for="risk in section.portfolioRisks" :key="risk">{{ risk }}</li>
            </ul>
          </div>
        </template>

        <div v-else-if="section.scenarios?.length" class="scenario-grid">
          <article
            v-for="scenario in section.scenarios"
            :key="scenario.name"
            class="scenario-card"
            :class="`is-${scenarioTone(scenario.name)}`"
          >
            <span>{{ scenario.name }}</span>
            <p>{{ scenario.condition }}</p>
          </article>
        </div>

        <div v-else-if="section.facts?.length" class="fact-grid">
          <article v-for="fact in section.facts" :key="`${fact.label}-${fact.value}`" class="fact-item">
            <span v-if="fact.label">{{ fact.label }}</span>
            <p>{{ fact.value }}</p>
          </article>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.report-sections {
  --section-label-width: 132px;
  --section-gap: 28px;
  --section-space: 30px;
  --body-size: 14px;
  --card-padding: 18px;
  border-top: 2px solid #1e3342;
}

.report-section {
  display: grid;
  grid-template-columns: var(--section-label-width) minmax(0, 1fr);
  gap: var(--section-gap);
  padding: var(--section-space) 0;
  border-bottom: 1px solid #dce3e7;
  break-inside: avoid;
}

.section-heading {
  display: flex;
  align-items: baseline;
  gap: 9px;
}

.section-heading > span {
  color: #a1abb2;
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}

.section-heading h2 {
  margin: 0;
  color: #203441;
  font-size: 17px;
  line-height: 1.4;
}

.section-content {
  min-width: 0;
}

.fact-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-block: 1px solid #e1e7ea;
}

.fact-item {
  min-width: 0;
  padding: 14px 18px 14px 0;
}

.fact-item + .fact-item {
  padding-left: 18px;
  border-left: 1px solid #e1e7ea;
}

.fact-item span,
.opportunity-card dt,
.portfolio-risk-block header span {
  color: #7c8992;
  font-size: 10px;
  font-weight: 720;
  line-height: 1.4;
}

.fact-item p {
  margin: 6px 0 0;
  color: #354650;
  font-size: var(--body-size);
  font-weight: 560;
  line-height: 1.62;
  overflow-wrap: anywhere;
}

.opportunity-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.opportunity-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid #dbe3e7;
  border-top: 3px solid #b17a27;
  border-radius: 6px;
  background: #ffffff;
  break-inside: avoid;
}

.opportunity-card header {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: var(--card-padding) var(--card-padding) 12px;
  background: #f7f9fa;
}

.opportunity-card header span {
  color: #a7762f;
  font-size: 10px;
  font-weight: 750;
}

.opportunity-card h3 {
  margin: 0;
  color: #1f303b;
  font-size: 17px;
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.opportunity-card dl {
  margin: 0;
  padding: 3px var(--card-padding) 10px;
}

.opportunity-card dl > div {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 9px;
  padding: 10px 0;
}

.opportunity-card dl > div + div {
  border-top: 1px solid #edf1f3;
}

.opportunity-card dt {
  padding-top: 1px;
}

.opportunity-card dd {
  margin: 0;
  color: #46555f;
  font-size: calc(var(--body-size) - 1px);
  line-height: 1.58;
  overflow-wrap: anywhere;
}

.opportunity-card .confirm-row dt {
  color: #257458;
}

.opportunity-card .invalidate-row dt {
  color: #b34d45;
}

.holding-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.holding-action-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px;
}

.holding-action-row {
  min-width: 0;
  padding: 9px 10px;
  border: 1px solid #dbe3e7;
  border-top: 2px solid #87949d;
  border-radius: 4px;
  background: #ffffff;
  break-inside: avoid;
}

.holding-action-row.is-danger {
  border-top-color: #b94b42;
}

.holding-action-row header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.holding-action-row header div {
  display: flex;
  align-items: baseline;
  min-width: 0;
  gap: 6px;
}

.holding-action-row header strong {
  color: #1f303b;
  font-size: 11px;
  line-height: 1.35;
}

.holding-action-row header span {
  color: #89949c;
  font-size: 8px;
  font-variant-numeric: tabular-nums;
}

.holding-action-row header em {
  flex: 0 0 auto;
  padding: 2px 5px;
  border-radius: 3px;
  color: #9f4039;
  background: #faeceb;
  font-size: 8px;
  font-style: normal;
  font-weight: 700;
}

.holding-action-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 12px;
  margin-top: 6px;
  padding: 5px 0;
  border-block: 1px solid #edf1f3;
  color: #7e8991;
  font-size: 8px;
}

.holding-action-metrics strong {
  color: #334550;
  font-size: 9px;
  font-variant-numeric: tabular-nums;
}

.holding-action-row p {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 5px;
  margin: 5px 0 0;
  color: #5b6871;
  font-size: 8px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.holding-action-row p span {
  color: #8a959c;
  font-weight: 700;
}

.holding-action-row .holding-action,
.holding-action-row .holding-action span {
  color: #9d4039;
  font-weight: 650;
}

.portfolio-risk-block {
  display: grid;
  grid-template-columns: 132px minmax(0, 1fr);
  gap: 18px;
  margin-top: 16px;
  padding: 15px 18px;
  border-left: 3px solid #bd5b52;
  background: #fbf6f5;
}

.portfolio-risk-block header span,
.portfolio-risk-block header strong {
  display: block;
}

.portfolio-risk-block header strong {
  margin-top: 4px;
  color: #8f3d38;
  font-size: 13px;
}

.portfolio-risk-block ul {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 18px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.portfolio-risk-block li {
  position: relative;
  min-width: 0;
  padding-left: 11px;
  color: #654b49;
  font-size: calc(var(--body-size) - 2px);
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.portfolio-risk-block li::before {
  content: "";
  position: absolute;
  top: 8px;
  left: 0;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #bd5b52;
}

.scenario-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.scenario-card {
  min-width: 0;
  padding: 15px 16px 17px;
  border-top: 3px solid #8b969d;
  background: #f5f7f8;
}

.scenario-card.is-positive {
  border-top-color: #2d8060;
  background: #f2f8f5;
}

.scenario-card.is-negative {
  border-top-color: #ba5148;
  background: #fbf5f4;
}

.scenario-card span {
  color: #243843;
  font-size: 13px;
  font-weight: 750;
}

.scenario-card p {
  margin: 7px 0 0;
  color: #53616a;
  font-size: calc(var(--body-size) - 1px);
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.is-compact {
  --section-label-width: 104px;
  --section-gap: 20px;
  --section-space: 15px;
  --body-size: 10px;
  --card-padding: 11px;
}

.is-compact .section-heading {
  gap: 7px;
}

.is-compact .section-heading h2 {
  font-size: 13px;
}

.is-compact .fact-item {
  padding-block: 9px;
}

.is-compact .opportunity-grid,
.is-compact .scenario-grid,
.is-compact .holding-grid {
  gap: 8px;
}

.is-compact .opportunity-card h3 {
  font-size: 13px;
}

.is-compact .opportunity-card dl > div {
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 6px;
  padding: 6px 0;
}

.is-compact .portfolio-risk-block {
  grid-template-columns: 98px minmax(0, 1fr);
  gap: 12px;
  margin-top: 9px;
  padding: 10px 12px;
}

.is-compact .scenario-card {
  padding: 10px 11px 11px;
}

.is-compact .scenario-card span {
  font-size: 11px;
}

@media (max-width: 760px) {
  .report-section {
    grid-template-columns: minmax(0, 1fr);
    gap: 14px;
    padding: 24px 0;
  }

  .opportunity-grid,
  .holding-grid,
  .holding-action-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .portfolio-risk-block {
    grid-template-columns: minmax(0, 1fr);
    gap: 9px;
  }
}

@media (max-width: 520px) {
  .fact-grid,
  .scenario-grid,
  .portfolio-risk-block ul {
    grid-template-columns: minmax(0, 1fr);
  }

  .fact-item + .fact-item {
    padding-left: 0;
    border-top: 1px solid #e1e7ea;
    border-left: 0;
  }
}

.report-sections.is-compact .report-section {
  grid-template-columns: var(--section-label-width) minmax(0, 1fr);
  gap: var(--section-gap);
  padding: var(--section-space) 0;
}

.report-sections.is-compact .fact-grid,
.report-sections.is-compact .opportunity-grid,
.report-sections.is-compact .scenario-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.report-sections.is-compact .holding-action-grid,
.report-sections.is-compact .portfolio-risk-block ul {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.report-sections.is-compact .portfolio-risk-block {
  grid-template-columns: 98px minmax(0, 1fr);
}

.report-sections.is-compact .fact-item + .fact-item {
  padding-left: 18px;
  border-top: 0;
  border-left: 1px solid #e1e7ea;
}
</style>
