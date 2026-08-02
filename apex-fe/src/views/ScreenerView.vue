<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { runScreener } from '../api/screener'
import { batchBacktest } from '../api/backtest'
import { saveObserve } from '../api/observe'

const router = useRouter()
const loading = ref(false)
const form = ref({
  groupName: '我的自选',
  peMin: '',
  peMax: 40,
  pbMin: '',
  pbMax: 10,
  industry: '',
  pctChgMin: '',
  pctChgMax: '',
  pctChg20Min: '',
  pctChg20Max: '',
  minCircMvYi: '',
  maxCircMvYi: '',
  minBars: 60,
  excludeSt: true,
  excludeLimitUp: true,
  excludeLimitDown: false,
  minVolumeRatio: '',
  minUpDays: '',
  rs20Min: '',
  maxAtrPct: '',
  minAtrPct: '',
  limit: 40,
})
const rows = ref([])
const batchRows = ref([])

async function onRun() {
  loading.value = true
  try {
    const res = await runScreener({
      groupName: form.value.groupName,
      peMin: form.value.peMin !== '' ? Number(form.value.peMin) : null,
      peMax: form.value.peMax ? Number(form.value.peMax) : null,
      pbMin: form.value.pbMin !== '' ? Number(form.value.pbMin) : null,
      pbMax: form.value.pbMax ? Number(form.value.pbMax) : null,
      industry: form.value.industry || null,
      pctChgMin: form.value.pctChgMin !== '' ? Number(form.value.pctChgMin) : null,
      pctChgMax: form.value.pctChgMax !== '' ? Number(form.value.pctChgMax) : null,
      pctChg20Min: form.value.pctChg20Min !== '' ? Number(form.value.pctChg20Min) : null,
      pctChg20Max: form.value.pctChg20Max !== '' ? Number(form.value.pctChg20Max) : null,
      minCircMv:
        form.value.minCircMvYi !== '' ? Number(form.value.minCircMvYi) * 1e8 : null,
      maxCircMv:
        form.value.maxCircMvYi !== '' ? Number(form.value.maxCircMvYi) * 1e8 : null,
      minBars: Number(form.value.minBars || 60),
      excludeSt: form.value.excludeSt,
      excludeLimitUp: form.value.excludeLimitUp,
      excludeLimitDown: form.value.excludeLimitDown,
      minVolumeRatio: form.value.minVolumeRatio !== '' ? Number(form.value.minVolumeRatio) : null,
      minUpDays: form.value.minUpDays !== '' ? Number(form.value.minUpDays) : null,
      rs20Min: form.value.rs20Min !== '' ? Number(form.value.rs20Min) : null,
      maxAtrPct: form.value.maxAtrPct !== '' ? Number(form.value.maxAtrPct) : null,
      minAtrPct: form.value.minAtrPct !== '' ? Number(form.value.minAtrPct) : null,
      limit: Number(form.value.limit || 40),
    })
    rows.value = res.data || []
    ElMessage.success(`选出 ${rows.value.length} 只`)
  } catch (e) {
    ElMessage.error(e.message || '选股失败')
  } finally {
    loading.value = false
  }
}

async function addObserve(row) {
  if (!row?.code) return
  try {
    await saveObserve({
      code: row.code,
      name: row.name || '',
      status: 'WATCHING',
      reason: '条件选股',
      tags: 'screener',
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

onMounted(onRun)
</script>

<template>
  <div class="page">
    <header class="header">
      <div>
        <p class="eyebrow">Apex · Screener</p>
        <h1>条件选股</h1>
        <p>PE/PB/行业/量能过滤 · 结果可进观察池或批量回测</p>
      </div>
      <div class="actions">
        <el-button type="primary" :loading="loading" @click="onRun">运行选股</el-button>
        <el-button :loading="loading" @click="onBatchBacktest">批量回测前8</el-button>
        <el-button plain @click="router.push('/decision')">智能决策</el-button>
      </div>
    </header>

    <el-form :inline="true" class="form">
      <el-form-item label="分组"><el-input v-model="form.groupName" style="width: 120px" /></el-form-item>
      <el-form-item label="PE≥"><el-input v-model="form.peMin" style="width: 70px" /></el-form-item>
      <el-form-item label="PE≤"><el-input v-model="form.peMax" style="width: 70px" /></el-form-item>
      <el-form-item label="PB≥"><el-input v-model="form.pbMin" style="width: 70px" /></el-form-item>
      <el-form-item label="PB≤"><el-input v-model="form.pbMax" style="width: 70px" /></el-form-item>
      <el-form-item label="行业"><el-input v-model="form.industry" style="width: 120px" placeholder="如 银行" /></el-form-item>
      <el-form-item label="今日≥"><el-input v-model="form.pctChgMin" style="width: 70px" placeholder="%" /></el-form-item>
      <el-form-item label="今日≤"><el-input v-model="form.pctChgMax" style="width: 70px" placeholder="%" /></el-form-item>
      <el-form-item label="20日≥"><el-input v-model="form.pctChg20Min" style="width: 70px" placeholder="%" /></el-form-item>
      <el-form-item label="20日≤"><el-input v-model="form.pctChg20Max" style="width: 70px" placeholder="%" /></el-form-item>
      <el-form-item label="流通≥亿"><el-input v-model="form.minCircMvYi" style="width: 80px" /></el-form-item>
      <el-form-item label="流通≤亿"><el-input v-model="form.maxCircMvYi" style="width: 80px" /></el-form-item>
      <el-form-item label="K线≥"><el-input v-model="form.minBars" style="width: 80px" /></el-form-item>
      <el-form-item label="量比≥"><el-input v-model="form.minVolumeRatio" style="width: 70px" placeholder="1.5" /></el-form-item>
      <el-form-item label="连涨≥"><el-input v-model="form.minUpDays" style="width: 70px" placeholder="天" /></el-form-item>
      <el-form-item label="RS20≥"><el-input v-model="form.rs20Min" style="width: 70px" placeholder="相对300" /></el-form-item>
      <el-form-item label="ATR%≤"><el-input v-model="form.maxAtrPct" style="width: 70px" placeholder="如8" /></el-form-item>
      <el-form-item label="ATR%≥"><el-input v-model="form.minAtrPct" style="width: 70px" /></el-form-item>
      <el-form-item><el-checkbox v-model="form.excludeSt">排除ST</el-checkbox></el-form-item>
      <el-form-item><el-checkbox v-model="form.excludeLimitUp">排除涨停</el-checkbox></el-form-item>
      <el-form-item><el-checkbox v-model="form.excludeLimitDown">排除跌停</el-checkbox></el-form-item>
    </el-form>

    <div v-if="!loading && !rows.length" class="page-empty">
      <h3>暂无筛选结果</h3>
      <p>调整 PE/量能/RS 条件后运行；结果可一键进观察池或批量回测</p>
      <el-button type="primary" :loading="loading" @click="onRun">运行选股</el-button>
      <el-button plain @click="router.push('/valuation')">估值筛选</el-button>
    </div>

    <el-table v-else v-loading="loading" :data="rows" height="360" stripe>
      <el-table-column prop="code" label="代码" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" width="120" />
      <el-table-column prop="latestPrice" label="现价" width="90" />
      <el-table-column prop="pctChg" label="今日%" width="80">
        <template #default="{ row }">
          <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">{{ row.pctChg ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="pctChg5" label="5日%" width="80">
        <template #default="{ row }">
          <span :class="Number(row.pctChg5) >= 0 ? 'up' : 'down'">{{ row.pctChg5 ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="pctChg20" label="20日%" width="80">
        <template #default="{ row }">
          <span :class="Number(row.pctChg20) >= 0 ? 'up' : 'down'">{{ row.pctChg20 ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="volumeRatio" label="量比" width="80" />
      <el-table-column prop="upDays" label="连涨" width="70" />
      <el-table-column prop="rs20VsHs300" label="RS20" width="80" sortable />
      <el-table-column prop="atrPct" label="ATR%" width="80" sortable />
      <el-table-column prop="peTtm" label="PE" width="80" sortable />
      <el-table-column prop="pb" label="PB" width="80" sortable />
      <el-table-column prop="circMv" label="流通(亿)" width="90">
        <template #default="{ row }">
          {{ row.circMv != null ? (Number(row.circMv) / 1e8).toFixed(1) : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="industry" label="行业" width="120" />
      <el-table-column prop="barCount" label="K线" width="80" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link @click="router.push({ path: '/backtest', query: { code: row.code } })">回测</el-button>
          <el-button link type="warning" @click="addObserve(row)">观察</el-button>
          <el-button link @click="router.push({ path: '/paper', query: { code: row.code, side: 'BUY' } })">模拟</el-button>
        </template>
      </el-table-column>
    </el-table>

    <h3 v-if="batchRows.length">批量回测排名</h3>
    <el-table v-if="batchRows.length" :data="batchRows" size="small">
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
</style>
