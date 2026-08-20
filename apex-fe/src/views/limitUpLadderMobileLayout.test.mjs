import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const ladderSource = await readFile(new URL('./LimitUpLadderView.vue', import.meta.url), 'utf8')
const mobileStyles = ladderSource.slice(ladderSource.indexOf('@media (max-width: 720px)'))

test('mobile ladder cards share the remaining row width across three columns', () => {
  assert.match(
    mobileStyles,
    /\.tier\s*\{[\s\S]*?grid-template-columns:\s*max-content minmax\(0, 1fr\);/,
  )
  assert.match(
    mobileStyles,
    /\.tier-grid\s*\{[\s\S]*?display:\s*grid;[\s\S]*?grid-template-columns:\s*repeat\(3, minmax\(0, 1fr\)\);[\s\S]*?width:\s*100%;/,
  )
  assert.match(mobileStyles, /\.card\s*\{[\s\S]*?width:\s*100%;[\s\S]*?min-width:\s*0;/)
})

test('mobile ladder gives concepts more room beside percentage changes', () => {
  assert.match(mobileStyles, /\.card-sub\s*\{[\s\S]*?gap:\s*2px;/)
  assert.match(mobileStyles, /\.card-pct\s*\{[\s\S]*?font-size:\s*9px;/)
})
