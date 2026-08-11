import http from './http'

export function fetchSyncOverview() {
  return http.get('/api/sync/overview', { activity: false })
}

export function startSyncJob(body) {
  return http.post('/api/sync/jobs', body)
}

export function fetchSyncJob(id) {
  return http.get(`/api/sync/jobs/${id}`, { activity: false })
}

/** 兼容旧命名 */
export function getSyncJob(id) {
  return fetchSyncJob(id)
}

export function stopSyncJob(id) {
  return http.post(`/api/sync/jobs/${id}/stop`)
}

export function fetchRecentSyncJobs(limit = 20) {
  return http.get('/api/sync/jobs', { params: { limit }, activity: false })
}
