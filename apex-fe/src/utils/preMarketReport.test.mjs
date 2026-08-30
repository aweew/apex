import assert from 'node:assert/strict'
import test from 'node:test'
import {
  parseHoldingLine,
  parseOpportunityLine,
  parsePreMarketReport,
  parseScenarioLine,
  parseStockPickLine,
} from './preMarketReport.js'

const content = `今日投资机会｜算力硬件重回主线，开盘承接决定持续性
日期：2026-08-27
核心观点：先看英伟达财报对 AI 链定价的修正。

01｜市场状态
优先看：算力、光模块。
最大风险：高开兑现，量能承接不足。

02｜资金风格
量能：缩量 -1.21%。
行业资金：数据暂缺

03｜投资机会
1. 算力｜催化：英伟达财报；确认：开盘成交放大；失效：高开低走。

05｜开盘剧本
偏强｜核心方向放量。`

test('parses report metadata and sections for structured rendering', () => {
  const report = parsePreMarketReport(content)

  assert.equal(report.title, '今日投资机会｜算力硬件重回主线，开盘承接决定持续性')
  assert.equal(report.date, '2026-08-27')
  assert.equal(report.judgement, '先看英伟达财报对 AI 链定价的修正。')
  assert.equal(report.priority, '算力、光模块。')
  assert.equal(report.risk, '高开兑现，量能承接不足。')
  assert.deepEqual(report.sections.map((section) => section.title), ['市场状态', '资金风格', '投资机会', '开盘剧本'])
})

test('removes unavailable placeholder lines instead of showing them', () => {
  const report = parsePreMarketReport(content)
  const capitalSection = report.sections.find((section) => section.title === '资金风格')

  assert.deepEqual(capitalSection.lines, ['量能：缩量 -1.21%。'])
})

test('returns an empty document for blank content', () => {
  assert.deepEqual(parsePreMarketReport(''), {
    title: '',
    date: '',
    judgement: '',
    priority: '',
    risk: '',
    sections: [],
  })
})

test('parses a holding reminder into visual metrics and action fields', () => {
  const holding = parseHoldingLine('- 德明利 001309｜正向关注｜入选：价格波动超过 3%｜仓位 9.05%｜价格 429.76｜盈亏 5.32%｜趋势 中性震荡 · RS20 +14.2 · 雷达 2/8 · 相对大盘偏强｜处理 不加仓；收盘跌破止损 309.59 全部卖出')

  assert.deepEqual(holding, {
    name: '德明利',
    code: '001309',
    status: '正向关注',
    reason: '价格波动超过 3%',
    weight: 9.05,
    weightText: '9.05%',
    priceText: '429.76',
    pnl: 5.32,
    pnlText: '5.32%',
    trend: '中性震荡 · RS20 +14.2 · 雷达 2/8 · 相对大盘偏强',
    advice: '不加仓；收盘跌破止损 309.59 全部卖出',
    radarHit: 2,
    radarTotal: 8,
  })
})

test('keeps unavailable holding metrics empty instead of inventing values', () => {
  const holding = parseHoldingLine('- 示例股份 600000｜高风险｜入选：已触及止损线｜处理 优先离场复盘')

  assert.equal(holding.weight, null)
  assert.equal(holding.pnl, null)
  assert.equal(holding.radarHit, null)
  assert.equal(holding.radarTotal, null)
})

test('parses an opportunity into catalyst confirmation and invalidation fields', () => {
  assert.deepEqual(
    parseOpportunityLine('1. 算力｜催化：英伟达发布财报；确认：板块放量且核心股强于指数；失效：高开低走。'),
    {
      rank: 1,
      direction: '算力',
      catalyst: '英伟达发布财报',
      confirmation: '板块放量且核心股强于指数',
      invalidation: '高开低走。',
    },
  )
})

test('parses an individual stock pick with opinion and execution boundaries', () => {
  assert.deepEqual(
    parseStockPickLine('1. 贵州茅台 600519｜级别：首选；观点：白酒主线与偏低估值共振，优先于普通反弹标的；依据：综合评分 86，策略 S2；触发：9:45 前强于沪深 300 且不跌破开盘价；失效：跌破 1450；仓位：10%。'),
    {
      rank: 1,
      level: '首选',
      name: '贵州茅台',
      code: '600519',
      opinion: '白酒主线与偏低估值共振，优先于普通反弹标的',
      evidence: '综合评分 86，策略 S2',
      trigger: '9:45 前强于沪深 300 且不跌破开盘价',
      invalidation: '跌破 1450',
      weight: '10%。',
    },
  )
})

test('enriches the stock recommendation section separately from theme opportunities', () => {
  const report = parsePreMarketReport(`今日投资机会｜回踩结构 · 主线共振
日期：2026-08-31
核心观点：今日首选贵州茅台，主线和估值同时占优。
首选个股：贵州茅台 600519。
最大风险：白酒板块低开转弱。

03｜个股推荐
1. 贵州茅台 600519｜级别：首选；观点：白酒主线与偏低估值共振；依据：综合评分 86；触发：9:45 前强于沪深 300；失效：跌破开盘价。`)
  const stockSection = report.sections.find((section) => section.number === '03')

  assert.equal(report.priority, '贵州茅台 600519。')
  assert.equal(stockSection.stockPicks[0].code, '600519')
  assert.equal(stockSection.opportunities.length, 0)
})

test('parses opening scenarios into named conditions', () => {
  assert.deepEqual(parseScenarioLine('转弱｜核心方向高开低走、下跌家数持续扩大。'), {
    name: '转弱',
    condition: '核心方向高开低走、下跌家数持续扩大。',
  })
})

test('enriches report sections for one shared visual renderer', () => {
  const report = parsePreMarketReport(content)
  const opportunitySection = report.sections.find((section) => section.number === '03')
  const scenarioSection = report.sections.find((section) => section.number === '05')

  assert.equal(opportunitySection.opportunities[0].direction, '算力')
  assert.equal(opportunitySection.opportunities[0].confirmation, '开盘成交放大')
  assert.equal(scenarioSection.scenarios[0].name, '偏强')
})
