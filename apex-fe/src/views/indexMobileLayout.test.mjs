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
