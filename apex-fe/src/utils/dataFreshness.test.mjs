import assert from 'node:assert/strict'
import test from 'node:test'

import {
  clearDataFreshness,
  dataFreshness,
  staleDataTime,
  publishDataFreshness,
} from './dataFreshness.js'

const tradingCalendar = {
  date: '2026-08-26',
  tradingDay: true,
  latestTradingDay: '2026-08-26',
  prevTradingDay: '2026-08-25',
}

test('published data freshness keeps an actionable label and source route', () => {
  clearDataFreshness()

  publishDataFreshness({
    level: 'YELLOW',
    label: '决策数据预警',
    detail: '行情截至 2026-08-21',
    route: '/decision',
  })

  assert.deepEqual(dataFreshness.value, {
    level: 'YELLOW',
    label: '决策数据预警',
    detail: '行情截至 2026-08-21',
    route: '/decision',
  })
})

test('clearing data freshness removes stale page context on navigation', () => {
  publishDataFreshness({ level: 'GREEN', label: '数据正常', detail: '', route: '/dashboard' })
  clearDataFreshness()

  assert.equal(dataFreshness.value, null)
})

test('daily data hides the latest completed trading day before close', () => {
  assert.equal(staleDataTime({
    tradeDate: '2026-08-25',
    updatedAt: '2026-08-25T15:08:00',
    calendar: tradingCalendar,
    now: new Date('2026-08-26T02:00:00Z'),
  }), '')
})

test('daily data requires the current trading day after the close sync window', () => {
  assert.equal(staleDataTime({
    tradeDate: '2026-08-26',
    updatedAt: '2026-08-26T15:18:00',
    calendar: tradingCalendar,
    now: new Date('2026-08-26T07:35:00Z'),
  }), '')
  assert.equal(staleDataTime({
    tradeDate: '2026-08-25',
    updatedAt: '2026-08-25T15:08:00',
    calendar: tradingCalendar,
    now: new Date('2026-08-26T07:35:00Z'),
  }), '最后同步 2026-08-25 15:08')
})

test('intraday data must use the current trading date and stay within five minutes', () => {
  assert.equal(staleDataTime({
    tradeDate: '2026-08-26',
    updatedAt: '2026-08-26T10:27:00',
    intraday: true,
    calendar: tradingCalendar,
    now: new Date('2026-08-26T02:30:00Z'),
  }), '')
  assert.equal(staleDataTime({
    tradeDate: '2026-08-26',
    updatedAt: '2026-08-26T10:20:00',
    intraday: true,
    calendar: tradingCalendar,
    now: new Date('2026-08-26T02:30:00Z'),
  }), '最后同步 2026-08-26 10:20')
})

test('intraday data keeps the session-edge quote fresh during lunch and after close', () => {
  assert.equal(staleDataTime({
    tradeDate: '2026-08-26',
    updatedAt: '2026-08-26T11:29:00',
    intraday: true,
    calendar: tradingCalendar,
    now: new Date('2026-08-26T04:10:00Z'),
  }), '')
  assert.equal(staleDataTime({
    tradeDate: '2026-08-26',
    updatedAt: '2026-08-26T14:58:00',
    intraday: true,
    calendar: tradingCalendar,
    now: new Date('2026-08-26T08:30:00Z'),
  }), '')
})

test('non-trading days use the server calendar latest trading day', () => {
  assert.equal(staleDataTime({
    tradeDate: '2026-08-28',
    updatedAt: '2026-08-28T15:10:00',
    calendar: {
      date: '2026-08-30',
      tradingDay: false,
      latestTradingDay: '2026-08-28',
      prevTradingDay: '2026-08-28',
    },
    now: new Date('2026-08-30T03:00:00Z'),
  }), '')
})

test('stale data falls back to its last data date when sync time is unavailable', () => {
  assert.equal(staleDataTime({
    tradeDate: '2026-08-24',
    calendar: tradingCalendar,
    now: new Date('2026-08-26T02:00:00Z'),
  }), '最后同步 2026-08-24')
})

test('calendar failure keeps the last sync time visible instead of assuming freshness', () => {
  assert.equal(staleDataTime({
    tradeDate: '2026-08-25',
    updatedAt: '2026-08-25T15:08:00',
    calendar: null,
    now: new Date('2026-08-26T02:00:00Z'),
  }), '最后同步 2026-08-25 15:08')
})
