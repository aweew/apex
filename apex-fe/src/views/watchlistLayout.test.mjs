import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./WatchlistView.vue', import.meta.url), 'utf8')

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
