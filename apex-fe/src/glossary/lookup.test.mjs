import test from 'node:test'
import assert from 'node:assert/strict'
import { findTerm, searchTerms } from './lookup.js'

test('findTerm by id and alias', () => {
  assert.equal(findTerm('sharpe')?.title, '夏普比率')
  assert.equal(findTerm('夏普')?.id, 'sharpe')
  assert.equal(findTerm('MACD')?.id, 'macd')
})

test('searchTerms ranks exact title high', () => {
  const rows = searchTerms('最大回撤')
  assert.ok(rows.length)
  assert.equal(rows[0].id, 'max_drawdown')
})

test('searchTerms finds english fragment', () => {
  const rows = searchTerms('var')
  assert.ok(rows.some((t) => t.id === 'var95'))
})
