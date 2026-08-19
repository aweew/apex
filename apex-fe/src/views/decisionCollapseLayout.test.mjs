import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const decisionSource = await readFile(new URL('./DecisionView.vue', import.meta.url), 'utf8')

test('mobile decision collapse keeps each arrow centered beside its two-line heading', () => {
  const mobileStyles = decisionSource.slice(decisionSource.indexOf('@media (max-width: 560px)'))

  assert.match(
    mobileStyles,
    /\.more-collapse :deep\(\.el-collapse-item__header\)\s*\{[^}]*align-items:\s*center;/,
  )
  assert.match(
    mobileStyles,
    /\.collapse-heading\s*\{[^}]*flex-direction:\s*column;[^}]*align-items:\s*flex-start;/,
  )
})

test('AI stock note priority tag keeps its content width on mobile', () => {
  assert.match(
    decisionSource,
    /<el-tag\s+v-if="n\.priority"\s+class="note-priority"/,
  )
  assert.match(
    decisionSource,
    /\.note-priority\s*\{[^}]*justify-self:\s*start;[^}]*width:\s*fit-content;/,
  )
})

test('mobile decision results use full-width lists instead of frozen desktop tables', () => {
  const mobileStyles = decisionSource.slice(decisionSource.indexOf('@media (max-width: 560px)'))

  assert.equal((decisionSource.match(/class="decision-desktop-table"/g) || []).length, 3)
  assert.match(decisionSource, /class="decision-mobile-list decision-mobile-buy-list"[\s\S]*v-for="row in filteredBuys"/)
  assert.match(decisionSource, /class="decision-mobile-list decision-mobile-sell-list"[\s\S]*v-for="row in sells"/)
  assert.match(decisionSource, /class="decision-mobile-list decision-mobile-hold-list"[\s\S]*v-for="row in holds"/)
  assert.match(mobileStyles, /\.decision-desktop-table\s*\{[^}]*display:\s*none;/)
  assert.match(mobileStyles, /\.decision-mobile-list\s*\{[^}]*display:\s*block;/)
})
