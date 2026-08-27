import assert from 'node:assert/strict'
import test from 'node:test'
import { parsePreMarketReport } from './preMarketReport.js'

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
