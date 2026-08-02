<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchDecisionToday, runDecision } from '../api/decision'
import { getAccount, orderFromSignal, placeOrder } from '../api/paper'

const router = useRouter()
const loading = ref(false)
const ordering = ref(false)
const groupName = ref('我的自选')
const data = ref(null)
const activeTab = ref('buys')

const buys = computed(() => data.value?.buys || [])
const sells = computed(() => data.value?.sells || [])
const holds = computed(() => data.value?.holds || [])
const briefing = computed(() => data.value?.marketBriefing || null)
const factors = computed(() => briefing.value?.factors || [])
const tips = computed(() => briefing.value?.tips || [])
const indexLines = computed(() => briefing.value?.indexLines || [])
const hotThemes = computed(() => briefing.value?.hotThemes || [])

function stanceClass(s) {
  if (s === '进攻') return 'stance-attack'
  if (s === '防守') return 'stance-defend'
  return 'stance-balance'
}

function signalClass(s) {
  if (s === '偏多') return 'up'
  if (s === '偏空') return 'down'
  return ''
}

function tipType(level) {
  if (level === 'danger') return 'error'
  if (level === 'warn') return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await fetchDecisionToday(undefined, groupName.value)
    data.value = res.data
    pickDefaultTab()
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onRun() {
  loading.value = true
  try {
    const res = await runDecision({ groupName: groupName.value })
    data.value = res.data
    pickDefaultTab()
    ElMessage.success(res.data?.message || '决策已生成')
  } catch (e) {
    ElMessage.error(e.message || '生成失败')
  } finally {
    loading.value = false
  }
}

function pickDefaultTab() {
  // 优先展示买入机会（不局限持仓）；有持仓卖出时再切到卖出
  if (buys.value.length) activeTab.value = 'buys'
  else if (sells.value.length) activeTab.value = 'sells'
  else activeTab.value = 'holds'
}

function fmtPct(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  if (Math.abs(n) <= 1) return (n * 100).toFixed(1) + '%'
  return n.toFixed(1) + '%'
}

function fmtScore(v) {
  if (v == null) return '-'
  return Number(v).toFixed(1)
}

async function onPaperOrder(row) {
  if (!row || row.action === 'HOLD') return
  const buy = row.action === 'BUY'
  try {
    const { value } = await ElMessageBox.prompt(
      buy
        ? '目标仓位比例(如 0.1=10%)；留空则按信号/建议仓位一键下单'
        : '卖出数量(股)；留空则按信号全平',
      `${row.code} ${row.action}`,
      {
        inputValue: buy ? String(row.suggestedWeight ?? 0.1) : '',
        confirmButtonText: '模拟下单',
      },
    )
    ordering.value = true
    const acc = await getAccount()
    const text = String(value ?? '').trim()
    if (!text && row.signalId) {
      await orderFromSignal(row.signalId, acc.data.id, buy ? row.suggestedWeight : undefined)
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
    } else if (!text && buy && row.suggestedWeight) {
      payload.targetWeight = Number(row.suggestedWeight)
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

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="header">
      <div>
        <h1>智能决策</h1>
        <p>
          {{ data?.message || '每日先看市场简报（大盘/风格/量能/涨停），再给出买卖清单' }}
          <span v-if="data?.riskNote"> · {{ data.riskNote }}</span>
        </p>
      </div>
      <div class="actions">
        <el-input v-model="groupName" style="width: 130px" placeholder="自选分组" />
        <el-button type="primary" :loading="loading" @click="onRun">一键生成决策</el-button>
        <el-button @click="load">刷新</el-button>
        <el-button @click="router.push('/market')">大盘</el-button>
        <el-button @click="router.push('/limit-up')">涨停复盘</el-button>
        <el-button @click="router.push('/signals')">信号明细</el-button>
      </div>
    </header>

    <section v-if="briefing" class="briefing" :class="stanceClass(briefing.stance)">
      <div class="brief-head">
        <div>
          <div class="brief-kicker">每日市场简报 · {{ briefing.asOf || '-' }}</div>
          <h2>
            立场
            <span class="stance-pill">{{ briefing.stance || '均衡' }}</span>
            <span class="score">{{ briefing.stanceScore ?? '-' }}/100</span>
          </h2>
          <p class="brief-reason">{{ briefing.stanceReason }}</p>
          <p class="brief-pos">{{ briefing.positionAdvice }}</p>
        </div>
        <div class="brief-side">
          <div v-if="indexLines.length" class="index-lines">
            <div v-for="line in indexLines" :key="line">{{ line }}</div>
          </div>
          <div v-if="hotThemes.length" class="theme-row">
            <span v-for="t in hotThemes.slice(0, 6)" :key="t" class="theme-chip">{{ t }}</span>
          </div>
        </div>
      </div>

      <div v-if="factors.length" class="factor-grid">
        <div v-for="f in factors" :key="f.name" class="factor">
          <label>{{ f.name }}</label>
          <b :class="signalClass(f.signal)">{{ f.value }}</b>
          <span class="factor-signal" :class="signalClass(f.signal)">{{ f.signal }}</span>
          <p>{{ f.note }}</p>
        </div>
      </div>

      <div v-if="tips.length" class="tips">
        <el-alert
          v-for="(tip, idx) in tips"
          :key="idx"
          class="tip-item"
          :type="tipType(tip.level)"
          :closable="false"
          show-icon
          :title="tip.text"
        />
      </div>
    </section>

    <div class="summary" v-if="data">
      <div><label>决策日</label><b>{{ data.actionDate || '-' }}</b></div>
      <div><label>分组</label><span>{{ data.groupName || '-' }}</span></div>
      <div><label>股票池</label><span>{{ data.universeCount ?? '-' }}</span></div>
      <div><label>建议买入</label><b class="up">{{ buys.length }}</b></div>
      <div><label>建议卖出</label><b class="down">{{ sells.length }}</b></div>
      <div><label>继续持有</label><span>{{ holds.length }}</span></div>
    </div>

    <el-tabs v-model="activeTab" class="tabs">
      <el-tab-pane :label="`建议买入 (${buys.length})`" name="buys">
        <el-alert
          class="tab-hint"
          type="info"
          :closable="false"
          show-icon
          title="买入来自「自选」股票池 + 多平台热点共振扩扫；多策略/热点共振会加分抬仓，基本面偏弱降权；已持仓标的会标注为加仓"
        />
        <el-table :data="buys" size="small" stripe empty-text="暂无买入机会（可先充实自选并同步日线后重跑）">
          <el-table-column prop="code" label="代码" width="100" fixed>
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" width="110" />
          <el-table-column prop="strategyId" label="策略" width="70" />
          <el-table-column label="评分" width="70">
            <template #default="{ row }">{{ fmtScore(row.score) }}</template>
          </el-table-column>
          <el-table-column label="建议仓位" width="90">
            <template #default="{ row }">{{ fmtPct(row.suggestedWeight) }}</template>
          </el-table-column>
          <el-table-column width="70">
            <template #header><TermTip term="confluence">共振</TermTip></template>
            <template #default="{ row }">
              <el-tag v-if="row.confluence" size="small" type="success">{{ row.confluenceCount }}</el-tag>
              <span v-else>{{ row.confluenceCount || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="理由" min-width="220" show-overflow-tooltip />
          <el-table-column prop="fundNote" label="基本面" min-width="160" show-overflow-tooltip />
          <el-table-column prop="exitRule" label="离场规则" width="140" show-overflow-tooltip />
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link :loading="ordering" @click="onPaperOrder(row)">模拟买</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`建议卖出 (${sells.length})`" name="sells">
        <el-alert
          class="tab-hint"
          type="warning"
          :closable="false"
          show-icon
          title="卖出只针对「我的持仓」：策略卖出信号或触及止损/止盈"
        />
        <el-table :data="sells" size="small" stripe empty-text="持仓暂无卖出建议">
          <el-table-column prop="code" label="代码" width="100" fixed>
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" width="110" />
          <el-table-column prop="strategyId" label="策略" width="70" />
          <el-table-column label="评分" width="70">
            <template #default="{ row }">{{ fmtScore(row.score) }}</template>
          </el-table-column>
          <el-table-column width="70">
            <template #header><TermTip term="confluence">共振</TermTip></template>
            <template #default="{ row }">
              <el-tag v-if="row.confluence" size="small" type="danger">{{ row.confluenceCount }}</el-tag>
              <span v-else>{{ row.confluenceCount || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="理由" min-width="220" show-overflow-tooltip />
          <el-table-column prop="fundNote" label="基本面" min-width="160" show-overflow-tooltip />
          <el-table-column prop="exitRule" label="离场" width="140" show-overflow-tooltip />
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button type="danger" link :loading="ordering" @click="onPaperOrder(row)">模拟卖</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`继续持有 (${holds.length})`" name="holds">
        <el-alert
          class="tab-hint"
          type="info"
          :closable="false"
          show-icon
          title="仅展示「我的持仓」中暂无卖出信号的标的"
        />
        <el-table :data="holds" size="small" stripe empty-text="「我的持仓」为空，或持仓均已有买卖建议">
          <el-table-column prop="code" label="代码" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" width="120" />
          <el-table-column prop="reason" label="理由" min-width="180" show-overflow-tooltip />
          <el-table-column prop="exitRule" label="止损/止盈" min-width="160" show-overflow-tooltip />
          <el-table-column prop="fundNote" label="基本面" min-width="180" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.briefing {
  margin-bottom: 16px;
  padding: 16px 18px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass);
}

.briefing.stance-attack {
  border-color: rgba(239, 83, 80, 0.35);
}

.briefing.stance-defend {
  border-color: rgba(64, 158, 255, 0.4);
}

.brief-head {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 16px;
  margin-bottom: 14px;
}

.brief-kicker {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 4px;
}

.brief-head h2 {
  margin: 0 0 8px;
  font-size: 22px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.stance-pill {
  display: inline-flex;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 700;
  background: rgba(0, 0, 0, 0.06);
}

.stance-attack .stance-pill {
  color: #c45656;
  background: rgba(239, 83, 80, 0.12);
}

.stance-defend .stance-pill {
  color: #3a7bd5;
  background: rgba(64, 158, 255, 0.14);
}

.stance-balance .stance-pill {
  color: #6b7280;
}

.score {
  font-size: 14px;
  color: var(--muted);
  font-weight: 600;
}

.brief-reason,
.brief-pos {
  margin: 0 0 4px;
  font-size: 13px;
  color: var(--muted);
}

.brief-pos {
  color: inherit;
  font-weight: 600;
}

.index-lines {
  font-size: 13px;
  line-height: 1.7;
}

.theme-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.theme-chip {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.05);
}

.factor-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.factor {
  padding: 10px 12px;
  border: 1px solid var(--glass-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.45);
}

.factor label {
  display: block;
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 4px;
}

.factor b {
  display: block;
  font-size: 13px;
  line-height: 1.35;
  margin-bottom: 4px;
}

.factor-signal {
  font-size: 12px;
  font-weight: 700;
}

.factor p {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--muted);
}

.tips {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tip-item {
  margin: 0;
}

.summary {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.summary > div {
  background: var(--glass);
  backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  -webkit-backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  padding: 12px 14px;
  box-shadow: var(--shadow-soft);
}

.summary label {
  display: block;
  color: var(--muted);
  font-size: 11px;
  margin-bottom: 6px;
}

.tabs {
  margin-top: 4px;
}

.tab-hint {
  margin-bottom: 10px;
}

@media (max-width: 900px) {
  .brief-head,
  .factor-grid,
  .summary {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
