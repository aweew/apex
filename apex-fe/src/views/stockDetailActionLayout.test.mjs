import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./StockView.vue', import.meta.url), 'utf8')

test('stock detail actions separate sync, research, and secondary tools', () => {
  assert.match(source, /class="sync-action-wrap"/)
  assert.match(source, /class="research-actions"/)
  assert.match(source, /class="action-secondary"/)
  assert.match(source, /<Refresh \/>/)
  assert.match(source, /<DataAnalysis \/>/)
  assert.match(source, /历史回测/)
  assert.match(source, /查看观察池/)
})

test('mobile stock detail actions use stable grids and touch targets', () => {
  assert.match(source, /@media \(max-width: 820px\)[\s\S]*?\.action-primary\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\);/)
  assert.match(source, /@media \(max-width: 820px\)[\s\S]*?\.research-actions\s*\{[\s\S]*?grid-template-columns:\s*repeat\(3, minmax\(0, 1fr\)\);/)
  assert.match(source, /@media \(max-width: 820px\)[\s\S]*?\.action-secondary\s*\{[\s\S]*?grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/)
  assert.match(source, /\.sync-action-wrap :deep\(\.sync-action\)[\s\S]*?min-height:\s*44px;/)
  assert.match(source, /\.research-actions :deep\(\.el-button\),[\s\S]*?min-height:\s*44px;/)
})

test('stock sync exposes independent progress without resizing the primary action', () => {
  assert.match(source, /aria-live="polite"/)
  assert.match(source, /日线 \{\{ syncStateLabel\(syncProgress\.bars\) \}\}/)
  assert.match(source, /行情 \{\{ syncStateLabel\(syncProgress\.quote\) \}\}/)
  assert.match(source, /const syncButtonLabel = computed/)
  assert.match(source, /if \(!code\.value \|\| syncingBars\.value\) return/)
  assert.match(source, /min-height:\s*64px;/)
})
