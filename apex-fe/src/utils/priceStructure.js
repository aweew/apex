const MIN_SAMPLE_SIZE = 20
const DEFAULT_TURNOVER = 0.03

function finiteNumber(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function roundPrice(value, step) {
  const digits = step < 0.01 ? 3 : 2
  return Number(value.toFixed(digits))
}

function movingAverage(closes, period, endIndex = closes.length - 1) {
  if (endIndex + 1 < period) return null
  let sum = 0
  for (let i = endIndex - period + 1; i <= endIndex; i++) sum += closes[i]
  return sum / period
}

function quantilePrice(distribution, target) {
  let cumulative = 0
  for (const row of distribution) {
    cumulative += row.weight
    if (cumulative >= target) return row.price
  }
  return distribution.at(-1)?.price ?? null
}

function strengthOf(value, maxValue) {
  const ratio = maxValue > 0 ? value / maxValue : 0
  if (ratio >= 0.72) return { key: 'strong', label: '强' }
  if (ratio >= 0.45) return { key: 'medium', label: '中' }
  return { key: 'watch', label: '观察' }
}

function buildPeaks(distribution, step, currentPrice) {
  const smooth = distribution.map((row, index) => {
    const prev = distribution[index - 1]?.weight ?? row.weight
    const next = distribution[index + 1]?.weight ?? row.weight
    return prev * 0.25 + row.weight * 0.5 + next * 0.25
  })
  const maxValue = Math.max(...smooth)
  const candidates = []
  for (let i = 0; i < smooth.length; i++) {
    const prev = smooth[i - 1] ?? -Infinity
    const next = smooth[i + 1] ?? -Infinity
    if (smooth[i] >= prev && smooth[i] >= next && smooth[i] >= maxValue * 0.16) {
      candidates.push({ index: i, density: smooth[i] })
    }
  }
  if (!candidates.length && smooth.length) {
    const index = smooth.indexOf(maxValue)
    candidates.push({ index, density: maxValue })
  }

  const selected = []
  for (const candidate of candidates.sort((a, b) => b.density - a.density)) {
    if (selected.some((item) => Math.abs(item.index - candidate.index) < 4)) continue
    selected.push(candidate)
    if (selected.length >= 8) break
  }

  return selected.map(({ index, density }) => {
    const from = Math.max(0, index - 2)
    const to = Math.min(distribution.length - 1, index + 2)
    let zoneWeight = 0
    let weightedPrice = 0
    for (let i = from; i <= to; i++) {
      zoneWeight += distribution[i].weight
      weightedPrice += distribution[i].price * distribution[i].weight
    }
    const price = zoneWeight > 0 ? weightedPrice / zoneWeight : distribution[index].price
    const distancePct = currentPrice > 0 ? ((price - currentPrice) / currentPrice) * 100 : 0
    return {
      price: roundPrice(price, step),
      rangeLow: roundPrice(distribution[from].price, step),
      rangeHigh: roundPrice(distribution[to].price, step),
      densityPct: Number((zoneWeight * 100).toFixed(2)),
      distancePct: Number(distancePct.toFixed(2)),
      strength: strengthOf(density, maxValue),
      score: density / (1 + Math.abs(distancePct) / 12),
    }
  })
}

function pickLevel(peaks, side) {
  const rows = peaks.filter((row) => (side === 'support' ? row.distancePct < -0.05 : row.distancePct > 0.05))
  return rows.sort((a, b) => b.score - a.score)[0] || null
}

function buildTrend(closes, movingAverages) {
  const { MA5, MA10, MA20 } = movingAverages
  const previousMa20 = movingAverage(closes, 20, closes.length - 6)
  const ma20SlopePct = MA20 && previousMa20 ? ((MA20 - previousMa20) / previousMa20) * 100 : 0
  if (MA5 > MA10 && MA10 > MA20 && ma20SlopePct > 0.15) {
    return { key: 'bullish', label: '偏强', description: '均线多头排列，优先观察回踩支撑', ma20SlopePct: Number(ma20SlopePct.toFixed(2)) }
  }
  if (MA5 < MA10 && MA10 < MA20 && ma20SlopePct < -0.15) {
    return { key: 'bearish', label: '偏弱', description: '均线空头排列，反弹压力更值得重视', ma20SlopePct: Number(ma20SlopePct.toFixed(2)) }
  }
  return { key: 'sideways', label: '震荡', description: '均线未形成一致方向，支撑压力均需确认', ma20SlopePct: Number(ma20SlopePct.toFixed(2)) }
}

function buildSwingResistance(bars, currentPrice, step) {
  const pivotWindow = 3
  const startIndex = Math.max(pivotWindow, bars.length - 120)
  let resistance = null
  for (let index = startIndex; index < bars.length - pivotWindow; index++) {
    const price = bars[index].high
    if (price <= currentPrice * 1.003) continue
    let confirmedHigh = true
    for (let offset = 1; offset <= pivotWindow; offset++) {
      if (price < bars[index - offset].high || price <= bars[index + offset].high) {
        confirmedHigh = false
        break
      }
    }
    if (!confirmedHigh) continue
    const distancePct = ((price - currentPrice) / currentPrice) * 100
    if (resistance && resistance.distancePct <= distancePct) continue
    const roundedPrice = roundPrice(price, step)
    resistance = {
      price: roundedPrice,
      rangeLow: roundedPrice,
      rangeHigh: roundedPrice,
      distancePct: Number(distancePct.toFixed(2)),
      strength: { key: 'watch', label: '结构' },
      source: 'swing-high',
    }
  }
  return resistance
}

export function buildPriceLevelMarkLines(structure, compact = false) {
  if (!structure?.ready) return []
  const rows = []
  const addLevel = (level, type) => {
    if (!level || !Number.isFinite(Number(level.price))) return
    const support = type === 'support'
    const price = Number(level.price)
    const label = compact ? (support ? '支' : '阻') : (support ? '支撑' : '关键阻力')
    const color = support ? '#1f8f48' : '#d92d20'
    rows.push({
      yAxis: price,
      label: {
        show: !compact,
        formatter: `${label} ${price.toFixed(2)}`,
        position: 'end',
        distance: compact ? 4 : 7,
        offset: [0, support ? 8 : -8],
        color,
        fontSize: compact ? 9 : 10,
        fontWeight: 600,
        backgroundColor: 'rgba(255,255,255,0.94)',
        borderColor: support ? 'rgba(52,199,89,0.28)' : 'rgba(255,59,48,0.28)',
        borderWidth: 1,
        borderRadius: 3,
        padding: [2, 4],
      },
      lineStyle: {
        color: support ? 'rgba(52,199,89,0.88)' : 'rgba(255,59,48,0.88)',
        type: 'dashed',
        width: 1.2,
      },
    })
  }
  addLevel(structure.support, 'support')
  addLevel(structure.keyResistance || structure.resistance, 'resistance')
  return rows
}

export function analyzePriceStructure(inputBars, latestPrice, options = {}) {
  const bars = (inputBars || [])
    .map((bar) => ({
      ...bar,
      open: finiteNumber(bar.openPrice),
      high: finiteNumber(bar.highPrice),
      low: finiteNumber(bar.lowPrice),
      close: finiteNumber(bar.closePrice),
      turnover: finiteNumber(bar.turnoverRate),
    }))
    .filter((bar) => bar.high > 0 && bar.low > 0 && bar.close > 0 && bar.high >= bar.low)

  const currentPrice = finiteNumber(latestPrice) || bars.at(-1)?.close || null
  if (bars.length < MIN_SAMPLE_SIZE || !currentPrice) {
    return { ready: false, sampleSize: bars.length, minimumSampleSize: MIN_SAMPLE_SIZE, currentPrice }
  }

  const binCount = clamp(Number(options.binCount) || 72, 40, 120)
  const minPrice = Math.min(currentPrice, ...bars.map((bar) => bar.low))
  const maxPrice = Math.max(currentPrice, ...bars.map((bar) => bar.high))
  const rawSpan = maxPrice - minPrice
  const step = rawSpan > 0 ? rawSpan / (binCount - 1) : Math.max(currentPrice * 0.002, 0.01)
  const weights = new Array(binCount).fill(0)
  let actualTurnoverBars = 0

  for (const bar of bars) {
    const hasTurnover = bar.turnover != null && bar.turnover >= 0
    if (hasTurnover) actualTurnoverBars += 1
    const turnover = hasTurnover ? clamp(bar.turnover / 100, 0, 1) : DEFAULT_TURNOVER
    for (let i = 0; i < weights.length; i++) weights[i] *= 1 - turnover
    if (turnover === 0) continue

    const lowIndex = clamp(Math.floor((bar.low - minPrice) / step), 0, binCount - 1)
    const highIndex = clamp(Math.ceil((bar.high - minPrice) / step), 0, binCount - 1)
    const typicalPrice = (bar.high + bar.low + bar.close) / 3
    const halfRange = Math.max(bar.high - bar.low, step)
    let shapeSum = 0
    const shapes = []
    for (let i = lowIndex; i <= highIndex; i++) {
      const price = minPrice + i * step
      const shape = Math.max(0.08, 1 - Math.abs(price - typicalPrice) / halfRange)
      shapes.push([i, shape])
      shapeSum += shape
    }
    for (const [index, shape] of shapes) weights[index] += turnover * (shape / shapeSum)
  }

  const totalWeight = weights.reduce((sum, value) => sum + value, 0)
  const distribution = weights.map((weight, index) => ({
    price: roundPrice(minPrice + index * step, step),
    weight: totalWeight > 0 ? weight / totalWeight : 0,
    percent: totalWeight > 0 ? Number(((weight / totalWeight) * 100).toFixed(4)) : 0,
  }))
  const averageCost = distribution.reduce((sum, row) => sum + row.price * row.weight, 0)
  const profitRatio = distribution
    .filter((row) => row.price <= currentPrice)
    .reduce((sum, row) => sum + row.weight, 0)
  const peaks = buildPeaks(distribution, step, currentPrice)
  const resistance = pickLevel(peaks, 'resistance')
  const keyResistance = resistance
    ? { ...resistance, source: 'chip-peak' }
    : buildSwingResistance(bars, currentPrice, step)
  const closes = bars.map((bar) => bar.close)
  const movingAverages = Object.fromEntries(
    [5, 10, 20, 60].map((period) => {
      const value = movingAverage(closes, period)
      return [`MA${period}`, value == null ? null : roundPrice(value, step)]
    }),
  )
  const dynamicLevels = Object.entries(movingAverages)
    .filter(([, value]) => value != null)
    .map(([name, price]) => ({
      name,
      price,
      distancePct: Number((((price - currentPrice) / currentPrice) * 100).toFixed(2)),
      side: price <= currentPrice ? 'support' : 'resistance',
    }))
    .sort((a, b) => Math.abs(a.distancePct) - Math.abs(b.distancePct))

  return {
    ready: true,
    sampleSize: bars.length,
    asOfDate: bars.at(-1)?.tradeDate || '',
    currentPrice: roundPrice(currentPrice, step),
    averageCost: roundPrice(averageCost, step),
    profitRatioPct: Number((profitRatio * 100).toFixed(1)),
    concentration70: {
      low: roundPrice(quantilePrice(distribution, 0.15), step),
      high: roundPrice(quantilePrice(distribution, 0.85), step),
    },
    concentration90: {
      low: roundPrice(quantilePrice(distribution, 0.05), step),
      high: roundPrice(quantilePrice(distribution, 0.95), step),
    },
    distribution,
    peaks: peaks.sort((a, b) => b.densityPct - a.densityPct),
    support: pickLevel(peaks, 'support'),
    resistance,
    keyResistance,
    movingAverages,
    dynamicLevels,
    trend: buildTrend(closes, movingAverages),
    quality: {
      actualTurnoverBars,
      actualTurnoverRatioPct: Number(((actualTurnoverBars / bars.length) * 100).toFixed(0)),
      usedFallbackTurnover: actualTurnoverBars < bars.length,
    },
  }
}
