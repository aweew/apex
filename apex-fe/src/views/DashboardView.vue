<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { dashboardHome } from '../api/dashboard'
import { runDecision } from '../api/decision'

const router = useRouter()
const loading = ref(false)
const running = ref(false)
const home = ref(null)
const loadError = ref('')
const chartRef = ref(null)
let chart

const market = computed(() => home.value?.market || null)
const decision = computed(() => home.value?.decision || null)
const account = computed(() => home.value?.account || null)
const dataHealth = computed(() => home.value?.dataHealth || null)
const themes = computed(() => market.value?.hotThemes || [])
const tips = computed(() => market.value?.tips || [])
const topBuys = computed(() => decision.value?.topBuys || [])
const topSells = computed(() => decision.value?.topSells || [])
const hasEquity = computed(() => (home.value?.equityCurve || []).length > 0)
const indexCards = computed(() => {
  const rows = market.value?.indexes
  if (rows?.length) return rows
  // 兼容旧接口：从文案行解析
  return (market.value?.indexLines || []).map((line) => parseIndexLine(line)).filter(Boolean)
})

function parseIndexLine(line) {
  const text = String(line || '').trim()
  if (!text) return null
  const m = text.match(/^(.+?)\s+([+-]?\d+(?:\.\d+)?)%\s*·\s*([\d.]+)$/)
  if (!m) {
    return { name: text, pctChg: null, close: null, direction: 'flat' }
  }
  const pct = Number(m[2])
  return {
    name: m[1],
    pctChg: pct,
    close: Number(m[3]),
    direction: pct > 0 ? 'up' : pct < 0 ? 'down' : 'flat',
  }
}

function fmtIndexPct(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return '-'
  const sign = n > 0 ? '+' : ''
  return `${sign}${n.toFixed(2)}%`
}

function volumeTone(trend) {
  if (trend === '放量') return 'vol-up'
  if (trend === '缩量') return 'vol-down'
  if (trend === '平量') return 'vol-flat'
  return ''
}

const breadth = computed(() => {
  const up = Number(market.value?.breadthUp)
  const down = Number(market.value?.breadthDown)
  if (Number.isNaN(up) || Number.isNaN(down) || (up <= 0 && down <= 0)) {
    return null
  }
  const total = up + down
  const upPct = total > 0 ? (up / total) * 100 : 50
  return {
    up,
    down,
    upPct,
    downPct: 100 - upPct,
    ratio: down > 0 ? (up / down).toFixed(2) : up > 0 ? '∞' : '-',
  }
})
const scorePct = computed(() => {
  const s = Number(market.value?.stanceScore)
  if (Number.isNaN(s)) return 0
  return Math.max(0, Math.min(100, s))
})

function stanceClass(s) {
  if (s === '进攻') return 'stance-attack'
  if (s === '防守') return 'stance-defend'
  return 'stance-balance'
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

function fmtPct(v, digits = 2) {
  if (v == null || v === '') return '-'
  return (Number(v) * 100).toFixed(digits) + '%'
}

function fmtScore(v) {
  if (v == null || v === '') return '-'
  return Number(v).toFixed(0)
}

function fmtWeight(v) {
  if (v == null || v === '') return '-'
  return (Number(v) * 100).toFixed(1) + '%'
}

function fmtAsset(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

function renderEquity(points) {
  if (!chartRef.value) return
  const rows = points || []
  if (!rows.length) {
    if (chart) {
      chart.clear()
    }
    return
  }
  if (!chart) chart = echarts.init(chartRef.value)
  const vals = rows.map((p) => Number(p.equity))
  chart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 48, right: 12, top: 20, bottom: 28 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.92)',
      borderColor: 'rgba(0,0,0,0.06)',
      textStyle: { color: '#1d1d1f', fontSize: 12 },
    },
    xAxis: {
      type: 'category',
      data: rows.map((p) => p.tradeDate),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { fontSize: 10, color: '#86868b' },
    },
    yAxis: {
      type: 'value',
      scale: true,
      splitNumber: 3,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: 'rgba(0,0,0,0.05)' } },
      axisLabel: { fontSize: 10, color: '#86868b' },
    },
    series: [
      {
        type: 'line',
        showSymbol: false,
        smooth: 0.25,
        data: vals,
        lineStyle: { color: '#0071e3', width: 2.2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(0,113,227,0.22)' },
            { offset: 1, color: 'rgba(0,113,227,0.02)' },
          ]),
        },
      },
    ],
  })
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await dashboardHome()
    home.value = res.data
    await nextTick()
    renderEquity(home.value?.equityCurve)
  } catch (e) {
    home.value = null
    const msg = e.message || '加载失败'
    loadError.value = msg.includes('404') || msg.includes('Not Found')
      ? '决策看板接口未就绪：请重启后端后再刷新（/api/dashboard/home）'
      : msg
    ElMessage.error(loadError.value)
  } finally {
    loading.value = false
  }
}

async function onRunDecision() {
  running.value = true
  try {
    await runDecision({ groupName: '我的自选' })
    ElMessage.success('决策已生成')
    router.push('/decision')
  } catch (e) {
    ElMessage.error(e.message || '生成失败')
  } finally {
    running.value = false
  }
}

function onResize() {
  chart?.resize()
}

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="page dash" v-loading="loading">
    <header class="header dash-header">
      <div>
        <p class="eyebrow">Apex · Command</p>
        <h1>决策看板</h1>
        <p class="sub">{{ home?.message || '先看市场立场，再处理买卖行动' }}</p>
      </div>
      <div class="actions">
        <el-button type="primary" class="cta" :loading="running" @click="onRunDecision">
          一键生成决策
        </el-button>
        <el-button @click="router.push('/decision')">智能决策</el-button>
        <el-button plain @click="router.push('/sync')">同步健康</el-button>
        <el-button text @click="load">刷新</el-button>
      </div>
    </header>

    <el-alert
      v-if="loadError"
      class="load-alert"
      type="error"
      show-icon
      :closable="false"
      :title="loadError"
    >
      <template #default>
        看板数据依赖 <code>/api/dashboard/home</code>。重启后端后点「刷新」；指数/板块未同步时简报也会偏空。
      </template>
    </el-alert>

    <!-- ① 市场立场（始终占位，避免整块消失） -->
    <section
      class="stance-panel enter"
      :class="market ? stanceClass(market.stance) : 'stance-empty'"
    >
      <div class="stance-glow" aria-hidden="true" />
      <div class="stance-main">
        <div class="kicker">
          <span>市场立场 · {{ market?.asOf || '待加载' }}</span>
          <el-tag
            v-if="market"
            size="small"
            effect="plain"
            :type="dataLevelType(market.dataLevel)"
            round
          >
            数据{{ dataLevelLabel(market.dataLevel) }}
          </el-tag>
          <el-tag v-else size="small" type="info" effect="plain" round>未加载</el-tag>
        </div>
        <div class="stance-title-row">
          <div
            class="score-ring"
            :style="{ '--pct': market ? scorePct : 0 }"
            :aria-label="`立场分 ${market?.stanceScore ?? '-'}`"
          >
            <div class="score-ring-inner">
              <strong>{{ market?.stanceScore ?? '-' }}</strong>
              <small>/100</small>
            </div>
          </div>
          <div class="stance-copy">
            <h2>
              <span class="pill">{{ market?.stance || '暂无' }}</span>
            </h2>
            <p class="reason">
              {{
                market?.stanceReason
                  || (loadError
                    ? '首页接口未返回市场简报，请先重启后端并刷新'
                    : '正在等待市场简报；若长期空白，请先同步指数/板块/涨停')
              }}
            </p>
            <p class="advice">
              {{ market?.positionAdvice || '同步行情后，这里会给出进攻 / 均衡 / 防守与仓位建议' }}
            </p>
          </div>
        </div>
      </div>
      <div class="stance-side">
        <div class="index-board-head">
          <span>大盘快照</span>
          <el-button link type="primary" @click="router.push('/market')">大盘页</el-button>
        </div>
        <div v-if="indexCards.length" class="index-board">
          <div
            v-for="idx in indexCards.slice(0, 4)"
            :key="idx.name"
            class="index-card"
            :class="idx.direction || 'flat'"
          >
            <div class="idx-name">{{ idx.name }}</div>
            <div class="idx-pct" :class="idx.direction">{{ fmtIndexPct(idx.pctChg) }}</div>
            <div class="idx-close">{{ idx.close != null ? Number(idx.close).toFixed(2) : '-' }}</div>
          </div>
        </div>
        <div v-else class="index-board placeholder">
          <div v-for="name in ['上证', '深成指', '创业板', '科创50']" :key="name" class="index-card ghost">
            <div class="idx-name">{{ name }}</div>
            <div class="idx-pct">-</div>
            <div class="idx-close">待同步</div>
          </div>
        </div>
        <div class="meta-row">
          <span v-if="market?.limitUpCount != null" class="limit-badge">
            涨停 <b>{{ market.limitUpCount }}</b> 家
          </span>
          <span
            v-if="market?.volumeTrend"
            class="vol-badge"
            :class="volumeTone(market.volumeTrend)"
            :title="market.volumeVsMa5Pct != null ? `较5日均量 ${fmtIndexPct(market.volumeVsMa5Pct)}` : ''"
          >
            {{ market.volumeTrend }}
            <b v-if="market.volumeVsMa5Pct != null">{{ fmtIndexPct(market.volumeVsMa5Pct) }}</b>
          </span>
          <div v-if="breadth" class="breadth-box" :title="`涨跌比 ${breadth.ratio}`">
            <div class="breadth-nums">
              <span class="up">涨 {{ breadth.up }}</span>
              <span class="sep">/</span>
              <span class="down">跌 {{ breadth.down }}</span>
            </div>
            <div class="breadth-bar" aria-hidden="true">
              <i class="up-seg" :style="{ width: breadth.upPct + '%' }" />
              <i class="down-seg" :style="{ width: breadth.downPct + '%' }" />
            </div>
          </div>
          <el-button link type="primary" @click="router.push('/sync')">去同步</el-button>
          <el-button link type="primary" @click="router.push('/decision')">完整简报</el-button>
        </div>
      </div>
    </section>

    <div class="two-col">
      <!-- ② 今日决策 -->
      <section class="panel enter delay-1">
        <div class="panel-head">
          <div>
            <h3>今日决策</h3>
            <p class="panel-desc">买入机会 Top3</p>
          </div>
          <el-button link type="primary" @click="router.push('/decision')">全部</el-button>
        </div>

        <div v-if="decision?.hasToday" class="count-row">
          <span class="count-chip buy">买 <b>{{ decision.buyCount }}</b></span>
          <span class="count-chip sell">卖 <b>{{ decision.sellCount }}</b></span>
          <span class="count-chip hold">持有 <b>{{ decision.holdCount }}</b></span>
          <span class="count-date">{{ decision.actionDate }}</span>
        </div>
        <p v-else class="panel-desc soft">今日尚无决策清单</p>

        <el-empty
          v-if="!decision?.hasToday"
          class="dash-empty"
          description="生成后将在此显示买入 Top3"
          :image-size="56"
        >
          <el-button type="primary" size="small" :loading="running" @click="onRunDecision">
            一键生成决策
          </el-button>
        </el-empty>
        <el-table
          v-else
          :data="topBuys"
          size="small"
          class="dash-table"
          empty-text="暂无买入建议"
          stripe
        >
          <el-table-column prop="code" label="代码" width="88">
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">
                {{ row.code }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" width="90" />
          <el-table-column prop="strategyId" label="策略" width="56" />
          <el-table-column label="评分" width="56">
            <template #default="{ row }">
              <span class="num">{{ fmtScore(row.score) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="仓位" width="64">
            <template #default="{ row }">
              <span class="num">{{ fmtWeight(row.suggestedWeight) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="主线" min-width="88">
            <template #default="{ row }">
              <el-tag v-if="row.mainlineMatch" size="small" type="warning" effect="light" round>
                {{ row.mainlineName || '匹配' }}
              </el-tag>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <!-- ③ 持仓行动 -->
      <section class="panel enter delay-2">
        <div class="panel-head">
          <div>
            <h3>持仓行动</h3>
            <p class="panel-desc">止损止盈与策略卖出优先</p>
          </div>
          <el-button link type="primary" @click="router.push('/holding')">我的持仓</el-button>
        </div>

        <el-empty
          v-if="!topSells.length"
          class="dash-empty"
          description="持仓暂无卖点"
          :image-size="56"
        />
        <el-table v-else :data="topSells" size="small" class="dash-table" stripe>
          <el-table-column prop="code" label="代码" width="88">
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">
                {{ row.code }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" width="90" />
          <el-table-column label="策略" width="72">
            <template #default="{ row }">
              <span :class="row.strategyId === 'RISK' ? 'risk-tag' : ''">
                {{ row.strategyId === 'RISK' ? '风控' : row.strategyId || '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="exitRule" label="触发" min-width="140" show-overflow-tooltip />
        </el-table>
      </section>
    </div>

    <!-- ④ 主线与情绪 -->
    <section class="panel enter delay-3">
      <div class="panel-head">
        <div>
          <h3>主线与情绪</h3>
          <p class="panel-desc">今日关注题材与操作提示</p>
        </div>
        <div class="panel-links">
          <el-button link type="primary" @click="router.push('/sector')">板块</el-button>
          <el-button link type="primary" @click="router.push('/limit-up')">涨停复盘</el-button>
        </div>
      </div>
      <div v-if="themes.length" class="theme-row">
        <span v-for="(t, i) in themes" :key="t" class="theme-chip" :style="{ '--i': i }">
          {{ t }}
        </span>
      </div>
      <div v-else class="empty-guide">
        <p>暂无主线题材</p>
        <span>先在同步中心刷新「板块行情」，看板才会显示今日主线芯片与操作提示。</span>
        <el-button size="small" round @click="router.push('/sync')">去同步板块</el-button>
      </div>
      <div v-if="tips.length" class="tip-list">
        <div v-for="(t, i) in tips" :key="i" class="tip-item">
          <span class="tip-dot" />
          <span>{{ t }}</span>
        </div>
      </div>
    </section>

    <!-- ⑤ 账户快览 -->
    <section class="panel enter delay-4">
      <div class="panel-head">
        <div>
          <h3>账户快览</h3>
          <p class="panel-desc">核心指标 · 细节见模拟盘</p>
        </div>
        <el-button link type="primary" @click="router.push('/paper')">更多绩效 →</el-button>
      </div>
      <div class="kpi-row">
        <div class="kpi">
          <label>总资产</label>
          <b class="num">{{ account ? fmtAsset(account.totalAsset) : '-' }}</b>
        </div>
        <div class="kpi">
          <label>累计收益</label>
          <b
            class="num"
            :class="account && Number(account.totalReturn) >= 0 ? 'up' : account ? 'down' : ''"
          >
            {{ account ? fmtPct(account.totalReturn) : '-' }}
          </b>
        </div>
        <div class="kpi">
          <label>仓位</label>
          <b class="num">{{ account ? fmtPct(account.positionRatio, 1) : '-' }}</b>
        </div>
        <div class="kpi">
          <label>最大回撤</label>
          <b class="num">{{ account ? fmtPct(account.maxDrawdown) : '-' }}</b>
        </div>
        <div class="kpi">
          <label>胜率</label>
          <b class="num">{{ account ? fmtPct(account.winRate, 0) : '-' }}</b>
        </div>
        <div class="kpi" :class="{ warn: account && (account.criticalCount || 0) > 0 }">
          <label>告警</label>
          <b class="num">
            {{ account ? `C${account.criticalCount || 0} / W${account.warnCount || 0}` : '-' }}
          </b>
        </div>
      </div>
      <div class="chart-shell" :class="{ empty: !hasEquity }">
        <div class="chart-label">纸面权益</div>
        <div ref="chartRef" class="mini-chart" :class="{ hidden: !hasEquity }" />
        <div v-if="!hasEquity" class="chart-empty">
          <p>暂无权益曲线</p>
          <span>在模拟盘产生成交后，这里会画出纸面净值走势。</span>
          <el-button size="small" round @click="router.push('/paper')">去模拟盘</el-button>
        </div>
      </div>
    </section>

    <!-- ⑥ 数据可信度 -->
    <section
      class="panel health enter delay-5"
      :class="'lvl-' + (dataHealth?.level || 'yellow').toLowerCase()"
    >
      <div class="health-bar" aria-hidden="true" />
      <div class="health-body">
        <div class="panel-head">
          <div>
            <h3>数据可信度</h3>
            <p class="panel-desc">
              {{
                dataHealth?.suggestion
                  || '尚未拿到数据健康状态；重启后端并刷新后会显示自选/K线覆盖情况'
              }}
            </p>
          </div>
          <el-tag
            size="small"
            effect="dark"
            :type="dataLevelType(dataHealth?.level || 'YELLOW')"
            round
          >
            {{ dataLevelLabel(dataHealth?.level || 'YELLOW') }}
          </el-tag>
        </div>
        <div class="health-stats">
          <span>自选 <b>{{ dataHealth?.watchlistCount ?? '-' }}</b></span>
          <span>过期 <b>{{ dataHealth?.barsStaleCount ?? '-' }}</b></span>
          <span>空K <b>{{ dataHealth?.barsEmptyCount ?? '-' }}</b></span>
        </div>
        <el-button size="small" round @click="router.push('/sync')">去同步中心</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.dash {
  max-width: 1280px;
  margin: 0 auto;
}

.load-alert {
  margin-bottom: 14px;
}

.load-alert code {
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.05);
}

.dash-header .eyebrow {
  margin: 0 0 4px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--accent);
}

.dash-header .sub {
  margin-top: 6px;
}

.cta {
  min-width: 132px;
  font-weight: 600;
}

/* —— enter motion —— */
.enter {
  animation: dashIn 0.45s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.delay-1 { animation-delay: 0.04s; }
.delay-2 { animation-delay: 0.08s; }
.delay-3 { animation-delay: 0.12s; }
.delay-4 { animation-delay: 0.16s; }
.delay-5 { animation-delay: 0.2s; }

@keyframes dashIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}

/* —— stance hero —— */
.stance-panel {
  position: relative;
  overflow: hidden;
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 20px;
  margin-bottom: 14px;
  padding: 20px 22px;
  border-radius: calc(var(--radius) + 4px);
  border: 1px solid var(--glass-border);
  background: var(--glass-strong);
  backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  -webkit-backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  box-shadow: var(--shadow);
}

.stance-glow {
  position: absolute;
  inset: -40% auto auto -10%;
  width: 55%;
  height: 140%;
  pointer-events: none;
  opacity: 0.55;
  filter: blur(40px);
  background: radial-gradient(circle at 30% 40%, rgba(0, 113, 227, 0.18), transparent 65%);
}

.stance-attack .stance-glow {
  background: radial-gradient(circle at 30% 40%, rgba(255, 59, 48, 0.16), transparent 65%);
}

.stance-defend .stance-glow {
  background: radial-gradient(circle at 30% 40%, rgba(0, 113, 227, 0.2), transparent 65%);
}

.stance-attack {
  border-color: rgba(255, 59, 48, 0.22);
}

.stance-defend {
  border-color: rgba(0, 113, 227, 0.28);
}

.stance-balance {
  border-color: rgba(0, 113, 227, 0.16);
}

.stance-empty {
  border-style: dashed;
  border-color: rgba(0, 0, 0, 0.1);
}

.stance-empty .pill {
  background: rgba(0, 0, 0, 0.05);
  color: var(--slate);
}


.stance-main,
.stance-side {
  position: relative;
  z-index: 1;
}

.kicker {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--slate);
  margin-bottom: 14px;
}

.stance-title-row {
  display: flex;
  align-items: flex-start;
  gap: 18px;
}

.score-ring {
  --pct: 50;
  flex: 0 0 88px;
  width: 88px;
  height: 88px;
  border-radius: 50%;
  background: conic-gradient(
    var(--accent) calc(var(--pct) * 1%),
    rgba(0, 0, 0, 0.06) 0
  );
  display: grid;
  place-items: center;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.5);
}

.stance-attack .score-ring {
  background: conic-gradient(
    #ff3b30 calc(var(--pct) * 1%),
    rgba(0, 0, 0, 0.06) 0
  );
}

.stance-defend .score-ring {
  background: conic-gradient(
    #0071e3 calc(var(--pct) * 1%),
    rgba(0, 0, 0, 0.06) 0
  );
}

.score-ring-inner {
  width: 70px;
  height: 70px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.92);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  line-height: 1.05;
}

.score-ring-inner strong {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}

.score-ring-inner small {
  font-size: 10px;
  color: var(--muted);
}

.stance-copy h2 {
  margin: 0 0 10px;
}

.pill {
  display: inline-block;
  padding: 4px 14px;
  border-radius: 999px;
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.02em;
  background: rgba(0, 113, 227, 0.1);
  color: var(--accent);
}

.stance-attack .pill {
  background: rgba(255, 59, 48, 0.1);
  color: var(--up);
}

.stance-defend .pill {
  background: rgba(0, 113, 227, 0.12);
  color: var(--accent);
}

.reason {
  margin: 0 0 6px;
  font-size: 14px;
  line-height: 1.5;
  color: var(--ink-soft);
  max-width: 36em;
}

.advice {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--slate);
}

.index-board-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--muted);
}

.index-board {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.index-card {
  position: relative;
  padding: 10px 12px 9px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid var(--line);
  overflow: hidden;
  min-height: 72px;
}

.index-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--muted);
}

.index-card.up::before {
  background: var(--up);
}

.index-card.down::before {
  background: var(--down);
}

.index-card.up {
  background: linear-gradient(135deg, rgba(255, 59, 48, 0.08), rgba(255, 255, 255, 0.75));
  border-color: rgba(255, 59, 48, 0.14);
}

.index-card.down {
  background: linear-gradient(135deg, rgba(52, 199, 89, 0.1), rgba(255, 255, 255, 0.75));
  border-color: rgba(52, 199, 89, 0.16);
}

.index-card.ghost {
  border-style: dashed;
  background: rgba(0, 0, 0, 0.02);
}

.index-card.ghost::before {
  display: none;
}

.idx-name {
  font-size: 11px;
  font-weight: 500;
  color: var(--slate);
  margin-bottom: 4px;
}

.idx-pct {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
  color: var(--ink-soft);
}

.idx-pct.up {
  color: var(--up);
}

.idx-pct.down {
  color: var(--down);
}

.idx-close {
  margin-top: 4px;
  font-size: 11px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}

.meta-row {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
}

.limit-badge,
.vol-badge {
  padding: 4px 10px;
  border-radius: 999px;
  color: var(--ink-soft);
  font-size: 12px;
}

.limit-badge {
  background: rgba(255, 59, 48, 0.08);
}

.limit-badge b {
  color: var(--up);
  font-variant-numeric: tabular-nums;
}

.vol-badge {
  background: rgba(0, 0, 0, 0.04);
}

.vol-badge b {
  margin-left: 4px;
  font-variant-numeric: tabular-nums;
  font-weight: 650;
}

.vol-badge.vol-up {
  background: rgba(255, 59, 48, 0.1);
  color: var(--up);
}

.vol-badge.vol-down {
  background: rgba(52, 199, 89, 0.12);
  color: #248a3d;
}

.vol-badge.vol-flat {
  background: rgba(0, 113, 227, 0.08);
  color: var(--accent);
}

.breadth-box {
  min-width: 132px;
  padding: 4px 10px 5px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid var(--line);
}

.breadth-nums {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  margin-bottom: 4px;
}

.breadth-nums .sep {
  color: var(--muted);
}

.breadth-bar {
  display: flex;
  height: 4px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(0, 0, 0, 0.06);
}

.breadth-bar i {
  display: block;
  height: 100%;
}

.breadth-bar .up-seg {
  background: var(--up);
}

.breadth-bar .down-seg {
  background: var(--down);
}

/* —— panels —— */
.panel {
  position: relative;
  margin-bottom: 14px;
  padding: 16px 18px 18px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass);
  backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  -webkit-backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  box-shadow: var(--shadow-soft);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.panel:hover {
  box-shadow: var(--shadow);
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-head h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--ink);
}

.panel-desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--muted);
}

.panel-desc.soft {
  margin-bottom: 8px;
}

.panel-links {
  display: flex;
  gap: 4px;
}

.count-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.count-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  background: rgba(0, 0, 0, 0.04);
  color: var(--slate);
}

.count-chip b {
  font-variant-numeric: tabular-nums;
  font-size: 14px;
}

.count-chip.buy b { color: var(--up); }
.count-chip.sell b { color: var(--down); }
.count-chip.hold b { color: var(--ink-soft); }

.count-date {
  margin-left: auto;
  font-size: 11px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}

.dash-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(255, 255, 255, 0.45);
  --el-table-row-hover-bg-color: rgba(0, 113, 227, 0.05);
  --el-table-border-color: rgba(0, 0, 0, 0.05);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.dash-empty {
  padding: 12px 0 4px;
}

.num {
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

.risk-tag {
  color: var(--warn);
  font-weight: 600;
}

.theme-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.theme-chip {
  font-size: 12px;
  font-weight: 500;
  padding: 6px 12px;
  border-radius: 10px;
  color: var(--ink-soft);
  background: rgba(0, 113, 227, 0.08);
  border: 1px solid rgba(0, 113, 227, 0.12);
  animation: chipIn 0.35s ease both;
  animation-delay: calc(var(--i, 0) * 0.04s);
}

@keyframes chipIn {
  from {
    opacity: 0;
    transform: scale(0.94);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.tip-list {
  display: grid;
  gap: 8px;
}

.tip-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.4);
  border: 1px solid var(--line);
  font-size: 12px;
  line-height: 1.45;
  color: var(--ink-soft);
}

.tip-dot {
  flex: 0 0 6px;
  width: 6px;
  height: 6px;
  margin-top: 5px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.soft-msg {
  margin: 0 0 8px;
}

.empty-guide {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 14px 16px;
  border-radius: var(--radius-sm);
  border: 1px dashed var(--line-strong);
  background: rgba(255, 255, 255, 0.35);
}

.empty-guide p {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-soft);
}

.empty-guide span {
  font-size: 12px;
  color: var(--muted);
  line-height: 1.45;
  margin-bottom: 4px;
}

/* —— KPI —— */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.kpi {
  padding: 12px 12px 10px;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid var(--line);
  transition: transform 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.kpi:hover {
  transform: translateY(-2px);
  background: var(--glass-strong);
  box-shadow: var(--shadow-soft);
}

.kpi label {
  display: block;
  font-size: 11px;
  font-weight: 500;
  color: var(--muted);
  margin-bottom: 6px;
  letter-spacing: 0.02em;
}

.kpi b {
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 650;
  color: var(--ink);
}

.kpi.warn {
  background: rgba(255, 159, 10, 0.12);
  border-color: rgba(255, 159, 10, 0.28);
}

.kpi.warn b {
  color: #c77700;
}

.chart-shell {
  position: relative;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.45);
  border: 1px solid var(--line);
  padding: 8px 4px 4px;
  min-height: 168px;
}

.chart-shell.empty {
  border-style: dashed;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 16px;
}

.chart-label {
  position: absolute;
  top: 10px;
  left: 14px;
  z-index: 1;
  font-size: 11px;
  font-weight: 500;
  color: var(--muted);
}

.mini-chart {
  height: 168px;
  width: 100%;
}

.mini-chart.hidden {
  display: none;
}

.chart-empty {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.chart-empty p {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-soft);
}

.chart-empty span {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 4px;
}

/* —— health —— */
.health {
  display: grid;
  grid-template-columns: 4px 1fr;
  gap: 0;
  padding: 0;
  overflow: hidden;
}

.health-bar {
  background: var(--accent);
}

.health.lvl-green .health-bar { background: var(--down); }
.health.lvl-yellow .health-bar { background: var(--warn); }
.health.lvl-red .health-bar { background: var(--up); }

.health-body {
  padding: 14px 16px 16px;
}

.health-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--slate);
}

.health-stats b {
  color: var(--ink);
  font-variant-numeric: tabular-nums;
  margin-left: 4px;
}

.muted {
  color: var(--muted);
}

@media (max-width: 1100px) {
  .kpi-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .two-col {
    grid-template-columns: 1fr;
  }

  .stance-panel {
    grid-template-columns: 1fr;
  }

  .meta-row {
    justify-content: flex-start;
  }

  .count-date {
    margin-left: 0;
    width: 100%;
  }
}

@media (max-width: 560px) {
  .stance-title-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .index-board {
    grid-template-columns: 1fr;
  }

  .kpi-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (prefers-reduced-motion: reduce) {
  .enter,
  .theme-chip {
    animation: none;
  }

  .kpi:hover,
  .panel:hover {
    transform: none;
  }
}
</style>
