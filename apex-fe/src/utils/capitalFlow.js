function toNumber(value) {
  if (value === null || value === undefined || value === '') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function formatSigned(value, digits, suffix) {
  const amount = toNumber(value)
  if (amount === null) return '-'
  const prefix = amount > 0 ? '+' : ''
  return `${prefix}${amount.toFixed(digits)}${suffix}`
}

export function formatCapitalAmount(value) {
  const amount = toNumber(value)
  if (amount === null) return '-'
  if (Math.abs(amount) >= 100000000) return formatSigned(amount / 100000000, 2, '亿')
  if (Math.abs(amount) >= 10000) return formatSigned(amount / 10000, 2, '万')
  return formatSigned(amount, 2, '')
}

export function formatNorthboundAmount(value) {
  return formatCapitalAmount(value)
}

export function formatCapitalPercent(value) {
  const percent = toNumber(value)
  return percent === null ? '-' : formatSigned(percent, 2, '%')
}

export function formatCapitalPrice(value) {
  const price = toNumber(value)
  return price === null ? '-' : price.toFixed(2)
}

export function resolveCapitalClass(value) {
  const amount = toNumber(value)
  if (amount > 0) return 'up'
  if (amount < 0) return 'down'
  return 'flat'
}
