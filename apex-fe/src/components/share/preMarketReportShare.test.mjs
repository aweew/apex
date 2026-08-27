import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./PreMarketReportShareSheet.vue', import.meta.url), 'utf8')

test('pre-market share sheet leads with the three actionable decisions', () => {
  assert.match(source, /BrandShareLockup[^>]+subtitle="盘前观点"/)
  assert.match(source, /今日判断/)
  assert.match(source, /优先方向/)
  assert.match(source, /最大风险/)
  assert.match(source, /document\.judgement/)
  assert.match(source, /document\.priority/)
  assert.match(source, /document\.risk/)
})

test('pre-market share sheet turns report sections into visual decision blocks', () => {
  assert.match(source, /PreMarketHoldingCard/)
  assert.match(source, /class="sentiment-meter"/)
  assert.match(source, /class="variable-card"/)
  assert.match(source, /class="direction-rank"/)
  assert.match(source, /class="holding-card-grid"/)
  assert.match(source, /class="risk-bar"/)
  assert.match(source, /class="scenario-grid"/)
  assert.match(source, /section\.lines/)
  assert.match(source, /BrandShareFoot/)
  assert.doesNotMatch(source, /missingData|本次数据缺口/)
})

test('pre-market share sheet uses a stable high-resolution long-image canvas', () => {
  assert.match(source, /width:\s*760px/)
  assert.match(source, /overflow-wrap:\s*anywhere/)
  assert.match(source, /letter-spacing:\s*0/)
  assert.doesNotMatch(source, /max-height/)
})

test('pre-market share sheet keeps the visual report compact and scannable', () => {
  assert.match(source, /grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\)/)
  assert.match(source, /break-inside:\s*avoid/)
  assert.match(source, /font-variant-numeric:\s*tabular-nums/)
})
