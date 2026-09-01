function toNumber(value) {
  if (value === null || value === undefined || value === '') return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

const DRAGON_TIGER_NUMERIC_FIELDS = new Set([
  'closePrice',
  'pctChg',
  'turnoverRate',
  'netBuyAmount',
  'buyAmount',
  'sellAmount',
  'amount',
])

const STOCK_FLOW_NUMERIC_FIELDS = new Set([
  'pctChg',
  'mainNetInflow',
  'mainNetInflowPct',
  'superLargeNetInflow',
  'largeNetInflow',
  'mediumNetInflow',
  'smallNetInflow',
])

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

function sortCapitalFlowItems(items, prop, order, numericFields) {
  if (!Array.isArray(items) || !prop || !['ascending', 'descending'].includes(order)) {
    return Array.isArray(items) ? [...items] : []
  }
  const direction = order === 'ascending' ? 1 : -1
  const indexedItems = items.map((item, index) => ({ item, index }))
  indexedItems.sort((left, right) => {
    const leftRawValue = prop === 'name' ? left.item?.name || left.item?.code : left.item?.[prop]
    const rightRawValue = prop === 'name' ? right.item?.name || right.item?.code : right.item?.[prop]
    const leftValue = numericFields.has(prop) ? toNumber(leftRawValue) : leftRawValue
    const rightValue = numericFields.has(prop) ? toNumber(rightRawValue) : rightRawValue
    const leftMissing = leftValue === null || leftValue === undefined || leftValue === ''
    const rightMissing = rightValue === null || rightValue === undefined || rightValue === ''
    if (leftMissing || rightMissing) {
      if (leftMissing && rightMissing) return left.index - right.index
      return leftMissing ? 1 : -1
    }
    const result = typeof leftValue === 'number'
      ? leftValue - rightValue
      : String(leftValue).localeCompare(String(rightValue), 'zh-CN')
    return result === 0 ? left.index - right.index : result * direction
  })
  return indexedItems.map(({ item }) => item)
}

export function sortDragonTigerItems(items, prop, order) {
  return sortCapitalFlowItems(items, prop, order, DRAGON_TIGER_NUMERIC_FIELDS)
}

export function sortStockFlowItems(items, prop, order) {
  return sortCapitalFlowItems(items, prop, order, STOCK_FLOW_NUMERIC_FIELDS)
}
