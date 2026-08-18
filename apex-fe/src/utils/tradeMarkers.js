function tradeDateOf(record) {
  const value = record?.tradeTime || record?.tradeDate || ''
  return String(value).slice(0, 10)
}

function markerLabel(record) {
  const portfolioName = String(record?.portfolioName || '').trim()
  const ownerLabel = String(record?.ownerLabel || '').trim()
  return Array.from(portfolioName || ownerLabel || '我').slice(0, 3).join('')
}

function mappedBarIndex(tradeDate, bars) {
  for (let index = 0; index < bars.length; index += 1) {
    const barDate = String(bars[index]?.tradeDate || '').slice(0, 10)
    const previousDate = index > 0 ? String(bars[index - 1]?.tradeDate || '').slice(0, 10) : ''
    if (tradeDate === barDate) return index
    if (index > 0 && tradeDate > previousDate && tradeDate < barDate) return index
  }
  return -1
}

function groupPrice(records, bar, side) {
  let weightedAmount = 0
  let totalWeight = 0
  for (const record of records) {
    const price = Number(record?.price)
    if (!Number.isFinite(price) || price <= 0) continue
    const quantity = Number(record?.quantity)
    const weight = Number.isFinite(quantity) && quantity > 0 ? quantity : 1
    weightedAmount += price * weight
    totalWeight += weight
  }
  if (totalWeight > 0) return Number((weightedAmount / totalWeight).toFixed(4))
  const fallback = side === 'BUY' ? Number(bar?.lowPrice) : Number(bar?.highPrice)
  if (Number.isFinite(fallback)) return fallback
  return Number(bar?.closePrice) || 0
}

export function buildTradeMarkerSeries(records = [], chartBars = []) {
  const bars = Array.isArray(chartBars) ? chartBars : []
  const groups = new Map()
  for (const record of Array.isArray(records) ? records : []) {
    const side = String(record?.side || '').toUpperCase()
    if (side !== 'BUY' && side !== 'SELL') continue
    const tradeDate = tradeDateOf(record)
    const barIndex = mappedBarIndex(tradeDate, bars)
    if (barIndex < 0) continue
    const barDate = String(bars[barIndex]?.tradeDate || '').slice(0, 10)
    const key = `${barDate}:${side}`
    if (!groups.has(key)) groups.set(key, { side, barIndex, records: [] })
    groups.get(key).records.push(record)
  }

  const result = { buy: [], sell: [] }
  for (const group of groups.values()) {
    const bar = bars[group.barIndex]
    const first = group.records[0]
    const prefix = group.side === 'BUY' ? 'B' : 'S'
    const extra = group.records.length > 1 ? ` +${group.records.length - 1}` : ''
    const marker = {
      value: [String(bar?.tradeDate || '').slice(0, 10), groupPrice(group.records, bar, group.side)],
      labelText: `${prefix} ${markerLabel(first)}${extra}`,
      records: group.records,
    }
    if (group.side === 'BUY') result.buy.push(marker)
    else result.sell.push(marker)
  }
  return result
}
