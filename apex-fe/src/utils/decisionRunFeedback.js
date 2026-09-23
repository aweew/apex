import { isActiveSyncJob } from '../views/syncPolling.mjs'

export function decisionJobChanged(previous, current) {
  return !!previous && !!current && !isActiveSyncJob(current)
    && (previous.id !== current.id || previous.status !== current.status)
}

export function decisionRunFeedback(result, job) {
  if (isActiveSyncJob(job)) {
    return { type: 'info', title: '正在生成决策', detail: job.message || '任务已提交，等待执行；完成后自动更新结果。' }
  }
  const attempt = result?.latestRun
  const retained = result?.generated ? '；下方保留上次已发布结果，请核对决策日期。' : ''
  if (attempt?.status === 'RUNNING') {
    return { type: 'info', title: '正在生成个人决策', detail: (attempt.message || '正在评估候选与持仓，完成后自动更新。') + retained }
  }
  if (attempt?.status === 'FAILED') {
    return { type: 'error', title: '最近一次决策失败', detail: (attempt.message || '请查看任务详情后重试。') + retained }
  }
  if (attempt?.status === 'SUCCESS' && attempt.published === false) {
    return { type: 'warning', title: '决策已运行，结果未发布', detail: (attempt.message || '关键数据未就绪，请先到同步中心补齐数据，再重新生成。') + retained }
  }
  if (!result?.generated) {
    if (job?.status === 'FAILED' || job?.status === 'PARTIAL' || job?.status === 'CANCELLED') {
      return { type: 'error', title: '当前没有已发布决策，请检查最近任务', detail: job.message || '请到同步中心查看任务详情。' }
    }
    return { type: 'warning', title: '今日决策尚未生成', detail: job?.status === 'SUCCESS'
      ? '最近任务已结束，但当前账号尚无今日已发布结果。请查看任务详情中的数据状态及用户生成情况。'
      : '生成后会显示买入候选、卖出建议和持有结论；没有符合条件的机会也会明确说明。' }
  }
  const buys = result.buys?.length || 0
  const sells = result.sells?.length || 0
  const holds = result.holds?.length || 0
  const executableBuys = (result.buys || []).filter((row) => row.executableHint === true).length
  const blockedCounts = new Map()
  for (const row of result.buys || []) {
    if (row.executableHint === true) continue
    for (const reason of new Set(row.riskFlags || [])) {
      if (!reason.includes('禁止') && !reason.includes('不足') && !reason.includes('缺少') && !reason.includes('仅观察')) continue
      blockedCounts.set(reason, (blockedCounts.get(reason) || 0) + 1)
    }
  }
  const reasons = [...blockedCounts].sort((a, b) => b[1] - a[1]).slice(0, 3)
    .map(([reason, count]) => `${reason}（${count}只）`).join('；')
  return {
    type: buys > 0 && !executableBuys ? 'warning' : 'success',
    title: buys > 0 && !executableBuys ? `已评估 ${buys} 只候选，当前均需继续观察`
      : buys + sells + holds ? '决策结果已发布' : '决策已完成，暂无符合条件的操作',
    detail: `买入候选 ${buys} · 卖出 ${sells} · 持有 ${holds} · 通过开仓风控 ${executableBuys}。${reasons || result.message || '请结合数据状态和风控条件查看下方清单。'}`,
  }
}
