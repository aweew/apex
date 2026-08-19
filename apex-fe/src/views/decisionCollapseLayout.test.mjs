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
