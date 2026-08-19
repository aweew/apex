import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const sectorSource = await readFile(new URL('./SectorBoardView.vue', import.meta.url), 'utf8')

test('sector page provides a dedicated mobile filter surface', () => {
  assert.match(sectorSource, /class="sector-mobile-filters"/)
  assert.match(sectorSource, /class="mobile-filter-primary"/)
  assert.match(sectorSource, /class="mobile-sort-strip"/)
  assert.match(sectorSource, /class="mobile-filter-summary"/)
  assert.match(sectorSource, /class="mobile-sector-shortcuts"/)
})

test('mobile sector filters combine sort direction and progressively disclose all metrics', () => {
  assert.match(sectorSource, /mobileSortExpanded = ref\(false\)/)
  assert.match(sectorSource, /function selectMobileSort\(value\)[\s\S]*?mobileSortExpanded\.value = false/)
  assert.match(sectorSource, /function toggleSortOrder\(\)/)
  assert.match(sectorSource, /:aria-label="order === 'desc' \? '切换为升序' : '切换为降序'"/)
  assert.match(sectorSource, /v-show="mobileSortExpanded"[\s\S]*?class="mobile-sort-overflow"/)
})

test('desktop sector filters remain separate from the mobile controls', () => {
  assert.match(sectorSource, /class="sector-filters sector-desktop-filters"/)
  assert.match(sectorSource, /class="sector-shortcuts sector-desktop-shortcuts"/)
  assert.match(sectorSource, /@media \(max-width: 560px\)[\s\S]*?\.sector-desktop-filters,[\s\S]*?\.sector-desktop-shortcuts\s*\{\s*display:\s*none;/)
})

test('mobile sector controls keep touch targets at least 44 pixels tall', () => {
  assert.match(sectorSource, /\.mobile-sort-chip\s*\{[\s\S]*?min-height:\s*44px;/)
  assert.match(sectorSource, /\.mobile-order-toggle\s*\{[\s\S]*?width:\s*44px;[\s\S]*?height:\s*44px;/)
})

test('mobile sector tabs keep three equal segments and a contained active surface', () => {
  const mobileStyles = sectorSource.slice(sectorSource.indexOf('@media (max-width: 560px)'))
  assert.match(mobileStyles, /\.tabs :deep\(\.el-tabs__nav\)\s*\{[\s\S]*?display:\s*grid;[\s\S]*?grid-template-columns:\s*repeat\(3, minmax\(0, 1fr\)\);[\s\S]*?float:\s*none;/)
  assert.match(mobileStyles, /\.tabs :deep\(\.el-tabs__item\)\s*\{[\s\S]*?width:\s*100%;/)
  assert.match(mobileStyles, /\.tabs :deep\(\.el-tabs__item\.is-active\)\s*\{[\s\S]*?border-radius:\s*5px;[\s\S]*?background:\s*var\(--accent\);[\s\S]*?color:\s*#fff;/)
})

test('mobile mainline cards keep headings compact and summaries inside each card', () => {
  const compactStyles = sectorSource.slice(
    sectorSource.indexOf('@media (max-width: 900px)'),
    sectorSource.indexOf('@media (max-width: 560px)'),
  )
  const mobileStyles = sectorSource.slice(sectorSource.indexOf('@media (max-width: 560px)'))

  assert.match(mobileStyles, /\.mainline-heading\s*\{[\s\S]*?flex-direction:\s*row;/)
  assert.match(compactStyles, /\.mainline-reason\s*\{[\s\S]*?overflow:\s*hidden;[\s\S]*?-webkit-line-clamp:\s*2;/)
  assert.match(compactStyles, /\.mainline-item::after\s*\{[\s\S]*?top:\s*26px;/)
  assert.doesNotMatch(mobileStyles, /\.mainline-item\s*\{[\s\S]*?min-height:\s*116px;/)
})

test('constituent refresh keeps cached rows visible and reuses the refresh response', () => {
  assert.match(sectorSource, /v-loading="drawerLoading"/)
  assert.doesNotMatch(sectorSource, /v-loading="drawerLoading \|\| drawerRefreshing"/)
  assert.match(
    sectorSource,
    /const res = await refreshSectorConstituents\([\s\S]*?const refreshed = res\.data\?\.constituents[\s\S]*?constituents\.value = \{ \.\.\.refreshed, items: sortedItems \}/,
  )
  assert.doesNotMatch(
    sectorSource,
    /await refreshSectorConstituents\([\s\S]*?await loadConstituents\(\)/,
  )
  assert.match(sectorSource, /v-model="drawerSortBy"[^>]*:disabled="drawerRefreshing"/)
})

test('stale constituent requests cannot overwrite a newly opened sector', () => {
  assert.match(sectorSource, /let constituentLoadSequence = 0/)
  assert.match(
    sectorSource,
    /const requestSequence = \+\+constituentLoadSequence[\s\S]*?requestSequence !== constituentLoadSequence[\s\S]*?currentSector\.value\?\.code !== sectorCode/,
  )
  assert.match(
    sectorSource,
    /async function openConstituents\(row\)[\s\S]*?constituentLoadSequence \+= 1[\s\S]*?currentSector\.value = row/,
  )
})
