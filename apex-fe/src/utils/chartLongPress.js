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
  onPinchStart,
  onPinch,
  onPinchEnd,
  delay = 320,
  deactivateDelay = 0,
  movementTolerance = 10,
  directionTolerance = 6,
}) {
  let pressTimer = null
  let deactivateTimer = null
  let pressActive = false
  let tooltipVisible = false
  let pressStart = null
  let inspectDirection = null
  let pinchActive = false
  let pinchStartDistance = 0
  let lastPinchScale = 1
  let suppressSinglePointer = false
  const activePointers = new Map()

  const clearPressTimer = () => {
    if (pressTimer) {
      clearTimeout(pressTimer)
      pressTimer = null
    }
  }
  const clearDeactivateTimer = () => {
    if (deactivateTimer) {
      clearTimeout(deactivateTimer)
      deactivateTimer = null
    }
  }
  const hideTooltip = () => {
    clearDeactivateTimer()
    if (!tooltipVisible) return
    tooltipVisible = false
    onDeactivate()
  }
  const deactivate = () => {
    clearPressTimer()
    pressStart = null
    inspectDirection = null
    if (!pressActive) return
    pressActive = false
    if (deactivateDelay > 0) {
      clearDeactivateTimer()
      deactivateTimer = setTimeout(hideTooltip, deactivateDelay)
      return
    }
    hideTooltip()
  }
  const pinchMetrics = () => {
    const points = [...activePointers.values()].slice(0, 2)
    if (points.length < 2) return null
    return {
      distance: Math.hypot(points[1].x - points[0].x, points[1].y - points[0].y),
      clientX: (points[0].x + points[1].x) / 2,
      clientY: (points[0].y + points[1].y) / 2,
    }
  }
  const startPinch = () => {
    if (pinchActive) return
    const metrics = pinchMetrics()
    if (!metrics || metrics.distance <= 0) return
    clearPressTimer()
    clearDeactivateTimer()
    pressActive = false
    pressStart = null
    inspectDirection = null
    suppressSinglePointer = true
    hideTooltip()
    pinchActive = true
    pinchStartDistance = metrics.distance
    lastPinchScale = 1
    onPinchStart?.(metrics)
  }
  const finishPinch = () => {
    if (!pinchActive) return
    pinchActive = false
    pinchStartDistance = 0
    lastPinchScale = 1
    onPinchEnd?.()
  }
  const onPointerDown = (event) => {
    if (event.pointerType === 'mouse' && event.button !== 0) return
    const pointerId = event.pointerId ?? 1
    activePointers.set(pointerId, { x: event.clientX, y: event.clientY })
    event.stopImmediatePropagation()
    if (activePointers.size >= 2) {
      event.preventDefault()
      startPinch()
      return
    }
    if (suppressSinglePointer) return
    clearPressTimer()
    pressActive = false
    inspectDirection = null
    pressStart = { x: event.clientX, y: event.clientY }
    pressTimer = setTimeout(() => {
      if (activePointers.size !== 1 || suppressSinglePointer) return
      pressTimer = null
      clearDeactivateTimer()
      pressActive = true
      tooltipVisible = true
      onActivate(event)
    }, delay)
  }
  const onPointerMove = (event) => {
    const pointerId = event.pointerId ?? 1
    if (!activePointers.has(pointerId)) return
    activePointers.set(pointerId, { x: event.clientX, y: event.clientY })
    event.stopImmediatePropagation()
    if (activePointers.size >= 2) {
      event.preventDefault()
      startPinch()
      const metrics = pinchMetrics()
      if (!metrics || pinchStartDistance <= 0) return
      const scale = metrics.distance / pinchStartDistance
      if (Math.abs(scale - lastPinchScale) < 0.01) return
      lastPinchScale = scale
      onPinch?.({ ...metrics, scale: Number(scale.toFixed(4)) })
      return
    }
    if (suppressSinglePointer) return
    if (!pressStart) return
    const deltaX = event.clientX - pressStart.x
    const deltaY = event.clientY - pressStart.y
    const moved = Math.hypot(deltaX, deltaY)
    if (!pressActive && moved > movementTolerance) {
      clearPressTimer()
      pressStart = null
      return
    }
    if (pressActive) {
      if (!inspectDirection) {
        const horizontalDistance = Math.abs(deltaX)
        const verticalDistance = Math.abs(deltaY)
        if (verticalDistance >= directionTolerance && verticalDistance > horizontalDistance) {
          pressActive = false
          pressStart = null
          inspectDirection = 'vertical'
          hideTooltip()
          return
        }
        if (horizontalDistance < directionTolerance || horizontalDistance < verticalDistance) return
        inspectDirection = 'horizontal'
      }
      event.preventDefault()
      onUpdate(event)
    }
  }
  const onPointerEnd = (event) => {
    const pointerId = event.pointerId ?? 1
    if (!activePointers.has(pointerId)) return
    event.stopImmediatePropagation()
    activePointers.delete(pointerId)
    if (pinchActive && activePointers.size < 2) finishPinch()
    if (activePointers.size > 0) return
    suppressSinglePointer = false
    deactivate()
  }
  const onContextMenu = (event) => event.preventDefault()

  element.addEventListener('pointerdown', onPointerDown, { capture: true, passive: false })
  element.addEventListener('pointermove', onPointerMove, { capture: true, passive: false })
  element.addEventListener('pointerup', onPointerEnd, { capture: true })
  element.addEventListener('pointercancel', onPointerEnd, { capture: true })
  element.addEventListener('pointerleave', onPointerEnd, { capture: true })
  element.addEventListener('contextmenu', onContextMenu)

  return () => {
    clearPressTimer()
    clearDeactivateTimer()
    finishPinch()
    activePointers.clear()
    pressActive = false
    pressStart = null
    inspectDirection = null
    suppressSinglePointer = false
    if (tooltipVisible) {
      tooltipVisible = false
      onDeactivate()
    }
    element.removeEventListener('pointerdown', onPointerDown, { capture: true })
    element.removeEventListener('pointermove', onPointerMove, { capture: true })
    element.removeEventListener('pointerup', onPointerEnd, { capture: true })
    element.removeEventListener('pointercancel', onPointerEnd, { capture: true })
    element.removeEventListener('pointerleave', onPointerEnd, { capture: true })
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
