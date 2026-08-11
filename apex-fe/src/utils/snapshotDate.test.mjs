import assert from 'node:assert/strict'
import test from 'node:test'

import { snapshotFallbackText, snapshotStamp } from './snapshotDate.js'

test('snapshotStamp only returns the actual response date', () => {
  assert.equal(snapshotStamp({ tradeDate: '2026-08-10T00:00:00' }), '2026-08-10')
  assert.equal(snapshotStamp(null), '')
})

test('snapshotFallbackText explains when the requested date is unavailable', () => {
  assert.equal(
    snapshotFallbackText('2026-08-11', '2026-08-10'),
    '请求 2026-08-11，当前展示最近可用数据 2026-08-10',
  )
  assert.equal(snapshotFallbackText('2026-08-10', '2026-08-10'), '')
})
