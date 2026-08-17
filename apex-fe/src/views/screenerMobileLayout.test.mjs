import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const screenerSource = await readFile(new URL('./ScreenerView.vue', import.meta.url), 'utf8')
const desktopTableSource = screenerSource.slice(
  screenerSource.indexOf('<el-table\n      v-if="!isMobileViewport"'),
  screenerSource.indexOf('<section v-if="isMobileViewport" class="mobile-results-section"'),
)

test('stock screener uses a dedicated mobile filter surface with progressive disclosure', () => {
  assert.match(screenerSource, /isMobileViewport = computed\(\(\) => viewportWidth\.value <= 820\)/)
  assert.match(screenerSource, /mobileAdvancedOpen = ref\(false\)/)
  assert.match(screenerSource, /class="mobile-filter-surface"/)
  assert.match(screenerSource, /class="advanced-filter-toggle"/)
  assert.match(screenerSource, /v-show="mobileAdvancedOpen"[\s\S]*?class="mobile-advanced-filters"/)
  assert.match(screenerSource, /mobileAdvancedFilterCount/)
})

test('desktop form and table remain separate from mobile controls and results', () => {
  assert.match(screenerSource, /v-if="!isMobileViewport" class="filter-panel desktop-filter-panel"/)
  assert.match(screenerSource, /v-if="!isMobileViewport"[\s\S]*?class="screener-table"/)
  assert.match(screenerSource, /class="screener-mobile-list"/)
  assert.match(screenerSource, /class="screener-mobile-card"/)
})

test('desktop screener uses the shared stock identity hierarchy with market badges', () => {
  assert.match(desktopTableSource, /<el-table-column prop="name" label="股票"[\s\S]*?class="security-link"[\s\S]*?class="security-name-text"[\s\S]*?<SecurityMarketBadge :security="row" include-main \/>[\s\S]*?class="security-code"/)
  assert.doesNotMatch(desktopTableSource, /<el-table-column prop="code" label="代码"/)
  assert.match(screenerSource, /\.security-link\s*\{[\s\S]*?flex-direction:\s*column;[\s\S]*?align-items:\s*center;/)
  assert.match(screenerSource, /\.security-code\s*\{[\s\S]*?font-size:\s*11px;/)
})

test('mobile screener reuses market badges including Shanghai and Shenzhen', () => {
  assert.match(screenerSource, /class="mobile-stock-identity"[\s\S]*?<SecurityMarketBadge :security="row" include-main \/>/)
  assert.doesNotMatch(screenerSource, /class="market-badge"/)
})

test('mobile screener uses compact actions and pagination', () => {
  assert.match(screenerSource, /class="mobile-filter-actions"/)
  assert.match(screenerSource, /class="mobile-pager"/)
  assert.match(screenerSource, /class="mobile-batch-results"/)
  assert.match(screenerSource, /\.mobile-filter-actions :deep\(\.el-button\)[\s\S]*?min-height:\s*44px;/)
  assert.match(screenerSource, /\.mobile-pager-button\s*\{[\s\S]*?min-height:\s*44px;/)
  assert.match(screenerSource, /\.advanced-filter-toggle\s*\{[\s\S]*?min-height:\s*44px;/)
})

test('mobile stock cards keep key metrics in stable responsive tracks', () => {
  assert.match(screenerSource, /\.mobile-stock-metrics\s*\{[\s\S]*?grid-template-columns:\s*repeat\(4, minmax\(0, 1fr\)\);/)
  assert.doesNotMatch(screenerSource, /\.mobile-stock-metrics\.is-screening\s*\{[\s\S]*?grid-template-columns:\s*repeat\(3, minmax\(0, 1fr\)\);/)
})

test('batch backtest uses a current trailing two-year range', () => {
  assert.match(screenerSource, /buildTrailingDateRange\(2\)/)
  assert.match(screenerSource, /beginDate:\s*backtestRange\.beginDate/)
  assert.match(screenerSource, /endDate:\s*backtestRange\.endDate/)
  assert.doesNotMatch(screenerSource, /endDate:\s*'2026-08-01'/)
})

test('screener exposes free and strategy modes with a single primary strategy action', () => {
  assert.match(screenerSource, /activeMode = ref\('free'\)/)
  assert.match(screenerSource, /<el-segmented v-model="activeMode" :options="modeOptions"/)
  assert.match(screenerSource, /class="strategy-panel"/)
  assert.match(screenerSource, /运行策略/)
  assert.match(screenerSource, /class="strategy-rule-list"/)
})

test('strategy maintenance supports copy edit toggle delete and ordering', () => {
  assert.match(screenerSource, /copyScreenerTemplate/)
  assert.match(screenerSource, /updateScreenerStrategy/)
  assert.match(screenerSource, /toggleScreenerStrategy/)
  assert.match(screenerSource, /deleteScreenerStrategy/)
  assert.match(screenerSource, /reorderScreenerStrategies/)
  assert.match(screenerSource, /draggable="true"/)
  assert.match(screenerSource, /@drop="onStrategyDrop\(strategy.id\)"/)
})

test('strategy results show data cutoffs issues and per-stock evidence', () => {
  assert.match(screenerSource, /class="strategy-data-status"/)
  assert.match(screenerSource, /实时截面/)
  assert.match(screenerSource, /日线截止/)
  assert.match(screenerSource, /class="strategy-issues"/)
  assert.match(screenerSource, /class="evidence-line"/)
  assert.match(screenerSource, /class="mobile-evidence-line"/)
})

test('changing strategy clears results from the previously selected strategy', () => {
  assert.match(screenerSource, /@change="onStrategyChange"/)
  assert.match(screenerSource, /function onStrategyChange\(\) \{[\s\S]*strategyRunResult\.value = null[\s\S]*\}/)
})
