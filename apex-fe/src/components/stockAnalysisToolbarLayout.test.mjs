import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./StockAnalysisPanel.vue', import.meta.url), 'utf8')

test('stock analysis direction names describe the result that changes', () => {
  assert.match(source, /value="BUY">多头机会<\/el-radio-button>/)
  assert.match(source, /value="SELL">空头风险<\/el-radio-button>/)
  assert.match(source, /const radarDirectionLabel = computed/)
  assert.match(source, /const radarResultText = computed/)
  assert.match(source, /class="analysis-mode-result"[\s\S]*?aria-live="polite"/)
})

test('stock analysis ignores stale direction responses', () => {
  assert.match(source, /let rulesRequestSeq = 0/)
  assert.match(source, /const requestedSide = side\.value/)
  assert.match(source, /fetchStockAnalysis\(props\.code, requestedSide, 120, false, false\)/)
  assert.match(source, /requestSeq !== rulesRequestSeq \|\| requestedSide !== side\.value/)
  assert.match(source, /fetchStockAnalysis\(props\.code, requestedSide, 120, true, forceAi\)/)
})

test('stock analysis shows traceable local news coverage', () => {
  assert.match(source, /class="card stock-news-card"/)
  assert.match(source, /data\.newsSummary/)
  assert.match(source, /data\.recentNews\.slice\(0, 5\)/)
  assert.match(source, /newsSourceLabel\(news\.source\)/)
  assert.match(source, /fmtNewsTime\(news\.publishedAt\)/)
  assert.match(source, /:href="news\.url \|\| undefined"/)
  assert.match(source, /\.stock-news-item\s*\{[^}]*grid-template-columns:\s*auto minmax\(0, 1fr\) auto;/)
})

test('mobile stock analysis controls keep a stable two-row hierarchy', () => {
  assert.match(source, /class="analysis-mode"/)
  assert.match(source, /class="analysis-actions"/)
  assert.match(source, /class="analysis-refresh"/)
  assert.match(source, /class="analysis-ai"/)
  assert.match(source, /class="analysis-link"/)
  assert.match(source, /<Refresh \/>/)
  assert.match(source, /<MagicStick \/>/)
  assert.match(source, /@media \(max-width: 560px\)[\s\S]*?\.analysis-toolbar\s*\{[\s\S]*?grid-template-columns:\s*1fr;/)
  assert.match(source, /@media \(max-width: 560px\)[\s\S]*?\.analysis-actions\s*\{[\s\S]*?grid-template-columns:\s*44px minmax\(78px, 1\.1fr\) minmax\(76px, 1fr\) minmax\(58px, 0\.72fr\);/)
  assert.match(source, /@media \(max-width: 560px\)[\s\S]*?min-height:\s*44px;/)
})
