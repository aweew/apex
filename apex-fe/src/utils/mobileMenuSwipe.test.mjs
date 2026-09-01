import assert from 'node:assert/strict'
import test from 'node:test'

import {
  isMobileBackSwipeStart,
  mobileBackSwipeOffset,
  menuSwipeProgress,
  resolveMenuSwipeAxis,
  shouldNavigateBackAfterSwipe,
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

test('mobile menu swipe follows the direction of a quick fling', () => {
  assert.equal(shouldOpenMenuAfterSwipe(0.2, 0.46), true)
  assert.equal(shouldOpenMenuAfterSwipe(0.8, -0.46), false)
  assert.equal(shouldOpenMenuAfterSwipe(0.2, 0.2), false)
})

test('mobile back swipe reserves only the left screen edge', () => {
  assert.equal(isMobileBackSwipeStart(0), true)
  assert.equal(isMobileBackSwipeStart(24), true)
  assert.equal(isMobileBackSwipeStart(25), false)
  assert.equal(isMobileBackSwipeStart(-1), false)
})

test('mobile back swipe requires a deliberate rightward drag', () => {
  assert.equal(shouldNavigateBackAfterSwipe(63), false)
  assert.equal(shouldNavigateBackAfterSwipe(64), true)
  assert.equal(shouldNavigateBackAfterSwipe(-80), false)
  assert.equal(shouldNavigateBackAfterSwipe(32, 0.46), true)
  assert.equal(shouldNavigateBackAfterSwipe(31, 1), false)
})

test('mobile back swipe feedback is damped and bounded', () => {
  assert.equal(mobileBackSwipeOffset(-10), 0)
  assert.equal(mobileBackSwipeOffset(100), 32)
  assert.equal(mobileBackSwipeOffset(1000), 48)
})
