const SHANGHAI_TIME_ZONE = 'Asia/Shanghai'
const SUNDAY_VISIBLE_MINUTE = 21 * 60
const MONDAY_HIDDEN_MINUTE = 9 * 60 + 30

function shanghaiClock(value) {
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone: SHANGHAI_TIME_ZONE,
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  })
  const parts = Object.fromEntries(formatter.formatToParts(value)
    .filter((part) => ['weekday', 'hour', 'minute'].includes(part.type))
    .map((part) => [part.type, part.value]))
  return {
    weekday: parts.weekday,
    minuteOfDay: Number(parts.hour) * 60 + Number(parts.minute),
  }
}

export function isWeekendReportVisible(value = new Date()) {
  const clock = shanghaiClock(value)
  return (clock.weekday === 'Sun' && clock.minuteOfDay >= SUNDAY_VISIBLE_MINUTE)
    || (clock.weekday === 'Mon' && clock.minuteOfDay < MONDAY_HIDDEN_MINUTE)
}
