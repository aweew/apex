const SHANGHAI_TIME_ZONE = 'Asia/Shanghai'
const REPORT_OPEN_MINUTE = 18 * 60 + 30
const NEXT_SESSION_OPEN_MINUTE = 9 * 60 + 30

function shanghaiClock(value) {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: SHANGHAI_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  })
  const parts = Object.fromEntries(formatter.formatToParts(value)
    .filter((part) => ['year', 'month', 'day', 'hour', 'minute'].includes(part.type))
    .map((part) => [part.type, part.value]))
  return {
    date: `${parts.year}-${parts.month}-${parts.day}`,
    minuteOfDay: Number(parts.hour) * 60 + Number(parts.minute),
  }
}

export function isPostMarketReportVisible(value = new Date(), calendar) {
  if (!calendar?.date) return false
  const clock = shanghaiClock(value)
  const calendarDate = String(calendar.date).slice(0, 10)
  const nextTradingDay = String(calendar.nextTradingDay || '').slice(0, 10)

  if (clock.date === calendarDate) {
    if (calendar.tradingDay) {
      return clock.minuteOfDay < NEXT_SESSION_OPEN_MINUTE || clock.minuteOfDay >= REPORT_OPEN_MINUTE
    }
    return Boolean(calendar.latestTradingDay)
  }
  if (clock.date > calendarDate && (!nextTradingDay || clock.date < nextTradingDay)) return true
  return clock.date === nextTradingDay && clock.minuteOfDay < NEXT_SESSION_OPEN_MINUTE
}
