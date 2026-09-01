import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const screenerSource = await readFile(new URL('./ScreenerView.vue', import.meta.url), 'utf8')
const desktopTableSource = screenerSource.slice(
  screenerSource.indexOf('<el-table\n      v-if="!isMobileViewport"'),
  screenerSource.indexOf('<section v-if="isMobileViewport" class="mobile-results-section"'),
)
const mobileStrategySelectorSource = screenerSource.slice(
  screenerSource.indexOf('<div v-else class="strategy-selector">'),
  screenerSource.indexOf('<div class="strategy-actions">'),
)
const mobileFreeFilterSource = screenerSource.slice(
  screenerSource.indexOf('<section v-else class="mobile-filter-surface"'),
  screenerSource.indexOf('<section v-else class="strategy-panel"'),
)

test('stock screener uses a dedicated mobile filter surface with progressive disclosure', () => {
  assert.match(screenerSource, /isMobileViewport = computed\(\(\) => viewportWidth\.value <= 820\)/)
  assert.match(screenerSource, /mobileAdvancedOpen = ref\(false\)/)
  assert.match(screenerSource, /class="mobile-filter-surface"/)
  assert.match(screenerSource, /class="advanced-filter-toggle"/)
  assert.match(screenerSource, /v-show="mobileAdvancedOpen"[\s\S]*?class="mobile-advanced-filters"/)
  assert.match(screenerSource, /mobileAdvancedFilterCount/)
})

test('mobile screener searches the full market with compact condition and submit actions', () => {
  assert.doesNotMatch(mobileFreeFilterSource, /class="mobile-filter-heading"|<h2>筛选条件<\/h2>/)
  assert.match(mobileFreeFilterSource, /class="mobile-search-row"/)
  assert.doesNotMatch(mobileFreeFilterSource, /class="mobile-scope-field"|class="mobile-segmented"|>全部股票<|>自选</)
  assert.match(mobileFreeFilterSource, /class="advanced-filter-toggle"[\s\S]*?<Filter \/>[\s\S]*?<span>条件<\/span>/)
  assert.match(mobileFreeFilterSource, /class="mobile-filter-reset"[\s\S]*?:icon="RefreshRight"[\s\S]*?aria-label="重置筛选条件"/)
  assert.match(screenerSource, /\.mobile-search-row\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\) auto;/)
  assert.match(screenerSource, /\.mobile-filter-actions\s*\{[\s\S]*?display:\s*flex;/)
  assert.match(screenerSource, /\.mobile-filter-actions :deep\(\.el-button\)\s*\{[\s\S]*?width:\s*auto;/)
  assert.match(screenerSource, /\.mobile-filter-surface\s*\{[\s\S]*?box-shadow:\s*none;/)
})

test('free screener always queries the full market while watchlist stays outside this page', () => {
  assert.match(screenerSource, /groupName:\s*'__MARKET__'/)
  assert.doesNotMatch(screenerSource, /v-model="form\.scope"|v-model="form\.groupName"|function resolveGroupName/)
})

test('desktop form and table remain separate from mobile controls and results', () => {
  assert.match(screenerSource, /v-if="!isMobileViewport" class="filter-panel desktop-filter-panel"/)
  assert.match(screenerSource, /v-if="!isMobileViewport"[\s\S]*?class="screener-table"/)
  assert.match(screenerSource, /class="screener-mobile-list"/)
  assert.match(screenerSource, /class="screener-mobile-card"/)
})

test('desktop screener uses the shared stock identity hierarchy with market badges', () => {
  assert.match(desktopTableSource, /<el-table-column prop="name" label="股票"[\s\S]*?<StockIdentity[\s\S]*?:security="row"[\s\S]*?include-main[\s\S]*?compact/)
  assert.doesNotMatch(desktopTableSource, /<el-table-column prop="code" label="代码"/)
})

test('screener presents one human-readable stock universe without internal pool metadata', () => {
  assert.match(screenerSource, /`共 \$\{marketTotal\} 只股票`/)
  assert.match(screenerSource, /`筛选结果 \$\{displayRows\.length\} 只股票`/)
  assert.doesNotMatch(screenerSource, /池内标|label="股票池"|class="universe-badge"/)
  assert.doesNotMatch(screenerSource, /fetchScreenerMeta|meta\.universeCount|meta\.universeBatchNo|meta\.note/)
})

test('mobile screener reuses market badges including Shanghai and Shenzhen', () => {
  assert.match(screenerSource, /class="mobile-stock-identity"[\s\S]*?<StockIdentity :security="row" include-main compact \/>/)
  assert.doesNotMatch(screenerSource, /class="market-badge"/)
})

test('mobile screener uses compact actions and pagination', () => {
  assert.match(screenerSource, /class="mobile-filter-actions"/)
  assert.match(screenerSource, /class="mobile-pager"/)
  assert.match(screenerSource, /class="mobile-batch-results"/)
  assert.match(screenerSource, /\.mobile-filter-actions :deep\(\.el-button\)[\s\S]*?height:\s*36px;[\s\S]*?min-height:\s*36px;/)
  assert.match(screenerSource, /\.mobile-filter-actions :deep\(\.mobile-filter-reset\)[\s\S]*?width:\s*40px;[\s\S]*?min-width:\s*40px;/)
  assert.match(screenerSource, /\.mobile-pager-button\s*\{[\s\S]*?min-height:\s*36px;/)
  assert.match(screenerSource, /\.advanced-filter-toggle\s*\{[\s\S]*?width:\s*auto;[\s\S]*?min-height:\s*36px;/)
  assert.match(screenerSource, /@media \(max-width: 820px\)[\s\S]*?\.screener-mode-switch :deep\(\.el-segmented\)\s*\{[\s\S]*?width:\s*max-content;[\s\S]*?max-width:\s*100%;[\s\S]*?min-height:\s*36px;/)
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

test('desktop strategy workspace exposes all templates and practical explanations', () => {
  assert.match(screenerSource, /class="strategy-workspace"/)
  assert.match(screenerSource, /class="strategy-catalog"/)
  assert.match(screenerSource, /v-for="strategy in systemStrategies"/)
  assert.match(screenerSource, /item\.templateKey === 'PUBLIC_FIRST_BOARD_DISPERSION'/)
  assert.match(screenerSource, /class="strategy-core-idea"/)
  assert.match(screenerSource, />通俗理解</)
  assert.match(screenerSource, />操作方法</)
  assert.match(screenerSource, />风险纪律</)
  assert.match(screenerSource, /selectedStrategy\.guide\.executionSteps/)
  assert.match(screenerSource, /selectedStrategy\.guide\.riskNotes/)
  assert.match(screenerSource, /\.strategy-definition\s*\{[\s\S]*?max-width:\s*1160px;/)
})

test('desktop strategy catalog keeps rows scannable and distinguishes run modes', () => {
  assert.match(screenerSource, /<em :class="strategy\.runMode === 'CLOSE' \? 'is-close' : 'is-realtime'">/)
  assert.match(screenerSource, /\.strategy-catalog-group button\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\) 38px;[\s\S]*?min-height:\s*52px;[\s\S]*?border-top:/)
  assert.match(screenerSource, /\.strategy-catalog-group button em\.is-realtime\s*\{[\s\S]*?color:\s*var\(--accent\);/)
  assert.match(screenerSource, /\.strategy-catalog-group button em\.is-close\s*\{[\s\S]*?color:\s*#9a5b16;/)
})

test('strategy workspace collapses to one column on mobile', () => {
  assert.match(screenerSource, /\.strategy-workspace\s*\{[\s\S]*?grid-template-columns:\s*minmax\(220px, 280px\) minmax\(0, 1fr\);/)
  assert.match(screenerSource, /@media \(max-width: 820px\)[\s\S]*?\.strategy-workspace\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\);/)
})

test('mobile strategy header keeps the shared page-title treatment', () => {
  assert.doesNotMatch(screenerSource, /--page-title-size:/)
  assert.doesNotMatch(screenerSource, /\.screener-header > div:first-child > \.eyebrow\s*\{[\s\S]*?display:\s*none !important;/)
  assert.doesNotMatch(screenerSource, /\.screener-header h1::after\s*\{[\s\S]*?display:\s*none;/)
  assert.match(screenerSource, /\.screener-header\s*\{[\s\S]*?display:\s*block;[\s\S]*?margin-bottom:\s*12px;/)
})

test('mobile strategy selector stays full width without opening a search keyboard or zooming the viewport', () => {
  assert.doesNotMatch(mobileStrategySelectorSource, /\bfilterable\b/)
  assert.match(mobileStrategySelectorSource, /class="mobile-strategy-select"/)
  assert.match(screenerSource, /@media \(max-width: 820px\)[\s\S]*?\.strategy-toolbar\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\);[\s\S]*?justify-content:\s*stretch;/)
  assert.match(screenerSource, /\.strategy-toolbar > \*\s*\{[\s\S]*?min-width:\s*0;[\s\S]*?width:\s*100%;/)
  assert.match(screenerSource, /\.mobile-strategy-select :deep\(\.el-select__wrapper\)\s*\{[\s\S]*?min-height:\s*44px;[\s\S]*?font-size:\s*16px;/)
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

test('strategy definition identifies realtime and close run modes', () => {
  assert.match(screenerSource, /selectedStrategy\.runMode === 'CLOSE' \? '收盘策略' : '实时策略'/)
})

test('changing strategy clears results from the previously selected strategy', () => {
  assert.match(screenerSource, /@change="onStrategyChange"/)
  assert.match(screenerSource, /function onStrategyChange\(\) \{[\s\S]*strategyRunResult\.value = null[\s\S]*\}/)
})

test('strategy editor exposes reusable short-term daily rules', () => {
  assert.match(screenerSource, /value: 'DAYS_SINCE_LIMIT_UP'[\s\S]*?kind: 'integer'[\s\S]*?lookback: true/)
  assert.match(screenerSource, /value: 'VOLUME_MA_RATIO'[\s\S]*?kind: 'number'[\s\S]*?lookback: true/)
  assert.match(screenerSource, /value: 'CLOSE_MA_DISTANCE_PCT'[\s\S]*?kind: 'number'[\s\S]*?lookback: true/)
  assert.match(screenerSource, /value: 'BREAKOUT_PREVIOUS_HIGH'[\s\S]*?kind: 'boolean'[\s\S]*?lookback: true/)
  assert.match(screenerSource, /value: 'MA_BULLISH_ALIGNMENT'[\s\S]*?kind: 'boolean'/)
})
