export function snapshotStamp(payload, field = 'tradeDate') {
  const value = payload?.[field]
  return value ? String(value).slice(0, 10) : ''
}

export function snapshotFallbackText(requestedDate, actualDate) {
  const requested = requestedDate ? String(requestedDate).slice(0, 10) : ''
  const actual = actualDate ? String(actualDate).slice(0, 10) : ''
  if (!requested || !actual || requested === actual) return ''
  return `请求 ${requested}，当前展示最近可用数据 ${actual}`
}
