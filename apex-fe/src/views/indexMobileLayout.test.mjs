import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { resolveTreemapLabelFontSize } from '../utils/heatmapLabel.js'

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
  assert.match(indexSource, /async function onRefreshQuotes\(\)[\s\S]*?quoteRefreshing\.value = true[\s\S]*?await load\(true, false\)/)
})

test('market refresh and index sync expose only the action that is running', () => {
  assert.match(indexSource, /const quoteRefreshing = ref\(false\)/)
  assert.match(indexSource, /const indexSyncing = ref\(false\)/)
  assert.match(indexSource, /<div class="page mc-page" v-loading="loading">/)
  assert.match(indexSource, /:loading="quoteRefreshing" :disabled="indexSyncing"/)
  assert.match(indexSource, /:loading="indexSyncing" :disabled="quoteRefreshing"/)
})

test('market mainlines keep the signed percentage in one aligned value', () => {
  assert.match(indexSource, /class="themes-head"[\s\S]*?市场主线/)
  assert.match(indexSource, /class="theme-pct" :class="t\.pctDir">\{\{ t\.pctText \}\}/)
  assert.doesNotMatch(indexSource, /class="theme-sign"/)
  assert.match(indexSource, /\.theme-item\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\) auto;/)
})

test('industry and concept rankings open the matching sector constituents', () => {
  assert.match(
    indexSource,
    /class="rank-row"[\s\S]*?openSectorConstituents\(row, 'INDUSTRY'\)/,
  )
  assert.match(
    indexSource,
    /class="rank-row"[\s\S]*?openSectorConstituents\(row, 'CONCEPT'\)/,
  )
  assert.match(
    indexSource,
    /function openSectorConstituents\(row, type\)[\s\S]*?query:\s*\{ type, code: row\.code \}/,
  )
  assert.match(indexSource, /\.rank-row\s*\{[^}]*grid-template-columns:\s*22px minmax\(0, 1fr\) auto;[^}]*min-height:\s*44px;/)
})

test('embedded heatmap controls use balanced mobile rows', async () => {
  const heatmapSource = await readFile(new URL('./HeatmapView.vue', import.meta.url), 'utf8')
  assert.match(heatmapSource, /class="heatmap-type"/)
  assert.match(heatmapSource, /\.embed-head \.actions\s*\{[\s\S]*?grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/)
  assert.match(heatmapSource, /\.embed-head \.heatmap-refresh\s*\{[\s\S]*?grid-column:\s*1 \/ -1;/)
  assert.match(
    heatmapSource,
    /\.embed-head \.heatmap-type :deep\(\.el-radio-button__inner\)\s*\{[^}]*display:\s*inline-flex;[^}]*align-items:\s*center;[^}]*justify-content:\s*center;[^}]*height:\s*40px;[^}]*line-height:\s*1;/,
  )
})

test('embedded heatmap favors readable mobile blocks over clipped labels', async () => {
  const heatmapSource = await readFile(new URL('./HeatmapView.vue', import.meta.url), 'utf8')
  assert.match(heatmapSource, /const mobileNodeLimit = 18/)
  assert.match(heatmapSource, /props\.embedded && window\.matchMedia\('\(max-width: 560px\)'\)\.matches[\s\S]*?sortedNodes\.slice\(0, mobileNodeLimit\)/)
  assert.match(heatmapSource, /function resizeTreemapLabel\(\{ rect \}\)/)
  assert.match(heatmapSource, /fontSize: resolveTreemapLabelFontSize\(rect\)/)
  assert.match(heatmapSource, /padding:\s*\[2, 2\]/)
  assert.match(heatmapSource, /lineHeight:\s*13/)
  assert.match(heatmapSource, /labelLayout: resizeTreemapLabel/)
  assert.match(heatmapSource, /class="share-card" :class="\{ 'is-embedded': embedded \}"/)
  assert.match(heatmapSource, /\.share-card\.is-embedded \{[\s\S]*?background: #f2f5f7;/)
})

test('treemap labels shrink through readable size tiers before hiding', () => {
  assert.equal(resolveTreemapLabelFontSize({ width: 100, height: 50 }), 12)
  assert.equal(resolveTreemapLabelFontSize({ width: 70, height: 34 }), 11)
  assert.equal(resolveTreemapLabelFontSize({ width: 48, height: 26 }), 10)
  assert.equal(resolveTreemapLabelFontSize({ width: 30, height: 20 }), 9)
  assert.equal(resolveTreemapLabelFontSize({ width: 21, height: 17 }), 8)
})

test('treemap labels hide only when two minimum-size Chinese characters cannot fit', () => {
  assert.equal(resolveTreemapLabelFontSize({ width: 20, height: 17 }), 0)
  assert.equal(resolveTreemapLabelFontSize({ width: 21, height: 16 }), 0)
})

test('observe pool keeps mobile controls and cards inside stable tracks', async () => {
  const observeSource = await readFile(new URL('./ObserveView.vue', import.meta.url), 'utf8')
  const mobileStyles = observeSource.slice(observeSource.indexOf('@media (max-width: 560px)'))
  assert.match(mobileStyles, /\.page \.header > \.actions\s*\{[\s\S]*?grid-template-columns:\s*repeat\(3, minmax\(0, 1fr\)\);/)
  assert.doesNotMatch(observeSource, /导出CSV/)
  assert.match(observeSource, /:prefix-icon="Search"/)
  assert.match(observeSource, /class="filter-bar"[\s\S]*?class="status-chips"[\s\S]*?class="search"/)
  assert.match(observeSource, /\.filter-bar\s*\{[^}]*display:\s*flex;[^}]*align-items:\s*center;/)
  assert.match(observeSource, /\.search :deep\(\.el-input__wrapper\)\s*\{[\s\S]*?min-height:\s*40px;[\s\S]*?background:\s*#fff;/)
  assert.match(mobileStyles, /\.filter-bar\s*\{[\s\S]*?flex-direction:\s*column;/)
  assert.match(mobileStyles, /\.search\s*\{[\s\S]*?order:\s*-1;/)
  assert.match(mobileStyles, /\.status-chips\s*\{[\s\S]*?overflow-x:\s*auto;/)
  assert.match(mobileStyles, /\.card-actions\s*\{[\s\S]*?grid-template-columns:\s*repeat\(4, minmax\(0, 1fr\)\);/)
})
