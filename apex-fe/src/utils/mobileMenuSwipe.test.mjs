import assert from 'node:assert/strict'
import test from 'node:test'

import {
  menuContentOffset,
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

test('mobile menu pushes page content by the visible menu width', () => {
  assert.equal(menuContentOffset(0, 374), 0)
  assert.equal(menuContentOffset(0.5, 374), 187)
  assert.equal(menuContentOffset(1, 374), 374)
  assert.equal(menuContentOffset(1.2, 374), 374)
})

test('mobile menu swipe settles from distance or release velocity', () => {
  assert.equal(shouldOpenMenuAfterSwipe(0.41, 0.2), false)
  assert.equal(shouldOpenMenuAfterSwipe(0.42, 0.2), true)
  assert.equal(shouldOpenMenuAfterSwipe(0.2, 0.5), true)
  assert.equal(shouldOpenMenuAfterSwipe(0.8, -0.5), false)
})
