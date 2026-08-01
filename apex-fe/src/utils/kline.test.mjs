import test from 'node:test'
import assert from 'node:assert/strict'
import { aggregateBars, periodBucket, tdSequential } from './kline.js'

test('periodBucket week uses Monday', () => {
  // 2026-03-13 Friday -> week of 2026-03-09 Monday
  assert.equal(periodBucket('2026-03-13', 'week'), '2026-03-09')
  assert.equal(periodBucket('2026-03-01', 'month'), '2026-03')
  assert.equal(periodBucket('2026-03-13', 'day'), '2026-03-13')
})

test('aggregateBars week OHLC', () => {
  const daily = [
    { tradeDate: '2026-03-09', openPrice: 10, highPrice: 11, lowPrice: 9.5, closePrice: 10.5, volume: 100, amount: 1000 },
    { tradeDate: '2026-03-10', openPrice: 10.5, highPrice: 12, lowPrice: 10, closePrice: 11.5, volume: 200, amount: 2000 },
    { tradeDate: '2026-03-16', openPrice: 11, highPrice: 11.2, lowPrice: 10.8, closePrice: 11.1, volume: 50, amount: 500 },
  ]
  const weekly = aggregateBars(daily, 'week')
  assert.equal(weekly.length, 2)
  assert.equal(weekly[0].openPrice, 10)
  assert.equal(weekly[0].closePrice, 11.5)
  assert.equal(weekly[0].highPrice, 12)
  assert.equal(weekly[0].lowPrice, 9.5)
  assert.equal(weekly[0].volume, 300)
  assert.equal(weekly[1].tradeDate, '2026-03-16')
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
