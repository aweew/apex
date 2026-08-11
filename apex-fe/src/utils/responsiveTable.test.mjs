import assert from 'node:assert/strict'
import test from 'node:test'

import { resolveActionColumnFixed, resolveActionColumnVisible } from './responsiveTable.js'

test('mobile action columns are hidden by default', () => {
  assert.equal(resolveActionColumnVisible(390), false)
  assert.equal(resolveActionColumnVisible(820), false)
})

test('desktop action columns remain visible', () => {
  assert.equal(resolveActionColumnVisible(821), true)
  assert.equal(resolveActionColumnVisible(1440), true)
})

test('mobile action columns stay in normal table flow', () => {
  assert.equal(resolveActionColumnFixed(390), false)
  assert.equal(resolveActionColumnFixed(820), false)
})

test('desktop action columns remain fixed on the right', () => {
  assert.equal(resolveActionColumnFixed(821), 'right')
  assert.equal(resolveActionColumnFixed(1440), 'right')
})
