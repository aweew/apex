import http from './http'

export function dashboardOverview(accountId) {
  return http.get('/api/dashboard/overview', { params: { accountId } })
}

export function riskOverview(accountId) {
  return http.get('/api/risk/overview', { params: { accountId } })
}

export function listRiskRules() {
  return http.get('/api/risk/rules')
}

export function updateRiskRule(payload) {
  return http.put('/api/risk/rules', payload)
}

export function applyRiskPreset(preset) {
  return http.post('/api/risk/rules/preset', null, { params: { preset } })
}

export function listConfig() {
  return http.get('/api/config')
}

export function updateConfig(payload) {
  return http.put('/api/config', payload)
}

export function localLogin(payload) {
  return http.post('/api/auth/local-login', payload)
}
