import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const indexSource = await readFile(new URL('./IndexBoardView.vue', import.meta.url), 'utf8')
const phoneStyles = indexSource.slice(
  indexSource.indexOf('@media (max-width: 560px)'),
  indexSource.indexOf('@media (min-width: 360px)'),
)
const mobileStyles = indexSource.slice(
  indexSource.indexOf('@media (max-width: 720px)'),
  indexSource.indexOf('@media (max-width: 560px)'),
)

test('mobile money effect metrics stay in one aligned five-column strip', () => {
  assert.match(phoneStyles, /\.pulse-effect-grid\s*\{[\s\S]*?grid-template-columns:\s*repeat\(5, minmax\(0, 1fr\)\);/)
  assert.match(phoneStyles, /\.pulse-effect-grid\s*\{[\s\S]*?gap:\s*0;/)
  assert.match(phoneStyles, /\.metric \+ \.metric::before\s*\{[\s\S]*?background:\s*var\(--mc-line\);/)
  assert.doesNotMatch(mobileStyles, /\.pulse-effect-grid\s*\{[\s\S]*?grid-template-columns:\s*repeat\(2,/)
})

test('mobile money effect cells use compact centered typography', () => {
  assert.match(phoneStyles, /\.pulse-effect-grid \.metric\s*\{[\s\S]*?align-items:\s*center;/)
  assert.match(phoneStyles, /\.pulse-effect-grid \.metric \.k\s*\{[\s\S]*?white-space:\s*nowrap;/)
  assert.match(phoneStyles, /\.pulse-effect-grid \.metric \.v\s*\{[\s\S]*?font-size:\s*14px;/)
  assert.match(phoneStyles, /\.pulse-effect-head\s*\{\s*margin-bottom:\s*8px;\s*\}/)
})

test('mobile hero index prices can shrink without widening the page', () => {
  assert.match(mobileStyles, /\.hero-price\s*\{[\s\S]*?min-width:\s*0;/)
  assert.match(mobileStyles, /\.hero-price b\s*\{[\s\S]*?min-width:\s*0;[\s\S]*?font-size:\s*20px;/)
  assert.match(mobileStyles, /\.hero-price em\s*\{[\s\S]*?flex:\s*0 0 auto;[\s\S]*?white-space:\s*nowrap;/)
})

test('money effect typography uses the available width at larger mobile sizes', () => {
  assert.match(indexSource, /@media \(min-width: 360px\) and \(max-width: 560px\)[\s\S]*?\.pulse-effect-grid \.metric \.v\s*\{[\s\S]*?font-size:\s*15px;/)
  assert.doesNotMatch(indexSource, /@media \(min-width: 561px\) and \(max-width: 720px\)/)
})

test('market board first load reuses the briefing snapshot', () => {
  assert.match(indexSource, /onMounted\(async \(\) => \{\s*\/\/ 首次进入复用服务端简报快照[\s\S]*?await load\(\)/)
  assert.match(indexSource, /async function onRefreshQuotes\(\)[\s\S]*?await load\(true\)/)
})

test('market mainlines keep the signed percentage in one aligned value', () => {
  assert.match(indexSource, /class="themes-head"[\s\S]*?市场主线/)
  assert.match(indexSource, /class="theme-pct" :class="t\.pctDir">\{\{ t\.pctText \}\}/)
  assert.doesNotMatch(indexSource, /class="theme-sign"/)
  assert.match(indexSource, /\.theme-item\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\) auto;/)
})

test('embedded heatmap controls use balanced mobile rows', async () => {
  const heatmapSource = await readFile(new URL('./HeatmapView.vue', import.meta.url), 'utf8')
  assert.match(heatmapSource, /class="heatmap-type"/)
  assert.match(heatmapSource, /\.embed-head \.actions\s*\{[\s\S]*?grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/)
  assert.match(heatmapSource, /\.embed-head \.heatmap-refresh\s*\{[\s\S]*?grid-column:\s*1 \/ -1;/)
})

test('embedded heatmap favors readable mobile blocks over clipped labels', async () => {
  const heatmapSource = await readFile(new URL('./HeatmapView.vue', import.meta.url), 'utf8')
  assert.match(heatmapSource, /const mobileNodeLimit = 18/)
  assert.match(heatmapSource, /props\.embedded && window\.matchMedia\('\(max-width: 560px\)'\)\.matches[\s\S]*?sortedNodes\.slice\(0, mobileNodeLimit\)/)
  assert.match(heatmapSource, /visibleMin: isEmbeddedMobile \? 2600 : 1100/)
  assert.match(heatmapSource, /class="share-card" :class="\{ 'is-embedded': embedded \}"/)
  assert.match(heatmapSource, /\.share-card\.is-embedded \{[\s\S]*?background: #f2f5f7;/)
})

test('observe pool keeps mobile controls and cards inside stable tracks', async () => {
  const observeSource = await readFile(new URL('./ObserveView.vue', import.meta.url), 'utf8')
  const mobileStyles = observeSource.slice(observeSource.indexOf('@media (max-width: 560px)'))
  assert.match(mobileStyles, /\.page \.header > \.actions\s*\{[\s\S]*?grid-template-columns:\s*repeat\(3, minmax\(0, 1fr\)\);/)
  assert.doesNotMatch(observeSource, /导出CSV/)
  assert.match(observeSource, /:prefix-icon="Search"/)
  assert.match(observeSource, /\.search :deep\(\.el-input__wrapper\)\s*\{[\s\S]*?background:\s*rgba\(100, 116, 139, 0\.08\);/)
  assert.match(mobileStyles, /\.search\s*\{[\s\S]*?grid-column:\s*1 \/ -1;/)
  assert.match(mobileStyles, /\.status-chips\s*\{[\s\S]*?overflow-x:\s*auto;/)
  assert.match(mobileStyles, /\.card-actions\s*\{[\s\S]*?grid-template-columns:\s*repeat\(4, minmax\(0, 1fr\)\);/)
})
