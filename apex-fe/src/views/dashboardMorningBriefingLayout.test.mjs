import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const dashboardSource = await readFile(new URL('./DashboardView.vue', import.meta.url), 'utf8')

test('dashboard places overnight market and news before action panels', () => {
  const contextIndex = dashboardSource.indexOf('隔夜与今日消息')
  const actionIndex = dashboardSource.indexOf('<div class="two-col">')

  assert.ok(contextIndex > 0)
  assert.ok(actionIndex > contextIndex)
  assert.match(dashboardSource, /home\.value\?\.morningBriefing/)
  assert.match(dashboardSource, /aria-label="隔夜美股与今日消息面"/)
})

test('dashboard morning context has a compact responsive layout', () => {
  assert.match(dashboardSource, /\.morning-context-grid\s*\{[^}]*grid-template-columns:/s)
  assert.match(
    dashboardSource,
    /@media \(max-width: 900px\)[\s\S]*\.morning-context-grid\s*\{[^}]*grid-template-columns:\s*1fr/s,
  )
  assert.match(dashboardSource, /v-for="item in morningNewsCards"/)
})

test('dashboard separates overnight indexes, market themes and star quotes with legacy fallback', () => {
  assert.match(dashboardSource, /morningBriefing\.value\?\.indexQuotes/)
  assert.match(dashboardSource, /morningBriefing\.value\?\.marketThemes/)
  assert.match(dashboardSource, /morningBriefing\.value\?\.starQuotes/)
  assert.match(
    dashboardSource,
    /const overnightIndexes\s*=\s*computed\([\s\S]{0,500}?indexQuotes[\s\S]{0,500}?marketQuotes/,
  )
  assert.match(
    dashboardSource,
    /const overnightStars\s*=\s*computed\([\s\S]{0,500}?starQuotes[\s\S]{0,500}?marketQuotes/,
  )
  assert.match(dashboardSource, /legacyIndexSymbols\.has\(quote\.symbol\)/)
  assert.match(dashboardSource, /!legacyIndexSymbols\.has\(quote\.symbol\)/)
  assert.match(dashboardSource, /v-for="quote in overnightIndexes"/)
  assert.match(dashboardSource, /v-for="theme in overnightThemes"/)
  assert.match(dashboardSource, /v-for="quote in overnightStars"/)
  assert.doesNotMatch(dashboardSource, /绝对涨跌幅前八/)
})

test('dashboard keeps overnight layers stable across desktop and phone layouts', () => {
  assert.match(
    dashboardSource,
    /\.overnight-index-grid\s*\{[^}]*grid-template-columns:\s*repeat\(3,\s*minmax\(0,\s*1fr\)\);/s,
  )
  assert.match(
    dashboardSource,
    /\.overnight-theme-grid\s*\{[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/s,
  )
  assert.match(
    dashboardSource,
    /\.overnight-star-grid\s*\{[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/s,
  )
  assert.match(
    dashboardSource,
    /@media \(max-width: 560px\)[\s\S]*?\.overnight-theme-grid\s*\{[^}]*grid-template-columns:\s*1fr;/s,
  )
})
