import http from './http'

export function runDecision(payload = {}) {
  return http.post('/api/decision/run', payload)
}

export function fetchDecisionToday(date, groupName = '我的自选') {
  return http.get('/api/decision/today', { params: { date, groupName } })
}

export function fetchDecisionBuyAiSummary(date, groupName = '我的自选', force = false) {
  const params = { groupName, force }
  // 仅传 yyyy-MM-dd；空值不带 date，避免转换器吃空串
  if (date) {
    const text = String(date).trim().slice(0, 10)
    if (/^\d{4}-\d{2}-\d{2}$/.test(text)) {
      params.date = text
    }
  }
  return http.get('/api/decision/buy-ai-summary', {
    params,
    timeout: 90000,
  })
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
