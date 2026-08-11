import test from 'node:test'
import assert from 'node:assert/strict'

import { resolveScrollPosition } from './scrollBehavior.js'

test('new route navigation starts at the top', () => {
  assert.deepEqual(resolveScrollPosition(null), { left: 0, top: 0 })
})

test('browser history navigation restores the previous position', () => {
  const savedPosition = { left: 0, top: 541 }
  assert.deepEqual(resolveScrollPosition(savedPosition), savedPosition)
})
