<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  backtestStress,
  benchmarkCompare,
  compareStrategies,
  getBacktestDetail,
  listBacktestJobs,
  monthlyReturns,
  paramSweep,
  portfolioBacktest,
  runBacktest,
  strategyLeaderboard,
  walkForward,
} from '../api/backtest'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const form = ref({
  code: String(route.query.code || '600519'),
  strategyId: String(route.query.strategyId || 'S1'),
  beginDate: '2024-01-01',
  endDate: '2026-08-01',
  initCash: 1000000,
})
const job = ref(null)
const trades = ref([])
const jobs = ref([])
const compareRows = ref([])
const portfolioLegs = ref([])
const portfolioCodes = ref([])
const benchmarkRow = ref(null)
const leaderboard = ref([])
const sweepRows = ref([])
const walkRow = ref(null)
const monthlyRows = ref([])
const stressRow = ref(null)
const expectancy = ref(null)
const chartRef = ref(null)
let chart

async function loadJobs() {
  try {
    const [res, board] = await Promise.all([listBacktestJobs(15), strategyLeaderboard(100)])
    jobs.value = res.data || []
    leaderboard.value = board.data || []
  } catch {
    jobs.value = []
  }
}

async function showDetail(id) {
  loading.value = true
  try {
    const [detail, mon, st] = await Promise.all([
      getBacktestDetail(id),
      monthlyReturns(id),
      backtestStress(id, 400, 20),
    ])
    job.value = detail.data.job
    trades.value = detail.data.trades || []
    expectancy.value = detail.data.expectancy
    monthlyRows.value = mon.data || []
    stressRow.value = st.data || null
    await nextTick()
    renderChart(detail.data.equities || [])
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onRun() {
  loading.value = true
  try {
    const res = await runBacktest(form.value)
    job.value = res.data
    const [detail, mon] = await Promise.all([getBacktestDetail(res.data.id), monthlyReturns(res.data.id)])
    trades.value = detail.data.trades || []
    monthlyRows.value = mon.data || []
    await nextTick()
    renderChart(detail.data.equities || [])
    await loadJobs()
    ElMessage.success('回测完成（过去表现不代表未来收益）')
  } catch (e) {
    ElMessage.error(e.message || '回测失败')
  } finally {
    loading.value = false
  }
}

async function onCompare() {
  loading.value = true
  try {
    const res = await compareStrategies({
      code: form.value.code,
      strategyId: 'S1',
      beginDate: form.value.beginDate,
      endDate: form.value.endDate,
      initCash: form.value.initCash,
    })
    compareRows.value = res.data || []
    ElMessage.success('策略对比完成')
    await loadJobs()
  } catch (e) {
    ElMessage.error(e.message || '对比失败')
  } finally {
    loading.value = false
  }
}

async function onPortfolio() {
  loading.value = true
  try {
    const res = await portfolioBacktest({
      strategyId: form.value.strategyId,
      beginDate: form.value.beginDate,
      endDate: form.value.endDate,
      initCash: form.value.initCash,
      limit: 8,
    })
    const data = res.data || {}
    job.value = data.job
    portfolioLegs.value = data.legs || []
    portfolioCodes.value = data.codes || []
    trades.value = []
    await nextTick()
    if ((data.benchmarkEquities || []).length) {
      renderOverlayChart(
        data.equities || [],
        data.benchmarkEquities || [],
        [],
        data.benchmarkCode || '000300',
      )
    } else {
      renderChart(data.equities || [])
    }
    await loadJobs()
    ElMessage.success(`组合回测完成：${(portfolioCodes.value || []).join(',') || '无有效标的'}`)
  } catch (e) {
    ElMessage.error(e.message || '组合回测失败')
  } finally {
    loading.value = false
  }
}

async function onSweep() {
  loading.value = true
  try {
    const res = await paramSweep({
      code: form.value.code,
      beginDate: form.value.beginDate,
      endDate: form.value.endDate,
      initCash: form.value.initCash,
      fastPeriods: '5,10,20',
      slowPeriods: '20,60,120',
    })
    sweepRows.value = res.data || []
    ElMessage.success(`参数扫描 ${sweepRows.value.length} 组`)
  } catch (e) {
    ElMessage.error(e.message || '扫描失败')
  } finally {
    loading.value = false
  }
}

async function onWalkForward() {
  loading.value = true
  try {
    const res = await walkForward({
      code: form.value.code,
      strategyId: form.value.strategyId,
      beginDate: form.value.beginDate,
      endDate: form.value.endDate,
      initCash: form.value.initCash,
    })
    walkRow.value = res.data
    ElMessage.success('walk-forward 完成')
  } catch (e) {
    ElMessage.error(e.message || 'walk-forward 失败')
  } finally {
    loading.value = false
  }
}

async function onBenchmark() {
  loading.value = true
  try {
    const res = await benchmarkCompare({
      code: form.value.code,
      strategyId: form.value.strategyId,
      beginDate: form.value.beginDate,
      endDate: form.value.endDate,
      initCash: form.value.initCash,
    })
    const data = res.data || {}
    benchmarkRow.value = data
    job.value = data.job
    trades.value = []
    if (data.job?.id) {
      const detail = await getBacktestDetail(data.job.id)
      trades.value = detail.data.trades || []
    }
    await nextTick()
    renderOverlayChart(
      data.strategyEquities || [],
      data.benchmarkEquities || [],
      data.stockEquities || [],
      data.benchmarkCode || '000300',
    )
    await loadJobs()
    ElMessage.success('已对比沪深300基准（含叠加曲线）')
  } catch (e) {
    ElMessage.error(e.message || '基准对比失败')
  } finally {
    loading.value = false
  }
}

function renderChart(equities) {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  chart.setOption({
    backgroundColor: 'transparent',
    legend: { top: 0 },
    grid: { left: 50, right: 20, top: 36, bottom: 40 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: equities.map((e) => e.tradeDate),
    },
    yAxis: { type: 'value', scale: true },
    series: [
      {
        type: 'line',
        name: '策略权益',
        showSymbol: false,
        data: equities.map((e) => e.equity),
        lineStyle: { color: '#1f6f5b', width: 2 },
        areaStyle: { color: 'rgba(31,111,91,0.12)' },
      },
    ],
  }, true)
}

function alignSeries(baseDates, series) {
  const map = new Map((series || []).map((e) => [String(e.tradeDate), e.equity]))
  let last = null
  return baseDates.map((d) => {
    if (map.has(d)) last = map.get(d)
    return last
  })
}

function renderOverlayChart(strategyEq, benchEq, stockEq, benchName) {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const dates = (strategyEq || []).map((e) => String(e.tradeDate))
  const fallbackDates = dates.length
    ? dates
    : (benchEq || []).map((e) => String(e.tradeDate))
  chart.setOption({
    backgroundColor: 'transparent',
    legend: { top: 0, data: ['策略', benchName || '基准', '个股持有'] },
    grid: { left: 50, right: 20, top: 36, bottom: 40 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: fallbackDates },
    yAxis: { type: 'value', scale: true },
    series: [
      {
        type: 'line',
        name: '策略',
        showSymbol: false,
        data: (strategyEq || []).map((e) => e.equity),
        lineStyle: { color: '#1f6f5b', width: 2 },
      },
      {
        type: 'line',
        name: benchName || '基准',
        showSymbol: false,
        data: alignSeries(fallbackDates, benchEq),
        lineStyle: { color: '#c45c26', width: 2 },
      },
      {
        type: 'line',
        name: '个股持有',
        showSymbol: false,
        data: alignSeries(fallbackDates, stockEq),
        lineStyle: { color: '#3b6ea5', width: 1.5, type: 'dashed' },
      },
    ],
  }, true)
}

function exportUrl() {
  if (!job.value?.id) return '#'
  return `http://127.0.0.1:8080/apex/api/export/backtest/${job.value.id}`
}

onMounted(loadJobs)
</script>

<template>
  <div class="page">
    <header class="header">
      <div>
        <h1>策略回测</h1>
        <p>含佣金/印花税/滑点 · 过去表现不代表未来收益</p>
      </div>
      <el-button @click="router.push(`/stock/${form.code}`)">看K线</el-button>
    </header>

    <el-form :inline="true" class="form">
      <el-form-item label="代码">
        <el-input v-model="form.code" style="width: 110px" />
      </el-form-item>
      <el-form-item label="策略">
        <el-select v-model="form.strategyId" style="width: 120px">
          <el-option label="S1 均线趋势" value="S1" />
          <el-option label="S2 RSI回调" value="S2" />
          <el-option label="S3 突破放量" value="S3" />
        </el-select>
      </el-form-item>
      <el-form-item label="开始">
        <el-input v-model="form.beginDate" style="width: 130px" />
      </el-form-item>
      <el-form-item label="结束">
        <el-input v-model="form.endDate" style="width: 130px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="onRun">运行回测</el-button>
      </el-form-item>
      <el-form-item>
        <el-button :loading="loading" @click="onCompare">S1/S2/S3对比</el-button>
      </el-form-item>
      <el-form-item>
        <el-button :loading="loading" @click="onPortfolio">股票池等权组合</el-button>
      </el-form-item>
      <el-form-item>
        <el-button :loading="loading" @click="onBenchmark">vs 沪深300</el-button>
      </el-form-item>
      <el-form-item>
        <el-button :loading="loading" @click="onSweep">均线参数扫描</el-button>
      </el-form-item>
      <el-form-item>
        <el-button :loading="loading" @click="onWalkForward">样本外验证</el-button>
      </el-form-item>
      <el-form-item v-if="job">
        <el-link :href="exportUrl()" target="_blank" type="primary">导出CSV</el-link>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="benchmarkRow"
      :title="`策略 ${(Number(benchmarkRow.strategyReturn || 0) * 100).toFixed(2)}% · 个股持有 ${(Number(benchmarkRow.stockBuyHoldReturn || 0) * 100).toFixed(2)}% · 沪深300 ${(Number(benchmarkRow.benchmarkReturn || 0) * 100).toFixed(2)}% · 超额 ${(Number(benchmarkRow.excessReturn || 0) * 100).toFixed(2)}%`"
      type="success"
      :closable="false"
      style="margin-bottom: 12px"
    />
    <el-alert
      v-if="stressRow"
      :title="`压力测试：${stressRow.message} · P5 ${(Number(stressRow.terminalReturnP5 || 0) * 100).toFixed(2)}% · 中位 ${(Number(stressRow.terminalReturnP50 || 0) * 100).toFixed(2)}% · P95 ${(Number(stressRow.terminalReturnP95 || 0) * 100).toFixed(2)}% · 均MaxDD ${(Number(stressRow.avgMaxDrawdown || 0) * 100).toFixed(2)}%`"
      type="warning"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="walkRow"
      :title="`Walk-forward：样本内至 ${walkRow.inSampleEnd} 收益 ${(Number(walkRow.inSampleReturn || 0) * 100).toFixed(2)}% / 夏普 ${walkRow.inSampleSharpe ?? '-'} · 样本外 ${(Number(walkRow.outSampleReturn || 0) * 100).toFixed(2)}% / 夏普 ${walkRow.outSampleSharpe ?? '-'} · 衰减 ${(Number(walkRow.returnDecay || 0) * 100).toFixed(2)}%`"
      :type="Number(walkRow.returnDecay) >= 0 ? 'success' : 'warning'"
      :closable="false"
      style="margin-bottom: 12px"
    />

    <el-alert
      v-if="portfolioCodes.length"
      :title="`组合成分：${portfolioCodes.join(' / ')}（等权分仓）`"
      type="success"
      :closable="false"
      style="margin-bottom: 12px"
    />

    <el-table v-if="sweepRows.length" :data="sweepRows" size="small" style="margin-bottom: 12px">
      <el-table-column prop="strategyId" label="参数组" width="120" />
      <el-table-column prop="fast" label="快" width="60" />
      <el-table-column prop="slow" label="慢" width="60" />
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
      <el-table-column prop="tradeCount" label="成交" width="80" />
    </el-table>

    <el-table v-if="leaderboard.length" :data="leaderboard" size="small" style="margin-bottom: 12px">
      <el-table-column prop="strategyId" label="策略榜" width="90" />
      <el-table-column prop="jobCount" label="样本" width="70" />
      <el-table-column prop="avgReturn" label="均收益" width="100">
        <template #default="{ row }">
          {{ row.avgReturn != null ? (Number(row.avgReturn) * 100).toFixed(2) + '%' : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="avgSharpe" label="均夏普" width="90" />
      <el-table-column prop="avgMaxDrawdown" label="均回撤" width="100">
        <template #default="{ row }">
          {{ row.avgMaxDrawdown != null ? (Number(row.avgMaxDrawdown) * 100).toFixed(2) + '%' : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="bestReturn" label="最佳" width="90">
        <template #default="{ row }">
          {{ row.bestReturn != null ? (Number(row.bestReturn) * 100).toFixed(2) + '%' : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="worstReturn" label="最差" width="90">
        <template #default="{ row }">
          {{ row.worstReturn != null ? (Number(row.worstReturn) * 100).toFixed(2) + '%' : '-' }}
        </template>
      </el-table-column>
    </el-table>

    <el-table v-if="portfolioLegs.length" :data="portfolioLegs" size="small" style="margin-bottom: 12px">
      <el-table-column prop="code" label="代码" width="100" />
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
      <el-table-column prop="tradeCount" label="成交" width="80" />
    </el-table>

    <el-table v-if="compareRows.length" :data="compareRows" size="small" style="margin-bottom: 12px">
      <el-table-column prop="strategyId" label="策略" width="80" />
      <el-table-column prop="totalReturn" label="收益" width="100">
        <template #default="{ row }">
          {{ row.totalReturn != null ? (Number(row.totalReturn) * 100).toFixed(2) + '%' : row.error || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="annualReturn" label="年化" width="100">
        <template #default="{ row }">
          {{ row.annualReturn != null ? (Number(row.annualReturn) * 100).toFixed(2) + '%' : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="maxDrawdown" label="回撤" width="100">
        <template #default="{ row }">
          {{ row.maxDrawdown != null ? (Number(row.maxDrawdown) * 100).toFixed(2) + '%' : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="sharpe" label="夏普" width="90" />
      <el-table-column prop="winRate" label="胜率" width="90">
        <template #default="{ row }">
          {{ row.winRate != null ? (Number(row.winRate) * 100).toFixed(1) + '%' : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="tradeCount" label="成交" width="80" />
    </el-table>

    <div v-if="job" class="metrics">
      <div>累计收益 {{ job.totalReturn != null ? (Number(job.totalReturn) * 100).toFixed(2) + '%' : '-' }}</div>
      <div>年化 {{ job.annualReturn != null ? (Number(job.annualReturn) * 100).toFixed(2) + '%' : '-' }}</div>
      <div>最大回撤 {{ job.maxDrawdown != null ? (Number(job.maxDrawdown) * 100).toFixed(2) + '%' : '-' }}</div>
      <div>夏普 {{ job.sharpe != null ? Number(job.sharpe).toFixed(2) : '-' }}</div>
      <div>Sortino {{ job.sortino != null ? Number(job.sortino).toFixed(2) : '-' }}</div>
      <div>胜率 {{ job.winRate != null ? (Number(job.winRate) * 100).toFixed(1) + '%' : '-' }}</div>
      <div>期望/笔 {{ expectancy != null ? expectancy : '-' }}</div>
      <div>成交 {{ job.tradeCount }}</div>
    </div>

    <div ref="chartRef" class="chart" />

    <el-table v-if="monthlyRows.length" :data="monthlyRows" size="small" style="margin: 12px 0">
      <el-table-column prop="month" label="月度收益" width="110" />
      <el-table-column prop="monthReturn" label="当月" width="100">
        <template #default="{ row }">
          <span :class="Number(row.monthReturn) >= 0 ? 'up' : 'down'">
            {{ row.monthReturn != null ? (Number(row.monthReturn) * 100).toFixed(2) + '%' : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="endEquity" label="月末权益" width="120" />
    </el-table>

    <h3>历史任务</h3>
    <el-table :data="jobs" size="small" height="180" @row-click="(row) => showDetail(row.id)">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="code" label="代码" width="100" />
      <el-table-column prop="strategyId" label="策略" width="80" />
      <el-table-column prop="beginDate" label="开始" width="120" />
      <el-table-column prop="endDate" label="结束" width="120" />
      <el-table-column prop="totalReturn" label="收益" width="100">
        <template #default="{ row }">
          {{ row.totalReturn != null ? (Number(row.totalReturn) * 100).toFixed(2) + '%' : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="maxDrawdown" label="回撤" width="100">
        <template #default="{ row }">
          {{ row.maxDrawdown != null ? (Number(row.maxDrawdown) * 100).toFixed(2) + '%' : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="tradeCount" label="成交" width="80" />
    </el-table>

    <el-table :data="trades" height="240" style="margin-top: 12px">
      <el-table-column prop="tradeDate" label="日期" width="120" />
      <el-table-column prop="side" label="方向" width="80" />
      <el-table-column prop="price" label="价格" width="100" />
      <el-table-column prop="quantity" label="数量" width="90" />
      <el-table-column prop="fee" label="费用" width="100" />
      <el-table-column prop="reason" label="原因" min-width="120" />
    </el-table>
  </div>
</template>

<style scoped>
.chart {
  height: 280px;
}
</style>
