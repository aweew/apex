import http from './http'

export function syncBars(payload) {
  return http.post('/api/data/bars/sync', payload, { timeout: 210000 })
}

export function syncBarsFast(payload) {
  return http.post('/api/data/bars/sync-fast', payload, { timeout: 10000 })
}

export function syncBarsGroup(groupName, beginDate, endDate) {
  return http.post('/api/data/bars/sync-group', null, {
    params: { groupName, beginDate, endDate },
  })
}

export function syncStaleBars(groupName, limit = 40) {
  return http.post('/api/data/bars/sync-stale', null, {
    params: { groupName, limit },
  })
}

export function fillWatchlistBars(groupName, rounds = 3, limit = 40) {
  return http.post('/api/data/bars/fill', null, {
    params: { groupName, rounds, limit },
    timeout: 300000,
  })
}

export function listBars(code, limit = 60) {
  return http.get('/api/data/bars/list', { params: { code, limit } })
}
