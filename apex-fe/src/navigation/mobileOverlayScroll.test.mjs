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
  assert.match(appSource, /class="mobile-menu-scroll"[\s\S]*?<\/div>\s*<div class="mobile-menu-actions">/)
  assert.match(appSource, /\.mobile-menu-actions\s*\{[\s\S]*?flex:\s*0 0 auto;/)
  assert.match(appSource, /\.mobile-logout-btn\s*\{[\s\S]*?grid-column:\s*1 \/ -1;/)
})

test('mobile search and glossary keep their content regions independently scrollable', () => {
  assert.match(appSource, /\.search-body\s*\{[\s\S]*?min-height:\s*0;[\s\S]*?overflow-y:\s*auto;/)
  assert.match(appSource, /\.search-body\s*\{[\s\S]*?touch-action:\s*pan-y;/)
  assert.match(glossarySource, /\.glossary-list\s*\{[\s\S]*?overflow-y:\s*auto;[\s\S]*?touch-action:\s*pan-y;/)
  assert.match(glossarySource, /\.glossary-detail\s*\{[\s\S]*?overflow-y:\s*auto;[\s\S]*?touch-action:\s*pan-y;/)
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
