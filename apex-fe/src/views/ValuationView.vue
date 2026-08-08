<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { saveObserve } from '../api/observe'
import { fetchValuation, fetchValuationScreen } from '../api/valuation'

const route = useRoute()
const router = useRouter()

const code = ref(String(route.query.code || '600519'))
const loading = ref(false)
const screenLoading = ref(false)
const detail = ref(null)
const screenRows = ref([])
const universe = ref('market')
const levelFilter = ref('')

const levelTone = computed(() => {
  const lv = detail.value?.level
  if (lv === 'UNDERVALUED' || lv === 'SLIGHTLY_CHEAP') return 'cheap'
  if (lv === 'OVERVALUED' || lv === 'SLIGHTLY_EXPENSIVE') return 'rich'
  if (lv === 'FAIR') return 'fair'
  return 'unknown'
})

async function loadDetail() {
  const c = String(code.value || '').trim()
  if (!c) {
    ElMessage.warning('请输入证券代码')
    return
  }
  loading.value = true
  try {
    const res = await fetchValuation(c)
    detail.value = res.data || null
    router.replace({ query: { ...route.query, code: c } })
  } catch (e) {
    detail.value = null
    ElMessage.error(e.message || '估值加载失败')
  } finally {
    loading.value = false
  }
}

async function loadScreen() {
  screenLoading.value = true
  try {
    const res = await fetchValuationScreen({
      universe: universe.value,
      limit: 40,
      level: levelFilter.value || undefined,
    })
    screenRows.value = res.data || []
  } catch (e) {
    screenRows.value = []
    ElMessage.error(e.message || '筛选失败')
  } finally {
    screenLoading.value = false
  }
}

function openRow(row) {
  if (!row?.code) return
  code.value = row.code
  loadDetail()
}

function goStock() {
  const c = detail.value?.code || code.value
  if (c) router.push({ path: `/stock/${c}` })
}

/** 当前估值标的加入观察池 */
async function addCurrentToObserve() {
  const c = detail.value?.code || code.value?.trim()
  if (!c) return
  try {
    await saveObserve({
      code: c,
      name: detail.value?.name || '',
      status: 'WATCHING',
      reason: `估值${detail.value?.levelLabel || ''}`.trim() || '估值关注',
      tags: 'valuation',
      note: detail.value?.actionHint || '',
      priority: detail.value?.level === 'UNDERVALUED' ? 4 : 3,
    })
    ElMessage.success('已加入观察池')
  } catch (e) {
    ElMessage.error(e.message || '加入失败')
  }
}

async function addScreenObserve(row, e) {
  e?.stopPropagation?.()
  if (!row?.code) return
  try {
    await saveObserve({
      code: row.code,
      name: row.name || '',
      status: 'WATCHING',
      reason: `估值筛选 ${row.levelLabel || ''}`.trim() || '估值筛选',
      tags: 'valuation',
      priority: row.level === 'UNDERVALUED' ? 4 : 3,
    })
    ElMessage.success(`${row.code} 已进观察池`)
  } catch (err) {
    ElMessage.error(err.message || '加入失败')
  }
}

function fmt(v, d = 2) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toFixed(d)
}

function fmtPct(v) {
  if (v == null || v === '') return '-'
  return fmt(v, 1) + '%'
}

function levelClass(level) {
  if (level === 'UNDERVALUED' || level === 'SLIGHTLY_CHEAP') return 'chip cheap'
  if (level === 'OVERVALUED' || level === 'SLIGHTLY_EXPENSIVE') return 'chip rich'
  if (level === 'FAIR') return 'chip fair'
  return 'chip'
}

/** 维度分进度条颜色：高分偏绿、中位蓝、低分琥珀/红 */
function dimBarColor(score) {
  const n = Number(score)
  if (Number.isNaN(n)) return 'var(--accent)'
  if (n >= 70) return 'var(--down)'
  if (n >= 55) return 'var(--accent)'
  if (n >= 40) return 'var(--warn)'
  return 'var(--up)'
}

watch(
  () => route.query.code,
  (v) => {
    if (v && String(v) !== code.value) {
      code.value = String(v)
      loadDetail()
    }
  },
)

onMounted(() => {
  loadDetail()
  loadScreen()
})
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="header">
      <div>
        <p class="eyebrow">灵枢 · Valuation</p>
        <h1>估值系统</h1>
        <p>行业相对 PE/PB · PEG · 简化内在价值 · 财务质量 → 低估 / 合理 / 高估</p>
      </div>
      <div class="actions">
        <el-input
          v-model="code"
          style="width: 140px"
          placeholder="证券代码"
          @keyup.enter="loadDetail"
        />
        <el-button type="primary" @click="loadDetail">评估</el-button>
        <el-button @click="goStock" :disabled="!detail?.code">个股详情</el-button>
        <el-button type="warning" plain :disabled="!detail?.code" @click="addCurrentToObserve">加入观察池</el-button>
        <el-button plain @click="router.push('/decision')">决策</el-button>
        <el-button text @click="router.push('/observe')">看观察池</el-button>
      </div>
    </header>

    <section v-if="detail" class="hero" :class="levelTone">
      <div class="hero-main">
        <div class="title-row">
          <h2>{{ detail.name || '—' }} <span class="code">{{ detail.code }}</span></h2>
          <span class="level">{{ detail.levelLabel || '—' }}</span>
        </div>
        <p class="summary">{{ detail.summary }}</p>
        <p class="action">{{ detail.actionHint }}</p>
      </div>
      <div class="hero-score">
        <div class="score-num">{{ fmt(detail.score, 1) }}</div>
        <div class="score-label">性价比综合分</div>
        <div class="price-line">
          现价 {{ fmt(detail.latestPrice) }}
          <span v-if="detail.fairPriceMid"> · 中枢 {{ fmt(detail.fairPriceMid) }}</span>
          <span v-if="detail.marginOfSafety != null"> · 安全边际 {{ fmtPct(detail.marginOfSafety) }}</span>
        </div>
      </div>
    </section>

    <div class="grid" v-if="detail">
      <section class="card-block">
        <h3>关键指标</h3>
        <div class="meta">
          <div><label>行业</label><span>{{ detail.industry || '-' }}</span></div>
          <div><label><TermTip term="pe_dynamic">市盈率（动）</TermTip></label><b>{{ fmt(detail.peDynamic) }}</b></div>
          <div><label><TermTip term="pe_static">市盈率（静）</TermTip></label><b>{{ fmt(detail.peStatic) }}</b></div>
          <div><label><TermTip term="pe_ttm">市盈率（TTM）</TermTip></label><b>{{ fmt(detail.peTtm) }}</b></div>
          <div><label>行业PE中位</label><span>{{ fmt(detail.industryPeMedian) }}</span></div>
          <div><label><TermTip term="pe_percentile">PE分位</TermTip></label><span>{{ detail.pePercentile != null ? fmtPct(detail.pePercentile) : '-' }}</span></div>
          <div><label><TermTip term="pb">PB</TermTip></label><b>{{ fmt(detail.pb) }}</b></div>
          <div><label>行业PB中位</label><span>{{ fmt(detail.industryPbMedian) }}</span></div>
          <div><label>PB分位</label><span>{{ detail.pbPercentile != null ? fmtPct(detail.pbPercentile) : '-' }}</span></div>
          <div><label><TermTip term="peg">PEG</TermTip></label><b>{{ fmt(detail.peg) }}</b></div>
          <div><label>盈利收益率</label><span>{{ detail.earningsYield != null ? fmtPct(detail.earningsYield) : '-' }}</span></div>
          <div><label><TermTip term="fair_value">公允PE</TermTip></label><span>{{ fmt(detail.fairPe, 1) }}</span></div>
          <div><label>公允PB</label><span>{{ fmt(detail.fairPb) }}</span></div>
          <div><label>同业样本</label><span>{{ detail.industryPeerCount ?? '-' }}</span></div>
          <div><label>报告期</label><span>{{ detail.reportDate || '-' }}</span></div>
          <div><label><TermTip term="roe">ROE</TermTip></label><span>{{ fmtPct(detail.roe) }}</span></div>
          <div><label>资产负债率</label><span>{{ fmtPct(detail.debtRatio) }}</span></div>
          <div><label>净利同比</label><span>{{ fmtPct(detail.netProfitYoy) }}</span></div>
          <div><label>营收同比</label><span>{{ fmtPct(detail.revenueYoy) }}</span></div>
        </div>
        <p class="note">{{ detail.dataNote }}</p>
      </section>

      <section class="card-block">
        <h3>维度评分</h3>
        <div v-if="detail.dimensions?.length" class="dims">
          <div v-for="d in detail.dimensions" :key="d.key" class="dim">
            <div class="dim-head">
              <span>{{ d.name }}</span>
              <b>{{ fmt(d.score, 1) }}</b>
            </div>
            <el-progress
              :percentage="Math.min(100, Math.max(0, Number(d.score) || 0))"
              :stroke-width="8"
              :show-text="false"
              :color="dimBarColor(d.score)"
            />
            <div class="dim-verdict">{{ d.verdict }}</div>
            <div class="dim-detail">{{ d.detail }}</div>
          </div>
        </div>
        <el-empty v-else description="暂无维度数据" />
      </section>
    </div>

    <div class="grid two" v-if="detail">
      <section class="card-block">
        <h3>估值亮点</h3>
        <ul class="points">
          <li v-for="(p, i) in detail.bullPoints || []" :key="'b' + i">{{ p }}</li>
        </ul>
      </section>
      <section class="card-block">
        <h3>风险提示</h3>
        <ul class="points bear">
          <li v-for="(p, i) in detail.bearPoints || []" :key="'r' + i">{{ p }}</li>
        </ul>
      </section>
    </div>

    <section class="card-block" v-if="detail?.assumptions?.length">
      <h3>模型假设</h3>
      <ul class="points muted">
        <li v-for="(a, i) in detail.assumptions" :key="'a' + i">{{ a }}</li>
      </ul>
      <div class="fair-band" v-if="detail.fairPriceLow != null">
        公允区间
        <b>{{ fmt(detail.fairPriceLow) }}</b> ~
        <b>{{ fmt(detail.fairPriceMid) }}</b> ~
        <b>{{ fmt(detail.fairPriceHigh) }}</b>
      </div>
    </section>

    <section class="card-block screen">
      <div class="screen-head">
        <h3>估值筛选</h3>
        <div class="screen-actions">
          <el-select v-model="universe" style="width: 130px" @change="loadScreen">
            <el-option label="全市场取样" value="market" />
            <el-option label="自选" value="watchlist" />
            <el-option label="观察池" value="observe" />
          </el-select>
          <el-select v-model="levelFilter" clearable placeholder="档位" style="width: 120px" @change="loadScreen">
            <el-option label="明显低估" value="UNDERVALUED" />
            <el-option label="偏低" value="SLIGHTLY_CHEAP" />
            <el-option label="合理" value="FAIR" />
            <el-option label="偏高" value="SLIGHTLY_EXPENSIVE" />
            <el-option label="明显高估" value="OVERVALUED" />
          </el-select>
          <el-button :loading="screenLoading" @click="loadScreen">刷新</el-button>
        </div>
      </div>
      <el-table
        :data="screenRows"
        size="small"
        stripe
        v-loading="screenLoading"
        empty-text="暂无筛选结果，可切换全市场/自选/观察池或档位后刷新"
        @row-click="openRow"
      >
        <el-table-column prop="code" label="代码" width="90" />
        <el-table-column prop="name" label="名称" width="100" />
        <el-table-column prop="industry" label="行业" min-width="110" show-overflow-tooltip />
        <el-table-column prop="score" label="综合分" width="80" sortable>
          <template #default="{ row }">{{ fmt(row.score, 1) }}</template>
        </el-table-column>
        <el-table-column label="结论" width="100">
          <template #default="{ row }">
            <span :class="levelClass(row.level)">{{ row.levelLabel }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="peTtm" label="PE" width="70">
          <template #default="{ row }">{{ fmt(row.peTtm, 1) }}</template>
        </el-table-column>
        <el-table-column prop="pb" label="PB" width="70">
          <template #default="{ row }">{{ fmt(row.pb) }}</template>
        </el-table-column>
        <el-table-column prop="peg" label="PEG" width="70">
          <template #default="{ row }">{{ fmt(row.peg) }}</template>
        </el-table-column>
        <el-table-column prop="marginOfSafety" label="安全边际" width="90">
          <template #default="{ row }">{{ row.marginOfSafety != null ? fmtPct(row.marginOfSafety) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="88" fixed="right">
          <template #default="{ row }">
            <el-button link type="warning" @click="addScreenObserve(row, $event)">观察</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.page {
  padding: 20px 24px 40px;
  max-width: 1200px;
}
.eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.04em;
  color: var(--accent);
  text-transform: uppercase;
}
.header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 18px;
}
.header h1 {
  margin: 0 0 6px;
  font-size: 1.55rem;
  letter-spacing: -0.02em;
}
.header p {
  margin: 0;
  color: #6e6e73;
  font-size: 0.92rem;
}
.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.hero {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 22px;
  border-radius: 14px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, #f5f5f7 0%, #e8eef5 100%);
  border: 1px solid #e5e5ea;
}
.hero.cheap {
  background: linear-gradient(135deg, #eef7f0 0%, #e3f0ea 100%);
  border-color: #c8ddd0;
}
.hero.rich {
  background: linear-gradient(135deg, #faf3f0 0%, #f3e8e4 100%);
  border-color: #e8d5ce;
}
.hero.fair {
  background: linear-gradient(135deg, #f4f6fa 0%, #e9eef8 100%);
}
.hero-main {
  flex: 1;
  min-width: 0;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.title-row h2 {
  margin: 0;
  font-size: 1.35rem;
}
.title-row .code {
  color: #86868b;
  font-weight: 500;
  font-size: 1rem;
}
.level {
  font-size: 0.85rem;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.06);
  font-weight: 600;
}
.summary {
  margin: 10px 0 6px;
  font-size: 0.98rem;
  line-height: 1.45;
}
.action {
  margin: 0;
  color: #515154;
  font-size: 0.9rem;
}
.hero-score {
  text-align: right;
  min-width: 160px;
}
.score-num {
  font-size: 2.4rem;
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1;
}
.score-label {
  color: #6e6e73;
  font-size: 0.8rem;
  margin: 4px 0 10px;
}
.price-line {
  font-size: 0.82rem;
  color: #515154;
}
.grid {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}
.grid.two {
  grid-template-columns: 1fr 1fr;
}
.card-block {
  background: #fff;
  border: 1px solid #ececf0;
  border-radius: 12px;
  padding: 16px 18px;
  margin-bottom: 14px;
}
.card-block h3 {
  margin: 0 0 12px;
  font-size: 1rem;
}
.meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px 12px;
}
.meta label {
  display: block;
  color: #86868b;
  font-size: 0.75rem;
  margin-bottom: 2px;
}
.meta span,
.meta b {
  font-size: 0.92rem;
}
.note {
  margin: 12px 0 0;
  color: #86868b;
  font-size: 0.8rem;
}
.dims {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.dim-head {
  display: flex;
  justify-content: space-between;
  font-size: 0.9rem;
  margin-bottom: 4px;
}
.dim-verdict {
  margin-top: 4px;
  font-size: 0.85rem;
  font-weight: 600;
}
.dim-detail {
  color: #6e6e73;
  font-size: 0.78rem;
  margin-top: 2px;
  line-height: 1.4;
}
.points {
  margin: 0;
  padding-left: 18px;
  line-height: 1.55;
  font-size: 0.9rem;
}
.points.bear li {
  color: #6b3f35;
}
.points.muted {
  color: #6e6e73;
  font-size: 0.85rem;
}
.fair-band {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #f0f0f2;
  font-size: 0.9rem;
}
.screen-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.screen-head h3 {
  margin: 0;
}
.screen-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.chip {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  background: #f0f0f2;
  font-size: 0.78rem;
}
.chip.cheap {
  background: #e5f3ea;
  color: #1f6b3a;
}
.chip.rich {
  background: #f8e9e4;
  color: #8a3b28;
}
.chip.fair {
  background: #e8eef8;
  color: #2f4d7a;
}
:deep(.el-table__row) {
  cursor: pointer;
}
@media (max-width: 900px) {
  .grid,
  .grid.two {
    grid-template-columns: 1fr;
  }
  .meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .hero {
    flex-direction: column;
  }
  .hero-score {
    text-align: left;
  }
}
</style>
