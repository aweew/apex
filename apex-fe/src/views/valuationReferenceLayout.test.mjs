import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('./ValuationView.vue', import.meta.url), 'utf8')
const analysisSource = readFileSync(new URL('../components/StockAnalysisPanel.vue', import.meta.url), 'utf8')

test('valuation key metrics show current verdicts and reference ranges inline', () => {
  assert.match(source, /class="metric-verdict"[^>]*>当前：/)
  assert.match(source, /class="metric-reference"[^>]*>参考：/)
  assert.match(source, /dimension\('peg'\)\?\.reference/)
  assert.match(source, /dimension\('quality'\)\?\.reference/)
  assert.match(source, /dimension\('growth'\)\?\.reference/)
})

test('stock analysis valuation dimensions keep verdicts and references visible', () => {
  assert.match(analysisSource, /class="dim-verdict">当前：\{\{ d\.verdict \}\}/)
  assert.match(analysisSource, /v-if="d\.reference" class="dim-reference">参考：\{\{ d\.reference \}\}/)
})
