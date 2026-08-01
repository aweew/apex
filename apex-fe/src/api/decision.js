import http from './http'

export function runDecision(payload = {}) {
  return http.post('/api/decision/run', payload)
}

export function fetchDecisionToday(date, groupName = '我的自选') {
  return http.get('/api/decision/today', { params: { date, groupName } })
}
