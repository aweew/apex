import http from './http'

export function fetchIndexBoard(sparkDays = 30) {
  return http.get('/api/index/board', { params: { sparkDays } })
}

export function fetchIndexBars(code, limit = 120) {
  return http.get(`/api/index/${code}/bars`, { params: { limit } })
}

export function refreshIndexBoard(start = '20180101') {
  return http.post('/api/index/refresh', null, { params: { start } })
}
