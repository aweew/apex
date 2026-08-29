import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./PreMarketReportShareSheet.vue', import.meta.url), 'utf8')

test('pre-market share sheet leads with the investment headline and thesis', () => {
  assert.match(source, /BrandShareLockup[^>]+subtitle="盘前观点"/)
  assert.match(source, /document\.title/)
  assert.match(source, /核心观点/)
  assert.match(source, /优先方向/)
  assert.match(source, /最大风险/)
  assert.match(source, /document\.judgement/)
  assert.match(source, /document\.priority/)
  assert.match(source, /document\.risk/)
})

test('pre-market share sheet turns report sections into visual decision blocks', () => {
  assert.match(source, /PreMarketReportSections/)
  assert.doesNotMatch(source, /关键变量|variable-card|S 级/)
  assert.match(source, /BrandShareFoot/)
  assert.doesNotMatch(source, /missingData|本次数据缺口/)
  assert.match(source, /:holding-limit="3"/)
  assert.match(source, /\['03', '04', '05'\]/)
  assert.match(source, /String\(index \+ 1\)\.padStart\(2, '0'\)/)
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
  assert.doesNotMatch(source, /font-size:\s*8px/)
  assert.match(source, /marketDataAsOf/)
  assert.match(source, /focusChanges/)
})
