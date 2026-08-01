import http from './http'

export function fetchStockDetail(code, barLimit = 120, refresh = false) {
  return http.get(`/api/stock/${code}`, { params: { barLimit, refresh } })
}

export function syncStockBasic(code) {
  return http.post(`/api/stock/${code}/sync`)
}

export function searchStock(q, limit = 15) {
  return http.get('/api/stock/search', { params: { q, limit } })
}
