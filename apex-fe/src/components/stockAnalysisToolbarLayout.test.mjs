import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./StockAnalysisPanel.vue', import.meta.url), 'utf8')

test('stock analysis direction names describe the result that changes', () => {
  assert.match(source, /value="BUY">多头\/机会<\/el-radio-button>/)
  assert.match(source, /value="SELL">空头\/风险<\/el-radio-button>/)
  assert.match(source, /<h3>多头\/机会<\/h3>/)
  assert.match(source, /<h3>空头\/风险<\/h3>/)
  assert.match(source, /const radarDirectionLabel = computed/)
  assert.match(source, /const radarResultText = computed/)
  assert.match(source, /class="analysis-mode-result"[\s\S]*?aria-live="polite"/)
})

test('stock analysis uses market direction colors and readable progress values', () => {
  assert.match(source, /\.tone-bull\s*\{[\s\S]*rgba\(255, 59, 48, 0\.1\)/)
  assert.match(source, /\.tone-bear\s*\{[\s\S]*rgba\(52, 199, 89, 0\.12\)/)
  assert.match(source, /\.tone-bull \.point-list li::before\s*\{[\s\S]*background: #ff3b30;/)
  assert.match(source, /\.tone-bear \.point-list li::before\s*\{[\s\S]*background: #34c759;/)
  assert.match(source, /\.mini-bar em\s*\{[\s\S]*color: var\(--slate\);[\s\S]*background: rgba\(255, 255, 255, 0\.88\);/)
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
  assert.match(source, /<h3>个股消息面<\/h3>/)
  assert.match(source, /data\.newsSummary/)
  assert.match(source, /data\.recentNews\.slice\(0, 5\)/)
  assert.match(source, /newsSourceLabel\(news\.source\)/)
  assert.match(source, /fmtNewsTime\(news\.publishedAt\)/)
  assert.match(source, /:href="news\.url \|\| undefined"/)
  assert.match(source, /\.stock-news-item\s*\{[^}]*grid-template-columns:\s*auto minmax\(0, 1fr\) auto;/)
})

test('today radar summarizes technical news valuation and market evidence without inventing data', () => {
  assert.match(source, /const todayRadarItems = computed/)
  assert.match(source, /label: '技术信号'/)
  assert.match(source, /label: '个股消息'/)
  assert.match(source, /label: '估值'/)
  assert.match(source, /label: '盘面热点'/)
  assert.match(source, /\['UNDERVALUED', 'SLIGHTLY_CHEAP'\]\.includes/)
  assert.match(source, /valuationLevel === 'UNKNOWN'/)
  assert.match(source, /analysis\.capital\?\.hotHit/)
  assert.match(source, /class="today-radar"/)
  assert.match(source, /v-for="item in todayRadarItems"/)
  assert.match(source, /<h3><TermTip term="pe_ttm">估值<\/TermTip><\/h3>/)
  assert.match(source, /<h3>盘面热点<\/h3>/)
})

test('opening the radar does not automatically trigger slow AI refresh', () => {
  const loadRulesBody = source.slice(source.indexOf('async function loadRules()'), source.indexOf('async function loadAi('))

  assert.doesNotMatch(loadRulesBody, /loadAi\(/)
  assert.match(source, /@click="refreshAi"/)
})

test('desktop stock analysis commands use content width instead of filling the toolbar', () => {
  assert.match(
    source,
    /\.analysis-actions\s*\{[^}]*display:\s*flex;[^}]*gap:\s*8px;[^}]*align-items:\s*end;[^}]*justify-content:\s*flex-end;/s,
  )
  assert.match(
    source,
    /\.analysis-actions :deep\(\.el-button\)\s*\{[^}]*width:\s*auto;[^}]*min-width:\s*0;/s,
  )
  assert.match(
    source,
    /\.analysis-actions :deep\(\.analysis-refresh\)\s*\{[^}]*width:\s*36px;[^}]*min-width:\s*36px;/s,
  )
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
  assert.match(source, /@media \(max-width: 560px\)[\s\S]*?\.analysis-actions\s*\{[^}]*display:\s*grid;[^}]*grid-template-columns:\s*44px minmax\(78px, 1\.1fr\) minmax\(76px, 1fr\) minmax\(58px, 0\.72fr\);/)
  assert.match(source, /@media \(max-width: 560px\)[\s\S]*?\.analysis-actions :deep\(\.el-button\)\s*\{[^}]*width:\s*100%;[^}]*min-width:\s*0;[^}]*min-height:\s*44px;/)
  assert.match(source, /@media \(max-width: 560px\)[\s\S]*?\.today-radar-grid\s*\{[^}]*grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/)
})

test('stock analysis share capture uses a fixed dense canvas independent of mobile breakpoints', () => {
  assert.match(source, /const ANALYSIS_SHARE_WIDTH = 720/)
  assert.match(source, /:class="\{ 'is-share-capture': capturingShare \}"/)
  assert.match(source, /el\.style\.width = `\$\{ANALYSIS_SHARE_WIDTH\}px`/)
  assert.match(source, /\.share-card\.is-share-capture \.grid-2\s*\{[\s\S]*?grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/)
  assert.match(source, /\.share-card\.is-share-capture \.today-radar-grid\s*\{[^}]*grid-template-columns:\s*repeat\(4, minmax\(0, 1fr\)\);/)
  assert.match(source, /\.share-card\.is-share-capture \.quote-core\s*\{[\s\S]*?grid-template-columns:\s*repeat\(5, minmax\(0, 1fr\)\);/)
  assert.match(source, /\.share-card\.is-share-capture \.stock-news-item:nth-child\(n \+ 4\)/)
})
