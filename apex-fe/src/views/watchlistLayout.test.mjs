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
  assert.match(source, /同步已选（\{\{ selected\.length \}\}）/)
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

test('mobile daily actions stay compact and use consistent touch targets', () => {
  assert.match(
    source,
    /\.watchlist-action-buttons\s*\{[^}]*display:\s*grid;[^}]*grid-template-columns:\s*minmax\(0, 1\.2fr\) repeat\(2, minmax\(0, 1fr\)\);[^}]*max-width:\s*360px;/,
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
    /\.watchlist-action-buttons :deep\(\.watchlist-reload-action\)\s*\{[^}]*width:\s*44px;[^}]*height:\s*44px;/,
  )
})

test('wrapped mover names stay aligned after their group label', () => {
  assert.match(source, /class="mover-items"/)
  assert.match(
    source,
    /\.mover-group\s*\{[^}]*display:\s*grid;[^}]*grid-template-columns:\s*max-content minmax\(0, 1fr\);/,
  )
  assert.match(source, /\.mover-items\s*\{[^}]*display:\s*flex;[^}]*flex-wrap:\s*wrap;/)
})

test('watchlist keeps enough desktop height for scanning more rows', () => {
  assert.match(source, /class="watchlist-table"[\s\S]*height="calc\(100vh - 300px\)"/)
})

test('watchlist keeps the circulating market-value header and sort control on one line', () => {
  assert.match(source, /<el-table-column prop="circMv" width="128" sortable label-class-name="watchlist-circ-mv-header">/)
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
