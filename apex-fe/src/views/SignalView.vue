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

const filtered = computed(() => {
  return rows.value.filter((r) => {
    if (sideFilter.value && r.side !== sideFilter.value) return false
    if (strategyFilter.value && r.strategyId !== strategyFilter.value) return false
    return true
  })
})

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
      buy ? '目标仓位比例(如 0.1=10%)，也可填整百股数量；留空则按风控单票上限一键下单' : '卖出数量(股)；留空则按信号全平',
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
      await orderFromSignal(row.id, acc.data.id)
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
    if (buy && num > 0 && num < 1) {
      payload.targetWeight = num
    } else {
      payload.quantity = num
    }
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
  if (!row?.id) {
    ElMessage.warning('信号缺少 id')
    return
  }
  try {
    await ElMessageBox.confirm(
      row.side === 'SELL'
        ? `按信号卖出全部持仓 ${row.code} ${row.name || ''}？`
        : `按风控单票上限买入 ${row.code} ${row.name || ''}？`,
      '信号一键下单',
      { type: 'warning' },
    )
  } catch {
    return
  }
  ordering.value = true
  try {
    const acc = await getAccount()
    await orderFromSignal(row.id, acc.data.id)
    ElMessage.success('信号已转模拟单')
    router.push('/paper')
  } catch (e) {
    ElMessage.error(e.message || '下单失败')
  } finally {
    ordering.value = false
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
  <div class="page">
    <header class="header">
      <div>
        <h1><TermTip term="strategy_signal">策略信号</TermTip></h1>
        <p>
          S1/S2/S3 · 股票池 {{ universeCount }} 只 · 可一键模拟下单
          <template v-if="stats">
            · 近{{ stats.days }}日 BUY {{ stats.buyCount }} / SELL {{ stats.sellCount }} / 共 {{ stats.total }}
          </template>
        </p>
      </div>
      <div class="actions">
        <el-button @click="onRefreshUniverse" :loading="loading">刷新股票池</el-button>
        <el-button type="primary" @click="onRun" :loading="loading">运行信号</el-button>
        <el-button @click="load" :loading="loading">刷新列表</el-button>
        <el-button @click="exportCsv" :disabled="!filtered.length">导出CSV</el-button>
        <el-link
          type="primary"
          href="http://127.0.0.1:8080/apex/api/export/signals?dedupeByCode=true"
          target="_blank"
          style="margin: 0 8px"
        >服务端导出</el-link>
        <el-button @click="router.push('/screener')">条件选股</el-button>
      </div>
    </header>

    <el-alert
      v-if="forward"
      :title="`前瞻评估：${forward.message || ''} · 胜率 ${forward.hitRate != null ? (Number(forward.hitRate) * 100).toFixed(1) + '%' : '-'} · 均收益 ${forward.avgForwardReturn != null ? (Number(forward.avgForwardReturn) * 100).toFixed(2) + '%' : '-'} · 中位 ${forward.medianForwardReturn != null ? (Number(forward.medianForwardReturn) * 100).toFixed(2) + '%' : '-'} · BUY ${forward.buyCount ?? 0} / SELL ${forward.sellCount ?? 0}`"
      :type="Number(forward.hitRate) >= 0.5 ? 'success' : 'warning'"
      :closable="false"
      style="margin-bottom: 12px"
    />
    <p class="term-help">
      <TermTip term="forward_eval">什么是前瞻评估？</TermTip>
      ·
      <TermTip term="confluence">什么是策略共振？</TermTip>
    </p>
    <el-alert
      v-if="confluence"
      :title="`策略共振：${confluence.message}`"
      type="success"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-table
      v-if="confluence?.items?.length"
      :data="confluence.items"
      size="small"
      style="margin-bottom: 12px; max-width: 840px"
    >
      <el-table-column prop="code" label="代码" width="100" />
      <el-table-column prop="name" label="名称" width="120" />
      <el-table-column prop="side" label="方向" width="80" />
      <el-table-column prop="strategyCount" label="策略数" width="80" />
      <el-table-column label="策略" min-width="140">
        <template #default="{ row }">{{ (row.strategies || []).join('/') }}</template>
      </el-table-column>
      <el-table-column prop="avgScore" label="均分" width="80" />
      <el-table-column prop="maxScore" label="最高分" width="80" />
    </el-table>
    <el-table
      v-if="forward?.scoreBuckets?.length"
      :data="forward.scoreBuckets"
      size="small"
      style="margin-bottom: 12px; max-width: 520px"
    >
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

    <div class="toolbar">
      <el-select v-model="sideFilter" clearable placeholder="方向" style="width: 110px" @change="load">
        <el-option label="BUY" value="BUY" />
        <el-option label="SELL" value="SELL" />
      </el-select>
      <el-select v-model="strategyFilter" clearable placeholder="策略" style="width: 120px">
        <el-option label="S1" value="S1" />
        <el-option label="S2" value="S2" />
        <el-option label="S3" value="S3" />
      </el-select>
      <el-input
        v-model="minScore"
        clearable
        placeholder="最低评分"
        style="width: 110px"
        @change="load"
      />
      <el-switch
        v-model="dedupeByCode"
        active-text="按代码去重"
        inactive-text="全部历史"
        @change="load"
      />
    </div>

    <el-alert
      v-if="!loading && !rows.length"
      title="暂无信号：自选同步日线 → 刷新股票池 → 运行信号"
      type="info"
      :closable="false"
      style="margin-bottom: 12px"
    />

    <el-table v-loading="loading || ordering" :data="filtered" height="calc(100vh - 200px)">
      <el-table-column prop="signalDate" label="日期" width="120" sortable />
      <el-table-column prop="code" label="代码" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" width="120" show-overflow-tooltip />
      <el-table-column prop="strategyId" label="策略" width="80" />
      <el-table-column prop="side" label="方向" width="80" />
      <el-table-column prop="score" label="评分" width="90" sortable />
      <el-table-column prop="reasonJson" label="理由" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="success" @click="onQuickFromSignal(row)">一键</el-button>
          <el-button link type="primary" @click="onPaperOrder(row)">模拟{{ row.side === 'SELL' ? '卖' : '买' }}</el-button>
          <el-button link @click="router.push({ path: '/backtest', query: { code: row.code, strategyId: row.strategyId } })">回测</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.term-help {
  margin: -4px 0 12px;
  font-size: 12px;
  color: var(--muted);
}
</style>
