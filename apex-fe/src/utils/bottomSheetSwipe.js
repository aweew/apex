import { computed, onBeforeUnmount, ref } from 'vue'

const INTENT_LOCK_PX = 8
const DISMISS_PROGRESS = 0.28
const DISMISS_VELOCITY = 0.55
const SETTLE_DURATION_MS = 180

export function resolveBottomSheetSwipeAxis(deltaX, deltaY) {
  if (Math.max(Math.abs(deltaX), Math.abs(deltaY)) < INTENT_LOCK_PX) return null
  return Math.abs(deltaY) > Math.abs(deltaX) ? 'vertical' : 'horizontal'
}

export function bottomSheetOffset(deltaY, sheetHeight) {
  const height = Math.max(0, Number(sheetHeight) || 0)
  return Math.min(height, Math.max(0, Number(deltaY) || 0))
}

export function shouldDismissBottomSheet(offsetY, sheetHeight, velocityY) {
  const height = Math.max(1, Number(sheetHeight) || 1)
  const progress = Math.max(0, Number(offsetY) || 0) / height
  return progress >= DISMISS_PROGRESS || Number(velocityY) >= DISMISS_VELOCITY
}

function isInteractiveTarget(target) {
  return Boolean(target?.closest?.('button, a, input, textarea, select, [role="button"]'))
}

export function useBottomSheetSwipe({ enabled, onDismiss }) {
  const offsetY = ref(0)
  const dragging = ref(false)
  const settling = ref(false)
  let gesture = null
  let settleTimer

  const panelStyle = computed(() => {
    if (!dragging.value && !settling.value && offsetY.value === 0) return undefined
    return {
      transform: `translate3d(0, ${offsetY.value}px, 0)`,
      transition: dragging.value ? 'none' : `transform ${SETTLE_DURATION_MS}ms ease-out`,
      willChange: 'transform',
    }
  })

  const backdropStyle = computed(() => {
    if (offsetY.value <= 0 || !gesture?.height) return undefined
    const progress = Math.min(1, offsetY.value / gesture.height)
    return { backgroundColor: `rgba(15, 23, 42, ${Math.max(0.08, 0.38 * (1 - progress))})` }
  })

  function clearSettleTimer() {
    if (!settleTimer) return
    window.clearTimeout(settleTimer)
    settleTimer = undefined
  }

  function reset() {
    clearSettleTimer()
    gesture = null
    offsetY.value = 0
    dragging.value = false
    settling.value = false
  }

  function onTouchStart(event) {
    if (!enabled?.() || event.touches?.length !== 1 || isInteractiveTarget(event.target)) return
    const touch = event.touches[0]
    const sheet = event.currentTarget?.closest?.('[data-bottom-sheet], .el-drawer')
    const sheetHeight = sheet?.getBoundingClientRect?.().height || window.innerHeight
    clearSettleTimer()
    settling.value = false
    gesture = {
      axis: null,
      height: sheetHeight,
      startX: touch.clientX,
      startY: touch.clientY,
      startAt: event.timeStamp,
    }
  }

  function onTouchMove(event) {
    if (!gesture || event.touches?.length !== 1) return
    const touch = event.touches[0]
    const deltaX = touch.clientX - gesture.startX
    const deltaY = touch.clientY - gesture.startY
    if (!gesture.axis) {
      gesture.axis = resolveBottomSheetSwipeAxis(deltaX, deltaY)
    }
    if (gesture.axis !== 'vertical') return
    offsetY.value = bottomSheetOffset(deltaY, gesture.height)
    dragging.value = offsetY.value > 0
    if (event.cancelable) event.preventDefault()
  }

  function onTouchEnd(event) {
    if (!gesture) return
    const touch = event.changedTouches?.[0]
    const endY = touch?.clientY ?? gesture.startY + offsetY.value
    const duration = Math.max(1, event.timeStamp - gesture.startAt)
    const velocityY = (endY - gesture.startY) / duration
    const dismiss = gesture.axis === 'vertical'
      && shouldDismissBottomSheet(offsetY.value, gesture.height, velocityY)
    gesture = null
    dragging.value = false
    if (dismiss) {
      onDismiss?.()
      settleTimer = window.setTimeout(reset, SETTLE_DURATION_MS)
      return
    }
    settling.value = offsetY.value > 0
    offsetY.value = 0
    settleTimer = window.setTimeout(reset, SETTLE_DURATION_MS)
  }

  onBeforeUnmount(reset)

  return {
    backdropStyle,
    dragging,
    onTouchCancel: reset,
    onTouchEnd,
    onTouchMove,
    onTouchStart,
    panelStyle,
    reset,
  }
}
