const MARKET_SESSION_CONFIG = {
  cn: {
    timeZone: 'Asia/Shanghai',
    sessions: [[570, 690], [780, 900]],
  },
  hk: {
    timeZone: 'Asia/Shanghai',
    sessions: [[570, 720], [780, 960]],
  },
  jp: {
    timeZone: 'Asia/Shanghai',
    sessions: [[480, 630], [690, 870]],
  },
  kr: {
    timeZone: 'Asia/Shanghai',
    sessions: [[480, 870]],
  },
  us: {
    timeZone: 'America/New_York',
    sessions: [[570, 960]],
  },
}

export const MARKET_PRIORITY = ['cn', 'hk', 'jp', 'kr', 'us']

export function resolveMarketTab(market) {
  if (market === 'jp' || market === 'kr') return 'asia'
  return market
}

function localMarketClock(time, timeZone) {
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone,
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  })
  const parts = Object.fromEntries(formatter.formatToParts(time)
    .filter((part) => ['weekday', 'hour', 'minute'].includes(part.type))
    .map((part) => [part.type, part.value]))
  return {
    weekday: parts.weekday,
    minuteOfDay: Number(parts.hour) * 60 + Number(parts.minute),
  }
}

export function isMarketOpen(market, time = new Date()) {
  const config = MARKET_SESSION_CONFIG[market]
  if (!config) return false
  const clock = localMarketClock(time, config.timeZone)
  if (clock.weekday === 'Sat' || clock.weekday === 'Sun') return false
  return config.sessions.some(([start, end]) => clock.minuteOfDay >= start && clock.minuteOfDay < end)
}

export function resolveActiveMarket(time = new Date()) {
  return MARKET_PRIORITY.find((market) => isMarketOpen(market, time)) || null
}
