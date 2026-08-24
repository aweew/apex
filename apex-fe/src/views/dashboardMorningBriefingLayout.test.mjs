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
  assert.match(dashboardSource, /HOME_CACHE_KEY\s*=\s*'apex\.dashboard\.home\.v16'/)
  assert.match(dashboardSource, /const command\s*=\s*computed\(\(\)\s*=>\s*home\.value\?\.command\s*\|\|\s*null\)/)
  assert.match(
    dashboardSource,
    /command\?\.preMarketSummary\?\.headline[\s\S]{0,300}?market\?\.positionAdvice/,
  )
})

test('dashboard places the command band after market effect and before pre-market context', () => {
  const effectIndex = dashboardSource.indexOf('aria-label="赚钱效应"')
  const commandIndex = dashboardSource.indexOf('aria-label="开盘准备"')
  const contextIndex = dashboardSource.indexOf('aria-label="盘前依据"')

  assert.ok(effectIndex > 0)
  assert.ok(commandIndex > effectIndex)
  assert.ok(contextIndex > commandIndex)
  assert.match(dashboardSource, /<section\s+v-if="command"[^>]+class="command-band[^>]+aria-label="开盘准备"/s)
  assert.match(dashboardSource, /command\.tradeDate/)
  assert.match(dashboardSource, /command\.marketDataAsOf/)
  assert.match(dashboardSource, /command\.decisionDataAsOf/)
  assert.match(dashboardSource, /command\.generatedAt/)
  assert.match(dashboardSource, /commandStatusLabel\(command\.status\)/)
})

test('dashboard renders position controls and at most three command actions in backend order', () => {
  assert.match(dashboardSource, /command\.value\?\.operationGuide\?\.items[\s\S]{0,160}?\.slice\(0,\s*3\)/)
  assert.match(
    dashboardSource,
    /const hasExecutableNewPosition\s*=\s*computed\([\s\S]{0,240}?BUY_CONDITIONALLY[\s\S]{0,120}?READY/,
  )
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
  assert.match(dashboardSource, /v-if="Number\(item\.targetCount\) > 0"[^>]+class="command-target-count"/)
  assert.match(dashboardSource, /\{\{ item\.targetCount \}\}/)
  assert.match(dashboardSource, /v-if="command\.operationGuide && command\.status === 'READY' && hasExecutableNewPosition"/)
  assert.match(dashboardSource, /v-if="!commandOperationItems\.length" class="command-guide-summary"/)
})

test('dashboard labels focus and actions without filler wording', () => {
  assert.match(dashboardSource, /<h4>今日重点<\/h4>/)
  assert.match(dashboardSource, /<strong>可买<\/strong>/)
  assert.match(dashboardSource, /<strong>先处理<\/strong>/)
  assert.match(dashboardSource, /command\.status === 'READY' \? '取消条件' : '恢复条件'/)
  assert.match(dashboardSource, /<h4>执行清单<\/h4>/)
})

test('dashboard omits repeated broad-market evidence from the pre-market summary', () => {
  assert.doesNotMatch(dashboardSource, /class="command-evidence"/)
  assert.doesNotMatch(dashboardSource, /preMarketSummary\.evidenceItems/)
  assert.doesNotMatch(dashboardSource, />核心依据<\/span>/)
  assert.doesNotMatch(dashboardSource, /<h4>盘前总结<\/h4>\s*<span>/)
  assert.match(dashboardSource, /command\.operationGuide && command\.status === 'READY'/)
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
  assert.match(
    dashboardSource,
    /\.morning-context-grid\s*\{[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);[^}]*gap:\s*0;/s,
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

test('dashboard morning context leads with a conclusion and keeps supporting evidence quiet', () => {
  assert.match(dashboardSource, /class="morning-context-time-label">更新<\/span>/)
  assert.match(dashboardSource, /class="morning-news-lead"/)
  assert.match(dashboardSource, /class="morning-news-summary-label">核心结论<\/span>/)
  assert.match(
    dashboardSource,
    /\.morning-news-lead\s*\{[^}]*grid-template-columns:\s*auto minmax\(0,\s*1fr\);[^}]*border-left:\s*2px solid/s,
  )
  assert.match(dashboardSource, /\.morning-news-summary\s*\{[^}]*max-width:\s*78ch;/s)
  assert.match(dashboardSource, /\.morning-context-block\s*\{[^}]*align-self:\s*start;/s)
  assert.match(
    dashboardSource,
    /@media \(max-width: 900px\)[\s\S]*?\.morning-news-block\s*\{[^}]*order:\s*-1;[^}]*border-bottom:\s*1px solid/s,
  )
  assert.match(
    dashboardSource,
    /@media \(max-width: 560px\)[\s\S]*?\.morning-news-item a,[\s\S]*?\.morning-news-title\s*\{[^}]*-webkit-line-clamp:\s*2;/s,
  )
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
  assert.match(dashboardSource, /v-for="\(theme, index\) in overnightThemes"/)
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

test('dashboard pre-market summary has a compact hierarchy and accessible action', () => {
  assert.match(dashboardSource, /class="morning-context-title"/)
  assert.match(dashboardSource, /class="morning-context-status"/)
  assert.match(
    dashboardSource,
    /\.morning-context-head\s*\{[^}]*display:\s*grid;[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\) auto;/s,
  )
  assert.match(dashboardSource, /\.morning-context-title\s*\{[^}]*text-align:\s*left;/s)
  assert.match(dashboardSource, /\.morning-context-link[^}]*min-height:\s*44px;/s)
  assert.match(
    dashboardSource,
    /@media \(max-width: 560px\)[\s\S]*?\.morning-context-head\s*\{[^}]*grid-template-columns:\s*1fr;/s,
  )
})

test('dashboard theme ranking exposes breadth without relying on color alone', () => {
  assert.match(dashboardSource, /v-for="\(theme, index\) in overnightThemes"/)
  assert.match(dashboardSource, /class="overnight-theme-rank"/)
  assert.match(dashboardSource, /class="overnight-theme-breadth"/)
  assert.match(dashboardSource, /class="overnight-theme-breadth-track"/)
  assert.match(dashboardSource, /themeUpPct\(theme\)/)
  assert.match(dashboardSource, /\{\{ theme\.upCount \?\? 0 \}\}\/\{\{ theme\.quoteCount \?\? 0 \}\} 上涨/)
  assert.match(
    dashboardSource,
    /\.overnight-theme\s*\{[^}]*grid-template-columns:\s*24px minmax\(0,\s*1fr\) minmax\(82px,\s*auto\);/s,
  )
})
