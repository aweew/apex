import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./StockView.vue', import.meta.url), 'utf8')

test('stock detail embeds the factor center as a lazy tab', () => {
  assert.match(source, /import FactorCenterView from '\.\/FactorCenterView\.vue'/)
  assert.match(source, /<el-tab-pane label="因子" name="factors" lazy>/)
  assert.match(source, /<FactorCenterView\s+embedded\s+:stock-code="String\(basic\?\.code \|\| code\)\.trim\(\)"/)
})

test('stock detail keeps only the compact high-value action toolbar', () => {
  const actions = source.slice(source.indexOf('<div class="actions">'), source.indexOf('</header>'))

  assert.match(actions, /class="stock-action-toolbar"/)
  assert.match(actions, /class="stock-icon-action sync-action"[\s\S]*?aria-label="syncButtonLabel"/)
  assert.doesNotMatch(actions, /class="stock-icon-action sync-action"[\s\S]*?type="primary"/)
  assert.match(actions, /class="stock-icon-action observe-action"[\s\S]*?aria-label="加入观察池"/)
  assert.match(actions, /历史回测/)
  assert.match(actions, /模拟买/)
  assert.doesNotMatch(actions, /综合研判|router\.push\('\/decision'\)|>估值<|查看观察池/)
})

test('mobile stock detail actions stay on one stable touch-friendly row', () => {
  const mobileStyles = source.slice(source.indexOf('@media (max-width: 820px)'))

  assert.match(mobileStyles, /\.stock-action-toolbar\s*\{[^}]*grid-template-columns:\s*44px 44px repeat\(2, minmax\(0, 1fr\)\);/)
  assert.match(mobileStyles, /\.stock-action-toolbar :deep\(\.el-button\)\s*\{[^}]*min-height:\s*44px;/)
  assert.match(mobileStyles, /\.stock-action-toolbar :deep\(\.stock-icon-action\)\s*\{[^}]*width:\s*44px;[^}]*min-width:\s*44px;/)
})

test('stock sync exposes independent progress without resizing the primary action', () => {
  assert.match(source, /aria-live="polite"/)
  assert.match(source, /日线 \{\{ syncStateLabel\(syncProgress\.bars\) \}\}/)
  assert.match(source, /行情 \{\{ syncStateLabel\(syncProgress\.quote\) \}\}/)
  assert.match(source, /const syncButtonLabel = computed/)
  assert.match(source, /if \(!code\.value \|\| syncingBars\.value\) return/)
  assert.match(source, /\.sync-progress,[\s\S]*?\.sync-result\s*\{[^}]*min-height:\s*15px;/)
  assert.match(source, /\.stock-action-toolbar :deep\(\.sync-action\)\s*\{[\s\S]*?background:\s*#fff;/)
  assert.match(source, /:deep\(\.sync-action:hover:not\(:disabled\)\)\s*\{[\s\S]*?background:\s*rgba\(0, 113, 227, 0\.06\);/)
})

test('stock detail only shows actionable daily-bar status notes', () => {
  assert.match(source, /note\.value = data\.needSyncBars \? data\.note \|\| '' : ''/)
})
