import assert from 'node:assert/strict'
import test from 'node:test'

import { isWeekendReportVisible } from './weekendReportVisibility.js'

test('weekend report is visible only from Sunday 21:00 until Monday 09:30 Shanghai time', () => {
  assert.equal(isWeekendReportVisible(new Date('2026-08-30T12:59:00Z')), false)
  assert.equal(isWeekendReportVisible(new Date('2026-08-30T13:00:00Z')), true)
  assert.equal(isWeekendReportVisible(new Date('2026-08-31T01:29:00Z')), true)
  assert.equal(isWeekendReportVisible(new Date('2026-08-31T01:30:00Z')), false)
  assert.equal(isWeekendReportVisible(new Date('2026-09-01T04:00:00Z')), false)
})
