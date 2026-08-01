import http from './http'

export function runScreener(payload) {
  return http.post('/api/screener/run', payload || {})
}
