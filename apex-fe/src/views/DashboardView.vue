<script setup>
import { nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { dashboardOverview } from '../api/dashboard'
import { getMarketBoard, getTradingCalendar } from '../api/market'
import { signalStats } from '../api/signal'
import { runPipeline } from '../api/pipeline'
import { paperFactorExposure, paperHealthScore, paperMonteCarlo, paperPerformance, paperVolTarget } from '../api/paper'
import { watchlistMovers } from '../api/watchlist'
import http from '../api/http'

const router = useRouter()
const loading = ref(false)
const data = ref(null)
const quality = ref(null)
const calendar = ref(null)
const board = ref(null)
const sigStats = ref(null)
const perf = ref(null)
const movers = ref(null)
const focus = ref(null)
const monteCarlo = ref(null)
const factorExp = ref(null)
const volTarget = ref(null)
const paperHealth = ref(null)
const chartRef = ref(null)
let chart

function renderEquity(paperPoints, benchPoints, altPoints, ddPoints) {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const paper = paperPoints || []
  const bench = benchPoints || []
  const alt = altPoints || []
  const dd = ddPoints || []
  const dates = [
    ...new Set([
      ...paper.map((p) => p.tradeDate),
      ...bench.map((p) => p.tradeDate),
      ...alt.map((p) => p.tradeDate),
      ...dd.map((p) => p.tradeDate),
    ]),
  ].sort()
  const paperMap = Object.fromEntries(paper.map((p) => [p.tradeDate, p.equity]))
  const benchMap = Object.fromEntries(bench.map((p) => [p.tradeDate, p.equity]))
  const altMap = Object.fromEntries(alt.map((p) => [p.tradeDate, p.equity]))
  const ddMap = Object.fromEntries(dd.map((p) => [p.tradeDate, Number(p.equity || 0) * -100]))
  chart.setOption({
    backgroundColor: 'transparent',
    grid: { left: 50, right: 48, top: 28, bottom: 28 },
    legend: { data: ['纸面', '沪深300', '中证500', '回撤%'], top: 0 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: dates },
    yAxis: [
      { type: 'value', scale: true, name: '权益' },
      { type: 'value', scale: true, name: '回撤%', min: 'dataMin', max: 0 },
    ],
    series: [
      {
        type: 'line',
        name: '纸面',
        showSymbol: false,
        data: dates.map((d) => paperMap[d] ?? null),
        lineStyle: { color: '#2f6b52', width: 2.5 },
        areaStyle: { color: 'rgba(47,107,82,0.12)' },
        connectNulls: true,
      },
      {
        type: 'line',
        name: '沪深300',
        showSymbol: false,
        data: dates.map((d) => benchMap[d] ?? null),
        lineStyle: { color: '#8a6d3b', width: 1.5, type: 'dashed' },
        connectNulls: true,
      },
      {
        type: 'line',
        name: '中证500',
        showSymbol: false,
        data: dates.map((d) => altMap[d] ?? null),
        lineStyle: { color: '#4a6fa5', width: 1.2, type: 'dotted' },
        connectNulls: true,
      },
      {
        type: 'line',
        name: '回撤%',
        yAxisIndex: 1,
        showSymbol: false,
        data: dates.map((d) => (ddMap[d] != null ? ddMap[d] : null)),
        lineStyle: { color: '#b85c38', width: 1 },
        areaStyle: { color: 'rgba(184,92,56,0.12)' },
        connectNulls: true,
      },
    ],
  })
}

async function load() {
  loading.value = true
  try {
    const [res, q, cal, b, st, pf, mv, fc, mc, fe, vt, ph] = await Promise.all([
      dashboardOverview(),
      http.get('/api/data/quality', { params: { groupName: '我的自选' } }),
      getTradingCalendar(undefined, 5),
      getMarketBoard('我的自选', 8),
      signalStats(5),
      paperPerformance(),
      watchlistMovers('我的自选', 5, 6),
      http.get('/api/focus/today', { params: { groupName: '我的自选', minScore: 70 } }),
      paperMonteCarlo(undefined, 300, 20),
      paperFactorExposure(),
      paperVolTarget(),
      paperHealthScore(),
    ])
    data.value = res.data
    quality.value = q.data
    calendar.value = cal.data
    board.value = b.data
    sigStats.value = st.data
    perf.value = pf.data
    movers.value = mv.data
    focus.value = fc.data
    monteCarlo.value = mc.data
    factorExp.value = fe.data
    volTarget.value = vt.data
    paperHealth.value = ph.data
    await nextTick()
    renderEquity(
      res.data.equityCurve || [],
      pf.data?.benchmarkEquities || [],
      pf.data?.altBenchmarkEquities || [],
      pf.data?.drawdownCurve || [],
    )
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function quickPipeline() {
  loading.value = true
  try {
    const res = await runPipeline({
      groupName: '我的自选',
      refreshQuotes: false,
      syncStaleBars: true,
      refreshUniverse: true,
      runSignals: true,
      runDaily: true,
    })
    ElMessage.success((res.data.steps || []).join(' → '))
    await load()
  } catch (e) {
    ElMessage.error(e.message || '流水线失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  window.addEventListener('resize', () => chart?.resize())
})
onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="header">
      <div>
        <h1>Apex</h1>
        <p>看板 · 绩效 · 持仓浮盈亏 · 一键研究</p>
      </div>
      <div class="actions">
        <el-button type="primary" @click="quickPipeline">快速研究</el-button>
        <el-button @click="router.push('/pipeline')">流水线</el-button>
        <el-button @click="router.push('/watchlist')">自选</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </header>

    <el-alert
      v-if="calendar"
      :title="`交易日历：${calendar.date} ${calendar.tradingDay ? '开市' : '休市'} · 最近交易日 ${calendar.latestTradingDay} · 下一交易日 ${calendar.nextTradingDay}`"
      :type="calendar.tradingDay ? 'success' : 'warning'"
      :closable="false"
      style="margin-bottom: 8px"
    />

    <el-alert
      v-if="quality"
      :title="`数据SLA ${quality.slaLevel || '-'} · 行情覆盖 ${quality.quoteCoverage != null ? (Number(quality.quoteCoverage) * 100).toFixed(0) + '%' : '-'} · K线就绪率 ${quality.barsReadyCoverage != null ? (Number(quality.barsReadyCoverage) * 100).toFixed(0) + '%' : '-'} · 自选 ${quality.watchlistCount} · 有行情 ${quality.quotedCount} · K线就绪 ${quality.barsReadyCount} · 过期 ${quality.barsStaleCount} · 空 ${quality.barsEmptyCount} · 股票池 ${quality.universeCount} · 近五日信号 ${quality.recentSignalCount}｜${quality.suggestion}`"
      :type="quality.slaLevel === 'GREEN' ? 'success' : quality.slaLevel === 'YELLOW' ? 'warning' : 'error'"
      :closable="false"
      style="margin-bottom: 12px"
    />
    <el-alert
      v-if="perf"
      :title="`纸面 vs 沪深300：Alpha ${(Number(perf.alpha || 0) * 100).toFixed(2)}% · 20日Alpha ${(Number(perf.rollingAlpha20 || 0) * 100).toFixed(2)}% · Beta ${perf.beta ?? '-'} · 20日Beta ${perf.rollingBeta20 ?? '-'} · Sortino ${perf.sortino ?? '-'} · IR ${perf.informationRatio ?? '-'} · TE ${perf.trackingError != null ? (Number(perf.trackingError) * 100).toFixed(2) + '%' : '-'} · vs中证500 Alpha ${(Number(perf.altAlpha || 0) * 100).toFixed(2)}% · 纸面 ${(Number(perf.paperReturn || 0) * 100).toFixed(2)}% · 基准 ${(Number(perf.benchmarkReturn || 0) * 100).toFixed(2)}%`"
      :type="Number(perf.alpha) >= 0 ? 'success' : 'warning'"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="movers"
      :title="`${movers.message}${(movers.gainers || []).length ? ' · 涨 ' + movers.gainers.map((g) => g.code).slice(0, 5).join('/') : ''}${(movers.losers || []).length ? ' · 跌 ' + movers.losers.map((g) => g.code).slice(0, 5).join('/') : ''}`"
      type="info"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="focus"
      :title="`今日关注：${focus.message}${focus.breadthMessage ? ' · ' + focus.breadthMessage : ''}${(focus.confluence || []).length ? ' · 策略共振 ' + focus.confluence.map((c) => c.code).slice(0, 5).join('/') : ''}${(focus.hotConfluence || []).length ? ' · 热点共振 ' + focus.hotConfluence.map((c) => c.code).slice(0, 5).join('/') : ''}${(focus.buySignals || []).length ? ' · BUY ' + focus.buySignals.map((s) => s.code).slice(0, 5).join('/') : ''}`"
      type="success"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <div v-if="(focus?.hotConfluence || []).length" class="section-head" style="margin-top: 4px">
      <h3>热点共振</h3>
      <el-button link type="primary" @click="router.push('/hot')">查看全部</el-button>
    </div>
    <el-table
      v-if="(focus?.hotConfluence || []).length"
      :data="focus.hotConfluence"
      size="small"
      style="margin-bottom: 12px"
    >
      <el-table-column prop="code" label="代码" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" width="120" />
      <el-table-column prop="sourceCount" label="源数" width="70" />
      <el-table-column prop="bestRank" label="最佳排名" width="90" />
      <el-table-column label="涨跌幅" width="90">
        <template #default="{ row }">
          <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">
            {{ row.pctChg != null ? Number(row.pctChg).toFixed(2) + '%' : '-' }}
          </span>
        </template>
      </el-table-column>
    </el-table>
    <el-alert
      v-if="paperHealth"
      :title="`${paperHealth.message}${(paperHealth.factors || []).length ? ' · ' + paperHealth.factors.slice(0, 4).join(' / ') : ''}`"
      :type="paperHealth.grade === 'A' || paperHealth.grade === 'B' ? 'success' : paperHealth.grade === 'C' ? 'warning' : 'error'"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="volTarget"
      :title="`波动目标：${volTarget.message} · 目标 ${(Number(volTarget.targetAnnVol || 0) * 100).toFixed(1)}% · 实现 ${(Number(volTarget.realizedAnnVol || 0) * 100).toFixed(1)}% · 建议仓 ${(Number(volTarget.suggestedPositionRatio || 0) * 100).toFixed(1)}%`"
      type="info"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="monteCarlo"
      :title="`纸面压力：${monteCarlo.message} · P5 ${(Number(monteCarlo.terminalReturnP5 || 0) * 100).toFixed(2)}% · 中位 ${(Number(monteCarlo.terminalReturnP50 || 0) * 100).toFixed(2)}% · DD-P95 ${(Number(monteCarlo.maxDrawdownP95 || 0) * 100).toFixed(2)}%`"
      type="warning"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="factorExp"
      :title="`因子：动量20 ${factorExp.momentum20 ?? '-'}% · 波动 ${factorExp.volatility20 ?? '-'}% · RS ${factorExp.rs20VsHs300 ?? '-'} · 股票仓 ${(Number(factorExp.stockWeight || 0) * 100).toFixed(1)}%`"
      type="info"
      :closable="false"
      style="margin-bottom: 12px"
    />

    <template v-if="data">
      <div class="cards">
        <div class="card">
          <div class="label">总资产</div>
          <div class="value">{{ data.risk?.totalAsset }}</div>
        </div>
        <div class="card">
          <div class="label"><TermTip term="total_return">累计收益</TermTip></div>
          <div class="value" :class="Number(data.paperMetrics?.totalReturn) >= 0 ? 'up' : 'down'">
            {{ data.paperMetrics?.totalReturn != null ? (Number(data.paperMetrics.totalReturn) * 100).toFixed(2) + '%' : '-' }}
          </div>
        </div>
        <div class="card">
          <div class="label"><TermTip term="position_ratio">仓位</TermTip></div>
          <div class="value">{{ ((data.risk?.positionRatio || 0) * 100).toFixed(1) }}%</div>
        </div>
        <div class="card">
          <div class="label"><TermTip term="unrealized_pnl">未实现盈亏</TermTip></div>
          <div class="value" :class="Number(data.paperMetrics?.unrealizedPnl) >= 0 ? 'up' : 'down'">
            {{ data.paperMetrics?.unrealizedPnl ?? '-' }}
          </div>
        </div>
        <div class="card">
          <div class="label">
            <TermTip term="realized_pnl">已实现</TermTip>
            /
            <TermTip term="win_rate">胜率</TermTip>
          </div>
          <div class="value" :class="Number(data.paperMetrics?.realizedPnl) >= 0 ? 'up' : 'down'">
            {{ data.paperMetrics?.realizedPnl ?? '-' }}
            ·
            {{ data.paperMetrics?.winRate != null ? (Number(data.paperMetrics.winRate) * 100).toFixed(0) + '%' : '-' }}
          </div>
        </div>
        <div class="card">
          <div class="label"><TermTip term="expectancy">期望/笔</TermTip></div>
          <div class="value" :class="Number(data.paperMetrics?.expectancy) >= 0 ? 'up' : 'down'">
            {{ data.paperMetrics?.expectancy ?? '-' }}
          </div>
        </div>
        <div class="card">
          <div class="label"><TermTip term="paper_turnover">换手率</TermTip></div>
          <div class="value">
            {{ data.paperMetrics?.turnoverRate != null ? (Number(data.paperMetrics.turnoverRate) * 100).toFixed(1) + '%' : '-' }}
          </div>
        </div>
        <div class="card">
          <div class="label"><TermTip term="max_drawdown">最大回撤</TermTip></div>
          <div class="value">
            {{ data.paperMetrics?.maxDrawdown != null ? (Number(data.paperMetrics.maxDrawdown) * 100).toFixed(2) + '%' : '-' }}
          </div>
        </div>
        <div class="card">
          <div class="label"><TermTip term="sharpe">夏普 / 20日</TermTip></div>
          <div class="value">{{ data.paperMetrics?.sharpe ?? '-' }} / {{ data.paperMetrics?.rollingSharpe20 ?? '-' }}</div>
        </div>
        <div class="card">
          <div class="label">
            <TermTip term="var95">VaR95</TermTip>
            /
            <TermTip term="cvar">CVaR</TermTip>
          </div>
          <div class="value">
            {{ data.paperMetrics?.dailyVar95 != null ? (Number(data.paperMetrics.dailyVar95) * 100).toFixed(2) + '%' : '-' }}
            /
            {{ data.paperMetrics?.dailyCvar95 != null ? (Number(data.paperMetrics.dailyCvar95) * 100).toFixed(2) + '%' : '-' }}
          </div>
        </div>
        <div class="card">
          <div class="label"><TermTip term="calmar">Calmar</TermTip></div>
          <div class="value">{{ data.paperMetrics?.calmar ?? '-' }}</div>
        </div>
        <div class="card">
          <div class="label"><TermTip term="underwater_ratio">水下占比</TermTip></div>
          <div class="value">
            {{ data.paperMetrics?.underwaterRatio != null ? (Number(data.paperMetrics.underwaterRatio) * 100).toFixed(0) + '%' : '-' }}
          </div>
        </div>
        <div class="card">
          <div class="label"><TermTip term="drawdown_recovery">回撤天数</TermTip></div>
          <div class="value">{{ data.paperMetrics?.drawdownRecoveryDays ?? '-' }}</div>
        </div>
        <div class="card">
          <div class="label"><TermTip term="profit_factor">盈亏因子</TermTip></div>
          <div class="value">{{ data.paperMetrics?.profitFactor ?? '-' }}</div>
        </div>
        <div class="card">
          <div class="label"><TermTip term="payoff_ratio">盈亏比</TermTip></div>
          <div class="value">{{ data.paperMetrics?.payoffRatio ?? '-' }}</div>
        </div>
        <div class="card">
          <div class="label"><TermTip term="sortino">Sortino</TermTip></div>
          <div class="value">{{ data.paperMetrics?.sortino ?? '-' }}</div>
        </div>
        <div class="card">
          <div class="label"><TermTip term="ulcer">Ulcer</TermTip></div>
          <div class="value">{{ data.paperMetrics?.ulcerIndex ?? '-' }}</div>
        </div>
        <div class="card">
          <div class="label">累计费用</div>
          <div class="value">{{ data.paperMetrics?.totalFee ?? '-' }}</div>
        </div>
        <div class="card">
          <div class="label"><TermTip term="omega">Omega</TermTip></div>
          <div class="value">{{ data.paperMetrics?.omega ?? '-' }}</div>
        </div>
        <div class="card">
          <div class="label"><TermTip term="max_losing_days">最长连亏天</TermTip></div>
          <div class="value">{{ data.paperMetrics?.maxLosingDays ?? '-' }}</div>
        </div>
        <div class="card">
          <div class="label">现金拖累</div>
          <div class="value">
            {{ data.paperMetrics?.cashDrag != null ? (Number(data.paperMetrics.cashDrag) * 100).toFixed(1) + '%' : '-' }}
          </div>
        </div>
        <div class="card warn">
          <div class="label">告警</div>
          <div class="value">
            C{{ data.risk?.criticalCount || 0 }} / W{{ data.risk?.warnCount || 0 }}
          </div>
        </div>
      </div>

      <template v-if="(data?.risk?.alerts || []).length">
        <el-alert
          v-for="(a, i) in data.risk.alerts"
          :key="'a' + i"
          :title="`[${a.level}] ${a.message}`"
          :type="a.level === 'CRITICAL' ? 'error' : a.level === 'WARN' ? 'warning' : 'info'"
          show-icon
          :closable="false"
          style="margin-bottom: 8px"
        />
      </template>
      <template v-else>
        <el-alert
          v-for="(w, i) in data?.risk?.warnings || []"
          :key="'w' + i"
          :title="w"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom: 8px"
        />
      </template>

      <template v-if="board">
        <el-alert
          v-if="board.breadth"
          :title="`市场宽度：${board.breadth.message}`"
          :type="Number(board.breadth.advanceDeclineRatio) >= 1 ? 'success' : 'warning'"
          :closable="false"
          style="margin-bottom: 8px"
        />
        <el-alert
          v-if="board.volRegime"
          :title="`波动体制：${board.volRegime.message}${board.volRegime.regime === 'HIGH' ? ' · 建议仓位已×0.5防守' : board.volRegime.regime === 'LOW' ? ' · 建议仓位可×1.1' : ''}`"
          :type="board.volRegime.regime === 'HIGH' ? 'error' : board.volRegime.regime === 'LOW' ? 'success' : 'info'"
          :closable="false"
          style="margin-bottom: 8px"
        />
        <el-alert
          :title="`信号：BUY ${board.buySignalCount} / SELL ${board.sellSignalCount} · 股票池 ${board.universeCount}${sigStats?.byStrategy ? ' · 策略 ' + Object.entries(sigStats.byStrategy).map(([k,v]) => k+':'+v).join(' ') : ''}`"
          type="info"
          :closable="false"
          style="margin-bottom: 12px"
        />
        <div class="board-grid">
          <div>
            <h3>涨幅榜</h3>
            <el-table :data="board.gainers || []" size="small" height="220">
              <el-table-column prop="code" label="代码" width="90">
                <template #default="{ row }">
                  <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
                </template>
              </el-table-column>
              <el-table-column prop="name" label="名称" width="100" />
              <el-table-column prop="pctChg" label="涨跌%" width="80">
                <template #default="{ row }">
                  <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">{{ row.pctChg }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div>
            <h3>跌幅榜</h3>
            <el-table :data="board.losers || []" size="small" height="220">
              <el-table-column prop="code" label="代码" width="90">
                <template #default="{ row }">
                  <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
                </template>
              </el-table-column>
              <el-table-column prop="name" label="名称" width="100" />
              <el-table-column prop="pctChg" label="涨跌%" width="80">
                <template #default="{ row }">
                  <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">{{ row.pctChg }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div>
            <h3>行业热力</h3>
            <el-table :data="board.industryHeat || []" size="small" height="220">
              <el-table-column prop="industry" label="行业" min-width="100" />
              <el-table-column prop="avgPctChg" label="均涨跌" width="80">
                <template #default="{ row }">
                  <span :class="Number(row.avgPctChg) >= 0 ? 'up' : 'down'">{{ row.avgPctChg }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="upCount" label="涨" width="50" />
              <el-table-column prop="downCount" label="跌" width="50" />
            </el-table>
          </div>
        </div>
      </template>

      <h3>纸面权益（成交回放 + 当前市值）</h3>
      <div ref="chartRef" class="chart" />

      <h3>行业归因</h3>
      <el-table :data="data.industryPnls || []" size="small" style="margin-bottom: 8px">
        <el-table-column prop="industry" label="行业" min-width="140" />
        <el-table-column prop="marketValue" label="市值" width="120" />
        <el-table-column prop="pnl" label="浮盈亏" width="120">
          <template #default="{ row }">
            <span :class="Number(row.pnl) >= 0 ? 'up' : 'down'">{{ row.pnl }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="weight" label="仓位占比" width="100">
          <template #default="{ row }">
            {{ row.weight != null ? (Number(row.weight) * 100).toFixed(1) + '%' : '-' }}
          </template>
        </el-table-column>
      </el-table>

      <div class="section-head">
        <h3>持仓</h3>
        <el-button link type="primary" @click="router.push('/paper')">模拟盘</el-button>
      </div>
      <el-empty v-if="!(data.positions || []).length" description="暂无持仓，可从流水线/信号一键模拟买入" :image-size="60" />
      <el-table v-else :data="data.positions || []" size="small">
        <el-table-column prop="code" label="代码" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" width="120" />
        <el-table-column prop="quantity" label="数量" width="90" />
        <el-table-column prop="costPrice" label="成本" width="100" />
        <el-table-column prop="marketPrice" label="现价" width="100" />
        <el-table-column prop="pnl" label="浮盈亏" width="100">
          <template #default="{ row }">
            <span :class="Number(row.pnl) >= 0 ? 'up' : 'down'">{{ row.pnl ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="pnlPct" label="盈亏%" width="90">
          <template #default="{ row }">
            <span :class="Number(row.pnlPct) >= 0 ? 'up' : 'down'">
              {{ row.pnlPct != null ? (Number(row.pnlPct) * 100).toFixed(2) + '%' : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="stopLoss" label="止损" width="100" />
        <el-table-column prop="takeProfit" label="止盈" width="100" />
      </el-table>

      <div class="section-head">
        <h3>今日清单</h3>
        <div>
          <el-button link type="primary" @click="router.push('/decision')">智能决策</el-button>
          <el-button link type="primary" @click="router.push('/hot')">市场热点</el-button>
          <el-button link type="primary" @click="router.push('/daily')">查看全部</el-button>
        </div>
      </div>
      <el-table :data="data.todayActions || []" size="small">
        <el-table-column prop="action" label="动作" width="80" />
        <el-table-column prop="code" label="代码" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" width="120" />
        <el-table-column prop="strategyId" label="策略" width="80" />
        <el-table-column prop="reason" label="理由" min-width="180" show-overflow-tooltip />
      </el-table>

      <div class="section-head">
        <h3>近五日信号</h3>
        <el-button link type="primary" @click="router.push('/signals')">信号页</el-button>
      </div>
      <el-table :data="data.recentSignals || []" size="small">
        <el-table-column prop="signalDate" label="日期" width="120" />
        <el-table-column prop="code" label="代码" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="strategyId" label="策略" width="80" />
        <el-table-column prop="side" label="方向" width="80" />
        <el-table-column prop="score" label="评分" width="80" />
      </el-table>
    </template>
  </div>
</template>

<style scoped>
@media (max-width: 1100px) {
  .board-grid {
    grid-template-columns: 1fr;
  }
}
</style>
