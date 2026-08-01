import http from './http'

export function runSignals(payload = {}) {
  return http.post('/api/signal/run', payload)
}

export function latestSignals(limit = 50, dedupeByCode = true, minScore, side) {
  return http.get('/api/signal/latest', { params: { limit, dedupeByCode, minScore, side } })
}

export function signalStats(days = 5) {
  return http.get('/api/signal/stats', { params: { days } })
}

export function signalForward(lookbackDays = 60, horizonDays = 5) {
  return http.get('/api/signal/forward', { params: { lookbackDays, horizonDays } })
}

export function signalConfluence(days = 5, minStrategies = 2) {
  return http.get('/api/signal/confluence', { params: { days, minStrategies } })
}

export function refreshUniverse(payload = {}) {
  return http.post('/api/universe/refresh', payload)
}

export function latestUniverse() {
  return http.get('/api/universe/latest')
}
