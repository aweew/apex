<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'
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
import { useSessionViewState } from '../utils/viewState.js'

const router = useRouter()
const loading = ref(false)
const ordering = ref(false)
const rows = ref([])
const universeCount = ref(0)
const sideFilter = ref('')
const strategyFilter = ref('')
const minScore = ref('')
const dedupeByCode = ref(true)
const stats = ref(null)
const forward = ref(null)
const confluence = ref(null)
const morePanels = ref([])
const viewportWidth = ref(window.innerWidth)
const isMobileViewport = computed(() => viewportWidth.value <= 820)
const actionColumnFixed = computed(() => resolveActionColumnFixed(viewportWidth.value))

useSessionViewState('signals', {
  sideFilter,
  strategyFilter,
  minScore,
  dedupeByCode,
})

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

const buyRows = computed(() => filtered.value.filter((r) => r.side === 'BUY'))
const sellRows = computed(() => filtered.value.filter((r) => r.side === 'SELL'))

async function load() {
  loading.value = true
  try {
    const score = minScore.value !== '' ? Number(minScore.value) : undefined
    const [sig, uni, st, fw, cf] = await Promise.all([
      latestSignals(100, dedupeByCode.value, score, sideFilter.value || undefined),
      latestUniverse(),
      signalStats(5),
      signalForward(60, 5),
      signalConfluence(5, 2),
    ])
    rows.value = sig.data || []
    universeCount.value = (uni.data || []).length
    stats.value = st.data
    forward.value = fw.data
    confluence.value = cf.data
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onGenerateSignals() {
  loading.value = true
  try {
    // 1. 按本地日线重建全市场可扫描池（≥60 根、剔 ST）
    const uni = await refreshUniverse({ scope: 'MARKET', looseFilter: true })
    const poolCount = uni.data?.count ?? 0
    universeCount.value = poolCount
    // 2. 对股票池跑 S1/S2/S3，写出买卖信号
    const res = await runSignals({ useUniverse: poolCount > 0 })
    rows.value = res.data || []
    ElMessage.success(`股票池 ${poolCount} 只 · 生成信号 ${rows.value.length} 条`)
    await load()
  } catch (e) {
    ElMessage.error(e.message || '生成失败')
  } finally {
    loading.value = false
  }
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
  load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewportWidth)
})
</script>

<template>
  <div class="page signal-page" v-loading="loading || ordering">
    <DecisionWorkspaceTabs />
    <header class="header">
      <div>
        <p class="eyebrow">灵枢 · Signals</p>
        <h1><TermTip term="strategy_signal">策略信号</TermTip></h1>
        <p class="sub">
          S1/S2/S3 · 股票池 {{ universeCount }} 只
          <span class="muted">（本地日线≥60根且非ST；全市场约五千，缺K线的需先补日线）</span>
          · 可一键模拟下单
        </p>
      </div>
      <div class="actions">
        <el-button
          type="primary"
          @click="onGenerateSignals"
          :loading="loading"
          title="先按本地日线重建全市场股票池，再跑 S1/S2/S3 生成买卖信号"
        >
          生成策略信号
        </el-button>
      </div>
    </header>

    <div class="stat-cards">
      <div class="stat-card">
        <label>近{{ stats?.days || 5 }}日 BUY</label>
        <b class="up">{{ stats?.buyCount ?? buyRows.length }}</b>
      </div>
      <div class="stat-card">
        <label>近{{ stats?.days || 5 }}日 SELL</label>
        <b class="down">{{ stats?.sellCount ?? sellRows.length }}</b>
      </div>
      <div class="stat-card">
        <label>前瞻胜率</label>
        <b>{{ forward?.hitRate != null ? (Number(forward.hitRate) * 100).toFixed(0) + '%' : '-' }}</b>
      </div>
      <div class="stat-card">
        <label>共振标的</label>
        <b>{{ confluence?.items?.length ?? 0 }}</b>
      </div>
    </div>

    <section class="list-panel">
      <div class="toolbar-bar">
        <el-select v-model="sideFilter" clearable placeholder="方向" style="width: 110px" @change="load">
          <el-option label="BUY" value="BUY" />
          <el-option label="SELL" value="SELL" />
        </el-select>
        <el-select v-model="strategyFilter" clearable placeholder="策略" style="width: 110px">
          <el-option label="S1" value="S1" />
          <el-option label="S2" value="S2" />
          <el-option label="S3" value="S3" />
        </el-select>
        <el-input v-model="minScore" clearable placeholder="最低评分" style="width: 110px" @change="load" />
        <el-switch v-model="dedupeByCode" active-text="按代码去重" @change="load" />
        <span class="muted">{{ filtered.length }} 条</span>
      </div>

      <div v-if="!loading && !rows.length" class="page-empty">
        <h3>暂无信号</h3>
        <p>先补日线，再点「生成策略信号」（自动重建股票池并跑 S1/S2/S3）</p>
        <el-button type="primary" :loading="loading" @click="onGenerateSignals">生成策略信号</el-button>
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
          <p class="signal-reason">{{ row.reasonJson || '暂无理由' }}</p>
        </article>
      </div>

      <el-table v-else class="signal-desktop-table" :data="filtered" size="small" stripe height="calc(100vh - 340px)">
        <el-table-column prop="signalDate" label="日期" width="110" sortable />
        <el-table-column prop="code" label="代码" width="96">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
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
        <el-table v-if="forward?.scoreBuckets?.length" :data="forward.scoreBuckets" size="small" stripe>
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
        <el-table v-if="confluence?.items?.length" :data="confluence.items" size="small" stripe>
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
  gap: 14px;
}

.signal-page .header {
  margin-bottom: 0;
}

.eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.04em;
  color: var(--accent);
  text-transform: uppercase;
}

.sub {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.list-panel {
  padding: 14px 16px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass-strong);
  box-shadow: var(--shadow-soft);
}

.more-collapse {
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
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

@media (max-width: 820px) {
  .list-panel {
    padding: 12px 10px 4px;
    overflow: hidden;
  }

  .signal-page .toolbar-bar {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    padding: 10px;
  }

  .signal-page .toolbar-bar :deep(.el-select),
  .signal-page .toolbar-bar :deep(.el-input) {
    width: 100% !important;
  }

  .signal-page .toolbar-bar :deep(.el-switch) {
    min-width: 0;
  }

  .signal-page .toolbar-bar > .muted {
    justify-self: end;
    align-self: center;
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
    padding: 14px 2px 13px;
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
    align-items: baseline;
    gap: 10px;
    min-width: 0;
  }

  .signal-stock :deep(.el-button) {
    min-height: 36px;
    margin: 0;
    padding: 0;
    font-size: 16px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }

  .signal-stock strong {
    overflow: hidden;
    color: var(--ink);
    font-size: 15px;
    font-weight: 650;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .signal-actions-trigger {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
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
    gap: 6px;
    min-width: 0;
    margin-top: 6px;
    color: var(--muted);
    font-size: 12px;
  }

  .signal-mobile-meta time {
    margin-right: 2px;
    font-variant-numeric: tabular-nums;
  }

  .strategy-badge,
  .side-badge,
  .signal-score {
    display: inline-flex;
    align-items: center;
    min-height: 24px;
    padding: 0 7px;
    border-radius: 4px;
    background: var(--fill);
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

  .signal-score b {
    margin-left: 3px;
    color: var(--ink-soft);
    font-variant-numeric: tabular-nums;
  }

  .signal-reason {
    margin: 9px 0 0;
    overflow-wrap: anywhere;
    color: var(--ink-soft);
    font-size: 12px;
    line-height: 1.55;
  }
}
</style>
