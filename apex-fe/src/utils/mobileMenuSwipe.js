export const MENU_SWIPE_LOCK_DISTANCE = 8
export const MENU_SWIPE_OPEN_PROGRESS = 0.5
export const MENU_SWIPE_FLING_VELOCITY = 0.45
export const MOBILE_BACK_SWIPE_EDGE_WIDTH = 24
export const MOBILE_BACK_SWIPE_DISTANCE = 64
export const MOBILE_BACK_SWIPE_MIN_FLING_DISTANCE = 32
export const MOBILE_BACK_SWIPE_MAX_OFFSET = 48

export function resolveMenuSwipeAxis(deltaX, deltaY) {
  const horizontalDistance = Math.abs(deltaX)
  const verticalDistance = Math.abs(deltaY)
  if (Math.max(horizontalDistance, verticalDistance) < MENU_SWIPE_LOCK_DISTANCE) return 'pending'
  return horizontalDistance > verticalDistance * 1.15 ? 'horizontal' : 'vertical'
}

export function menuSwipeProgress(startProgress, deltaX, drawerWidth) {
  if (!drawerWidth) return startProgress
  return Math.min(1, Math.max(0, startProgress + deltaX / drawerWidth))
}

export function shouldOpenMenuAfterSwipe(progress, velocityX = 0) {
  if (velocityX >= MENU_SWIPE_FLING_VELOCITY) return true
  if (velocityX <= -MENU_SWIPE_FLING_VELOCITY) return false
  return progress >= MENU_SWIPE_OPEN_PROGRESS
}

export function isMobileBackSwipeStart(startX) {
  return startX >= 0 && startX <= MOBILE_BACK_SWIPE_EDGE_WIDTH
}

export function mobileBackSwipeOffset(deltaX) {
  return Math.min(MOBILE_BACK_SWIPE_MAX_OFFSET, Math.max(0, deltaX) * 0.32)
}

export function shouldNavigateBackAfterSwipe(deltaX, velocityX = 0) {
  return deltaX >= MOBILE_BACK_SWIPE_DISTANCE
    || (deltaX >= MOBILE_BACK_SWIPE_MIN_FLING_DISTANCE && velocityX >= MENU_SWIPE_FLING_VELOCITY)
}
