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
  const mobileActions = appSource.slice(
    appSource.indexOf('<div class="mobile-top-actions">'),
    appSource.indexOf('<button\n        type="button"\n        class="nav-scrim"'),
  )
  const stockSectionIndex = appSource.indexOf('aria-labelledby="command-stock-title"')
  const routeSectionIndex = appSource.indexOf('aria-labelledby="command-route-title"')

  assert.match(appSource, /aria-label="个股搜索"/)
  assert.match(desktopActions, /class="desktop-stock-search"/)
  assert.match(desktopActions, /<input[\s\S]*?ref="desktopStockInputRef"[\s\S]*?v-model="query"[\s\S]*?type="search"[\s\S]*?aria-label="个股搜索"[\s\S]*?placeholder="搜索个股"/)
  assert.match(desktopActions, /@focus="openSearch"/)
  assert.match(desktopActions, /@input="onQueryInput"/)
  assert.match(desktopActions, /@keydown\.enter\.prevent="runSelectedCommand"/)
  assert.doesNotMatch(desktopActions, /class="search-btn stock-search-trigger"/)
  assert.match(mobileActions, /class="nav-icon-btn" aria-label="个股搜索"/)
  assert.match(appSource, /\.desktop-stock-search\s*\{[\s\S]*?width:\s*180px;[\s\S]*?height:\s*30px;/)
  assert.match(appSource, /@media \(max-width: 1100px\)[\s\S]*?\.desktop-stock-search\s*\{[\s\S]*?width:\s*132px;/)
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

test('desktop stock search uses a navigation dropdown while mobile keeps the full-screen search surface', () => {
  assert.match(appSource, /\.search-layer\s*\{[\s\S]*?inset:\s*56px 0 0;[\s\S]*?background:\s*transparent;/)
  assert.match(appSource, /\.search-panel\s*\{[\s\S]*?border-radius:\s*6px;/)
  assert.match(appSource, /<div v-if="isMobileViewport" class="search-head">/)
  assert.match(appSource, /@media \(max-width: 900px\)[\s\S]*?\.search-layer\s*\{[\s\S]*?inset:\s*0;[\s\S]*?background:\s*#fff;/)
  assert.match(appSource, /\.search-panel\s*\{[\s\S]*?background:\s*#fff;/)
})

test('navigation renders the current page data freshness instead of only service health', () => {
  assert.match(appSource, /dataFreshness/)
  assert.match(appSource, /data-status/)
  assert.match(appSource, /dataFreshness\.label/)
})

test('desktop account menu uses a compact icon while keeping identity inside the dropdown', () => {
  const desktopActions = appSource.slice(
    appSource.indexOf('<div class="nav-actions desktop-nav-actions">'),
    appSource.indexOf('<div\n        class="app-activity"'),
  )

  assert.match(desktopActions, /class="desktop-icon-btn user-menu"/)
  assert.match(desktopActions, /:aria-label="`打开账户菜单：\$\{currentUser\.nickName \|\| currentUser\.phone\}`"/)
  assert.match(desktopActions, /<User aria-hidden="true" \/>/)
  assert.doesNotMatch(desktopActions, /class="search-btn user-menu"/)
  assert.match(desktopActions, /class="user-dropdown-profile"[\s\S]*?currentUser\.nickName[\s\S]*?currentUser\.phone/)
  assert.match(appSource, /\.nav-actions\s*\{[\s\S]*?gap:\s*6px;/)
})

test('desktop navigation keeps secondary tools and account controls icon-sized', () => {
  const desktopActions = appSource.slice(
    appSource.indexOf('<div class="nav-actions desktop-nav-actions">'),
    appSource.indexOf('<div\n        class="app-activity"'),
  )

  assert.match(desktopActions, /class="desktop-icon-btn glossary-trigger"[\s\S]*?aria-label="打开名词百科"/)
  assert.match(desktopActions, /class="desktop-icon-btn fullscreen-trigger"/)
  assert.match(desktopActions, /class="desktop-icon-btn app-settings-trigger"/)
  assert.match(desktopActions, /class="desktop-icon-btn user-menu"/)
  assert.doesNotMatch(desktopActions, /<span>百科<\/span>/)
  assert.match(appSource, /\.desktop-icon-btn\s*\{[\s\S]*?width:\s*30px;[\s\S]*?height:\s*30px;/)
})
