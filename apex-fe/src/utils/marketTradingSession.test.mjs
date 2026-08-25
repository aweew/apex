import assert from 'node:assert/strict'
import test from 'node:test'
import { isMarketOpen, resolveActiveMarket } from './marketTradingSession.js'

test('active market prioritizes A shares during overlapping Asian trading hours', () => {
  assert.equal(resolveActiveMarket(new Date('2026-08-24T01:40:00Z')), 'cn')
})

test('active market follows the requested overseas priority outside A-share hours', () => {
  assert.equal(resolveActiveMarket(new Date('2026-08-24T00:30:00Z')), 'jp')
  assert.equal(resolveActiveMarket(new Date('2026-08-24T03:40:00Z')), 'hk')
  assert.equal(resolveActiveMarket(new Date('2026-08-24T07:30:00Z')), 'hk')
})

test('active market retains Korea session detection and handles New York daylight saving time', () => {
  assert.equal(isMarketOpen('kr', new Date('2026-08-24T02:45:00Z')), true)
  assert.equal(resolveActiveMarket(new Date('2026-08-24T02:45:00Z')), 'cn')
  assert.equal(resolveActiveMarket(new Date('2026-08-24T14:00:00Z')), 'us')
})

test('active market is empty outside trading sessions and on weekends', () => {
  assert.equal(resolveActiveMarket(new Date('2026-08-24T08:00:00Z')), null)
  assert.equal(resolveActiveMarket(new Date('2026-08-23T01:40:00Z')), null)
})
