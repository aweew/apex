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
const attribution = ref(null)
const morePanels = ref([])
const FILTER_PREF_KEY = 'apex.decision.buyFilters'
const savedFilters = (() => {
  try {
    return JSON.parse(localStorage.getItem(FILTER_PREF_KEY) || '{}')
  } catch {
    return {}
  }
})()
const buyStrategyFilter = ref(savedFilters.strategy || '')
const buyMinScore = ref(savedFilters.minScore ?? '')
const buyMainlineOnly = ref(!!savedFilters.mainlineOnly)
const buyExecutableOnly = ref(!!savedFilters.executableOnly)
const buyCheapOnly = ref(!!savedFilters.cheapOnly)

function persistBuyFilters() {
  try {
    localStorage.setItem(
      FILTER_PREF_KEY,
      JSON.stringify({
        strategy: buyStrategyFilter.value,
        minScore: buyMinScore.value,
        mainlineOnly: buyMainlineOnly.value,
        executableOnly: buyExecutableOnly.value,
        cheapOnly: buyCheapOnly.value,
      }),
    )
  } catch {
    /* ignore */
  }
}

const buys = computed(() => data.value?.buys || [])
const sells = computed(() => data.value?.sells || [])
const holds = computed(() => data.value?.holds || [])
const filteredBuys = computed(() => {
  const min = buyMinScore.value !== '' ? Number(buyMinScore.value) : null
  return buys.value.filter((row) => {
    if (buyStrategyFilter.value && row.strategyId !== buyStrategyFilter.value) return false
    if (buyMainlineOnly.value && !row.mainlineMatch) return false
    if (buyExecutableOnly.value && row.executableHint !== true) return false
    if (
      buyCheapOnly.value &&
      row.valuationLevel !== 'UNDERVALUED' &&
      row.valuationLevel !== 'SLIGHTLY_CHEAP'
    ) {
      return false
    }
    if (min != null && !Number.isNaN(min) && Number(row.score || 0) < min) return false
    return true
  })
})
const briefing = computed(() => data.value?.marketBriefing || null)
const factors = computed(() => briefing.value?.factors || [])
const tips = computed(() => briefing.value?.tips || [])
const indexLines = computed(() => briefing.value?.indexLines || [])
const hotThemes = computed(() => briefing.value?.hotThemes || [])
const strategies = computed(() => playbook.value?.strategies || [])
const scorePct = computed(() => {
  const s = Number(briefing.value?.stanceScore)
  if (Number.isNaN(s)) return 0
  return Math.max(0, Math.min(100, s))
})

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

function dataLevelLabel(level) {
  if (level === 'GREEN') return '正常'
  if (level === 'YELLOW') return '预警'
  if (level === 'RED') return '异常'
  return level || '-'
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
    const obs = res.data?.observeUpserted
    ElMessage.success(
      res.data?.message ||
        (obs != null ? `决策已生成，并写入观察池 ${obs} 条` : '决策已生成'),
    )
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

function parseIndexLine(line) {
  const raw = String(line || '')
  const m = raw.match(/^(.+?)\s+([+-]?\d+(?:\.\d+)?)%\s*[·•]\s*(.+)$/)
  if (!m) return { name: raw, pct: null, close: '', dir: '' }
  const pct = Number(m[2])
  return {
    name: m[1].trim(),
    pct,
    close: m[3].trim(),
    dir: pct > 0 ? 'up' : pct < 0 ? 'down' : '',
  }
}

const indexCards = computed(() => {
  const rows = briefing.value?.indexes
  if (rows?.length) {
    return rows.map((x) => {
      const pct = x?.pctChg != null ? Number(x.pctChg) : null
      return {
        name: x?.name || '',
        pct,
        close: x?.close != null ? Number(x.close).toFixed(2) : '-',
        dir: x?.direction || (pct > 0 ? 'up' : pct < 0 ? 'down' : ''),
      }
    }).filter((x) => x.name)
  }
  return (indexLines.value || []).map(parseIndexLine).filter((x) => x.name)
})

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
  <div class="page decision" v-loading="loading">
    <header class="header dec-header">
      <div>
        <p class="eyebrow">Apex · Decision</p>
        <h1>智能决策</h1>
        <p class="sub">
          {{ data?.message || '先看市场立场，再按评分出买卖单' }}
        </p>
      </div>
      <div class="actions">
        <el-input v-model="groupName" class="group-input" placeholder="自选分组" clearable />
        <el-button type="primary" class="cta" :loading="loading" @click="onRun">一键生成决策</el-button>
        <el-button type="warning" plain @click="router.push('/observe')">观察池</el-button>
        <el-link
          type="primary"
          :href="`http://127.0.0.1:8080/apex/api/export/decision?groupName=${encodeURIComponent(groupName || '')}`"
          target="_blank"
        >导出CSV</el-link>
        <el-button text @click="load">刷新</el-button>
      </div>
    </header>

    <!-- ① 市场立场 -->
    <section
      v-if="briefing"
      class="stance-panel"
      :class="stanceClass(briefing.stance)"
    >
      <div class="stance-main">
        <div class="kicker">
          <span>市场简报 · {{ briefing.asOf || '-' }}</span>
          <el-tag
            v-if="briefing.dataLevel"
            size="small"
            effect="plain"
            :type="dataLevelType(briefing.dataLevel)"
            round
          >
            数据{{ dataLevelLabel(briefing.dataLevel) }}
          </el-tag>
        </div>
        <div class="stance-title-row">
          <div class="score-ring" :style="{ '--pct': scorePct }">
            <div class="score-ring-inner">
              <strong>{{ briefing.stanceScore ?? '-' }}</strong>
              <small>/100</small>
            </div>
          </div>
          <div class="stance-copy">
            <h2><span class="pill">{{ briefing.stance || '均衡' }}</span></h2>
            <p class="reason">{{ briefing.stanceReason }}</p>
            <p class="advice">{{ briefing.positionAdvice || data?.riskNote }}</p>
          </div>
        </div>
      </div>
      <div class="stance-side">
        <div class="side-block">
          <div class="side-title">大盘</div>
          <div class="index-list">
            <div v-for="idx in indexCards.slice(0, 4)" :key="idx.name" class="index-line">
              <span class="n">{{ idx.name }}</span>
              <span class="c" :class="idx.dir">{{ idx.close || '-' }}</span>
              <span class="p" :class="idx.dir">
                {{ idx.pct == null ? '-' : (idx.pct > 0 ? '+' : '') + Number(idx.pct).toFixed(2) + '%' }}
              </span>
            </div>
          </div>
        </div>
        <div v-if="hotThemes.length" class="side-block">
          <div class="side-title">主线题材</div>
          <div class="theme-row">
            <span v-for="t in hotThemes.slice(0, 6)" :key="t" class="theme-chip">{{ t }}</span>
          </div>
        </div>
      </div>
    </section>

    <div v-if="factors.length" class="factor-strip">
      <div v-for="f in factors" :key="f.name" class="factor-cell">
        <label>{{ f.name }}</label>
        <div class="factor-val">
          <b :class="signalClass(f.signal)">{{ f.value }}</b>
          <em :class="signalClass(f.signal)">{{ f.signal }}</em>
        </div>
        <p>{{ f.note }}</p>
      </div>
    </div>

    <div v-if="tips.length" class="tips-row">
      <el-alert
        v-for="(tip, idx) in tips.slice(0, 3)"
        :key="idx"
        class="tip-item"
        :type="tipType(tip.level)"
        :closable="false"
        show-icon
        :title="tip.text"
      />
    </div>

    <!-- ② 今日清单 -->
    <section class="action-panel">
      <div class="action-head">
        <div>
          <h2>今日清单</h2>
          <p class="muted">
            {{ data?.actionDate || '-' }}
            <template v-if="data?.groupName"> · {{ data.groupName }}</template>
            <template v-if="data?.universeCount != null"> · 池 {{ data.universeCount }}</template>
          </p>
        </div>
        <div class="metric-row">
          <div class="metric">
            <label>买入</label>
            <b class="up">{{ data?.buyCount ?? buys.length }}</b>
          </div>
          <div class="metric">
            <label>卖出</label>
            <b class="down">{{ data?.sellCount ?? sells.length }}</b>
          </div>
          <div class="metric">
            <label>持有</label>
            <b>{{ data?.holdCount ?? holds.length }}</b>
          </div>
          <div class="metric">
            <label>可执行</label>
            <b>{{ data?.executableCount ?? 0 }}</b>
          </div>
          <div class="metric">
            <label>低估</label>
            <b class="up">{{ data?.valuationCheapCount ?? 0 }}</b>
          </div>
          <div class="metric">
            <label>高估</label>
            <b class="down">{{ data?.valuationRichCount ?? 0 }}</b>
          </div>
          <div class="metric">
            <label>主线</label>
            <b>{{ data?.mainlineMatchCount ?? 0 }}</b>
          </div>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="tabs">
        <el-tab-pane :label="`建议买入 (${buys.length})`" name="buys">
          <div v-if="buys.length" class="toolbar-bar">
            <el-select
              v-model="buyStrategyFilter"
              clearable
              placeholder="策略"
              style="width: 110px"
              @change="persistBuyFilters"
            >
              <el-option label="S1" value="S1" />
              <el-option label="S2" value="S2" />
              <el-option label="S3" value="S3" />
            </el-select>
            <el-input
              v-model="buyMinScore"
              clearable
              placeholder="最低分"
              style="width: 100px"
              @change="persistBuyFilters"
            />
            <el-checkbox v-model="buyMainlineOnly" @change="persistBuyFilters">仅主线</el-checkbox>
            <el-checkbox v-model="buyExecutableOnly" @change="persistBuyFilters">仅可执行</el-checkbox>
            <el-checkbox v-model="buyCheapOnly" @change="persistBuyFilters">仅低估</el-checkbox>
            <span class="muted">显示 {{ filteredBuys.length }} / {{ buys.length }}</span>
          </div>
          <div v-if="!buys.length" class="page-empty">
            <h3>暂无买入机会</h3>
            <p>先同步日线，再一键生成决策；系统会扫全 A + 热点并写入观察池</p>
            <el-button type="primary" :loading="loading" @click="onRun">一键生成决策</el-button>
          </div>
          <el-table
            v-else
            :data="filteredBuys"
            size="small"
            stripe
            empty-text="当前筛选下无买入标的"
          >
            <el-table-column prop="code" label="代码" width="96" fixed>
              <template #default="{ row }">
                <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="名称" width="100" />
            <el-table-column label="策略" width="108">
              <template #default="{ row }">{{ strategyName(row.strategyId) }}</template>
            </el-table-column>
            <el-table-column label="评分" width="110">
              <template #default="{ row }"><ScoreBar :score="row.score" /></template>
            </el-table-column>
            <el-table-column label="仓位" width="72">
              <template #default="{ row }">{{ fmtPct(row.suggestedWeight) }}</template>
            </el-table-column>
            <el-table-column width="100">
              <template #header><TermTip term="confluence">共振</TermTip></template>
              <template #default="{ row }">
                <span v-if="row.strategies?.length">{{ row.strategies.join('+') }}</span>
                <span v-else>{{ row.confluenceCount || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="主线" width="96">
              <template #default="{ row }">
                <el-tag v-if="row.mainlineMatch" size="small" type="warning" effect="plain">
                  {{ row.mainlineName || '匹配' }}
                </el-tag>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="估值" width="88">
              <template #default="{ row }">
                <el-button
                  v-if="row.valuationLabel"
                  link
                  type="primary"
                  @click="router.push({ path: '/valuation', query: { code: row.code } })"
                >{{ row.valuationLabel }}</el-button>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="联动" width="110">
              <template #default="{ row }">
                <el-tag
                  v-if="row.linkHint"
                  size="small"
                  effect="plain"
                  :type="row.linkHint.includes('降权') ? 'danger' : 'success'"
                >{{ row.linkHint }}</el-tag>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="scoreExplain" label="评分拆解" min-width="200" show-overflow-tooltip />
            <el-table-column label="风险" width="120">
              <template #default="{ row }">
                <template v-if="row.riskFlags?.length">
                  <el-tag
                    v-for="(rf, idx) in row.riskFlags.slice(0, 2)"
                    :key="idx"
                    size="small"
                    type="warning"
                    effect="plain"
                    class="risk-tag"
                  >{{ rf }}</el-tag>
                </template>
                <span v-else class="muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="理由" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link :loading="ordering" @click="onPaperOrder(row)">模拟买</el-button>
                <el-button
                  link
                  type="warning"
                  @click="router.push({ path: '/observe', query: { code: row.code, name: row.name || '' } })"
                >观察</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`建议卖出 (${sells.length})`" name="sells">
          <el-table :data="sells" size="small" stripe empty-text="持仓暂无卖出建议">
            <el-table-column prop="code" label="代码" width="96" fixed>
              <template #default="{ row }">
                <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="名称" width="100" />
            <el-table-column label="策略" width="120">
              <template #default="{ row }">{{ strategyName(row.strategyId) }}</template>
            </el-table-column>
          <el-table-column label="评分" width="110">
            <template #default="{ row }"><ScoreBar :score="row.score" /></template>
          </el-table-column>
          <el-table-column prop="exitRule" label="触发规则" min-width="150" show-overflow-tooltip />
          <el-table-column prop="scoreExplain" label="拆解" min-width="160" show-overflow-tooltip />
            <el-table-column prop="reason" label="理由" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button type="danger" link :loading="ordering" @click="onPaperOrder(row)">模拟卖</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`继续持有 (${holds.length})`" name="holds">
          <el-table :data="holds" size="small" stripe empty-text="持仓为空，或均已有买卖建议">
            <el-table-column prop="code" label="代码" width="96">
              <template #default="{ row }">
                <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="名称" width="110" />
            <el-table-column prop="reason" label="理由" min-width="180" show-overflow-tooltip />
            <el-table-column prop="exitRule" label="止损/止盈" min-width="150" show-overflow-tooltip />
            <el-table-column prop="fundNote" label="基本面" min-width="160" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <!-- ③ 次要信息折叠 -->
    <div v-if="(data?.executableCount || 0) > 0" class="exec-bar">
      <span>
        今日可执行提示 <b>{{ data.executableCount }}</b>
        · 低估 <b class="up">{{ data.valuationCheapCount ?? 0 }}</b>
      </span>
      <div class="exec-actions">
        <el-button size="small" type="warning" plain @click="router.push('/observe')">去观察池处理</el-button>
        <el-button size="small" type="primary" plain @click="buyExecutableOnly = true; persistBuyFilters(); activeTab = 'buys'">
          筛选可执行买入
        </el-button>
      </div>
    </div>

    <el-collapse v-model="morePanels" class="more-collapse">
      <el-collapse-item v-if="playbook" name="playbook">
        <template #title>
          <span class="collapse-title">策略战法与规则</span>
          <span class="collapse-sub">S1 / S2 / S3 · 评分与仓位</span>
        </template>
        <div class="strategy-grid">
          <article v-for="s in strategies" :key="s.strategyId" class="strategy-card">
            <header>
              <b>{{ s.strategyId }} · {{ s.name }}</b>
              <el-tag size="small" effect="plain">{{ s.style }}</el-tag>
            </header>
            <p><label>买入</label>{{ s.buyRule }}</p>
            <p><label>离场</label>{{ s.exitRule }}</p>
            <p class="fit"><label>市况</label>{{ s.marketFit }}</p>
          </article>
        </div>
        <div class="rules-grid">
          <div>
            <h3>流水线</h3>
            <ol>
              <li v-for="(step, i) in playbook.pipelineSteps || []" :key="i">
                {{ step.replace(/^\d+\.\s*/, '') }}
              </li>
            </ol>
          </div>
          <div>
            <h3>评分 / 仓位</h3>
            <ul>
              <li v-for="(r, i) in playbook.scoreRules || []" :key="'s'+i">{{ r }}</li>
              <li v-for="(r, i) in playbook.positionRules || []" :key="'p'+i">{{ r }}</li>
            </ul>
          </div>
          <div>
            <h3>门禁 / 卖出</h3>
            <ul>
              <li v-for="(r, i) in playbook.fundRules || []" :key="'f'+i">{{ r }}</li>
              <li v-for="(r, i) in playbook.sellRules || []" :key="'e'+i">{{ r }}</li>
            </ul>
          </div>
        </div>
      </el-collapse-item>

      <el-collapse-item v-if="attribution" name="attr">
        <template #title>
          <span class="collapse-title">复盘归因</span>
          <span class="collapse-sub">{{ attribution.message }}</span>
        </template>
        <div class="attr-grid">
          <div v-for="block in [
            { title: '按策略', rows: attribution.byStrategy },
            { title: '按共振', rows: attribution.byConfluence },
            { title: '按主线', rows: attribution.byMainline },
            { title: '按市场立场', rows: attribution.byStance },
          ]" :key="block.title">
            <h4>{{ block.title }}</h4>
            <el-table :data="block.rows || []" size="small" stripe empty-text="暂无">
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
        </div>
      </el-collapse-item>

      <el-collapse-item v-if="history.length" name="history">
        <template #title>
          <span class="collapse-title">决策历史</span>
          <span class="collapse-sub">点击行回看当日清单</span>
        </template>
        <el-table
          :data="history"
          size="small"
          stripe
          highlight-current-row
          class="history-table"
          @row-click="openHistoryDay"
        >
          <el-table-column prop="actionDate" label="日期" width="120" />
          <el-table-column prop="stance" label="立场" width="70" />
          <el-table-column prop="buyCount" label="买" width="60" />
          <el-table-column prop="sellCount" label="卖" width="60" />
          <el-table-column prop="holdCount" label="持有" width="60" />
          <el-table-column prop="executableCount" label="可执行" width="70" />
          <el-table-column prop="valuationCheapCount" label="低估" width="60" />
          <el-table-column prop="mainlineMatchCount" label="主线" width="60" />
          <el-table-column label="买入次日均%" width="120">
            <template #default="{ row }">
              <span :class="Number(row.nextDayAvgPct) > 0 ? 'up' : Number(row.nextDayAvgPct) < 0 ? 'down' : ''">
                {{ row.nextDayAvgPct == null ? '-' : Number(row.nextDayAvgPct).toFixed(2) + '%' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="note" label="说明" min-width="200" show-overflow-tooltip />
        </el-table>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.decision {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.decision .header {
  margin-bottom: 0;
}

.dec-header .eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.04em;
  color: var(--accent);
  text-transform: uppercase;
}

.dec-header .sub {
  margin: 6px 0 0;
  max-width: 52ch;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.5;
}

.dec-header .actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.group-input {
  width: 130px;
}

.cta {
  min-width: 128px;
}

/* —— 市场立场 —— */
.stance-panel {
  position: relative;
  display: grid;
  grid-template-columns: 1.45fr 1fr;
  gap: 18px;
  padding: 18px 20px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass-strong);
  box-shadow: var(--shadow-soft);
  overflow: hidden;
}

.stance-panel.stance-attack {
  border-color: rgba(255, 59, 48, 0.22);
  background:
    linear-gradient(135deg, rgba(255, 59, 48, 0.06), transparent 42%),
    var(--glass-strong);
}

.stance-panel.stance-defend {
  border-color: rgba(0, 113, 227, 0.22);
  background:
    linear-gradient(135deg, rgba(0, 113, 227, 0.07), transparent 42%),
    var(--glass-strong);
}

.kicker {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--muted);
}

.stance-title-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.score-ring {
  --pct: 0;
  width: 76px;
  height: 76px;
  border-radius: 50%;
  flex-shrink: 0;
  background: conic-gradient(
    var(--accent) calc(var(--pct) * 1%),
    rgba(0, 0, 0, 0.06) 0
  );
  display: grid;
  place-items: center;
}

.stance-attack .score-ring {
  background: conic-gradient(
    #ff3b30 calc(var(--pct) * 1%),
    rgba(0, 0, 0, 0.06) 0
  );
}

.score-ring-inner {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  line-height: 1.05;
}

.score-ring-inner strong {
  font-size: 20px;
  font-family: var(--font-display);
}

.score-ring-inner small {
  font-size: 10px;
  color: var(--muted);
}

.stance-copy h2 {
  margin: 0 0 8px;
}

.pill {
  display: inline-flex;
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 18px;
  font-weight: 750;
  background: rgba(0, 0, 0, 0.05);
}

.stance-attack .pill {
  color: #c45656;
  background: rgba(255, 59, 48, 0.12);
}

.stance-defend .pill {
  color: #0058b0;
  background: rgba(0, 113, 227, 0.12);
}

.reason,
.advice {
  margin: 0 0 4px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--muted);
}

.advice {
  color: var(--ink-soft);
  font-weight: 600;
}

.stance-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-left: 1px solid var(--line);
  padding-left: 18px;
}

.side-title {
  font-size: 11px;
  font-weight: 650;
  color: var(--muted);
  letter-spacing: 0.04em;
  margin-bottom: 6px;
}

.index-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.index-line {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 10px;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.index-line .n {
  color: var(--ink-soft);
}

.index-line .c,
.index-line .p {
  font-weight: 650;
  min-width: 4.5em;
  text-align: right;
}

.theme-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.theme-chip {
  font-size: 12px;
  padding: 3px 9px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.04);
  color: var(--ink-soft);
}

/* —— 因子条 —— */
.factor-strip {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
}

.factor-cell {
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--glass);
}

.factor-cell label {
  display: block;
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 4px;
}

.factor-val {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 2px;
}

.factor-val b {
  font-size: 13px;
  font-weight: 700;
}

.factor-val em {
  font-style: normal;
  font-size: 11px;
  font-weight: 700;
}

.factor-cell p {
  margin: 0;
  font-size: 11px;
  color: var(--muted);
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.tips-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tip-item {
  margin: 0;
}

/* —— 清单主面板 —— */
.action-panel {
  padding: 16px 18px 12px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass-strong);
  box-shadow: var(--shadow-soft);
}

.action-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.action-head h2 {
  margin: 0 0 4px;
  font-size: 17px;
  font-family: var(--font-display);
}

.metric-row {
  display: flex;
  gap: 18px;
}

.metric label {
  display: block;
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 2px;
}

.metric b {
  font-size: 22px;
  font-family: var(--font-display);
  font-variant-numeric: tabular-nums;
}

.tabs {
  margin-top: 4px;
}

.tabs :deep(.el-tabs__header) {
  margin-bottom: 10px;
}

.risk-tag {
  margin-right: 4px;
}

.exec-bar {
  position: sticky;
  bottom: 12px;
  z-index: 20;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: 14px 0 0;
  padding: 10px 14px;
  border: 1px solid rgba(0, 113, 227, 0.22);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(16px) saturate(160%);
  box-shadow: var(--shadow-soft);
  font-size: 13px;
  color: var(--ink-soft);
}

.exec-bar b {
  font-variant-numeric: tabular-nums;
}

.exec-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* —— 折叠次要区 —— */
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
  font-size: 14px;
}

.more-collapse :deep(.el-collapse-item__wrap) {
  border-bottom-color: var(--line);
  background: transparent;
}

.more-collapse :deep(.el-collapse-item__content) {
  padding: 14px 16px 18px;
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

.strategy-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.strategy-card {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.5);
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
}

.strategy-card label {
  color: var(--muted);
  margin-right: 6px;
}

.strategy-card .fit {
  color: var(--muted);
}

.rules-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.rules-grid h3,
.attr-grid h4 {
  margin: 0 0 8px;
  font-size: 13px;
}

.rules-grid ol,
.rules-grid ul {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--muted);
}

.attr-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.history-table :deep(.el-table__row) {
  cursor: pointer;
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

@media (max-width: 1100px) {
  .factor-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .stance-panel {
    grid-template-columns: 1fr;
  }

  .stance-side {
    border-left: none;
    padding-left: 0;
    border-top: 1px solid var(--line);
    padding-top: 12px;
  }

  .factor-strip,
  .strategy-grid,
  .rules-grid,
  .attr-grid {
    grid-template-columns: 1fr;
  }

  .action-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
