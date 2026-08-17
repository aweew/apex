<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import SecurityMarketBadge from '../components/SecurityMarketBadge.vue'
import { listPortfolios } from '../api/portfolio'
import { listTradeRecords } from '../api/trade'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const portfolios = ref([])
const records = ref([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const filters = reactive({
  portfolioId: route.query.portfolioId ? Number(route.query.portfolioId) : null,
  code: String(route.query.code || ''),
  side: '',
  source: '',
})

const sourceOptions = [
  { value: 'PORTFOLIO_WEB', label: '组合维护' },
  { value: 'PORTFOLIO_IMPORT', label: '组合导入' },
  { value: 'HOLDING_WEB', label: '持仓维护' },
  { value: 'WECHAT_BOT', label: '微信 Bot' },
  { value: 'MANUAL', label: '人工录入' },
  { value: 'DAILY_ACTION', label: '日终清单' },
]

const sourceLabels = Object.fromEntries(sourceOptions.map((item) => [item.value, item.label]))
const changeLabels = {
  OPEN: '建仓',
  ADD: '加仓',
  REDUCE: '减仓',
  CLEAR: '清仓',
  MANUAL: '成交',
}

const rangeLabel = computed(() => {
  if (!total.value) return '0 条'
  const start = (currentPage.value - 1) * pageSize.value + 1
  const end = Math.min(currentPage.value * pageSize.value, total.value)
  return `${start}-${end} / ${total.value}`
})

async function loadPortfolios() {
  try {
    const result = await listPortfolios(true)
    portfolios.value = result?.data || []
  } catch (error) {
    console.warn('加载组合筛选失败', error)
  }
}

async function loadRecords() {
  loading.value = true
  try {
    const result = await listTradeRecords({
      portfolioId: filters.portfolioId || undefined,
      code: filters.code.trim() || undefined,
      side: filters.side || undefined,
      source: filters.source || undefined,
      page: currentPage.value,
      size: pageSize.value,
    })
    const page = result?.data || {}
    records.value = page.records || []
    total.value = Number(page.total || 0)
  } catch (error) {
    records.value = []
    total.value = 0
    ElMessage.error(error.message || '加载交易记录失败')
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  currentPage.value = 1
  loadRecords()
}

function resetFilters() {
  filters.portfolioId = null
  filters.code = ''
  filters.side = ''
  filters.source = ''
  currentPage.value = 1
  loadRecords()
}

function changePage(page) {
  currentPage.value = page
  loadRecords()
}

function changePageSize(size) {
  pageSize.value = size
  currentPage.value = 1
  loadRecords()
}

function openStock(row) {
  if (row?.code) router.push(`/stock/${row.code}`)
}

function tradeTime(row) {
  const value = row?.tradeTime || row?.tradeDate
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, row?.tradeTime ? 16 : 10)
}

function fmtPrice(value) {
  if (value == null || value === '') return '-'
  const number = Number(value)
  if (!Number.isFinite(number)) return String(value)
  return number.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
}

function fmtQuantity(value) {
  if (value == null || value === '') return '-'
  return Number(value).toLocaleString('zh-CN')
}

function fmtPct(value) {
  if (value == null || value === '') return '-'
  const number = Number(value)
  if (!Number.isFinite(number)) return '-'
  return `${number > 0 ? '+' : ''}${number.toFixed(2)}%`
}

function pctTone(value) {
  const number = Number(value)
  if (!Number.isFinite(number) || number === 0) return ''
  return number > 0 ? 'is-up' : 'is-down'
}

function portfolioLabel(row) {
  if (row?.ownerLabel && row?.portfolioName) return `${row.ownerLabel} · ${row.portfolioName}`
  return row?.ownerLabel || row?.portfolioName || '未关联组合'
}

function quantityChange(row) {
  if (row?.beforeQuantity == null || row?.afterQuantity == null) return fmtQuantity(row?.quantity)
  return `${fmtQuantity(row.beforeQuantity)} -> ${fmtQuantity(row.afterQuantity)}`
}

onMounted(() => {
  loadPortfolios()
  loadRecords()
})
</script>

<template>
  <div class="page trade-record-page" v-loading="loading">
    <header class="trade-header">
      <div class="trade-heading">
        <el-button text :icon="ArrowLeft" @click="router.push('/portfolio')">返回组合</el-button>
        <div>
          <p class="eyebrow">Trades</p>
          <h1>交易记录</h1>
        </div>
      </div>
      <div class="trade-header-actions">
        <span>{{ rangeLabel }}</span>
        <el-button :icon="Refresh" circle title="刷新" aria-label="刷新" @click="loadRecords" />
      </div>
    </header>

    <section class="trade-filters" aria-label="交易记录筛选">
      <el-select v-model="filters.portfolioId" clearable placeholder="全部组合" class="portfolio-filter">
        <el-option v-for="item in portfolios" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-input
        v-model="filters.code"
        clearable
        placeholder="股票代码"
        class="code-filter"
        @keyup.enter="applyFilters"
      />
      <el-select v-model="filters.side" clearable placeholder="全部方向">
        <el-option label="买入" value="BUY" />
        <el-option label="卖出" value="SELL" />
      </el-select>
      <el-select v-model="filters.source" clearable placeholder="全部来源">
        <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <div class="filter-actions">
        <el-button type="primary" :icon="Search" @click="applyFilters">筛选</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </section>

    <el-table v-if="records.length" :data="records" class="trade-table" @row-click="openStock">
      <el-table-column label="时间" width="142">
        <template #default="{ row }">{{ tradeTime(row) }}</template>
      </el-table-column>
      <el-table-column label="股票" min-width="168">
        <template #default="{ row }">
          <div class="security-cell">
            <div class="security-name-line">
              <strong>{{ row.stockName || row.code }}</strong>
              <SecurityMarketBadge :security="row" include-main />
            </div>
            <span>{{ row.code }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="方向" width="86">
        <template #default="{ row }">
          <span class="side-label" :class="row.side === 'BUY' ? 'is-buy' : 'is-sell'">
            {{ row.side === 'BUY' ? '买入' : '卖出' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="76">
        <template #default="{ row }">{{ changeLabels[row.changeType] || row.changeType || '-' }}</template>
      </el-table-column>
      <el-table-column label="数量变化" min-width="130">
        <template #default="{ row }">{{ quantityChange(row) }}</template>
      </el-table-column>
      <el-table-column label="成交价" width="112" align="right">
        <template #default="{ row }">
          <div class="price-cell">
            <strong>{{ fmtPrice(row.price) }}</strong>
            <span v-if="row.estimated">估算</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="组合 / 归属" min-width="150">
        <template #default="{ row }">{{ portfolioLabel(row) }}</template>
      </el-table-column>
      <el-table-column label="来源" width="104">
        <template #default="{ row }">{{ sourceLabels[row.source] || row.source || '-' }}</template>
      </el-table-column>
      <el-table-column label="卖后至今" width="100" align="right">
        <template #default="{ row }"><span :class="pctTone(row.latestReturnPct)">{{ fmtPct(row.latestReturnPct) }}</span></template>
      </el-table-column>
      <el-table-column label="卖后最高" width="100" align="right">
        <template #default="{ row }"><span :class="pctTone(row.maxRisePct)">{{ fmtPct(row.maxRisePct) }}</span></template>
      </el-table-column>
      <el-table-column label="卖后最低" width="100" align="right">
        <template #default="{ row }"><span :class="pctTone(row.maxFallPct)">{{ fmtPct(row.maxFallPct) }}</span></template>
      </el-table-column>
    </el-table>

    <div v-if="records.length" class="trade-mobile-list">
      <article v-for="row in records" :key="row.id" class="trade-mobile-card" @click="openStock(row)">
        <header>
          <div class="security-cell">
            <div class="security-name-line">
              <strong>{{ row.stockName || row.code }}</strong>
              <SecurityMarketBadge :security="row" include-main />
            </div>
            <span>{{ row.code }} · {{ tradeTime(row) }}</span>
          </div>
          <span class="side-label" :class="row.side === 'BUY' ? 'is-buy' : 'is-sell'">
            {{ row.side === 'BUY' ? '买入' : '卖出' }}
          </span>
        </header>
        <div class="mobile-trade-core">
          <div><span>成交价</span><strong>{{ fmtPrice(row.price) }}<small v-if="row.estimated">估算</small></strong></div>
          <div><span>数量变化</span><strong>{{ quantityChange(row) }}</strong></div>
          <div><span>组合 / 归属</span><strong>{{ portfolioLabel(row) }}</strong></div>
          <div><span>来源</span><strong>{{ sourceLabels[row.source] || row.source || '-' }}</strong></div>
        </div>
        <div v-if="row.side === 'SELL'" class="mobile-performance">
          <div><span>至今</span><strong :class="pctTone(row.latestReturnPct)">{{ fmtPct(row.latestReturnPct) }}</strong></div>
          <div><span>最高</span><strong :class="pctTone(row.maxRisePct)">{{ fmtPct(row.maxRisePct) }}</strong></div>
          <div><span>最低</span><strong :class="pctTone(row.maxFallPct)">{{ fmtPct(row.maxFallPct) }}</strong></div>
        </div>
      </article>
    </div>

    <el-empty v-if="!loading && !records.length" description="暂无交易记录" />

    <div v-if="total > pageSize" class="trade-pagination">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="currentPage"
        :page-size="pageSize"
        :page-sizes="[20, 50, 100]"
        @current-change="changePage"
        @size-change="changePageSize"
      />
    </div>
  </div>
</template>

<style scoped>
.trade-record-page {
  max-width: 1500px;
  margin: 0 auto;
}

.trade-header,
.trade-heading,
.trade-header-actions,
.trade-filters,
.filter-actions,
.security-name-line {
  display: flex;
  align-items: center;
}

.trade-header {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.trade-heading {
  align-items: flex-end;
  gap: 10px;
}

.trade-heading :deep(.el-button) {
  margin-bottom: 2px;
}

.eyebrow {
  margin: 0 0 3px;
  color: var(--accent);
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0;
  text-transform: uppercase;
}

.trade-heading h1 {
  margin: 0;
  font-size: 26px;
  line-height: 1.2;
}

.trade-header-actions {
  gap: 8px;
  color: var(--muted, #6e6e73);
  font-size: 13px;
}

.trade-filters {
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 0;
  margin-bottom: 14px;
  border-top: 1px solid var(--line, rgba(0, 0, 0, 0.08));
  border-bottom: 1px solid var(--line, rgba(0, 0, 0, 0.08));
}

.trade-filters :deep(.el-select) {
  width: 138px;
}

.trade-filters .portfolio-filter {
  width: 170px;
}

.trade-filters .code-filter {
  width: 150px;
}

.filter-actions {
  gap: 8px;
}

.trade-table {
  width: 100%;
  cursor: pointer;
}

.security-cell {
  min-width: 0;
}

.security-name-line {
  gap: 6px;
  min-width: 0;
}

.security-name-line strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ink, #1d1d1f);
}

.security-cell > span {
  display: block;
  margin-top: 2px;
  color: var(--muted, #86868b);
  font-size: 12px;
}

.side-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 42px;
  height: 24px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 750;
}

.side-label.is-buy,
.is-up {
  color: #c43d4a;
}

.side-label.is-buy {
  background: rgba(196, 61, 74, 0.09);
}

.side-label.is-sell,
.is-down {
  color: #16775d;
}

.side-label.is-sell {
  background: rgba(22, 119, 93, 0.09);
}

.price-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.25;
}

.price-cell span {
  color: #a86400;
  font-size: 11px;
}

.trade-mobile-list {
  display: none;
}

.trade-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

@media (max-width: 820px) {
  .trade-record-page {
    padding: 12px;
  }

  .trade-header {
    align-items: flex-end;
    margin-bottom: 10px;
  }

  .trade-heading {
    align-items: center;
  }

  .trade-heading h1 {
    font-size: 22px;
  }

  .trade-heading .eyebrow,
  .trade-header-actions > span {
    display: none;
  }

  .trade-filters {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 8px;
    padding: 10px 0;
  }

  .trade-filters :deep(.el-select),
  .trade-filters .portfolio-filter,
  .trade-filters .code-filter {
    width: 100%;
  }

  .filter-actions {
    grid-column: 1 / -1;
  }

  .filter-actions :deep(.el-button) {
    flex: 1;
    min-height: 40px;
    margin-left: 0;
  }

  .trade-table {
    display: none;
  }

  .trade-mobile-list {
    display: grid;
    gap: 10px;
  }

  .trade-mobile-card {
    padding: 13px;
    border: 1px solid var(--line, rgba(0, 0, 0, 0.08));
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.76);
  }

  .trade-mobile-card > header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    padding-bottom: 10px;
    border-bottom: 1px solid var(--line, rgba(0, 0, 0, 0.07));
  }

  .mobile-trade-core {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 12px 14px;
    padding-top: 11px;
  }

  .mobile-trade-core > div,
  .mobile-performance > div {
    min-width: 0;
  }

  .mobile-trade-core span,
  .mobile-performance span {
    display: block;
    margin-bottom: 3px;
    color: var(--muted, #86868b);
    font-size: 11px;
  }

  .mobile-trade-core strong,
  .mobile-performance strong {
    display: block;
    overflow-wrap: anywhere;
    font-size: 13px;
  }

  .mobile-trade-core small {
    margin-left: 5px;
    color: #a86400;
    font-size: 10px;
    font-weight: 600;
  }

  .mobile-performance {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
    margin-top: 12px;
    padding: 10px;
    border-radius: 6px;
    background: rgba(0, 0, 0, 0.025);
  }

  .trade-pagination {
    justify-content: center;
    overflow-x: auto;
  }

  .trade-pagination :deep(.el-pagination__total),
  .trade-pagination :deep(.el-pagination__sizes) {
    display: none;
  }
}
</style>
