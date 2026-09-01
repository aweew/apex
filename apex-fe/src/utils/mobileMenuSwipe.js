export const MENU_SWIPE_LOCK_DISTANCE = 8
export const MENU_SWIPE_OPEN_PROGRESS = 0.5

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

export function shouldOpenMenuAfterSwipe(progress) {
  return progress >= MENU_SWIPE_OPEN_PROGRESS
}
