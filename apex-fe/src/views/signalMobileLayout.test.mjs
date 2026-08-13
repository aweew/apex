import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const signalSource = await readFile(new URL('./SignalView.vue', import.meta.url), 'utf8')

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

test('signal list keeps market badges beside stock names and balances mobile metadata', () => {
  assert.match(signalSource, /class="signal-stock-name">[\s\S]*?<strong>\{\{ row\.name \|\| '-' \}\}<\/strong>[\s\S]*?<SecurityMarketBadge :security="row"/)
  assert.match(signalSource, /<el-table-column prop="name" label="名称" width="128"[\s\S]*?class="signal-stock-name"/)
  assert.match(signalSource, /\.signal-mobile-meta\s*\{\s*display: grid;\s*grid-template-columns: repeat\(4, minmax\(0, 1fr\)\);/)
  assert.match(signalSource, /\.signal-mobile-meta \.strategy-badge,[\s\S]*?justify-self: center;/)
  assert.match(signalSource, /\.signal-mobile-meta \.signal-score\s*\{\s*justify-self: end;/)
})
