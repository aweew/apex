export function bindChartWheelScroll(element) {
  const onWheel = (event) => {
    if (!event.ctrlKey) event.stopImmediatePropagation()
  }

  element.addEventListener('wheel', onWheel, { capture: true, passive: true })

  return () => {
    element.removeEventListener('wheel', onWheel, { capture: true })
  }
}

export function bindLongPress({
  element,
  onActivate,
  onUpdate = onActivate,
  onDeactivate,
  delay = 320,
  movementTolerance = 10,
}) {
  let pressTimer = null
  let pressActive = false
  let pressStart = null

  const clearPressTimer = () => {
    if (pressTimer) {
      clearTimeout(pressTimer)
      pressTimer = null
    }
  }
  const deactivate = () => {
    clearPressTimer()
    pressStart = null
    if (!pressActive) return
    pressActive = false
    onDeactivate()
  }
  const onPointerDown = (event) => {
    if (event.pointerType === 'mouse' && event.button !== 0) return
    deactivate()
    pressStart = { x: event.clientX, y: event.clientY }
    pressTimer = setTimeout(() => {
      pressTimer = null
      pressActive = true
      onActivate(event)
    }, delay)
  }
  const onPointerMove = (event) => {
    if (!pressStart) return
    const moved = Math.hypot(event.clientX - pressStart.x, event.clientY - pressStart.y)
    if (!pressActive && moved > movementTolerance) {
      clearPressTimer()
      pressStart = null
      return
    }
    if (pressActive) {
      event.preventDefault()
      event.stopImmediatePropagation()
      onUpdate(event)
    }
  }
  const onContextMenu = (event) => event.preventDefault()

  element.addEventListener('pointerdown', onPointerDown)
  element.addEventListener('pointermove', onPointerMove, { capture: true, passive: false })
  element.addEventListener('pointerup', deactivate)
  element.addEventListener('pointercancel', deactivate)
  element.addEventListener('pointerleave', deactivate)
  element.addEventListener('contextmenu', onContextMenu)

  return () => {
    deactivate()
    element.removeEventListener('pointerdown', onPointerDown)
    element.removeEventListener('pointermove', onPointerMove, { capture: true })
    element.removeEventListener('pointerup', deactivate)
    element.removeEventListener('pointercancel', deactivate)
    element.removeEventListener('pointerleave', deactivate)
    element.removeEventListener('contextmenu', onContextMenu)
  }
}

export function resolveMobileTooltipPosition({
  point,
  contentSize,
  viewSize,
  chartTop,
  viewportHeight,
}) {
  const [contentWidth, contentHeight] = contentSize
  const [viewWidth, viewHeight] = viewSize
  const left = point[0] < viewWidth / 2 ? Math.max(8, viewWidth - contentWidth - 8) : 8
  const visibleTop = Math.max(8, -chartTop + 8)
  const visibleBottom = Math.min(viewHeight, viewportHeight - chartTop) - contentHeight - 8
  const top = visibleBottom <= visibleTop
    ? visibleTop
    : Math.min(Math.max(point[1] - contentHeight / 2, visibleTop), visibleBottom)
  return [left, top]
}
