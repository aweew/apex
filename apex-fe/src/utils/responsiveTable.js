export function resolveActionColumnFixed(viewportWidth) {
  return Number(viewportWidth) <= 820 ? false : 'right'
}

export function resolveActionColumnVisible(viewportWidth) {
  return Number(viewportWidth) > 820
}
