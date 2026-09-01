import assert from 'node:assert/strict'
import test from 'node:test'

import { isPostMarketReportVisible } from './postMarketReportVisibility.js'

test('post-market report opens at 18:30 on a trading day and closes at the next trading open', () => {
  const mondayCalendar = {
    date: '2026-08-31',
    tradingDay: true,
    nextTradingDay: '2026-09-01',
  }

  assert.equal(isPostMarketReportVisible(new Date('2026-08-31T10:29:00Z'), mondayCalendar), false)
  assert.equal(isPostMarketReportVisible(new Date('2026-08-31T10:30:00Z'), mondayCalendar), true)
  assert.equal(isPostMarketReportVisible(new Date('2026-09-01T01:29:00Z'), mondayCalendar), true)
  assert.equal(isPostMarketReportVisible(new Date('2026-09-01T01:30:00Z'), mondayCalendar), false)
})

test('post-market report remains available through weekends until the next trading open', () => {
  const fridayCalendar = {
    date: '2026-09-04',
    tradingDay: true,
    nextTradingDay: '2026-09-07',
  }

  assert.equal(isPostMarketReportVisible(new Date('2026-09-04T10:30:00Z'), fridayCalendar), true)
  assert.equal(isPostMarketReportVisible(new Date('2026-09-06T12:00:00Z'), fridayCalendar), true)
  assert.equal(isPostMarketReportVisible(new Date('2026-09-07T01:30:00Z'), fridayCalendar), false)
})

test('post-market report stays hidden without a completed trading session', () => {
  assert.equal(isPostMarketReportVisible(new Date('2026-09-05T12:00:00Z'), null), false)
  assert.equal(isPostMarketReportVisible(new Date('2026-09-05T12:00:00Z'), { tradingDay: false }), false)
})
