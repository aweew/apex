import assert from 'node:assert/strict'
import test from 'node:test'

import {
  clearDataFreshness,
  dataFreshness,
  publishDataFreshness,
} from './dataFreshness.js'

test('published data freshness keeps an actionable label and source route', () => {
  clearDataFreshness()

  publishDataFreshness({
    level: 'YELLOW',
    label: '决策数据预警',
    detail: '行情截至 2026-08-21',
    route: '/decision',
  })

  assert.deepEqual(dataFreshness.value, {
    level: 'YELLOW',
    label: '决策数据预警',
    detail: '行情截至 2026-08-21',
    route: '/decision',
  })
})

test('clearing data freshness removes stale page context on navigation', () => {
  publishDataFreshness({ level: 'GREEN', label: '数据正常', detail: '', route: '/dashboard' })
  clearDataFreshness()

  assert.equal(dataFreshness.value, null)
})
