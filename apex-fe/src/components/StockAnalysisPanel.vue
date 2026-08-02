<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchStockAnalysis } from '../api/stock'

const props = defineProps({
  code: { type: String, required: true },
})

const router = useRouter()
const loading = ref(false)
const side = ref('BUY')
const data = ref(null)
const error = ref('')

const stanceClass = computed(() => {
  const s = data.value?.stance || ''
  if (s.includes('积极') || s.includes('跟踪')) return 'good'
  if (s.includes('谨慎') || s.includes('回避')) return 'bad'
  return 'mid'
})

function fmtPct(v, digits = 2) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return '-'
  const sign = n > 0 ? '+' : ''
  return `${sign}${n.toFixed(digits)}%`
}

function fmtNum(v, digits = 2) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return '-'
  return n.toFixed(digits)
}

function fmtMoneyYi(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return '-'
  if (Math.abs(n) >= 1e8) return `${(n / 1e8).toFixed(2)}亿`
  if (Math.abs(n) >= 1e4) return `${(n / 1e4).toFixed(0)}万`
  return n.toFixed(0)
}

async function load() {
  if (!props.code) return
  loading.value = true
  error.value = ''
  try {
    const res = await fetchStockAnalysis(props.code, side.value, 120)
    data.value = res.data
  } catch (e) {
    data.value = null
    error.value = e.message || '加载失败'
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.code, side.value],
  () => load(),
  { immediate: true },
)

defineExpose({ reload: load })
</script>

<template>
  <div class="analysis" v-loading="loading">
    <div class="analysis-toolbar">
      <el-radio-group v-model="side" size="small">
        <el-radio-button value="BUY">偏多雷达</el-radio-button>
        <el-radio-button value="SELL">偏空雷达</el-radio-button>
      </el-radio-group>
      <el-button size="small" :loading="loading" @click="load">刷新研判</el-button>
      <el-button size="small" plain @click="router.push({ path: '/valuation', query: { code } })">完整估值</el-button>
      <el-button size="small" plain @click="router.push({ path: '/backtest', query: { code } })">回测</el-button>
    </div>

    <el-empty v-if="!loading && error" :description="error">
      <el-button type="primary" @click="load">重试</el-button>
    </el-empty>

    <template v-if="data">
      <!-- 结论总览 -->
      <section class="hero" :class="stanceClass">
        <div class="hero-score">
          <em>{{ fmtNum(data.compositeScore, 1) }}</em>
          <span>综合分</span>
        </div>
        <div class="hero-main">
          <div class="hero-stance">{{ data.stance }}</div>
          <p class="hero-summary">{{ data.summary }}</p>
          <p class="hero-action">{{ data.actionHint }}</p>
        </div>
      </section>

      <div v-if="data.scoreExplain?.length" class="explain">
        <span v-for="(x, i) in data.scoreExplain" :key="i" class="explain-chip">{{ x }}</span>
      </div>

      <div class="grid-2">
        <section class="card">
          <h3>多头 / 支持</h3>
          <ul v-if="data.bullPoints?.length">
            <li v-for="(p, i) in data.bullPoints" :key="'b' + i">{{ p }}</li>
          </ul>
          <p v-else class="muted">暂无明显支持点</p>
        </section>
        <section class="card">
          <h3>空头 / 风险</h3>
          <ul v-if="data.bearPoints?.length || data.riskFlags?.length">
            <li v-for="(p, i) in data.bearPoints || []" :key="'r' + i">{{ p }}</li>
            <li v-for="(p, i) in data.riskFlags || []" :key="'f' + i" class="risk">{{ p }}</li>
          </ul>
          <p v-else class="muted">暂无显著风险标记</p>
        </section>
      </div>

      <!-- 四维 -->
      <div class="grid-2">
        <section class="card">
          <header class="card-head">
            <h3><TermTip term="ma">技术面</TermTip></h3>
            <b>{{ data.tech?.hitCount ?? 0 }}/{{ data.tech?.total ?? 0 }} · {{ fmtNum(data.tech?.hitRate, 1) }}%</b>
          </header>
          <p class="dim-summary">{{ data.tech?.summary || '-' }}</p>
          <div class="kpi-row">
            <div><label><TermTip term="rsi">RSI14</TermTip></label><b>{{ fmtNum(data.tech?.rsi14, 1) }}</b></div>
            <div><label><TermTip term="atr_pct">ATR%</TermTip></label><b>{{ fmtNum(data.tech?.atrPct, 2) }}</b></div>
            <div><label><TermTip term="volume_ratio">量比</TermTip></label><b>{{ fmtNum(data.tech?.volumeRatio, 2) }}</b></div>
            <div><label><TermTip term="rs20">RS20</TermTip></label><b :class="Number(data.tech?.rs20VsHs300) >= 0 ? 'up' : 'down'">{{ fmtNum(data.tech?.rs20VsHs300, 2) }}</b></div>
            <div><label><TermTip term="rs60">RS60</TermTip></label><b :class="Number(data.tech?.rs60VsHs300) >= 0 ? 'up' : 'down'">{{ fmtNum(data.tech?.rs60VsHs300, 2) }}</b></div>
            <div><label>MA5/20</label><b>{{ fmtNum(data.tech?.ma5, 2) }} / {{ fmtNum(data.tech?.ma20, 2) }}</b></div>
          </div>
          <div class="radar">
            <button
              v-for="s in data.tech?.signals || []"
              :key="s.key"
              type="button"
              class="radar-chip"
              :class="{ on: s.hit }"
              :title="s.detail || s.label"
            >
              {{ s.label }}
            </button>
          </div>
        </section>

        <section class="card">
          <header class="card-head">
            <h3><TermTip term="pe_ttm">估值</TermTip></h3>
            <b>{{ data.valuation?.levelLabel || '-' }} · {{ fmtNum(data.valuation?.score, 1) }}</b>
          </header>
          <p class="dim-summary">{{ data.valuation?.summary || '估值暂不可用' }}</p>
          <div class="kpi-row">
            <div><label>PE</label><b>{{ fmtNum(data.valuation?.peTtm, 2) }}</b></div>
            <div><label>PB</label><b>{{ fmtNum(data.valuation?.pb, 2) }}</b></div>
            <div><label>PEG</label><b>{{ fmtNum(data.valuation?.peg, 2) }}</b></div>
            <div><label>安全边际</label><b>{{ fmtPct(data.valuation?.marginOfSafety) }}</b></div>
            <div><label>行业PE中位</label><b>{{ fmtNum(data.valuation?.industryPeMedian, 2) }}</b></div>
            <div><label>PE分位</label><b>{{ fmtNum(data.valuation?.pePercentile, 1) }}</b></div>
          </div>
          <div v-if="data.valuation?.dimensions?.length" class="dims">
            <div v-for="d in data.valuation.dimensions" :key="d.key" class="dim-row">
              <span>{{ d.name }}</span>
              <em>{{ fmtNum(d.score, 0) }}</em>
              <small>{{ d.verdict || d.detail || '' }}</small>
            </div>
          </div>
          <p v-if="data.valuation?.actionHint" class="hint">{{ data.valuation.actionHint }}</p>
        </section>

        <section class="card">
          <header class="card-head">
            <h3>资金 / 情绪</h3>
            <b>{{ data.capital?.hotHit ? `热点${data.capital.hotSourceCount}源` : '非热点' }}</b>
          </header>
          <p class="dim-summary">{{ data.capital?.summary || '-' }}</p>
          <div class="kpi-row">
            <div><label>量比</label><b>{{ fmtNum(data.capital?.volumeRatio, 2) }}</b></div>
            <div><label>板块</label><b>{{ data.capital?.sectorName || '-' }}</b></div>
            <div><label>板块涨跌</label><b :class="Number(data.capital?.sectorPctChg) >= 0 ? 'up' : 'down'">{{ fmtPct(data.capital?.sectorPctChg) }}</b></div>
            <div><label>板块净流入</label><b :class="Number(data.capital?.sectorNetInflow) >= 0 ? 'up' : 'down'">{{ fmtMoneyYi(data.capital?.sectorNetInflow) }}</b></div>
            <div><label>主力净流入</label><b :class="Number(data.capital?.sectorMainNetInflow) >= 0 ? 'up' : 'down'">{{ fmtMoneyYi(data.capital?.sectorMainNetInflow) }}</b></div>
            <div><label>热点排名</label><b>{{ data.capital?.hotBestRank ?? '-' }}</b></div>
          </div>
          <div v-if="data.capital?.hotSources?.length" class="hot-sources">
            <span v-for="s in data.capital.hotSources" :key="s" class="tag">{{ s }}</span>
          </div>
        </section>

        <section class="card">
          <header class="card-head">
            <h3><TermTip term="strategy_signal">策略 / 决策</TermTip></h3>
            <b>{{ data.signals?.length || 0 }} 条信号</b>
          </header>
          <div v-if="data.decision" class="decision-box">
            <div>今日决策：<b>{{ data.decision.action }}</b>
              <span v-if="data.decision.score != null"> · 分 {{ fmtNum(data.decision.score, 1) }}</span>
            </div>
            <p>{{ data.decision.reason || data.decision.scoreExplain || '' }}</p>
          </div>
          <el-table v-if="data.signals?.length" :data="data.signals" size="small" stripe>
            <el-table-column prop="strategyId" label="策略" width="80" />
            <el-table-column prop="side" label="方向" width="70" />
            <el-table-column prop="score" label="分" width="70" />
            <el-table-column prop="signalDate" label="日期" width="110" />
            <el-table-column label="理由" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.reasonJson || '-' }}</template>
            </el-table-column>
          </el-table>
          <p v-else class="muted">当日无策略信号（可先跑智能决策/信号）</p>
        </section>
      </div>

      <p class="footnote">{{ data.dataNote }}</p>
    </template>
  </div>
</template>

<style scoped>
.analysis {
  padding: 4px 0 16px;
}

.analysis-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.hero {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 16px;
  padding: 16px 18px;
  border-radius: 14px;
  border: 1px solid var(--glass-border);
  background: var(--glass);
  margin-bottom: 12px;
}

.hero.good {
  background: linear-gradient(135deg, rgba(52, 199, 89, 0.1), rgba(255, 255, 255, 0.7));
}

.hero.bad {
  background: linear-gradient(135deg, rgba(196, 86, 86, 0.1), rgba(255, 255, 255, 0.7));
}

.hero.mid {
  background: linear-gradient(135deg, rgba(255, 204, 0, 0.1), rgba(255, 255, 255, 0.7));
}

.hero-score {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.hero-score em {
  font-style: normal;
  font-size: 40px;
  font-weight: 800;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.hero-score span {
  margin-top: 4px;
  font-size: 12px;
  color: var(--muted);
}

.hero-stance {
  font-size: 20px;
  font-weight: 750;
  margin-bottom: 6px;
}

.hero-summary,
.hero-action {
  margin: 0 0 6px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--ink-soft);
}

.hero-action {
  color: var(--ink);
  font-weight: 600;
}

.explain {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.explain-chip {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.04);
  color: var(--ink-soft);
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}

.card {
  border: 1px solid var(--glass-border);
  border-radius: 12px;
  background: var(--glass);
  padding: 12px 14px;
  min-width: 0;
}

.card h3 {
  margin: 0;
  font-size: 15px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}

.card-head b {
  font-size: 12px;
  color: var(--muted);
  font-weight: 600;
}

.dim-summary {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--ink-soft);
  line-height: 1.5;
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 10px;
}

.kpi-row label {
  display: block;
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 2px;
}

.kpi-row b {
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.radar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.radar-chip {
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: #fff;
  border-radius: 999px;
  padding: 3px 9px;
  font-size: 11px;
  color: #86868b;
  cursor: default;
}

.radar-chip.on {
  border-color: rgba(52, 199, 89, 0.45);
  background: rgba(52, 199, 89, 0.12);
  color: #248a3d;
  font-weight: 650;
}

.dims {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dim-row {
  display: grid;
  grid-template-columns: 72px 36px 1fr;
  gap: 6px;
  font-size: 12px;
  align-items: baseline;
}

.dim-row em {
  font-style: normal;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.dim-row small {
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--ink-soft);
}

.card ul {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--ink-soft);
}

.card li.risk {
  color: #c45656;
}

.muted {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
}

.hot-sources {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(0, 113, 227, 0.08);
  color: #0071e3;
}

.decision-box {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.03);
  font-size: 13px;
}

.decision-box p {
  margin: 4px 0 0;
  color: var(--ink-soft);
}

.footnote {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--muted);
}

@media (max-width: 900px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }

  .hero {
    grid-template-columns: 1fr;
  }

  .kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
