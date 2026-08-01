import http from './http'

export function getAccount() {
  return http.get('/api/paper/account')
}

export function openAccount(payload) {
  return http.post('/api/paper/open', payload)
}

export function placeOrder(payload) {
  return http.post('/api/paper/order', payload)
}

export function listPositions(accountId) {
  return http.get('/api/paper/positions', { params: { accountId } })
}

export function listOrders(accountId) {
  return http.get('/api/paper/orders', { params: { accountId } })
}

export function suggestPosition(code, accountId, targetWeight) {
  return http.get('/api/paper/suggest', { params: { code, accountId, targetWeight } })
}

export function closeAllPositions(accountId) {
  return http.post('/api/paper/close-all', null, { params: { accountId } })
}

export function orderFromSignal(signalId, accountId, targetWeight) {
  return http.post('/api/paper/from-signal', null, {
    params: { signalId, accountId, targetWeight },
  })
}

export function refreshMarks(accountId) {
  return http.post('/api/paper/refresh-marks', null, { params: { accountId } })
}

export function paperPerformance(accountId, benchmarkCode = '000300') {
  return http.get('/api/paper/performance', { params: { accountId, benchmarkCode } })
}

export function paperExposure(accountId) {
  return http.get('/api/paper/exposure', { params: { accountId } })
}

export function updateStops(payload) {
  return http.post('/api/paper/stops', payload)
}

export function closeTriggered(accountId, type = 'BOTH') {
  return http.post('/api/paper/close-triggered', null, { params: { accountId, type } })
}

export function rebalanceSuggest(accountId, limit = 8) {
  return http.get('/api/paper/rebalance-suggest', { params: { accountId, limit } })
}

export function signalBuySuggest(accountId, limit = 5, minScore = 70) {
  return http.get('/api/paper/signal-buy-suggest', { params: { accountId, limit, minScore } })
}

export function paperMonthly(accountId) {
  return http.get('/api/paper/monthly', { params: { accountId } })
}

export function paperCorrelation(accountId, lookback = 60) {
  return http.get('/api/paper/correlation', { params: { accountId, lookback } })
}

export function paperCost(accountId) {
  return http.get('/api/paper/cost', { params: { accountId } })
}

export function paperKelly(accountId) {
  return http.get('/api/paper/kelly', { params: { accountId } })
}

export function paperFillQuality(accountId, limit = 30) {
  return http.get('/api/paper/fill-quality', { params: { accountId, limit } })
}

export function paperGapRisk(accountId) {
  return http.get('/api/paper/gap-risk', { params: { accountId } })
}

export function paperHoldBuckets(accountId) {
  return http.get('/api/paper/hold-buckets', { params: { accountId } })
}

export function paperWeekdayPnl(accountId) {
  return http.get('/api/paper/weekday-pnl', { params: { accountId } })
}

export function paperMonteCarlo(accountId, paths = 500, horizonDays = 20) {
  return http.get('/api/paper/monte-carlo', { params: { accountId, paths, horizonDays } })
}

export function paperFactorExposure(accountId) {
  return http.get('/api/paper/factor-exposure', { params: { accountId } })
}

export function paperAtrStops(accountId) {
  return http.get('/api/paper/atr-stops', { params: { accountId } })
}

export function applyAtrStops(accountId) {
  return http.post('/api/paper/atr-stops/apply', null, { params: { accountId } })
}

export function paperReturnHist(accountId) {
  return http.get('/api/paper/return-hist', { params: { accountId } })
}

export function paperVolTarget(accountId) {
  return http.get('/api/paper/vol-target', { params: { accountId } })
}

export function paperTradeCalendar(accountId, days = 60) {
  return http.get('/api/paper/trade-calendar', { params: { accountId, days } })
}

export function paperStopCoverage(accountId) {
  return http.get('/api/paper/stop-coverage', { params: { accountId } })
}

export function paperBetaTarget(accountId) {
  return http.get('/api/paper/beta-target', { params: { accountId } })
}

export function paperHealthScore(accountId) {
  return http.get('/api/paper/health-score', { params: { accountId } })
}

export function paperEquityQuality(accountId) {
  return http.get('/api/paper/equity-quality', { params: { accountId } })
}
