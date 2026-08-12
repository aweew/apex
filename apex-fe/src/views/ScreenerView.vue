<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Refresh, RefreshRight, Search } from '@element-plus/icons-vue'
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
const isMobileViewport = computed(() => viewportWidth.value <= 820)
const showActionColumn = computed(() => resolveActionColumnVisible(viewportWidth.value))
const mobileAdvancedOpen = ref(false)

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

const mobileAdvancedFilterCount = computed(() => {
  const values = [
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
  ]
  const filledCount = values.filter((value) => value !== '' && value != null).length
  const optionCount = [
    !form.value.excludeSt,
    form.value.excludeLimitUp,
    form.value.excludeLimitDown,
    Number(form.value.limit || 50) !== 50,
  ].filter(Boolean).length
  return filledCount + optionCount
})

const mobileTotalPages = computed(() => {
  return Math.max(1, Math.ceil(marketTotal.value / marketSize.value))
})

const mobilePageRange = computed(() => {
  if (!marketTotal.value) return '0 条'
  const start = (marketPage.value - 1) * marketSize.value + 1
  const end = Math.min(marketPage.value * marketSize.value, marketTotal.value)
  return `${start}-${end} / ${marketTotal.value}`
})

function hasAdvancedFilters() {
  if (form.value.scope !== '__MARKET__') return true
  if (form.value.excludeLimitUp || form.value.excludeLimitDown) return true
  if (Number(form.value.limit || 50) !== 50) return true
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

function formatPct(value) {
  if (value === '' || value == null) return '-'
  const percentage = Number(value)
  if (Number.isNaN(percentage)) return String(value)
  return `${percentage > 0 ? '+' : ''}${percentage.toFixed(2)}%`
}

function trendClass(value) {
  if (value === '' || value == null) return ''
  return Number(value) >= 0 ? 'up' : 'down'
}

function formatNumber(value, digits = 2) {
  if (value === '' || value == null) return '-'
  const numberValue = Number(value)
  if (Number.isNaN(numberValue)) return String(value)
  return numberValue.toFixed(digits)
}

function formatCircMv(value) {
  if (value === '' || value == null) return '-'
  const marketValue = Number(value)
  if (Number.isNaN(marketValue)) return String(value)
  return `${(marketValue / 1e8).toFixed(1)}亿`
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
  mobileAdvancedOpen.value = false
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
    <header class="header screener-header">
      <div>
        <p class="eyebrow">灵枢 · Screener</p>
        <h1>{{ isMobileViewport ? '股票筛选' : '股票' }}</h1>
        <p class="meta-line">
          <span class="chip">全市场 <b>{{ meta.marketCount ?? '—' }}</b></span>
          <span class="chip pool">股票池 <b>{{ meta.universeCount ?? '—' }}</b></span>
          <span v-if="meta.universeBatchNo" class="muted meta-batch">批次 {{ meta.universeBatchNo }}</span>
        </p>
        <p v-if="meta.note" class="hint">{{ meta.note }}</p>
      </div>
      <div class="actions header-refresh-actions">
        <el-button
          v-if="isMobileViewport"
          class="mobile-refresh-button"
          :icon="Refresh"
          aria-label="刷新股票数量"
          title="刷新股票数量"
          @click="loadMeta"
        />
        <el-button v-else @click="loadMeta">刷新数量</el-button>
      </div>
    </header>

    <section v-if="!isMobileViewport" class="filter-panel desktop-filter-panel" aria-label="股票筛选条件">
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

    <section v-else class="mobile-filter-surface" aria-label="股票筛选条件">
      <div class="mobile-filter-heading">
        <div>
          <h2>筛选条件</h2>
          <span>{{ screeningActive ? '当前为条件筛选结果' : '默认浏览全部市场' }}</span>
        </div>
        <span v-if="mobileAdvancedFilterCount" class="mobile-filter-count">
          {{ mobileAdvancedFilterCount }} 项已设置
        </span>
      </div>

      <form class="mobile-filter-form" @submit.prevent="onQuery">
        <label class="mobile-field mobile-keyword-field">
          <span>代码或名称</span>
          <el-input
            v-model="marketKeyword"
            clearable
            :prefix-icon="Search"
            placeholder="输入代码或股票名称"
            inputmode="search"
          />
        </label>

        <fieldset class="mobile-scope-field">
          <legend>筛选范围</legend>
          <div class="mobile-segmented" role="group" aria-label="筛选范围">
            <button
              type="button"
              :class="{ 'is-active': form.scope === '__MARKET__' }"
              :aria-pressed="form.scope === '__MARKET__'"
              @click="form.scope = '__MARKET__'"
            >
              全部市场
            </button>
            <button
              type="button"
              :class="{ 'is-active': form.scope === '__WATCH__' }"
              :aria-pressed="form.scope === '__WATCH__'"
              @click="form.scope = '__WATCH__'"
            >
              自选分组
            </button>
          </div>
        </fieldset>

        <label v-if="form.scope === '__WATCH__'" class="mobile-field">
          <span>自选分组</span>
          <el-input v-model="form.groupName" clearable placeholder="我的自选" />
        </label>

        <button
          type="button"
          class="advanced-filter-toggle"
          :aria-expanded="mobileAdvancedOpen"
          aria-controls="mobile-screener-advanced"
          @click="mobileAdvancedOpen = !mobileAdvancedOpen"
        >
          <span>
            更多条件
            <small v-if="mobileAdvancedFilterCount">{{ mobileAdvancedFilterCount }}</small>
          </span>
          <el-icon :class="{ 'is-open': mobileAdvancedOpen }"><ArrowDown /></el-icon>
        </button>

        <div v-show="mobileAdvancedOpen" id="mobile-screener-advanced" class="mobile-advanced-filters">
          <section class="mobile-filter-group">
            <h3>估值</h3>
            <div class="mobile-field-grid">
              <label class="mobile-field">
                <span>PE 最低</span>
                <el-input v-model="form.peMin" clearable inputmode="decimal" placeholder="不限" />
              </label>
              <label class="mobile-field">
                <span>PE 最高</span>
                <el-input v-model="form.peMax" clearable inputmode="decimal" placeholder="不限" />
              </label>
              <label class="mobile-field">
                <span>PB 最低</span>
                <el-input v-model="form.pbMin" clearable inputmode="decimal" placeholder="不限" />
              </label>
              <label class="mobile-field">
                <span>PB 最高</span>
                <el-input v-model="form.pbMax" clearable inputmode="decimal" placeholder="不限" />
              </label>
            </div>
          </section>

          <section class="mobile-filter-group">
            <h3>涨跌与行业</h3>
            <div class="mobile-field-grid">
              <label class="mobile-field mobile-field-wide">
                <span>行业</span>
                <el-input v-model="form.industry" clearable placeholder="如 银行" />
              </label>
              <label class="mobile-field">
                <span>今日最低</span>
                <el-input v-model="form.pctChgMin" clearable inputmode="decimal" placeholder="%" />
              </label>
              <label class="mobile-field">
                <span>今日最高</span>
                <el-input v-model="form.pctChgMax" clearable inputmode="decimal" placeholder="%" />
              </label>
              <label class="mobile-field">
                <span>20日最低</span>
                <el-input v-model="form.pctChg20Min" clearable inputmode="decimal" placeholder="%" />
              </label>
              <label class="mobile-field">
                <span>20日最高</span>
                <el-input v-model="form.pctChg20Max" clearable inputmode="decimal" placeholder="%" />
              </label>
            </div>
          </section>

          <section class="mobile-filter-group">
            <h3>规模与趋势</h3>
            <div class="mobile-field-grid">
              <label class="mobile-field">
                <span>流通市值最低</span>
                <el-input v-model="form.minCircMvYi" clearable inputmode="decimal" placeholder="亿元" />
              </label>
              <label class="mobile-field">
                <span>流通市值最高</span>
                <el-input v-model="form.maxCircMvYi" clearable inputmode="decimal" placeholder="亿元" />
              </label>
              <label class="mobile-field">
                <span>K线数量最低</span>
                <el-input v-model="form.minBars" clearable inputmode="numeric" placeholder="条" />
              </label>
              <label class="mobile-field">
                <span><TermTip term="volume_ratio">量比最低</TermTip></span>
                <el-input v-model="form.minVolumeRatio" clearable inputmode="decimal" placeholder="不限" />
              </label>
              <label class="mobile-field">
                <span><TermTip term="up_days">连续上涨最低</TermTip></span>
                <el-input v-model="form.minUpDays" clearable inputmode="numeric" placeholder="天" />
              </label>
              <label class="mobile-field">
                <span><TermTip term="rs20">RS20 最低</TermTip></span>
                <el-input v-model="form.rs20Min" clearable inputmode="decimal" placeholder="相对沪深300" />
              </label>
              <label class="mobile-field">
                <span><TermTip term="atr_pct">ATR% 最低</TermTip></span>
                <el-input v-model="form.minAtrPct" clearable inputmode="decimal" placeholder="不限" />
              </label>
              <label class="mobile-field">
                <span><TermTip term="atr_pct">ATR% 最高</TermTip></span>
                <el-input v-model="form.maxAtrPct" clearable inputmode="decimal" placeholder="不限" />
              </label>
            </div>
          </section>

          <section class="mobile-filter-group mobile-filter-options">
            <h3>结果与风险</h3>
            <label class="mobile-field mobile-limit-field">
              <span>最多返回</span>
              <el-input v-model="form.limit" inputmode="numeric" />
            </label>
            <div class="mobile-risk-options">
              <el-checkbox v-model="form.excludeSt">排除 ST</el-checkbox>
              <el-checkbox v-model="form.excludeLimitUp">排除涨停</el-checkbox>
              <el-checkbox v-model="form.excludeLimitDown">排除跌停</el-checkbox>
            </div>
          </section>
        </div>

        <div class="mobile-filter-actions">
          <el-button type="primary" native-type="submit" :icon="Search" :loading="loading || marketLoading">
            查询股票
          </el-button>
          <el-button :icon="RefreshRight" @click="onReset">重置</el-button>
        </div>
      </form>
    </section>

    <el-table
      v-if="!isMobileViewport"
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

    <section v-if="isMobileViewport" class="mobile-results-section" aria-labelledby="mobile-screener-results-title">
      <div class="mobile-results-heading">
        <div>
          <h2 id="mobile-screener-results-title">股票列表</h2>
          <span>
            {{ screeningActive ? `筛选结果 ${displayRows.length} 只` : `共 ${marketTotal} 只` }}
            <template v-if="!screeningActive"> · 池内标「池」</template>
          </span>
        </div>
        <el-button
          v-if="screeningActive"
          plain
          :loading="loading"
          @click="onBatchBacktest"
        >
          回测前 8
        </el-button>
      </div>

      <div v-loading="loading || marketLoading" class="screener-mobile-list">
        <button
          v-for="row in displayRows"
          :key="row.code"
          type="button"
          class="screener-mobile-card"
          @click="router.push(`/stock/${row.code}`)"
        >
          <span class="mobile-stock-heading">
            <span class="mobile-stock-identity">
              <strong>{{ row.name || row.code }}</strong>
              <span
                v-if="securityMarketBadge(row)"
                class="market-badge"
                :class="`is-${securityMarketBadge(row).tone}`"
                :title="securityMarketBadge(row).title"
              >{{ securityMarketBadge(row).label }}</span>
              <span v-if="!screeningActive && row.inUniverse" class="universe-badge">池</span>
              <small>{{ row.code }}</small>
            </span>
            <span class="mobile-stock-quote">
              <strong :class="trendClass(row.pctChg)">{{ formatPct(row.pctChg) }}</strong>
              <small>{{ row.latestPrice ?? '-' }}</small>
            </span>
          </span>

          <span class="mobile-stock-metrics" :class="{ 'is-screening': screeningActive }">
            <span>
              <small>PE</small>
              <b>{{ formatNumber(row.peTtm) }}</b>
            </span>
            <span>
              <small>PB</small>
              <b>{{ formatNumber(row.pb) }}</b>
            </span>
            <span>
              <small>流通</small>
              <b>{{ formatCircMv(row.circMv) }}</b>
            </span>
            <span>
              <small>行业</small>
              <b class="mobile-industry">{{ row.industry || '-' }}</b>
            </span>
            <template v-if="screeningActive">
              <span>
                <small>20日</small>
                <b :class="trendClass(row.pctChg20)">{{ formatPct(row.pctChg20) }}</b>
              </span>
              <span>
                <small>量比</small>
                <b>{{ formatNumber(row.volumeRatio) }}</b>
              </span>
              <span>
                <small>RS20</small>
                <b>{{ formatNumber(row.rs20VsHs300) }}</b>
              </span>
              <span>
                <small>ATR%</small>
                <b>{{ formatNumber(row.atrPct) }}</b>
              </span>
            </template>
          </span>
        </button>

        <div v-if="!displayRows.length && !loading && !marketLoading" class="mobile-empty-state">
          暂无符合条件的股票
        </div>
      </div>

      <div v-if="!screeningActive" class="mobile-pager" aria-label="股票列表分页">
        <el-button
          class="mobile-pager-button"
          :disabled="marketPage <= 1 || marketLoading"
          @click="onMarketPageChange(marketPage - 1)"
        >
          上一页
        </el-button>
        <span>
          <b>{{ marketPage }} / {{ mobileTotalPages }}</b>
          <small>{{ mobilePageRange }}</small>
        </span>
        <el-button
          class="mobile-pager-button"
          :disabled="marketPage >= mobileTotalPages || marketLoading"
          @click="onMarketPageChange(marketPage + 1)"
        >
          下一页
        </el-button>
      </div>
    </section>

    <div v-if="!isMobileViewport && !screeningActive" class="pager">
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

    <section v-if="isMobileViewport && batchRows.length" class="mobile-batch-results" aria-labelledby="mobile-batch-title">
      <h3 id="mobile-batch-title">批量回测排名</h3>
      <button
        v-for="(row, index) in batchRows"
        :key="`${row.code}-${row.jobId || index}`"
        type="button"
        :disabled="!row.jobId"
        @click="row.jobId && router.push({ path: '/backtest', query: { code: row.code } })"
      >
        <span class="mobile-batch-rank">{{ index + 1 }}</span>
        <span class="mobile-batch-stock">
          <b>{{ row.code }}</b>
          <small>{{ row.error || `${row.tradeCount ?? '-'} 笔成交` }}</small>
        </span>
        <span class="mobile-batch-return">
          <b :class="trendClass(row.totalReturn)">
            {{ row.totalReturn != null ? formatPct(Number(row.totalReturn) * 100) : '-' }}
          </b>
          <small>回撤 {{ row.maxDrawdown != null ? formatPct(Number(row.maxDrawdown) * 100) : '-' }}</small>
        </span>
      </button>
    </section>

    <h3 v-if="!isMobileViewport && batchRows.length">批量回测排名</h3>
    <el-table v-if="!isMobileViewport && batchRows.length" :data="batchRows" size="small" style="width: 100%">
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
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
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

.mobile-filter-surface,
.mobile-results-section {
  display: none;
}

@media (max-width: 820px) {
  .screener-page {
    overflow-x: clip;
  }

  .screener-header {
    position: relative;
    display: grid;
    grid-template-columns: minmax(0, 1fr) 44px;
    gap: 10px;
    margin-bottom: 12px;
  }

  .screener-header > div:first-child {
    min-width: 0;
  }

  .screener-header .eyebrow {
    display: none;
  }

  .screener-header h1 {
    font-size: 24px;
  }

  .header-refresh-actions {
    width: 44px !important;
    align-self: start;
  }

  .header-refresh-actions :deep(.mobile-refresh-button) {
    width: 44px;
    min-height: 44px;
    margin: 0;
    padding: 0;
    border-radius: 8px;
  }

  .meta-line {
    flex-wrap: nowrap;
    gap: 6px;
    min-width: 0;
    margin-top: 6px;
  }

  .chip {
    flex: 0 0 auto;
    padding: 3px 8px;
    border-radius: 6px;
    font-size: 12px;
  }

  .meta-batch {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .hint {
    display: -webkit-box;
    margin-top: 5px;
    overflow: hidden;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    font-size: 11px;
    line-height: 1.45;
  }

  .mobile-filter-surface {
    display: block;
    margin: 0 -2px 14px;
    padding: 12px;
    border: 1px solid var(--line);
    border-radius: 8px;
    background: var(--glass-strong);
    box-shadow: var(--shadow-soft);
  }

  .mobile-filter-heading,
  .mobile-results-heading {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 10px;
  }

  .mobile-filter-heading {
    margin-bottom: 12px;
  }

  .mobile-filter-heading h2,
  .mobile-results-heading h2 {
    margin: 0 0 2px;
    color: var(--ink);
    font-family: var(--font-display);
    font-size: 18px;
    font-weight: 650;
    line-height: 1.35;
    letter-spacing: 0;
  }

  .mobile-filter-heading > div > span,
  .mobile-results-heading > div > span {
    color: var(--muted);
    font-size: 11px;
    line-height: 1.35;
  }

  .mobile-filter-count {
    flex: 0 0 auto;
    padding: 3px 7px;
    border-radius: 5px;
    background: color-mix(in srgb, var(--accent) 10%, transparent);
    color: var(--accent);
    font-size: 11px;
    font-weight: 650;
  }

  .mobile-filter-form,
  .mobile-advanced-filters,
  .mobile-filter-group,
  .mobile-field {
    display: grid;
  }

  .mobile-filter-form {
    gap: 10px;
  }

  .mobile-field {
    min-width: 0;
    gap: 5px;
    color: var(--slate);
    font-size: 12px;
    font-weight: 600;
  }

  .mobile-field :deep(.el-input),
  .mobile-field :deep(.el-input__wrapper) {
    width: 100%;
    min-width: 0;
  }

  .mobile-field :deep(.el-input__wrapper) {
    min-height: 44px;
    border-radius: 7px;
    background: var(--paper);
    box-shadow: 0 0 0 1px var(--line) inset;
  }

  .mobile-field :deep(.el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 1px var(--accent) inset;
  }

  .mobile-scope-field {
    min-width: 0;
    margin: 0;
    padding: 0;
    border: 0;
  }

  .mobile-scope-field legend {
    margin-bottom: 5px;
    padding: 0;
    color: var(--slate);
    font-size: 12px;
    font-weight: 600;
  }

  .mobile-segmented {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 3px;
    padding: 3px;
    border: 1px solid var(--line);
    border-radius: 8px;
    background: var(--paper-deep);
  }

  .mobile-segmented button {
    min-width: 0;
    min-height: 44px;
    padding: 0 10px;
    border: 0;
    border-radius: 6px;
    background: transparent;
    color: var(--slate);
    font: inherit;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    touch-action: manipulation;
  }

  .mobile-segmented button.is-active {
    background: var(--glass-strong);
    color: var(--accent);
    box-shadow: 0 1px 4px rgba(20, 32, 51, 0.1);
  }

  .advanced-filter-toggle {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    min-height: 44px;
    padding: 0 2px;
    border: 0;
    border-top: 1px solid var(--line);
    border-bottom: 1px solid var(--line);
    background: transparent;
    color: var(--ink-soft);
    font: inherit;
    font-size: 13px;
    font-weight: 650;
    cursor: pointer;
    touch-action: manipulation;
  }

  .advanced-filter-toggle small {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 18px;
    height: 18px;
    margin-left: 4px;
    padding: 0 5px;
    border-radius: 9px;
    background: var(--accent);
    color: #fff;
    font-size: 10px;
  }

  .advanced-filter-toggle .el-icon {
    transition: transform 0.2s ease;
  }

  .advanced-filter-toggle .el-icon.is-open {
    transform: rotate(180deg);
  }

  .mobile-advanced-filters {
    gap: 14px;
    padding: 4px 0 2px;
  }

  .mobile-filter-group {
    gap: 8px;
  }

  .mobile-filter-group + .mobile-filter-group {
    padding-top: 12px;
    border-top: 1px solid var(--line);
  }

  .mobile-filter-group h3 {
    margin: 0;
    color: var(--ink-soft);
    font-size: 13px;
    font-weight: 700;
    letter-spacing: 0;
  }

  .mobile-field-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px 8px;
  }

  .mobile-field-wide {
    grid-column: 1 / -1;
  }

  .mobile-filter-options {
    grid-template-columns: minmax(88px, 0.7fr) minmax(0, 1.3fr);
    align-items: end;
    column-gap: 12px;
  }

  .mobile-filter-options h3 {
    grid-column: 1 / -1;
  }

  .mobile-risk-options {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 4px;
    min-width: 0;
  }

  .mobile-risk-options :deep(.el-checkbox) {
    min-width: 0;
    min-height: 44px;
    margin: 0;
  }

  .mobile-risk-options :deep(.el-checkbox__label) {
    min-width: 0;
    padding-left: 4px;
    overflow: hidden;
    font-size: 11px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-filter-actions {
    display: grid;
    grid-template-columns: minmax(0, 1.55fr) minmax(96px, 0.8fr);
    gap: 8px;
  }

  .mobile-filter-actions :deep(.el-button) {
    width: 100%;
    min-height: 44px;
    margin: 0;
    border-radius: 8px;
  }

  .mobile-results-section {
    display: block;
  }

  .mobile-results-heading {
    align-items: center;
    margin-bottom: 8px;
  }

  .mobile-results-heading :deep(.el-button) {
    min-height: 40px;
    margin: 0;
    border-radius: 7px;
  }

  .screener-mobile-list {
    display: grid;
    gap: 8px;
    min-height: 88px;
  }

  .screener-mobile-card {
    display: grid;
    gap: 11px;
    width: 100%;
    min-width: 0;
    padding: 12px;
    border: 1px solid var(--line);
    border-radius: 8px;
    background: var(--glass-strong);
    color: inherit;
    box-shadow: 0 3px 12px rgba(20, 32, 51, 0.04);
    font: inherit;
    text-align: left;
    cursor: pointer;
    touch-action: manipulation;
  }

  .screener-mobile-card:active {
    background: var(--fill);
  }

  .screener-mobile-card:focus-visible,
  .mobile-segmented button:focus-visible,
  .advanced-filter-toggle:focus-visible {
    outline: 3px solid rgba(0, 113, 227, 0.2);
    outline-offset: 1px;
  }

  .mobile-stock-heading {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 10px;
    min-width: 0;
  }

  .mobile-stock-identity,
  .mobile-stock-quote {
    display: flex;
    align-items: center;
    min-width: 0;
  }

  .mobile-stock-identity {
    flex: 1 1 auto;
    flex-wrap: wrap;
    gap: 5px;
  }

  .mobile-stock-identity strong {
    min-width: 0;
    max-width: 100%;
    overflow: hidden;
    color: var(--ink);
    font-size: 15px;
    font-weight: 700;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-stock-identity small {
    flex-basis: 100%;
    color: var(--muted);
    font-size: 11px;
    font-variant-numeric: tabular-nums;
  }

  .universe-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    border-radius: 4px;
    background: rgba(42, 157, 143, 0.1);
    color: #16775d;
    font-size: 10px;
    font-weight: 750;
  }

  .mobile-stock-quote {
    flex: 0 0 auto;
    align-items: flex-end;
    flex-direction: column;
    gap: 2px;
  }

  .mobile-stock-quote strong {
    font-size: 15px;
    font-weight: 750;
  }

  .mobile-stock-quote small {
    color: var(--slate);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
  }

  .mobile-stock-metrics {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 0;
    min-width: 0;
    padding-top: 9px;
    border-top: 1px solid var(--line);
  }

  .mobile-stock-metrics > span {
    display: grid;
    min-width: 0;
    gap: 3px;
    padding: 0 7px;
    border-right: 1px solid var(--line);
  }

  .mobile-stock-metrics > span:first-child,
  .mobile-stock-metrics > span:nth-child(5) {
    padding-left: 0;
  }

  .mobile-stock-metrics > span:nth-child(4n) {
    padding-right: 0;
    border-right: 0;
  }

  .mobile-stock-metrics.is-screening > span:nth-child(n + 5) {
    margin-top: 9px;
    padding-top: 9px;
    border-top: 1px solid var(--line);
  }

  .mobile-stock-metrics small {
    color: var(--muted);
    font-size: 10px;
    line-height: 1.2;
  }

  .mobile-stock-metrics b {
    min-width: 0;
    overflow: hidden;
    color: var(--ink-soft);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
    font-weight: 650;
    line-height: 1.25;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-stock-metrics b.up {
    color: var(--up);
  }

  .mobile-stock-metrics b.down {
    color: var(--down);
  }

  .mobile-empty-state {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 120px;
    padding: 20px;
    border: 1px dashed var(--line-strong);
    border-radius: 8px;
    color: var(--muted);
    font-size: 13px;
  }

  .mobile-pager {
    display: grid;
    grid-template-columns: minmax(76px, 1fr) minmax(100px, 1.25fr) minmax(76px, 1fr);
    align-items: center;
    gap: 8px;
    margin-top: 12px;
  }

  .mobile-pager-button {
    width: 100%;
    min-height: 44px;
    margin: 0 !important;
    border-radius: 8px;
  }

  .mobile-pager > span {
    display: grid;
    gap: 2px;
    min-width: 0;
    text-align: center;
  }

  .mobile-pager b,
  .mobile-pager small {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-pager b {
    color: var(--ink-soft);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
  }

  .mobile-pager small {
    color: var(--muted);
    font-size: 10px;
    font-variant-numeric: tabular-nums;
  }

  .mobile-batch-results {
    display: grid;
    gap: 7px;
    margin-top: 18px;
  }

  .mobile-batch-results h3 {
    margin-bottom: 1px;
  }

  .mobile-batch-results > button {
    display: grid;
    grid-template-columns: 24px minmax(0, 1fr) max-content;
    align-items: center;
    gap: 9px;
    min-width: 0;
    min-height: 56px;
    padding: 8px 10px;
    border: 1px solid var(--line);
    border-radius: 8px;
    background: var(--glass-strong);
    color: inherit;
    font: inherit;
    text-align: left;
    cursor: pointer;
  }

  .mobile-batch-results > button:disabled {
    cursor: default;
    opacity: 0.65;
  }

  .mobile-batch-rank {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 5px;
    background: var(--fill);
    color: var(--slate);
    font-size: 11px;
    font-weight: 750;
  }

  .mobile-batch-stock,
  .mobile-batch-return {
    display: grid;
    min-width: 0;
    gap: 2px;
  }

  .mobile-batch-stock b,
  .mobile-batch-return b {
    font-size: 13px;
    font-variant-numeric: tabular-nums;
  }

  .mobile-batch-stock small,
  .mobile-batch-return small {
    overflow: hidden;
    color: var(--muted);
    font-size: 10px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-batch-return {
    justify-items: end;
    text-align: right;
  }
}

@media (max-width: 360px) {
  .mobile-filter-surface {
    padding: 10px;
  }

  .mobile-risk-options :deep(.el-checkbox__label) {
    font-size: 10px;
  }
}
</style>
