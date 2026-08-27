import assert from 'node:assert/strict'
import test from 'node:test'
import { parseHoldingLine, parsePreMarketReport } from './preMarketReport.js'

const content = `Apex 每日盘前研报
日期：2026-08-27
今日判断：先看英伟达财报对 AI 链定价的修正。

01｜今日结论
优先看：算力、光模块。
最大风险：高开兑现，量能承接不足。

02｜资金与情绪
量能：缩量 -1.21%。
行业资金：数据暂缺

04｜今日方向
1. 算力：事件驱动，开盘看成交。

08｜开盘验证
偏多：核心方向放量。`

test('parses report metadata and sections for structured rendering', () => {
  const report = parsePreMarketReport(content)

  assert.equal(report.title, 'Apex 每日盘前研报')
  assert.equal(report.date, '2026-08-27')
  assert.equal(report.judgement, '先看英伟达财报对 AI 链定价的修正。')
  assert.equal(report.priority, '算力、光模块。')
  assert.equal(report.risk, '高开兑现，量能承接不足。')
  assert.deepEqual(report.sections.map((section) => section.title), ['今日结论', '资金与情绪', '今日方向', '开盘验证'])
})

test('removes unavailable placeholder lines instead of showing them', () => {
  const report = parsePreMarketReport(content)
  const capitalSection = report.sections.find((section) => section.title === '资金与情绪')

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
