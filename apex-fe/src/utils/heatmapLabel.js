const LABEL_SIZE_STEPS = [
  { minWidth: 90, minHeight: 40, fontSize: 12 },
  { minWidth: 64, minHeight: 32, fontSize: 11 },
  { minWidth: 44, minHeight: 24, fontSize: 10 },
  { minWidth: 28, minHeight: 19, fontSize: 9 },
  { minWidth: 21, minHeight: 17, fontSize: 8 },
]

export function resolveTreemapLabelFontSize(rect) {
  const width = Number(rect?.width)
  const height = Number(rect?.height)
  if (!Number.isFinite(width) || !Number.isFinite(height)) return 0

  for (const step of LABEL_SIZE_STEPS) {
    if (width >= step.minWidth && height >= step.minHeight) return step.fontSize
  }
  return 0
}
