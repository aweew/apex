import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const [signalSource, termTipSource] = await Promise.all([
  readFile(new URL('./SignalView.vue', import.meta.url), 'utf8'),
  readFile(new URL('../components/TermTip.vue', import.meta.url), 'utf8'),
])

test('strategy signal workspace tabs follow the module header', () => {
  assert.match(
    signalSource,
    /<header class="header signal-header">[\s\S]*?<\/header>\s*<DecisionWorkspaceTabs \/>/,
  )
})

test('strategy signal page keeps loading feedback local to the affected content', () => {
  assert.doesNotMatch(signalSource, /class="page signal-page"\s+v-loading=/)
  assert.match(signalSource, /class="signal-results"\s+v-loading="listLoading \|\| ordering"/)
  assert.match(signalSource, /class="signal-metrics"\s+v-loading="overviewLoading"/)
})

test('mobile filters use touch-friendly segmented controls and progressive disclosure', () => {
  assert.match(signalSource, /isMobileViewport = computed\(\(\) => viewportWidth\.value <= 900\)/)
  assert.match(signalSource, /class="signal-segmented signal-direction-filter"/)
  assert.match(signalSource, /class="signal-segmented signal-strategy-filter"/)
  assert.match(signalSource, /class="advanced-filter-toggle"/)
  assert.match(signalSource, /class="advanced-filters"/)
  assert.match(signalSource, /class="filter-result-summary"/)
})

test('filter changes reload only the signal list', () => {
  assert.match(signalSource, /async function loadSignalList\([\s\S]*?latestSignals\(/)
  assert.match(signalSource, /function updateSideFilter\([\s\S]*?loadSignalList\(\)/)
  assert.match(signalSource, /function resetFilters\([\s\S]*?loadSignalList\(\)/)
  assert.doesNotMatch(signalSource, /@change="load"/)
})

test('mobile evaluation sections avoid desktop tables', () => {
  assert.match(signalSource, /class="forward-mobile-list"/)
  assert.match(signalSource, /class="confluence-mobile-list"/)
  assert.match(signalSource, /v-else-if="forward\?\.scoreBuckets\?\.length"/)
  assert.match(signalSource, /v-else-if="confluence\?\.items\?\.length"/)
})

test('signal list reuses market badges and balances mobile metadata', () => {
  assert.match(signalSource, /class="signal-stock">[\s\S]*?<StockIdentity :security="row" interactive/)
  assert.match(signalSource, /<el-table-column prop="name" label="股票" width="144">[\s\S]*?<StockIdentity :security="row" interactive compact/)
  assert.match(signalSource, /\.signal-mobile-meta\s*\{\s*display: grid;\s*grid-template-columns: repeat\(4, minmax\(0, 1fr\)\);/)
  assert.match(signalSource, /\.signal-mobile-meta \.strategy-badge,[\s\S]*?justify-self: center;/)
  assert.match(signalSource, /\.signal-mobile-meta \.signal-score\s*\{\s*justify-self: end;/)
})

test('confluence stock identities open stock details on mobile and desktop', () => {
  const confluenceTemplate = signalSource.slice(
    signalSource.indexOf('<el-collapse-item name="cf">'),
    signalSource.indexOf('</el-collapse-item>', signalSource.indexOf('<el-collapse-item name="cf">')),
  )

  assert.match(
    confluenceTemplate,
    /<StockIdentity\s+:security="item"\s+:interactive="Boolean\(item\.code\)"\s+compact\s+@select="router\.push\(`\/stock\/\$\{item\.code\}`\)"/,
  )
  assert.match(
    confluenceTemplate,
    /<StockIdentity\s+:security="row"\s+:interactive="Boolean\(row\.code\)"\s+compact\s+@select="router\.push\(`\/stock\/\$\{row\.code\}`\)"/,
  )
})

test('touch term underlines stay attached to their labels', () => {
  assert.match(
    termTipSource,
    /@media \(max-width: 820px\), \(hover: none\)\s*\{[\s\S]*?\.term-tip\s*\{[^}]*display:\s*inline-block;[^}]*line-height:\s*1\.2;[^}]*vertical-align:\s*baseline;/,
  )
})
