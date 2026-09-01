import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const sectorSource = await readFile(new URL('./SectorBoardView.vue', import.meta.url), 'utf8')
const sectorApiSource = await readFile(new URL('../api/sector.js', import.meta.url), 'utf8')
const liquidTabsSource = await readFile(new URL('../components/LiquidGlassSegmented.vue', import.meta.url), 'utf8')

test('sector rotation requests and labels five trading days', () => {
  assert.match(sectorSource, /const ROTATION_DAY_COUNT = 5/)
  assert.match(sectorSource, /fetchSectorRotation\(\{ days: ROTATION_DAY_COUNT, type \}\)/)
  assert.match(sectorSource, /近 5 个交易日 Top5/)
  assert.match(sectorApiSource, /fetchSectorRotation\(\{ days = 5, type = 'INDUSTRY' \} = \{\}\)/)
})

test('sector page provides a dedicated mobile filter surface', () => {
  assert.match(sectorSource, /class="sector-mobile-filters"/)
  assert.match(sectorSource, /class="mobile-filter-primary"/)
  assert.match(sectorSource, /class="mobile-sort-strip"/)
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

test('embedded mobile sector controls stay compact without repeating the page title', () => {
  assert.match(sectorSource, /<div v-if="!embedded" class="sector-title-block">/)
  assert.doesNotMatch(sectorSource, /class="mobile-filter-summary"/)
  assert.match(sectorSource, /\.mobile-filter-primary :deep\(\.el-input__wrapper\)\s*\{[\s\S]*?min-height:\s*36px;/)
  assert.match(sectorSource, /\.mobile-sort-chip\s*\{[\s\S]*?min-height:\s*34px;/)
  assert.match(sectorSource, /\.mobile-order-toggle\s*\{[\s\S]*?width:\s*36px;[\s\S]*?height:\s*36px;/)
  assert.match(sectorSource, /\.mobile-sector-shortcuts\s*\{[\s\S]*?display:\s*flex;[\s\S]*?justify-content:\s*flex-end;/)
})

test('sector type switch uses the reusable liquid glass segmented control', () => {
  assert.match(sectorSource, /import LiquidGlassSegmented from '\.\.\/components\/LiquidGlassSegmented\.vue'/)
  assert.match(sectorSource, /<LiquidGlassSegmented[\s\S]*?v-model="activeTab"[\s\S]*?:options="TAB_OPTIONS"/)
  assert.match(liquidTabsSource, /grid-template-columns: repeat\(var\(--liquid-count\), minmax\(0, 1fr\)\)/)
  assert.match(liquidTabsSource, /backdrop-filter: blur\(18px\) saturate\(170%\)/)
  assert.match(liquidTabsSource, /transform 520ms cubic-bezier\(0\.22, 1\.35, 0\.36, 1\)/)
  assert.match(liquidTabsSource, /@media \(prefers-reduced-motion: reduce\)/)
})

test('tab requests update only the ranking surface and reject stale responses', () => {
  assert.match(sectorSource, /v-loading="loading && !board"/)
  assert.match(sectorSource, /class="ranking-content"/)
  assert.match(sectorSource, /loading && loadedBoardType !== activeTab/)
  assert.match(sectorSource, /const requestSequence = \+\+boardLoadSequence/)
  assert.match(sectorSource, /if \(requestSequence !== boardLoadSequence\) return/)
  assert.doesNotMatch(sectorSource, /v-loading="loading \|\| refreshing"/)
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

test('constituent drawer keeps metadata, controls, and table columns compact', () => {
  const drawerTemplate = sectorSource.slice(
    sectorSource.indexOf('<el-drawer'),
    sectorSource.indexOf('</el-drawer>'),
  )

  assert.match(drawerTemplate, /size="440px"/)
  assert.match(drawerTemplate, /v-if="constituentDataTime" class="drawer-snapshot"/)
  assert.doesNotMatch(drawerTemplate, /更新时间/)
  assert.match(drawerTemplate, /:icon="Refresh"[\s\S]*?aria-label="刷新成分股"/)
  assert.match(drawerTemplate, /class="constituent-table"/)
  assert.match(drawerTemplate, /prop="name" label="股票" width="132"/)
  assert.match(drawerTemplate, /prop="latestPrice" label="最新价" width="96"/)
  assert.match(drawerTemplate, /<el-table-column width="96" sortable prop="pctChg">/)
  assert.match(drawerTemplate, /label="操作" min-width="64"/)
  assert.match(sectorSource, /:global\(\.sector-drawer\)\s*\{[^}]*width:\s*min\(440px, 100vw\) !important;/)
  assert.match(
    sectorSource,
    /:global\(\.sector-drawer \.drawer-actions\)\s*\{[^}]*display:\s*grid;[^}]*grid-template-columns:\s*minmax\(0, 1fr\);/,
  )
  assert.match(
    sectorSource,
    /:global\(\.sector-drawer \.drawer-controls\)\s*\{[^}]*grid-template-columns:\s*minmax\(0, 1fr\) minmax\(0, 0\.82fr\) 36px;/,
  )
  assert.match(
    sectorSource,
    /:global\(\.sector-drawer \.constituent-table th\.is-sortable > \.cell\)\s*\{[^}]*display:\s*flex;[^}]*align-items:\s*center;[^}]*white-space:\s*nowrap;/,
  )
  assert.match(
    sectorSource,
    /:global\(\.sector-drawer \.constituent-table \.caret-wrapper\)\s*\{[^}]*flex:\s*0 0 24px;/,
  )
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

test('sector route opens requested code or exact-name constituents once after the board loads', () => {
  assert.match(sectorSource, /const pendingSectorCode = ref\(''\)/)
  assert.match(
    sectorSource,
    /function applyRouteQuery\(\)[\s\S]*?pendingSectorCode\.value = String\(route\.query\.code \|\| ''\)\.trim\(\)/,
  )
  assert.match(
    sectorSource,
    /async function openRouteSector\(\)[\s\S]*?const sectorName = String\(route\.query\.q \|\| ''\)\.trim\(\)[\s\S]*?sectorCode \? row\.code === sectorCode : row\.name === sectorName[\s\S]*?pendingSectorCode\.value = ''[\s\S]*?await openConstituents\(sector\)/,
  )
  assert.match(sectorSource, /await openRouteSector\(\)/)
  assert.match(
    sectorSource,
    /route\.query\.type, route\.query\.code[\s\S]*?pendingSectorCode\.value = String\(code \|\| ''\)\.trim\(\)[\s\S]*?activeTab\.value = type[\s\S]*?return[\s\S]*?openRouteSector\(\)/,
  )
  assert.match(sectorSource, /@closed="clearRouteSector"/)
})

test('an explicit concept type is preserved when filtering a legacy theme by name', () => {
  assert.match(
    sectorSource,
    /function applyRouteQuery\(\)[\s\S]*?if \(q\) \{[\s\S]*?nameFilter\.value = q[\s\S]*?if \(!TAB_META\[type\]\) \{[\s\S]*?activeTab\.value = 'THEME'/,
  )
})

test('sector ranking renders a fixed page instead of the complete board list', () => {
  assert.match(sectorSource, /const RANKING_PAGE_SIZE = 50/)
  assert.match(sectorSource, /const rankingPage = ref\(1\)/)
  assert.match(
    sectorSource,
    /const pagedItems = computed\(\(\) => \{[\s\S]*?const start = \(rankingPage\.value - 1\) \* RANKING_PAGE_SIZE[\s\S]*?return items\.value\.slice\(start, start \+ RANKING_PAGE_SIZE\)/,
  )
  assert.match(sectorSource, /<el-table[\s\S]*?:data="pagedItems"/)
})

test('sector ranking paging keeps global ranks and resets after list-affecting changes', () => {
  assert.match(sectorSource, /rankingPage\.value = 1/)
  assert.match(sectorSource, /rankingPageOffset \+ \$index \+ 1/)
  assert.match(sectorSource, /class="sector-pagination"/)
  assert.match(sectorSource, /@current-change="onRankingPageChange"/)
  assert.match(sectorSource, /class="sector-mobile-pagination"/)
  assert.match(sectorSource, /@click="onRankingPageChange\(rankingPage - 1\)"/)
  assert.match(sectorSource, /@click="onRankingPageChange\(rankingPage \+ 1\)"/)
})
