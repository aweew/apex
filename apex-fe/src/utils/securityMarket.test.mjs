import test from 'node:test'
import assert from 'node:assert/strict'
import { securityMarketBadge } from './securityMarket.js'

test('classifies supported security market badges', () => {
  const cases = [
    ['688001', '科'],
    ['301001', '创'],
    ['920001', '京'],
    ['830001', '京'],
    ['01810', '港'],
    ['1810.HK', '港'],
    ['AAPL', '美'],
    ['BRK.B', '美'],
    ['600519', null],
    ['000001', null],
  ]

  for (const [code, expected] of cases) {
    assert.equal(securityMarketBadge({ code })?.label || null, expected, code)
  }
})

test('uses an explicit market when provided', () => {
  assert.equal(securityMarketBadge({ code: '00700', market: 'HK' })?.label, '港')
  assert.equal(securityMarketBadge({ code: 'TSLA', market: 'NASDAQ' })?.label, '美')
  assert.equal(securityMarketBadge({ code: '430001', market: 'BJ' })?.label, '京')
})

test('optionally classifies Shanghai and Shenzhen main-board securities', () => {
  assert.equal(securityMarketBadge({ code: '600519' }, { includeMain: true })?.label, '沪')
  assert.equal(securityMarketBadge({ code: '000001' }, { includeMain: true })?.label, '深')
  assert.equal(securityMarketBadge({ code: '002594', market: 'SZ' }, { includeMain: true })?.label, '深')
  assert.equal(securityMarketBadge({ code: '600519', market: 'SH' })?.label || null, null)
})
