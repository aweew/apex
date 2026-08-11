import assert from 'node:assert/strict'
import test from 'node:test'
import {
  beginNavigationActivity,
  beginRequestActivity,
  isNavigating,
  requestCount,
} from './appActivity.js'

test('request activity tracks concurrent requests and ignores duplicate finishes', () => {
  const finishFirst = beginRequestActivity()
  const finishSecond = beginRequestActivity()

  assert.equal(requestCount.value, 2)
  finishFirst()
  finishFirst()
  assert.equal(requestCount.value, 1)
  finishSecond()
  assert.equal(requestCount.value, 0)
})

test('an older navigation cannot finish the latest navigation', () => {
  const finishFirst = beginNavigationActivity()
  const finishSecond = beginNavigationActivity()

  finishFirst()
  assert.equal(isNavigating.value, true)
  finishSecond()
  assert.equal(isNavigating.value, false)
})
