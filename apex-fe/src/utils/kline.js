/** 默认展示最近约三个月的日 K；数据不足时展示全部 */
export function defaultVisibleStart(barCount, visibleBars = 60) {
  const count = Number(barCount)
  if (!Number.isFinite(count) || count <= visibleBars) return 0
  return Number((((count - visibleBars) / count) * 100).toFixed(2))
}

/** 根据 dataZoom 百分比计算当前可见 K 线根数 */
export function visibleBarCount(barCount, startPct, endPct) {
  const count = Number(barCount)
  if (!Number.isFinite(count) || count <= 0) return 0
  const start = Math.max(0, Math.min(100, Number(startPct) || 0))
  const end = Math.max(start, Math.min(100, Number(endPct) || 0))
  return Math.max(1, Math.min(count, Math.round((count * (end - start)) / 100)))
}

/** 从最新信号向前保留标记，避免相邻 K 线上的文字与图标重叠 */
export function spaceChartSignals(signals, minimumBarGap = 5) {
  if (!Array.isArray(signals) || signals.length === 0) return []
  const barGap = Math.max(1, Number(minimumBarGap) || 5)
  const spacedSignals = new Array(signals.length).fill(null)
  let latestMarkerIndex = signals.length + barGap
  for (let i = signals.length - 1; i >= 0; i--) {
    if (!signals[i] || latestMarkerIndex - i < barGap) continue
    spacedSignals[i] = signals[i]
    latestMarkerIndex = i
  }
  return spacedSignals
}

/**
 * 计算按钮缩放后的 dataZoom 窗口。
 * 靠近最新行情时固定右边界，查看历史区间时以当前窗口中心为锚点。
 */
export function nextKlineZoomRange(
  barCount,
  startPct,
  endPct,
  direction,
  minVisibleBars = 12,
) {
  const count = Number(barCount)
  if (!Number.isFinite(count) || count <= 0) return { start: 0, end: 100 }
  const start = Math.max(0, Math.min(100, Number(startPct) || 0))
  const end = Math.max(start, Math.min(100, Number(endPct) || 0))
  const currentBars = visibleBarCount(count, start, end)
  const minimumBars = Math.min(count, Math.max(1, Number(minVisibleBars) || 12))
  let nextBars = currentBars
  if (direction === 'in' && currentBars > minimumBars) {
    nextBars = Math.max(minimumBars, Math.round(currentBars * 0.8))
  }
  if (direction === 'out') nextBars = Math.min(count, Math.round(currentBars / 0.8))
  if (nextBars === currentBars) return { start, end }

  const nextWidth = (nextBars / count) * 100
  let nextStart
  let nextEnd
  if (end >= 99.5) {
    nextEnd = 100
    nextStart = 100 - nextWidth
  } else if (start <= 0.5) {
    nextStart = 0
    nextEnd = nextWidth
  } else {
    const center = (start + end) / 2
    nextStart = center - nextWidth / 2
    nextEnd = center + nextWidth / 2
    if (nextStart < 0) {
      nextEnd -= nextStart
      nextStart = 0
    }
    if (nextEnd > 100) {
      nextStart -= nextEnd - 100
      nextEnd = 100
    }
  }
  return {
    start: Number(Math.max(0, nextStart).toFixed(2)),
    end: Number(Math.min(100, nextEnd).toFixed(2)),
  }
}

/** 根据双指起始距离计算阻尼缩放窗口，并固定手势开始时的中心锚点。 */
export function pinchKlineZoomRange(
  barCount,
  startPct,
  endPct,
  pinchScale,
  anchorRatio = 0.5,
  minVisibleBars = 12,
  sensitivity = 0.45,
) {
  const count = Number(barCount)
  if (!Number.isFinite(count) || count <= 0) return { start: 0, end: 100 }
  const start = Math.max(0, Math.min(100, Number(startPct) || 0))
  const end = Math.max(start, Math.min(100, Number(endPct) || 0))
  const scale = Math.max(0.1, Math.min(100, Number(pinchScale) || 1))
  const anchor = Math.max(0, Math.min(1, Number(anchorRatio) || 0))
  const minimumBars = Math.min(count, Math.max(1, Number(minVisibleBars) || 12))
  const minimumWidth = (minimumBars / count) * 100
  const currentWidth = end - start
  const nextWidth = Math.max(
    minimumWidth,
    Math.min(100, currentWidth / Math.pow(scale, sensitivity)),
  )
  const anchorValue = start + currentWidth * anchor
  let nextStart = anchorValue - nextWidth * anchor
  let nextEnd = nextStart + nextWidth
  if (nextStart < 0) {
    nextEnd -= nextStart
    nextStart = 0
  }
  if (nextEnd > 100) {
    nextStart -= nextEnd - 100
    nextEnd = 100
  }
  return {
    start: Number(Math.max(0, nextStart).toFixed(3)),
    end: Number(Math.min(100, nextEnd).toFixed(3)),
  }
}

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
      // 周/月 K 展示周期末日换手（与常见行情软件一致）
      turnoverRate: last.turnoverRate != null && last.turnoverRate !== '' ? Number(last.turnoverRate) : null,
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
