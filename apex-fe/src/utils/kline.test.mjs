import test from 'node:test'
import assert from 'node:assert/strict'
import {
  aggregateBars,
  defaultVisibleStart,
  detectMacdDivergences,
  nextKlineZoomRange,
  periodBucket,
  pinchKlineZoomRange,
  spaceChartSignals,
  tdSequential,
  visibleBarCount,
} from './kline.js'

test('detects confirmed MACD bearish divergence at a higher price high', () => {
  const highs = [10, 12, 10, 11, 13, 11, 10]
  const lows = [9, 10, 9, 10, 11, 10, 9]
  const closes = [9.5, 11.8, 9.8, 10.7, 12.8, 10.8, 9.6]
  const dif = [0.2, 0.8, 0.3, 0.4, 0.5, 0.2, 0.1]

  const signals = detectMacdDivergences(highs, lows, closes, dif, {
    pivotWindow: 1,
    minimumPivotGap: 2,
  })

  assert.equal(signals[4].type, 'bearish')
  assert.equal(signals[4].previousIndex, 1)
  assert.equal(signals.filter(Boolean).length, 1)
})

test('detects confirmed MACD bullish divergence at a lower price low', () => {
  const highs = [11, 10, 11, 10, 9, 10, 11]
  const lows = [10, 8, 10, 9, 7, 9, 10]
  const closes = [10.5, 8.2, 10.2, 9.3, 7.2, 9.2, 10.4]
  const dif = [-0.2, -0.8, -0.3, -0.4, -0.5, -0.2, -0.1]

  const signals = detectMacdDivergences(highs, lows, closes, dif, {
    pivotWindow: 1,
    minimumPivotGap: 2,
  })

  assert.equal(signals[4].type, 'bullish')
  assert.equal(signals[4].previousIndex, 1)
  assert.equal(signals.filter(Boolean).length, 1)
})

test('does not mark an unconfirmed divergence at the latest bar', () => {
  const highs = [10, 12, 10, 11, 13]
  const lows = [9, 10, 9, 10, 11]
  const closes = [9.5, 11.8, 9.8, 10.7, 12.8]
  const dif = [0.2, 0.8, 0.3, 0.4, 0.5]

  const signals = detectMacdDivergences(highs, lows, closes, dif, {
    pivotWindow: 1,
    minimumPivotGap: 2,
  })

  assert.equal(signals.filter(Boolean).length, 0)
})

test('does not treat zero-axis mismatches as MACD divergence', () => {
  const highs = [10, 12, 10, 11, 13, 11, 10]
  const lows = [9, 10, 9, 10, 11, 10, 9]
  const closes = [9.5, 11.8, 9.8, 10.7, 12.8, 10.8, 9.6]
  const dif = [0.2, 0.8, 0.3, 0.4, -0.5, -0.2, -0.1]

  const signals = detectMacdDivergences(highs, lows, closes, dif, {
    pivotWindow: 1,
    minimumPivotGap: 2,
  })

  assert.equal(signals.filter(Boolean).length, 0)
})

test('uses the final bar of an equal-price pivot as the confirmed divergence point', () => {
  const highs = [10, 12, 12, 10, 11, 13, 13, 11, 10]
  const lows = [9, 10, 10, 9, 10, 11, 11, 10, 9]
  const closes = [9.5, 11.7, 11.8, 9.8, 10.7, 12.7, 12.8, 10.8, 9.6]
  const dif = [0.2, 0.82, 0.8, 0.3, 0.4, 0.52, 0.5, 0.2, 0.1]

  const signals = detectMacdDivergences(highs, lows, closes, dif, {
    pivotWindow: 1,
    minimumPivotGap: 2,
  })

  assert.equal(signals[6].type, 'bearish')
  assert.equal(signals[6].previousIndex, 2)
})

test('defaultVisibleStart keeps roughly three months of daily bars visible', () => {
  assert.equal(defaultVisibleStart(40), 0)
  assert.equal(defaultVisibleStart(60), 0)
  assert.equal(defaultVisibleStart(120), 50)
  assert.equal(defaultVisibleStart(500), 88)
})

test('K-line zoom keeps the latest bar anchored and changes visible bars by one level', () => {
  assert.equal(visibleBarCount(100, 40, 100), 60)
  assert.deepEqual(nextKlineZoomRange(100, 40, 100, 'in'), { start: 52, end: 100 })
  assert.deepEqual(nextKlineZoomRange(100, 40, 100, 'out'), { start: 25, end: 100 })
})

test('K-line zoom uses the current center away from either data edge', () => {
  assert.deepEqual(nextKlineZoomRange(100, 20, 80, 'in'), { start: 26, end: 74 })
  assert.deepEqual(nextKlineZoomRange(100, 20, 80, 'out'), { start: 12.5, end: 87.5 })
})

test('K-line zoom stops at twelve bars and the full data range', () => {
  assert.deepEqual(nextKlineZoomRange(100, 88, 100, 'in'), { start: 88, end: 100 })
  assert.deepEqual(nextKlineZoomRange(100, 94, 100, 'in'), { start: 94, end: 100 })
  assert.deepEqual(nextKlineZoomRange(100, 0, 100, 'out'), { start: 0, end: 100 })
})

test('pinch zoom uses damped scaling around the gesture anchor', () => {
  const zoomedIn = pinchKlineZoomRange(500, 88, 100, 2, 0.5, 12)
  const directScaleWidth = (100 - 88) / 2

  assert.ok(zoomedIn.end - zoomedIn.start > directScaleWidth)
  assert.ok(Math.abs((zoomedIn.start + zoomedIn.end) / 2 - 94) < 0.01)

  const zoomedOut = pinchKlineZoomRange(500, 88, 100, 0.5, 0.5, 12)
  assert.ok(zoomedOut.end - zoomedOut.start < 24)
  assert.ok(zoomedOut.end - zoomedOut.start > 12)
})

test('pinch zoom respects the minimum visible bars and chart boundaries', () => {
  const minimumWindow = pinchKlineZoomRange(500, 88, 100, 100, 0.5, 12)
  assert.equal(visibleBarCount(500, minimumWindow.start, minimumWindow.end), 12)

  const leftBoundary = pinchKlineZoomRange(500, 0, 12, 0.5, 0, 12)
  assert.equal(leftBoundary.start, 0)
  assert.ok(leftBoundary.end <= 100)
})

test('dense chart signals keep the latest marker and leave enough K-line spacing', () => {
  const signals = new Array(20).fill(null)
  signals[2] = 'golden'
  signals[5] = 'death'
  signals[8] = 'golden'
  signals[15] = 'death'
  signals[18] = 'golden'

  const spacedSignals = spaceChartSignals(signals, 5)

  assert.deepEqual(
    spacedSignals.map((signal, index) => (signal ? [index, signal] : null)).filter(Boolean),
    [[2, 'golden'], [8, 'golden'], [18, 'golden']],
  )
  assert.equal(signals[15], 'death')
})

test('periodBucket week uses Monday', () => {
  // 2026-03-13 Friday -> week of 2026-03-09 Monday
  assert.equal(periodBucket('2026-03-13', 'week'), '2026-03-09')
  assert.equal(periodBucket('2026-03-01', 'month'), '2026-03')
  assert.equal(periodBucket('2026-03-13', 'day'), '2026-03-13')
})

test('aggregateBars week OHLC', () => {
  const daily = [
    { tradeDate: '2026-03-09', openPrice: 10, highPrice: 11, lowPrice: 9.5, closePrice: 10.5, volume: 100, amount: 1000, turnoverRate: 1.1 },
    { tradeDate: '2026-03-10', openPrice: 10.5, highPrice: 12, lowPrice: 10, closePrice: 11.5, volume: 200, amount: 2000, turnoverRate: 2.2 },
    { tradeDate: '2026-03-16', openPrice: 11, highPrice: 11.2, lowPrice: 10.8, closePrice: 11.1, volume: 50, amount: 500, turnoverRate: 3.3 },
  ]
  const weekly = aggregateBars(daily, 'week')
  assert.equal(weekly.length, 2)
  assert.equal(weekly[0].openPrice, 10)
  assert.equal(weekly[0].closePrice, 11.5)
  assert.equal(weekly[0].highPrice, 12)
  assert.equal(weekly[0].lowPrice, 9.5)
  assert.equal(weekly[0].volume, 300)
  assert.equal(weekly[0].turnoverRate, 2.2)
  assert.equal(weekly[1].tradeDate, '2026-03-16')
  assert.equal(weekly[1].turnoverRate, 3.3)
})

test('tdSequential marks sell 9 after rising setup', () => {
  // Need close[i] > close[i-4] for 9 bars in a row
  const closes = []
  for (let i = 0; i < 20; i++) closes.push(100 + i)
  const { sell, buy } = tdSequential(closes)
  assert.ok(sell.includes(9))
  assert.equal(Math.max(...buy), 0)
  const nineIdx = sell.indexOf(9)
  assert.equal(sell[nineIdx], 9)
})

test('tdSequential marks buy 9 after falling setup', () => {
  const closes = []
  for (let i = 0; i < 20; i++) closes.push(100 - i)
  const { sell, buy } = tdSequential(closes)
  assert.ok(buy.includes(9))
  assert.equal(Math.max(...sell), 0)
})
