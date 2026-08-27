import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const appSource = await readFile(new URL('../App.vue', import.meta.url), 'utf8')

test('command center searches routes and stocks from the same overlay', () => {
  assert.match(appSource, /const commandResults = computed/)
  assert.match(appSource, /const filteredRouteCommands = computed/)
  assert.match(appSource, /goCommand\(item\)/)
  assert.match(appSource, /快捷入口/)
  assert.match(appSource, /页面与操作/)
  assert.match(appSource, /股票/)
})

test('stock search is a first-level action and supports pinyin abbreviation discovery', () => {
  const desktopActions = appSource.slice(
    appSource.indexOf('<div class="nav-actions desktop-nav-actions">'),
    appSource.indexOf('<div\n        class="app-activity"'),
  )
  const stockSectionIndex = appSource.indexOf('aria-labelledby="command-stock-title"')
  const routeSectionIndex = appSource.indexOf('aria-labelledby="command-route-title"')

  assert.match(appSource, /aria-label="个股搜索"/)
  assert.match(desktopActions, /class="search-btn stock-search-trigger"/)
  assert.match(desktopActions, /<span>个股搜索<\/span>/)
  assert.match(appSource, /placeholder="输入代码、名称或拼音缩写"/)
  assert.match(appSource, /const commandResults = computed\(\(\) => \[\.\.\.stockCommandResults\.value, \.\.\.filteredRouteCommands\.value\]\)/)
  assert.ok(stockSectionIndex >= 0 && routeSectionIndex > stockSectionIndex)
})

test('command center supports keyboard selection instead of only opening the first stock result', () => {
  assert.match(appSource, /function moveCommandSelection\(step\)/)
  assert.match(appSource, /@keydown\.down\.prevent="moveCommandSelection\(1\)"/)
  assert.match(appSource, /@keydown\.up\.prevent="moveCommandSelection\(-1\)"/)
  assert.match(appSource, /@keydown\.enter\.prevent="runSelectedCommand"/)
  assert.match(appSource, /:aria-selected="index === commandSelection"/)
})

test('command center exposes stable combobox semantics and announces dynamic results', () => {
  assert.match(appSource, /role="combobox"/)
  assert.match(appSource, /aria-controls="command-results"/)
  assert.match(appSource, /:aria-expanded="searchOpen"/)
  assert.match(appSource, /aria-autocomplete="list"/)
  assert.match(appSource, /id="command-result-status" class="sr-only" role="status" aria-live="polite"/)
  assert.match(appSource, /const commandResultStatus = computed/)
  assert.match(appSource, /<div id="command-results" class="search-body">/)
})

test('command center keeps shortcut hints visible outside its scrollable result area', () => {
  assert.match(
    appSource,
    /<\/div>\s*<footer class="search-keys" aria-label="快捷键提示">上下键选择 · Enter 打开 · Ctrl\+\/ 名词百科<\/footer>/,
  )
  assert.match(
    appSource,
    /\.search-body\s*\{[\s\S]*?flex:\s*1;[\s\S]*?min-height:\s*0;[\s\S]*?overflow:\s*auto;/,
  )
  assert.match(
    appSource,
    /\.search-keys\s*\{[\s\S]*?flex:\s*0 0 auto;[\s\S]*?border-top:/,
  )
})

test('command center uses an opaque surface and a legible modal overlay', () => {
  assert.match(appSource, /\.search-layer\s*\{[\s\S]*?background:\s*rgba\(15, 23, 42, 0\.42\);/)
  assert.match(appSource, /\.search-panel\s*\{[\s\S]*?background:\s*#fff;/)
})

test('navigation renders the current page data freshness instead of only service health', () => {
  assert.match(appSource, /dataFreshness/)
  assert.match(appSource, /data-status/)
  assert.match(appSource, /dataFreshness\.label/)
})
