const ROUTE_MODULES = {
  dashboard: 'DASHBOARD',
  preMarketReport: 'PRE_MARKET_REPORT',
  apexAi: 'AI_CENTER',
  decision: 'DECISION',
  market: 'MARKET',
  limitUp: 'LIMIT_UP',
  sync: 'SYNC',
  hot: 'HOT',
  news: 'NEWS',
  holding: 'HOLDING',
  portfolio: 'PORTFOLIO',
  trades: 'TRADES',
  observe: 'OBSERVE',
  pipeline: 'PIPELINE',
  screener: 'SCREENER',
  valuation: 'VALUATION',
  watchlist: 'WATCHLIST',
  stock: 'STOCK',
  signals: 'SIGNALS',
  backtest: 'BACKTEST',
  paper: 'PAPER',
  daily: 'DAILY',
  config: 'CONFIG',
  usage: 'USAGE',
}

export function resolveUsageModule(route) {
  return ROUTE_MODULES[String(route?.name || '')] || ''
}
