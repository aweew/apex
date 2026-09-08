import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const [dashboardSource, decisionSource, marketBriefSource] = await Promise.all([
  readFile(new URL('./DashboardView.vue', import.meta.url), 'utf8'),
  readFile(new URL('./DecisionView.vue', import.meta.url), 'utf8'),
  readFile(new URL('../components/news/MarketBriefPanel.vue', import.meta.url), 'utf8'),
])

test('theme percentages render the sign and number as one aligned value', () => {
  for (const themeSource of [decisionSource, marketBriefSource]) {
    assert.match(
      themeSource,
      /<span v-if="t\.pctText" class="theme-pct" :class="t\.pctDir">\{\{ t\.pctText \}\}<\/span>/,
    )
    assert.doesNotMatch(themeSource, /theme-sign/)
  }
})

test('dashboard optically aligns positive signs without separating the percentage value', () => {
  assert.match(dashboardSource, /const effectItems\s*=\s*computed\(/)
  assert.match(dashboardSource, /class="signed-pct-sign"\s*:class="\{ 'is-positive': item\.pct\.sign === '\+' \}"/)
  assert.match(dashboardSource, /class="signed-pct-sign"\s*:class="\{ 'is-positive': t\.sign === '\+' \}"/)
  assert.match(dashboardSource, /\.signed-pct\s*\{[^}]*display:\s*inline-flex;[^}]*align-items:\s*baseline;/s)
  assert.match(dashboardSource, /\.signed-pct-sign\.is-positive\s*\{[^}]*transform:\s*translateY\(-0\.1em\);/s)
  assert.doesNotMatch(dashboardSource, /font-feature-settings:\s*'tnum' 1,\s*'case' 1;/)
})

test('market brief styles only the outer theme chip as a surface', () => {
  assert.match(marketBriefSource, /\.themes > span\s*\{/)
  assert.doesNotMatch(marketBriefSource, /\.themes span\s*\{/)
})

test('decision mainline themes use compact content-width chips', () => {
  assert.match(
    decisionSource,
    /\.theme-chip-grid\s*\{[^}]*display:\s*flex;[^}]*flex-wrap:\s*wrap;/,
  )
  assert.match(
    decisionSource,
    /\.theme-chip\s*\{[^}]*flex:\s*0 1 auto;[^}]*max-width:\s*min\(100%, 240px\);/,
  )
})

test('decision mainline themes open the matching concept constituents', () => {
  assert.match(
    decisionSource,
    /<button[\s\S]*?class="theme-chip"[\s\S]*?:aria-label="`查看\$\{t\.name\}成分股`"[\s\S]*?@click="openTheme\(t\)"/,
  )
  assert.match(
    decisionSource,
    /function openTheme\(theme\)[\s\S]*?const query = \{ type: theme\.boardType \|\| 'CONCEPT' \}[\s\S]*?if \(theme\.code\) query\.code = theme\.code[\s\S]*?else query\.q = theme\.name[\s\S]*?router\.push\(\{ path: '\/market', query: \{ \.\.\.query, tab: 'sector' \} \}\)/,
  )
  assert.match(decisionSource, /\.theme-chip\s*\{[^}]*cursor:\s*pointer;/)
})

test('decision factors balance seven cards across two complete rows', () => {
  assert.match(
    decisionSource,
    /\.factor-strip\s*\{[^}]*grid-template-columns:\s*repeat\(4, minmax\(0, 1fr\)\);/,
  )
  assert.match(
    decisionSource,
    /\.factor-cell:nth-child\(7\):last-child\s*\{[^}]*grid-column:\s*span 2;/,
  )
})
