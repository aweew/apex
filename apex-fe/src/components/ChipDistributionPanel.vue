<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import TermTip from './TermTip.vue'

const props = defineProps({
  analysis: {
    type: Object,
    required: true,
  },
})

const chartRef = ref(null)
let chart = null

function fmtPrice(value) {
  return value == null || Number.isNaN(Number(value)) ? '-' : Number(value).toFixed(2)
}

function fmtDistance(value) {
  if (value == null || Number.isNaN(Number(value))) return '-'
  const number = Number(value)
  return `${number > 0 ? '+' : ''}${number.toFixed(2)}%`
}

function nearestPriceLabel(rows, value) {
  if (!rows.length || value == null) return null
  let nearest = rows[0].price
  for (const row of rows) {
    if (Math.abs(row.price - value) < Math.abs(nearest - value)) nearest = row.price
  }
  return fmtPrice(nearest)
}

async function renderChart() {
  if (!props.analysis?.ready || !props.analysis.distribution?.length) return
  await nextTick()
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value, null, { renderer: 'canvas' })
  const rows = props.analysis.distribution
  const categories = rows.map((row) => fmtPrice(row.price))
  const currentLabel = nearestPriceLabel(rows, props.analysis.currentPrice)
  const costLabel = nearestPriceLabel(rows, props.analysis.averageCost)
  chart.setOption(
    {
      animation: false,
      grid: { top: 18, right: 22, bottom: 34, left: 58 },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter(params) {
          const item = Array.isArray(params) ? params[0] : params
          const row = rows[item?.dataIndex]
          if (!row) return ''
          const side = row.price <= props.analysis.currentPrice ? '获利筹码' : '套牢筹码'
          return `成本价 ${fmtPrice(row.price)}<br/>${side} ${row.percent.toFixed(2)}%`
        },
      },
      xAxis: {
        type: 'value',
        name: '筹码占比',
        nameLocation: 'middle',
        nameGap: 24,
        axisLabel: { formatter: '{value}%', color: '#86868b', fontSize: 10 },
        splitLine: { lineStyle: { color: 'rgba(0,0,0,0.05)', type: 'dashed' } },
      },
      yAxis: {
        type: 'category',
        data: categories,
        axisTick: { show: false },
        axisLine: { lineStyle: { color: 'rgba(0,0,0,0.12)' } },
        axisLabel: { interval: 7, color: '#6e6e73', fontSize: 10 },
      },
      series: [
        {
          name: '筹码占比',
          type: 'bar',
          data: rows.map((row) => ({
            value: row.percent,
            itemStyle: {
              color: row.price <= props.analysis.currentPrice ? 'rgba(52,199,89,0.72)' : 'rgba(255,59,48,0.72)',
              borderRadius: [0, 2, 2, 0],
            },
          })),
          barWidth: '82%',
          markLine: {
            silent: true,
            symbol: 'none',
            label: { fontSize: 10, padding: [2, 4], backgroundColor: 'rgba(255,255,255,0.86)' },
            data: [
              {
                yAxis: currentLabel,
                name: '现价',
                label: { formatter: `现价 ${fmtPrice(props.analysis.currentPrice)}`, position: 'insideEndTop', color: '#0071e3' },
                lineStyle: { color: '#0071e3', width: 1.2 },
              },
              {
                yAxis: costLabel,
                name: '平均成本',
                label: { formatter: `成本 ${fmtPrice(props.analysis.averageCost)}`, position: 'insideEndBottom', color: '#c27803' },
                lineStyle: { color: '#f59e0b', type: 'dashed', width: 1 },
              },
            ],
          },
        },
      ],
    },
    true,
  )
  chart.resize()
}

function onResize() {
  chart?.resize()
}

watch(() => props.analysis, renderChart, { deep: true })

onMounted(() => {
  renderChart()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <section class="structure-panel" aria-labelledby="price-structure-title">
    <div class="structure-head">
      <div>
        <h2 id="price-structure-title"><TermTip term="support_resistance">支撑与压力</TermTip></h2>
        <p>均线定方向，筹码找位置 · 截至 {{ analysis.asOfDate || '-' }}</p>
      </div>
      <span class="quality-tag">{{ analysis.sampleSize }} 根日线 · 换手覆盖 {{ analysis.quality.actualTurnoverRatioPct }}%</span>
    </div>

    <div class="structure-overview">
      <article class="level-tile level-tile--support">
        <span class="tile-label">主要支撑</span>
        <template v-if="analysis.support">
          <strong>{{ fmtPrice(analysis.support.price) }}</strong>
          <p>{{ fmtPrice(analysis.support.rangeLow) }} - {{ fmtPrice(analysis.support.rangeHigh) }}</p>
          <small>{{ analysis.support.strength.label }}密集 · 距现价 {{ fmtDistance(analysis.support.distancePct) }}</small>
        </template>
        <template v-else>
          <strong>暂无</strong>
          <p>现价下方未识别到显著筹码峰</p>
        </template>
      </article>
      <article class="level-tile level-tile--resistance">
        <span class="tile-label">主要压力</span>
        <template v-if="analysis.resistance">
          <strong>{{ fmtPrice(analysis.resistance.price) }}</strong>
          <p>{{ fmtPrice(analysis.resistance.rangeLow) }} - {{ fmtPrice(analysis.resistance.rangeHigh) }}</p>
          <small>{{ analysis.resistance.strength.label }}密集 · 距现价 {{ fmtDistance(analysis.resistance.distancePct) }}</small>
        </template>
        <template v-else>
          <strong>暂无</strong>
          <p>现价上方未识别到显著筹码峰</p>
        </template>
      </article>
      <article class="level-tile" :class="`level-tile--${analysis.trend.key}`">
        <span class="tile-label">均线趋势</span>
        <strong>{{ analysis.trend.label }}</strong>
        <p>{{ analysis.trend.description }}</p>
        <small>MA20 近 5 日斜率 {{ fmtDistance(analysis.trend.ma20SlopePct) }}</small>
      </article>
      <article class="level-tile level-tile--cost">
        <span class="tile-label">平均成本</span>
        <strong>{{ fmtPrice(analysis.averageCost) }}</strong>
        <p>获利盘约 {{ analysis.profitRatioPct.toFixed(1) }}%</p>
        <small>70% 成本 {{ fmtPrice(analysis.concentration70.low) }} - {{ fmtPrice(analysis.concentration70.high) }}</small>
      </article>
    </div>

    <div class="chip-layout">
      <div class="chip-chart-wrap">
        <div class="chip-title-row">
          <h3><TermTip term="chip_distribution">筹码分布</TermTip></h3>
          <span><i class="legend-dot legend-dot--profit" />获利筹码 <i class="legend-dot legend-dot--locked" />套牢筹码</span>
        </div>
        <div ref="chartRef" class="chip-chart" role="img" aria-label="按成本价展示的历史筹码分布图" />
      </div>

      <aside class="chip-stats" aria-label="筹码结构摘要">
        <h3>结构摘要</h3>
        <dl>
          <div><dt>当前价</dt><dd>{{ fmtPrice(analysis.currentPrice) }}</dd></div>
          <div><dt>平均成本</dt><dd>{{ fmtPrice(analysis.averageCost) }}</dd></div>
          <div><dt>获利盘</dt><dd>{{ analysis.profitRatioPct.toFixed(1) }}%</dd></div>
          <div><dt>90% 成本区</dt><dd>{{ fmtPrice(analysis.concentration90.low) }} - {{ fmtPrice(analysis.concentration90.high) }}</dd></div>
        </dl>
        <h3>动态均线</h3>
        <ul class="ma-levels">
          <li v-for="level in analysis.dynamicLevels" :key="level.name">
            <span>{{ level.name }}</span>
            <b>{{ fmtPrice(level.price) }}</b>
            <em :class="level.side === 'support' ? 'down' : 'up'">{{ level.side === 'support' ? '支撑侧' : '压力侧' }}</em>
          </li>
        </ul>
      </aside>
    </div>

    <p class="model-note">
      本模块按历史日线价格区间与换手率估算，并非交易所真实持仓。价位应视为区域；放量有效突破后，原压力可能转为支撑，跌破后则相反。
      <template v-if="analysis.quality.usedFallbackTurnover">部分日线缺少换手率，已使用保守默认值补算。</template>
    </p>
  </section>
</template>

<style scoped>
.structure-panel {
  margin-top: 22px;
}

.structure-head,
.chip-title-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.structure-head h2,
.chip-title-row h3,
.chip-stats h3 {
  margin: 0;
  color: var(--ink);
  letter-spacing: 0;
}

.structure-head h2 {
  font-size: 18px;
}

.structure-head p {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.quality-tag {
  color: var(--slate);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.structure-overview {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 12px 0;
}

.level-tile {
  min-height: 124px;
  padding: 13px 14px;
  background: var(--glass-strong);
  border: 1px solid var(--glass-border);
  border-top: 3px solid #86868b;
  border-radius: 8px;
  box-shadow: var(--shadow-soft);
}

.level-tile--support,
.level-tile--bullish {
  border-top-color: var(--down);
}

.level-tile--resistance,
.level-tile--bearish {
  border-top-color: var(--up);
}

.level-tile--cost {
  border-top-color: var(--warn);
}

.tile-label {
  display: block;
  color: var(--muted);
  font-size: 11px;
  font-weight: 600;
}

.level-tile strong {
  display: block;
  margin-top: 7px;
  color: var(--ink);
  font-size: 23px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.level-tile p {
  min-height: 30px;
  margin: 8px 0 4px;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.35;
}

.level-tile small {
  color: var(--muted);
  font-size: 10px;
  line-height: 1.35;
}

.chip-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 250px;
  gap: 12px;
}

.chip-chart-wrap,
.chip-stats {
  background: var(--glass-strong);
  border: 1px solid var(--glass-border);
  border-radius: 8px;
  box-shadow: var(--shadow-soft);
}

.chip-chart-wrap {
  padding: 14px 14px 8px;
}

.chip-title-row h3,
.chip-stats h3 {
  font-size: 13px;
}

.chip-title-row > span {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--muted);
  font-size: 10px;
}

.legend-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.legend-dot--profit { background: var(--down); }
.legend-dot--locked { background: var(--up); margin-left: 4px; }

.chip-chart {
  width: 100%;
  height: 410px;
}

.chip-stats {
  padding: 14px;
}

.chip-stats h3 + dl,
.chip-stats h3 + .ma-levels {
  margin-top: 8px;
}

.chip-stats h3:not(:first-child) {
  margin-top: 18px;
}

.chip-stats dl {
  margin: 0;
}

.chip-stats dl > div,
.ma-levels li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 34px;
  border-bottom: 1px solid var(--line);
  font-size: 11px;
}

.chip-stats dt,
.ma-levels span {
  color: var(--muted);
}

.chip-stats dd,
.ma-levels b {
  margin: 0;
  color: var(--ink);
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.ma-levels {
  margin: 0;
  padding: 0;
  list-style: none;
}

.ma-levels em {
  font-size: 10px;
  font-style: normal;
}

.model-note {
  margin: 10px 2px 0;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.55;
}

@media (max-width: 960px) {
  .structure-overview { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .chip-layout { grid-template-columns: 1fr; }
  .chip-stats { display: grid; grid-template-columns: 1fr 1fr; gap: 0 20px; }
  .chip-stats h3:not(:first-child) { margin-top: 0; }
}

@media (max-width: 560px) {
  .structure-head { align-items: flex-start; flex-direction: column; gap: 6px; }
  .structure-overview { grid-template-columns: 1fr; }
  .level-tile { min-height: 112px; }
  .chip-title-row { align-items: flex-start; flex-direction: column; gap: 8px; }
  .chip-chart { height: 360px; }
  .chip-stats { display: block; }
  .chip-stats h3:not(:first-child) { margin-top: 18px; }
}
</style>
