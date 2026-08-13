<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Filter, MoreFilled, Refresh } from '@element-plus/icons-vue'
import {
  latestSignals,
  latestUniverse,
  refreshUniverse,
  runSignals,
  signalConfluence,
  signalForward,
  signalStats,
} from '../api/signal'
import { saveObserve } from '../api/observe'
import { getAccount, orderFromSignal, placeOrder } from '../api/paper'
import DecisionWorkspaceTabs from '../components/DecisionWorkspaceTabs.vue'
import { resolveActionColumnFixed } from '../utils/responsiveTable.js'
import {
  clearSignalPageCache,
  readSignalListCache,
  readSignalOverviewCache,
  writeSignalListCache,
  writeSignalOverviewCache,
} from '../utils/signalPageCache.js'
import { useSessionViewState } from '../utils/viewState.js'

const router = useRouter()
const overviewLoading = ref(false)
const listLoading = ref(false)
const generating = ref(false)
const refreshing = ref(false)
const ordering = ref(false)
const rows = ref([])
const universeCount = ref(0)
const sideFilter = ref('')
const strategyFilter = ref('')
const minScore = ref(0)
const dedupeByCode = ref(true)
const stats = ref(null)
const forward = ref(null)
const confluence = ref(null)
const morePanels = ref([])
const advancedFiltersOpen = ref(false)
const viewportWidth = ref(window.innerWidth)
let signalListRequestVersion = 0
const isMobileViewport = computed(() => viewportWidth.value <= 900)
const actionColumnFixed = computed(() => resolveActionColumnFixed(viewportWidth.value))
const activeFilterCount = computed(() => {
  return Number(Boolean(sideFilter.value))
    + Number(Boolean(strategyFilter.value))
    + Number(Number(minScore.value) > 0)
})
const hasCustomFilters = computed(() => activeFilterCount.value > 0 || !dedupeByCode.value)
const scoreFilterLabel = computed(() => Number(minScore.value) > 0 ? `${minScore.value} 分以上` : '不限')

function signalReason(row) {
  const rawReason = String(row?.reasonJson || '').trim()
  if (!rawReason || rawReason === '暂无理由') return ''
  try {
    const parsedReason = JSON.parse(rawReason)
    if (typeof parsedReason === 'string') return parsedReason
    if (typeof parsedReason?.rule === 'string') return parsedReason.rule
    if (typeof parsedReason?.reason === 'string') return parsedReason.reason
  } catch {
    // 非 JSON 文案保持原样展示
  }
  return rawReason
}

useSessionViewState('signals', {
  sideFilter,
  strategyFilter,
  minScore,
  dedupeByCode,
})
minScore.value = Number(minScore.value) || 0
dedupeByCode.value = dedupeByCode.value !== false

function syncViewportWidth() {
  viewportWidth.value = window.innerWidth
}

const filtered = computed(() => {
  return rows.value.filter((r) => {
    if (sideFilter.value && r.side !== sideFilter.value) return false
    if (strategyFilter.value && r.strategyId !== strategyFilter.value) return false
    return true
  })
})

const buyRows = computed(() => rows.value.filter((r) => r.side === 'BUY'))
const sellRows = computed(() => rows.value.filter((r) => r.side === 'SELL'))

function signalListQuery() {
  return {
    dedupeByCode: dedupeByCode.value,
    minScore: Number(minScore.value) || 0,
    side: sideFilter.value || '',
  }
}

function applyOverview(overview) {
  universeCount.value = overview.universeCount
  stats.value = overview.stats
  forward.value = overview.forward
  confluence.value = overview.confluence
}

async function loadOverview({ force = false } = {}) {
  const cachedOverview = readSignalOverviewCache()
  if (!force && cachedOverview) {
    applyOverview(cachedOverview)
    return
  }

  overviewLoading.value = true
  try {
    const [uni, st, fw, cf] = await Promise.all([
      latestUniverse(),
      signalStats(5),
      signalForward(60, 5),
      signalConfluence(5, 2),
    ])
    const overview = {
      universeCount: (uni.data || []).length,
      stats: st.data,
      forward: fw.data,
      confluence: cf.data,
    }
    applyOverview(overview)
    writeSignalOverviewCache(overview)
  } catch (e) {
    ElMessage.error(e.message || '概览加载失败')
  } finally {
    overviewLoading.value = false
  }
}

async function loadSignalList({ force = false } = {}) {
  const requestVersion = ++signalListRequestVersion
  const query = signalListQuery()
  const cachedRows = readSignalListCache(query)
  if (!force && cachedRows) {
    rows.value = cachedRows
    listLoading.value = false
    return
  }

  listLoading.value = true
  try {
    const response = await latestSignals(
      100,
      query.dedupeByCode,
      query.minScore || undefined,
      query.side || undefined,
    )
    const nextRows = response.data || []
    writeSignalListCache(query, nextRows)
    if (requestVersion === signalListRequestVersion) rows.value = nextRows
  } catch (e) {
    if (requestVersion === signalListRequestVersion) {
      ElMessage.error(e.message || '信号列表加载失败')
    }
  } finally {
    if (requestVersion === signalListRequestVersion) listLoading.value = false
  }
}

async function onGenerateSignals() {
  generating.value = true
  try {
    // 1. 按本地日线重建全市场可扫描池（≥60 根、剔 ST）
    const uni = await refreshUniverse({ scope: 'MARKET', looseFilter: true })
    const poolCount = uni.data?.count ?? 0
    universeCount.value = poolCount
    // 2. 对股票池跑 S1/S2/S3，写出买卖信号
    const response = await runSignals({ useUniverse: poolCount > 0 })
    const generatedCount = (response.data || []).length
    clearSignalPageCache()
    await Promise.all([
      loadOverview({ force: true }),
      loadSignalList({ force: true }),
    ])
    ElMessage.success(`股票池 ${poolCount} 只 · 生成信号 ${generatedCount} 条`)
  } catch (e) {
    ElMessage.error(e.message || '生成失败')
  } finally {
    generating.value = false
  }
}

async function onRefresh() {
  refreshing.value = true
  clearSignalPageCache()
  try {
    await Promise.all([
      loadOverview({ force: true }),
      loadSignalList({ force: true }),
    ])
  } finally {
    refreshing.value = false
  }
}

function updateSideFilter(side) {
  if (sideFilter.value === side) return
  sideFilter.value = side
  loadSignalList()
}

function updateStrategyFilter(strategy) {
  strategyFilter.value = strategy
}

function updateServerFilters() {
  loadSignalList()
}

function resetFilters() {
  sideFilter.value = ''
  strategyFilter.value = ''
  minScore.value = 0
  dedupeByCode.value = true
  loadSignalList()
}

async function onPaperOrder(row) {
  try {
    const buy = row.side !== 'SELL'
    const { value } = await ElMessageBox.prompt(
      buy ? '目标仓位比例(如 0.1=10%)；留空则一键下单' : '卖出数量(股)；留空则全平',
      `${row.code} ${row.name || ''} ${row.side}`,
      {
        inputValue: buy ? '0.1' : '',
        confirmButtonText: '下单',
      },
    )
    ordering.value = true
    const acc = await getAccount()
    const text = String(value ?? '').trim()
    if (!text && row.id) {
      await orderFromSignal(row.id, acc.data.id, buy ? 0.1 : undefined)
      ElMessage.success('已按信号一键模拟成交')
      router.push('/paper')
      return
    }
    const num = Number(text)
    const payload = {
      accountId: acc.data.id,
      code: row.code,
      side: buy ? 'BUY' : 'SELL',
    }
    if (buy && num > 0 && num < 1) payload.targetWeight = num
    else payload.quantity = num
    await placeOrder(payload)
    ElMessage.success('已模拟成交')
    router.push('/paper')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '下单失败')
  } finally {
    ordering.value = false
  }
}

async function onQuickFromSignal(row) {
  try {
    ordering.value = true
    const acc = await getAccount()
    await orderFromSignal(row.id, acc.data.id, row.side === 'BUY' ? 0.1 : undefined)
    ElMessage.success('一键下单成功')
    router.push('/paper')
  } catch (e) {
    ElMessage.error(e.message || '下单失败')
  } finally {
    ordering.value = false
  }
}

async function addObserve(row) {
  if (!row?.code) return
  try {
    await saveObserve({
      code: row.code,
      name: row.name || '',
      status: 'WATCHING',
      side: row.side || 'BUY',
      reason: `信号 ${row.strategyId || ''}`.trim(),
      tags: 'signal',
      priority: 3,
    })
    ElMessage.success(`${row.code} 已进观察池`)
  } catch (e) {
    ElMessage.error(e.message || '加入失败')
  }
}

function handleMobileRowAction(command, row) {
  if (command === 'quick') {
    onQuickFromSignal(row)
    return
  }
  if (command === 'paper') {
    onPaperOrder(row)
    return
  }
  if (command === 'backtest') {
    router.push({ path: '/backtest', query: { code: row.code, strategyId: row.strategyId } })
    return
  }
  if (command === 'observe') addObserve(row)
}

onMounted(() => {
  window.addEventListener('resize', syncViewportWidth)
  loadOverview()
  loadSignalList()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewportWidth)
})
</script>

<template>
  <div class="page signal-page">
    <DecisionWorkspaceTabs />
    <header class="header signal-header">
      <div class="signal-heading">
        <p class="eyebrow">灵枢 · Signals</p>
        <h1><TermTip term="strategy_signal">策略信号</TermTip></h1>
        <p class="sub">
          股票池 {{ universeCount }} 只 <span aria-hidden="true">·</span> S1 / S2 / S3
          <span class="signal-data-rule">· 本地日线 ≥60 根且非 ST</span>
        </p>
      </div>
      <div class="actions signal-header-actions">
        <el-button
          class="refresh-button"
          :icon="Refresh"
          :loading="refreshing"
          :disabled="generating"
          aria-label="刷新策略信号"
          title="刷新现有信号与统计"
          @click="onRefresh"
        >
          <span>刷新</span>
        </el-button>
        <el-button
          type="primary"
          @click="onGenerateSignals"
          :loading="generating"
          :disabled="refreshing"
          title="先按本地日线重建全市场股票池，再跑 S1/S2/S3 生成买卖信号"
        >
          生成策略信号
        </el-button>
      </div>
    </header>

    <div class="signal-metrics" v-loading="overviewLoading">
      <div class="signal-metric">
        <label>近{{ stats?.days || 5 }}日买入</label>
        <b class="up">{{ stats?.buyCount ?? buyRows.length }}</b>
      </div>
      <div class="signal-metric">
        <label>近{{ stats?.days || 5 }}日卖出</label>
        <b class="down">{{ stats?.sellCount ?? sellRows.length }}</b>
      </div>
      <div class="signal-metric">
        <label>前瞻胜率</label>
        <b>{{ forward?.hitRate != null ? (Number(forward.hitRate) * 100).toFixed(0) + '%' : '-' }}</b>
      </div>
      <div class="signal-metric">
        <label>策略共振</label>
        <b>{{ confluence?.items?.length ?? 0 }}</b>
      </div>
    </div>

    <section class="list-panel">
      <div class="signal-filters">
        <div class="filter-group direction-group">
          <span class="filter-label">方向</span>
          <div class="signal-segmented signal-direction-filter" role="group" aria-label="信号方向">
            <button
              v-for="option in [{ label: '全部', value: '' }, { label: '买入', value: 'BUY' }, { label: '卖出', value: 'SELL' }]"
              :key="option.value || 'all'"
              type="button"
              :class="{ 'is-active': sideFilter === option.value }"
              :aria-pressed="sideFilter === option.value"
              @click="updateSideFilter(option.value)"
            >
              {{ option.label }}
            </button>
          </div>
        </div>

        <div class="filter-group strategy-group">
          <span class="filter-label">策略</span>
          <div class="signal-segmented signal-strategy-filter" role="group" aria-label="信号策略">
            <button
              v-for="option in [{ label: '全部', value: '' }, { label: 'S1', value: 'S1' }, { label: 'S2', value: 'S2' }, { label: 'S3', value: 'S3' }]"
              :key="option.value || 'all'"
              type="button"
              :class="{ 'is-active': strategyFilter === option.value }"
              :aria-pressed="strategyFilter === option.value"
              @click="updateStrategyFilter(option.value)"
            >
              {{ option.label }}
            </button>
          </div>
        </div>

        <button
          type="button"
          class="advanced-filter-toggle"
          :aria-expanded="advancedFiltersOpen"
          aria-controls="signal-advanced-filters"
          @click="advancedFiltersOpen = !advancedFiltersOpen"
        >
          <span class="advanced-filter-title">
            <el-icon><Filter /></el-icon>
            高级筛选
            <span v-if="Number(minScore) > 0 || !dedupeByCode" class="filter-count-badge">
              {{ Number(Number(minScore) > 0) + Number(!dedupeByCode) }}
            </span>
          </span>
          <span class="advanced-filter-value">{{ scoreFilterLabel }} · {{ dedupeByCode ? '已去重' : '未去重' }}</span>
          <el-icon class="advanced-filter-arrow" :class="{ 'is-open': advancedFiltersOpen }"><ArrowDown /></el-icon>
        </button>

        <div v-show="advancedFiltersOpen" id="signal-advanced-filters" class="advanced-filters">
          <div class="score-filter-row">
            <span class="filter-label">最低评分</span>
            <strong>{{ scoreFilterLabel }}</strong>
          </div>
          <el-slider
            v-model="minScore"
            :min="0"
            :max="100"
            :step="5"
            :show-tooltip="false"
            :marks="{ 0: '不限', 60: '60', 80: '80', 100: '100' }"
            @change="updateServerFilters"
          />
          <label class="dedupe-control">
            <strong>按股票代码去重</strong>
            <el-switch v-model="dedupeByCode" aria-label="按股票代码去重" @change="updateServerFilters" />
          </label>
        </div>

        <div class="filter-result-summary">
          <span>
            <b>{{ filtered.length }}</b> 条结果
            <span v-if="activeFilterCount"> · {{ activeFilterCount }} 项筛选</span>
          </span>
          <el-button v-if="hasCustomFilters" link type="primary" @click="resetFilters">重置</el-button>
        </div>
      </div>

      <div class="signal-results" v-loading="listLoading || ordering">
        <div v-if="!listLoading && !rows.length" class="page-empty">
          <h3>暂无信号</h3>
          <p>补齐日线数据后，可重新生成 S1 / S2 / S3 信号。</p>
          <el-button type="primary" :loading="generating" @click="onGenerateSignals">生成策略信号</el-button>
        </div>

        <div v-else-if="isMobileViewport" class="signal-mobile-list" role="list">
          <p v-if="!filtered.length" class="signal-filter-empty">当前筛选下暂无信号</p>
          <article
            v-for="(row, index) in filtered"
            :key="row.id || `${row.signalDate}-${row.code}-${row.strategyId}-${index}`"
            class="signal-mobile-item"
            role="listitem"
          >
          <header class="signal-mobile-head">
            <div class="signal-stock">
              <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">
                {{ row.code }}
              </el-button>
              <SecurityMarketBadge :security="row" />
              <strong>{{ row.name || '-' }}</strong>
            </div>
            <el-dropdown
              trigger="click"
              placement="bottom-end"
              @command="handleMobileRowAction($event, row)"
            >
              <button
                type="button"
                class="signal-actions-trigger"
                :aria-label="`${row.name || row.code}更多操作`"
                title="更多操作"
                @click.stop
              >
                <el-icon><MoreFilled /></el-icon>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="quick">一键模拟</el-dropdown-item>
                  <el-dropdown-item command="paper">
                    模拟{{ row.side === 'SELL' ? '卖出' : '买入' }}
                  </el-dropdown-item>
                  <el-dropdown-item command="backtest" divided>查看回测</el-dropdown-item>
                  <el-dropdown-item command="observe">加入观察池</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </header>
          <div class="signal-mobile-meta">
            <time>{{ row.signalDate || '-' }}</time>
            <span class="strategy-badge">{{ row.strategyId || '-' }}</span>
            <span class="side-badge" :class="row.side === 'BUY' ? 'is-buy' : 'is-sell'">
              {{ row.side === 'BUY' ? '买入' : '卖出' }}
            </span>
            <span class="signal-score">评分 <b>{{ row.score ?? '-' }}</b></span>
          </div>
          <p v-if="signalReason(row)" class="signal-reason">{{ signalReason(row) }}</p>
          </article>
        </div>

        <el-table v-else class="signal-desktop-table" :data="filtered" size="small" stripe height="calc(100vh - 340px)">
        <el-table-column prop="signalDate" label="日期" width="110" sortable />
        <el-table-column prop="code" label="代码" width="96">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
            <SecurityMarketBadge :security="row" />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" width="110" show-overflow-tooltip />
        <el-table-column prop="strategyId" label="策略" width="70" />
        <el-table-column prop="side" label="方向" width="70">
          <template #default="{ row }">
            <span :class="row.side === 'BUY' ? 'up' : 'down'">{{ row.side }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="110" sortable prop="score">
          <template #default="{ row }"><ScoreBar :score="row.score" /></template>
        </el-table-column>
        <el-table-column prop="reasonJson" label="理由" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="220" :fixed="actionColumnFixed">
          <template #default="{ row }">
            <el-button link type="success" @click="onQuickFromSignal(row)">一键</el-button>
            <el-button link type="primary" @click="onPaperOrder(row)">
              模拟{{ row.side === 'SELL' ? '卖' : '买' }}
            </el-button>
            <el-button
              link
              @click="router.push({ path: '/backtest', query: { code: row.code, strategyId: row.strategyId } })"
            >回测</el-button>
            <el-button link type="warning" @click="addObserve(row)">观察</el-button>
          </template>
        </el-table-column>
        </el-table>
      </div>
    </section>

    <el-collapse v-model="morePanels" class="more-collapse">
      <el-collapse-item name="forward">
        <template #title>
          <span class="collapse-title">前瞻评估</span>
          <span class="collapse-sub">
            <TermTip term="forward_eval">胜率与均收益</TermTip>
            ·
            {{ forward?.message || '暂无' }}
          </span>
        </template>
        <div v-if="isMobileViewport && forward?.scoreBuckets?.length" class="forward-mobile-list">
          <div v-for="bucket in forward.scoreBuckets" :key="bucket.bucket" class="forward-mobile-item">
            <strong>{{ bucket.bucket }}</strong>
            <span>样本 {{ bucket.sampleCount }}</span>
            <span>胜率 {{ bucket.hitRate != null ? (Number(bucket.hitRate) * 100).toFixed(0) + '%' : '-' }}</span>
            <span>均前瞻 {{ bucket.avgForwardReturn != null ? (Number(bucket.avgForwardReturn) * 100).toFixed(2) + '%' : '-' }}</span>
          </div>
        </div>
        <el-table v-else-if="forward?.scoreBuckets?.length" :data="forward.scoreBuckets" size="small" stripe>
          <el-table-column prop="bucket" label="评分桶" width="90" />
          <el-table-column prop="sampleCount" label="样本" width="80" />
          <el-table-column label="胜率" width="90">
            <template #default="{ row }">
              {{ row.hitRate != null ? (Number(row.hitRate) * 100).toFixed(0) + '%' : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="均前瞻" width="100">
            <template #default="{ row }">
              {{ row.avgForwardReturn != null ? (Number(row.avgForwardReturn) * 100).toFixed(2) + '%' : '-' }}
            </template>
          </el-table-column>
        </el-table>
        <p v-else class="muted">暂无分桶样本</p>
      </el-collapse-item>

      <el-collapse-item name="cf">
        <template #title>
          <span class="collapse-title">策略共振</span>
          <span class="collapse-sub">
            <TermTip term="confluence">多策略同向</TermTip>
            · {{ confluence?.message || '暂无' }}
          </span>
        </template>
        <div v-if="isMobileViewport && confluence?.items?.length" class="confluence-mobile-list">
          <article v-for="item in confluence.items" :key="`${item.code}-${item.side}`" class="confluence-mobile-item">
            <div>
              <strong>{{ item.name || item.code }}</strong>
              <SecurityMarketBadge :security="item" />
              <span>{{ item.code }}</span>
            </div>
            <span class="side-badge" :class="item.side === 'BUY' ? 'is-buy' : 'is-sell'">
              {{ item.side === 'BUY' ? '买入' : '卖出' }}
            </span>
            <p>{{ (item.strategies || []).join(' / ') }} · {{ item.strategyCount }} 个策略 · 均分 {{ item.avgScore ?? '-' }}</p>
          </article>
        </div>
        <el-table v-else-if="confluence?.items?.length" :data="confluence.items" size="small" stripe>
          <el-table-column prop="code" label="代码" width="100" />
          <el-table-column prop="name" label="名称" width="120" />
          <el-table-column prop="side" label="方向" width="80" />
          <el-table-column prop="strategyCount" label="策略数" width="80" />
          <el-table-column label="策略" min-width="140">
            <template #default="{ row }">{{ (row.strategies || []).join('/') }}</template>
          </el-table-column>
          <el-table-column label="均分" width="110">
            <template #default="{ row }"><ScoreBar :score="row.avgScore" /></template>
          </el-table-column>
          <el-table-column prop="maxScore" label="最高分" width="80" />
        </el-table>
        <p v-else class="muted">暂无共振</p>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.signal-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.signal-page :deep(.decision-workspace-tabs),
.signal-page .signal-header {
  margin-bottom: 0;
}

.signal-header-actions {
  flex-wrap: nowrap;
}

.sub {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.signal-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  min-height: 70px;
  overflow: hidden;
  border: 1px solid var(--glass-border);
  border-radius: 8px;
  background: var(--glass-strong);
  box-shadow: var(--shadow-soft);
}

.signal-metric {
  display: grid;
  align-content: center;
  min-width: 0;
  padding: 11px 14px;
  border-right: 1px solid var(--line);
}

.signal-metric:last-child {
  border-right: 0;
}

.signal-metric label {
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.signal-metric b {
  margin-top: 3px;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 20px;
  font-variant-numeric: tabular-nums;
}

.signal-metric b.up {
  color: var(--up);
}

.signal-metric b.down {
  color: var(--down);
}

.list-panel {
  padding: 14px 16px;
  border: 1px solid var(--glass-border);
  border-radius: 8px;
  background: var(--glass-strong);
  box-shadow: var(--shadow-soft);
}

.signal-filters {
  display: grid;
  grid-template-columns: minmax(250px, 1fr) minmax(280px, 1.15fr) auto;
  gap: 10px;
  margin-bottom: 12px;
}

.filter-group {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.filter-label {
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}

.signal-segmented {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 1fr;
  min-width: 0;
  padding: 3px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--paper-deep);
}

.signal-segmented button {
  min-width: 48px;
  min-height: 32px;
  padding: 0 10px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--slate);
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.signal-segmented button.is-active {
  background: var(--glass-strong);
  color: var(--ink);
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.1);
}

.signal-segmented button:focus-visible,
.advanced-filter-toggle:focus-visible,
.signal-actions-trigger:focus-visible {
  outline: 3px solid rgba(0, 113, 227, 0.2);
  outline-offset: 1px;
}

.advanced-filter-toggle {
  display: grid;
  grid-template-columns: auto auto 18px;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: transparent;
  color: var(--ink-soft);
  font: inherit;
  cursor: pointer;
}

.advanced-filter-title {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
}

.advanced-filter-value {
  color: var(--muted);
  font-size: 11px;
  white-space: nowrap;
}

.filter-count-badge {
  display: grid;
  min-width: 18px;
  min-height: 18px;
  place-items: center;
  border-radius: 50%;
  background: var(--accent);
  color: #fff;
  font-size: 10px;
}

.advanced-filter-arrow {
  color: var(--muted);
  transition: transform 0.2s ease;
}

.advanced-filter-arrow.is-open {
  transform: rotate(180deg);
}

.advanced-filters {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: minmax(240px, 1fr) minmax(250px, 1fr);
  gap: 16px 28px;
  padding: 14px 16px 20px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: var(--paper-deep);
}

.score-filter-row {
  grid-column: 1;
  display: flex;
  justify-content: space-between;
}

.score-filter-row strong {
  color: var(--ink-soft);
  font-size: 12px;
}

.advanced-filters :deep(.el-slider) {
  grid-column: 1;
  margin: -9px 8px 0;
}

.advanced-filters :deep(.el-slider__marks-text) {
  font-size: 10px;
  white-space: nowrap;
}

.dedupe-control {
  grid-column: 2;
  grid-row: 1 / span 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  cursor: pointer;
}

.dedupe-control strong {
  color: var(--ink-soft);
  font-size: 13px;
}

.filter-result-summary {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  color: var(--muted);
  font-size: 12px;
}

.filter-result-summary b {
  color: var(--ink-soft);
  font-variant-numeric: tabular-nums;
}

.filter-result-summary :deep(.el-button) {
  margin: 0;
}

.signal-results {
  position: relative;
  min-height: 90px;
}

.more-collapse {
  border: 1px solid var(--glass-border);
  border-radius: 8px;
  background: var(--glass);
  overflow: hidden;
}

.more-collapse :deep(.el-collapse-item__header) {
  padding: 0 16px;
  height: 48px;
  background: transparent;
  border-bottom-color: var(--line);
}

.more-collapse :deep(.el-collapse-item__content) {
  padding: 12px 16px 16px;
}

.collapse-title {
  font-weight: 700;
  margin-right: 10px;
}

.collapse-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--muted);
}

.muted {
  color: var(--muted);
  font-size: 12px;
}

.up {
  color: var(--up);
}

.down {
  color: var(--down);
}

.signal-mobile-list {
  display: none;
}

.forward-mobile-list,
.confluence-mobile-list {
  display: grid;
  gap: 8px;
}

.forward-mobile-item {
  display: grid;
  grid-template-columns: 1fr repeat(3, auto);
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 12px;
}

.forward-mobile-item:last-child,
.confluence-mobile-item:last-child {
  border-bottom: 0;
}

.forward-mobile-item strong {
  color: var(--ink);
}

.confluence-mobile-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
}

.confluence-mobile-item > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.confluence-mobile-item strong {
  overflow: hidden;
  color: var(--ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.confluence-mobile-item > div span,
.confluence-mobile-item p {
  color: var(--muted);
  font-size: 11px;
}

.confluence-mobile-item p {
  grid-column: 1 / -1;
  margin: 0;
}

@media (max-width: 900px) {
  .signal-page {
    gap: 10px;
  }

  .signal-header {
    gap: 9px;
  }

  .signal-heading h1 {
    font-size: 22px;
  }

  .signal-heading .eyebrow {
    display: none;
  }

  .signal-heading .sub {
    margin-top: 3px;
    font-size: 12px;
  }

  .signal-header-actions {
    display: grid !important;
    grid-template-columns: 44px minmax(0, 1fr);
    gap: 8px;
  }

  .signal-header-actions > :deep(.el-button) {
    width: 100% !important;
    min-height: 44px;
    margin: 0;
  }

  .signal-header-actions .refresh-button :deep(span > span) {
    display: none;
  }

  .signal-metrics {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    min-height: 62px;
  }

  .signal-metric {
    justify-items: center;
    padding: 8px 3px;
    text-align: center;
  }

  .signal-metric label {
    width: 100%;
    font-size: 10px;
  }

  .signal-metric b {
    font-size: 18px;
  }

  .list-panel {
    padding: 10px 10px 2px;
    overflow: hidden;
  }

  .signal-filters {
    display: grid;
    grid-template-columns: 1fr;
    gap: 6px;
    margin-bottom: 2px;
  }

  .filter-group {
    grid-template-columns: 28px minmax(0, 1fr);
    gap: 5px;
  }

  .filter-label {
    font-size: 11px;
  }

  .signal-segmented {
    padding: 2px;
    border-radius: 6px;
  }

  .signal-segmented button {
    min-width: 0;
    min-height: 34px;
    padding: 0 4px;
    font-size: 13px;
  }

  .advanced-filter-toggle {
    grid-template-columns: minmax(0, 1fr) auto 18px;
    min-height: 44px;
    padding: 0 8px 0 10px;
  }

  .advanced-filter-title {
    font-size: 13px;
  }

  .advanced-filter-value {
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .advanced-filters {
    grid-template-columns: 1fr;
    gap: 12px;
    padding: 12px 12px 18px;
  }

  .advanced-filters :deep(.el-slider) {
    grid-column: 1;
    margin: -4px 8px 12px;
  }

  .advanced-filters :deep(.el-slider__button-wrapper) {
    width: 44px;
    height: 44px;
  }

  .dedupe-control {
    grid-column: 1;
    grid-row: auto;
    min-height: 50px;
    padding-top: 10px;
    border-top: 1px solid var(--line);
  }

  .filter-result-summary {
    min-height: 42px;
    border-top: 1px solid var(--line);
  }

  .filter-result-summary :deep(.el-button) {
    min-height: 40px;
  }

  .signal-mobile-list {
    display: block;
    min-width: 0;
  }

  .signal-filter-empty {
    margin: 0;
    padding: 34px 12px;
    color: var(--muted);
    font-size: 13px;
    text-align: center;
  }

  .signal-mobile-item {
    min-width: 0;
    padding: 8px 2px;
    border-bottom: 1px solid var(--line);
  }

  .signal-mobile-item:last-child {
    border-bottom: 0;
  }

  .signal-mobile-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    min-width: 0;
  }

  .signal-stock {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  .signal-stock :deep(.el-button) {
    min-height: 32px;
    margin: 0;
    padding: 0;
    font-size: 15px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }

  .signal-stock strong {
    overflow: hidden;
    color: var(--ink);
    font-size: 14px;
    font-weight: 650;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .signal-actions-trigger {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    padding: 0;
    border: 0;
    border-radius: 6px;
    background: transparent;
    color: var(--muted);
    cursor: pointer;
  }

  .signal-actions-trigger:active {
    background: var(--fill);
  }

  .signal-actions-trigger .el-icon {
    font-size: 20px;
  }

  .signal-mobile-meta {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 5px;
    min-width: 0;
    margin-top: 1px;
    color: var(--muted);
    font-size: 11px;
  }

  .signal-mobile-meta time {
    margin-right: 2px;
    font-variant-numeric: tabular-nums;
  }

  .strategy-badge,
  .side-badge {
    display: inline-flex;
    align-items: center;
    min-height: 18px;
    padding: 0 4px;
    border-radius: 4px;
    background: var(--fill);
    font-size: 10px;
    line-height: 1;
  }

  .strategy-badge {
    color: var(--ink-soft);
    font-weight: 650;
  }

  .side-badge.is-buy {
    background: rgba(255, 59, 48, 0.08);
    color: var(--up);
  }

  .side-badge.is-sell {
    background: rgba(52, 199, 89, 0.1);
    color: var(--down);
  }

  .signal-score {
    color: var(--muted);
    font-size: 11px;
  }

  .signal-score b {
    margin-left: 3px;
    color: var(--ink-soft);
    font-variant-numeric: tabular-nums;
  }

  .signal-reason {
    margin: 3px 0 0;
    overflow-wrap: anywhere;
    overflow: hidden;
    color: var(--ink-soft);
    font-size: 11px;
    line-height: 1.35;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .more-collapse :deep(.el-collapse-item__header) {
    min-height: 50px;
    height: auto;
    padding: 6px 12px;
  }

  .more-collapse :deep(.el-collapse-item__content) {
    padding: 6px 12px 12px;
  }

  .collapse-title {
    flex: 0 0 auto;
    margin-right: 8px;
    font-size: 13px;
  }

  .collapse-sub {
    min-width: 0;
    overflow: hidden;
    font-size: 11px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .forward-mobile-item {
    grid-template-columns: 1fr 1fr;
    gap: 6px 12px;
  }
}

@media (min-width: 600px) and (max-width: 900px) {
  .signal-header {
    flex-direction: row;
    align-items: center;
  }

  .signal-heading {
    min-width: 0;
  }

  .signal-header-actions {
    width: min(320px, 44%) !important;
    flex: 0 0 auto;
  }

  .signal-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .advanced-filter-toggle,
  .advanced-filters,
  .filter-result-summary {
    grid-column: 1 / -1;
  }
}

@media (max-width: 380px) {
  .signal-data-rule {
    display: none;
  }

  .signal-metric label {
    white-space: normal;
    line-height: 1.15;
  }

  .advanced-filter-value {
    max-width: 100px;
  }
}
</style>
