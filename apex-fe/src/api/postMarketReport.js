import http from './http'

export function fetchPostMarketReport() {
  return http.get('/api/post-market-report', { timeout: 90000 })
}

export function refreshPostMarketReport() {
  return http.post('/api/post-market-report/refresh', null, { timeout: 120000 })
}
