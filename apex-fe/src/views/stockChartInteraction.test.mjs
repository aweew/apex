import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const stockSource = await readFile(new URL('./StockView.vue', import.meta.url), 'utf8')

test('visible K-line prices retain breathing room above highs and below lows', () => {
  assert.match(
    stockSource,
    /const pad = span > 0 \? span \* 0\.08 : Math\.max\(Math\.abs\(max\) \* 0\.02, 0\.01\)/,
  )
})

test('horizontal chart gestures stay inside the chart while vertical page scrolling remains available', () => {
  assert.match(
    stockSource,
    /\.chart\s*\{[\s\S]*?touch-action:\s*pan-y;[\s\S]*?overscroll-behavior-x:\s*contain;/,
  )
  assert.doesNotMatch(stockSource, /touch-action:\s*manipulation;/)
})

test('intraday polling pauses while the page is hidden and resumes on return', () => {
  assert.match(stockSource, /if \(!isIntraday\.value \|\| document\.hidden\) return/)
  assert.match(stockSource, /if \(!document\.hidden && isIntraday\.value && activeTab\.value === 'chart'\)/)
  assert.match(stockSource, /function onDocumentVisibilityChange\(\)/)
  assert.match(stockSource, /document\.addEventListener\('visibilitychange', onDocumentVisibilityChange\)/)
  assert.match(stockSource, /document\.removeEventListener\('visibilitychange', onDocumentVisibilityChange\)/)
})
