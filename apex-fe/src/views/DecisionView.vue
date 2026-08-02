<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchDecisionAttribution,
  fetchDecisionHistory,
  fetchDecisionPlaybook,
  fetchDecisionToday,
  runDecision,
} from '../api/decision'
import { getAccount, orderFromSignal, placeOrder } from '../api/paper'

const router = useRouter()
const loading = ref(false)
const ordering = ref(false)
const groupName = ref('我的自选')
const data = ref(null)
const activeTab = ref('buys')
const history = ref([])
const playbook = ref(null)
const showRules = ref(true)
const attribution = ref(null)

const buys = computed(() => data.value?.buys || [])
const sells = computed(() => data.value?.sells || [])
const holds = computed(() => data.value?.holds || [])
const briefing = computed(() => data.value?.marketBriefing || null)
const factors = computed(() => briefing.value?.factors || [])
const tips = computed(() => briefing.value?.tips || [])
const indexLines = computed(() => briefing.value?.indexLines || [])
const hotThemes = computed(() => briefing.value?.hotThemes || [])
const strategies = computed(() => playbook.value?.strategies || [])

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

function dataLevelType(level) {
  if (level === 'GREEN') return 'success'
  if (level === 'YELLOW') return 'warning'
  if (level === 'RED') return 'error'
  return 'info'
}

async function loadHistory() {
  try {
    const res = await fetchDecisionHistory(12)
    history.value = res.data || []
  } catch {
    history.value = []
  }
}

async function loadPlaybook() {
  try {
    const res = await fetchDecisionPlaybook()
    playbook.value = res.data
  } catch {
    playbook.value = null
  }
}

async function loadAttribution() {
  try {
    const res = await fetchDecisionAttribution(20)
    attribution.value = res.data
  } catch {
    attribution.value = null
  }
}

function strategyName(id) {
  if (id === 'RISK') return 'RISK 止损止盈'
  const s = strategies.value.find((x) => x.strategyId === id)
  return s ? `${id} ${s.name}` : id || '-'
}

async function load() {
  loading.value = true
  try {
    const [res] = await Promise.all([
      fetchDecisionToday(undefined, groupName.value),
      loadPlaybook(),
    ])
    data.value = res.data
    pickDefaultTab()
    await Promise.all([loadHistory(), loadAttribution()])
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
    await Promise.all([loadHistory(), loadAttribution()])
    ElMessage.success(res.data?.message || '决策已生成')
  } catch (e) {
    ElMessage.error(e.message || '生成失败')
  } finally {
    loading.value = false
  }
}

async function openHistoryDay(row) {
  if (!row?.actionDate) return
  loading.value = true
  try {
    const res = await fetchDecisionToday(row.actionDate, groupName.value)
    data.value = res.data
    pickDefaultTab()
    ElMessage.success(`已切换到决策日 ${row.actionDate}`)
  } catch (e) {
    ElMessage.error(e.message || '加载历史决策失败')
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
          {{ data?.message || '先看市场立场 → 再按策略战法出单；评分与仓位有明确交易规则' }}
          <span v-if="data?.riskNote"> · {{ data.riskNote }}</span>
        </p>
      </div>
      <div class="actions">
        <el-input v-model="groupName" style="width: 130px" placeholder="自选分组" />
        <el-button type="primary" :loading="loading" @click="onRun">一键生成决策</el-button>
        <el-button @click="load">刷新</el-button>
        <el-button @click="showRules = !showRules">{{ showRules ? '收起战法' : '策略战法' }}</el-button>
        <el-button @click="router.push('/signals')">信号明细</el-button>
      </div>
    </header>

    <section v-if="showRules && playbook" class="playbook">
      <div class="playbook-head">
        <h2>策略战法</h2>
        <span class="muted">{{ playbook.message }}</span>
      </div>
      <div class="strategy-grid">
        <article v-for="s in strategies" :key="s.strategyId" class="strategy-card">
          <header>
            <b>{{ s.strategyId }} · {{ s.name }}</b>
            <el-tag size="small">{{ s.style }}</el-tag>
          </header>
          <p><label>买入</label>{{ s.buyRule }}</p>
          <p><label>离场</label>{{ s.exitRule }}</p>
          <p class="fit"><label>市况</label>{{ s.marketFit }}</p>
          <p v-if="s.paramsHint" class="params"><label>参数</label>{{ s.paramsHint }}</p>
        </article>
      </div>
      <div class="rules-grid">
        <div>
          <h3>流水线</h3>
          <ol>
            <li v-for="(step, i) in playbook.pipelineSteps || []" :key="i">{{ step.replace(/^\d+\.\s*/, '') }}</li>
          </ol>
        </div>
        <div>
          <h3>评分规则</h3>
          <ul>
            <li v-for="(r, i) in playbook.scoreRules || []" :key="'s'+i">{{ r }}</li>
          </ul>
          <h3>仓位规则</h3>
          <ul>
            <li v-for="(r, i) in playbook.positionRules || []" :key="'p'+i">{{ r }}</li>
          </ul>
        </div>
        <div>
          <h3>基本面门禁</h3>
          <ul>
            <li v-for="(r, i) in playbook.fundRules || []" :key="'f'+i">{{ r }}</li>
          </ul>
          <h3>卖出优先级</h3>
          <ul>
            <li v-for="(r, i) in playbook.sellRules || []" :key="'e'+i">{{ r }}</li>
          </ul>
        </div>
      </div>
    </section>

    <section v-if="briefing" class="briefing" :class="stanceClass(briefing.stance)">
      <div class="brief-head">
        <div>
          <div class="brief-kicker">
            每日市场简报 · {{ briefing.asOf || '-' }}
            <el-tag v-if="briefing.dataLevel" size="small" :type="dataLevelType(briefing.dataLevel)" class="data-tag">
              数据{{ briefing.dataLevel }}
            </el-tag>
          </div>
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
          <el-table-column label="策略" width="120">
            <template #default="{ row }">{{ strategyName(row.strategyId) }}</template>
          </el-table-column>
          <el-table-column label="评分" width="70">
            <template #default="{ row }">{{ fmtScore(row.score) }}</template>
          </el-table-column>
          <el-table-column label="建议仓位" width="90">
            <template #default="{ row }">{{ fmtPct(row.suggestedWeight) }}</template>
          </el-table-column>
          <el-table-column width="120">
            <template #header><TermTip term="confluence">共振策略</TermTip></template>
            <template #default="{ row }">
              <span v-if="row.strategies?.length">{{ row.strategies.join('+') }}</span>
              <span v-else>{{ row.confluenceCount || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="主线匹配" width="110">
            <template #default="{ row }">
              <el-tag v-if="row.mainlineMatch" size="small" type="warning">{{ row.mainlineName || '匹配' }}</el-tag>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="scoreExplain" label="评分拆解" min-width="200" show-overflow-tooltip />
          <el-table-column prop="reason" label="理由" min-width="180" show-overflow-tooltip />
          <el-table-column prop="fundNote" label="基本面" min-width="140" show-overflow-tooltip />
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
          <el-table-column label="策略" width="120">
            <template #default="{ row }">{{ strategyName(row.strategyId) }}</template>
          </el-table-column>
          <el-table-column label="评分" width="70">
            <template #default="{ row }">{{ fmtScore(row.score) }}</template>
          </el-table-column>
          <el-table-column prop="exitRule" label="触发规则" min-width="160" show-overflow-tooltip />
          <el-table-column prop="scoreExplain" label="卖出拆解" min-width="180" show-overflow-tooltip />
          <el-table-column prop="reason" label="理由" min-width="180" show-overflow-tooltip />
          <el-table-column prop="fundNote" label="基本面" min-width="140" show-overflow-tooltip />
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

    <section v-if="attribution" class="attribution">
      <h3>复盘归因</h3>
      <p class="muted">{{ attribution.message }}</p>
      <div class="attr-grid">
        <div>
          <h4>按策略</h4>
          <el-table :data="attribution.byStrategy || []" size="small" stripe empty-text="暂无">
            <el-table-column prop="label" label="桶" width="80" />
            <el-table-column prop="sampleCount" label="样本" width="60" />
            <el-table-column label="次日均%" width="90">
              <template #default="{ row }">
                <span :class="Number(row.avgNextPct) > 0 ? 'up' : Number(row.avgNextPct) < 0 ? 'down' : ''">
                  {{ row.avgNextPct == null ? '-' : Number(row.avgNextPct).toFixed(2) + '%' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="胜率" width="70">
              <template #default="{ row }">{{ row.winRate == null ? '-' : row.winRate + '%' }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div>
          <h4>按共振</h4>
          <el-table :data="attribution.byConfluence || []" size="small" stripe empty-text="暂无">
            <el-table-column prop="label" label="桶" width="80" />
            <el-table-column prop="sampleCount" label="样本" width="60" />
            <el-table-column label="次日均%" width="90">
              <template #default="{ row }">
                <span :class="Number(row.avgNextPct) > 0 ? 'up' : Number(row.avgNextPct) < 0 ? 'down' : ''">
                  {{ row.avgNextPct == null ? '-' : Number(row.avgNextPct).toFixed(2) + '%' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="胜率" width="70">
              <template #default="{ row }">{{ row.winRate == null ? '-' : row.winRate + '%' }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div>
          <h4>按主线</h4>
          <el-table :data="attribution.byMainline || []" size="small" stripe empty-text="暂无">
            <el-table-column prop="label" label="桶" width="90" />
            <el-table-column prop="sampleCount" label="样本" width="60" />
            <el-table-column label="次日均%" width="90">
              <template #default="{ row }">
                <span :class="Number(row.avgNextPct) > 0 ? 'up' : Number(row.avgNextPct) < 0 ? 'down' : ''">
                  {{ row.avgNextPct == null ? '-' : Number(row.avgNextPct).toFixed(2) + '%' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="胜率" width="70">
              <template #default="{ row }">{{ row.winRate == null ? '-' : row.winRate + '%' }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div>
          <h4>按市场立场</h4>
          <el-table :data="attribution.byStance || []" size="small" stripe empty-text="暂无">
            <el-table-column prop="label" label="桶" width="90" />
            <el-table-column prop="sampleCount" label="样本" width="60" />
            <el-table-column label="次日均%" width="90">
              <template #default="{ row }">
                <span :class="Number(row.avgNextPct) > 0 ? 'up' : Number(row.avgNextPct) < 0 ? 'down' : ''">
                  {{ row.avgNextPct == null ? '-' : Number(row.avgNextPct).toFixed(2) + '%' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="胜率" width="70">
              <template #default="{ row }">{{ row.winRate == null ? '-' : row.winRate + '%' }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div>
          <h4>卖出按策略（次日表现，越负越好）</h4>
          <el-table :data="attribution.bySellStrategy || []" size="small" stripe empty-text="暂无卖出样本">
            <el-table-column prop="label" label="桶" width="100" />
            <el-table-column prop="sampleCount" label="样本" width="60" />
            <el-table-column label="次日均%" width="90">
              <template #default="{ row }">
                <span :class="Number(row.avgNextPct) < 0 ? 'up' : Number(row.avgNextPct) > 0 ? 'down' : ''">
                  {{ row.avgNextPct == null ? '-' : Number(row.avgNextPct).toFixed(2) + '%' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="胜率*" width="70">
              <template #default="{ row }">
                <span class="muted">{{ row.winRate == null ? '-' : row.winRate + '%' }}</span>
              </template>
            </el-table-column>
          </el-table>
          <p class="muted tiny">* 卖出桶的「胜率」仍是次日上涨占比，解读时宜看次日均%是否为负</p>
        </div>
      </div>
    </section>

    <section v-if="history.length" class="history">
      <h3>决策历史 · 事后收益</h3>
      <p class="muted tiny">点击某一行可回看当日买卖清单（与复盘归因对照）</p>
      <el-table :data="history" size="small" stripe highlight-current-row @row-click="openHistoryDay" class="history-table">
        <el-table-column prop="actionDate" label="日期" width="120" />
        <el-table-column prop="stance" label="立场" width="70" />
        <el-table-column prop="dataLevel" label="数据" width="70" />
        <el-table-column prop="buyCount" label="买" width="60" />
        <el-table-column prop="sellCount" label="卖" width="60" />
        <el-table-column prop="holdCount" label="持有" width="60" />
        <el-table-column label="买入次日均涨跌%" width="140">
          <template #default="{ row }">
            <span :class="Number(row.nextDayAvgPct) > 0 ? 'up' : Number(row.nextDayAvgPct) < 0 ? 'down' : ''">
              {{ row.nextDayAvgPct == null ? '-' : Number(row.nextDayAvgPct).toFixed(2) + '%' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="说明" min-width="220" show-overflow-tooltip />
      </el-table>
    </section>
  </div>
</template>

<style scoped>
.playbook {
  margin-bottom: 16px;
  padding: 14px 16px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass);
}

.playbook-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 12px;
}

.playbook-head h2 {
  margin: 0;
  font-size: 16px;
}

.strategy-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.strategy-card {
  padding: 10px 12px;
  border: 1px solid var(--glass-border);
  border-radius: 8px;
}

.strategy-card header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.strategy-card p {
  margin: 4px 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--text);
}

.strategy-card label {
  color: var(--muted);
  margin-right: 6px;
}

.strategy-card .fit,
.strategy-card .params {
  color: var(--muted);
}

.rules-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr;
  gap: 12px;
}

.rules-grid h3 {
  margin: 0 0 6px;
  font-size: 13px;
}

.rules-grid ol,
.rules-grid ul {
  margin: 0 0 12px;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--muted);
}

.muted {
  color: var(--muted);
  font-size: 12px;
}

@media (max-width: 960px) {
  .strategy-grid,
  .rules-grid {
    grid-template-columns: 1fr;
  }
}

.attribution {
  margin-top: 20px;
}

.attribution h3 {
  margin: 0 0 6px;
  font-size: 15px;
}

.attribution h4 {
  margin: 0 0 8px;
  font-size: 13px;
}

.tiny {
  margin: 6px 0 0;
  font-size: 12px;
}

.attr-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 960px) {
  .attr-grid {
    grid-template-columns: 1fr;
  }
}

.history {
  margin-top: 20px;
}

.history-table :deep(.el-table__row) {
  cursor: pointer;
}

.history h3 {
  margin: 0 0 10px;
  font-size: 15px;
}

.up {
  color: var(--up, #ef5350);
}

.down {
  color: var(--down, #26a69a);
}

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
  display: flex;
  align-items: center;
  gap: 8px;
}

.data-tag {
  vertical-align: middle;
}

.muted {
  color: var(--muted);
  font-size: 12px;
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
