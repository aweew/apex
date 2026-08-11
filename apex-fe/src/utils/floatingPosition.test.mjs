import assert from 'node:assert/strict'
import test from 'node:test'

import {
  clampFloatingPosition,
  floatingPositionFromRatio,
  floatingPositionToRatio,
} from './floatingPosition.js'

test('floating position stays inside the viewport', () => {
  const bounds = { viewportWidth: 390, viewportHeight: 720, width: 46, height: 46 }
  assert.deepEqual(clampFloatingPosition({ left: -20, top: 900 }, bounds), {
    left: 8,
    top: 666,
  })
})

test('relative position adapts between desktop and mobile viewports', () => {
  const desktop = { viewportWidth: 1440, viewportHeight: 900, width: 48, height: 48 }
  const mobile = { viewportWidth: 390, viewportHeight: 720, width: 46, height: 46 }
  const ratio = floatingPositionToRatio({ left: 700, top: 420 }, desktop)
  const restored = floatingPositionFromRatio(ratio, mobile)

  assert.ok(restored.left >= 8 && restored.left <= 336)
  assert.ok(restored.top >= 8 && restored.top <= 666)
  assert.ok(Math.abs(ratio.x - 0.5) < 0.01)
  assert.ok(Math.abs(ratio.y - 0.5) < 0.01)
})
