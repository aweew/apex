import http from './http'

export function fetchDailyPreMarketReport() {
  return http.get('/api/pre-market-report', { timeout: 90000 })
}

export function refreshDailyPreMarketReport() {
  return http.post('/api/pre-market-report/refresh', null, { timeout: 120000 })
}
