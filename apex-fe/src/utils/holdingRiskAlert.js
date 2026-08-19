export const HOLDING_ALERT_TYPE = Object.freeze({
  STOP_LOSS: 'STOP_LOSS',
  TAKE_PROFIT: 'TAKE_PROFIT',
})

function positivePrice(value) {
  const price = Number(value)
  return Number.isFinite(price) && price > 0 ? price : null
}

export function detectHoldingRiskAlert(holding) {
  const marketPrice = positivePrice(holding?.marketPrice)
  if (marketPrice == null) return null

  const stopLoss = positivePrice(holding?.stopLoss)
  if (stopLoss != null && marketPrice <= stopLoss) {
    return {
      code: String(holding?.code || ''),
      type: HOLDING_ALERT_TYPE.STOP_LOSS,
      marketPrice,
      triggerPrice: stopLoss,
    }
  }

  const takeProfit = positivePrice(holding?.takeProfit)
  if (takeProfit != null && marketPrice >= takeProfit) {
    return {
      code: String(holding?.code || ''),
      type: HOLDING_ALERT_TYPE.TAKE_PROFIT,
      marketPrice,
      triggerPrice: takeProfit,
    }
  }
  return null
}

export function summarizeHoldingRiskAlerts(holdings) {
  const items = []
  for (const holding of holdings || []) {
    const alert = detectHoldingRiskAlert(holding)
    if (!alert) continue
    items.push({
      ...alert,
      name: String(holding?.name || ''),
    })
  }
  items.sort((left, right) => {
    if (left.type === right.type) return left.code.localeCompare(right.code)
    return left.type === HOLDING_ALERT_TYPE.STOP_LOSS ? -1 : 1
  })
  return {
    total: items.length,
    stopLossCount: items.filter((item) => item.type === HOLDING_ALERT_TYPE.STOP_LOSS).length,
    takeProfitCount: items.filter((item) => item.type === HOLDING_ALERT_TYPE.TAKE_PROFIT).length,
    items,
  }
}
