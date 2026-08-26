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
    应收账款: 'accounts_receivable',
    净利润现金含量: 'cash_conversion_ratio',
    FCF倍数: 'price_to_free_cash_flow',
  }

  for (const [alias, expectedId] of Object.entries(expectedTerms)) {
    assert.equal(findTerm(alias)?.id, expectedId, `${alias} should resolve to ${expectedId}`)
  }
})

test('financial encyclopedia covers the requested learning map', () => {
  const expectedTerms = {
    货币: 'money',
    信用: 'credit',
    利率: 'interest_rate',
    汇率: 'exchange_rate',
    通胀: 'inflation',
    资产: 'asset',
    负债: 'liability',
    权益: 'owners_equity',
    收入: 'accounting_income',
    费用: 'expense',
    利润: 'profit',
    股票: 'stock',
    债券: 'bond',
    基金: 'fund',
    衍生品: 'derivative',
    期货: 'futures',
    期权: 'option',
    互换: 'swap',
    一级发行: 'primary_market',
    二级交易: 'secondary_market',
    做市商: 'market_maker',
    流动性: 'market_liquidity',
    波动率: 'volatility',
    套利: 'arbitrage',
    贝塔系数: 'beta',
    阿尔法收益: 'alpha',
    夏普比率: 'sharpe',
    最大回撤: 'max_drawdown',
    胜率: 'win_rate',
    盈亏比: 'payoff_ratio',
    资本资产定价: 'capm',
    套利定价: 'apt',
    有效市场假说: 'efficient_market_hypothesis',
    随机游走: 'random_walk',
    均值回归: 'mean_reversion',
    财务报表分析: 'financial_statement_analysis',
    现金流折现: 'dcf',
    内部收益率: 'irr',
    净现值: 'npv',
    回收期: 'payback_period',
    宏观经济周期: 'business_cycle',
    GDP: 'gdp',
    CPI: 'cpi',
    PMI: 'pmi',
    失业率: 'unemployment_rate',
    贸易差额: 'trade_balance',
    中央银行: 'central_bank',
    公开市场操作: 'open_market_operations',
    准备金率: 'reserve_requirement_ratio',
    再贴现率: 'rediscount_rate',
    基准利率: 'policy_rate',
    风险价值: 'var95',
    压力测试: 'stress_testing',
    蒙特卡洛模拟: 'monte_carlo_simulation',
    对冲: 'hedging',
    保险: 'insurance',
    行为金融学: 'behavioral_finance',
    锚定效应: 'anchoring_bias',
    过度自信: 'overconfidence_bias',
    损失厌恶: 'loss_aversion',
    羊群效应: 'herding_effect',
    公司治理: 'corporate_governance',
    股权激励: 'equity_incentive',
    并购: 'mergers_acquisitions',
    杠杆收购: 'leveraged_buyout',
    破产重整: 'bankruptcy_reorganization',
  }

  for (const [alias, expectedId] of Object.entries(expectedTerms)) {
    assert.equal(findTerm(alias)?.id, expectedId, `${alias} should resolve to ${expectedId}`)
  }
})

test('new financial learning categories and explanations stay complete', () => {
  const expectedCategories = ['基础', '市场', '估值', '宏观', '行为', '公司金融']
  const financeTerms = allTerms().filter((term) => expectedCategories.includes(term.category))

  for (const category of expectedCategories) {
    assert.ok(financeTerms.some((term) => term.category === category), `${category} should contain terms`)
  }
  assert.ok(financeTerms.length >= 80)
  for (const term of financeTerms) {
    assert.ok(term.plain, `${term.id} should provide a plain explanation`)
    assert.ok(term.highlights?.length, `${term.id} should define highlights`)
    assert.ok(term.related?.length, `${term.id} should provide related reading`)
  }
})

test('similar financial concepts keep distinct lookup keys', () => {
  assert.equal(findTerm('ETF')?.id, 'etf')
  assert.equal(findTerm('指数基金')?.id, 'index_fund')
  assert.equal(findTerm('折现率')?.id, 'discount_rate')
  assert.equal(findTerm('再贴现率')?.id, 'rediscount_rate')
  assert.equal(findTerm('内在价值')?.id, 'fair_value')
  assert.equal(findTerm('期权内在价值')?.id, 'option_intrinsic_value')
  assert.equal(findTerm('Forward')?.id, 'forward_eval')
  assert.equal(findTerm('Forward Contract')?.id, 'forward_contract')
})

test('full glossary browse limit includes every category', () => {
  const terms = allTerms()
  const browsableTerms = searchTerms('', terms.length)

  assert.equal(browsableTerms.length, terms.length)
  for (const category of ['基础', '市场', '估值', '宏观', '行为', '公司金融']) {
    assert.ok(browsableTerms.some((term) => term.category === category))
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
  assert.ok(terms.length >= 259)
})

test('diagram builders return svg for keyed terms', () => {
  const ids = listDiagramIds()
  assert.ok(ids.includes('max_drawdown'))
  assert.ok(getDiagramSvg('max_drawdown').includes('<svg'))
  assert.equal(getDiagramSvg('not_exist'), '')
  assert.ok(findTerm('max_drawdown')?.diagram === 'max_drawdown')
  assert.ok(findTerm('macd')?.diagram === 'macd')
})
