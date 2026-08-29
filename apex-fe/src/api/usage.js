import http from './http'

export function recordPageView(moduleCode) {
  return http.post('/api/usage/events/page-view', { moduleCode }, { activity: false })
}

export function fetchUserUsageOverview(days = 30) {
  return http.get('/api/usage/admin/overview', { params: { days } })
}
