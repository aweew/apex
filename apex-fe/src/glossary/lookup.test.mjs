import test from 'node:test'
import assert from 'node:assert/strict'
import {
  allTerms,
  findTerm,
  getRelatedTerms,
  searchTerms,
  splitHighlightedText,
} from './lookup.js'
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
  assert.equal(findTerm('ADX')?.id, 'dmi_adx')
  assert.equal(findTerm('CCI')?.id, 'cci')
  assert.equal(findTerm('WR')?.id, 'williams_r')
  assert.equal(findTerm('SAR')?.id, 'sar')
  assert.equal(findTerm('OBV')?.id, 'obv')
  assert.equal(findTerm('MFI')?.id, 'mfi')
  assert.equal(findTerm('情绪周期')?.id, 'emotion_cycle')
  assert.equal(findTerm('安全边际')?.id, 'safety_margin')
  assert.equal(findTerm('智能决策')?.id, 'decision')
  assert.equal(findTerm('筹码峰')?.id, 'chip_distribution')
})

test('new technical indicators provide plain explanations', () => {
  for (const termId of ['dmi_adx', 'cci', 'williams_r', 'sar', 'obv', 'mfi']) {
    const term = findTerm(termId)
    assert.ok(term?.plain, `${termId} should provide a plain explanation`)
    assert.ok(term?.highlights?.length, `${termId} should highlight the plain explanation`)
  }
})

test('common stock terms stay available even when they are basic', () => {
  const expectedTerms = {
    总市值: 'total_mv',
    市值: 'total_mv',
    换手率: 'stock_turnover',
    振幅: 'amplitude',
    指数: 'market_index',
    板块: 'sector',
    'T+1': 't_plus_one',
    ST股: 'st_stock',
    除权除息: 'ex_right_dividend',
    后复权: 'hfq',
    盘口: 'order_book',
    委比: 'order_ratio',
    内盘: 'inner_outer_volume',
    主力净流入: 'main_fund_flow',
    融资融券: 'margin_trading',
    龙虎榜: 'dragon_tiger_list',
    大宗交易: 'block_trade',
    限售解禁: 'lockup_expiry',
    股票质押: 'share_pledge',
    营业收入: 'revenue',
    净利润: 'net_profit',
    经营现金流: 'operating_cash_flow',
  }

  for (const [alias, expectedId] of Object.entries(expectedTerms)) {
    assert.equal(findTerm(alias)?.id, expectedId, `${alias} should resolve to ${expectedId}`)
  }
})

test('difficult terms provide plain explanations with valid highlights and related terms', () => {
  const terms = allTerms()
  const explainedTerms = terms.filter((term) => term.plain)

  assert.ok(explainedTerms.length >= 20)
  for (const term of explainedTerms) {
    assert.ok(term.highlights?.length, `${term.id} should define highlights`)
    const searchableText = [term.short, term.plain, term.detail, term.tip].filter(Boolean).join(' ')
    for (const highlight of term.highlights) {
      assert.ok(searchableText.includes(highlight), `${term.id} highlight should exist in its content: ${highlight}`)
    }
    for (const relatedId of term.related || []) {
      assert.notEqual(relatedId, term.id, `${term.id} should not relate to itself`)
      assert.ok(findTerm(relatedId), `${term.id} has an invalid related term: ${relatedId}`)
    }
  }
})

test('highlight segments prefer longer phrases and keep plain text intact', () => {
  assert.deepEqual(splitHighlightedText('Beta≈1，Beta>1 时波动放大', ['Beta', 'Beta≈1', '波动放大']), [
    { text: 'Beta≈1', highlighted: true },
    { text: '，', highlighted: false },
    { text: 'Beta', highlighted: true },
    { text: '>1 时', highlighted: false },
    { text: '波动放大', highlighted: true },
  ])
})

test('related terms resolve in configured order', () => {
  assert.deepEqual(
    getRelatedTerms(findTerm('sharpe')).map((term) => term.id),
    ['sortino', 'volatility', 'max_drawdown'],
  )
})

test('search includes plain explanations', () => {
  assert.equal(searchTerms('赚钱效率')[0]?.id, 'sharpe')
})

test('glossary term ids are unique', () => {
  const terms = allTerms()
  const ids = terms.map((t) => t.id)
  assert.equal(ids.length, new Set(ids).size)
  assert.ok(terms.length >= 145)
})

test('diagram builders return svg for keyed terms', () => {
  const ids = listDiagramIds()
  assert.ok(ids.includes('max_drawdown'))
  assert.ok(getDiagramSvg('max_drawdown').includes('<svg'))
  assert.equal(getDiagramSvg('not_exist'), '')
  assert.ok(findTerm('max_drawdown')?.diagram === 'max_drawdown')
  assert.ok(findTerm('macd')?.diagram === 'macd')
})
