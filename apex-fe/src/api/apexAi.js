import http from './http'

export function getApexAiContext() {
  return http.get('/api/apex-ai/context')
}

export function analyzeWithXiaoling(payload) {
  return http.post('/api/apex-ai/analyze', payload, { timeout: 90000 })
}
