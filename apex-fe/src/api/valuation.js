import http from './http'

export function fetchValuation(code) {
  return http.get(`/api/valuation/${encodeURIComponent(code)}`)
}

export function fetchValuationBrief(code) {
  return http.get(`/api/valuation/${encodeURIComponent(code)}/brief`)
}

export function fetchValuationScreen(params = {}) {
  return http.get('/api/valuation/screen', { params })
}
