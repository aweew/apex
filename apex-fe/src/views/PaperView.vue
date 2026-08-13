<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { buildApiUrl } from '../api/baseUrl'
import {
  applyAtrStops,
  closeAllPositions,
  closeTriggered,
  getAccount,
  listOrders,
  listPositions,
  paperAtrStops,
  paperExposure,
  paperPerformance,
  placeOrder,
  paperCorrelation,
  paperCost,
  paperFactorExposure,
  paperFillQuality,
  paperGapRisk,
  paperHoldBuckets,
  paperKelly,
  paperMonteCarlo,
  paperMonthly,
  paperReturnHist,
  paperBetaTarget,
  paperEquityQuality,
  paperHealthScore,
  paperStopCoverage,
  paperTradeCalendar,
  paperVolTarget,
  paperWeekdayPnl,
  rebalanceSuggest,
  refreshMarks,
  signalBuySuggest,
  suggestPosition,
  updateStops,
} from '../api/paper'
import { dashboardOverview, riskOverview } from '../api/dashboard'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const account = ref(null)
const positions = ref([])
const orders = ref([])
const risk = ref(null)
const metrics = ref(null)
const perf = ref(null)
const exposure = ref(null)
const rebalance = ref(null)
const monthly = ref([])
const corr = ref(null)
const cost = ref(null)
const kelly = ref(null)
const fillQ = ref(null)
const gapRisk = ref(null)
const holdBuckets = ref(null)
const weekdayPnl = ref(null)
const monteCarlo = ref(null)
const factorExp = ref(null)
const atrStops = ref(null)
const returnHist = ref(null)
const volTarget = ref(null)
const tradeCal = ref(null)
const stopCov = ref(null)
const betaTarget = ref(null)
const paperHealth = ref(null)
const equityQ = ref(null)
const orderSide = ref('')
const orderDays = ref(90)
const form = ref({
  code: String(route.query.code || '600519'),
  side: String(route.query.side || 'BUY'),
  quantity: 100,
  targetWeight: '',
})

const filteredOrders = computed(() => {
  const cutoff = Date.now() - Number(orderDays.value || 90) * 86400000
  return (orders.value || []).filter((o) => {
    if (orderSide.value && o.side !== orderSide.value) return false
    if (!o.tradeDate) return true
    return new Date(o.tradeDate).getTime() >= cutoff
  })
})

async function load() {
  loading.value = true
  try {
    const acc = await getAccount()
    account.value = acc.data
    const [pos, ord, r, dash, pf, ex, mo, cr, cs, ky, fq, gr, hb, wd, mc, fe, atr, rh, vt, tc, sc, bt, ph, eq] = await Promise.all([
      listPositions(acc.data.id),
      listOrders(acc.data.id),
      riskOverview(acc.data.id),
      dashboardOverview(acc.data.id),
      paperPerformance(acc.data.id),
      paperExposure(acc.data.id),
      paperMonthly(acc.data.id),
      paperCorrelation(acc.data.id, 60),
      paperCost(acc.data.id),
      paperKelly(acc.data.id),
      paperFillQuality(acc.data.id, 20),
      paperGapRisk(acc.data.id),
      paperHoldBuckets(acc.data.id),
      paperWeekdayPnl(acc.data.id),
      paperMonteCarlo(acc.data.id, 500, 20),
      paperFactorExposure(acc.data.id),
      paperAtrStops(acc.data.id),
      paperReturnHist(acc.data.id),
      paperVolTarget(acc.data.id),
      paperTradeCalendar(acc.data.id, 60),
      paperStopCoverage(acc.data.id),
      paperBetaTarget(acc.data.id),
      paperHealthScore(acc.data.id),
      paperEquityQuality(acc.data.id),
    ])
    positions.value = pos.data || []
    orders.value = ord.data || []
    risk.value = r.data
    metrics.value = dash.data?.paperMetrics || null
    perf.value = pf.data || null
    exposure.value = ex.data || null
    monthly.value = mo.data || []
    corr.value = cr.data || null
    cost.value = cs.data || null
    kelly.value = ky.data || null
    fillQ.value = fq.data || null
    gapRisk.value = gr.data || null
    holdBuckets.value = hb.data || null
    weekdayPnl.value = wd.data || null
    monteCarlo.value = mc.data || null
    factorExp.value = fe.data || null
    atrStops.value = atr.data || null
    returnHist.value = rh.data || null
    volTarget.value = vt.data || null
    tradeCal.value = tc.data || null
    stopCov.value = sc.data || null
    betaTarget.value = bt.data || null
    paperHealth.value = ph.data || null
    equityQ.value = eq.data || null
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onOrder() {
  loading.value = true
  try {
    const payload = {
      accountId: account.value.id,
      code: form.value.code,
      side: form.value.side,
    }
    const tw = Number(form.value.targetWeight)
    if (tw > 0 && tw < 1) {
      payload.targetWeight = tw
    } else {
      payload.quantity = Number(form.value.quantity)
    }
    await placeOrder(payload)
    ElMessage.success('模拟成交成功')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '下单失败')
  } finally {
    loading.value = false
  }
}

async function onSuggest() {
  loading.value = true
  try {
    const tw = Number(form.value.targetWeight)
    const res = await suggestPosition(
      form.value.code,
      account.value.id,
      tw > 0 && tw < 1 ? tw : undefined,
    )
    const s = res.data || {}
    form.value.quantity = s.suggestedQuantity || 0
    if (s.targetWeight != null) form.value.targetWeight = String(s.targetWeight)
    ElMessage.success(
      `建议 ${s.suggestedQuantity} 股 · 约 ${s.estimatedAmount} · ${s.message || ''}`,
    )
  } catch (e) {
    ElMessage.error(e.message || '建议失败')
  } finally {
    loading.value = false
  }
}

async function onCloseAll() {
  if (!positions.value.length) {
    ElMessage.warning('无持仓')
    return
  }
  try {
    await ElMessageBox.confirm(`确认卖出全部 ${positions.value.length} 只持仓？`, '一键平仓', {
      type: 'warning',
    })
  } catch {
    return
  }
  loading.value = true
  try {
    const res = await closeAllPositions(account.value.id)
    ElMessage.success(`已平仓 ${((res.data || []).length)} 笔`)
    await load()
  } catch (e) {
    ElMessage.error(e.message || '平仓失败')
  } finally {
    loading.value = false
  }
}

async function onRebalanceSuggest() {
  loading.value = true
  try {
    const res = await rebalanceSuggest(account.value?.id, 8)
    rebalance.value = res.data
    ElMessage.success(res.data?.message || '已生成再平衡建议')
  } catch (e) {
    ElMessage.error(e.message || '再平衡建议失败')
  } finally {
    loading.value = false
  }
}

async function onSignalBuySuggest() {
  loading.value = true
  try {
    const res = await signalBuySuggest(account.value?.id, 5, 70)
    rebalance.value = res.data
    ElMessage.success(res.data?.message || '已生成信号买入建议')
  } catch (e) {
    ElMessage.error(e.message || '信号建议失败')
  } finally {
    loading.value = false
  }
}

async function onApplyAtrStops() {
  try {
    await ElMessageBox.confirm('按 ATR14 建议覆盖全部持仓止损/止盈？', 'ATR 止损', { type: 'warning' })
  } catch {
    return
  }
  loading.value = true
  try {
    const res = await applyAtrStops(account.value?.id)
    ElMessage.success(`已更新 ${res.data ?? 0} 只止损止盈`)
    await load()
  } catch (e) {
    ElMessage.error(e.message || '应用失败')
  } finally {
    loading.value = false
  }
}

async function onCloseTriggered() {
  try {
    await ElMessageBox.confirm('确认平仓所有已触及止损/止盈的持仓？', '触发平仓', { type: 'warning' })
  } catch {
    return
  }
  loading.value = true
  try {
    const res = await closeTriggered(account.value.id, 'BOTH')
    ElMessage.success(`已触发平仓 ${(res.data || []).length} 笔`)
    await load()
  } catch (e) {
    ElMessage.error(e.message || '触发平仓失败')
  } finally {
    loading.value = false
  }
}

async function onRefreshMarks() {
  loading.value = true
  try {
    const res = await refreshMarks(account.value?.id)
    positions.value = res.data || []
    ElMessage.success('已刷新持仓市价')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '刷新失败')
  } finally {
    loading.value = false
  }
}

function quickSell(row) {
  form.value.code = row.code
  form.value.side = 'SELL'
  form.value.quantity = row.quantity
}

async function onEditStops(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      '格式：止损价,止盈价（如 45.2,58）',
      `设置 ${row.code} 止损止盈`,
      { inputValue: `${row.stopLoss || ''},${row.takeProfit || ''}` },
    )
    const parts = String(value).split(',').map((s) => s.trim())
    const stopLoss = parts[0] ? Number(parts[0]) : null
    const takeProfit = parts[1] ? Number(parts[1]) : null
    await updateStops({
      accountId: account.value.id,
      code: row.code,
      stopLoss,
      takeProfit,
    })
    ElMessage.success('已更新止损止盈')
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '更新失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="header">
      <div>
        <p class="eyebrow">Paper</p>
        <h1>模拟盘</h1>
        <p v-if="account">账户 {{ account.accountName }} · 现金 {{ account.cash }} · 对照决策清单验证执行</p>
        <p v-else>信号建议下单 · 止损止盈 · 再平衡</p>
      </div>
      <div class="actions">
        <el-button :loading="loading" @click="onRefreshMarks">刷新市价</el-button>
        <el-button :loading="loading" @click="onRebalanceSuggest">再平衡建议</el-button>
        <el-button :loading="loading" @click="onSignalBuySuggest">信号买入建议</el-button>
        <el-button type="warning" plain :loading="loading" @click="onCloseTriggered">触发止损止盈</el-button>
        <el-button plain :loading="loading" @click="onApplyAtrStops">应用ATR止损</el-button>
        <el-button type="danger" plain :loading="loading" @click="onCloseAll">一键平仓</el-button>
        <el-link
          type="primary"
          :href="buildApiUrl(`/api/export/paper/performance?accountId=${account?.id || ''}`)"
          target="_blank"
        >导出绩效</el-link>
        <el-link
          type="primary"
          :href="buildApiUrl(`/api/export/paper/orders?accountId=${account?.id || ''}`)"
          target="_blank"
        >导出订单CSV</el-link>
        <el-button plain @click="router.push('/decision')">决策清单</el-button>
        <el-button plain @click="router.push('/holding')">真实持仓</el-button>
        <el-button text @click="load" :loading="loading">刷新</el-button>
      </div>
    </header>

    <el-alert
      v-if="risk"
      :title="`仓位 ${(risk.positionRatio * 100).toFixed(1)}% / 总仓上限 ${(risk.totalLimit * 100).toFixed(0)}% / 单票 ${(risk.singleLimit * 100).toFixed(0)}% / 行业 ${((risk.industryLimit || 0.3) * 100).toFixed(0)}%`"
      type="info"
      :closable="false"
      style="margin-bottom: 12px"
    />
    <el-alert
      v-if="metrics"
      :title="`绩效：累计 ${(Number(metrics.totalReturn || 0) * 100).toFixed(2)}% · 回撤 ${metrics.maxDrawdown != null ? (Number(metrics.maxDrawdown) * 100).toFixed(2) + '%' : '-'} · Ulcer ${metrics.ulcerIndex ?? '-'} · 夏普 ${metrics.sharpe ?? '-'} · 20日夏普 ${metrics.rollingSharpe20 ?? '-'} · Sortino ${metrics.sortino ?? '-'} · 盈亏因子 ${metrics.profitFactor ?? '-'} · 盈亏比 ${metrics.payoffRatio ?? '-'} · VaR95 ${metrics.dailyVar95 != null ? (Number(metrics.dailyVar95) * 100).toFixed(2) + '%' : '-'} · Calmar ${metrics.calmar ?? '-'} · 费用 ${metrics.totalFee ?? '-'} · 费率 ${metrics.feeRate != null ? (Number(metrics.feeRate) * 10000).toFixed(1) + 'bp' : '-'} · 均持仓 ${metrics.avgHoldDays ?? '-'}天 · 连胜 ${metrics.winStreak ?? 0} / 连亏 ${metrics.lossStreak ?? 0} · 浮盈 ${metrics.unrealizedPnl ?? '-'} · 已实现 ${metrics.realizedPnl ?? '-'} · 胜率 ${metrics.winRate != null ? (Number(metrics.winRate) * 100).toFixed(0) + '%' : '-'} · 换手 ${(Number(metrics.turnoverRate || 0) * 100).toFixed(1)}% · 闭合 ${metrics.closedTradeCount ?? 0}`"
      type="success"
      :closable="false"
      style="margin-bottom: 12px"
    />
    <el-alert
      v-if="perf"
      :title="`相对沪深300：纸面 ${(Number(perf.paperReturn || 0) * 100).toFixed(2)}% · TWR ${(Number(perf.timeWeightedReturn || 0) * 100).toFixed(2)}% · 基准 ${(Number(perf.benchmarkReturn || 0) * 100).toFixed(2)}% · Alpha ${(Number(perf.alpha || 0) * 100).toFixed(2)}% · 20日Alpha ${(Number(perf.rollingAlpha20 || 0) * 100).toFixed(2)}% · Beta ${perf.beta ?? '-'} · 20日Beta ${perf.rollingBeta20 ?? '-'} · Sortino ${perf.sortino ?? '-'} · IR ${perf.informationRatio ?? '-'} · TE ${perf.trackingError != null ? (Number(perf.trackingError) * 100).toFixed(2) + '%' : '-'} · vs${perf.altBenchmarkCode || '000905'} Alpha ${(Number(perf.altAlpha || 0) * 100).toFixed(2)}% · 自 ${perf.startDate}`"
      :type="Number(perf.alpha) >= 0 ? 'success' : 'warning'"
      :closable="false"
      style="margin-bottom: 12px"
    />
    <el-alert
      v-if="exposure"
      :title="`暴露：仓位 ${(Number(exposure.equityWeight || 0) * 100).toFixed(1)}% · 现金 ${(Number(exposure.cashWeight || 0) * 100).toFixed(1)}% · 第一大 ${(Number(exposure.top1Weight || 0) * 100).toFixed(1)}% · 前五 ${(Number(exposure.top5Weight || 0) * 100).toFixed(1)}% · HHI ${exposure.herfindahl ?? '-'}`"
      type="info"
      :closable="false"
      style="margin-bottom: 12px"
    />
    <el-table
      v-if="exposure?.industries?.length"
      :data="exposure.industries"
      size="small"
      style="margin-bottom: 12px"
    >
      <el-table-column prop="industry" label="行业归因" width="140" />
      <el-table-column prop="marketValue" label="市值" width="110" />
      <el-table-column label="权重" width="90">
        <template #default="{ row }">{{ (Number(row.weight || 0) * 100).toFixed(1) }}%</template>
      </el-table-column>
      <el-table-column label="浮盈亏" width="100">
        <template #default="{ row }">
          <span :class="Number(row.pnl) >= 0 ? 'up' : 'down'">{{ row.pnl ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="盈亏贡献" width="100">
        <template #default="{ row }">{{ (Number(row.pnlContribution || 0) * 100).toFixed(1) }}%</template>
      </el-table-column>
    </el-table>
    <el-alert
      v-if="(risk?.alerts || []).length"
      :title="risk.alerts.map((a) => `[${a.level}] ${a.message}`).join('；')"
      type="warning"
      :closable="false"
      style="margin-bottom: 12px"
    />
    <el-alert
      v-else-if="risk?.warnings?.length"
      :title="risk.warnings.join('；')"
      type="warning"
      :closable="false"
      style="margin-bottom: 12px"
    />

    <el-form :inline="true">
      <el-form-item label="代码"><el-input v-model="form.code" style="width: 110px" /></el-form-item>
      <el-form-item label="方向">
        <el-select v-model="form.side" style="width: 100px">
          <el-option label="买入" value="BUY" />
          <el-option label="卖出" value="SELL" />
        </el-select>
      </el-form-item>
      <el-form-item label="数量"><el-input v-model="form.quantity" style="width: 100px" /></el-form-item>
      <el-form-item label="仓位%"><el-input v-model="form.targetWeight" style="width: 90px" placeholder="0.1" /></el-form-item>
      <el-form-item>
        <el-button :loading="loading" @click="onSuggest">风控建议数量</el-button>
        <el-button type="primary" :loading="loading" @click="onOrder">下单</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="rebalance"
      :title="`${rebalance.message} · 目标 ${ (rebalance.targetCodes || []).join('/') } · 单票权 ${(Number(rebalance.targetWeight || 0) * 100).toFixed(1)}%`"
      type="info"
      :closable="false"
      style="margin-bottom: 10px"
    />
    <el-table v-if="rebalance?.orders?.length" :data="rebalance.orders" size="small" style="margin-bottom: 12px">
      <el-table-column prop="code" label="再平衡" width="90" />
      <el-table-column prop="side" label="方向" width="70" />
      <el-table-column prop="quantity" label="数量" width="90" />
      <el-table-column prop="price" label="参考价" width="90" />
      <el-table-column prop="currentWeight" label="当前权" width="90">
        <template #default="{ row }">{{ (Number(row.currentWeight || 0) * 100).toFixed(1) }}%</template>
      </el-table-column>
      <el-table-column prop="targetWeight" label="目标权" width="90">
        <template #default="{ row }">{{ (Number(row.targetWeight || 0) * 100).toFixed(1) }}%</template>
      </el-table-column>
      <el-table-column prop="reason" label="原因" min-width="140" />
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="
              form.code = row.code;
              form.side = row.side;
              form.quantity = row.quantity;
            "
          >填入</el-button>
        </template>
      </el-table-column>
    </el-table>

    <h3 v-if="monthly.length">月度收益</h3>
    <el-table v-if="monthly.length" :data="monthly" size="small" style="margin-bottom: 12px">
      <el-table-column prop="month" label="月份" width="100" />
      <el-table-column label="当月收益" width="120">
        <template #default="{ row }">
          <span :class="Number(row.monthReturn) >= 0 ? 'up' : 'down'">
            {{ row.monthReturn != null ? (Number(row.monthReturn) * 100).toFixed(2) + '%' : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="endEquity" label="月末权益" width="130" />
    </el-table>

    <h3>持仓</h3>
    <div v-if="!positions.length" class="page-empty">
      <h3>模拟盘暂无持仓</h3>
      <p>从决策清单或信号页一键下单，验证策略执行与止损止盈</p>
      <el-button type="primary" @click="router.push('/decision')">去决策清单</el-button>
      <el-button plain @click="router.push('/signals')">看策略信号</el-button>
    </div>
    <el-table v-else :data="positions" size="small">
      <el-table-column prop="code" label="代码" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
          <SecurityMarketBadge :security="row" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" width="120" />
      <el-table-column prop="quantity" label="数量" width="90" />
      <el-table-column prop="holdDays" label="持有天" width="80" />
      <el-table-column prop="costPrice" label="成本" width="100" />
      <el-table-column prop="marketPrice" label="现价" width="100" />
      <el-table-column prop="marketValue" label="市值" width="110" />
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
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link @click="onEditStops(row)">止损止盈</el-button>
          <el-button link type="danger" @click="quickSell(row)">卖出</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-alert
      v-if="cost"
      :title="`${cost.message || '费用'} · 买 ${cost.buyCount ?? 0} / 卖 ${cost.sellCount ?? 0} · 占本金 ${cost.feeToCapital != null ? (Number(cost.feeToCapital) * 100).toFixed(3) + '%' : '-'}`"
      type="warning"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="kelly"
      :title="`Kelly：${kelly.message} · 胜率 ${kelly.winRate != null ? (Number(kelly.winRate) * 100).toFixed(0) + '%' : '-'} · 盈亏比 ${kelly.payoffRatio ?? '-'} · 半Kelly ${(Number(kelly.halfKelly || 0) * 100).toFixed(1)}% · 建议仓位 ${(Number(kelly.suggestedWeight || 0) * 100).toFixed(1)}%`"
      type="success"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="fillQ"
      :title="`成交质量：${fillQ.message} · 分 ${fillQ.qualityScore ?? '-'} · 买滑 ${fillQ.avgBuySlippage != null ? (Number(fillQ.avgBuySlippage) * 10000).toFixed(1) + 'bp' : '-'} · 卖滑 ${fillQ.avgSellSlippage != null ? (Number(fillQ.avgSellSlippage) * 10000).toFixed(1) + 'bp' : '-'}`"
      :type="Number(fillQ.qualityScore) >= 80 ? 'success' : 'warning'"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="gapRisk"
      :title="`隔夜缺口：${gapRisk.message} · 均|缺口| ${gapRisk.avgAbsGapPct ?? '-'}% · 最大 ${gapRisk.maxAbsGapPct ?? '-'}%`"
      :type="Number(gapRisk.maxAbsGapPct) >= 3 ? 'error' : 'info'"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="paperHealth"
      :title="`${paperHealth.message}${(paperHealth.factors || []).length ? ' · ' + paperHealth.factors.slice(0, 3).join(' / ') : ''}`"
      :type="paperHealth.grade === 'A' || paperHealth.grade === 'B' ? 'success' : 'warning'"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="equityQ"
      :title="`曲线质量：${equityQ.message}`"
      type="info"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="stopCov"
      :title="`止损覆盖：${stopCov.message} · 止盈覆盖 ${stopCov.takeCoverage != null ? (Number(stopCov.takeCoverage) * 100).toFixed(0) + '%' : '-'}`"
      :type="Number(stopCov.stopCoverage) >= 0.8 ? 'success' : 'warning'"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="betaTarget"
      :title="`Beta目标：${betaTarget.message} · 当前 ${betaTarget.currentBeta ?? '-'} · 目标 ${betaTarget.targetBeta ?? '-'} · 建议仓 ${(Number(betaTarget.suggestedPositionRatio || 0) * 100).toFixed(1)}%`"
      type="info"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="volTarget"
      :title="`波动目标：${volTarget.message} · 目标 ${(Number(volTarget.targetAnnVol || 0) * 100).toFixed(1)}% · 实现 ${(Number(volTarget.realizedAnnVol || 0) * 100).toFixed(1)}% · 缩放 ${volTarget.scale ?? '-'} · 建议仓 ${(Number(volTarget.suggestedPositionRatio || 0) * 100).toFixed(1)}%`"
      type="info"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="monteCarlo"
      :title="`蒙特卡洛：${monteCarlo.message} · P5 ${(Number(monteCarlo.terminalReturnP5 || 0) * 100).toFixed(2)}% · 中位 ${(Number(monteCarlo.terminalReturnP50 || 0) * 100).toFixed(2)}% · P95 ${(Number(monteCarlo.terminalReturnP95 || 0) * 100).toFixed(2)}% · 均MaxDD ${(Number(monteCarlo.avgMaxDrawdown || 0) * 100).toFixed(2)}% · DD-P95 ${(Number(monteCarlo.maxDrawdownP95 || 0) * 100).toFixed(2)}%`"
      type="warning"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="factorExp"
      :title="`因子暴露：${factorExp.message} · 动量20 ${factorExp.momentum20 ?? '-'}% · 波动 ${factorExp.volatility20 ?? '-'}% · RS20 ${factorExp.rs20VsHs300 ?? '-'} · 股票仓 ${(Number(factorExp.stockWeight || 0) * 100).toFixed(1)}%`"
      type="info"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <el-alert
      v-if="corr"
      :title="`${corr.message || '持仓相关'}${(corr.codes || []).length ? ' · ' + corr.codes.join('/') : ''}`"
      type="info"
      :closable="false"
      style="margin: 12px 0"
    />

    <h3 v-if="gapRisk?.items?.length">隔夜缺口明细</h3>
    <el-table v-if="gapRisk?.items?.length" :data="gapRisk.items" size="small" style="margin-bottom: 12px">
      <el-table-column prop="code" label="代码" width="100" />
      <el-table-column prop="name" label="名称" width="120" />
      <el-table-column prop="avgAbsGapPct" label="均|缺口|%" width="110" />
      <el-table-column prop="maxAbsGapPct" label="最大|缺口|%" width="110" />
      <el-table-column prop="lastGapPct" label="最近缺口%" width="110" />
    </el-table>

    <h3 v-if="fillQ?.items?.length">成交质量明细</h3>
    <el-table v-if="fillQ?.items?.length" :data="fillQ.items" size="small" style="margin-bottom: 12px">
      <el-table-column prop="tradeDate" label="日期" width="120" />
      <el-table-column prop="code" label="代码" width="100" />
      <el-table-column prop="side" label="方向" width="80" />
      <el-table-column prop="fillPrice" label="成交价" width="100" />
      <el-table-column prop="closePrice" label="收盘" width="100" />
      <el-table-column label="不利滑点bp" width="110">
        <template #default="{ row }">
          {{ row.slippageVsClose != null ? (Number(row.slippageVsClose) * 10000).toFixed(1) : '-' }}
        </template>
      </el-table-column>
    </el-table>

    <h3 v-if="atrStops?.items?.length"><TermTip term="atr">ATR</TermTip> 止损建议</h3>
    <el-table v-if="atrStops?.items?.length" :data="atrStops.items" size="small" style="margin-bottom: 12px">
      <el-table-column prop="code" label="代码" width="90" />
      <el-table-column prop="atr14" label="ATR14" width="90" />
      <el-table-column prop="suggestedStopLoss" label="建议止损" width="100" />
      <el-table-column prop="suggestedTakeProfit" label="建议止盈" width="100" />
      <el-table-column prop="currentStopLoss" label="当前止损" width="100" />
      <el-table-column prop="currentTakeProfit" label="当前止盈" width="100" />
    </el-table>

    <h3 v-if="holdBuckets?.buckets?.length">持仓周期分桶</h3>
    <el-table v-if="holdBuckets?.buckets?.length" :data="holdBuckets.buckets" size="small" style="margin-bottom: 12px">
      <el-table-column prop="bucket" label="周期" width="100" />
      <el-table-column prop="tradeCount" label="笔数" width="80" />
      <el-table-column label="胜率" width="90">
        <template #default="{ row }">
          {{ row.winRate != null ? (Number(row.winRate) * 100).toFixed(0) + '%' : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="均收益" width="100">
        <template #default="{ row }">
          {{ row.avgReturn != null ? (Number(row.avgReturn) * 100).toFixed(2) + '%' : '-' }}
        </template>
      </el-table-column>
    </el-table>

    <h3 v-if="returnHist?.buckets?.length">收益分布</h3>
    <el-table v-if="returnHist?.buckets?.length" :data="returnHist.buckets" size="small" style="margin-bottom: 12px; max-width: 360px">
      <el-table-column prop="bucket" label="区间" width="120" />
      <el-table-column prop="count" label="笔数" width="80" />
    </el-table>

    <h3 v-if="weekdayPnl?.items?.length">周几盈亏</h3>
    <el-table v-if="weekdayPnl?.items?.length" :data="weekdayPnl.items" size="small" style="margin-bottom: 12px">
      <el-table-column prop="label" label="星期" width="80" />
      <el-table-column prop="tradeCount" label="笔数" width="80" />
      <el-table-column label="胜率" width="90">
        <template #default="{ row }">
          {{ row.winRate != null ? (Number(row.winRate) * 100).toFixed(0) + '%' : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="均收益" width="100">
        <template #default="{ row }">
          {{ row.avgReturn != null ? (Number(row.avgReturn) * 100).toFixed(2) + '%' : '-' }}
        </template>
      </el-table-column>
    </el-table>

    <h3 v-if="tradeCal?.daysList?.length">成交日历</h3>
    <el-table v-if="tradeCal?.daysList?.length" :data="tradeCal.daysList" size="small" height="220" style="margin-bottom: 12px">
      <el-table-column prop="tradeDate" label="日期" width="120" />
      <el-table-column prop="buyCount" label="买" width="60" />
      <el-table-column prop="sellCount" label="卖" width="60" />
      <el-table-column prop="turnover" label="成交额" width="120" />
      <el-table-column prop="fee" label="费用" width="90" />
      <el-table-column prop="netBuyAmount" label="净买入" width="120" />
    </el-table>

    <h3>订单</h3>
    <el-form :inline="true" style="margin-bottom: 8px">
      <el-form-item label="方向">
        <el-select v-model="orderSide" clearable style="width: 100px">
          <el-option label="BUY" value="BUY" />
          <el-option label="SELL" value="SELL" />
        </el-select>
      </el-form-item>
      <el-form-item label="近N日">
        <el-input v-model="orderDays" style="width: 80px" />
      </el-form-item>
    </el-form>
    <el-table :data="filteredOrders" size="small" height="280">
      <el-table-column prop="tradeDate" label="日期" width="120" />
      <el-table-column prop="code" label="代码" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
          <SecurityMarketBadge :security="row" />
        </template>
      </el-table-column>
      <el-table-column prop="side" label="方向" width="80" />
      <el-table-column prop="price" label="价格" width="100" />
      <el-table-column prop="quantity" label="数量" width="90" />
      <el-table-column prop="fee" label="费用" width="100" />
      <el-table-column prop="status" label="状态" width="90" />
    </el-table>
  </div>
</template>

<style scoped>
/* 共用样式见 style.css */
</style>
