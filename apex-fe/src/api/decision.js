import http from './http'

export function runDecision(payload = {}) {
  return http.post('/api/decision/run', payload)
}

export function fetchDecisionToday(date, groupName = '我的自选') {
  return http.get('/api/decision/today', { params: { date, groupName } })
}

export function fetchDecisionHistory(limit = 15) {
  return http.get('/api/decision/history', { params: { limit } })
}

export function fetchDecisionPlaybook() {
  return http.get('/api/decision/playbook')
}

export function fetchDecisionAttribution(days = 20) {
  return http.get('/api/decision/attribution', { params: { days } })
}
