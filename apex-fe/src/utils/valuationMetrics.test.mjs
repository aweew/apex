import test from 'node:test'
import assert from 'node:assert/strict'

import { availablePeMetrics } from './valuationMetrics.js'

test('availablePeMetrics keeps valid PE values in a stable order', () => {
  assert.deepEqual(
    availablePeMetrics({ peDynamic: 22, peStatic: 18.45, peTtm: 20.1 }),
    [
      { key: 'peDynamic', label: '动', value: '22.0' },
      { key: 'peStatic', label: '静', value: '18.5' },
      { key: 'peTtm', label: 'TTM', value: '20.1' },
    ],
  )
})

test('availablePeMetrics omits missing, invalid, and non-positive values', () => {
  assert.deepEqual(
    availablePeMetrics({ peDynamic: -183.29, peStatic: null, peTtm: '36.7' }),
    [{ key: 'peTtm', label: 'TTM', value: '36.7' }],
  )
  assert.deepEqual(availablePeMetrics({}), [])
})
