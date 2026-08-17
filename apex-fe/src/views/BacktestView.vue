<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, RefreshRight, View } from '@element-plus/icons-vue'
import { buildApiUrl } from '../api/baseUrl'
import {
  backtestStress,
  benchmarkCompare,
  compareStrategies,
  getBacktestExperiment,
  getBacktestDetail,
  listBacktestExperiments,
  listBacktestJobs,
  monthlyReturns,
  paramSweep,
  portfolioBacktest,
  rollingEvaluate,
  removeBacktestExperiment,
  runBacktest,
  strategyLeaderboard,
} from '../api/backtest'
import {
  benchmarkOptions,
  buildRollingPayload,
  buildTrailingDateRange,
  formatAmount,
  formatPercent,
  restoreRollingForms,
} from '../utils/backtestLab'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const labLoading = ref(false)
const experimentLoading = ref(false)
const labControlsDisabled = computed(() => labLoading.value || experimentLoading.value)
const experimentError = ref('')
const defaultBacktestRange = buildTrailingDateRange(3)
const form = ref({
  code: String(route.query.code || '600519'),
  strategyId: String(route.query.strategyId || 'S1'),
  beginDate: defaultBacktestRange.beginDate,
  endDate: defaultBacktestRange.endDate,
  initCash: 1000000,
})
const windowModes = [
  { label: '固定窗口', value: 'ROLLING' },
  { label: '扩展窗口', value: 'EXPANDING' },
]
const labForm = ref({
  windowMode: 'ROLLING',
  trainDays: 252,
  testDays: 63,
  stepDays: 63,
  benchmarkCode: '000300',
  commissionPercent: 0.05,
  stampTaxPercent: 0.05,
  buySlippagePercent: 0.1,
  sellSlippagePercent: 0.1,
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
const rollingResult = ref(null)
const experimentRows = ref([])
const selectedExperimentIds = ref([])
const experimentCompareRows = computed(() => selectedExperimentIds.value
  .map((experimentId) => experimentRows.value.find((experiment) => experiment.id === experimentId))
  .filter(Boolean))
const monthlyRows = ref([])
const stressRow = ref(null)
const expectancy = ref(null)
const chartRef = ref(null)
let chart
let jobsLoadSequence = 0

async function loadJobs() {
  const loadSequence = ++jobsLoadSequence
  const [jobsResult, leaderboardResult] = await Promise.allSettled([
    listBacktestJobs(15),
    strategyLeaderboard(100),
  ])
  if (loadSequence !== jobsLoadSequence) return
  jobs.value = jobsResult.status === 'fulfilled' ? jobsResult.value.data || [] : []
  leaderboard.value = leaderboardResult.status === 'fulfilled' ? leaderboardResult.value.data || [] : []
}

async function loadExperiments() {
  experimentLoading.value = true
  experimentError.value = ''
  try {
    const response = await listBacktestExperiments(20)
    experimentRows.value = response.data || []
    const availableIds = new Set(experimentRows.value.map((experiment) => experiment.id))
    selectedExperimentIds.value = selectedExperimentIds.value.filter((experimentId) => availableIds.has(experimentId))
  } catch {
    experimentRows.value = []
    selectedExperimentIds.value = []
    experimentError.value = '历史记录暂时不可用，请稍后重试'
  } finally {
    experimentLoading.value = false
  }
}

function resetBacktestResults() {
  if (chart) {
    chart.dispose()
    chart = null
  }
  job.value = null
  trades.value = []
  compareRows.value = []
  portfolioLegs.value = []
  portfolioCodes.value = []
  benchmarkRow.value = null
  sweepRows.value = []
  monthlyRows.value = []
  stressRow.value = null
  expectancy.value = null
}

async function showDetail(id) {
  if (loading.value) return
  resetBacktestResults()
  loading.value = true
  try {
    const [detailResult, monthlyResult, stressResult] = await Promise.allSettled([
      getBacktestDetail(id),
      monthlyReturns(id),
      backtestStress(id, 400, 20),
    ])
    if (detailResult.status !== 'fulfilled') throw detailResult.reason
    const detail = detailResult.value.data || {}
    job.value = detail.job
    trades.value = detail.trades || []
    expectancy.value = detail.expectancy
    monthlyRows.value = monthlyResult.status === 'fulfilled' ? monthlyResult.value.data || [] : []
    stressRow.value = stressResult.status === 'fulfilled' ? stressResult.value.data || null : null
    await nextTick()
    renderChart(detail.equities || [])
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onRun() {
  if (loading.value) return
  resetBacktestResults()
  loading.value = true
  try {
    const res = await runBacktest(form.value)
    job.value = res.data
    const [detailResult, monthlyResult, stressResult] = await Promise.allSettled([
      getBacktestDetail(res.data.id),
      monthlyReturns(res.data.id),
      backtestStress(res.data.id, 400, 20),
    ])
    await loadJobs()
    if (detailResult.status !== 'fulfilled') throw detailResult.reason
    const detail = detailResult.value.data || {}
    trades.value = detail.trades || []
    expectancy.value = detail.expectancy
    monthlyRows.value = monthlyResult.status === 'fulfilled' ? monthlyResult.value.data || [] : []
    stressRow.value = stressResult.status === 'fulfilled' ? stressResult.value.data || null : null
    await nextTick()
    renderChart(detail.equities || [])
    ElMessage.success('回测完成（过去表现不代表未来收益）')
  } catch (e) {
    ElMessage.error(e.message || '回测失败')
  } finally {
    loading.value = false
  }
}

async function onCompare() {
  if (loading.value) return
  resetBacktestResults()
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
  if (loading.value) return
  resetBacktestResults()
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
  if (loading.value) return
  resetBacktestResults()
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

async function runRollingEvaluation(strategyConfig, replayAudit) {
  if (labControlsDisabled.value) return
  labLoading.value = true
  rollingResult.value = null
  try {
    const res = await rollingEvaluate(buildRollingPayload(
      form.value,
      labForm.value,
      strategyConfig,
      replayAudit,
    ))
    rollingResult.value = res.data || null
    await loadExperiments()
    ElMessage.success(`滚动评估完成：${rollingResult.value?.foldCount || 0} 个样本外窗口`)
  } catch (e) {
    ElMessage.error(e.message || '滚动评估失败')
  } finally {
    labLoading.value = false
  }
}

function hasCompleteReplaySnapshot(detail) {
  const request = detail?.request
  const result = detail?.result
  return Boolean(
    request?.code
    && request.strategyId
    && request.strategyConfig?.logicVersion
    && request.beginDate
    && request.endDate
    && request.initCash !== null
    && request.initCash !== undefined
    && request.benchmarkCode
    && request.windowMode
    && request.trainDays !== null
    && request.trainDays !== undefined
    && request.testDays !== null
    && request.testDays !== undefined
    && request.stepDays !== null
    && request.stepDays !== undefined
    && request.commissionRate !== null
    && request.commissionRate !== undefined
    && request.stampTaxRate !== null
    && request.stampTaxRate !== undefined
    && request.buySlippage !== null
    && request.buySlippage !== undefined
    && request.sellSlippage !== null
    && request.sellSlippage !== undefined
    && result?.executionModelVersion
    && result.priceAdjustment
    && /^[0-9a-f]{64}$/i.test(result.dataFingerprint || '')
  )
}

async function onRollingEvaluate() {
  await runRollingEvaluation()
}

function applyExperimentDetail(detail) {
  if (!detail?.request || !detail?.result) return false
  const restored = restoreRollingForms(detail.request, form.value, labForm.value)
  form.value = restored.backtestForm
  labForm.value = restored.labForm
  rollingResult.value = detail.result
  return true
}

async function loadExperimentDetail(row) {
  experimentLoading.value = true
  try {
    const response = await getBacktestExperiment(row.id)
    return response.data || null
  } catch (error) {
    ElMessage.error(error.message || '实验历史加载失败')
    return null
  } finally {
    experimentLoading.value = false
  }
}

async function onViewExperiment(row) {
  if (labLoading.value || experimentLoading.value) return
  const detail = await loadExperimentDetail(row)
  if (!applyExperimentDetail(detail)) return
  document.getElementById('rolling-lab-title')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function onRerunExperiment(row) {
  if (labLoading.value || experimentLoading.value) return
  const detail = await loadExperimentDetail(row)
  if (!applyExperimentDetail(detail)) return
  if (!hasCompleteReplaySnapshot(detail)) {
    ElMessage.warning('旧实验缺少完整审计快照，无法精确复跑')
    return
  }
  await runRollingEvaluation(detail.request.strategyConfig, detail.result)
}

async function onRemoveExperiment(row) {
  if (labLoading.value || experimentLoading.value) return
  try {
    await ElMessageBox.confirm(`确认删除实验 #${row.id}？`, '删除实验', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    experimentLoading.value = true
    await removeBacktestExperiment(row.id)
    selectedExperimentIds.value = selectedExperimentIds.value.filter((experimentId) => experimentId !== row.id)
    await loadExperiments()
    ElMessage.success('实验历史已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '删除失败')
    }
  } finally {
    experimentLoading.value = false
  }
}

function isExperimentSelected(row) {
  return selectedExperimentIds.value.includes(row.id)
}

function toggleExperimentCompare(row, selected) {
  if (!selected) {
    selectedExperimentIds.value = selectedExperimentIds.value.filter((experimentId) => experimentId !== row.id)
    return
  }
  if (selectedExperimentIds.value.length >= 2) {
    ElMessage.warning('最多对比两条实验')
    return
  }
  selectedExperimentIds.value = [...selectedExperimentIds.value, row.id]
}

function formatExperimentTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function onBenchmark() {
  if (loading.value) return
  resetBacktestResults()
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
      const [detailResult] = await Promise.allSettled([getBacktestDetail(data.job.id)])
      const detail = detailResult.status === 'fulfilled' ? detailResult.value.data || {} : {}
      trades.value = detail.trades || []
      expectancy.value = detail.expectancy
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
  return buildApiUrl(`/api/export/backtest/${job.value.id}`)
}

onMounted(() => {
  loadJobs()
  loadExperiments()
})
</script>

<template>
  <div class="page">
    <header class="header">
      <div>
        <p class="eyebrow">Backtest</p>
        <h1>策略实验室</h1>
        <p>含佣金/印花税/滑点 · 过去表现不代表未来收益 · 对照决策页策略强度</p>
      </div>
      <div class="actions">
        <el-button type="primary" plain @click="router.push(`/stock/${form.code}`)">看K线</el-button>
        <el-button plain @click="router.push('/decision')">智能决策</el-button>
        <el-button plain @click="router.push('/signals')">信号</el-button>
      </div>
    </header>

    <el-form :inline="true" class="form">
      <el-form-item label="代码">
        <el-input v-model="form.code" :disabled="labControlsDisabled" style="width: 110px" />
      </el-form-item>
      <el-form-item label="策略">
        <el-select v-model="form.strategyId" :disabled="labControlsDisabled" style="width: 120px">
          <el-option label="S1 均线趋势" value="S1" />
          <el-option label="S2 RSI回调" value="S2" />
          <el-option label="S3 突破放量" value="S3" />
        </el-select>
      </el-form-item>
      <el-form-item label="开始">
        <el-input v-model="form.beginDate" :disabled="labControlsDisabled" style="width: 130px" />
      </el-form-item>
      <el-form-item label="结束">
        <el-input v-model="form.endDate" :disabled="labControlsDisabled" style="width: 130px" />
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
      <el-form-item v-if="job">
        <el-link :href="exportUrl()" target="_blank" type="primary">导出CSV</el-link>
      </el-form-item>
    </el-form>

    <section class="lab-section" aria-labelledby="rolling-lab-title">
      <div class="lab-head">
        <div>
          <p class="eyebrow">Strategy lab</p>
          <h2 id="rolling-lab-title">滚动样本外</h2>
        </div>
        <el-button type="primary" :loading="labLoading" :disabled="labControlsDisabled" @click="onRollingEvaluate">
          运行滚动评估
        </el-button>
      </div>

      <div class="lab-controls">
        <label class="lab-control lab-control-mode">
          <span>训练窗口</span>
          <el-segmented v-model="labForm.windowMode" :options="windowModes" :disabled="labControlsDisabled" />
        </label>
        <label class="lab-control">
          <span>训练日</span>
          <el-input-number v-model="labForm.trainDays" :min="60" :max="1250" :step="21" :disabled="labControlsDisabled" />
        </label>
        <label class="lab-control">
          <span>测试日</span>
          <el-input-number v-model="labForm.testDays" :min="20" :max="500" :step="21" :disabled="labControlsDisabled" />
        </label>
        <label class="lab-control">
          <span>步长</span>
          <el-input-number v-model="labForm.stepDays" :min="labForm.testDays" :max="500" :step="21" :disabled="labControlsDisabled" />
        </label>
        <label class="lab-control">
          <span>基准</span>
          <el-select v-model="labForm.benchmarkCode" :disabled="labControlsDisabled">
            <el-option
              v-for="benchmarkOption in benchmarkOptions"
              :key="benchmarkOption.value"
              :label="benchmarkOption.label"
              :value="benchmarkOption.value"
            />
          </el-select>
        </label>
        <label class="lab-control">
          <span>初始资金</span>
          <el-input-number v-model="form.initCash" :min="0.01" :step="100000" :precision="2" :disabled="labControlsDisabled" />
        </label>
      </div>

      <div class="lab-costs">
        <label class="lab-control">
          <span>佣金 (%)</span>
          <el-input-number v-model="labForm.commissionPercent" :min="0" :max="5" :step="0.01" :precision="6" :disabled="labControlsDisabled" />
        </label>
        <label class="lab-control">
          <span>印花税 (%)</span>
          <el-input-number v-model="labForm.stampTaxPercent" :min="0" :max="5" :step="0.01" :precision="6" :disabled="labControlsDisabled" />
        </label>
        <label class="lab-control">
          <span>买入滑点 (%)</span>
          <el-input-number v-model="labForm.buySlippagePercent" :min="0" :max="5" :step="0.01" :precision="6" :disabled="labControlsDisabled" />
        </label>
        <label class="lab-control">
          <span>卖出滑点 (%)</span>
          <el-input-number v-model="labForm.sellSlippagePercent" :min="0" :max="5" :step="0.01" :precision="6" :disabled="labControlsDisabled" />
        </label>
      </div>

      <div v-if="rollingResult" class="lab-result">
        <div class="lab-run-meta">
          <span>{{ rollingResult.code }} · {{ rollingResult.strategyName }} · {{ rollingResult.strategyParameters }}</span>
          <span>{{ rollingResult.benchmarkCode }} · {{ rollingResult.windowMode === 'EXPANDING' ? '扩展窗口' : '固定窗口' }}</span>
          <span>初始资金 {{ formatAmount(rollingResult.initCash) }} 元 · 训练 {{ rollingResult.trainDays }} / 样本外 {{ rollingResult.testDays }} / 步长 {{ rollingResult.stepDays }}</span>
          <span>数据 {{ rollingResult.dataBeginDate }} 至 {{ rollingResult.dataEndDate }} · 样本外 {{ rollingResult.outSampleBeginDate }} 至 {{ rollingResult.outSampleEndDate }}</span>
          <span>佣金 {{ formatPercent(rollingResult.cost?.commissionRate) }} · 印花税 {{ formatPercent(rollingResult.cost?.stampTaxRate) }} · 滑点 买 {{ formatPercent(rollingResult.cost?.buySlippage) }} / 卖 {{ formatPercent(rollingResult.cost?.sellSlippage) }}</span>
          <span>{{ rollingResult.executionModelVersion || '未知成交模型' }} · {{ rollingResult.priceAdjustment || '未知复权口径' }}</span>
          <span v-if="rollingResult.dataFingerprint" :title="rollingResult.dataFingerprint">
            数据指纹 {{ rollingResult.dataFingerprint.slice(0, 12) }}
          </span>
        </div>

        <div class="lab-metrics">
          <div class="lab-metric">
            <span>窗口</span>
            <b>{{ rollingResult.foldCount }}</b>
          </div>
          <div class="lab-metric">
            <span>样本外复合</span>
            <b :class="Number(rollingResult.compoundedOutSampleReturn) >= 0 ? 'up' : 'down'">
              {{ formatPercent(rollingResult.compoundedOutSampleReturn) }}
            </b>
          </div>
          <div class="lab-metric">
            <span>基准复合</span>
            <b>{{ formatPercent(rollingResult.compoundedBenchmarkReturn) }}</b>
          </div>
          <div class="lab-metric">
            <span>复合超额</span>
            <b :class="Number(rollingResult.compoundedExcessReturn) >= 0 ? 'up' : 'down'">
              {{ formatPercent(rollingResult.compoundedExcessReturn) }}
            </b>
          </div>
          <div class="lab-metric">
            <span>正收益窗口</span>
            <b>{{ formatPercent(rollingResult.positiveFoldRate, 0) }}</b>
          </div>
          <div class="lab-metric">
            <span>跑赢基准</span>
            <b>{{ formatPercent(rollingResult.benchmarkWinRate, 0) }}</b>
          </div>
          <div class="lab-metric">
            <span>整体夏普</span>
            <b>{{ Number(rollingResult.outSampleSharpe || 0).toFixed(2) }}</b>
          </div>
          <div class="lab-metric">
            <span>最差回撤</span>
            <b class="risk">{{ formatPercent(rollingResult.worstOutSampleDrawdown) }}</b>
          </div>
          <div class="lab-metric">
            <span>窗口覆盖</span>
            <b>{{ formatPercent(rollingResult.coverageRate, 0) }}</b>
          </div>
        </div>

        <div class="lab-table-wrap">
          <el-table class="lab-fold-table" :data="rollingResult.folds || []" size="small">
            <el-table-column prop="foldNo" label="#" width="48" />
            <el-table-column label="训练区间" min-width="210">
              <template #default="{ row }">{{ row.trainBeginDate }} 至 {{ row.trainEndDate }}</template>
            </el-table-column>
            <el-table-column label="样本外区间" min-width="210">
              <template #default="{ row }">{{ row.testBeginDate }} 至 {{ row.testEndDate }}</template>
            </el-table-column>
            <el-table-column label="样本外" width="96">
              <template #default="{ row }">{{ formatPercent(row.outSampleReturn) }}</template>
            </el-table-column>
            <el-table-column label="基准" width="96">
              <template #default="{ row }">{{ formatPercent(row.benchmarkReturn) }}</template>
            </el-table-column>
            <el-table-column label="超额" width="96">
              <template #default="{ row }">
                <span :class="Number(row.excessReturn) >= 0 ? 'up' : 'down'">{{ formatPercent(row.excessReturn) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="outSampleSharpe" label="夏普" width="82" />
            <el-table-column label="回撤" width="96">
              <template #default="{ row }">{{ formatPercent(row.outSampleMaxDrawdown) }}</template>
            </el-table-column>
            <el-table-column prop="tradeCount" label="成交" width="72" />
            <el-table-column label="期末状态" width="126">
              <template #default="{ row }">
                <span :class="Number(row.endingPositionQuantity || 0) > 0 ? 'risk' : ''">
                  {{ Number(row.endingPositionQuantity || 0) > 0 ? `未平仓 ${row.endingPositionQuantity} 股` : '已清算' }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div class="experiment-history" v-loading="experimentLoading">
        <div class="experiment-head">
          <div>
            <p class="eyebrow">Research archive</p>
            <h3>实验历史</h3>
          </div>
          <span>{{ experimentRows.length }} 条</span>
        </div>

        <div v-if="experimentCompareRows.length === 2" class="experiment-compare">
          <article v-for="experiment in experimentCompareRows" :key="experiment.id" class="experiment-compare-item">
            <header>
              <b>{{ experiment.code }} · {{ experiment.strategyId }}</b>
              <span>#{{ experiment.id }}</span>
            </header>
            <div class="experiment-compare-context">
              <span>{{ experiment.benchmarkCode }} · {{ experiment.windowMode === 'EXPANDING' ? '扩展窗口' : '固定窗口' }} · 训练 {{ experiment.trainDays }} / 样本外 {{ experiment.testDays }} / 步长 {{ experiment.stepDays }} · {{ experiment.foldCount }} 窗</span>
              <span>{{ experiment.initCash != null ? `初始资金 ${formatAmount(experiment.initCash)} 元` : '未知初始资金' }}</span>
              <span>参数 {{ experiment.strategyParameters || '-' }}</span>
              <span>数据 {{ experiment.dataBeginDate }} 至 {{ experiment.dataEndDate }}</span>
              <span>样本外 {{ experiment.outSampleBeginDate }} 至 {{ experiment.outSampleEndDate }}</span>
              <span>佣金 {{ formatPercent(experiment.commissionRate) }} · 印花税 {{ formatPercent(experiment.stampTaxRate) }}</span>
              <span>滑点 买 {{ formatPercent(experiment.buySlippage) }} · 卖 {{ formatPercent(experiment.sellSlippage) }}</span>
              <span>{{ experiment.executionModelVersion || '未知成交模型' }} · {{ experiment.priceAdjustment || '未知复权口径' }}</span>
            </div>
            <div class="experiment-compare-metrics">
              <span>样本外 <b :class="Number(experiment.compoundedOutSampleReturn) >= 0 ? 'up' : 'down'">{{ formatPercent(experiment.compoundedOutSampleReturn) }}</b></span>
              <span>超额 <b :class="Number(experiment.compoundedExcessReturn) >= 0 ? 'up' : 'down'">{{ formatPercent(experiment.compoundedExcessReturn) }}</b></span>
              <span>夏普 <b>{{ Number(experiment.outSampleSharpe || 0).toFixed(2) }}</b></span>
              <span>最差回撤 <b class="risk">{{ formatPercent(experiment.worstOutSampleDrawdown) }}</b></span>
            </div>
            <small :title="experiment.dataFingerprint">指纹 {{ experiment.dataFingerprint?.slice(0, 12) || '-' }}</small>
          </article>
        </div>

        <div v-if="experimentRows.length" class="experiment-table-wrap experiment-desktop">
          <el-table class="experiment-table" :data="experimentRows" size="small">
            <el-table-column label="对比" width="56" align="center">
              <template #default="{ row }">
                <el-checkbox
                  :model-value="isExperimentSelected(row)"
                  :disabled="labLoading || experimentLoading"
                  :aria-label="`选择实验 ${row.id}`"
                  @change="(selected) => toggleExperimentCompare(row, selected)"
                />
              </template>
            </el-table-column>
            <el-table-column label="时间" width="142">
              <template #default="{ row }">{{ formatExperimentTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="标的 / 策略" min-width="150">
              <template #default="{ row }">
                <b>{{ row.code }}</b> · {{ row.strategyId }}
              </template>
            </el-table-column>
            <el-table-column label="窗口" width="112">
              <template #default="{ row }">{{ row.windowMode === 'EXPANDING' ? '扩展' : '固定' }} · {{ row.foldCount }}</template>
            </el-table-column>
            <el-table-column label="样本外" width="94">
              <template #default="{ row }"><span :class="Number(row.compoundedOutSampleReturn) >= 0 ? 'up' : 'down'">{{ formatPercent(row.compoundedOutSampleReturn) }}</span></template>
            </el-table-column>
            <el-table-column label="超额" width="94">
              <template #default="{ row }"><span :class="Number(row.compoundedExcessReturn) >= 0 ? 'up' : 'down'">{{ formatPercent(row.compoundedExcessReturn) }}</span></template>
            </el-table-column>
            <el-table-column label="夏普" width="72">
              <template #default="{ row }">{{ Number(row.outSampleSharpe || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column label="回撤" width="92">
              <template #default="{ row }"><span class="risk">{{ formatPercent(row.worstOutSampleDrawdown) }}</span></template>
            </el-table-column>
            <el-table-column label="操作" width="132" align="right">
              <template #default="{ row }">
                <div class="experiment-row-actions">
                  <el-tooltip content="查看结果"><el-button circle :icon="View" :disabled="labLoading || experimentLoading" aria-label="查看结果" @click="onViewExperiment(row)" /></el-tooltip>
                  <el-tooltip content="按原配置复跑"><el-button circle :icon="RefreshRight" :disabled="labLoading || experimentLoading" aria-label="按原配置复跑" @click="onRerunExperiment(row)" /></el-tooltip>
                  <el-tooltip content="删除实验"><el-button circle type="danger" plain :icon="Delete" :disabled="labLoading || experimentLoading" aria-label="删除实验" @click="onRemoveExperiment(row)" /></el-tooltip>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="experimentRows.length" class="experiment-mobile-list">
          <article v-for="experiment in experimentRows" :key="experiment.id" class="experiment-mobile-item">
            <header>
              <el-checkbox
                :model-value="isExperimentSelected(experiment)"
                :disabled="labLoading || experimentLoading"
                :aria-label="`选择实验 ${experiment.id}`"
                @change="(selected) => toggleExperimentCompare(experiment, selected)"
              />
              <div>
                <b>{{ experiment.code }} · {{ experiment.strategyId }}</b>
                <span>{{ formatExperimentTime(experiment.createTime) }} · #{{ experiment.id }}</span>
              </div>
            </header>
            <div class="experiment-mobile-metrics">
              <span>样本外 <b :class="Number(experiment.compoundedOutSampleReturn) >= 0 ? 'up' : 'down'">{{ formatPercent(experiment.compoundedOutSampleReturn) }}</b></span>
              <span>超额 <b :class="Number(experiment.compoundedExcessReturn) >= 0 ? 'up' : 'down'">{{ formatPercent(experiment.compoundedExcessReturn) }}</b></span>
              <span>夏普 <b>{{ Number(experiment.outSampleSharpe || 0).toFixed(2) }}</b></span>
              <span>回撤 <b class="risk">{{ formatPercent(experiment.worstOutSampleDrawdown) }}</b></span>
            </div>
            <div class="experiment-mobile-actions">
              <el-button :icon="View" :disabled="labLoading || experimentLoading" @click="onViewExperiment(experiment)">查看</el-button>
              <el-button :icon="RefreshRight" :disabled="labLoading || experimentLoading" @click="onRerunExperiment(experiment)">复跑</el-button>
              <el-button type="danger" plain :icon="Delete" :disabled="labLoading || experimentLoading" aria-label="删除实验" @click="onRemoveExperiment(experiment)" />
            </div>
          </article>
        </div>

        <div v-if="experimentError && !experimentLoading" class="experiment-error">
          <el-alert :title="experimentError" type="error" :closable="false" show-icon />
          <el-button :icon="RefreshRight" @click="loadExperiments">重试</el-button>
        </div>
        <el-empty v-else-if="!experimentLoading && !experimentRows.length" description="暂无实验历史" :image-size="56" />
      </div>
    </section>

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

    <div class="lab-table-wrap leaderboard-table-wrap" v-if="leaderboard.length">
      <el-table :data="leaderboard" size="small" style="min-width: 1460px; margin-bottom: 12px">
        <el-table-column prop="strategyId" label="配对策略榜" width="110" />
        <el-table-column prop="jobCount" label="配对批次" width="90" />
        <el-table-column label="冻结参数" min-width="260">
          <template #default="{ row }">{{ row.strategyParameters || '-' }}</template>
        </el-table-column>
        <el-table-column label="执行口径" width="170">
          <template #default="{ row }">
            {{ row.executionModelVersion || '-' }} · {{ row.priceAdjustment || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="资金 / 成本" min-width="300">
          <template #default="{ row }">
            {{ formatAmount(row.initCash) }} 元 · 佣 {{ formatPercent(row.commissionRate) }} · 税 {{ formatPercent(row.stampTaxRate) }} · 滑点 {{ formatPercent(row.buySlippage) }} / {{ formatPercent(row.sellSlippage) }}
          </template>
        </el-table-column>
        <el-table-column label="配置指纹" width="130">
          <template #default="{ row }">
            <span :title="row.comparisonConfigFingerprint">
              {{ row.comparisonConfigFingerprint ? row.comparisonConfigFingerprint.slice(0, 12) : '-' }}
            </span>
          </template>
        </el-table-column>
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
    </div>

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

    <div v-if="job" class="stat-cards" style="grid-template-columns: repeat(4, minmax(0, 1fr)); margin-bottom: 12px">
      <div class="stat-card">
        <label><TermTip term="total_return">累计收益</TermTip></label>
        <b :class="Number(job.totalReturn) >= 0 ? 'up' : 'down'">
          {{ job.totalReturn != null ? (Number(job.totalReturn) * 100).toFixed(2) + '%' : '-' }}
        </b>
      </div>
      <div class="stat-card">
        <label><TermTip term="annualized_return">年化</TermTip></label>
        <b>{{ job.annualReturn != null ? (Number(job.annualReturn) * 100).toFixed(2) + '%' : '-' }}</b>
      </div>
      <div class="stat-card">
        <label><TermTip term="max_drawdown">最大回撤</TermTip></label>
        <b class="down">{{ job.maxDrawdown != null ? (Number(job.maxDrawdown) * 100).toFixed(2) + '%' : '-' }}</b>
      </div>
      <div class="stat-card">
        <label><TermTip term="sharpe">夏普</TermTip></label>
        <b>{{ job.sharpe != null ? Number(job.sharpe).toFixed(2) : '-' }}</b>
      </div>
      <div class="stat-card">
        <label><TermTip term="sortino">Sortino</TermTip></label>
        <b>{{ job.sortino != null ? Number(job.sortino).toFixed(2) : '-' }}</b>
      </div>
      <div class="stat-card">
        <label><TermTip term="win_rate">胜率</TermTip></label>
        <b>{{ job.winRate != null ? (Number(job.winRate) * 100).toFixed(1) + '%' : '-' }}</b>
      </div>
      <div class="stat-card">
        <label><TermTip term="expectancy">期望/笔</TermTip></label>
        <b>{{ expectancy != null ? expectancy : '-' }}</b>
      </div>
      <div class="stat-card">
        <label>成交</label>
        <b>{{ job.tradeCount ?? '-' }}</b>
      </div>
    </div>

    <div v-if="job" ref="chartRef" class="chart" />

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

    <h3 v-if="jobs.length">历史任务</h3>
    <el-table v-if="jobs.length" :data="jobs" size="small" height="180" @row-click="(row) => showDetail(row.id)">
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

    <el-table v-if="trades.length" :data="trades" height="240" style="margin-top: 12px">
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

.lab-section {
  margin: 8px 0 18px;
  padding: 20px 0;
  border-top: 1px solid var(--line-strong);
  border-bottom: 1px solid var(--line);
}

.lab-head,
.lab-run-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.lab-head {
  margin-bottom: 18px;
}

.lab-head h2 {
  margin: 0;
  font-size: 20px;
  line-height: 1.25;
  letter-spacing: 0;
}

.lab-controls,
.lab-costs {
  display: grid;
  grid-template-columns: minmax(210px, 1.4fr) repeat(5, minmax(110px, 1fr));
  gap: 12px;
}

.lab-costs {
  grid-template-columns: repeat(4, minmax(140px, 1fr));
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed var(--line);
}

.lab-control {
  display: grid;
  gap: 6px;
  min-width: 0;
  color: var(--slate);
  font-size: 12px;
}

.lab-control :deep(.el-input-number),
.lab-control :deep(.el-select),
.lab-control :deep(.el-segmented) {
  width: 100%;
}

.lab-result {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid var(--line-strong);
}

.lab-run-meta {
  justify-content: flex-start;
  color: var(--slate);
  font-size: 12px;
}

.lab-run-meta span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.lab-run-meta span + span::before {
  content: "·";
  margin-right: 12px;
  color: var(--line-strong);
}

.lab-metrics {
  display: grid;
  grid-template-columns: repeat(9, minmax(88px, 1fr));
  margin: 16px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.lab-metric {
  min-width: 0;
  padding: 12px 10px;
  border-left: 1px solid var(--line);
}

.lab-metric:first-child {
  border-left: 0;
}

.lab-metric span,
.lab-metric b {
  display: block;
  overflow-wrap: anywhere;
}

.lab-metric span {
  min-height: 32px;
  color: var(--slate);
  font-size: 11px;
  line-height: 1.4;
}

.lab-metric b {
  margin-top: 4px;
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}

.lab-metric .risk {
  color: var(--warn);
}

.lab-table-wrap {
  max-width: 100%;
  overflow-x: auto;
}

.lab-fold-table {
  min-width: 1020px;
}

.experiment-history {
  min-height: 120px;
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid var(--line-strong);
}

.experiment-head,
.experiment-compare-item header,
.experiment-mobile-item header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.experiment-head {
  margin-bottom: 12px;
}

.experiment-head h3 {
  margin: 0;
  font-size: 17px;
  letter-spacing: 0;
}

.experiment-head > span,
.experiment-compare-item header span,
.experiment-mobile-item header span,
.experiment-compare-item small {
  color: var(--slate);
  font-size: 12px;
}

.experiment-table-wrap {
  max-width: 100%;
  overflow-x: auto;
}

.experiment-table {
  min-width: 940px;
}

.experiment-row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

.experiment-row-actions :deep(.el-button) {
  width: 32px;
  height: 32px;
  margin: 0;
}

.experiment-compare {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 14px;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.experiment-compare-item {
  min-width: 0;
  padding: 12px;
}

.experiment-compare-item + .experiment-compare-item {
  border-left: 1px solid var(--line);
}

.experiment-compare-metrics,
.experiment-mobile-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
  margin: 10px 0;
  color: var(--slate);
  font-size: 12px;
}

.experiment-compare-context {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 4px 12px;
  margin-top: 8px;
  color: var(--slate);
  font-size: 12px;
}

.experiment-compare-context span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.experiment-compare-metrics b,
.experiment-mobile-metrics b {
  display: block;
  margin-top: 2px;
  color: var(--ink);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.experiment-compare-metrics .risk,
.experiment-mobile-metrics .risk,
.experiment-table .risk {
  color: var(--warn);
}

.experiment-mobile-list {
  display: none;
}

.experiment-error {
  display: flex;
  align-items: center;
  gap: 10px;
}

.experiment-error .el-alert {
  flex: 1;
  min-width: 0;
}

@media (max-width: 900px) {
  .lab-controls {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .lab-control-mode {
    grid-column: 1 / -1;
  }

  .lab-costs {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .lab-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .lab-metric:nth-child(3n + 1) {
    border-left: 0;
  }
}

@media (max-width: 420px) {
  .lab-head {
    align-items: stretch;
  }

  .lab-head > .el-button {
    width: 100%;
    min-height: 44px;
    touch-action: manipulation;
  }

  .lab-controls,
  .lab-costs {
    grid-template-columns: minmax(0, 1fr);
  }

  .lab-control-mode {
    grid-column: auto;
  }

  .lab-control :deep(.el-select__wrapper),
  .lab-control :deep(.el-input__wrapper),
  .lab-control :deep(.el-input-number),
  .lab-control :deep(.el-segmented) {
    min-height: 44px;
  }

  .lab-control :deep(.el-input-number__decrease),
  .lab-control :deep(.el-input-number__increase) {
    width: 44px;
    height: 44px;
    touch-action: manipulation;
  }

  .lab-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .lab-metric,
  .lab-metric:nth-child(3n + 1) {
    border-left: 1px solid var(--line);
  }

  .lab-metric:nth-child(2n + 1) {
    border-left: 0;
  }

  .experiment-desktop {
    display: none;
  }

  .experiment-mobile-list {
    display: grid;
    gap: 10px;
  }

  .experiment-compare {
    grid-template-columns: minmax(0, 1fr);
  }

  .experiment-compare-item + .experiment-compare-item {
    border-top: 1px solid var(--line);
    border-left: 0;
  }

  .experiment-mobile-item {
    min-width: 0;
    padding: 12px;
    border: 1px solid var(--line);
    border-radius: 4px;
  }

  .experiment-mobile-item header {
    justify-content: flex-start;
  }

  .experiment-mobile-item header > div {
    display: grid;
    min-width: 0;
    gap: 2px;
  }

  .experiment-mobile-actions {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 44px;
    gap: 8px;
  }

  .experiment-mobile-actions :deep(.el-button) {
    width: 100%;
    min-height: 44px;
    margin: 0;
    touch-action: manipulation;
  }

  .experiment-error {
    align-items: stretch;
    flex-direction: column;
  }

  .experiment-error :deep(.el-button) {
    min-height: 44px;
    margin: 0;
  }
}
</style>
