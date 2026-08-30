import assert from 'node:assert/strict'
import test from 'node:test'

import { buildPortfolioIntradaySeries, shouldShowPortfolioIntraday } from './portfolioIntraday.js'

test('shows intraday return only when the authoritative calendar does not mark a non-trading day', () => {
  assert.equal(shouldShowPortfolioIntraday({ tradingDay: true }), true)
  assert.equal(shouldShowPortfolioIntraday({ tradingDay: false }), false)
  assert.equal(shouldShowPortfolioIntraday(undefined), true)
  assert.equal(shouldShowPortfolioIntraday(null), true)
})

test('builds intraday return curve summary from valid five-minute points', () => {
  const result = buildPortfolioIntradaySeries([
    { snapshotTime: '2026-08-21 09:30:00', todayPct: -0.2, todayPnl: -200 },
    { snapshotTime: '2026-08-21 09:35:00', todayPct: 0.4, todayPnl: 400 },
    { snapshotTime: '2026-08-21 09:40:00', todayPct: 0.1, todayPnl: 100 },
  ])

  assert.deepEqual(result.times, ['09:30', '09:35', '09:40'])
  assert.deepEqual(result.returnRates, [-0.2, 0.4, 0.1])
  assert.equal(result.latestReturnRate, 0.1)
  assert.equal(result.highestReturnRate, 0.4)
  assert.equal(result.lowestReturnRate, -0.2)
  assert.equal(result.amplitude, 0.6)
  assert.equal(result.latestPnl, 100)
})

test('drops invalid points and returns empty summary safely', () => {
  const result = buildPortfolioIntradaySeries([
    { snapshotTime: '', todayPct: 1 },
    { snapshotTime: '2026-08-21 09:30:00', todayPct: null },
  ])

  assert.deepEqual(result.times, [])
  assert.equal(result.latestReturnRate, null)
  assert.equal(result.amplitude, null)
})
