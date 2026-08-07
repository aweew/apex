<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fillWatchlistBars } from '../api/bars'
import { runPipeline } from '../api/pipeline'
import { latestUniverse } from '../api/signal'

const router = useRouter()
const loading = ref(false)
const filling = ref(false)
const groupName = ref('我的自选')
const form = ref({
  refreshQuotes: true,
  syncStaleBars: true,
  refreshUniverse: false,
  runSignals: false,
  runDaily: false,
  runDecision: true,
})
const result = ref(null)
const fillResult = ref(null)
const universe = ref([])

const stepCards = computed(() => [
  { key: 'quotes', label: '刷新行情', on: form.value.refreshQuotes, tip: '自选最新价' },
  { key: 'bars', label: '同步日线', on: form.value.syncStaleBars, tip: '补齐 K 线' },
  { key: 'decision', label: '智能决策', on: form.value.runDecision, tip: '池·信号·观察' },
  { key: 'universe', label: '刷新股票池', on: form.value.refreshUniverse, tip: '全市场有日线标的' },
  { key: 'signals', label: '运行信号', on: form.value.runSignals, tip: '旧路径' },
  { key: 'daily', label: '日终清单', on: form.value.runDaily, tip: '旧路径' },
])

async function loadUniverse() {
  try {
    const res = await latestUniverse()
    universe.value = res.data || []
  } catch {
    universe.value = []
  }
}

async function onRun() {
  loading.value = true
  try {
    const res = await runPipeline({
      groupName: groupName.value,
      ...form.value,
    })
    result.value = res.data
    ElMessage.success((res.data.steps || []).join(' → '))
    await loadUniverse()
  } catch (e) {
    ElMessage.error(e.message || '流水线失败')
  } finally {
    loading.value = false
  }
}

async function onFillBars() {
  filling.value = true
  try {
    const res = await fillWatchlistBars(groupName.value, 3, 40)
    fillResult.value = res.data
    ElMessage.success(
      `补齐 ${res.data.rounds} 轮 · 成功 ${res.data.totalSuccess} · K线 ${res.data.totalBars}`,
    )
  } catch (e) {
    ElMessage.error(e.message || '补齐失败')
  } finally {
    filling.value = false
  }
}

function toggleStep(key) {
  if (key === 'quotes') form.value.refreshQuotes = !form.value.refreshQuotes
  if (key === 'bars') form.value.syncStaleBars = !form.value.syncStaleBars
  if (key === 'decision') {
    form.value.runDecision = !form.value.runDecision
    if (form.value.runDecision) {
      form.value.refreshUniverse = false
      form.value.runSignals = false
      form.value.runDaily = false
    }
  }
  if (key === 'universe' && !form.value.runDecision) {
    form.value.refreshUniverse = !form.value.refreshUniverse
  }
  if (key === 'signals' && !form.value.runDecision) {
    form.value.runSignals = !form.value.runSignals
  }
  if (key === 'daily' && !form.value.runDecision) {
    form.value.runDaily = !form.value.runDaily
  }
}

onMounted(loadUniverse)
</script>

<template>
  <div class="page pipe-page">
    <header class="header">
      <div>
        <p class="eyebrow">灵枢 · Pipeline</p>
        <h1>研究流水线</h1>
        <p class="sub">一键：行情准备 → 智能决策（股票池 · 信号 · 研判 · 观察池）</p>
      </div>
      <div class="header-actions">
        <el-button :loading="filling" @click="onFillBars">多轮补齐 K 线</el-button>
        <el-button type="primary" class="cta" :loading="loading" @click="onRun">一键运行</el-button>
        <el-button plain @click="router.push('/decision')">决策</el-button>
        <el-button plain @click="router.push('/observe')">观察池</el-button>
      </div>
    </header>

    <section class="pipe-panel">
      <div class="group-row">
        <label>自选分组</label>
        <el-input v-model="groupName" style="width: 200px" />
      </div>

      <div class="step-grid">
        <button
          v-for="s in stepCards"
          :key="s.key"
          type="button"
          class="step-card"
          :class="{ on: s.on, muted: form.runDecision && ['universe', 'signals', 'daily'].includes(s.key) }"
          :disabled="form.runDecision && ['universe', 'signals', 'daily'].includes(s.key)"
          @click="toggleStep(s.key)"
        >
          <b>{{ s.label }}</b>
          <span>{{ s.tip }}</span>
          <em>{{ s.on ? '开' : '关' }}</em>
        </button>
      </div>
    </section>

    <section v-if="result" class="result-panel">
      <div class="result-head">
        <h2>运行结果</h2>
        <div class="result-metrics" v-if="result.decisionBuyCount != null">
          <span>买 <b class="up">{{ result.decisionBuyCount }}</b></span>
          <span>卖 <b class="down">{{ result.decisionSellCount }}</b></span>
          <span>持 <b>{{ result.decisionHoldCount }}</b></span>
          <span v-if="result.decisionExecutableCount != null">可执行 <b>{{ result.decisionExecutableCount }}</b></span>
          <span v-if="result.decisionValuationCheapCount != null">低估 <b class="up">{{ result.decisionValuationCheapCount }}</b></span>
          <span v-if="result.observeUpserted != null">观察池 <b>{{ result.observeUpserted }}</b></span>
        </div>
      </div>
      <ol class="step-list">
        <li v-for="(step, i) in result.steps || []" :key="i">{{ step }}</li>
      </ol>
      <div class="actions">
        <el-button type="primary" @click="router.push('/decision')">看智能决策</el-button>
        <el-button @click="router.push('/observe')">看观察池</el-button>
        <el-button @click="router.push('/signals')">看信号</el-button>
        <el-button @click="router.push('/dashboard')">看板</el-button>
      </div>
    </section>

    <el-alert
      v-if="fillResult"
      :title="`K线补齐：${fillResult.rounds}轮 · 成功${fillResult.totalSuccess} · 失败${fillResult.totalFail} · 写入${fillResult.totalBars}`"
      type="info"
      :closable="false"
    />

    <section class="uni-panel">
      <div class="uni-head">
        <h3>当前股票池 {{ universe.length }}</h3>
        <el-button link type="primary" @click="loadUniverse">刷新</el-button>
      </div>
      <el-table :data="universe" height="380" size="small" stripe empty-text="暂无股票池，先跑流水线">
        <el-table-column prop="code" label="代码" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" width="140">
          <template #default="{ row }">
            <StockBoardTag :code="row.code" :market="row.market">{{ row.name || '-' }}</StockBoardTag>
          </template>
        </el-table-column>
        <el-table-column prop="reasonTags" label="标签/评分" min-width="280" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="批次" width="150" />
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.pipe-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pipe-page .header {
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

.cta {
  min-width: 120px;
}

.pipe-panel,
.result-panel,
.uni-panel {
  padding: 16px 18px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass-strong);
  box-shadow: var(--shadow-soft);
}

.group-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.group-row label {
  font-size: 13px;
  color: var(--muted);
}

.step-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
}

.step-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, background 0.15s;
}

.step-card b {
  font-size: 13px;
}

.step-card span {
  font-size: 11px;
  color: var(--muted);
}

.step-card em {
  font-style: normal;
  font-size: 11px;
  font-weight: 700;
  color: var(--muted);
  margin-top: 4px;
}

.step-card.on {
  border-color: rgba(0, 113, 227, 0.35);
  background: var(--accent-soft);
}

.step-card.on em {
  color: var(--accent);
}

.step-card.muted,
.step-card:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.result-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.result-head h2 {
  margin: 0;
  font-size: 16px;
}

.result-metrics {
  display: flex;
  gap: 14px;
  font-size: 13px;
  color: var(--muted);
}

.result-metrics b {
  font-size: 16px;
  margin-left: 4px;
}

.step-list {
  margin: 0 0 12px;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-soft);
}

.uni-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.uni-head h3 {
  margin: 0;
}

.up {
  color: var(--up);
}

.down {
  color: var(--down);
}

@media (max-width: 1100px) {
  .step-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .step-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
