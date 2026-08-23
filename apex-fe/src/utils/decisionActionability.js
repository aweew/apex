function dateText(value) {
  return String(value || '').slice(0, 10)
}

export function chinaMarketDate(date = new Date()) {
  const dateParts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date)
  const dateValues = {}
  for (const part of dateParts) {
    if (part.type !== 'literal') dateValues[part.type] = part.value
  }
  return `${dateValues.year}-${dateValues.month}-${dateValues.day}`
}

export function isCurrentLiveDecision(context) {
  const actionDate = dateText(context?.actionDate)
  const currentDate = dateText(context?.currentDate) || chinaMarketDate()
  return context?.generated === true
    && context?.runMode === 'LIVE'
    && Boolean(actionDate)
    && actionDate === currentDate
}

function isHistoricalDecision(context) {
  const actionDate = dateText(context?.actionDate)
  const currentDate = dateText(context?.currentDate) || chinaMarketDate()
  return context?.runMode === 'REPLAY'
    || (Boolean(actionDate) && actionDate !== currentDate)
}

export function canPaperBuy(row, context) {
  return row?.action === 'BUY'
    && row.executableHint === true
    && (context?.dataLevel === 'GREEN' || context?.dataLevel === 'YELLOW')
    && isCurrentLiveDecision(context)
}

export function buyActionState(row, context) {
  if (!isCurrentLiveDecision(context)) {
    if (context?.runMode === 'SHADOW') return '仅观察：影子运行'
    if (isHistoricalDecision(context)) return '仅回放：非当日决策'
    return '仅观察：运行状态不完整'
  }
  if (context?.dataLevel === 'RED') return '仅观察：数据异常'
  if (row?.executableHint === true) {
    return context?.dataLevel === 'YELLOW' ? '可执行：先复核' : '可执行'
  }
  return '仅观察：风控未通过'
}

export function paperBuyBlockedReason(row, context) {
  if (!isCurrentLiveDecision(context)) {
    if (context?.runMode === 'SHADOW') return '当前为影子运行，未发布结果不允许模拟买入'
    if (isHistoricalDecision(context)) {
      return '当前为历史回放，仅供复盘，不允许模拟买入'
    }
    return '决策运行状态不完整，暂不允许模拟买入'
  }
  if (context?.dataLevel === 'RED') return '市场数据异常，暂不允许按该建议模拟买入'
  if (row?.entryGatePassed === false) return '市场或板块开仓门禁未通过，请继续观察'
  return '该建议未通过可执行风控，只能加入观察池跟踪'
}
