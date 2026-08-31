import test from 'node:test'
import assert from 'node:assert/strict'
import { bindChartWheelScroll, bindLongPress, resolveMobileTooltipPosition } from './chartLongPress.js'

function pointerEvent(type, x, y, pointerId = 1) {
  const event = new Event(type, { cancelable: true })
  Object.defineProperties(event, {
    clientX: { value: x },
    clientY: { value: y },
    pointerId: { value: pointerId },
    pointerType: { value: 'touch' },
    button: { value: 0 },
  })
  return event
}

function wait(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds))
}

test('ordinary chart wheel keeps page scrolling and does not reach ECharts', () => {
  let wheelListener
  const element = {
    addEventListener(type, listener) {
      if (type === 'wheel') wheelListener = listener
    },
    removeEventListener() {},
  }
  const cleanup = bindChartWheelScroll(element)
  let stopped = 0
  let prevented = 0

  wheelListener({
    ctrlKey: false,
    stopImmediatePropagation: () => stopped++,
    preventDefault: () => prevented++,
  })

  assert.equal(stopped, 1)
  assert.equal(prevented, 0)
  cleanup()
})

test('control wheel still reaches ECharts for chart zooming', () => {
  let wheelListener
  const element = {
    addEventListener(type, listener) {
      if (type === 'wheel') wheelListener = listener
    },
    removeEventListener() {},
  }
  const cleanup = bindChartWheelScroll(element)
  let stopped = 0

  wheelListener({ ctrlKey: true, stopImmediatePropagation: () => stopped++ })

  assert.equal(stopped, 0)
  cleanup()
})

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

test('long press tooltip remains visible briefly after release', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const element = new EventTarget()
  let deactivated = 0
  const cleanup = bindLongPress({
    element,
    delay: 10,
    deactivateDelay: 30,
    onActivate: () => {},
    onDeactivate: () => deactivated++,
  })

  element.dispatchEvent(pointerEvent('pointerdown', 30, 30))
  t.mock.timers.tick(10)
  element.dispatchEvent(pointerEvent('pointerup', 30, 30))
  t.mock.timers.tick(29)
  assert.equal(deactivated, 0)

  t.mock.timers.tick(1)
  assert.equal(deactivated, 1)
  cleanup()
})

test('another long press cancels the pending tooltip hide', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const element = new EventTarget()
  let activated = 0
  let deactivated = 0
  const cleanup = bindLongPress({
    element,
    delay: 10,
    deactivateDelay: 40,
    onActivate: () => activated++,
    onDeactivate: () => deactivated++,
  })

  element.dispatchEvent(pointerEvent('pointerdown', 30, 30))
  t.mock.timers.tick(10)
  element.dispatchEvent(pointerEvent('pointerup', 30, 30))
  t.mock.timers.tick(10)
  element.dispatchEvent(pointerEvent('pointerdown', 40, 40))
  t.mock.timers.tick(10)

  assert.equal(activated, 2)
  assert.equal(deactivated, 0)
  cleanup()
  assert.equal(deactivated, 1)
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

test('single-finger vertical movement stays with page scrolling and never reaches the chart', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const element = new EventTarget()
  let activated = 0
  let updated = 0
  const cleanup = bindLongPress({
    element,
    delay: 20,
    movementTolerance: 10,
    onActivate: () => activated++,
    onUpdate: () => updated++,
    onDeactivate: () => {},
  })

  const pointerDown = pointerEvent('pointerdown', 40, 40)
  element.dispatchEvent(pointerDown)
  const firstMove = pointerEvent('pointermove', 42, 62)
  element.dispatchEvent(firstMove)
  const continuedMove = pointerEvent('pointermove', 43, 88)
  element.dispatchEvent(continuedMove)
  t.mock.timers.tick(20)

  assert.equal(pointerDown.defaultPrevented, false)
  assert.equal(firstMove.defaultPrevented, false)
  assert.equal(continuedMove.defaultPrevented, false)
  assert.equal(pointerDown.cancelBubble, true)
  assert.equal(firstMove.cancelBubble, true)
  assert.equal(continuedMove.cancelBubble, true)
  assert.equal(activated, 0)
  assert.equal(updated, 0)
  cleanup()
})

test('vertical movement after long press exits inspection without blocking page scroll', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const element = new EventTarget()
  let activated = 0
  let updated = 0
  let deactivated = 0
  const cleanup = bindLongPress({
    element,
    delay: 20,
    deactivateDelay: 50,
    onActivate: () => activated++,
    onUpdate: () => updated++,
    onDeactivate: () => deactivated++,
  })

  element.dispatchEvent(pointerEvent('pointerdown', 80, 80))
  t.mock.timers.tick(20)
  const verticalMove = pointerEvent('pointermove', 82, 100)
  element.dispatchEvent(verticalMove)

  assert.equal(activated, 1)
  assert.equal(updated, 0)
  assert.equal(deactivated, 1)
  assert.equal(verticalMove.defaultPrevented, false)
  assert.equal(verticalMove.cancelBubble, true)
  cleanup()
})

test('two fingers cancel long press and emit a stable pinch scale', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  const element = new EventTarget()
  let activated = 0
  let pinchStarted = 0
  let pinchEnded = 0
  const pinchUpdates = []
  const cleanup = bindLongPress({
    element,
    delay: 20,
    onActivate: () => activated++,
    onDeactivate: () => {},
    onPinchStart: () => pinchStarted++,
    onPinch: (payload) => pinchUpdates.push(payload),
    onPinchEnd: () => pinchEnded++,
  })

  element.dispatchEvent(pointerEvent('pointerdown', 100, 80, 1))
  const secondDown = pointerEvent('pointerdown', 200, 80, 2)
  element.dispatchEvent(secondDown)
  const pinchMove = pointerEvent('pointermove', 220, 80, 2)
  element.dispatchEvent(pinchMove)
  t.mock.timers.tick(20)
  element.dispatchEvent(pointerEvent('pointerup', 220, 80, 2))
  element.dispatchEvent(pointerEvent('pointerup', 100, 80, 1))

  assert.equal(secondDown.defaultPrevented, true)
  assert.equal(pinchMove.defaultPrevented, true)
  assert.equal(secondDown.cancelBubble, true)
  assert.equal(pinchMove.cancelBubble, true)
  assert.equal(activated, 0)
  assert.equal(pinchStarted, 1)
  assert.equal(pinchUpdates.length, 1)
  assert.equal(pinchUpdates[0].scale, 1.2)
  assert.equal(pinchUpdates[0].clientX, 160)
  assert.equal(pinchEnded, 1)
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
