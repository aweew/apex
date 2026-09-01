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
  assert.doesNotMatch(menuActions, /dataFreshness|data-status|class="health"/)
  assert.match(menuActions, /class="mobile-action-btn mobile-logout-btn"/)
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
  assert.match(appSource, /const mobileMenuDrawerStyle = computed\(\(\) => \(isMobileViewport\.value/)
  assert.match(appSource, /:inert="isMobileViewport && !mobileMenuActive"/)
})

test('mobile navigation drawer follows horizontal page swipes progressively', () => {
  assert.match(appSource, /@touchstart="onMobileMenuTouchStart"/)
  assert.match(appSource, /@touchmove="onMobileMenuTouchMove"/)
  assert.match(appSource, /@touchend="finishMobileMenuSwipe"/)
  assert.match(appSource, /@touchcancel="cancelMobileMenuSwipe"/)
  assert.match(appSource, /:style="mobileMenuDrawerStyle"/)
  assert.match(appSource, /:style="mobileMenuScrimStyle"/)
  assert.match(appSource, /window\.requestAnimationFrame/)
  assert.match(appSource, /\.nav-scrim\s*\{[^}]*background:\s*rgba\(15, 23, 42, 0\.42\);[^}]*opacity:\s*0;/)
  assert.match(appSource, /\.links\s*\{[^}]*width:\s*min\(calc\(100vw - 64px\), 360px\);[^}]*max-width:\s*none;/)
  assert.match(appSource, /\.shell\s*\{[^}]*overflow-x:\s*clip;/)
  assert.doesNotMatch(appSource, /--mobile-menu-page-offset/)
  assert.doesNotMatch(appSource, /menuContentOffset/)
  assert.match(appSource, /\.links\.dragging\s*\{[^}]*transition:\s*none;/)
  assert.match(appSource, /\.mobile-menu-dragging \.nav-scrim\s*\{[^}]*transition:\s*none;/)
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

test('mobile bottom navigation docks to the viewport edge without covering final controls', () => {
  const bottomNavStart = appSource.indexOf('class="mobile-bottom-nav"')
  const bottomNavTemplate = appSource.slice(bottomNavStart, appSource.indexOf('</nav>', bottomNavStart))
  assert.match(appSource, /class="mobile-bottom-nav"/)
  assert.match(appSource, /aria-label="常用功能"/)
  assert.match(appSource, /data-mobile-swipe-ignore/)
  assert.match(appSource, /class="mobile-bottom-nav__item"/)
  assert.match(bottomNavTemplate, /:to="item\.to"/)
  assert.doesNotMatch(bottomNavTemplate, /@click="setMobileMenu\(true\)"/)
  assert.match(appSource, /\.mobile-bottom-nav\s*\{[\s\S]*?position:\s*fixed;[\s\S]*?bottom:\s*0;[\s\S]*?background:\s*rgba\(255, 255, 255, 0\.4\);/)
  assert.match(appSource, /\.mobile-bottom-nav\s*\{[\s\S]*?border-radius:\s*12px 12px 0 0;[\s\S]*?backdrop-filter:\s*blur\(16px\) saturate\(150%\);/)
  assert.match(appSource, /\.mobile-bottom-nav\s*\{[\s\S]*?padding:\s*5px 6px calc\(5px \+ env\(safe-area-inset-bottom\)\);/)
  assert.match(appSource, /\.main\s*\{[^}]*padding-bottom:\s*calc\(76px \+ env\(safe-area-inset-bottom\)\);/)
})

test('settings stay at the bottom of the menu and expose common preferences', () => {
  const menuActions = appSource.slice(
    appSource.indexOf('<div class="mobile-menu-actions">'),
    appSource.indexOf('</div>\n      </div>\n      <div class="nav-actions desktop-nav-actions">'),
  )

  assert.match(menuActions, /class="mobile-action-btn app-settings-trigger"/)
  assert.match(menuActions, /<Setting aria-hidden="true" \/>/)
  assert.match(menuActions, />设置</)
  assert.match(appSource, /class="desktop-icon-btn app-settings-trigger"/)
  assert.match(appSource, /<el-drawer[\s\S]*?v-model="settingsOpen"[\s\S]*?title="设置"/)
  assert.match(appSource, /减少动效/)
  assert.match(appSource, /清除最近搜索/)
  assert.match(appSource, /to="\/config"[\s\S]*?>系统参数</)
  assert.match(appSource, /to="\/sync"[\s\S]*?>同步中心</)
  assert.doesNotMatch(appSource, /\.mobile-menu-actions \.app-settings-trigger\s*\{[^}]*grid-column:/s)
})

test('fullscreen follows the browser state and cleans up its listener', () => {
  assert.match(appSource, /const isFullscreen = ref\(Boolean\(document\.fullscreenElement\)\)/)
  assert.match(appSource, /document\.documentElement\.requestFullscreen\(\)/)
  assert.match(appSource, /document\.exitFullscreen\(\)/)
  assert.match(appSource, /document\.addEventListener\('fullscreenchange', syncFullscreenState\)/)
  assert.match(appSource, /document\.removeEventListener\('fullscreenchange', syncFullscreenState\)/)
  assert.match(appSource, /:model-value="isFullscreen"[\s\S]*?@change="toggleFullscreen"/)
  assert.match(appSource, /class="desktop-icon-btn fullscreen-trigger"/)
})

test('reduced motion is persisted and applied at the document root', () => {
  assert.match(appSource, /apex\.ui\.reduce-motion/)
  assert.match(appSource, /document\.documentElement\.classList\.toggle\('reduce-motion', enabled\)/)
  assert.match(appSource, /localStorage\.setItem\(REDUCE_MOTION_KEY, String\(enabled\)\)/)
  assert.match(appSource, /:global\(html\.reduce-motion\) \*/)
})
