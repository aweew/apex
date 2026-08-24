import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./WatchlistView.vue', import.meta.url), 'utf8')

test('watchlist keeps daily actions clear and moves low-frequency sync tasks into a menu', () => {
  assert.match(source, /class="watchlist-action-panel"/)
  assert.match(source, /同步已选（\{\{ selected\.length \}\}）/)
  assert.match(source, /<el-dropdown[^>]*@command="onSyncCommand"/)
  assert.match(source, /command="fill-bars"[\s\S]*补齐缺失 K 线/)
  assert.match(source, /command="fill-quotes"[\s\S]*补齐缺失行情/)
})

test('watchlist presents market signals as a concise summary and keeps correlation details collapsed', () => {
  assert.match(source, /class="watchlist-insights"/)
  assert.match(source, /<el-collapse v-if="corr\?\.codes\?\.length" class="correlation-collapse">/)
  assert.match(source, /title="同涨同跌风险"/)
  assert.doesNotMatch(source, /<el-alert/)
})

test('watchlist provides a compact mobile layout for importing, actions, and filters', () => {
  assert.match(source, /@media \(max-width: 720px\)/)
  assert.match(source, /\.watchlist-import[\s\S]*grid-template-columns:\s*1fr;/)
  assert.match(source, /\.watchlist-filter-controls[\s\S]*grid-template-columns:\s*1fr 1fr;/)
})

test('watchlist keeps enough desktop height for scanning more rows', () => {
  assert.match(source, /class="watchlist-table"[\s\S]*height="calc\(100vh - 300px\)"/)
})
