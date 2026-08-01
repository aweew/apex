/** 交易日归属桶：日 / 自然周(周一) / 月 */
export function periodBucket(tradeDate, period) {
  const text = String(tradeDate || '')
  if (period === 'month') return text.slice(0, 7)
  if (period === 'week') {
    const parts = text.split(/[-/]/).map(Number)
    if (parts.length < 3 || parts.some((n) => Number.isNaN(n))) return text
    const date = new Date(parts[0], parts[1] - 1, parts[2])
    const day = date.getDay()
    const diff = day === 0 ? -6 : 1 - day
    date.setDate(date.getDate() + diff)
    const y = date.getFullYear()
    const m = String(date.getMonth() + 1).padStart(2, '0')
    const d = String(date.getDate()).padStart(2, '0')
    return `${y}-${m}-${d}`
  }
  return text
}

/** 日线聚合成周/月 K */
export function aggregateBars(dailyBars, period) {
  if (!dailyBars?.length || period === 'day') return dailyBars || []
  const groups = new Map()
  for (const bar of dailyBars) {
    const key = periodBucket(bar.tradeDate, period)
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(bar)
  }
  const result = []
  for (const rows of groups.values()) {
    const first = rows[0]
    const last = rows[rows.length - 1]
    let high = -Infinity
    let low = Infinity
    let volume = 0
    let amount = 0
    let amountOk = true
    for (const row of rows) {
      high = Math.max(high, Number(row.highPrice))
      low = Math.min(low, Number(row.lowPrice))
      volume += Number(row.volume) || 0
      if (row.amount == null || row.amount === '') amountOk = false
      else amount += Number(row.amount) || 0
    }
    result.push({
      tradeDate: last.tradeDate,
      openPrice: Number(first.openPrice),
      closePrice: Number(last.closePrice),
      highPrice: high,
      lowPrice: low,
      volume,
      amount: amountOk ? amount : null,
      pctChg: null,
    })
  }
  return result
}

/**
 * 神奇九转（TD Sequential Setup）
 * 卖：收盘价连续高于 4 周期前收盘；买：连续低于 4 周期前收盘；计到 9 完成一轮
 */
export function tdSequential(closes) {
  const buy = new Array(closes.length).fill(0)
  const sell = new Array(closes.length).fill(0)
  let buyCount = 0
  let sellCount = 0
  for (let i = 4; i < closes.length; i++) {
    const cur = closes[i]
    const ref = closes[i - 4]
    if (cur == null || ref == null || Number.isNaN(cur) || Number.isNaN(ref)) {
      buyCount = 0
      sellCount = 0
      continue
    }
    if (cur < ref) {
      buyCount += 1
      sellCount = 0
      buy[i] = Math.min(buyCount, 9)
      if (buyCount >= 9) buyCount = 0
    } else if (cur > ref) {
      sellCount += 1
      buyCount = 0
      sell[i] = Math.min(sellCount, 9)
      if (sellCount >= 9) sellCount = 0
    } else {
      buyCount = 0
      sellCount = 0
    }
  }
  return { buy, sell }
}
