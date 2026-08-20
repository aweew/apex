import assert from 'node:assert/strict'
import test from 'node:test'

import {
  formatCapitalAmount,
  formatCapitalPercent,
  formatCapitalPrice,
  formatNorthboundAmount,
  resolveCapitalClass,
} from './capitalFlow.js'

test('capital flow amounts use compact Chinese units without losing the sign', () => {
  assert.equal(formatCapitalAmount(128560000), '+1.29亿')
  assert.equal(formatCapitalAmount(-8560000), '-856.00万')
  assert.equal(formatCapitalAmount(0), '0.00')
  assert.equal(formatCapitalAmount(null), '-')
})

test('capital flow percentages keep explicit direction', () => {
  assert.equal(formatCapitalPercent(3.126), '+3.13%')
  assert.equal(formatCapitalPercent(-0.8), '-0.80%')
  assert.equal(formatCapitalPercent(null), '-')
})

test('capital flow prices keep missing values distinct from zero', () => {
  assert.equal(formatCapitalPrice(null), '-')
  assert.equal(formatCapitalPrice('12.3'), '12.30')
  assert.equal(formatCapitalPrice(0), '0.00')
})

test('northbound missing disclosure stays unavailable instead of being inferred', () => {
  assert.equal(formatNorthboundAmount(null), '-')
  assert.equal(formatNorthboundAmount(2630000000), '+26.30亿')
})

test('capital flow colors follow the A-share direction convention', () => {
  assert.equal(resolveCapitalClass(1), 'up')
  assert.equal(resolveCapitalClass(-1), 'down')
  assert.equal(resolveCapitalClass(0), 'flat')
  assert.equal(resolveCapitalClass(null), 'flat')
})
