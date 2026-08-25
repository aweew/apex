const GLOBAL_MARKET_CONFIG = [
  {
    key: 'us',
    label: '美国市场',
    benchmarkCode: 'US_SPX',
    position: { x: 14, y: 41, mobileX: 24, mobileY: 16 },
  },
  {
    key: 'cn',
    label: '中国内地',
    benchmarkCode: 'CN_SH',
    position: { x: 62, y: 46, mobileX: 24, mobileY: 39 },
  },
  {
    key: 'hk',
    label: '中国香港',
    benchmarkCode: 'HK_HSI',
    position: { x: 63, y: 71, mobileX: 24, mobileY: 63 },
  },
  {
    key: 'kr',
    label: '韩国市场',
    benchmarkCode: 'KR_KOSPI',
    position: { x: 77, y: 35, mobileX: 76, mobileY: 39 },
  },
  {
    key: 'jp',
    label: '日本市场',
    benchmarkCode: 'JP_N225',
    position: { x: 87, y: 54, mobileX: 76, mobileY: 63 },
  },
]

export function buildGlobalMarketHubs(marketIndexes = {}) {
  return GLOBAL_MARKET_CONFIG.map((market) => {
    const items = Array.isArray(marketIndexes[market.key]) ? marketIndexes[market.key] : []
    return {
      ...market,
      items,
      primary: items.find((item) => item.code === market.benchmarkCode) || items[0] || null,
    }
  })
}

export function summarizeGlobalMarkets(marketIndexes = {}) {
  const items = Object.values(marketIndexes).flatMap((rows) => (Array.isArray(rows) ? rows : []))
  const tradeDates = items.map((item) => item.tradeDate).filter(Boolean).sort()
  let up = 0
  let down = 0
  let flat = 0

  for (const item of items) {
    if (item.pctChg == null || Number.isNaN(Number(item.pctChg))) continue
    const pctChg = Number(item.pctChg)
    if (pctChg > 0) up += 1
    else if (pctChg < 0) down += 1
    else flat += 1
  }

  return {
    up,
    down,
    flat,
    total: items.length,
    latestTradeDate: tradeDates.at(-1) || '',
  }
}

export function derivePointChange(item) {
  if (item?.closePrice == null || item?.pctChg == null) return null
  const closePrice = Number(item.closePrice)
  const pctChg = Number(item.pctChg)
  if (Number.isNaN(closePrice) || Number.isNaN(pctChg) || pctChg === -100) return null
  const previousClose = closePrice / (1 + pctChg / 100)
  return closePrice - previousClose
}
