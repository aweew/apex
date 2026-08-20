import test from 'node:test'
import assert from 'node:assert/strict'
import { bindLongPress, resolveMobileTooltipPosition } from './chartLongPress.js'

function pointerEvent(type, x, y) {
  const event = new Event(type, { cancelable: true })
  Object.defineProperties(event, {
    clientX: { value: x },
    clientY: { value: y },
    pointerType: { value: 'touch' },
    button: { value: 0 },
  })
  return event
}

function wait(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds))
}

test('quick tap does not activate the tooltip', async () => {
  const element = new EventTarget()
  let activated = 0
  const cleanup = bindLongPress({
    element,
    delay: 20,
    onActivate: () => activated++,
    onDeactivate: () => {},
  })

  element.dispatchEvent(pointerEvent('pointerdown', 20, 20))
  element.dispatchEvent(pointerEvent('pointerup', 20, 20))
  await wait(30)

  assert.equal(activated, 0)
  cleanup()
})

test('long press activates and release deactivates the tooltip', async () => {
  const element = new EventTarget()
  let activated = 0
  let deactivated = 0
  const cleanup = bindLongPress({
    element,
    delay: 10,
    onActivate: () => activated++,
    onDeactivate: () => deactivated++,
  })

  element.dispatchEvent(pointerEvent('pointerdown', 30, 30))
  await wait(20)
  assert.equal(activated, 1)

  element.dispatchEvent(pointerEvent('pointerup', 30, 30))
  assert.equal(deactivated, 1)
  cleanup()
})

test('moving before the delay cancels the long press', async () => {
  const element = new EventTarget()
  let activated = 0
  const cleanup = bindLongPress({
    element,
    delay: 20,
    movementTolerance: 10,
    onActivate: () => activated++,
    onDeactivate: () => {},
  })

  element.dispatchEvent(pointerEvent('pointerdown', 10, 10))
  element.dispatchEvent(pointerEvent('pointermove', 30, 10))
  await wait(30)

  assert.equal(activated, 0)
  cleanup()
})

test('long press movement is captured before the chart can pan', async () => {
  const listeners = new Map()
  const element = {
    addEventListener(type, listener, options) {
      listeners.set(type, { listener, options })
    },
    removeEventListener() {},
  }
  let updated = 0
  bindLongPress({
    element,
    delay: 10,
    onActivate: () => {},
    onUpdate: () => updated++,
    onDeactivate: () => {},
  })

  listeners.get('pointerdown').listener(pointerEvent('pointerdown', 10, 10))
  await wait(20)
  const moveEvent = pointerEvent('pointermove', 24, 10)
  listeners.get('pointermove').listener(moveEvent)

  assert.equal(listeners.get('pointermove').options.capture, true)
  assert.equal(moveEvent.defaultPrevented, true)
  assert.equal(moveEvent.cancelBubble, true)
  assert.equal(updated, 1)
})

test('mobile tooltip stays inside the visible part of a partially scrolled chart', () => {
  const [left, top] = resolveMobileTooltipPosition({
    point: [176, 450],
    contentSize: [280, 330],
    viewSize: [353, 520],
    chartTop: 250,
    viewportHeight: 812,
  })

  assert.ok(left >= 8)
  assert.ok(left + 280 <= 353 - 8)
  assert.ok(top >= 8)
  assert.ok(250 + top + 330 <= 812 - 8)
})

test('mobile tooltip accounts for a chart top above the viewport', () => {
  const [, top] = resolveMobileTooltipPosition({
    point: [80, 260],
    contentSize: [280, 330],
    viewSize: [353, 520],
    chartTop: -120,
    viewportHeight: 812,
  })

  assert.ok(-120 + top >= 8)
  assert.ok(-120 + top + 330 <= 812 - 8)
})
