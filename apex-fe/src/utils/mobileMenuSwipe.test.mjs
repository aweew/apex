import assert from 'node:assert/strict'
import test from 'node:test'

import {
  menuSwipeProgress,
  resolveMenuSwipeAxis,
  shouldOpenMenuAfterSwipe,
} from './mobileMenuSwipe.js'

test('mobile menu swipe waits for intent before locking an axis', () => {
  assert.equal(resolveMenuSwipeAxis(6, 2), 'pending')
  assert.equal(resolveMenuSwipeAxis(4, 12), 'vertical')
  assert.equal(resolveMenuSwipeAxis(18, 5), 'horizontal')
})

test('mobile menu swipe progress follows the finger within drawer bounds', () => {
  assert.equal(menuSwipeProgress(0, 90, 300), 0.3)
  assert.equal(menuSwipeProgress(1, -75, 300), 0.75)
  assert.equal(menuSwipeProgress(0, -30, 300), 0)
  assert.equal(menuSwipeProgress(1, 60, 300), 1)
})

test('mobile menu swipe settles only from the halfway position', () => {
  assert.equal(shouldOpenMenuAfterSwipe(0.49), false)
  assert.equal(shouldOpenMenuAfterSwipe(0.5), true)
  assert.equal(shouldOpenMenuAfterSwipe(0.2), false)
  assert.equal(shouldOpenMenuAfterSwipe(0.8), true)
})
