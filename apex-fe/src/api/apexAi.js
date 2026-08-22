import http from './http'

export function getApexAiContext() {
  return http.get('/api/apex-ai/context')
}

export function analyzeWithXiaoling(payload) {
  return http.post('/api/apex-ai/analyze', payload, { timeout: 30000 })
}

export function enhanceXiaolingAnalysis(payload) {
  return http.post('/api/apex-ai/enhance', payload, { timeout: 60000 })
}

export function getLatestApexAiConversation() {
  return http.get('/api/apex-ai/conversation/latest')
}
