import test from 'node:test'
import assert from 'node:assert/strict'
import { analyzePriceStructure } from './priceStructure.js'

function makeBars(prices, turnoverRate = 8) {
  return prices.map((close, index) => ({
    tradeDate: `2026-07-${String(index + 1).padStart(2, '0')}`,
    openPrice: close - 0.2,
    highPrice: close + 0.5,
    lowPrice: close - 0.5,
    closePrice: close,
    volume: 100000 + index * 1000,
    turnoverRate,
  }))
}

test('returns insufficient result when fewer than 20 valid bars exist', () => {
  const result = analyzePriceStructure(makeBars(new Array(12).fill(10)), 10)
  assert.equal(result.ready, false)
  assert.equal(result.sampleSize, 12)
})

test('normalizes chip distribution and exposes a cost peak as support below price', () => {
  const prices = [...new Array(35).fill(10), ...new Array(10).fill(10.3)]
  const result = analyzePriceStructure(makeBars(prices), 12)
  assert.equal(result.ready, true)
  assert.ok(Math.abs(result.distribution.reduce((sum, row) => sum + row.percent, 0) - 100) < 0.02)
  assert.ok(result.support)
  assert.ok(result.support.price < 12)
  assert.ok(result.averageCost > 9.5 && result.averageCost < 10.8)
  assert.ok(result.distribution.at(-1).price >= 12)
})

test('same dense cost area becomes resistance when current price is below it', () => {
  const result = analyzePriceStructure(makeBars(new Array(45).fill(10)), 8)
  assert.equal(result.ready, true)
  assert.ok(result.resistance)
  assert.ok(result.resistance.price > 8)
})

test('recent high-turnover trading shifts average cost toward recent prices', () => {
  const oldBars = makeBars(new Array(30).fill(10), 2)
  const recentBars = makeBars(new Array(20).fill(20), 35)
  const result = analyzePriceStructure([...oldBars, ...recentBars], 20)
  assert.ok(result.averageCost > 17)
})

test('detects bullish moving-average alignment and rising MA20', () => {
  const prices = Array.from({ length: 80 }, (_, i) => 10 + i * 0.12)
  const result = analyzePriceStructure(makeBars(prices), prices.at(-1))
  assert.equal(result.trend.key, 'bullish')
  assert.ok(result.movingAverages.MA5 > result.movingAverages.MA20)
  assert.ok(result.trend.ma20SlopePct > 0)
})

test('zero-turnover bars do not move the existing chip distribution', () => {
  const traded = makeBars(new Array(30).fill(10), 8)
  const suspended = makeBars(new Array(10).fill(20), 0)
  const result = analyzePriceStructure([...traded, ...suspended], 20)
  assert.ok(result.averageCost < 12)
})
