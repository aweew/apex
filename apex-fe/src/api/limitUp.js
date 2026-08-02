import http from './http'

export function fetchLimitUpLadder(tradeDate) {
  return http.get('/api/limit-up/ladder', {
    params: { tradeDate },
  })
}

export function refreshLimitUpLadder(tradeDate) {
  return http.post(
    '/api/limit-up/refresh',
    null,
    { params: { tradeDate }, timeout: 300000 },
  )
}
