import http from './http'

export function runScreener(payload) {
  return http.post('/api/screener/run', payload || {})
}

/** 全市场 / 股票池数量 */
export function fetchScreenerMeta() {
  return http.get('/api/screener/meta')
}

/** 分页浏览全市场股票 */
export function fetchScreenerMarket(params = {}) {
  return http.get('/api/screener/market', { params })
}
