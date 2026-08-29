<script setup>
import { computed } from 'vue'
import { TrendCharts, WarningFilled } from '@element-plus/icons-vue'

const props = defineProps({
  holding: { type: Object, required: true },
  compact: { type: Boolean, default: false },
})

const statusTone = computed(() => {
  if (props.holding.status === '高风险') return 'danger'
  if (props.holding.status === '风险观察') return 'warning'
  if (props.holding.status === '正向关注') return 'positive'
  return 'neutral'
})

const pnlTone = computed(() => {
  if (props.holding.pnl > 0) return 'positive'
  if (props.holding.pnl < 0) return 'negative'
  return 'neutral'
})

const weightWidth = computed(() => Math.min(Math.max(props.holding.weight || 0, 0), 100))
const radarDots = computed(() => Array.from({ length: Math.min(props.holding.radarTotal || 0, 12) }))
</script>

<template>
  <article class="holding-card" :class="[`is-${statusTone}`, { 'is-compact': props.compact }]">
    <header class="holding-head">
      <router-link class="holding-identity" :to="`/stock/${holding.code}`">
        <strong>{{ holding.name }}</strong>
        <span>{{ holding.code }}</span>
      </router-link>
      <span class="status-badge" :class="`is-${statusTone}`">
        <el-icon v-if="statusTone === 'danger' || statusTone === 'warning'"><WarningFilled /></el-icon>
        {{ holding.status }}
      </span>
    </header>

    <div class="holding-metrics" aria-label="持仓关键指标">
      <div v-if="holding.weightText" class="metric weight-metric">
        <span>仓位</span>
        <strong>{{ holding.weightText }}</strong>
        <i aria-hidden="true"><b :style="{ width: `${weightWidth}%` }" /></i>
      </div>
      <div v-if="holding.priceText" class="metric">
        <span>现价</span>
        <strong>{{ holding.priceText }}</strong>
      </div>
      <div v-if="holding.pnlText" class="metric">
        <span>持仓盈亏</span>
        <strong :class="`metric-${pnlTone}`">{{ holding.pnl > 0 ? '+' : '' }}{{ holding.pnlText }}</strong>
      </div>
    </div>

    <div v-if="holding.radarTotal" class="radar-row" :aria-label="`技术雷达命中 ${holding.radarHit} 项，共 ${holding.radarTotal} 项`">
      <span><el-icon><TrendCharts /></el-icon>技术雷达</span>
      <div class="radar-dots" aria-hidden="true">
        <i v-for="(_, index) in radarDots" :key="index" :class="{ active: index < holding.radarHit }" />
      </div>
      <strong>{{ holding.radarHit }}/{{ holding.radarTotal }}</strong>
    </div>

    <p v-if="holding.trend" class="trend-text">{{ holding.trend }}</p>

    <dl class="holding-notes">
      <div v-if="holding.reason">
        <dt>入选</dt>
        <dd>{{ holding.reason }}</dd>
      </div>
      <div v-if="holding.advice" class="action-note">
        <dt>处理</dt>
        <dd>{{ holding.advice }}</dd>
      </div>
    </dl>
  </article>
</template>

<style scoped>
.holding-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid #dfe4e8;
  border-top: 3px solid #82909d;
  border-radius: 6px;
  background: #fff;
}

.holding-card.is-danger {
  border-top-color: #b94b42;
}

.holding-card.is-warning {
  border-top-color: #b7791f;
}

.holding-card.is-positive {
  border-top-color: #297858;
}

.holding-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 15px 16px 12px;
}

.holding-identity {
  min-width: 0;
  color: inherit;
  text-decoration: none;
}

.holding-identity strong {
  display: block;
  color: #17202a;
  font-size: 17px;
  line-height: 1.3;
}

.holding-identity span {
  display: block;
  margin-top: 3px;
  color: #7b8793;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.status-badge {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  min-height: 24px;
  padding: 3px 8px;
  border-radius: 4px;
  color: #52606d;
  background: #f0f2f4;
  font-size: 12px;
  font-weight: 650;
  line-height: 1.35;
}

.status-badge.is-danger {
  color: #a83731;
  background: #fbeceb;
}

.status-badge.is-warning {
  color: #996515;
  background: #fbf2df;
}

.status-badge.is-positive {
  color: #216b4e;
  background: #e8f4ee;
}

.holding-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-block: 1px solid #edf0f2;
  background: #f8f9fa;
}

.metric {
  min-width: 0;
  padding: 10px 12px;
  border-right: 1px solid #e7ebee;
}

.metric:last-child {
  border-right: 0;
}

.metric span {
  display: block;
  color: #7b8793;
  font-size: 11px;
}

.metric strong {
  display: block;
  margin-top: 4px;
  color: #25313c;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  line-height: 1.25;
}

.metric strong.metric-positive {
  color: #b83b36;
}

.metric strong.metric-negative {
  color: #237a58;
}

.weight-metric i {
  display: block;
  width: 100%;
  height: 3px;
  margin-top: 7px;
  overflow: hidden;
  border-radius: 2px;
  background: #dfe5e9;
}

.weight-metric b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #427ba8;
}

.radar-row {
  display: grid;
  grid-template-columns: auto minmax(72px, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 12px 16px 0;
}

.radar-row > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #5e6b77;
  font-size: 12px;
  font-weight: 650;
}

.radar-row > strong {
  color: #394956;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.radar-dots {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(7px, 1fr));
  gap: 4px;
  height: 8px;
}

.radar-dots i {
  height: 8px;
  border-radius: 2px;
  background: #dfe4e8;
}

.radar-dots i.active {
  background: #427ba8;
}

.trend-text {
  margin: 8px 16px 0;
  color: #687581;
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.holding-notes {
  margin: 12px 16px 15px;
}

.holding-notes > div {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  gap: 8px;
  padding-top: 9px;
  border-top: 1px solid #edf0f2;
}

.holding-notes > div + div {
  margin-top: 9px;
}

.holding-notes dt {
  color: #7b8793;
  font-size: 12px;
  line-height: 1.65;
}

.holding-notes dd {
  margin: 0;
  color: #46535f;
  font-size: 13px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.holding-notes .action-note {
  border-top-color: #eadbd9;
}

.action-note dt,
.action-note dd {
  color: #9e4039;
  font-weight: 650;
}

.holding-card.is-compact .holding-head {
  padding: 10px 11px 8px;
}

.holding-card.is-compact .holding-identity strong {
  font-size: 13px;
}

.holding-card.is-compact .holding-identity span,
.holding-card.is-compact .status-badge,
.holding-card.is-compact .metric span,
.holding-card.is-compact .radar-row > span,
.holding-card.is-compact .radar-row > strong,
.holding-card.is-compact .holding-notes dt {
  font-size: 9px;
}

.holding-card.is-compact .status-badge {
  min-height: 20px;
  padding: 2px 6px;
}

.holding-card.is-compact .metric {
  padding: 6px 7px;
}

.holding-card.is-compact .metric strong {
  font-size: 11px;
}

.holding-card.is-compact .radar-row {
  grid-template-columns: auto minmax(58px, 1fr) auto;
  gap: 7px;
  padding: 8px 11px 0;
}

.holding-card.is-compact .radar-dots,
.holding-card.is-compact .radar-dots i {
  height: 6px;
}

.holding-card.is-compact .trend-text {
  margin: 5px 11px 0;
  font-size: 8px;
  line-height: 1.45;
}

.holding-card.is-compact .holding-notes {
  margin: 7px 11px 9px;
}

.holding-card.is-compact .holding-notes > div {
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 5px;
  padding-top: 6px;
}

.holding-card.is-compact .holding-notes > div + div {
  margin-top: 6px;
}

.holding-card.is-compact .holding-notes dd {
  font-size: 9px;
  line-height: 1.45;
}

@media (max-width: 420px) {
  .holding-head {
    padding-inline: 14px;
  }

  .metric {
    padding-inline: 9px;
  }

  .metric strong {
    font-size: 13px;
  }

  .radar-row,
  .trend-text,
  .holding-notes {
    margin-inline: 14px;
  }

  .radar-row {
    padding-inline: 0;
  }
}
</style>
