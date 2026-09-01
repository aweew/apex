import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./WatchlistView.vue', import.meta.url), 'utf8')
const operationColumnSource = source.slice(
  source.indexOf('<el-table-column v-if="showActionColumn" label="操作"'),
  source.indexOf('</el-table-column>\n    </el-table>'),
)

test('watchlist keeps daily actions clear and moves low-frequency sync tasks into a menu', () => {
  assert.match(source, /class="watchlist-action-panel"/)
  assert.match(source, /同步已选（\$\{selected\.length\}）/)
  assert.match(source, /同步（\$\{selected\.length\}）/)
  assert.match(source, /<el-dropdown[^>]*@command="onSyncCommand"/)
  assert.match(source, /command="import-watchlist"[\s\S]*导入自选/)
  assert.match(source, /command="fill-bars"[\s\S]*补齐缺失 K 线/)
  assert.match(source, /command="fill-quotes"[\s\S]*补齐缺失行情/)
})

test('watchlist keeps import details in a dialog while retaining an empty-state entry point', () => {
  assert.match(source, /const importDialogVisible = ref\(false\)/)
  assert.match(source, /function openImportDialog\(\)/)
  assert.match(source, /<el-dialog v-model="importDialogVisible" title="导入自选"/)
  assert.match(source, /<el-button type="primary" @click="openImportDialog">导入自选<\/el-button>/)
  assert.doesNotMatch(source, /class="watchlist-import"/)
})

test('watchlist presents market signals as a concise summary and keeps correlation details collapsed', () => {
  assert.match(source, /class="watchlist-insights"/)
  assert.match(source, /<el-collapse v-if="corr\?\.codes\?\.length" class="correlation-collapse">/)
  assert.match(source, /title="同涨同跌风险"/)
  assert.doesNotMatch(source, /<el-alert/)
})

test('watchlist provides a compact layout for actions and filters', () => {
  assert.match(source, /@media \(max-width: 720px\)/)
  assert.match(
    source,
    /\.watchlist-filter-controls\s*\{[^}]*display:\s*flex;[^}]*flex-wrap:\s*wrap;/,
  )
  assert.match(source, /class="watchlist-keyword-filter"/)
  assert.match(source, /class="watchlist-sort-control"/)
  assert.match(source, /v-model="sortMode"/)
  assert.doesNotMatch(source, /sortByPct/)
})

test('mobile watchlist keeps search stable on iOS and collapses secondary filters', () => {
  assert.match(source, /class="watchlist-primary-filter-row"/)
  assert.match(source, /:prefix-icon="Search"[\s\S]*inputmode="search"/)
  assert.match(source, /class="watchlist-filter-toggle"[\s\S]*:aria-expanded="filtersExpanded"/)
  assert.match(source, /v-show="!isMobileViewport \|\| filtersExpanded"/)
  assert.match(
    source,
    /\.watchlist-keyword-filter :deep\(\.el-input__inner\),[\s\S]*?font-size:\s*16px;/,
  )
  assert.match(
    source,
    /\.watchlist-primary-filter-row\s*\{[^}]*display:\s*grid;[^}]*grid-template-columns:\s*minmax\(0, 1fr\) auto;/,
  )
})

test('watchlist exposes active filter count and a focused reset action', () => {
  assert.match(source, /const activeFilterCount = computed/)
  assert.match(source, /class="watchlist-filter-count"/)
  assert.match(source, /function clearAdvancedFilters\(\)/)
  assert.match(source, /class="watchlist-filter-reset"[\s\S]*@click="clearAdvancedFilters"/)
  assert.match(source, /inputmode="decimal"[\s\S]*aria-label="市盈率上限"/)
})

test('mobile daily actions stay compact and use consistent touch targets', () => {
  assert.match(
    source,
    /class="watchlist-action-buttons"[\s\S]*?@click="onRefreshQuotes"[\s\S]*?@click="onSyncSelected"/,
  )
  assert.match(
    source,
    /class="watchlist-list-heading"[\s\S]*?class="watchlist-reload-action"[\s\S]*?@click="loadList"/,
  )
  assert.match(
    source,
    /\.watchlist-action-buttons\s*\{[^}]*display:\s*grid;[^}]*grid-template-columns:\s*repeat\(3, minmax\(0, 1fr\)\);[^}]*width:\s*100%;/,
  )
  assert.match(
    source,
    /\.watchlist-action-buttons :deep\(\.el-button\),[\s\S]*?\.watchlist-action-buttons :deep\(\.el-dropdown\)\s*\{[^}]*width:\s*100%;/,
  )
  assert.match(
    source,
    /\.watchlist-action-buttons :deep\(\.el-button\)\s*\{[^}]*min-height:\s*44px;/,
  )
  assert.match(
    source,
    /\.watchlist-list-heading :deep\(\.watchlist-reload-action\)\s*\{[^}]*width:\s*44px;[^}]*height:\s*44px;/,
  )
  assert.doesNotMatch(source, /\.watchlist-reload-action\s*\{[^}]*position:\s*absolute;/)
})

test('wrapped mover names stay aligned after their group label', () => {
  assert.match(source, /class="mover-items"/)
  assert.match(
    source,
    /\.mover-group\s*\{[^}]*display:\s*grid;[^}]*grid-template-columns:\s*max-content minmax\(0, 1fr\);/,
  )
  assert.match(source, /\.mover-items\s*\{[^}]*display:\s*flex;[^}]*flex-wrap:\s*wrap;/)
})

test('watchlist uses natural page scrolling and limits rendered rows', () => {
  assert.match(source, /const pageSize = computed\(\(\) => \(isMobileViewport\.value \? 30 : 50\)\)/)
  assert.match(source, /const pagedRows = computed/)
  assert.match(source, /:data="pagedRows"/)
  assert.match(source, /class="watchlist-pagination"/)
  assert.match(source, /watchlistTableRef\.value\?\.clearSelection\(\)/)
  assert.doesNotMatch(source, /height="calc\(100vh - 300px\)"/)
  assert.doesNotMatch(source, /height:\s*calc\(100vh - 360px\)/)
})

test('watchlist sorts the complete filtered result before pagination', () => {
  assert.match(source, /const sortedRows = computed/)
  assert.match(source, /sortable="custom"/)
  assert.match(source, /@sort-change="handleTableSort"/)
  assert.match(source, /row-key="code"/)
  assert.match(source, /reserve-selection/)
})

test('watchlist keeps the circulating market-value header and sort control on one line', () => {
  assert.match(source, /<el-table-column prop="circMv" width="128" sortable="custom" label-class-name="watchlist-circ-mv-header">/)
  assert.match(
    source,
    /\.watchlist-table :deep\(th\.watchlist-circ-mv-header > \.cell\)\s*\{[^}]*display:\s*flex;[^}]*align-items:\s*center;[^}]*white-space:\s*nowrap;/,
  )
  assert.match(
    source,
    /\.watchlist-table :deep\(th\.watchlist-circ-mv-header \.caret-wrapper\)\s*\{[^}]*flex:\s*0 0 24px;/,
  )
})

test('mobile watchlist replaces the fixed action column with one compact row menu', () => {
  assert.match(source, /import \{ resolveActionColumnVisible \} from '\.\.\/utils\/responsiveTable\.js'/)
  assert.match(source, /const isMobileViewport = computed\(\(\) => viewportWidth\.value <= 820\)/)
  assert.match(source, /const showActionColumn = computed\(\(\) => resolveActionColumnVisible\(viewportWidth\.value\)\)/)
  assert.match(operationColumnSource, /v-if="showActionColumn"[\s\S]*fixed="right"/)
  assert.match(source, /v-if="isMobileViewport"[\s\S]*class="watchlist-row-actions-trigger"[\s\S]*@command="handleWatchlistRowAction\(\$event, row\)"/)
  assert.match(source, /command="detail"[\s\S]*K 线[\s\S]*command="observe"[\s\S]*加入观察[\s\S]*command="backtest"[\s\S]*回测/)
  assert.match(source, /\.watchlist-row-actions-trigger\s*\{[\s\S]*?width:\s*44px;[\s\S]*?height:\s*44px;/)
  assert.match(source, /window\.addEventListener\('resize', syncViewportWidth\)/)
  assert.match(source, /window\.removeEventListener\('resize', syncViewportWidth\)/)
})
