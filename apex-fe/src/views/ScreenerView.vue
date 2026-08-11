<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchScreenerMarket, fetchScreenerMeta, runScreener } from '../api/screener'
import { batchBacktest } from '../api/backtest'
import { saveObserve } from '../api/observe'
import { resolveActionColumnVisible } from '../utils/responsiveTable.js'
import { securityMarketBadge } from '../utils/securityMarket.js'
import { useSessionViewState } from '../utils/viewState.js'

const router = useRouter()
const loading = ref(false)
const marketLoading = ref(false)
const screeningActive = ref(false)
const viewportWidth = ref(window.innerWidth)
const showActionColumn = computed(() => resolveActionColumnVisible(viewportWidth.value))

function syncViewportWidth() {
  viewportWidth.value = window.innerWidth
}

const meta = ref({
  marketCount: null,
  universeCount: null,
  universeBatchNo: null,
  note: '',
})

function emptyForm() {
  return {
    scope: '__MARKET__',
    groupName: '我的自选',
    peMin: '',
    peMax: '',
    pbMin: '',
    pbMax: '',
    industry: '',
    pctChgMin: '',
    pctChgMax: '',
    pctChg20Min: '',
    pctChg20Max: '',
    minCircMvYi: '',
    maxCircMvYi: '',
    minBars: '',
    excludeSt: true,
    excludeLimitUp: false,
    excludeLimitDown: false,
    minVolumeRatio: '',
    minUpDays: '',
    rs20Min: '',
    maxAtrPct: '',
    minAtrPct: '',
    limit: 50,
  }
}

const form = ref(emptyForm())
const rows = ref([])
const batchRows = ref([])

const marketKeyword = ref('')
const marketPage = ref(1)
const marketSize = ref(50)
const marketTotal = ref(0)
const marketRows = ref([])

useSessionViewState('screener', {
  form,
  marketKeyword,
  marketPage,
  marketSize,
})

const displayRows = computed(() => {
  const sourceRows = screeningActive.value ? rows.value : marketRows.value
  const keyword = String(marketKeyword.value || '').trim().toLowerCase()
  if (!screeningActive.value || !keyword) return sourceRows
  return sourceRows.filter((row) =>
    String(row.code || '').toLowerCase().includes(keyword)
      || String(row.name || '').toLowerCase().includes(keyword),
  )
})

function hasAdvancedFilters() {
  if (form.value.scope !== '__MARKET__') return true
  if (form.value.excludeLimitUp || form.value.excludeLimitDown) return true
  return [
    form.value.peMin,
    form.value.peMax,
    form.value.pbMin,
    form.value.pbMax,
    form.value.industry,
    form.value.pctChgMin,
    form.value.pctChgMax,
    form.value.pctChg20Min,
    form.value.pctChg20Max,
    form.value.minCircMvYi,
    form.value.maxCircMvYi,
    form.value.minBars,
    form.value.minVolumeRatio,
    form.value.minUpDays,
    form.value.rs20Min,
    form.value.maxAtrPct,
    form.value.minAtrPct,
  ].some((value) => value !== '' && value != null)
}

function numOrNull(v) {
  if (v === '' || v == null) return null
  const n = Number(v)
  return Number.isNaN(n) ? null : n
}

function resolveGroupName() {
  if (form.value.scope === '__MARKET__') return '__MARKET__'
  return String(form.value.groupName || '').trim() || '我的自选'
}

async function loadMeta() {
  try {
    const res = await fetchScreenerMeta()
    meta.value = res.data || meta.value
  } catch {
    // 摘要失败不阻断选股
  }
}

async function onRun() {
  loading.value = true
  try {
    const res = await runScreener({
      groupName: resolveGroupName(),
      peMin: numOrNull(form.value.peMin),
      peMax: numOrNull(form.value.peMax),
      pbMin: numOrNull(form.value.pbMin),
      pbMax: numOrNull(form.value.pbMax),
      industry: form.value.industry || null,
      pctChgMin: numOrNull(form.value.pctChgMin),
      pctChgMax: numOrNull(form.value.pctChgMax),
      pctChg20Min: numOrNull(form.value.pctChg20Min),
      pctChg20Max: numOrNull(form.value.pctChg20Max),
      minCircMv: form.value.minCircMvYi !== '' ? Number(form.value.minCircMvYi) * 1e8 : null,
      maxCircMv: form.value.maxCircMvYi !== '' ? Number(form.value.maxCircMvYi) * 1e8 : null,
      minBars: numOrNull(form.value.minBars),
      excludeSt: form.value.excludeSt,
      excludeLimitUp: form.value.excludeLimitUp,
      excludeLimitDown: form.value.excludeLimitDown,
      minVolumeRatio: numOrNull(form.value.minVolumeRatio),
      minUpDays: numOrNull(form.value.minUpDays),
      rs20Min: numOrNull(form.value.rs20Min),
      maxAtrPct: numOrNull(form.value.maxAtrPct),
      minAtrPct: numOrNull(form.value.minAtrPct),
      limit: Number(form.value.limit || 50),
    })
    rows.value = res.data || []
    screeningActive.value = true
    const scopeLabel = form.value.scope === '__MARKET__' ? '全市场' : `自选「${resolveGroupName()}」`
    ElMessage.success(`${scopeLabel}选出 ${rows.value.length} 只`)
    loadMeta()
  } catch (e) {
    ElMessage.error(e.message || '选股失败')
  } finally {
    loading.value = false
  }
}

function onReset() {
  form.value = emptyForm()
  marketKeyword.value = ''
  screeningActive.value = false
  rows.value = []
  batchRows.value = []
  ElMessage.info('已清空条件，默认全市场')
  loadMarket(true)
}

function onQuery() {
  if (hasAdvancedFilters()) {
    onRun()
    return
  }
  screeningActive.value = false
  loadMarket(true)
}

async function loadMarket(resetPage = false) {
  if (resetPage) marketPage.value = 1
  marketLoading.value = true
  try {
    const res = await fetchScreenerMarket({
      keyword: marketKeyword.value || undefined,
      page: marketPage.value,
      size: marketSize.value,
      excludeSt: form.value.excludeSt,
    })
    const page = res.data || {}
    marketRows.value = page.records || []
    marketTotal.value = Number(page.total || 0)
    marketPage.value = Number(page.current || marketPage.value)
    marketSize.value = Number(page.size || marketSize.value)
  } catch (e) {
    marketRows.value = []
    marketTotal.value = 0
    ElMessage.error(e.message || '加载全市场失败')
  } finally {
    marketLoading.value = false
  }
}

function onMarketPageChange(p) {
  marketPage.value = p
  loadMarket(false)
}

function onMarketSizeChange(s) {
  marketSize.value = s
  loadMarket(true)
}

async function addObserve(row) {
  if (!row?.code) return
  try {
    await saveObserve({
      code: row.code,
      name: row.name || '',
      status: 'WATCHING',
      reason: screeningActive.value ? '条件选股' : '全市场浏览',
      tags: screeningActive.value ? 'screener' : 'market',
    })
    ElMessage.success(`${row.code} 已进观察池`)
  } catch (e) {
    ElMessage.error(e.message || '加入失败')
  }
}

async function onBatchBacktest() {
  if (!displayRows.value.length) {
    ElMessage.warning('请先选股')
    return
  }
  loading.value = true
  try {
    const codes = displayRows.value.slice(0, 8).map((r) => r.code)
    const res = await batchBacktest({
      codes,
      strategyId: 'S1',
      beginDate: '2025-01-01',
      endDate: '2026-08-01',
      limit: 8,
    })
    batchRows.value = res.data || []
    ElMessage.success('批量回测完成')
  } catch (e) {
    ElMessage.error(e.message || '批量回测失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadMeta()
  loadMarket(true)
  window.addEventListener('resize', syncViewportWidth)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewportWidth)
})
</script>

<template>
  <div class="page screener-page">
    <header class="header">
      <div>
        <p class="eyebrow">灵枢 · Screener</p>
        <h1>股票</h1>
        <p class="meta-line">
          <span class="chip">全市场 <b>{{ meta.marketCount ?? '—' }}</b></span>
          <span class="chip pool">股票池 <b>{{ meta.universeCount ?? '—' }}</b></span>
          <span v-if="meta.universeBatchNo" class="muted">批次 {{ meta.universeBatchNo }}</span>
        </p>
        <p v-if="meta.note" class="hint">{{ meta.note }}</p>
      </div>
      <div class="actions">
        <el-button @click="loadMeta">刷新数量</el-button>
      </div>
    </header>

    <section class="filter-panel" aria-label="股票筛选条件">
      <div class="filter-heading">
        <div>
          <h2>股票列表</h2>
          <span class="muted">
            {{ screeningActive ? `筛选结果 ${displayRows.length} 只` : `共 ${marketTotal} 只` }} · 池内标「池」
          </span>
        </div>
        <div class="actions row-actions">
          <el-button type="primary" :loading="loading || marketLoading" @click="onQuery">查询</el-button>
          <el-button @click="onReset">重置</el-button>
          <el-button :disabled="!screeningActive" :loading="loading" @click="onBatchBacktest">批量回测前8</el-button>
        </div>
      </div>

      <el-form :inline="true" class="form" @submit.prevent="onQuery">
        <el-form-item label="代码/名称">
          <el-input v-model="marketKeyword" clearable style="width: 140px" @keyup.enter="onQuery" />
        </el-form-item>
        <el-form-item label="范围">
          <el-select v-model="form.scope" style="width: 120px">
            <el-option
              :label="meta.marketCount != null ? `全部市场 (${meta.marketCount})` : '全部市场'"
              value="__MARKET__"
            />
            <el-option label="自选分组" value="__WATCH__" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scope === '__WATCH__'" label="分组">
          <el-input v-model="form.groupName" style="width: 120px" placeholder="我的自选" clearable />
        </el-form-item>
        <el-form-item label="PE≥"><el-input v-model="form.peMin" clearable style="width: 70px" /></el-form-item>
        <el-form-item label="PE≤"><el-input v-model="form.peMax" clearable style="width: 70px" /></el-form-item>
        <el-form-item label="PB≥"><el-input v-model="form.pbMin" clearable style="width: 70px" /></el-form-item>
        <el-form-item label="PB≤"><el-input v-model="form.pbMax" clearable style="width: 70px" /></el-form-item>
        <el-form-item label="行业"><el-input v-model="form.industry" clearable style="width: 120px" placeholder="如 银行" /></el-form-item>
        <el-form-item label="今日≥"><el-input v-model="form.pctChgMin" clearable style="width: 70px" placeholder="%" /></el-form-item>
        <el-form-item label="今日≤"><el-input v-model="form.pctChgMax" clearable style="width: 70px" placeholder="%" /></el-form-item>
        <el-form-item label="20日≥"><el-input v-model="form.pctChg20Min" clearable style="width: 70px" placeholder="%" /></el-form-item>
        <el-form-item label="20日≤"><el-input v-model="form.pctChg20Max" clearable style="width: 70px" placeholder="%" /></el-form-item>
        <el-form-item label="流通≥亿"><el-input v-model="form.minCircMvYi" clearable style="width: 80px" /></el-form-item>
        <el-form-item label="流通≤亿"><el-input v-model="form.maxCircMvYi" clearable style="width: 80px" /></el-form-item>
        <el-form-item label="K线≥"><el-input v-model="form.minBars" clearable style="width: 80px" placeholder="可选" /></el-form-item>
        <el-form-item>
          <template #label><TermTip term="volume_ratio">量比≥</TermTip></template>
          <el-input v-model="form.minVolumeRatio" clearable style="width: 70px" placeholder="可选" />
        </el-form-item>
        <el-form-item>
          <template #label><TermTip term="up_days">连涨≥</TermTip></template>
          <el-input v-model="form.minUpDays" clearable style="width: 70px" placeholder="天" />
        </el-form-item>
        <el-form-item>
          <template #label><TermTip term="rs20">RS20≥</TermTip></template>
          <el-input v-model="form.rs20Min" clearable style="width: 70px" placeholder="相对300" />
        </el-form-item>
        <el-form-item>
          <template #label><TermTip term="atr_pct">ATR%≤</TermTip></template>
          <el-input v-model="form.maxAtrPct" clearable style="width: 70px" placeholder="可选" />
        </el-form-item>
        <el-form-item>
          <template #label><TermTip term="atr_pct">ATR%≥</TermTip></template>
          <el-input v-model="form.minAtrPct" clearable style="width: 70px" />
        </el-form-item>
        <el-form-item label="条数"><el-input v-model="form.limit" style="width: 70px" /></el-form-item>
        <el-form-item><el-checkbox v-model="form.excludeSt">排除ST</el-checkbox></el-form-item>
        <el-form-item><el-checkbox v-model="form.excludeLimitUp">排除涨停</el-checkbox></el-form-item>
        <el-form-item><el-checkbox v-model="form.excludeLimitDown">排除跌停</el-checkbox></el-form-item>
      </el-form>
    </section>

    <el-table
      v-loading="loading || marketLoading"
      class="screener-table"
      :data="displayRows"
      stripe
      style="width: 100%"
      empty-text="暂无符合条件的股票"
    >
        <el-table-column prop="code" label="代码" min-width="96">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="120">
          <template #default="{ row }">
            <span class="security-name">
              <span>{{ row.name || '-' }}</span>
              <span
                v-if="securityMarketBadge(row)"
                class="market-badge"
                :class="`is-${securityMarketBadge(row).tone}`"
                :title="securityMarketBadge(row).title"
              >{{ securityMarketBadge(row).label }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column v-if="!screeningActive" label="股票池" width="74">
          <template #default="{ row }">
            <el-tag v-if="row.inUniverse" size="small" type="success" effect="plain">池</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="latestPrice" label="现价" min-width="84" />
        <el-table-column prop="pctChg" label="今日%" min-width="80">
          <template #default="{ row }">
            <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">{{ row.pctChg ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="screeningActive" prop="pctChg5" label="5日%" min-width="80">
          <template #default="{ row }">
            <span :class="Number(row.pctChg5) >= 0 ? 'up' : 'down'">{{ row.pctChg5 ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="screeningActive" prop="pctChg20" label="20日%" min-width="80">
          <template #default="{ row }">
            <span :class="Number(row.pctChg20) >= 0 ? 'up' : 'down'">{{ row.pctChg20 ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="screeningActive" prop="volumeRatio" min-width="72">
          <template #header><TermTip term="volume_ratio">量比</TermTip></template>
        </el-table-column>
        <el-table-column v-if="screeningActive" prop="upDays" min-width="64">
          <template #header><TermTip term="up_days">连涨</TermTip></template>
        </el-table-column>
        <el-table-column v-if="screeningActive" prop="rs20VsHs300" min-width="72" sortable>
          <template #header><TermTip term="rs20">RS20</TermTip></template>
        </el-table-column>
        <el-table-column v-if="screeningActive" prop="atrPct" min-width="72" sortable>
          <template #header><TermTip term="atr_pct">ATR%</TermTip></template>
        </el-table-column>
        <el-table-column prop="peTtm" min-width="72" sortable>
          <template #header><TermTip term="pe_ttm">PE</TermTip></template>
        </el-table-column>
        <el-table-column prop="pb" min-width="72" sortable>
          <template #header><TermTip term="pb">PB</TermTip></template>
        </el-table-column>
        <el-table-column prop="circMv" label="流通(亿)" min-width="88">
          <template #default="{ row }">
            {{ row.circMv != null ? (Number(row.circMv) / 1e8).toFixed(1) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="industry" label="行业" min-width="110" show-overflow-tooltip />
        <el-table-column prop="barCount" label="K线" min-width="72" />
        <el-table-column v-if="showActionColumn" label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link @click="router.push({ path: '/backtest', query: { code: row.code } })">回测</el-button>
            <el-button link type="warning" @click="addObserve(row)">观察</el-button>
            <el-button link @click="router.push({ path: '/paper', query: { code: row.code, side: 'BUY' } })">模拟</el-button>
          </template>
        </el-table-column>
    </el-table>

    <div v-if="!screeningActive" class="pager">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="marketTotal"
        :current-page="marketPage"
        :page-size="marketSize"
        :page-sizes="[50, 100, 200]"
        @current-change="onMarketPageChange"
        @size-change="onMarketSizeChange"
      />
    </div>

    <h3 v-if="batchRows.length">批量回测排名</h3>
    <el-table v-if="batchRows.length" :data="batchRows" size="small" style="width: 100%">
        <el-table-column prop="code" label="代码" width="100" />
        <el-table-column prop="jobId" label="任务" width="80" />
        <el-table-column prop="totalReturn" label="收益" width="100">
          <template #default="{ row }">
            {{ row.totalReturn != null ? (Number(row.totalReturn) * 100).toFixed(2) + '%' : row.error || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="maxDrawdown" label="回撤" width="100">
          <template #default="{ row }">
            {{ row.maxDrawdown != null ? (Number(row.maxDrawdown) * 100).toFixed(2) + '%' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="sharpe" label="夏普" width="90" />
        <el-table-column prop="sortino" label="Sortino" width="90" />
        <el-table-column prop="tradeCount" label="成交" width="80" />
        <el-table-column label="详情" width="100">
          <template #default="{ row }">
            <el-button v-if="row.jobId" link type="primary" @click="router.push({ path: '/backtest', query: { code: row.code } })">查看</el-button>
          </template>
        </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.04em;
  color: var(--accent);
  text-transform: uppercase;
}

.screener-page {
  min-height: calc(100vh - 48px);
}

.meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin: 6px 0 0;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  font-size: 13px;
}

.chip.pool {
  background: color-mix(in srgb, #16a34a 14%, transparent);
}

.chip b {
  font-variant-numeric: tabular-nums;
}

.hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--muted, #888);
  max-width: 720px;
}

.muted {
  color: var(--muted, #888);
  font-size: 12px;
}

.filter-panel {
  margin: 4px 0 12px;
  padding: 12px 0 2px;
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
}

.filter-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

.filter-heading h2 {
  margin: 0 0 3px;
  font-size: 18px;
  line-height: 1.35;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 0;
}

.form {
  margin-bottom: 0;
}

.screener-table {
  width: 100%;
}

.security-name {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
}

.market-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 18px;
  width: 18px;
  height: 18px;
  border: 1px solid transparent;
  border-radius: 4px;
  box-sizing: border-box;
  font-size: 10px;
  font-weight: 750;
  line-height: 1;
}

.market-badge.is-star {
  color: #0a66c2;
  background: rgba(0, 113, 227, 0.09);
  border-color: rgba(0, 113, 227, 0.18);
}

.market-badge.is-chinext {
  color: #16775d;
  background: rgba(42, 157, 143, 0.1);
  border-color: rgba(42, 157, 143, 0.2);
}

.market-badge.is-bj {
  color: #a86400;
  background: rgba(255, 159, 10, 0.11);
  border-color: rgba(255, 159, 10, 0.22);
}

.market-badge.is-hk {
  color: #6b4fbb;
  background: rgba(107, 79, 187, 0.1);
  border-color: rgba(107, 79, 187, 0.2);
}

.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 820px) {
  .filter-heading {
    display: block;
  }

  .filter-heading .row-actions {
    margin-top: 10px;
  }

  .form :deep(.el-form-item) {
    margin-right: 10px;
    margin-bottom: 10px;
  }

  .pager {
    justify-content: flex-start;
    overflow-x: auto;
    padding-bottom: 4px;
  }
}
</style>
