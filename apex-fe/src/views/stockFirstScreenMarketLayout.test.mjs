import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./StockView.vue', import.meta.url), 'utf8')

test('stock market chart is visible before research tabs on the first screen', () => {
  const marketOverviewIndex = source.indexOf('class="market-overview"')
  const chartStageIndex = source.indexOf('class="chart-stage"')
  const researchTabsIndex = source.indexOf('<el-tabs v-model="activeTab"')

  assert.ok(marketOverviewIndex > 0)
  assert.ok(chartStageIndex > marketOverviewIndex)
  assert.ok(researchTabsIndex > chartStageIndex)
  assert.doesNotMatch(source, /<el-tab-pane[^>]+name="chart"/)
  assert.doesNotMatch(source, /<el-tab-pane[^>]+name="summary"/)
  assert.match(source, /const klinePeriod = ref\('day'\)/)
})

test('market overview combines quote snapshot and the primary chart', () => {
  assert.match(source, /class="market-overview"[\s\S]*?class="quote-snapshot"[\s\S]*?class="market-chart"/)
  assert.match(source, /class="quote-primary"/)
  assert.match(source, /class="quote-metrics"/)
  assert.match(source, /class="chart-primary-controls"[\s\S]*?class="chart-stage"/)
  assert.match(source, /if \(basic\.value\?\.latestPrice == null \|\| basic\.value\?\.pctChg == null\)/)
  assert.match(source, /if \(basic\.value\?\.pctChg == null\) return ''/)
})

test('mobile first-screen market layout stays compact and overflow-safe', () => {
  assert.match(source, /\.market-overview\s*\{[^}]*display:\s*grid;[^}]*grid-template-columns:\s*minmax\(168px, 0\.24fr\) minmax\(0, 1fr\);/s)
  assert.match(source, /@media \(max-width: 820px\)[\s\S]*?\.market-overview\s*\{[^}]*grid-template-columns:\s*minmax\(0, 1fr\);/s)
  assert.match(source, /@media \(max-width: 820px\)[\s\S]*?\.chart\s*\{[^}]*height:\s*500px;/s)
  assert.match(source, /\.quote-metrics > div:nth-child\(n \+ 9\)\s*\{[^}]*display:\s*none;/s)
  assert.doesNotMatch(source, /class="chart-signal-summary"|class="period-meta"/)
  assert.match(source, /\.market-chart\s*\{[^}]*min-width:\s*0;/s)
})

test('legacy market tab links resolve to the first-screen market overview', () => {
  assert.match(source, /const LEGACY_MARKET_TABS = \['summary', 'chart'\]/)
  assert.match(source, /LEGACY_MARKET_TABS\.includes\(tab\)/)
})
