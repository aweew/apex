import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const [dashboardSource, decisionSource, marketBriefSource] = await Promise.all([
  readFile(new URL('./DashboardView.vue', import.meta.url), 'utf8'),
  readFile(new URL('./DecisionView.vue', import.meta.url), 'utf8'),
  readFile(new URL('../components/news/MarketBriefPanel.vue', import.meta.url), 'utf8'),
])
const themeSources = [dashboardSource, decisionSource, marketBriefSource]

test('theme percentages render the sign and number as one aligned value', () => {
  for (const themeSource of themeSources) {
    assert.match(
      themeSource,
      /<span v-if="t\.pctText" class="theme-pct" :class="t\.pctDir">\{\{ t\.pctText \}\}<\/span>/,
    )
    assert.doesNotMatch(themeSource, /theme-sign/)
  }
})

test('market brief styles only the outer theme chip as a surface', () => {
  assert.match(marketBriefSource, /\.themes > span\s*\{/)
  assert.doesNotMatch(marketBriefSource, /\.themes span\s*\{/)
})
