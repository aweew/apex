import http from './http'

export function listHoldings() {
  return http.get('/api/holding/list')
}

export function saveHolding(payload) {
  return http.post('/api/holding/save', payload)
}

export function removeHolding(id) {
  return http.delete(`/api/holding/${id}`)
}

export function refreshHoldingQuotes(onlyMissing = true) {
  return http.post('/api/holding/refresh-quotes', null, {
    params: { onlyMissing },
  })
}
