<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
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

async function onRefreshUniverse() {
  loading.value = true
  try {
    const res = await refreshUniverse({ groupName: '我的自选' })
    ElMessage.success(`股票池批次 ${res.data.batchNo}，入选 ${res.data.count}`)
    await load()
  } catch (e) {
    ElMessage.error(e.message || '刷新失败')
  } finally {
    loading.value = false
  }
}

async function onRun() {
  loading.value = true
  try {
    const res = await runSignals({ useUniverse: universeCount.value > 0 })
    rows.value = res.data || []
    ElMessage.success(`生成信号 ${rows.value.length} 条`)
    await load()
  } catch (e) {
    ElMessage.error(e.message || '运行失败')
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

function exportCsv() {
  const header = ['signalDate', 'code', 'name', 'strategyId', 'side', 'score', 'reasonJson']
  const lines = [header.join(',')]
  for (const row of filtered.value) {
    lines.push(
      header
        .map((k) => `"${String(row[k] ?? '').split('"').join('""')}"`)
        .join(','),
    )
  }
  const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `signals_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(load)
</script>

<template>
  <div class="page signal-page" v-loading="loading || ordering">
    <header class="header">
      <div>
        <p class="eyebrow">Apex · Signals</p>
        <h1><TermTip term="strategy_signal">策略信号</TermTip></h1>
        <p class="sub">S1/S2/S3 · 股票池 {{ universeCount }} 只 · 可一键模拟下单</p>
      </div>
      <div class="actions">
        <el-button @click="onRefreshUniverse" :loading="loading">刷新股票池</el-button>
        <el-button type="primary" @click="onRun" :loading="loading">运行信号</el-button>
        <el-button text @click="load">刷新</el-button>
        <el-button text @click="exportCsv" :disabled="!filtered.length">导出本地</el-button>
        <el-link
          type="primary"
          href="http://127.0.0.1:8080/apex/api/export/signals?limit=200"
          target="_blank"
        >服务端CSV</el-link>
        <el-button plain @click="router.push('/decision')">智能决策</el-button>
        <el-button plain @click="router.push('/observe')">观察池</el-button>
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
        <p>自选同步日线 → 刷新股票池 → 运行信号</p>
        <el-button type="primary" :loading="loading" @click="onRun">运行信号</el-button>
      </div>

      <el-table v-else :data="filtered" size="small" stripe height="calc(100vh - 340px)">
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
        <el-table-column label="操作" width="220" fixed="right">
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
</style>
