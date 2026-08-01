import http from './http'

export function runPipeline(payload) {
  return http.post('/api/pipeline/run', payload || {})
}
