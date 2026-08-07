import test from 'node:test'
import assert from 'node:assert/strict'
import { boardTagTitle, normalizeStockDigits, resolveBoardTag } from './marketBoard.js'

test('normalizeStockDigits handles suffixes', () => {
  assert.equal(normalizeStockDigits('600519.SH'), '600519')
  assert.equal(normalizeStockDigits('SZ000001'), '000001')
  assert.equal(normalizeStockDigits('01810'), '01810')
})

test('resolveBoardTag 科创板 / 创业板', () => {
  assert.equal(resolveBoardTag('688981'), '科')
  assert.equal(resolveBoardTag('689009'), '科')
  assert.equal(resolveBoardTag('688981.SH'), '科')
  assert.equal(resolveBoardTag('300750'), '创')
  assert.equal(resolveBoardTag('301308'), '创')
})

test('resolveBoardTag 京 / 港 / 美', () => {
  assert.equal(resolveBoardTag('830799'), '京')
  assert.equal(resolveBoardTag('920178'), '京')
  assert.equal(resolveBoardTag('430047'), '京')
  assert.equal(resolveBoardTag('01810'), '港')
  assert.equal(resolveBoardTag('1810'), '港')
  assert.equal(resolveBoardTag('00700.HK'), '港')
  assert.equal(resolveBoardTag('AAPL'), '美')
  assert.equal(resolveBoardTag('TSLA', 'US'), '美')
  assert.equal(resolveBoardTag('920178', 'BJ'), '京')
})

test('resolveBoardTag 主板无标签', () => {
  assert.equal(resolveBoardTag('600519'), '')
  assert.equal(resolveBoardTag('000001'), '')
  assert.equal(resolveBoardTag('002415'), '')
  assert.equal(resolveBoardTag(''), '')
})

test('boardTagTitle', () => {
  assert.equal(boardTagTitle('科'), '科创板')
  assert.equal(boardTagTitle('创'), '创业板')
  assert.equal(boardTagTitle('京'), '北交所')
  assert.equal(boardTagTitle('港'), '港股')
  assert.equal(boardTagTitle('美'), '美股')
  assert.equal(boardTagTitle(''), '')
})
