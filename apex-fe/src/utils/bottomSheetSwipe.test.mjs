import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import {
  bottomSheetOffset,
  resolveBottomSheetSwipeAxis,
  shouldDismissBottomSheet,
} from './bottomSheetSwipe.js'

const glossarySource = readFileSync(new URL('../components/GlossaryPanel.vue', import.meta.url), 'utf8')
const tradeSource = readFileSync(new URL('../components/HoldingTradeDialog.vue', import.meta.url), 'utf8')

test('bottom sheet swipe locks to the dominant axis after intentional movement', () => {
  assert.equal(resolveBottomSheetSwipeAxis(3, 7), null)
  assert.equal(resolveBottomSheetSwipeAxis(12, 9), 'horizontal')
  assert.equal(resolveBottomSheetSwipeAxis(4, 15), 'vertical')
})

test('bottom sheet only follows downward movement within its height', () => {
  assert.equal(bottomSheetOffset(-20, 600), 0)
  assert.equal(bottomSheetOffset(180, 600), 180)
  assert.equal(bottomSheetOffset(720, 600), 600)
})

test('bottom sheet closes after enough distance or a fast downward release', () => {
  assert.equal(shouldDismissBottomSheet(150, 600, 0.2), false)
  assert.equal(shouldDismissBottomSheet(170, 600, 0.2), true)
  assert.equal(shouldDismissBottomSheet(48, 600, 0.6), true)
  assert.equal(shouldDismissBottomSheet(48, 600, -0.6), false)
})

test('glossary and mobile trade drawer bind the shared gesture to their top zones', () => {
  assert.match(glossarySource, /useBottomSheetSwipe/)
  assert.match(glossarySource, /class="glossary-drag-zone"[\s\S]*?@touchstart="sheetSwipe\.onTouchStart"[\s\S]*?@touchmove="sheetSwipe\.onTouchMove"[\s\S]*?@touchend="sheetSwipe\.onTouchEnd"/)
  assert.match(tradeSource, /useBottomSheetSwipe/)
  assert.match(tradeSource, /class="trade-dialog-header"[\s\S]*?@touchstart="sheetSwipe\.onTouchStart"[\s\S]*?@touchmove="sheetSwipe\.onTouchMove"[\s\S]*?@touchend="sheetSwipe\.onTouchEnd"/)
})
