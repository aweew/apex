import http from './http'

export function fetchStockDetail(code, barLimit = 120, refresh = false) {
  return http.get(`/api/stock/${code}`, { params: { barLimit, refresh } })
}

export function fetchStockIntraday(code) {
  return http.get(`/api/stock/${encodeURIComponent(code)}/intraday`, {
    timeout: 20000,
    activity: false,
  })
}

export function syncStockBasic(code) {
  return http.post(`/api/stock/${code}/sync`, null, { timeout: 60000 })
}

export function searchStock(q, limit = 15) {
  return http.get('/api/stock/search', { params: { q, limit }, activity: false })
}

export function fetchStockFundamental(code, periodLimit = 40, reportPeriodLimit = 12) {
  return http.get(`/api/stock/${code}/fundamental`, {
    params: { periodLimit, reportPeriodLimit },
  })
}

export function fetchCompanyProfile(code, refresh = false) {
  return http.get(`/api/stock/${code}/profile`, { params: { refresh } })
}

export function refreshCompanyProfile(code) {
  return http.post(`/api/stock/${code}/profile/refresh`)
}

/** 个股综合研判：技术 + 估值 + 资金情绪 + 策略结论（可选 AI） */
export function fetchStockAnalysis(code, side = 'BUY', barLimit = 120, withAi = false, forceAi = false) {
  return http.get(`/api/stock/${encodeURIComponent(code)}/analysis`, {
    params: { side, barLimit, withAi, forceAi },
    timeout: withAi ? 90000 : 45000,
  })
}
