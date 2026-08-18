import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const dashboardSource = await readFile(new URL('./DashboardView.vue', import.meta.url), 'utf8')

test('dashboard places overnight market and news before action panels', () => {
  const contextIndex = dashboardSource.indexOf('盘前依据')
  const actionIndex = dashboardSource.indexOf('<div class="two-col">')

  assert.ok(contextIndex > 0)
  assert.ok(actionIndex > contextIndex)
  assert.match(dashboardSource, /home\.value\?\.morningBriefing/)
  assert.match(dashboardSource, /aria-label="盘前依据"/)
})

test('dashboard uses command headline with legacy advice fallback and a new cache version', () => {
  assert.match(dashboardSource, /HOME_CACHE_KEY\s*=\s*'apex\.dashboard\.home\.v15'/)
  assert.match(dashboardSource, /const command\s*=\s*computed\(\(\)\s*=>\s*home\.value\?\.command\s*\|\|\s*null\)/)
  assert.match(
    dashboardSource,
    /command\?\.preMarketSummary\?\.headline[\s\S]{0,300}?market\?\.positionAdvice/,
  )
})

test('dashboard places the command band after market effect and before pre-market context', () => {
  const effectIndex = dashboardSource.indexOf('aria-label="赚钱效应"')
  const commandIndex = dashboardSource.indexOf('aria-label="盘前指挥"')
  const contextIndex = dashboardSource.indexOf('aria-label="盘前依据"')

  assert.ok(effectIndex > 0)
  assert.ok(commandIndex > effectIndex)
  assert.ok(contextIndex > commandIndex)
  assert.match(dashboardSource, /<section\s+v-if="command"[^>]+class="command-band[^>]+aria-label="盘前指挥"/s)
  assert.match(dashboardSource, /command\.tradeDate/)
  assert.match(dashboardSource, /command\.marketDataAsOf/)
  assert.match(dashboardSource, /command\.decisionDataAsOf/)
  assert.match(dashboardSource, /command\.generatedAt/)
  assert.match(dashboardSource, /commandStatusLabel\(command\.status\)/)
})

test('dashboard renders position controls and at most three command actions in backend order', () => {
  assert.match(dashboardSource, /command\.value\?\.operationGuide\?\.items[\s\S]{0,160}?\.slice\(0,\s*3\)/)
  assert.match(dashboardSource, /v-for="item in commandOperationItems"/)
  assert.match(dashboardSource, /command\.operationGuide\.targetPositionMin/)
  assert.match(dashboardSource, /command\.operationGuide\.targetPositionMax/)
  assert.match(dashboardSource, /fmtFactor\(command\.operationGuide\.newPositionFactor\)/)
  assert.match(dashboardSource, /function fmtFactor\(value\)[\s\S]{0,180}?\.toFixed\(2\)[\s\S]{0,80}?倍/)
  assert.match(dashboardSource, /@click="openCommandAction\(item\.code\)"/)
  assert.match(dashboardSource, /RISK_FIRST:\s*'\/portfolio'/)
  assert.match(dashboardSource, /BUY_CONDITIONALLY:\s*'\/decision'/)
  assert.match(dashboardSource, /WATCH_ALERTS:\s*'\/observe'/)
  assert.match(dashboardSource, /REFRESH_DATA:\s*'\/sync'/)
  assert.match(dashboardSource, /code\s*===\s*'VIEW_CONTEXT'/)
  assert.match(dashboardSource, /getElementById\('pre-market-context'\)/)
  assert.match(dashboardSource, /v-if="item\.targetCount != null"[^>]+class="command-target-count"/)
  assert.match(dashboardSource, /\{\{ item\.targetCount \}\}/)
})

test('dashboard shows at most four pre-market evidence items with labels and values', () => {
  assert.match(dashboardSource, /command\.preMarketSummary\.evidenceItems\.slice\(0,\s*4\)/)
  assert.match(dashboardSource, /v-for="item in command\.preMarketSummary\.evidenceItems\.slice\(0, 4\)"/)
  assert.match(dashboardSource, /class="command-evidence-label">\{\{ item\.label \}\}/)
  assert.match(dashboardSource, /class="command-evidence-value">\{\{ item\.value \}\}/)
})

test('dashboard command band is two-column on desktop and safe on phone layouts', () => {
  assert.match(
    dashboardSource,
    /\.command-grid\s*\{[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/s,
  )
  assert.match(
    dashboardSource,
    /@media \(max-width: 900px\)[\s\S]*?\.command-grid\s*\{[^}]*grid-template-columns:\s*1fr;/s,
  )
  assert.match(dashboardSource, /\.command-action\s*\{[^}]*min-height:\s*44px;/s)
  assert.match(dashboardSource, /\.command-action[^}]*overflow-wrap:\s*anywhere;/s)
  assert.doesNotMatch(dashboardSource, /\.command-(?:band|grid|column|action)\s*\{[^}]*(?<!-)height:\s*\d+px;/s)
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
