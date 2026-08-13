import http from './http'

export function runDecision(payload = {}) {
  // 全市场扫描 + 策略/观察池同步可能数分钟，避免默认 60s 超时导致「跑完无提示」
  return http.post('/api/decision/run', payload, { timeout: 600000 })
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

export function fetchDecisionAdvice(date) {
  return http.get('/api/decision/advice', { params: date ? { date } : {} })
}
