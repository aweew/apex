import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const appSource = await readFile(new URL('../App.vue', import.meta.url), 'utf8')
const sharedStyles = await readFile(new URL('../style.css', import.meta.url), 'utf8')
const glossarySource = await readFile(new URL('../components/GlossaryPanel.vue', import.meta.url), 'utf8')

test('mobile navigation owns scrolling inside a dedicated drawer body', () => {
  assert.match(appSource, /class="mobile-menu-scroll"/)
  assert.match(appSource, /\.links\s*\{[\s\S]*?height:\s*100dvh;[\s\S]*?overflow:\s*hidden;/)
  assert.match(appSource, /\.mobile-menu-scroll\s*\{[\s\S]*?min-height:\s*0;[\s\S]*?overflow-y:\s*auto;/)
  assert.match(appSource, /\.mobile-menu-scroll\s*\{[\s\S]*?touch-action:\s*pan-y;/)
  assert.match(appSource, /\.mobile-menu-scroll\s*\{[\s\S]*?-webkit-overflow-scrolling:\s*touch;/)
  const mobileStyles = appSource.slice(appSource.indexOf('@media (max-width: 900px)'))
  assert.match(mobileStyles, /\.nav-group\s*\{[^}]*flex:\s*0 0 auto;/)
  assert.match(appSource, /class="mobile-menu-scroll"[\s\S]*?<\/div>\s*<div class="mobile-menu-actions">/)
  assert.match(appSource, /\.mobile-menu-actions\s*\{[\s\S]*?flex:\s*0 0 auto;/)
  assert.match(
    appSource,
    /\.mobile-menu-actions\s*\{[^}]*grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/,
  )
})

test('mobile navigation keeps glossary beside search and removes density configuration', () => {
  const topActions = appSource.slice(
    appSource.indexOf('<div class="mobile-top-actions">'),
    appSource.indexOf('<button\n        v-if="mobileMenuOpen"'),
  )
  const menuActions = appSource.slice(
    appSource.indexOf('<div class="mobile-menu-actions">'),
    appSource.indexOf('</div>\n      </div>\n      <div class="nav-actions desktop-nav-actions">'),
  )

  const searchIndex = topActions.indexOf('aria-label="个股搜索"')
  const glossaryIndex = topActions.indexOf('aria-label="名词百科"')
  const menuIndex = topActions.indexOf('aria-label="打开菜单"')
  assert.ok(searchIndex >= 0 && glossaryIndex > searchIndex && menuIndex > glossaryIndex)
  assert.doesNotMatch(menuActions, /名词百科/)
  assert.match(menuActions, /class="health"[\s\S]*?class="mobile-action-btn mobile-logout-btn"/)
  assert.doesNotMatch(appSource, /denseMode|toggleDense|apex\.ui\.dense|紧凑密度|舒适密度|Ctrl\+Shift\+D/)
  assert.doesNotMatch(sharedStyles, /\.shell\.dense/)
})

test('mobile document keeps viewport scrolling so navigation and module title stay sticky', () => {
  const mobileMenuWatch = appSource.slice(
    appSource.indexOf('watch(\n  mobileMenuOpen'),
    appSource.indexOf('watch(\n  searchOpen'),
  )
  const searchWatch = appSource.slice(
    appSource.indexOf('watch(\n  searchOpen'),
    appSource.indexOf('\nonMounted('),
  )
  const glossaryWatch = glossarySource.slice(
    glossarySource.indexOf('watch(\n  visible'),
    glossarySource.indexOf('\nfunction isMobileViewport'),
  )
  const mobileSharedStyles = sharedStyles.slice(sharedStyles.indexOf('@media (max-width: 900px)'))
  assert.doesNotMatch(mobileSharedStyles, /html,\s*body\s*\{[^}]*overflow-y:\s*auto;/)
  assert.match(appSource, /\.nav\s*\{[^}]*position:\s*sticky;[^}]*top:\s*0;/)
  assert.match(appSource, /window\.addEventListener\('scroll', scheduleMobileModuleTitle/)
  assert.match(mobileMenuWatch, /document\.documentElement\.classList\.toggle\('mobile-menu-open', open\)/)
  assert.match(mobileMenuWatch, /\{ immediate: true \}/)
  assert.match(searchWatch, /document\.documentElement\.classList\.toggle\('search-open', open\)/)
  assert.match(searchWatch, /\{ immediate: true \}/)
  assert.match(glossaryWatch, /document\.documentElement\.classList\.toggle\('glossary-open', isVisible\)/)
  assert.match(glossaryWatch, /\{ immediate: true \}/)
})

test('mobile search and glossary keep their content regions independently scrollable', () => {
  assert.match(appSource, /\.search-body\s*\{[\s\S]*?min-height:\s*0;[\s\S]*?overflow-y:\s*auto;/)
  assert.match(appSource, /\.search-body\s*\{[\s\S]*?touch-action:\s*pan-y;/)
  assert.match(glossarySource, /\.glossary-list\s*\{[\s\S]*?overflow-y:\s*auto;[\s\S]*?touch-action:\s*pan-y;/)
  assert.match(glossarySource, /\.glossary-detail\s*\{[\s\S]*?overflow-y:\s*auto;[\s\S]*?touch-action:\s*pan-y;/)
})

test('glossary restores keyboard focus to its original trigger after close', () => {
  assert.match(glossarySource, /if \(!visible\.value\) returnFocus = document\.activeElement/)
  assert.match(glossarySource, /const focusTarget = returnFocus/)
  assert.match(glossarySource, /nextTick\(\(\) => focusTarget\?\.focus\?\.\(\)\)/)
})

test('glossary announces search results and exposes selected filters semantically', () => {
  assert.match(glossarySource, /aria-controls="glossary-results"/)
  assert.match(glossarySource, /:aria-pressed="!category"/)
  assert.match(glossarySource, /:aria-pressed="category === cat"/)
  assert.match(glossarySource, /class="glossary-result-count" role="status" aria-live="polite" aria-atomic="true"/)
  assert.match(glossarySource, /<ul id="glossary-results" class="glossary-list">/)
  assert.match(glossarySource, /:aria-pressed="active\?\.id === term\.id"/)
})

test('shared mobile dialogs and drawers retain vertical touch scrolling', () => {
  assert.match(sharedStyles, /\.el-dialog__body,[\s\S]*?\.el-drawer__body\s*\{[\s\S]*?min-height:\s*0;[\s\S]*?overflow-y:\s*auto;/)
  assert.match(sharedStyles, /\.el-dialog__body,[\s\S]*?\.el-drawer__body\s*\{[\s\S]*?touch-action:\s*auto;/)
})

test('desktop navigation keeps its original horizontal layout', () => {
  const desktopStyles = appSource.slice(0, appSource.indexOf('@media (max-width: 900px)'))
  assert.match(desktopStyles, /\.mobile-menu-scroll\s*\{\s*display:\s*contents;/)
})

test('mobile sticky title keeps only the page name in the narrow navigation slot', () => {
  assert.match(appSource, /\? heading\.textContent\.trim\(\)/)
  assert.doesNotMatch(appSource, /\$\{heading\.textContent\.trim\(\)\} · \$\{module\.textContent\.trim\(\)\}/)
})

test('brand navigation replaces history and the dashboard never shows a back action', () => {
  assert.match(
    appSource,
    /<RouterLink[\s\S]*?class="brand-block"[\s\S]*?to="\/dashboard"[\s\S]*?replace/,
  )
  assert.match(
    appSource,
    /function syncMobileBackTarget\(\)\s*\{[\s\S]*?route\.path === '\/dashboard'[\s\S]*?mobileBackPath\.value = ''[\s\S]*?mobileBackLabel\.value = ''[\s\S]*?return/,
  )
})
