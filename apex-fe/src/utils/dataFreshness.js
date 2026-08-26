import { ref } from 'vue'

export const dataFreshness = ref(null)
export const tradingCalendar = ref(undefined)

const SHANGHAI_TIME_ZONE = 'Asia/Shanghai'
const MORNING_START = 9 * 60 + 30
const MORNING_END = 11 * 60 + 30
const AFTERNOON_START = 13 * 60
const MARKET_CLOSE = 15 * 60
const DAILY_SYNC_READY = 15 * 60 + 30
const INTRADAY_FRESH_MINUTES = 5

function shanghaiParts(value) {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: SHANGHAI_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  })
  return Object.fromEntries(formatter.formatToParts(value)
    .filter((part) => ['year', 'month', 'day', 'hour', 'minute'].includes(part.type))
    .map((part) => [part.type, part.value]))
}

function normalizeDate(value) {
  return value ? String(value).slice(0, 10) : ''
}

function normalizeDateTime(value) {
  if (!value) return ''
  const text = String(value).replace('T', ' ')
  return text.length > 10 ? text.slice(0, 16) : text.slice(0, 10)
}

function timestampMinute(value) {
  const match = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/)
  if (!match) return null
  return {
    date: `${match[1]}-${match[2]}-${match[3]}`,
    minuteOfDay: Number(match[4]) * 60 + Number(match[5]),
  }
}

function latestTradeDate(calendar, minuteOfDay, intraday) {
  if (!calendar?.tradingDay) return normalizeDate(calendar?.latestTradingDay)
  if (intraday) {
    return minuteOfDay >= MORNING_START
      ? normalizeDate(calendar.date)
      : normalizeDate(calendar.prevTradingDay)
  }
  return minuteOfDay >= DAILY_SYNC_READY
    ? normalizeDate(calendar.date)
    : normalizeDate(calendar.prevTradingDay)
}

function isFreshIntradayTime(updatedAt, currentDate, minuteOfDay) {
  const timestamp = timestampMinute(updatedAt)
  if (!timestamp || timestamp.date !== currentDate) return false
  if (minuteOfDay >= MORNING_START && minuteOfDay < MORNING_END) {
    return minuteOfDay - timestamp.minuteOfDay >= 0
      && minuteOfDay - timestamp.minuteOfDay <= INTRADAY_FRESH_MINUTES
  }
  if (minuteOfDay >= MORNING_END && minuteOfDay < AFTERNOON_START) {
    return timestamp.minuteOfDay >= MORNING_END - INTRADAY_FRESH_MINUTES
  }
  if (minuteOfDay >= AFTERNOON_START && minuteOfDay < MARKET_CLOSE) {
    return minuteOfDay - timestamp.minuteOfDay >= 0
      && minuteOfDay - timestamp.minuteOfDay <= INTRADAY_FRESH_MINUTES
  }
  return timestamp.minuteOfDay >= MARKET_CLOSE - INTRADAY_FRESH_MINUTES
}

export function chinaMarketDate(value = new Date()) {
  const parts = shanghaiParts(value)
  return `${parts.year}-${parts.month}-${parts.day}`
}

export function setTradingCalendar(value) {
  tradingCalendar.value = value || null
}

export function staleDataTime({
  tradeDate,
  updatedAt,
  intraday = false,
  latest,
  calendar = tradingCalendar.value,
  now = new Date(),
} = {}) {
  if (latest === true) return ''
  const fallback = normalizeDateTime(updatedAt) || normalizeDate(tradeDate)
  if (latest === false) return fallback ? `最后同步 ${fallback}` : '最后同步时间未知'
  if (calendar === undefined) return ''
  if (!calendar || normalizeDate(calendar.date) !== chinaMarketDate(now)) {
    return fallback ? `最后同步 ${fallback}` : '最后同步时间未知'
  }

  const parts = shanghaiParts(now)
  const minuteOfDay = Number(parts.hour) * 60 + Number(parts.minute)
  const expectedTradeDate = latestTradeDate(calendar, minuteOfDay, intraday)
  const actualTradeDate = normalizeDate(tradeDate) || timestampMinute(updatedAt)?.date || ''
  let fresh = Boolean(expectedTradeDate && actualTradeDate === expectedTradeDate)
  if (fresh && intraday && expectedTradeDate === normalizeDate(calendar.date)
      && minuteOfDay >= MORNING_START) {
    fresh = isFreshIntradayTime(updatedAt, expectedTradeDate, minuteOfDay)
  }
  if (fresh) return ''
  return fallback ? `最后同步 ${fallback}` : '最后同步时间未知'
}

export function publishDataFreshness(payload) {
  if (!payload?.label || !payload?.route) return
  dataFreshness.value = {
    level: payload.level || 'YELLOW',
    label: payload.label,
    detail: payload.detail || '',
    route: payload.route,
  }
}

export function clearDataFreshness() {
  dataFreshness.value = null
}
