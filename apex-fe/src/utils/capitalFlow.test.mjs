import assert from 'node:assert/strict'
import test from 'node:test'

import {
  formatCapitalAmount,
  formatCapitalPercent,
  formatCapitalPrice,
  resolveCapitalClass,
  sortDragonTigerItems,
  sortStockFlowItems,
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

test('capital flow colors follow the A-share direction convention', () => {
  assert.equal(resolveCapitalClass(1), 'up')
  assert.equal(resolveCapitalClass(-1), 'down')
  assert.equal(resolveCapitalClass(0), 'flat')
  assert.equal(resolveCapitalClass(null), 'flat')
})

test('dragon tiger rows support stable numeric sorting with missing values last', () => {
  const rows = [
    { code: '000001', netBuyAmount: 120, pctChg: 3 },
    { code: '000002', netBuyAmount: null, pctChg: 8 },
    { code: '000003', netBuyAmount: 360, pctChg: -2 },
    { code: '000004', netBuyAmount: 120, pctChg: 5 },
  ]

  assert.deepEqual(
    sortDragonTigerItems(rows, 'netBuyAmount', 'descending').map((item) => item.code),
    ['000003', '000001', '000004', '000002'],
  )
  assert.deepEqual(
    sortDragonTigerItems(rows, 'pctChg', 'ascending').map((item) => item.code),
    ['000003', '000001', '000004', '000002'],
  )
  assert.deepEqual(rows.map((item) => item.code), ['000001', '000002', '000003', '000004'])
})

test('stock flow rows support every business field with missing values last', () => {
  const rows = [
    { code: '000001', name: '平安银行', mainNetInflow: 120, mainNetInflowPct: 3, smallNetInflow: -20 },
    { code: '000002', name: '万科A', mainNetInflow: null, mainNetInflowPct: 8, smallNetInflow: 10 },
    { code: '000003', name: '中兴通讯', mainNetInflow: 360, mainNetInflowPct: -2, smallNetInflow: null },
    { code: '000004', name: '平安银行', mainNetInflow: 120, mainNetInflowPct: 5, smallNetInflow: 30 },
  ]

  assert.deepEqual(
    sortStockFlowItems(rows, 'mainNetInflow', 'descending').map((item) => item.code),
    ['000003', '000001', '000004', '000002'],
  )
  assert.deepEqual(
    sortStockFlowItems(rows, 'smallNetInflow', 'ascending').map((item) => item.code),
    ['000001', '000002', '000004', '000003'],
  )
  assert.deepEqual(
    sortStockFlowItems(rows, 'name', 'ascending').map((item) => item.code),
    ['000001', '000004', '000002', '000003'],
  )
  assert.deepEqual(rows.map((item) => item.code), ['000001', '000002', '000003', '000004'])
})
