import http from './http'

export function runBacktest(payload) {
  return http.post('/api/backtest/run', payload)
}

export function getBacktestDetail(id) {
  return http.get(`/api/backtest/${id}`)
}

export function listBacktestJobs(limit = 20) {
  return http.get('/api/backtest/jobs', { params: { limit } })
}

export function batchBacktest(payload) {
  return http.post('/api/backtest/batch', payload || {})
}

export function compareStrategies(payload) {
  return http.post('/api/backtest/compare', payload)
}

export function portfolioBacktest(payload) {
  return http.post('/api/backtest/portfolio', payload || {})
}

export function benchmarkCompare(payload, benchmarkCode = '000300') {
  return http.post('/api/backtest/benchmark', payload, { params: { benchmarkCode } })
}

export function strategyLeaderboard(limit = 100) {
  return http.get('/api/backtest/leaderboard', { params: { limit } })
}

export function paramSweep(payload) {
  return http.post('/api/backtest/sweep', payload || {})
}

export function walkForward(payload, inSampleRatio = 0.7) {
  return http.post('/api/backtest/walk-forward', payload || {}, { params: { inSampleRatio } })
}

export function monthlyReturns(jobId) {
  return http.get(`/api/backtest/${jobId}/monthly`)
}

export function backtestStress(jobId, paths = 500, horizonDays = 20) {
  return http.get(`/api/backtest/${jobId}/stress`, { params: { paths, horizonDays } })
}
