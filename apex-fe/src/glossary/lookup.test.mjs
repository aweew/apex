import test from 'node:test'
import assert from 'node:assert/strict'
import { findTerm, searchTerms, allTerms } from './lookup.js'
import { getDiagramSvg, listDiagramIds } from './diagrams.js'

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

test('findTerm resolves strategy ids to dedicated entries', () => {
  assert.equal(findTerm('S1')?.id, 's1_ma_trend')
  assert.equal(findTerm('S2')?.id, 's2_rsi_pullback')
  assert.equal(findTerm('主线')?.id, 'mainline')
  assert.equal(findTerm('赚钱效应')?.id, 'money_effect')
  assert.equal(findTerm('PEG')?.id, 'peg')
})

test('PE variants resolve to dedicated glossary entries', () => {
  assert.equal(findTerm('市盈率（动）')?.id, 'pe_dynamic')
  assert.equal(findTerm('市盈率（静）')?.id, 'pe_static')
  assert.equal(findTerm('市盈率（TTM）')?.id, 'pe_ttm')
  assert.equal(findTerm('动态市盈率')?.id, 'pe_dynamic')
  assert.equal(findTerm('静态市盈率')?.id, 'pe_static')
  assert.equal(findTerm('PE(TTM)')?.id, 'pe_ttm')
})

test('extra terms are searchable', () => {
  assert.equal(findTerm('stop_loss')?.title, '止损')
  assert.equal(findTerm('情绪周期')?.id, 'emotion_cycle')
  assert.equal(findTerm('安全边际')?.id, 'safety_margin')
  assert.equal(findTerm('智能决策')?.id, 'decision')
})

test('glossary term ids are unique', () => {
  const terms = allTerms()
  const ids = terms.map((t) => t.id)
  assert.equal(ids.length, new Set(ids).size)
  assert.ok(terms.length >= 110)
})

test('diagram builders return svg for keyed terms', () => {
  const ids = listDiagramIds()
  assert.ok(ids.includes('max_drawdown'))
  assert.ok(getDiagramSvg('max_drawdown').includes('<svg'))
  assert.equal(getDiagramSvg('not_exist'), '')
  assert.ok(findTerm('max_drawdown')?.diagram === 'max_drawdown')
  assert.ok(findTerm('macd')?.diagram === 'macd')
})
