<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchScreenerMarket, fetchScreenerMeta, runScreener } from '../api/screener'
import { batchBacktest } from '../api/backtest'
import { saveObserve } from '../api/observe'

const router = useRouter()
const loading = ref(false)
const marketLoading = ref(false)
const activeTab = ref('screen')

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
const marketExcludeSt = ref(true)
const marketPage = ref(1)
const marketSize = ref(50)
const marketTotal = ref(0)
const marketRows = ref([])

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
  rows.value = []
  batchRows.value = []
  ElMessage.info('已清空条件，默认全市场')
}

async function loadMarket(resetPage = false) {
  if (resetPage) marketPage.value = 1
  marketLoading.value = true
  try {
    const res = await fetchScreenerMarket({
      keyword: marketKeyword.value || undefined,
      page: marketPage.value,
      size: marketSize.value,
      excludeSt: marketExcludeSt.value,
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
      reason: activeTab.value === 'market' ? '全市场浏览' : '条件选股',
      tags: activeTab.value === 'market' ? 'market' : 'screener',
    })
    ElMessage.success(`${row.code} 已进观察池`)
  } catch (e) {
    ElMessage.error(e.message || '加入失败')
  }
}

async function onBatchBacktest() {
  if (!rows.value.length) {
    ElMessage.warning('请先选股')
    return
  }
  loading.value = true
  try {
    const codes = rows.value.slice(0, 8).map((r) => r.code)
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

watch(activeTab, (tab) => {
  if (tab === 'market' && !marketRows.value.length) {
    loadMarket(true)
  }
})

onMounted(() => {
  loadMeta()
})
</script>

<template>
  <div class="page screener-page">
    <header class="header">
      <div>
        <p class="eyebrow">灵枢 · Screener</p>
        <h1>条件选股</h1>
        <p class="meta-line">
          <span class="chip">全市场 <b>{{ meta.marketCount ?? '—' }}</b></span>
          <span class="chip pool">股票池 <b>{{ meta.universeCount ?? '—' }}</b></span>
          <span v-if="meta.universeBatchNo" class="muted">批次 {{ meta.universeBatchNo }}</span>
        </p>
        <p v-if="meta.note" class="hint">{{ meta.note }}</p>
      </div>
      <div class="actions">
        <el-button @click="loadMeta">刷新数量</el-button>
        <el-button plain @click="router.push('/signals')">信号 / 重建池</el-button>
        <el-button plain @click="router.push('/decision')">智能决策</el-button>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="tabs">
      <el-tab-pane label="条件选股" name="screen" />
      <el-tab-pane :label="`全市场股票${meta.marketCount != null ? ' (' + meta.marketCount + ')' : ''}`" name="market" />
    </el-tabs>

    <template v-if="activeTab === 'screen'">
      <div class="actions row-actions">
        <el-button type="primary" :loading="loading" @click="onRun">运行选股</el-button>
        <el-button @click="onReset">清空条件</el-button>
        <el-button :loading="loading" @click="onBatchBacktest">批量回测前8</el-button>
      </div>

      <el-form :inline="true" class="form">
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

      <div v-if="!loading && !rows.length" class="page-empty">
        <h3>暂无筛选结果</h3>
        <p>
          当前范围：{{ form.scope === '__MARKET__' ? `全部市场（库内 ${meta.marketCount ?? '—'} 只）` : `自选「${form.groupName || '我的自选'}」` }}。
          策略股票池 {{ meta.universeCount ?? '—' }} 只。设定条件后点「运行选股」。
        </p>
        <el-button type="primary" :loading="loading" @click="onRun">运行选股</el-button>
        <el-button plain @click="activeTab = 'market'">浏览全市场</el-button>
        <el-button plain @click="router.push('/valuation')">估值筛选</el-button>
      </div>

      <el-table
        v-else
        v-loading="loading"
        class="screener-table"
        :data="rows"
        stripe
        style="width: 100%"
      >
        <el-table-column prop="code" label="代码" min-width="96">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="108" />
        <el-table-column prop="latestPrice" label="现价" min-width="84" />
        <el-table-column prop="pctChg" label="今日%" min-width="80">
          <template #default="{ row }">
            <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">{{ row.pctChg ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="pctChg5" label="5日%" min-width="80">
          <template #default="{ row }">
            <span :class="Number(row.pctChg5) >= 0 ? 'up' : 'down'">{{ row.pctChg5 ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="pctChg20" label="20日%" min-width="80">
          <template #default="{ row }">
            <span :class="Number(row.pctChg20) >= 0 ? 'up' : 'down'">{{ row.pctChg20 ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="volumeRatio" min-width="72">
          <template #header><TermTip term="volume_ratio">量比</TermTip></template>
        </el-table-column>
        <el-table-column prop="upDays" min-width="64">
          <template #header><TermTip term="up_days">连涨</TermTip></template>
        </el-table-column>
        <el-table-column prop="rs20VsHs300" min-width="72" sortable>
          <template #header><TermTip term="rs20">RS20</TermTip></template>
        </el-table-column>
        <el-table-column prop="atrPct" min-width="72" sortable>
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
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link @click="router.push({ path: '/backtest', query: { code: row.code } })">回测</el-button>
            <el-button link type="warning" @click="addObserve(row)">观察</el-button>
            <el-button link @click="router.push({ path: '/paper', query: { code: row.code, side: 'BUY' } })">模拟</el-button>
          </template>
        </el-table-column>
      </el-table>

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
    </template>

    <template v-else>
      <div class="market-toolbar">
        <el-input
          v-model="marketKeyword"
          clearable
          placeholder="代码 / 名称"
          style="width: 200px"
          @keyup.enter="loadMarket(true)"
        />
        <el-checkbox v-model="marketExcludeSt" @change="loadMarket(true)">排除ST</el-checkbox>
        <el-button type="primary" :loading="marketLoading" @click="loadMarket(true)">查询</el-button>
        <span class="muted">共 {{ marketTotal }} 只 · 池内标「池」</span>
      </div>

      <el-table
        v-loading="marketLoading"
        class="screener-table"
        :data="marketRows"
        stripe
        style="width: 100%"
        empty-text="暂无股票，请先在「同步」补全市场代码"
      >
        <el-table-column prop="code" label="代码" min-width="96">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="market" label="市场" width="72" />
        <el-table-column label="股票池" width="80">
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
        <el-table-column prop="peTtm" label="PE" min-width="72" />
        <el-table-column prop="pb" label="PB" min-width="72" />
        <el-table-column prop="circMv" label="流通(亿)" min-width="88">
          <template #default="{ row }">
            {{ row.circMv != null ? (Number(row.circMv) / 1e8).toFixed(1) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="industry" label="行业" min-width="110" show-overflow-tooltip />
        <el-table-column prop="barCount" label="K线" min-width="72" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="warning" @click="addObserve(row)">观察</el-button>
            <el-button link @click="router.push({ path: '/paper', query: { code: row.code, side: 'BUY' } })">模拟</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
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
    </template>
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

.tabs {
  margin-top: 4px;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.form {
  margin-bottom: 12px;
}

.screener-table {
  width: 100%;
}

.market-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}

.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
