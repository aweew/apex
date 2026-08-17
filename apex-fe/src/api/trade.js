import http from './http'

export function listTradeRecords(params = {}) {
  return http.get('/api/trades', { params })
}

export function fetchTradeMarkers(code) {
  return http.get('/api/trades/markers', { params: { code } })
}
