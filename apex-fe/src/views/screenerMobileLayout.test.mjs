import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const screenerSource = await readFile(new URL('./ScreenerView.vue', import.meta.url), 'utf8')

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
