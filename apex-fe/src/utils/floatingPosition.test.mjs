import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

import {
  clampFloatingPosition,
  floatingPositionToEdges,
  floatingPositionFromRatio,
  floatingPositionToRatio,
} from './floatingPosition.js'

const sharedStyleSource = await readFile(new URL('../style.css', import.meta.url), 'utf8')
const shareButtonSource = await readFile(new URL('../components/FloatingShareButton.vue', import.meta.url), 'utf8')

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

test('dragged position converts back to fixed right and bottom edges', () => {
  const bounds = { viewportWidth: 390, viewportHeight: 720, width: 42, height: 42 }

  assert.deepEqual(floatingPositionToEdges({ left: 320, top: 650 }, bounds), {
    right: 28,
    bottom: 28,
  })
  assert.deepEqual(floatingPositionToEdges({ left: -20, top: 900 }, bounds), {
    right: 340,
    bottom: 8,
  })
})

test('page entrance does not create a containing block for fixed controls', () => {
  const pageIn = sharedStyleSource.slice(
    sharedStyleSource.indexOf('@keyframes pageIn'),
    sharedStyleSource.indexOf('@media (max-width: 1200px)'),
  )

  assert.doesNotMatch(pageIn, /transform:/)
})

test('share progress animation respects reduced-motion preferences', () => {
  assert.match(
    shareButtonSource,
    /@media \(prefers-reduced-motion: reduce\)\s*\{[\s\S]*?\.spinning\s*\{[\s\S]*?animation:\s*none;/,
  )
})
