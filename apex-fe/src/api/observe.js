import http from './http'

export function listObserve(params = {}) {
  return http.get('/api/observe/list', { params })
}

export function saveObserve(payload) {
  return http.post('/api/observe/save', payload)
}

export function removeObserve(id) {
  return http.delete(`/api/observe/${id}`)
}

export function archiveObserve(id) {
  return http.post(`/api/observe/${id}/archive`)
}

export function refreshObserve() {
  return http.post('/api/observe/refresh')
}

export function fetchGuideTemplate(reason) {
  return http.get('/api/observe/guide-template', { params: { reason } })
}

export function fetchGuideTemplates() {
  return http.get('/api/observe/guide-templates')
}

/** 一键自动决策：跑策略并写入观察池 */
export function autoDecideObserve(payload = {}) {
  return http.post('/api/observe/auto-decide', payload, { timeout: 120000 })
}
