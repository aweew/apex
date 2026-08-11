import assert from 'node:assert/strict'
import test from 'node:test'

import { resolveActionColumnFixed } from './responsiveTable.js'

test('mobile action columns stay in normal table flow', () => {
  assert.equal(resolveActionColumnFixed(390), false)
  assert.equal(resolveActionColumnFixed(820), false)
})

test('desktop action columns remain fixed on the right', () => {
  assert.equal(resolveActionColumnFixed(821), 'right')
  assert.equal(resolveActionColumnFixed(1440), 'right')
})
