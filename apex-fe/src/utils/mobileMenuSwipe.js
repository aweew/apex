export const MENU_SWIPE_LOCK_DISTANCE = 8
export const MENU_SWIPE_OPEN_PROGRESS = 0.42
export const MENU_SWIPE_RELEASE_VELOCITY = 0.45

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

export function menuContentOffset(progress, drawerWidth) {
  const width = Math.max(0, Number(drawerWidth) || 0)
  const boundedProgress = Math.min(1, Math.max(0, Number(progress) || 0))
  return boundedProgress * width
}

export function shouldOpenMenuAfterSwipe(progress, velocity) {
  if (velocity >= MENU_SWIPE_RELEASE_VELOCITY) return true
  if (velocity <= -MENU_SWIPE_RELEASE_VELOCITY) return false
  return progress >= MENU_SWIPE_OPEN_PROGRESS
}
