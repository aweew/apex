export function clampFloatingPosition(position, bounds, gap = 8) {
  const maxLeft = Math.max(gap, bounds.viewportWidth - bounds.width - gap)
  const maxTop = Math.max(gap, bounds.viewportHeight - bounds.height - gap)
  return {
    left: Math.min(maxLeft, Math.max(gap, position.left)),
    top: Math.min(maxTop, Math.max(gap, position.top)),
  }
}

export function floatingPositionToRatio(position, bounds, gap = 8) {
  const clamped = clampFloatingPosition(position, bounds, gap)
  const horizontalRange = Math.max(1, bounds.viewportWidth - bounds.width - gap * 2)
  const verticalRange = Math.max(1, bounds.viewportHeight - bounds.height - gap * 2)
  return {
    x: (clamped.left - gap) / horizontalRange,
    y: (clamped.top - gap) / verticalRange,
  }
}

export function floatingPositionToEdges(position, bounds, gap = 8) {
  const clamped = clampFloatingPosition(position, bounds, gap)
  return {
    right: Math.max(gap, bounds.viewportWidth - clamped.left - bounds.width),
    bottom: Math.max(gap, bounds.viewportHeight - clamped.top - bounds.height),
  }
}

export function floatingPositionFromRatio(ratio, bounds, gap = 8) {
  const horizontalRange = Math.max(0, bounds.viewportWidth - bounds.width - gap * 2)
  const verticalRange = Math.max(0, bounds.viewportHeight - bounds.height - gap * 2)
  return clampFloatingPosition(
    {
      left: gap + horizontalRange * Math.min(1, Math.max(0, Number(ratio?.x) || 0)),
      top: gap + verticalRange * Math.min(1, Math.max(0, Number(ratio?.y) || 0)),
    },
    bounds,
    gap,
  )
}
